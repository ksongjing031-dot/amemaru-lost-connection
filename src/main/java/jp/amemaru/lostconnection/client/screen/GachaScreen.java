package jp.amemaru.lostconnection.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import jp.amemaru.lostconnection.client.fx.FxTextures;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.GachaC2S;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 祈願所（ガチャ）の画面（11月27日【補完】）。
 *
 * <ul>
 *   <li>左：階ごとの祈願（その階のボスを1回も倒していない祈願は「？？？」）</li>
 *   <li>右：祈願の名前と説明・★5（大きく回す）・★4・祈願札の数・天井までの回数・「1回」「10回」・「確率」</li>
 *   <li>引いた：暗くなって魔法陣が回り、光の柱（いちばん良い星の色：★3 青・★4 紫・★5 虹）。カードが1枚ずつめくれる。
 *       押すと飛ばせる。★5のカードは虹のふち</li>
 * </ul>
 */
public final class GachaScreen extends Screen {
    private static final int W = 372;
    private static final int H = 232;
    private static final int TAB_W = 88;
    private static final int SUMMON_MS = 2300;
    private static final int CARD_MS = 170;
    private static final int[] STAR_COLORS = {0, 0, 0, 0x6FB8FF, 0xC07CFF, 0xFFD27A};

    private CompoundTag data;
    private int selected;
    private int left;
    private int top;
    private boolean showRates;
    private String error = "";
    private long errorAt;
    /** 引いた結果（空なら祈願の画面）。 */
    private final List<ItemStack> results = new ArrayList<>();
    private final List<Integer> stars = new ArrayList<>();
    private int best;
    private long pulledAt;
    private int revealed;
    private boolean skipped;
    private boolean waiting;
    private boolean boomPlayed;

    public GachaScreen(CompoundTag data) {
        super(Component.translatable("screen.amemaru.gacha.title"));
        this.data = data;
        selectById(data.getString("select"));
    }

    // ---------------------------------------------------------------- 受け取り

    public static void handle(String kind, CompoundTag data) {
        Minecraft minecraft = Minecraft.getInstance();
        GachaScreen screen = minecraft.screen instanceof GachaScreen open ? open : null;
        switch (kind) {
            case "screen" -> {
                if (screen != null) {
                    screen.update(data);
                    screen.selectById(data.getString("select"));
                    screen.rebuild();
                } else {
                    minecraft.setScreen(new GachaScreen(data));
                }
            }
            case "result" -> {
                if (screen != null) {
                    screen.update(data);
                    screen.showResults(data);
                }
            }
            case "error" -> {
                if (screen != null) {
                    screen.update(data);
                    screen.waiting = false;
                    screen.error = Component.translatable(data.getString("error")).getString();
                    screen.errorAt = Util.getMillis();
                    screen.rebuild();
                }
            }
            default -> {
            }
        }
    }

    private void update(CompoundTag next) {
        String id = bannerId();
        this.data = next;
        selectById(id);
    }

    private void selectById(String id) {
        ListTag banners = banners();
        for (int i = 0; i < banners.size(); i++) {
            if (banners.getCompound(i).getString("id").equals(id)) {
                selected = i;
                return;
            }
        }
        // 選ばれていなければ、倒したことのある中でいちばん奥の階
        selected = 0;
        for (int i = 0; i < banners.size(); i++) {
            if (banners.getCompound(i).getInt("kills") > 0) {
                selected = i;
            }
        }
    }

    private ListTag banners() {
        return data.getList("banners", Tag.TAG_COMPOUND);
    }

    private CompoundTag banner() {
        ListTag banners = banners();
        return selected >= 0 && selected < banners.size() ? banners.getCompound(selected) : new CompoundTag();
    }

    private String bannerId() {
        return banner().getString("id");
    }

    private boolean unlocked(CompoundTag banner) {
        // 管理者のコマンドで開いたときは、まだ倒していない階の祈願も見せる
        return banner.getInt("kills") > 0 || data.getBoolean("admin");
    }

