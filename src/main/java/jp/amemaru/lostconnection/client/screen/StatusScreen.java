package jp.amemaru.lostconnection.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.amemaru.lostconnection.client.AmemaruHudArt;
import jp.amemaru.lostconnection.client.ClientPlayerState;
import jp.amemaru.lostconnection.client.KeyBindings;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
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
 * <p>11月28日のご指示「GUIがごちゃついている（特にKキーで開く画面が見ずらい）」で作り直した【補完】：左の列に成長
 * （レベル・経験値・ツリーポイント・所持金）、右の列にステータス（基本の7つと、装備とアクセサリーで上がる4つ）を並べ、
 * 下に行き先のボタンをそろえる。文字は列の幅で切り、はみ出さない。ステータスはスキルツリー（J）の節で振る
 * （11月24日に決定。この画面は受け取った値を表示するだけ）。</p>
 */
public class StatusScreen extends Screen {
    private static final int MAX_WIDTH = 372;
    private static final int MAX_HEIGHT = 214;
    private static final int ROW_HEIGHT = 12;
    /** 所持金の行（上からの距離）。 */
    private static final int MONEY_Y = 102;

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
        // 11月24日：スキルツリーのステータスの節の印にも使うため、装備で上がる4つの色も決める
        STAT_COLORS.put(StatType.CRIT_DAMAGE, 0xFFFFB45C);
        STAT_COLORS.put(StatType.HEAL_POWER, 0xFF7CF0B0);
        STAT_COLORS.put(StatType.COOLDOWN_REDUCTION, 0xFF8FD3FF);
        STAT_COLORS.put(StatType.LOST_RESIST, 0xFFC8C8D8);
    }

    /** ステータスの色（スキルツリーの節の印でも使う）。 */
    static int statColor(StatType type) {
        return STAT_COLORS.getOrDefault(type, 0xFFFFFFFF);
    }

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    /** 左の列（成長）と右の列（ステータス）の左端と幅。 */
    private int leftX;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    /** 「補助の能力」の見出しの所（マウスを乗せると説明）。 */
    private int subHeaderY;
    /** ツリーポイントの行・覚醒の行の所（マウスを乗せると説明）。 */
    private int pointsTop = -100;
    private int pointsLeftX;
    private int awakenTop = -100;

    public StatusScreen() {
        super(Component.translatable("screen.amemaru.status.title"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_WIDTH, width - 12);
        panelHeight = Math.min(MAX_HEIGHT, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        leftX = left + 12;
        leftWidth = (panelWidth - 36) * 46 / 100;
        rightX = leftX + leftWidth + 12;
        rightWidth = left + panelWidth - 12 - rightX;

        // 下の行：行き先のボタン（同じ幅でそろえる）
        int buttonY = top + panelHeight - 20;
        int gap = 6;
        int buttonWidth = (panelWidth - 24 - gap * 3) / 4;
        int x = left + 12;
        addRenderableWidget(new CrystalButton(x, buttonY, buttonWidth, 14,
                Component.translatable("screen.amemaru.status.skill_tree_key", KeyBindings.OPEN_SKILL_TREE.getTranslatedKeyMessage()), pressed -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new SkillTreeScreen());
                    }
                }));
        x += buttonWidth + gap;
        // Phase 3：アクセサリーの枠（サーバーに頼んで開く）と図鑑
        addRenderableWidget(new CrystalButton(x, buttonY, buttonWidth, 14,
                Component.translatable("screen.amemaru.status.accessory"),
                pressed -> ModNetwork.sendToServer(new jp.amemaru.lostconnection.network.c2s.AccessoryActionC2S(
                        jp.amemaru.lostconnection.network.c2s.AccessoryActionC2S.Action.OPEN, 0, false))));
        x += buttonWidth + gap;
        addRenderableWidget(new CrystalButton(x, buttonY, buttonWidth, 14,
                Component.translatable("screen.amemaru.status.bestiary"), pressed -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new BestiaryScreen());
                    }
                }));
        x += buttonWidth + gap;
        addRenderableWidget(new CrystalButton(x, buttonY, left + panelWidth - 12 - x, 14,
                Component.translatable("screen.amemaru.status.close"), pressed -> onClose()));
        // Phase 6：ノットを渡す（11月22日に決定。所持金の行の右）
        CrystalButton give = new CrystalButton(leftX + leftWidth - 40, top + MONEY_Y - 3, 40, 13,
                Component.translatable("screen.amemaru.status.give_knots"), pressed -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new TransferScreen(null, null));
                    }
                });
        give.setTooltip(Tooltip.create(Component.translatable("screen.amemaru.status.give_knots_tooltip")));
        addRenderableWidget(give);
        // Phase 6 その3：称号の一覧（右上の角。持っている称号から一つ選んで表示する）
        CrystalButton titles = new CrystalButton(left + panelWidth - 56, top + 4, 48, 13, Component.translatable("screen.amemaru.status.titles"),
                pressed -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new TitleListScreen(this));
                    }
                });
        titles.setTooltip(Tooltip.create(Component.translatable("screen.amemaru.status.titles_tooltip")));
        addRenderableWidget(titles);
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
        graphics.drawCenteredString(font, heading, left + (panelWidth - 56) / 2 + 28, top + 7, TEXT_HEADER);
        drawDivider(graphics, left + 12, left + panelWidth - 12, top + 21);

        if (!state.hasData()) {
            graphics.drawCenteredString(font, Component.translatable("screen.amemaru.status.no_data"),
                    width / 2, top + panelHeight / 2, TEXT_DIM);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        // 2つの列のあいだの細い線
        graphics.fill(rightX - 7, top + 28, rightX - 6, top + panelHeight - 28, 0x303E5274);

        awakenTop = -100;
        renderGrowth(graphics, state);
        renderStats(graphics, state);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHints(graphics, state, mouseX, mouseY);
    }

    /** マウスを乗せた所の説明（ツリーポイント・補助の能力・覚醒）。 */
    private void renderHints(GuiGraphics graphics, ClientPlayerState state, int mouseX, int mouseY) {
        String text = null;
        if (mouseY >= pointsTop && mouseY < pointsTop + 10 && mouseX >= pointsLeftX && mouseX < leftX + leftWidth) {
            text = Component.translatable("screen.amemaru.status.points_hint", KeyBindings.OPEN_SKILL_TREE.getTranslatedKeyMessage()).getString();
        } else if (mouseY >= subHeaderY - 1 && mouseY < subHeaderY + 9 && mouseX >= rightX && mouseX < rightX + rightWidth) {
            text = Component.translatable("screen.amemaru.status.sub_tooltip").getString();
        } else if (mouseY >= awakenTop && mouseY < awakenTop + 10 && mouseX >= leftX && mouseX < leftX + leftWidth) {
            jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.JobInfo job =
                    jp.amemaru.lostconnection.client.ClientDefinitions.get().job(state.jobIndex());
            if (job != null) {
                StringBuilder builder = new StringBuilder(Component.translatable("screen.amemaru.skill_tree.awaken").getString());
                for (jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.AwakeningInfo awakening : job.awakenings()) {
                    boolean on = state.level() >= awakening.level();
                    builder.append('\n').append(Component.translatable(on ? "screen.amemaru.skill_tree.awaken_on"
                            : "screen.amemaru.skill_tree.awaken_off", awakening.level(), awakening.name()).getString());
                }
                text = builder.toString();
            }
        }
        if (text != null) {
            graphics.renderTooltip(font, UiStyle.tooltipLines(font, text, 220), mouseX, mouseY);
        }
    }

    /** パネル：濃い藍色の地、二重のふち、四隅の小さな結晶。 */
    private void drawPanel(GuiGraphics graphics) {
        int x0 = left;
        int y0 = top;
        int x1 = left + panelWidth;
        int y1 = top + panelHeight;
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
    private void drawDivider(GuiGraphics graphics, int x0, int x1, int y) {
        int center = (x0 + x1) / 2;
        int step = Math.max(4, (x1 - x0) / 12);
        for (int i = 0; i < 5; i++) {
            int half = (x1 - x0) / 2 - i * step;
            if (half <= 0) {
                break;
            }
            int alpha = 0x22 + i * 0x18;
            graphics.fill(center - half, y, center + half, y + 1, (alpha << 24) | (BORDER_LIGHT & 0xFFFFFF));
        }
        graphics.fill(center - 1, y - 1, center + 1, y + 2, 0xFFBFEFFF);
    }

    /** 小さな見出し（文字と、その右へ伸びる細い線）。 */
    private void drawHeader(GuiGraphics graphics, Component text, int x, int y, int columnWidth) {
        String shown = UiStyle.clip(font, text.getString(), columnWidth);
        graphics.drawString(font, shown, x, y, TEXT_HEADER, false);
        int lineX = x + font.width(shown) + 4;
        if (lineX < x + columnWidth) {
            graphics.fill(lineX, y + 4, x + columnWidth, y + 5, 0x403E5274);
        }
    }

    /** 左の列：レベルの結晶・レベル・ツリーポイント・経験値のバー・所持金・職業。 */
    private void renderGrowth(GuiGraphics graphics, ClientPlayerState state) {
        int x = leftX;
        int right = leftX + leftWidth;
        int gemX = x;
        int gemY = top + 27;
        boolean pointsLeft = state.skillPoints() > 0;
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

        // レベルと、ツリーポイント（残っていれば金色。マウスを乗せると使い道）
        int textX = gemX + AmemaruHudArt.GEM_W + 8;
        Component levelText = state.isMaxLevel()
                ? Component.translatable("screen.amemaru.status.level_max", state.level())
                : Component.translatable("screen.amemaru.status.level", state.level());
        graphics.drawString(font, UiStyle.clip(font, levelText.getString(), right - textX), textX, gemY + 4, TEXT, true);
        Component points = Component.translatable("screen.amemaru.status.skill_points", state.skillPoints());
        graphics.drawString(font, UiStyle.clip(font, points.getString(), right - textX), textX, gemY + 16,
                pointsLeft ? TEXT_POINTS : TEXT_DIM, false);
        pointsTop = gemY + 15;
        pointsLeftX = textX;

        // 経験値：見出しと数・バー・次のレベルまで
        int expY = top + 64;
        graphics.drawString(font, Component.translatable("screen.amemaru.status.exp_label"), x, expY, TEXT_DIM, false);
        String numbers = state.isMaxLevel() ? "―"
                : String.format(java.util.Locale.ROOT, "%,d / %,d", state.exp(), state.expToNext());
        graphics.drawString(font, numbers, right - font.width(numbers), expY, TEXT, false);
        int barY = expY + 11;
        graphics.fill(x, barY, right, barY + 7, 0xFF0A0F1C);
        graphics.fill(x + 1, barY + 1, right - 1, barY + 6, 0xFF1C2742);
        float progress = state.isMaxLevel() ? 1.0F : Mth.clamp(state.expProgress(), 0.0F, 1.0F);
        int filled = Math.round((right - x - 2) * progress);
        if (filled > 0) {
            graphics.fillGradient(x + 1, barY + 1, x + 1 + filled, barY + 6, 0xFFFFE27A, 0xFFE0A030);
            graphics.fill(x + 1, barY + 1, x + 1 + filled, barY + 2, 0x80FFFFFF);
        }
        Component under = state.isMaxLevel()
                ? Component.translatable("screen.amemaru.status.exp_max_hint")
                : Component.translatable("screen.amemaru.status.next", Math.max(0, state.expToNext() - state.exp()));
        String underText = UiStyle.clip(font, under.getString(), leftWidth);
        graphics.drawString(font, underText, right - font.width(underText), barY + 11, TEXT_DIM, false);

        // 所持金（Phase 4）。右に「渡す」のボタン
        int moneyY = top + MONEY_Y;
        Component moneyLabel = Component.translatable("screen.amemaru.status.money_label");
        graphics.drawString(font, moneyLabel, x, moneyY, TEXT_DIM, false);
        String money = Component.translatable("screen.amemaru.status.money_value",
                String.format(java.util.Locale.ROOT, "%,d", state.knots())).getString();
        int moneyX = x + font.width(moneyLabel) + 6;
        graphics.drawString(font, UiStyle.clip(font, money, right - 44 - moneyX), moneyX, moneyY, TEXT_POINTS, true);

        renderJob(graphics, state, x, right, top + 120);
    }

    /** 左の列の下：職業（上位職と覚醒も）。 */
    private void renderJob(GuiGraphics graphics, ClientPlayerState state, int x, int right, int y) {
        jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.JobInfo job =
                jp.amemaru.lostconnection.client.ClientDefinitions.get().job(state.jobIndex());
        drawHeader(graphics, Component.translatable("screen.amemaru.status.job_section"), x, y, leftWidth);
        y += 12;
        if (job == null || !state.hasJob()) {
            graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.status.no_job").getString(), leftWidth), x, y,
                    TEXT_DIM, false);
            return;
        }
        // 印・名前（上位職になっていれば上位職の名前）・役割
        RenderSystem.enableBlend();
        graphics.blit(job.icon(), x, y - 2, 12, 12, 0, 0, 64, 64, 64, 64);
        String role = job.role();
        int roleX = right - font.width(role);
        String name = state.promoted() && job.hasUpper() ? job.upperName() : job.name();
        graphics.drawString(font, UiStyle.clip(font, name, roleX - 6 - (x + 16)), x + 16, y, 0xFF000000 | job.color(), false);
        graphics.drawString(font, role, roleX, y, TEXT_DIM, false);
        y += 12;
        if (job.hasUpper()) {
            Component upper = state.promoted()
                    ? Component.translatable("screen.amemaru.status.upper_done", job.upperName())
                    : Component.translatable("screen.amemaru.status.upper_todo", job.upperName(), job.upperMinDay(), job.upperMinLevel());
            graphics.drawString(font, UiStyle.clip(font, upper.getString(), leftWidth), x, y, state.promoted() ? TEXT_POINTS : TEXT_DIM, false);
            y += 12;
        }
        if (!job.awakenings().isEmpty()) {
            // 覚醒：目覚めたものは金色の◆、まだのものは暗い◇（入りきらない分は「…」）
            Component label = Component.translatable("screen.amemaru.status.awaken_label");
            graphics.drawString(font, label, x, y, TEXT_DIM, false);
            int cursor = x + font.width(label) + 4;
            for (int i = 0; i < job.awakenings().size(); i++) {
                jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.AwakeningInfo awakening = job.awakenings().get(i);
                boolean on = state.level() >= awakening.level();
                String part = (on ? "◆" : "◇") + awakening.name();
                boolean last = i == job.awakenings().size() - 1;
                int room = right - cursor;
                if (font.width(part) > room || (!last && font.width(part) + font.width(" …") > room)) {
                    graphics.drawString(font, UiStyle.clip(font, part, room), cursor, y, on ? TEXT_POINTS : TEXT_DIM, false);
                    break;
                }
                graphics.drawString(font, part, cursor, y, on ? TEXT_POINTS : TEXT_DIM, false);
                cursor += font.width(part) + 5;
            }
            awakenTop = y - 1;
        }
    }

    /** 右の列：基本の7つと、装備とアクセサリーで上がる4つ（どちらも名前は左、数は右にそろえる）。 */
    private void renderStats(GuiGraphics graphics, ClientPlayerState state) {
        int x = rightX;
        int right = rightX + rightWidth;
        drawHeader(graphics, Component.translatable("screen.amemaru.status.base_section"), x, top + 27, rightWidth);
        int y = top + 39;
        int row = 0;
        for (StatType type : StatType.allocatable()) {
            drawStatRow(graphics, state, type, x, right, y, row++);
            y += ROW_HEIGHT;
        }
        subHeaderY = y + 5;
        drawHeader(graphics, Component.translatable("screen.amemaru.status.sub_short"), x, subHeaderY, rightWidth);
        y = subHeaderY + 12;
        row = 0;
        for (StatType type : SUB_STATS) {
            drawStatRow(graphics, state, type, x, right, y, row++);
            y += ROW_HEIGHT;
        }
    }

    private void drawStatRow(GuiGraphics graphics, ClientPlayerState state, StatType type, int x, int right, int y, int row) {
        if (row % 2 == 0) {
            graphics.fill(x - 2, y - 2, right + 2, y + ROW_HEIGHT - 2, ROW_STRIPE);
        }
        // 小さな結晶の印（ステータスの色）
        int color = STAT_COLORS.getOrDefault(type, 0xFFFFFFFF);
        graphics.fill(x + 2, y + 2, x + 4, y + 6, color);
        graphics.fill(x + 1, y + 3, x + 5, y + 5, color);
        String value = type.format(state.stat(type));
        int valueX = right - font.width(value);
        String label = UiStyle.clip(font, Component.translatable(type.translationKey()).getString(), valueX - 6 - (x + 9));
        graphics.drawString(font, label, x + 9, y, TEXT, false);
        graphics.drawString(font, value, valueX, y, TEXT, false);
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
