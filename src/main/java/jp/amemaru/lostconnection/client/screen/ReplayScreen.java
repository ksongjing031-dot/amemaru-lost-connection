package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.ReplayMemoryC2S;
import jp.amemaru.lostconnection.network.s2c.ReplayListS2C;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 大結晶の「記憶の再生」（Phase 0-A 14章：見逃したストーリーイベントの演出を、自分の画面だけで再生する）。
 * 本人が見たものと、サーバーで起きたものが並ぶ。選ぶと、その演出が自分の画面だけで流れる。
 */
public class ReplayScreen extends Screen {
    private static final int ROW_H = 22;
    private final ReplayListS2C data;

    public ReplayScreen(ReplayListS2C data) {
        super(Component.translatable("screen.amemaru.replay"));
        this.data = data;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int[] panel() {
        int w = 260;
        int rows = Math.max(1, data.entries().size());
        int h = 40 + rows * ROW_H + 18;
        int x0 = (width - w) / 2;
        int y0 = (height - h) / 2;
        return new int[]{x0, y0, x0 + w, y0 + h};
    }

    private int[] row(int i) {
        int[] p = panel();
        int y = p[1] + 30 + i * ROW_H;
        return new int[]{p[0] + 10, y, p[2] - 10, y + ROW_H - 4};
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int[] p = panel();
        UiStyle.panel(graphics, p[0], p[1], p[2], p[3]);
        graphics.drawString(font, Component.translatable("screen.amemaru.replay.title"), p[0] + 10, p[1] + 8, UiStyle.TEXT_HEADER, false);
        UiStyle.divider(graphics, p[0] + 8, p[2] - 8, p[1] + 21);
        if (data.entries().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.replay.none"), p[0] + 14, p[1] + 34, UiStyle.TEXT_DIM, false);
        }
        for (int i = 0; i < data.entries().size(); i++) {
            int[] r = row(i);
            boolean hover = mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3];
            UiStyle.card(graphics, r[0], r[1], r[2], r[3], hover ? 0xFFF2A8 : 0x8FD3FF, hover);
            graphics.drawString(font, UiStyle.clip(font, data.entries().get(i).title(), r[2] - r[0] - 16), r[0] + 8, r[1] + 5,
                    hover ? UiStyle.TEXT_GOLD : UiStyle.TEXT, false);
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.replay.hint"), p[0] + 10, p[3] - 13, UiStyle.TEXT_DIM, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < data.entries().size(); i++) {
            int[] r = row(i);
            if (mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3]) {
                ModNetwork.sendToServer(new ReplayMemoryC2S(data.entries().get(i).event()));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
