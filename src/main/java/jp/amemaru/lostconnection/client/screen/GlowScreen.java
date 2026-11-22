package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientConnection;
import jp.amemaru.lostconnection.connection.ConnectionCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 大結晶の輝き（Phase 7 その1。Phase 0-A 7章「大結晶：コネクション量と輝き段階の表示」・13章）。大結晶の台座の
 * 「記憶の石」を右クリックすると開く。
 *
 * <ul>
 *   <li>左：輝き段階（1〜10）と加護の星、次の段階までのCP（人数補正のあと）、いま効いていること、次の段階で効くこと</li>
 *   <li>右：今日のあなた（7分野のCPと上限・合計・結び札の残り）／みんなの貢献（分野ごとの上位3人）</li>
 *   <li>下：「記憶の再生」（見逃したストーリーイベント）</li>
 * </ul>
 */
public class GlowScreen extends Screen {
    private static final int W = 340;
    private static final int H = 226;
    private static final int BAR = 0xFF8FD3FF;

    private int left;
    private int top;
    private boolean everyone;

    public GlowScreen() {
        super(Component.translatable("screen.amemaru.glow.title"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
        addRenderableWidget(new CrystalButton(left + 178, top + 24, 72, 14, Component.translatable("screen.amemaru.glow.mine"),
                b -> everyone = false));
        addRenderableWidget(new CrystalButton(left + 254, top + 24, 72, 14, Component.translatable("screen.amemaru.glow.everyone"),
                b -> everyone = true));
        addRenderableWidget(new CrystalButton(left + 10, top + H - 24, 110, 16, Component.translatable("screen.amemaru.glow.replay"),
                b -> ClientConnection.send("replay", new CompoundTag())));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 自動の確認の写真：右の表を「みんなの貢献」にする（false で「今日のあなた」）。 */
    public void ciEveryone(boolean on) {
        everyone = on;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        graphics.drawCenteredString(font, title, left + W / 2, top + 7, UiStyle.TEXT_HEADER);
        CompoundTag s = ClientConnection.state();
        renderServer(graphics, s);
        if (everyone) {
            renderEveryone(graphics, s);
        } else {
            renderMine(graphics, s);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ---------------------------------------------------------------- 左：サーバー全体の輝き

    private void renderServer(GuiGraphics graphics, CompoundTag s) {
        int x0 = left + 8;
        int x1 = left + 170;
        UiStyle.card(graphics, x0, top + 20, x1, top + H - 30, 0x8FD3FF, false);
        int x = x0 + 6;
        int y = top + 26;
        int stage = Math.max(1, s.getInt("stage"));
        int maxStage = Math.max(1, s.getInt("maxStage"));
        graphics.drawString(font, Component.translatable("screen.amemaru.glow.stage", stage, maxStage), x, y, UiStyle.TEXT_GOLD, true);
        if (stage >= maxStage && s.getInt("maxStars") > 0) {
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < s.getInt("maxStars"); i++) {
                stars.append(i < s.getInt("stars") ? "★" : "☆");
            }
            Component starLine = Component.translatable("screen.amemaru.glow.stars", stars.toString());
            graphics.drawString(font, starLine, x1 - 6 - font.width(starLine), y, 0xFFFFD866, false);
        }
        y += 14;
        long total = s.getLong("total");
        long from = s.getLong("from");
        long next = s.getLong("next");
        // 次の段階（または次の星）までの帯
        int bx0 = x;
        int bx1 = x1 - 6;
        graphics.fill(bx0, y, bx1, y + 7, 0xFF0B1022);
        double ratio = next > from ? Math.max(0.0, Math.min(1.0, (double) (total - from) / (double) (next - from))) : 1.0;
        graphics.fill(bx0 + 1, y + 1, bx0 + 1 + (int) Math.round((bx1 - bx0 - 2) * ratio), y + 6, BAR);
        y += 10;
        graphics.drawString(font, Component.translatable("screen.amemaru.glow.total", format(total)), x, y, UiStyle.TEXT, false);
        if (next > 0) {
            Component rest = Component.translatable("screen.amemaru.glow.rest", format(Math.max(0, next - total)));
            graphics.drawString(font, rest, x1 - 6 - font.width(rest), y, UiStyle.TEXT_DIM, false);
        }
        y += 11;
        String scale = String.format(Locale.ROOT, "%.2f", s.getDouble("scale"));
        String active = String.format(Locale.ROOT, "%.1f", s.getDouble("active"));
        y += 10 * UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.glow.scale", scale, active, s.getInt("base")).getString(), x, y,
                x1 - x0 - 12, UiStyle.TEXT_DIM, 2) + 4;
        graphics.drawString(font, Component.translatable("screen.amemaru.glow.effects_now"), x, y, UiStyle.TEXT_HEADER, false);
        y += 11;
        y = effectLines(graphics, s.getList("effects", Tag.TAG_COMPOUND), x, y, UiStyle.TEXT_GOOD, "screen.amemaru.glow.effects_none");
        if (s.contains("upcoming")) {
            y += 3;
            graphics.drawString(font, Component.translatable("screen.amemaru.glow.effects_next", stage + 1), x, y, UiStyle.TEXT_HEADER, false);
            y += 11;
            effectLines(graphics, s.getList("upcoming", Tag.TAG_COMPOUND), x, y, UiStyle.TEXT_DIM, "screen.amemaru.glow.effects_none");
        }
    }

    private int effectLines(GuiGraphics graphics, ListTag list, int x, int y, int color, String emptyKey) {
        if (list.isEmpty()) {
            graphics.drawString(font, Component.translatable(emptyKey), x + 4, y, UiStyle.TEXT_DIM, false);
            return y + 10;
        }
        for (Tag t : list) {
            CompoundTag one = (CompoundTag) t;
            Component line = jp.amemaru.lostconnection.connection.ConnectionService.effectText(one.getString("key"), one.getDouble("value"));
            graphics.drawString(font, UiStyle.clip(font, "・" + line.getString(), 150), x + 2, y, color, false);
            y += 10;
        }
        return y;
    }

    // ---------------------------------------------------------------- 右：今日のあなた

    private void renderMine(GuiGraphics graphics, CompoundTag s) {
        int x0 = left + 176;
        int x1 = left + W - 8;
        UiStyle.card(graphics, x0, top + 40, x1, top + H - 30, 0xC07CFF, false);
        int x = x0 + 6;
        int y = top + 46;
        graphics.drawString(font, Component.translatable("screen.amemaru.glow.today"), x, y, UiStyle.TEXT_HEADER, false);
        y += 12;
        int[] today = s.getIntArray("today");
        long[] lifetime = s.getLongArray("lifetime");
        int capCategory = Math.max(1, s.getInt("capCategory"));
        int sum = 0;
        for (ConnectionCategory c : ConnectionCategory.values()) {
            int v = c.ordinal() < today.length ? today[c.ordinal()] : 0;
            sum += v;
            graphics.drawString(font, Component.translatable(c.translationKey()), x, y, UiStyle.TEXT, false);
            int bx0 = x + 30;
            int bx1 = x1 - 52;
            graphics.fill(bx0, y + 1, bx1, y + 7, 0xFF0B1022);
            double r = Math.min(1.0, (double) v / capCategory);
            graphics.fill(bx0 + 1, y + 2, bx0 + 1 + (int) Math.round((bx1 - bx0 - 2) * r), y + 6, v >= capCategory ? 0xFFFFD866 : 0xFFC07CFF);
            String text = v + "/" + capCategory;
            graphics.drawString(font, text, x1 - 6 - font.width(text), y, v >= capCategory ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM, false);
            y += 11;
        }
        y += 2;
        int capTotal = Math.max(1, s.getInt("capTotal"));
        Component totalLine = Component.translatable("screen.amemaru.glow.today_total", sum, capTotal);
        graphics.drawString(font, totalLine, x, y, sum >= capTotal ? UiStyle.TEXT_GOLD : UiStyle.TEXT, false);
        y += 11;
        long life = 0;
        for (long v : lifetime) {
            life += v;
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.glow.lifetime", format(life)), x, y, UiStyle.TEXT_DIM, false);
        y += 11;
        graphics.drawString(font, Component.translatable("screen.amemaru.glow.thanks_left", s.getInt("thanksLeft")), x, y, 0xFFE3A6FF, false);
        y += 11;
        // 今日の相棒と、はじめましてボーナス（15章）
        CompoundTag partner = s.getCompound("partner");
        Component partnerLine;
        if (!partner.contains("name")) {
            partnerLine = Component.translatable("screen.amemaru.glow.partner_none");
        } else if (partner.getBoolean("done")) {
            partnerLine = Component.translatable("screen.amemaru.glow.partner_done", partner.getString("name"));
        } else {
            partnerLine = Component.translatable("screen.amemaru.glow.partner", partner.getString("name"), partner.getInt("progress"),
                    partner.getInt("goal"));
        }
        graphics.drawString(font, UiStyle.clip(font, partnerLine.getString(), x1 - x - 6), x, y,
                partner.getBoolean("done") ? UiStyle.TEXT_GOLD : 0xFFE3A6FF, false);
        y += 11;
        double bonus = s.getDouble("firstMeetBonus");
        String bonusText = bonus == Math.rint(bonus) ? String.valueOf((long) bonus) : String.format(Locale.ROOT, "%.1f", bonus);
        Component meetLine = Component.translatable("screen.amemaru.glow.first_meet", s.getInt("firstMeets"), s.getInt("firstMeetDaily"), bonusText);
        graphics.drawString(font, UiStyle.clip(font, meetLine.getString(), x1 - x - 6), x, y, bonus > 0 ? UiStyle.TEXT_GOOD : UiStyle.TEXT_DIM,
                false);
    }

    // ---------------------------------------------------------------- 右：みんなの貢献

    private void renderEveryone(GuiGraphics graphics, CompoundTag s) {
        int x0 = left + 176;
        int x1 = left + W - 8;
        UiStyle.card(graphics, x0, top + 40, x1, top + H - 30, 0x9CFFB0, false);
        int x = x0 + 6;
        int y = top + 46;
        graphics.drawString(font, Component.translatable("screen.amemaru.glow.everyone_title"), x, y, UiStyle.TEXT_HEADER, false);
        y += 12;
        CompoundTag top3 = s.getCompound("top");
        long[] cats = s.getLongArray("categories");
        for (ConnectionCategory c : ConnectionCategory.values()) {
            long sum = c.ordinal() < cats.length ? cats[c.ordinal()] : 0;
            // 1行に：分野と合計（白）、上位の人（薄い色）
            Component head = Component.translatable("screen.amemaru.glow.category_total", Component.translatable(c.translationKey()), format(sum));
            graphics.drawString(font, head, x, y, UiStyle.TEXT, false);
            int nx = x + Math.max(64, font.width(head) + 6);
            List<String> names = new ArrayList<>();
            for (Tag t : top3.getList(c.key(), Tag.TAG_COMPOUND)) {
                CompoundTag one = (CompoundTag) t;
                names.add(one.getString("name"));
            }
            String line = names.isEmpty() ? Component.translatable("screen.amemaru.glow.nobody").getString() : String.join("・", names);
            graphics.drawString(font, UiStyle.clip(font, line, x1 - nx - 6), nx, y, UiStyle.TEXT_DIM, false);
            y += 12;
        }
        y += 4;
        UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.glow.everyone_note").getString(), x, y, x1 - x - 6,
                UiStyle.TEXT_DIM, 4);
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
