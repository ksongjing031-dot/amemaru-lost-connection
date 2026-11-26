package jp.amemaru.lostconnection.client.parlor;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

/**
 * 遊技台の画面の描き方の道具（11月27日【補完】）：枠・光る四角・虹色・7セグメントの数字・大きな文字・音。
 */
public final class ParlorDraw {
    private ParlorDraw() {
    }

    /** 虹色（t はミリ秒。speed は1周の長さ）。 */
    public static int rainbow(long t, int speed, float offset) {
        float h = ((t % speed) / (float) speed + offset) % 1.0F;
        return Mth.hsvToRgb(h, 0.75F, 1.0F) | 0xFF000000;
    }

    public static int alpha(int rgb, float a) {
        return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static int darker(int rgb, float f) {
        return mix(rgb | 0xFF000000, 0xFF000000, f);
    }

    public static int lighter(int rgb, float f) {
        return mix(rgb | 0xFF000000, 0xFFFFFFFF, f);
    }

    /** 立体の枠（上と左が明るく、下と右が暗い）。 */
    public static void bevel(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color | 0xFF000000);
        g.fill(x, y, x + w, y + 1, lighter(color, 0.35F));
        g.fill(x, y, x + 1, y + h, lighter(color, 0.25F));
        g.fill(x, y + h - 1, x + w, y + h, darker(color, 0.45F));
        g.fill(x + w - 1, y, x + w, y + h, darker(color, 0.35F));
    }

    /** へこんだ枠（窓）。 */
    public static void inset(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, color | 0xFF000000);
        g.fill(x, y, x + w, y + 1, darker(color, 0.5F));
        g.fill(x, y + h - 1, x + w, y + h, lighter(color, 0.2F));
    }

    /** 光る四角（ふちがにじむ）。 */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int color, float strength) {
        for (int i = 3; i >= 1; i--) {
            g.fill(x - i, y - i, x + w + i, y + h + i, alpha(color, 0.12F * strength * (4 - i)));
        }
        g.fill(x, y, x + w, y + h, alpha(color, Math.min(1.0F, strength)));
    }

    /** 文字を大きさ scale で、真ん中にそろえて描く（ふち取り）。 */
    public static void bigCentered(GuiGraphics g, Font font, Component text, int cx, int cy, float scale, int color, int outline) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1.0F);
        int w = font.width(text);
        int x = -w / 2;
        int y = -4;
        if (outline != 0) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        g.drawString(font, text.getString(), x + dx, y + dy, outline, false);
                    }
                }
            }
        }
        g.drawString(font, text, x, y, color, false);
        g.pose().popPose();
    }

    public static void bigCentered(GuiGraphics g, Font font, String text, int cx, int cy, float scale, int color, int outline) {
        bigCentered(g, font, Component.literal(text), cx, cy, scale, color, outline);
    }

    /** 7セグメントの数字（データランプの表示）。digits 桁で右にそろえる。 */
    public static void segments(GuiGraphics g, int x, int y, long value, int digits, int on, int off, int size) {
        String s = Long.toString(Math.max(0, value));
        int w = size + 3;
        for (int i = 0; i < digits; i++) {
            int idx = s.length() - digits + i;
            int d = idx >= 0 ? s.charAt(idx) - '0' : -1;
            seg(g, x + i * w, y, d, on, off, size);
        }
    }

    private static final int[] SEG = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};

    private static void seg(GuiGraphics g, int x, int y, int d, int on, int off, int size) {
        int bits = d < 0 ? 0 : SEG[d];
        int s = size;
        int t = Math.max(1, size / 5);
        int[][] r = {
                {x + t, y, x + s - t, y + t},               // a
                {x + s - t, y + t, x + s, y + s},            // b
                {x + s - t, y + s, x + s, y + 2 * s - t},    // c
                {x + t, y + 2 * s - t, x + s - t, y + 2 * s},// d
                {x, y + s, x + t, y + 2 * s - t},            // e
                {x, y + t, x + t, y + s},                    // f
                {x + t, y + s - t / 2 - 1, x + s - t, y + s + t / 2}, // g
        };
        for (int i = 0; i < 7; i++) {
            boolean lit = (bits & (1 << i)) != 0;
            g.fill(r[i][0], r[i][1], r[i][2], r[i][3], lit ? on : off);
        }
    }

    public static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** 塗りつぶした円（小さな丸。玉・保留・ランプ）。 */
    public static void disc(GuiGraphics g, float cx, float cy, float r, int color) {
        int ir = (int) Math.ceil(r);
        for (int dy = -ir; dy <= ir; dy++) {
            float half = (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            if (half <= 0) {
                continue;
            }
            g.fill(Math.round(cx - half), Math.round(cy + dy), Math.round(cx + half), Math.round(cy + dy + 1), color);
        }
    }

    /** 線（細い四角を並べる）。 */
    public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int color) {
        int n = (int) Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            int x = Math.round(Mth.lerp(t, x0, x1));
            int y = Math.round(Mth.lerp(t, y0, y1));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    public static void enableBlend() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public static int parse(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16) | 0xFF000000;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
