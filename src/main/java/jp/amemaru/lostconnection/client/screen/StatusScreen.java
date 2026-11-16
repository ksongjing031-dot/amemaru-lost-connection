package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientPlayerState;
import jp.amemaru.lostconnection.client.KeyBindings;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.AllocateStatC2S;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ステータス画面（初期値：Kキー）。レベル・経験値・ポイントを確かめ、ステータスポイントを振り分ける。
 *
 * <p>「＋」で1ポイント、Shiftを押しながらで10ポイント振り分ける。振り分けはサーバーが確かめてから反映し、
 * 結果は同期で返ってくる（この画面は受け取った値を表示するだけ）。</p>
 */
public class StatusScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 222;
    private static final int ROW_HEIGHT = 13;
    private static final int STATS_TOP = 70;
    private static final int SUB_TOP = 172;

    private static final int PANEL_COLOR = 0xE0141820;
    private static final int BORDER_COLOR = 0xFF6C7A96;
    private static final int TEXT = 0xFFFFFF;
    private static final int TEXT_DIM = 0xA0A0A0;
    private static final int TEXT_HEADER = 0xC8D2E6;
    private static final int TEXT_POINTS = 0xFFFF55;
    private static final int EXP_TOP = 0xFF9CFF5A;
    private static final int EXP_BOTTOM = 0xFF4CA02A;

    private static final List<StatType> SUB_STATS = List.of(
            StatType.CRIT_DAMAGE, StatType.HEAL_POWER, StatType.COOLDOWN_REDUCTION, StatType.LOST_RESIST);

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
            Button button = Button.builder(Component.literal("+"), pressed -> allocate(type))
                    .bounds(left + PANEL_WIDTH - 30, y - 2, 18, 12)
                    .tooltip(Tooltip.create(Component.translatable("screen.amemaru.status.plus_tooltip")))
                    .build();
            plusButtons.put(type, addRenderableWidget(button));
            y += ROW_HEIGHT;
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, pressed -> onClose())
                .bounds(width / 2 - 50, top + PANEL_HEIGHT - 24, 100, 18)
                .build());
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, BORDER_COLOR);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_COLOR);
        graphics.drawCenteredString(font, title, width / 2, top + 7, TEXT);

        ClientPlayerState state = ClientPlayerState.get();
        if (!state.hasData()) {
            graphics.drawCenteredString(font, Component.translatable("screen.amemaru.status.no_data"),
                    width / 2, top + PANEL_HEIGHT / 2, TEXT_DIM);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        renderGrowth(graphics, state);
        renderStats(graphics, state);
        renderSubStats(graphics, state);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** レベル・スキルポイント・経験値。 */
    private void renderGrowth(GuiGraphics graphics, ClientPlayerState state) {
        int x = left + 12;
        int right = left + PANEL_WIDTH - 12;

        Component level = state.isMaxLevel()
                ? Component.translatable("screen.amemaru.status.level_max", state.level())
                : Component.translatable("screen.amemaru.status.level", state.level());
        graphics.drawString(font, level, x, top + 24, TEXT, true);

        Component skill = Component.translatable("screen.amemaru.status.skill_points", state.skillPoints());
        graphics.drawString(font, skill, right - font.width(skill), top + 24, TEXT_DIM, true);

        Component exp = state.isMaxLevel()
                ? Component.translatable("screen.amemaru.status.exp_max")
                : Component.translatable("screen.amemaru.status.exp", state.exp(), state.expToNext());
        graphics.drawString(font, exp, x, top + 37, TEXT_DIM, true);

        int barY = top + 48;
        int barWidth = right - x;
        graphics.fill(x - 1, barY - 1, right + 1, barY + 6, 0xC0000000);
        graphics.fill(x, barY, right, barY + 5, 0xFF2A2A2A);
        int filled = Math.round(state.expProgress() * barWidth);
        if (filled > 0) {
            graphics.fillGradient(x, barY, x + filled, barY + 5, EXP_TOP, EXP_BOTTOM);
        }
    }

    /** 振り分けられる7つのステータス。 */
    private void renderStats(GuiGraphics graphics, ClientPlayerState state) {
        int x = left + 12;
        Component header = Component.translatable("screen.amemaru.status.points", state.statPoints());
        graphics.drawString(font, Component.translatable("screen.amemaru.status.base_section"), x, top + 58, TEXT_HEADER, true);
        graphics.drawString(font, header, left + PANEL_WIDTH - 12 - font.width(header), top + 58,
                state.statPoints() > 0 ? TEXT_POINTS : TEXT_DIM, true);

        int y = top + STATS_TOP;
        int valueRight = left + 150;
        for (StatType type : StatType.allocatable()) {
            graphics.drawString(font, Component.translatable(type.translationKey()), x + 4, y, TEXT, true);

            String value = type.format(state.stat(type));
            graphics.drawString(font, value, valueRight - font.width(value), y, TEXT, true);

            int points = state.allocated(type);
            if (points > 0) {
                graphics.drawString(font, Component.translatable("screen.amemaru.status.allocated", points),
                        valueRight + 6, y, TEXT_DIM, true);
            }
            y += ROW_HEIGHT;
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
            graphics.drawString(font, value, columnX + columnWidth - 4 - font.width(value), y, TEXT, true);
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
