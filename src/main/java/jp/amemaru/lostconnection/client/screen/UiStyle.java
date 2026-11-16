package jp.amemaru.lostconnection.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.amemaru.lostconnection.client.AmemaruHudArt;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 画面の共通の見た目（「結晶と糸」：濃い藍色の地、二重のふち、四隅の小さな結晶、糸のような区切り）。
 */
public final class UiStyle {
    public static final int OUTLINE = 0xFF090C14;
    public static final int BORDER = 0xFF3E5274;
    public static final int BORDER_LIGHT = 0xFF8FD3FF;
    public static final int PANEL_TOP = 0xF2131B33;
    public static final int PANEL_BOTTOM = 0xF20B1022;
    public static final int CARD_TOP = 0xE01A2747;
    public static final int CARD_BOTTOM = 0xE0111A33;
    public static final int TEXT = 0xFFFFFF;
    public static final int TEXT_DIM = 0x9AA6BF;
    public static final int TEXT_HEADER = 0xBFEFFF;
    public static final int TEXT_GOLD = 0xFFF2A8;
    public static final int TEXT_BAD = 0xFF8A8A;
    public static final int TEXT_GOOD = 0x9CFFB0;

    private UiStyle() {
    }

    /** パネル：濃い藍色の地、二重のふち、上のふちの光、四隅の結晶。 */
    public static void panel(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0 - 2, y0 - 1, x1 + 2, y1 + 1, OUTLINE);
        graphics.fill(x0 - 1, y0 - 2, x1 + 1, y1 + 2, OUTLINE);
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, BORDER);
        graphics.fillGradient(x0, y0, x1, y1, PANEL_TOP, PANEL_BOTTOM);
        int center = (x0 + x1) / 2;
        for (int i = 0; i < 6; i++) {
            int half = (x1 - x0) / 2 - i * 18;
            if (half <= 0) {
                break;
            }
            int alpha = 0x28 + i * 0x16;
            graphics.fill(center - half, y0 - 1, center + half, y0, (alpha << 24) | (BORDER_LIGHT & 0xFFFFFF));
        }
        RenderSystem.enableBlend();
        int size = AmemaruHudArt.DIAMOND_SIZE;
        graphics.blit(AmemaruHudArt.TEXTURE, x0 - 5, y0 - 5, AmemaruHudArt.U_DIAMOND, AmemaruHudArt.V_DIAMOND, size, size, 256, 256);
        graphics.blit(AmemaruHudArt.TEXTURE, x1 - 4, y0 - 5, AmemaruHudArt.U_DIAMOND, AmemaruHudArt.V_DIAMOND, size, size, 256, 256);
        graphics.blit(AmemaruHudArt.TEXTURE, x0 - 5, y1 - 4, AmemaruHudArt.U_DIAMOND, AmemaruHudArt.V_DIAMOND, size, size, 256, 256);
        graphics.blit(AmemaruHudArt.TEXTURE, x1 - 4, y1 - 4, AmemaruHudArt.U_DIAMOND, AmemaruHudArt.V_DIAMOND, size, size, 256, 256);
    }

    /** カード（パネルの中の小さな区切り）。{@code accent} はふちの色。 */
    public static void card(GuiGraphics graphics, int x0, int y0, int x1, int y1, int accent, boolean highlighted) {
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, highlighted ? (0xFF000000 | accent) : 0xFF26324D);
        graphics.fillGradient(x0, y0, x1, y1, CARD_TOP, CARD_BOTTOM);
        graphics.fill(x0, y0, x1, y0 + 1, (0x80 << 24) | (accent & 0xFFFFFF));
    }

    /** 細い区切りの線（糸のように、中央から両端へ薄くなる）。 */
    public static void divider(GuiGraphics graphics, int x0, int x1, int y) {
        int center = (x0 + x1) / 2;
        for (int i = 0; i < 5; i++) {
            int half = (x1 - x0) / 2 - i * 20;
            if (half <= 0) {
                break;
            }
            int alpha = 0x22 + i * 0x18;
            graphics.fill(center - half, y, center + half, y + 1, (alpha << 24) | (BORDER_LIGHT & 0xFFFFFF));
        }
        graphics.fill(center - 1, y - 1, center + 1, y + 2, 0xFFBFEFFF);
    }

    /** 文を幅で折り返して描く。描いた行の数を返す。 */
    public static int wrapped(GuiGraphics graphics, Font font, String text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(FormattedText.of(text), width);
        int count = 0;
        for (FormattedCharSequence line : lines) {
            if (count >= maxLines) {
                break;
            }
            graphics.drawString(font, line, x, y + count * 10, color, false);
            count++;
        }
        return count;
    }

    /** 色（0xRRGGBB）を少し暗くする。 */
    public static int darken(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }
}
