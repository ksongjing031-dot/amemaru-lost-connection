package jp.amemaru.lostconnection.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.amemaru.lostconnection.client.AmemaruHudArt;
import jp.amemaru.lostconnection.client.ClientPlayerState;
import jp.amemaru.lostconnection.client.KeyBindings;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.AllocateStatC2S;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ステータス画面（初期値：Kキー）。HUDと同じ「結晶と糸」のデザイン。
 *
 * <p>レベル・経験値・ポイントを確かめ、ステータスポイントを振り分ける。「＋」で1ポイント、
 * Shiftを押しながらで10ポイント。振り分けはサーバーが確かめてから反映し、結果は同期で返ってくる
 * （この画面は受け取った値を表示するだけ）。</p>
 */
public class StatusScreen extends Screen {
    private static final int PANEL_WIDTH = 248;
    private static final int PANEL_HEIGHT = 236;
    private static final int ROW_HEIGHT = 13;
    private static final int STATS_TOP = 73;
    private static final int SUB_TOP = 181;
    private static final int VALUE_RIGHT = 160;

    private static final int OUTLINE = 0xFF090C14;
    private static final int BORDER = 0xFF3E5274;
    private static final int BORDER_LIGHT = 0xFF8FD3FF;
    private static final int PANEL_TOP = 0xF2131B33;
    private static final int PANEL_BOTTOM = 0xF20B1022;
    private static final int ROW_STRIPE = 0x12FFFFFF;
    private static final int TEXT = 0xFFFFFF;
    private static final int TEXT_DIM = 0x9AA6BF;
    private static final int TEXT_HEADER = 0xBFEFFF;
    private static final int TEXT_POINTS = 0xFFF2A8;

    private static final List<StatType> SUB_STATS = List.of(
            StatType.CRIT_DAMAGE, StatType.HEAL_POWER, StatType.COOLDOWN_REDUCTION, StatType.LOST_RESIST);

    /** ステータスごとの小さな印の色（HUDのバーの色と合わせる）。 */
    private static final Map<StatType, Integer> STAT_COLORS = new EnumMap<>(StatType.class);

    static {
        STAT_COLORS.put(StatType.HP, 0xFF52DC6E);
        STAT_COLORS.put(StatType.MP, 0xFF5AAEF8);
        STAT_COLORS.put(StatType.ATTACK, 0xFFF06A4A);
        STAT_COLORS.put(StatType.MAGIC, 0xFFB57CFF);
        STAT_COLORS.put(StatType.DEFENSE, 0xFFE0B04A);
        STAT_COLORS.put(StatType.SPEED, 0xFFE8F4FF);
        STAT_COLORS.put(StatType.CRIT, 0xFFFFE066);
    }

    private final Map<StatType, Button> plusButtons = new EnumMap<>(StatType.class);
    private int left;
    private int top;

