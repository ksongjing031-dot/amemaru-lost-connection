package jp.amemaru.lostconnection.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * HUDと同じ「結晶と糸」の見た目のボタン。濃い藍色の地に、水色のふち。
 * 押せないときは暗く、マウスを乗せると明るくなる。
 */
public class CrystalButton extends Button {
    private static final int OUTLINE = 0xFF090C14;
    private static final int BORDER = 0xFF4F6B99;
    private static final int BORDER_HOVER = 0xFFAEE8FF;
    private static final int BORDER_OFF = 0xFF27324D;
    private static final int FILL_TOP = 0xFF22345A;
    private static final int FILL_BOTTOM = 0xFF17233F;
    private static final int FILL_HOVER_TOP = 0xFF2F4C80;
    private static final int FILL_HOVER_BOTTOM = 0xFF1F3560;
    private static final int FILL_OFF = 0xFF121828;
    private static final int TEXT = 0xFFFFFF;
    private static final int TEXT_OFF = 0x6E7891;

    public CrystalButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean hover = active && isHoveredOrFocused();
        int border = !active ? BORDER_OFF : (hover ? BORDER_HOVER : BORDER);
        graphics.fill(x + 1, y, x + width - 1, y + height, OUTLINE);
        graphics.fill(x, y + 1, x + width, y + height - 1, OUTLINE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, border);
        if (active) {
            graphics.fillGradient(x + 2, y + 2, x + width - 2, y + height - 2,
                    hover ? FILL_HOVER_TOP : FILL_TOP, hover ? FILL_HOVER_BOTTOM : FILL_BOTTOM);
            // 上のふちの、結晶のきらめき
            graphics.fill(x + 2, y + 2, x + width - 2, y + 3, hover ? 0x60FFFFFF : 0x30FFFFFF);
        } else {
            graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, FILL_OFF);
        }
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(getMessage());
        graphics.drawString(font, getMessage(), x + (width - textWidth) / 2 + 1, y + (height - 8) / 2 + 1,
                active ? TEXT : TEXT_OFF, active);
    }
}
