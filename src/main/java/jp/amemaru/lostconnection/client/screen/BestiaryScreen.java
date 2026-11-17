package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientAdventure;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.network.s2c.SyncAdventureS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 図鑑（敵ごとのドロップ一覧つき。仕様S11・Phase 0-A 20章【確定】：図鑑に、敵ごとに手に入れたドロップを記録する）。
 *
 * <p>左に敵の一覧（出会っていない敵は「？？？」）、右に選んだ敵の姿・階・レベル・倒した数・説明・ドロップ一覧。
 * ドロップは、手に入れた品は絵と名前、まだの品は「？？？」で出す。レアドロップには確率も出す。</p>
 */
public class BestiaryScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 206;
    private static final int ROW = 14;
    private static final int ROWS = 12;

    private final List<EnemyDef.Info> enemies = new ArrayList<>();
    private final Map<ResourceLocation, LivingEntity> previews = new HashMap<>();
    private int selected;
    private int scroll;
    private int left;
    private int top;

    public BestiaryScreen() {
        super(Component.translatable("screen.amemaru.bestiary.title"));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        enemies.clear();
        enemies.addAll(EnemyDefs.clientInfo().values());
        enemies.sort(Comparator.comparingInt(EnemyDef.Info::floor).thenComparingInt(EnemyDef.Info::level)
                .thenComparing(info -> info.id().toString()));
        selected = Math.min(selected, Math.max(0, enemies.size() - 1));
        addRenderableWidget(new CrystalButton(left + WIDTH - 54, top + HEIGHT - 20, 46, 14,
                Component.translatable("screen.amemaru.status.close"), pressed -> onClose()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + WIDTH, top + HEIGHT);
        graphics.drawString(font, title, left + 8, top + 7, UiStyle.TEXT_HEADER, true);
        int total = 0;
        for (SyncAdventureS2C.Bestiary entry : ClientAdventure.allBestiary()) {
            total += entry.kills();
        }
        Component summary = Component.translatable("screen.amemaru.bestiary.summary", countMet(), enemies.size(), total);
        graphics.drawString(font, summary, left + WIDTH - 8 - font.width(summary), top + 7, UiStyle.TEXT_DIM, true);
        UiStyle.divider(graphics, left + 8, left + WIDTH - 8, top + 18);

        if (enemies.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.bestiary.empty"), left + 10, top + 26, UiStyle.TEXT_DIM, true);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        // 一覧
        for (int i = 0; i < ROWS && scroll + i < enemies.size(); i++) {
            int index = scroll + i;
            EnemyDef.Info info = enemies.get(index);
            int y = top + 24 + i * ROW;
            boolean met = met(info.id());
            if (index == selected) {
                graphics.fill(left + 6, y - 2, left + 98, y + ROW - 3, 0x608FD3FF);
            }
            String name = met ? info.name() : "？？？";
            graphics.drawString(font, Component.translatable("screen.amemaru.bestiary.row", info.floor(), name), left + 8, y,
                    met ? UiStyle.TEXT : UiStyle.TEXT_DIM, false);
        }
        renderDetail(graphics, enemies.get(selected), mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderDetail(GuiGraphics graphics, EnemyDef.Info info, int mouseX, int mouseY) {
        int x = left + 106;
        int y = top + 24;
        UiStyle.card(graphics, x - 2, y - 2, left + WIDTH - 6, top + HEIGHT - 24, 0x8FD3FF, false);
        boolean met = met(info.id());
        SyncAdventureS2C.Bestiary record = ClientAdventure.bestiary(info.id());
        if (!met) {
            graphics.drawString(font, "？？？", x + 2, y + 2, UiStyle.TEXT_DIM, true);
            graphics.drawString(font, Component.translatable("screen.amemaru.bestiary.unknown", info.floor()), x + 2, y + 14,
                    UiStyle.TEXT_DIM, false);
        } else {
            LivingEntity preview = preview(info.id());
            if (preview != null) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, left + WIDTH - 36, y + 62, previewScale(preview),
                        (float) (left + WIDTH - 36 - mouseX), (float) (y + 30 - mouseY), preview);
            }
            graphics.drawString(font, info.name(), x + 2, y + 2, info.lost() ? 0xE8F4FF : UiStyle.TEXT, true);
            graphics.drawString(font, Component.translatable("screen.amemaru.bestiary.info", info.floor(), info.level(),
                    record == null ? 0 : record.kills()), x + 2, y + 13, UiStyle.TEXT_GOLD, false);
            if (info.lost()) {
                graphics.drawString(font, Component.translatable("screen.amemaru.bestiary.lost"), x + 2, y + 23, 0xBFEFFF, false);
            }
            UiStyle.wrapped(graphics, font, info.description(), x + 2, y + 34, 128, UiStyle.TEXT_DIM, 4);
        }
        // ドロップ一覧
        int dropY = top + 108;
        graphics.drawString(font, Component.translatable("screen.amemaru.bestiary.drops"), x + 2, dropY, UiStyle.TEXT_HEADER, true);
        dropY += 11;
        List<ResourceLocation> obtained = record == null ? List.of() : record.drops();
        int dx = x + 2;
        for (ResourceLocation id : info.drops()) {
            dx = drawDrop(graphics, id, obtained.contains(id), null, dx, dropY, mouseX, mouseY);
        }
        dropY += 22;
        graphics.drawString(font, Component.translatable("screen.amemaru.bestiary.rare"), x + 2, dropY, 0xC07CFF, true);
        dropY += 11;
        dx = x + 2;
        for (EnemyDef.RareDrop rare : info.rare()) {
            String chance = String.format(Locale.ROOT, rare.chance() < 1.0 ? "%.2f%%" : "%.1f%%", rare.chance());
            dx = drawDrop(graphics, rare.item(), obtained.contains(rare.item()), chance, dx, dropY, mouseX, mouseY);
        }
    }

    /** 品の絵（手に入れていなければ「？」）。マウスを乗せると名前（と確率）。 */
    private int drawDrop(GuiGraphics graphics, ResourceLocation id, boolean obtained, @Nullable String chance, int x, int y,
                         int mouseX, int mouseY) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        graphics.fill(x - 1, y - 1, x + 17, y + 17, obtained ? 0xFF4F6B99 : 0xFF26324D);
        graphics.fill(x, y, x + 16, y + 16, 0xFF0B1022);
        if (obtained && item != null) {
            graphics.renderItem(new ItemStack(item), x, y);
        } else {
            graphics.drawString(font, "?", x + 6, y + 4, UiStyle.TEXT_DIM, false);
        }
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            List<Component> lines = new ArrayList<>();
            lines.add(obtained && item != null ? new ItemStack(item).getHoverName() : Component.literal("？？？"));
            if (chance != null) {
                lines.add(Component.translatable("screen.amemaru.bestiary.chance", chance));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        return x + 20;
    }

    @Nullable
    private LivingEntity preview(ResourceLocation id) {
        if (previews.containsKey(id)) {
            return previews.get(id);
        }
        LivingEntity living = null;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type != null && Minecraft.getInstance().level != null) {
            Entity entity = type.create(Minecraft.getInstance().level);
            if (entity instanceof LivingEntity found) {
                living = found;
            }
        }
        previews.put(id, living);
        return living;
    }

    private static int previewScale(LivingEntity entity) {
        float size = Math.max(entity.getBbWidth(), entity.getBbHeight());
        return Math.max(12, Math.min(34, Math.round(34.0F / Math.max(0.6F, size))));
    }

    private int countMet() {
        int count = 0;
        for (EnemyDef.Info info : enemies) {
            if (met(info.id())) {
                count++;
            }
        }
        return count;
    }

    private static boolean met(ResourceLocation id) {
        SyncAdventureS2C.Bestiary record = ClientAdventure.bestiary(id);
        return record != null && record.kills() > 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < ROWS && scroll + i < enemies.size(); i++) {
            int y = top + 24 + i * ROW;
            if (mouseX >= left + 6 && mouseX < left + 98 && mouseY >= y - 2 && mouseY < y + ROW - 2) {
                selected = scroll + i;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (enemies.size() > ROWS) {
            scroll = Math.max(0, Math.min(enemies.size() - ROWS, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** CIの撮影用：その敵を選ぶ。 */
    public void select(ResourceLocation id) {
        for (int i = 0; i < enemies.size(); i++) {
            if (enemies.get(i).id().equals(id)) {
                selected = i;
                return;
            }
        }
    }
}