    public StatusScreen() {
        super(Component.translatable("screen.amemaru.status.title"));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        plusButtons.clear();

        int y = top + STATS_TOP;
        for (StatType type : StatType.allocatable()) {
            CrystalButton button = new CrystalButton(left + PANEL_WIDTH - 34, y - 1, 20, 12,
                    Component.literal("+"), pressed -> allocate(type));
            button.setTooltip(Tooltip.create(Component.translatable("screen.amemaru.status.plus_tooltip")));
            plusButtons.put(type, addRenderableWidget(button));
            y += ROW_HEIGHT;
        }

        int buttonY = top + PANEL_HEIGHT - 20;
        addRenderableWidget(new CrystalButton(left + 12, buttonY, 64, 16,
                Component.translatable("screen.amemaru.status.skill_tree"), pressed -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new SkillTreeScreen());
                    }
                }));
        // Phase 3：アクセサリーの枠（サーバーに頼んで開く）と図鑑
        addRenderableWidget(new CrystalButton(left + 80, buttonY, 64, 16,
                Component.translatable("screen.amemaru.status.accessory"),
                pressed -> ModNetwork.sendToServer(new jp.amemaru.lostconnection.network.c2s.AccessoryActionC2S(
                        jp.amemaru.lostconnection.network.c2s.AccessoryActionC2S.Action.OPEN, 0, false))));
        addRenderableWidget(new CrystalButton(left + 148, buttonY, 42, 16,
                Component.translatable("screen.amemaru.status.bestiary"), pressed -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new BestiaryScreen());
                    }
                }));
        addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 54, buttonY, 42, 16,
                Component.translatable("screen.amemaru.status.close"), pressed -> onClose()));
        // Phase 6：ノットを渡す（11月22日に決定。所持金の行の右）
        CrystalButton give = new CrystalButton(left + PANEL_WIDTH - 54, top + SUB_TOP + 23, 42, 11,
                Component.translatable("screen.amemaru.status.give_knots"), pressed -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new TransferScreen(null, null));
                    }
                });
        give.setTooltip(Tooltip.create(Component.translatable("screen.amemaru.status.give_knots_tooltip")));
        addRenderableWidget(give);
        updateButtons();
    }

    private void allocate(StatType type) {
        int amount = hasShiftDown() ? AllocateStatC2S.MAX_PER_REQUEST : 1;
        ModNetwork.sendToServer(new AllocateStatC2S(type, amount));
    }

    @Override
    public void tick() {
        super.tick();
        updateButtons();
    }

    private void updateButtons() {
        ClientPlayerState state = ClientPlayerState.get();
        boolean canAllocate = state.hasData() && state.statPoints() > 0;
        for (Button button : plusButtons.values()) {
            button.active = canAllocate;
        }
    }

    // ---------------------------------------------------------------- 描画

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanel(graphics);

        ClientPlayerState state = ClientPlayerState.get();
        jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.JobInfo job =
                jp.amemaru.lostconnection.client.ClientDefinitions.get().job(state.jobIndex());
        Component heading = job == null ? title : Component.translatable("screen.amemaru.status.title_job", job.name());
        graphics.drawCenteredString(font, heading, width / 2, top + 7, TEXT_HEADER);
        drawDivider(graphics, top + 19);

        if (!state.hasData()) {
            graphics.drawCenteredString(font, Component.translatable("screen.amemaru.status.no_data"),
                    width / 2, top + PANEL_HEIGHT / 2, TEXT_DIM);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        renderGrowth(graphics, state);
        renderStats(graphics, state);
        renderSubStats(graphics, state);
        // 所持金（Phase 4）
        Component money = Component.translatable("screen.amemaru.status.money",
                String.format(java.util.Locale.ROOT, "%,d", state.knots()));
        graphics.drawString(font, money, left + 16, top + SUB_TOP + 25, TEXT_POINTS, true);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** パネル：濃い藍色の地、二重のふち、四隅の小さな結晶。 */
    private void drawPanel(GuiGraphics graphics) {
        int x0 = left;
        int y0 = top;
        int x1 = left + PANEL_WIDTH;
        int y1 = top + PANEL_HEIGHT;
        graphics.fill(x0 - 2, y0 - 1, x1 + 2, y1 + 1, OUTLINE);
        graphics.fill(x0 - 1, y0 - 2, x1 + 1, y1 + 2, OUTLINE);
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, BORDER);
        graphics.fillGradient(x0, y0, x1, y1, PANEL_TOP, PANEL_BOTTOM);
        // 上のふちに、水色の光の線（中央ほど明るい）
        int center = (x0 + x1) / 2;
        for (int i = 0; i < 6; i++) {
            int half = (x1 - x0) / 2 - i * 18;
            int alpha = 0x28 + i * 0x16;
            graphics.fill(center - half, y0 - 1, center + half, y0, (alpha << 24) | (BORDER_LIGHT & 0xFFFFFF));
        }
        // 四隅の結晶
        RenderSystem.enableBlend();
        int size = AmemaruHudArt.DIAMOND_SIZE;
        graphics.blit(AmemaruHudArt.TEXTURE, x0 - 5, y0 - 5, AmemaruHudArt.U_DIAMOND, AmemaruHudArt.V_DIAMOND, size, size, 256, 256);
        graphics.blit(AmemaruHudArt.TEXTURE, x1 - 4, y0 - 5, AmemaruHudArt.U_DIAMOND, AmemaruHudArt.V_DIAMOND, size, size, 256, 256);
        graphics.blit(AmemaruHudArt.TEXTURE, x0 - 5, y1 - 4, AmemaruHudArt.U_DIAMOND, AmemaruHudArt.V_DIAMOND, size, size, 256, 256);
        graphics.blit(AmemaruHudArt.TEXTURE, x1 - 4, y1 - 4, AmemaruHudArt.U_DIAMOND, AmemaruHudArt.V_DIAMOND, size, size, 256, 256);
    }

    /** 細い区切りの線（糸のように、中央から両端へ薄くなる）。 */
    private void drawDivider(GuiGraphics graphics, int y) {
        int x0 = left + 12;
        int x1 = left + PANEL_WIDTH - 12;
        int center = (x0 + x1) / 2;
        for (int i = 0; i < 5; i++) {
            int half = (x1 - x0) / 2 - i * 20;
            int alpha = 0x22 + i * 0x18;
            graphics.fill(center - half, y, center + half, y + 1, (alpha << 24) | (BORDER_LIGHT & 0xFFFFFF));
        }
        graphics.fill(center - 1, y - 1, center + 1, y + 2, 0xFFBFEFFF);
    }

    /** レベルの結晶・レベル・スキルポイント・経験値のバー。 */
    private void renderGrowth(GuiGraphics graphics, ClientPlayerState state) {
        int gemX = left + 14;
        int gemY = top + 24;
        boolean pointsLeft = state.statPoints() > 0;
        RenderSystem.enableBlend();
        if (pointsLeft) {
            float pulse = 0.45F + 0.55F * (0.5F + 0.5F * Mth.sin(Util.getMillis() / 1000.0F * 4.4F));
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, pulse);
            graphics.blit(AmemaruHudArt.TEXTURE, gemX - 2, gemY - 2, AmemaruHudArt.U_GEM_GLOW, AmemaruHudArt.V_GEM_GLOW,
                    AmemaruHudArt.GEM_GLOW_W, AmemaruHudArt.GEM_GLOW_H, 256, 256);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        graphics.blit(AmemaruHudArt.TEXTURE, gemX, gemY, pointsLeft ? AmemaruHudArt.U_GEM_LIT : AmemaruHudArt.U_GEM, 0,
                AmemaruHudArt.GEM_W, AmemaruHudArt.GEM_H, 256, 256);
        String level = Integer.toString(state.level());
        int levelX = gemX + AmemaruHudArt.GEM_W / 2 - font.width(level) / 2 + 1;
        int levelY = gemY + AmemaruHudArt.GEM_HEX_TOP + 6;
        graphics.drawString(font, level, levelX + 1, levelY, 0x0A0F1C, false);
        graphics.drawString(font, level, levelX - 1, levelY, 0x0A0F1C, false);
        graphics.drawString(font, level, levelX, levelY + 1, 0x0A0F1C, false);
        graphics.drawString(font, level, levelX, levelY - 1, 0x0A0F1C, false);
        graphics.drawString(font, level, levelX, levelY, pointsLeft ? TEXT_POINTS : TEXT, false);

        int x = left + 46;
        int right = left + PANEL_WIDTH - 14;
        Component levelText = state.isMaxLevel()
                ? Component.translatable("screen.amemaru.status.level_max", state.level())
                : Component.translatable("screen.amemaru.status.level", state.level());
        graphics.drawString(font, levelText, x, top + 27, TEXT, true);
        Component skill = Component.translatable("screen.amemaru.status.skill_points", state.skillPoints());
        graphics.drawString(font, skill, right - font.width(skill), top + 27, TEXT_DIM, true);

        // 経験値のバー（HUDと同じ絵）
        int barY = top + 39;
        graphics.blit(AmemaruHudArt.TEXTURE, x, barY, 0, AmemaruHudArt.V_EXP_FRAME, AmemaruHudArt.EXP_W, AmemaruHudArt.EXP_H, 256, 256);
        int filled = Math.round(state.expProgress() * AmemaruHudArt.EXP_INNER);
        if (filled > 0) {
            graphics.blit(AmemaruHudArt.TEXTURE, x, barY, 0, AmemaruHudArt.V_EXP_FILL, 1 + filled, AmemaruHudArt.EXP_H, 256, 256);
        }
        Component exp = state.isMaxLevel()
                ? Component.translatable("screen.amemaru.status.exp_max")
                : Component.translatable("screen.amemaru.status.exp", state.exp(), state.expToNext());
        graphics.drawString(font, exp, x, top + 47, TEXT_DIM, true);
        if (!state.isMaxLevel()) {
            Component next = Component.translatable("screen.amemaru.status.next", Math.max(0, state.expToNext() - state.exp()));
            graphics.drawString(font, next, right - font.width(next), top + 47, TEXT_DIM, true);
        }
    }

    /** 振り分けられる7つのステータス。 */
    private void renderStats(GuiGraphics graphics, ClientPlayerState state) {
        int x = left + 12;
        int right = left + PANEL_WIDTH - 12;
        graphics.drawString(font, Component.translatable("screen.amemaru.status.base_section"), x, top + 60, TEXT_HEADER, true);
        Component points = Component.translatable("screen.amemaru.status.points", state.statPoints());
        boolean pointsLeft = state.statPoints() > 0;
        int badgeRight = right - 2;
        int badgeLeft = badgeRight - font.width(points) - 8;
        graphics.fill(badgeLeft, top + 58, badgeRight, top + 69, pointsLeft ? 0xC0403A10 : 0x80202638);
        graphics.fill(badgeLeft, top + 58, badgeRight, top + 59, pointsLeft ? 0xFFE8C860 : 0xFF3E4A66);
        graphics.drawString(font, points, badgeLeft + 4, top + 60, pointsLeft ? TEXT_POINTS : TEXT_DIM, true);

        int y = top + STATS_TOP;
        int row = 0;
        for (StatType type : StatType.allocatable()) {
            if (row % 2 == 0) {
                graphics.fill(x, y - 1, right, y + ROW_HEIGHT - 1, ROW_STRIPE);
            }
            // 小さな結晶の印（ステータスの色）
            int color = STAT_COLORS.getOrDefault(type, 0xFFFFFFFF);
            graphics.fill(x + 5, y + 3, x + 7, y + 7, color);
            graphics.fill(x + 4, y + 4, x + 8, y + 6, color);

            graphics.drawString(font, Component.translatable(type.translationKey()), x + 12, y + 2, TEXT, true);
            String value = type.format(state.stat(type));
            graphics.drawString(font, value, left + VALUE_RIGHT - font.width(value), y + 2, TEXT, true);
            int points2 = state.allocated(type);
            if (points2 > 0) {
                graphics.drawString(font, Component.translatable("screen.amemaru.status.allocated", points2),
                        left + VALUE_RIGHT + 4, y + 2, TEXT_DIM, true);
            }
            y += ROW_HEIGHT;
            row++;
        }
    }

    /** 装備とアクセサリーで上がる4つ（2列に並べる）。 */
    private void renderSubStats(GuiGraphics graphics, ClientPlayerState state) {
        int x = left + 12;
        graphics.drawString(font, Component.translatable("screen.amemaru.status.sub_section"), x, top + SUB_TOP - 12,
                TEXT_HEADER, true);
        int columnWidth = (PANEL_WIDTH - 24) / 2;
        for (int i = 0; i < SUB_STATS.size(); i++) {
            StatType type = SUB_STATS.get(i);
            int columnX = x + 4 + (i % 2) * columnWidth;
            int y = top + SUB_TOP + (i / 2) * 11;
            Component label = Component.translatable(type.translationKey());
            String value = type.format(state.stat(type));
            graphics.drawString(font, label, columnX, y, TEXT_DIM, true);
            graphics.drawString(font, value, columnX + columnWidth - 8 - font.width(value), y, TEXT, true);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyBindings.OPEN_STATUS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
