package jp.amemaru.lostconnection.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

/**
 * 称号の文字の見た目（Phase 0-A 22章【確定】の色）：通常は白（段階の最上位は金）・レアは青・隠しは紫・
 * 限定は赤金でゆっくり光る・ユニークは虹色で色が変わり続ける。頭の上・画面・知らせで同じ見た目にする。
 */
public final class TitleText {
    /** 限定称号の光り方（赤金と、明るい金の間を行き来する） */
    private static final int LIMITED_DARK = 0xE8A04A;
    private static final int LIMITED_LIGHT = 0xFFE2A8;

    private TitleText() {
    }

    /**
     * 称号の名前（{@code brackets} なら【】で囲む）。{@code time} は {@code Util.getMillis()}。
     *
     * @param kind normal・rare・hidden・limited・unique
     */
    public static MutableComponent of(String name, int color, String kind, boolean rainbow, long time, boolean brackets) {
        String text = brackets ? "【" + name + "】" : name;
        boolean special = "limited".equals(kind) || "unique".equals(kind);
        if (rainbow) {
            MutableComponent out = Component.empty();
            int[] codePoints = text.codePoints().toArray();
            for (int i = 0; i < codePoints.length; i++) {
                float hue = ((time / 12L + i * 28L) % 360L) / 360.0F;
                int rgb = Mth.hsvToRgb(hue, 0.55F, 1.0F);
                out.append(Component.literal(new String(Character.toChars(codePoints[i])))
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true)));
            }
            return out;
        }
        int shown = color;
        if ("limited".equals(kind)) {
            float t = (Mth.sin((float) (time % 100000L) / 650.0F) + 1.0F) * 0.5F;
            shown = lerp(LIMITED_DARK, LIMITED_LIGHT, t);
        }
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(shown & 0xFFFFFF)).withBold(special));
    }

    /** 文字の色だけ（一覧の小さな印など）。 */
    public static int color(int color, String kind, boolean rainbow, long time) {
        if (rainbow) {
            return Mth.hsvToRgb(((time / 12L) % 360L) / 360.0F, 0.55F, 1.0F);
        }
        if ("limited".equals(kind)) {
            float t = (Mth.sin((float) (time % 100000L) / 650.0F) + 1.0F) * 0.5F;
            return lerp(LIMITED_DARK, LIMITED_LIGHT, t);
        }
        return color & 0xFFFFFF;
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }
}