    // ---------------------------------------------------------------- 画面

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
        if (!results.isEmpty()) {
            CrystalButton back = new CrystalButton(left + W / 2 - 40, top + H - 22, 80, 16,
                    Component.translatable("screen.amemaru.gacha.back"), b -> closeResults());
            back.visible = allRevealed();
            addRenderableWidget(back);
            return;
        }
        ListTag banners = banners();
        for (int i = 0; i < banners.size(); i++) {
            CompoundTag one = banners.getCompound(i);
            int index = i;
            String label = unlocked(one) ? Component.translatable("screen.amemaru.gacha.tab", one.getInt("floor"), shortName(one)).getString()
                    : Component.translatable("screen.amemaru.gacha.tab_locked", one.getInt("floor")).getString();
            CrystalButton tab = new CrystalButton(left + 8, top + 26 + i * 24, TAB_W - 8, 20, Component.literal(label), b -> {
                selected = index;
                showRates = false;
                rebuild();
            });
            tab.active = i != selected;
            addRenderableWidget(tab);
        }
        CompoundTag banner = banner();
        int tokens = banner.getInt("tokens");
        int ten = Math.max(2, data.getInt("ten"));
        int x = left + TAB_W + 8;
        int y = top + H - 26;
        CrystalButton one = new CrystalButton(x, y, 108, 18, Component.translatable("screen.amemaru.gacha.pull_one"), b -> pull(1));
        one.active = unlocked(banner) && tokens >= 1 && !waiting;
        addRenderableWidget(one);
        CrystalButton many = new CrystalButton(x + 114, y, 116, 18, Component.translatable("screen.amemaru.gacha.pull_ten", ten), b -> pull(ten));
        many.active = unlocked(banner) && tokens >= ten && !waiting;
        addRenderableWidget(many);
        addRenderableWidget(new CrystalButton(x + 236, y, 40, 18, Component.translatable(showRates ? "screen.amemaru.gacha.rates_close"
                : "screen.amemaru.gacha.rates"), b -> {
            showRates = !showRates;
            rebuild();
        }));
    }

    private static String shortName(CompoundTag banner) {
        String name = banner.getString("name");
        return name.endsWith("の祈願") ? name.substring(0, name.length() - 3) : name;
    }

    private void pull(int count) {
        if (waiting) {
            return;
        }
        waiting = true;
        error = "";
        ModNetwork.sendToServer(new GachaC2S(bannerId(), count));
        rebuild();
    }

    private void showResults(CompoundTag tag) {
        waiting = false;
        results.clear();
        stars.clear();
        ListTag list = tag.getList("results", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            results.add(ItemStack.of(one.getCompound("stack")));
            stars.add(one.getInt("stars"));
        }
        best = tag.getInt("best");
        pulledAt = Util.getMillis();
        revealed = 0;
        skipped = false;
        boomPlayed = false;
        showRates = false;
        play(SoundEvents.BEACON_ACTIVATE, 1.0F, 1.4F);
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F);
        rebuild();
    }

    private void closeResults() {
        results.clear();
        stars.clear();
        rebuild();
    }

    private boolean allRevealed() {
        return skipped || revealed >= results.size();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!results.isEmpty() && !allRevealed()) {
            skipped = true;
            revealed = results.size();
            rebuild();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- 描く

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        graphics.drawString(font, title, left + 10, top + 8, UiStyle.TEXT_HEADER, true);
        if (!results.isEmpty()) {
            renderResults(graphics, mouseX, mouseY);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        CompoundTag banner = banner();
        renderBanner(graphics, banner, mouseX, mouseY);
        if (showRates) {
            renderRates(graphics, banner);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!error.isEmpty() && Util.getMillis() - errorAt < 4000) {
            graphics.drawCenteredString(font, error, left + TAB_W + (W - TAB_W) / 2, top + H - 38, UiStyle.TEXT_BAD);
        }
        if (waiting) {
            graphics.drawCenteredString(font, Component.translatable("screen.amemaru.gacha.waiting"), left + TAB_W + (W - TAB_W) / 2,
                    top + H - 38, UiStyle.TEXT_DIM);
        }
    }

    private void renderBanner(GuiGraphics graphics, CompoundTag banner, int mouseX, int mouseY) {
        int x0 = left + TAB_W + 4;
        int y0 = top + 24;
        int x1 = left + W - 8;
        int y1 = top + H - 32;
        int color = banner.getInt("color") & 0xFFFFFF;
        // 祈願の色の地と、ゆっくり回る魔法陣
        graphics.fillGradient(x0, y0, x1, y1, 0xE0000000 | UiStyle.darken(color, 0.32F), 0xF00B1022);
        long now = Util.getMillis();
        drawSigil(graphics, FxTextures.SIGIL_STAR, x1 - 62, y0 + 62, 56, now / 60.0F, color, 0.35F);
        graphics.renderOutline(x0, y0, x1 - x0, y1 - y0, 0xFF000000 | UiStyle.darken(color, 0.8F));
        boolean open = unlocked(banner);
        graphics.drawString(font, open ? banner.getString("name") : Component.translatable("screen.amemaru.gacha.locked_name",
                banner.getInt("floor")).getString(), x0 + 8, y0 + 6, 0xFF000000 | color, true);
        if (!open) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.gacha.locked", banner.getInt("floor")).getString(),
                    x0 + 8, y0 + 22, x1 - x0 - 16, UiStyle.TEXT_DIM, 4);
            return;
        }
        UiStyle.wrapped(graphics, font, banner.getString("text"), x0 + 8, y0 + 18, x1 - x0 - 130, UiStyle.TEXT, 3);
        // ★5（大きく回す）
        ItemStack five = first(banner, 5);
        int fx = x1 - 62;
        int fy = y0 + 62;
        graphics.pose().pushPose();
        graphics.pose().translate(fx, fy, 150.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(Mth.sin(now / 700.0F) * 8.0F));
        graphics.pose().scale(3.0F, 3.0F, 1.0F);
        graphics.renderItem(five, -8, -8);
        graphics.pose().popPose();
        graphics.drawCenteredString(font, Component.literal("★★★★★").withStyle(ChatFormatting.GOLD), fx, y0 + 96, 0xFFFFFF);
        graphics.drawCenteredString(font, UiStyle.clip(font, five.getHoverName().getString(), 120), fx, y0 + 106,
                Rarity.UNIQUE.colorAt(now) & 0xFFFFFF);
        if (mouseX >= fx - 28 && mouseX < fx + 28 && mouseY >= fy - 28 && mouseY < fy + 28) {
            setTooltipForNextRenderPass(Screen.getTooltipFromItem(Minecraft.getInstance(), five).stream()
                    .map(Component::getVisualOrderText).toList());
        }
        // ★4
        graphics.drawString(font, Component.literal("★★★★").withStyle(ChatFormatting.LIGHT_PURPLE), x0 + 8, y0 + 56, 0xFFFFFF, false);
        ListTag four = banner.getList("pool4", Tag.TAG_COMPOUND);
        for (int i = 0; i < four.size(); i++) {
            ItemStack stack = stack(four.getCompound(i));
            int ix = x0 + 8 + i * 22;
            int iy = y0 + 68;
            graphics.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0x60C07CFF);
            graphics.renderItem(stack, ix, iy);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                setTooltipForNextRenderPass(Screen.getTooltipFromItem(Minecraft.getInstance(), stack).stream()
                        .map(Component::getVisualOrderText).toList());
            }
        }
        // 祈願札・天井・引いた数
        ItemStack token = item(banner.getString("token"), 1);
        int ty = y0 + 96;
        graphics.renderItem(token, x0 + 8, ty);
        graphics.drawString(font, Component.translatable("screen.amemaru.gacha.tokens", token.getHoverName(), banner.getInt("tokens")),
                x0 + 28, ty + 4, UiStyle.TEXT_GOLD, false);
        int pity = banner.getInt("pity");
        int since = banner.getInt("since");
        graphics.drawString(font, Component.translatable("screen.amemaru.gacha.pity", Math.max(1, pity - since)), x0 + 8, ty + 22,
                UiStyle.TEXT_HEADER, false);
        graphics.drawString(font, Component.translatable("screen.amemaru.gacha.pulled", banner.getInt("pulls"), banner.getInt("fives")),
                x0 + 8, ty + 34, UiStyle.TEXT_DIM, false);
        graphics.drawString(font, Component.translatable("screen.amemaru.gacha.how", data.getInt("boss_tokens"), banner.getInt("floor")),
                x0 + 8, ty + 46, UiStyle.TEXT_DIM, false);
        // 天井までの帯
        int barX = x0 + 8;
        int barY = ty + 58;
        int barW = 150;
        graphics.fill(barX, barY, barX + barW, barY + 4, 0xFF1A2236);
        graphics.fill(barX, barY, barX + Math.round(barW * Mth.clamp(since / (float) Math.max(1, pity), 0.0F, 1.0F)), barY + 4,
                0xFFFFD27A);
    }

    private void renderRates(GuiGraphics graphics, CompoundTag banner) {
        int x0 = left + TAB_W + 12;
        int y0 = top + 30;
        int x1 = left + W - 14;
        int y1 = top + H - 34;
        graphics.fill(x0, y0, x1, y1, 0xF00B1022);
        graphics.renderOutline(x0, y0, x1 - x0, y1 - y0, UiStyle.BORDER_LIGHT | 0xFF000000);
        int y = y0 + 6;
        graphics.drawString(font, Component.translatable("screen.amemaru.gacha.rates_title", fmt(banner.getDouble("five")),
                fmt(banner.getDouble("four")), fmt(100.0 - banner.getDouble("five") - banner.getDouble("four"))), x0 + 6, y,
                UiStyle.TEXT_HEADER, false);
        y += 12;
        graphics.drawString(font, Component.translatable("screen.amemaru.gacha.rates_rule", banner.getInt("pity"), data.getInt("ten"),
                data.getInt("guarantee")), x0 + 6, y, UiStyle.TEXT_DIM, false);
        y += 14;
        int col = 0;
        for (int stars = 5; stars >= 3; stars--) {
            ListTag pool = banner.getList("pool" + stars, Tag.TAG_COMPOUND);
            for (int i = 0; i < pool.size(); i++) {
                CompoundTag one = pool.getCompound(i);
                ItemStack stack = stack(one);
                int cx = x0 + 6 + col * ((x1 - x0 - 12) / 2);
                String count = one.getInt("min") == one.getInt("max") ? (one.getInt("min") > 1 ? "×" + one.getInt("min") : "")
                        : "×" + one.getInt("min") + "〜" + one.getInt("max");
                String line = "★" + stars + " " + UiStyle.clip(font, stack.getHoverName().getString() + count, 96) + " "
                        + fmt(one.getDouble("chance")) + "%";
                graphics.drawString(font, line, cx, y, STAR_COLORS[stars] | 0xFF000000, false);
                col++;
                if (col >= 2) {
                    col = 0;
                    y += 10;
                }
                if (y > y1 - 10) {
                    return;
                }
            }
        }
    }

    private void renderResults(GuiGraphics graphics, int mouseX, int mouseY) {
        long now = Util.getMillis();
        long t = now - pulledAt;
        int cx = left + W / 2;
        int cy = top + H / 2;
        graphics.fill(left + 4, top + 22, left + W - 4, top + H - 4, 0xF0050810);
        int color = STAR_COLORS[Math.max(3, Math.min(5, best))];
        if (!skipped && t < SUMMON_MS) {
            // 魔法陣が回り、光の柱がのびる
            float p = t / (float) SUMMON_MS;
            int shown = best >= 5 && p > 0.55F ? Rarity.UNIQUE.colorAt(now) & 0xFFFFFF : (p > 0.55F ? color : 0x8FD3FF);
            drawSigil(graphics, FxTextures.SIGIL_ARCANE, cx, cy + 10, 40 + 50 * p, now / 12.0F, shown, 0.9F);
            drawSigil(graphics, FxTextures.SIGIL_STAR, cx, cy + 10, 24 + 30 * p, -now / 9.0F, 0xFFFFFF, 0.6F);
            int pillar = Math.round(6 + 40 * p * p);
            graphics.fillGradient(cx - pillar / 2, top + 24, cx + pillar / 2, cy + 10, 0x00FFFFFF, 0xC0000000 | shown);
            if (p > 0.85F) {
                int flash = (int) (255 * Math.min(1.0F, (p - 0.85F) / 0.15F));
                graphics.fill(left + 4, top + 22, left + W - 4, top + H - 4, (flash << 24) | 0xFFFFFF);
            }
            if (t > SUMMON_MS - 120 && !boomPlayed) {
                boomPlayed = true;
                play(best >= 5 ? SoundEvents.TOTEM_USE : best >= 4 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.1F);
            }
            return;
        }
        // カードを1枚ずつめくる
        int count = results.size();
        int target = skipped ? count : Math.min(count, (int) ((t - SUMMON_MS) / CARD_MS) + 1);
        while (revealed < target) {
            int s = stars.get(revealed);
            play(s >= 5 ? SoundEvents.TOTEM_USE : s >= 4 ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_HIT, 0.6F,
                    s >= 4 ? 1.2F : 1.6F);
            revealed++;
            if (revealed >= count) {
                rebuild();
            }
        }
        int cols = count >= 10 ? 5 : count;
        int cardW = 62;
        int cardH = 78;
        int gap = 6;
        int rows = (count + cols - 1) / cols;
        int gridW = cols * cardW + (cols - 1) * gap;
        int gridH = rows * cardH + (rows - 1) * gap;
        int gx = cx - gridW / 2;
        int gy = top + 28 + Math.max(0, (H - 60 - gridH) / 2);
        for (int i = 0; i < Math.min(revealed, count); i++) {
            int x = gx + (i % cols) * (cardW + gap);
            int y = gy + (i / cols) * (cardH + gap);
            renderCard(graphics, x, y, cardW, cardH, results.get(i), stars.get(i), now, mouseX, mouseY);
        }
        if (allRevealed()) {
            graphics.drawCenteredString(font, Component.translatable("screen.amemaru.gacha.done"), cx, top + H - 34, UiStyle.TEXT_DIM);
        } else {
            graphics.drawCenteredString(font, Component.translatable("screen.amemaru.gacha.skip"), cx, top + H - 16, UiStyle.TEXT_DIM);
        }
    }

    private void renderCard(GuiGraphics graphics, int x, int y, int w, int h, ItemStack stack, int stars, long now, int mouseX, int mouseY) {
        int color = stars >= 5 ? Rarity.UNIQUE.colorAt(now) & 0xFFFFFF : STAR_COLORS[Math.max(3, stars)];
        graphics.fillGradient(x, y, x + w, y + h, 0xF0000000 | UiStyle.darken(color, 0.35F), 0xF00B1022);
        graphics.renderOutline(x, y, w, h, 0xFF000000 | color);
        if (stars >= 5) {
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, 0x80000000 | color);
            drawSigil(graphics, FxTextures.STAR, x + w / 2, y + 28, 30, now / 20.0F, color, 0.5F);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x + w / 2.0F, y + 28, 150.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.renderItem(stack, -8, -8);
        graphics.renderItemDecorations(font, stack, -8, -8);
        graphics.pose().popPose();
        graphics.drawCenteredString(font, "★".repeat(Math.max(3, stars)), x + w / 2, y + 50, 0xFF000000 | color);
        String name = UiStyle.clip(font, stack.getHoverName().getString(), w - 4);
        graphics.drawCenteredString(font, name, x + w / 2, y + 62, 0xFFFFFF);
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            List<FormattedCharSequence> lines = Screen.getTooltipFromItem(Minecraft.getInstance(), stack).stream()
                    .map(Component::getVisualOrderText).toList();
            setTooltipForNextRenderPass(lines);
        }
    }

    /** 魔法陣の絵（ワールドの演出の絵）を、画面に回して描く。 */
    private static void drawSigil(GuiGraphics graphics, ResourceLocation texture, int cx, int cy, float radius, float angle, int color,
                                  float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle % 360.0F));
        int r = Math.round(radius);
        graphics.blit(texture, -r, -r, 0, 0, r * 2, r * 2, r * 2, r * 2);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static ItemStack first(CompoundTag banner, int stars) {
        ListTag pool = banner.getList("pool" + stars, Tag.TAG_COMPOUND);
        return pool.isEmpty() ? new ItemStack(Items.BARRIER) : stack(pool.getCompound(0));
    }

    private static ItemStack stack(CompoundTag entry) {
        return item(entry.getString("item"), Math.max(1, entry.getInt("min")));
    }

    private static ItemStack item(String id, int count) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        return item == null || item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item, count);
    }

    private static String fmt(double value) {
        return Math.abs(value - Math.round(value)) < 1.0E-9 ? String.format(Locale.ROOT, "%d", Math.round(value))
                : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
    }

    private static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
