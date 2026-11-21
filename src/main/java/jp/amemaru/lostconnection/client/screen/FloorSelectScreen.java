package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.FloorSelectC2S;
import jp.amemaru.lostconnection.network.s2c.FloorSelectS2C;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * 塔の門で行く階を選ぶ画面（Phase 0-A 7章「塔の門：解放済みの階層を選んで入る。管理者が予告した次の解放の表示」）。
 * 章ごとに縦に5階ずつ並べる。開いていて建ててある階だけ選べる。まだ開いていない階の名前は「？？？」。
 */
public class FloorSelectScreen extends Screen {
    private static final String[] CHAPTERS = {"第1章", "第2章", "第3章", "第4章"};
    private static final int[] CHAPTER_COLORS = {0x9CFFB0, 0x8FE6FF, 0xFFB36B, 0xF2F2F2};
    private static final int CELL_W = 96;
    private static final int CELL_H = 30;

    private final FloorSelectS2C data;
    private final long received = Util.getMillis();

    public FloorSelectScreen(FloorSelectS2C data) {
        super(Component.translatable("screen.amemaru.floor_select"));
        this.data = data;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int x0() {
        return (width - (CELL_W * 4 + 8 * 3)) / 2;
    }

    private int y0() {
        return (height - (CELL_H * 5 + 6 * 4)) / 2 + 10;
    }

    private int[] cell(int floor) {
        int chapter = (floor - 1) / 5;
        int row = (floor - 1) % 5;
        int x = x0() + chapter * (CELL_W + 8);
        int y = y0() + row * (CELL_H + 6);
        return new int[]{x, y, x + CELL_W, y + CELL_H};
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int px0 = x0() - 12;
        int py0 = y0() - 40;
        int px1 = x0() + CELL_W * 4 + 8 * 3 + 12;
        int py1 = y0() + CELL_H * 5 + 6 * 4 + 22;
        UiStyle.panel(graphics, px0, py0, px1, py1);
        graphics.drawString(font, Component.translatable("screen.amemaru.floor_select.title"), px0 + 10, py0 + 8, UiStyle.TEXT_HEADER, false);
        Component day = Component.translatable("screen.amemaru.floor_select.day", data.day());
        graphics.drawString(font, day, px1 - 10 - font.width(day), py0 + 8, UiStyle.TEXT_GOLD, false);
        for (int c = 0; c < 4; c++) {
            graphics.drawString(font, CHAPTERS[c], x0() + c * (CELL_W + 8) + 2, y0() - 12, 0xFF000000 | CHAPTER_COLORS[c], false);
        }
        List<Component> tooltip = null;
        for (FloorSelectS2C.Entry e : data.floors()) {
            if (e.floor() < 1 || e.floor() > 20) {
                continue;
            }
            int[] r = cell(e.floor());
            boolean hover = mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3];
            int accent = CHAPTER_COLORS[(e.floor() - 1) / 5];
            boolean open = e.state() == FloorSelectS2C.State.OPEN;
            UiStyle.card(graphics, r[0], r[1], r[2], r[3], open ? accent : 0x3E5274, open && hover);
            String name = e.name().isEmpty() ? "？？？" : e.name();
            int titleColor = open ? UiStyle.TEXT : UiStyle.TEXT_DIM;
            graphics.drawString(font, e.floor() + "階", r[0] + 5, r[1] + 4, open ? 0xFF000000 | accent : UiStyle.TEXT_DIM, false);
            graphics.drawString(font, UiStyle.clip(font, name, CELL_W - 34), r[0] + 30, r[1] + 4, titleColor, false);
            Component state = switch (e.state()) {
                case OPEN -> e.levelMin() > 0 ? Component.translatable("screen.amemaru.floor_select.level", e.levelMin(), e.levelMax())
                        : Component.translatable("screen.amemaru.floor_select.open");
                case NOT_BUILT -> Component.translatable("screen.amemaru.floor_select.not_built");
                case NEXT -> Component.translatable("screen.amemaru.floor_select.next", countdown(e.nextMillis()));
                case LOCKED -> Component.translatable("screen.amemaru.floor_select.locked");
            };
            int stateColor = switch (e.state()) {
                case OPEN -> UiStyle.TEXT_GOOD;
                case NEXT -> UiStyle.TEXT_GOLD;
                default -> UiStyle.TEXT_DIM;
            };
            graphics.drawString(font, state, r[0] + 5, r[1] + 17, stateColor, false);
            if (!e.firstKill().isEmpty()) {
                graphics.drawString(font, "★", r[2] - 10, r[1] + 17, UiStyle.TEXT_GOLD, false);
            }
            if (hover) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.literal(e.floor() + "階　" + name));
                if (e.levelMin() > 0 && !e.name().isEmpty()) {
                    tooltip.add(Component.translatable("screen.amemaru.floor_select.level", e.levelMin(), e.levelMax()));
                }
                if (!e.firstKill().isEmpty()) {
                    tooltip.add(Component.translatable("screen.amemaru.floor_select.first_kill", e.firstKill()));
                }
                if (open) {
                    tooltip.add(Component.translatable("screen.amemaru.floor_select.click"));
                }
            }
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.floor_select.hint"), px0 + 10, py1 - 14, UiStyle.TEXT_DIM, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tooltip != null) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private String countdown(long millis) {
        long left = Math.max(0L, millis - (Util.getMillis() - received)) / 1000L;
        return String.format(java.util.Locale.ROOT, "%d:%02d", left / 60, left % 60);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (FloorSelectS2C.Entry e : data.floors()) {
            if (e.floor() < 1 || e.floor() > 20) {
                continue;
            }
            int[] r = cell(e.floor());
            if (mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3]) {
                if (e.state() == FloorSelectS2C.State.OPEN) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.8F));
                    ModNetwork.sendToServer(new FloorSelectC2S(e.floor()));
                    onClose();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
