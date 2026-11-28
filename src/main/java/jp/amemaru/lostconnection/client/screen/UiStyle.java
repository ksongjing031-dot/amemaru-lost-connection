package jp.amemaru.lostconnection.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.amemaru.lostconnection.client.AmemaruHudArt;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
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

    /** パネルの上の2つの角の飾りだけを描き直す（パネルの中の上に帯を敷いたとき、飾りの内側が欠けないように）。 */
    public static void panelTopCorners(GuiGraphics graphics, int x0, int y0, int x1) {
        RenderSystem.enableBlend();
        int size = AmemaruHudArt.DIAMOND_SIZE;
        graphics.blit(AmemaruHudArt.TEXTURE, x0 - 5, y0 - 5, AmemaruHudArt.U_DIAMOND, AmemaruHudArt.V_DIAMOND, size, size, 256, 256);
        graphics.blit(AmemaruHudArt.TEXTURE, x1 - 4, y0 - 5, AmemaruHudArt.U_DIAMOND, AmemaruHudArt.V_DIAMOND, size, size, 256, 256);
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

    /** 行の頭に来てはいけない文字（閉じかっこ・句読点・小さい仮名など）。前の文字といっしょに次の行へ送る（追い出し）。 */
    private static final String NO_LINE_START = "、。，．,.・：；:;？！?!ー－～…‥）」』】〕〉》｝)]}ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ％%";
    /** 行の終わりに来てはいけない文字（開きかっこ）。次の行へ送る。 */
    private static final String NO_LINE_END = "（「『【〔〈《｛([{";
    private static final String ELLIPSIS = "…";

    /**
     * 文を幅で折り返す（日本語の禁則つき）。英数字の続き（例：{@code Lv30}・{@code MP}）は途中で切らない。
     * {@code \n} で改行する。
     */
    public static List<String> wrap(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            int i = 0;
            int n = paragraph.length();
            while (i < n) {
                int j = i + 1;
                if (isWordChar(paragraph.charAt(i))) {
                    while (j < n && isWordChar(paragraph.charAt(j))) {
                        j++;
                    }
                }
                String token = paragraph.substring(i, j);
                if (line.length() > 0 && font.width(line + token) > width) {
                    if (token.length() == 1 && NO_LINE_START.indexOf(token.charAt(0)) >= 0) {
                        // 行頭禁則（11月28日：ぶら下げると枠からはみ出すので、追い出しにする）：前の行の終わりの文字
                        // （英数字の続きなら続きごと。句読点が並ぶならその前から）を、句読点といっしょに次の行へ送る
                        int cut = line.length() - 1;
                        if (isWordChar(line.charAt(cut))) {
                            while (cut > 0 && isWordChar(line.charAt(cut - 1))) {
                                cut--;
                            }
                        }
                        while (cut > 0 && NO_LINE_START.indexOf(line.charAt(cut)) >= 0) {
                            cut--;
                        }
                        if (cut <= 0) {
                            // 送れない（行が一語だけ）：しかたなく、ぶら下げる
                            line.append(token);
                            i = j;
                            continue;
                        }
                        String moved = line.substring(cut);
                        line.setLength(cut);
                        if (line.length() > 1 && NO_LINE_END.indexOf(line.charAt(line.length() - 1)) >= 0) {
                            // 行末禁則：開きかっこが行の終わりに残るなら、それも送る
                            moved = line.charAt(line.length() - 1) + moved;
                            line.setLength(line.length() - 1);
                        }
                        lines.add(line.toString());
                        line = new StringBuilder(moved).append(token);
                        i = j;
                        continue;
                    }
                    String carry = "";
                    if (line.length() > 1 && NO_LINE_END.indexOf(line.charAt(line.length() - 1)) >= 0) {
                        // 行末禁則：開きかっこは次の行へ
                        carry = line.substring(line.length() - 1);
                        line.setLength(line.length() - 1);
                    }
                    lines.add(line.toString());
                    line = new StringBuilder(carry);
                    if (token.equals(" ")) {
                        i = j;
                        continue;
                    }
                }
                line.append(token);
                i = j;
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static boolean isWordChar(char c) {
        return c < 0x80 && (Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.' || c == '%' || c == '/');
    }

    /** 幅に入らなければ、後ろを「…」にして縮める。 */
    public static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String base = text.endsWith(ELLIPSIS) ? text.substring(0, text.length() - 1) : text;
        int limit = Math.max(0, width - font.width(ELLIPSIS));
        while (!base.isEmpty() && font.width(base) > limit) {
            base = base.substring(0, base.length() - 1);
        }
        return base + ELLIPSIS;
    }

    /** 折り返すと {@code maxLines} 行に入りきらないか（入りきらないときは、ツールチップで全文を見せる）。 */
    public static boolean overflows(Font font, String text, int width, int maxLines) {
        return wrap(font, text, width).size() > maxLines;
    }

    /** 文を幅で折り返して描く（日本語の禁則つき。入りきらない分は、最後の行を「…」にする）。描いた行の数を返す。 */
    public static int wrapped(GuiGraphics graphics, Font font, String text, int x, int y, int width, int color, int maxLines) {
        if (text == null || text.isEmpty() || maxLines <= 0) {
            return 0;
        }
        List<String> lines = wrap(font, text, width);
        int count = Math.min(maxLines, lines.size());
        for (int k = 0; k < count; k++) {
            String line = lines.get(k);
            if (k == count - 1 && lines.size() > count) {
                line = clip(font, line + ELLIPSIS, width);
            }
            graphics.drawString(font, line, x, y + k * 10, color, false);
        }
        return count;
    }

    /** 全文を、ツールチップ用に折り返す。 */
    public static List<FormattedCharSequence> tooltipLines(Font font, String text, int width) {
        List<FormattedCharSequence> result = new ArrayList<>();
        for (String line : wrap(font, text, width)) {
            result.add(FormattedCharSequence.forward(line, net.minecraft.network.chat.Style.EMPTY));
        }
        return result;
    }

    /** 色（0xRRGGBB）を少し暗くする。 */
    public static int darken(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }
}
