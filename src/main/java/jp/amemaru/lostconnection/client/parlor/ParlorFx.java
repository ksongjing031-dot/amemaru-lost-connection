package jp.amemaru.lostconnection.client.parlor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 遊技台の画面の動き（11月28日のご指示「パチンコとスロットにもっとアニメーションをつける」【補完】）：画面の中だけで動く
 * 紙吹雪・火花・きらめき・広がる輪と、画面の光（フラッシュ）・揺れ、動きのやわらげ方（イージング）。
 *
 * <p>どれも見た目だけ（当たりや払い出しは、サーバーの台の様子のとおり）。</p>
 */
final class ParlorFx {
    static final int CONFETTI = 0;
    static final int SPARK = 1;
    static final int STAR = 2;
    static final int RING = 3;
    static final int MEDAL = 4;

    private static final int MAX = 400;
    private static final int[] RAINBOW = {0xFFFF4060, 0xFFFFB040, 0xFFFFF060, 0xFF60F080, 0xFF50C0FF, 0xFFB070FF};

    private static final class P {
        float x;
        float y;
        float vx;
        float vy;
        float gravity;
        float size;
        float angle;
        float spin;
        int color;
        long born;
        int life;
        int kind;
    }

    private final List<P> list = new ArrayList<>();
    private final Random random = new Random();
    private long last;
    private long flashStart;
    private long flashUntil;
    private int flashColor;
    private long shakeUntil;
    private float shakePower;

    // ---------------------------------------------------------------- 出す

    /** 上から紙吹雪を降らせる（x0〜x1 の幅に）。colors が null なら虹の色。 */
    void confetti(float x0, float x1, float y, int count, int[] colors) {
        for (int i = 0; i < count; i++) {
            P p = add(CONFETTI, x0 + random.nextFloat() * (x1 - x0), y - random.nextFloat() * 20.0F, 1400 + random.nextInt(900));
            p.vx = (random.nextFloat() - 0.5F) * 0.06F;
            p.vy = 0.02F + random.nextFloat() * 0.05F;
            p.gravity = 0.00004F;
            p.size = 1.5F + random.nextFloat() * 1.5F;
            p.spin = (random.nextFloat() - 0.5F) * 0.04F;
            p.color = pick(colors);
        }
    }

    /** 1点から火花・星を散らす。 */
    void burst(float x, float y, int count, int color, float speed, int kind) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            float s = speed * (0.4F + random.nextFloat() * 0.8F);
            P p = add(kind, x, y, 350 + random.nextInt(450));
            p.vx = (float) Math.cos(a) * s;
            p.vy = (float) Math.sin(a) * s - speed * 0.3F;
            p.gravity = kind == SPARK ? 0.0003F : 0.00008F;
            p.size = kind == STAR ? 2.0F + random.nextFloat() * 2.0F : 1.0F + random.nextFloat() * 1.2F;
            p.color = color == 0 ? pick(null) : color;
        }
    }

    /** 広がる光の輪。 */
    void ring(float x, float y, int color, int life, float radius) {
        P p = add(RING, x, y, life);
        p.size = radius;
        p.color = color;
    }

    /** 弧を描いて飛ぶメダル（受け皿へ落ちる）。 */
    void medal(float x, float y, float vx, float vy, int life) {
        P p = add(MEDAL, x, y, life);
        p.vx = vx;
        p.vy = vy;
        p.gravity = 0.0009F;
        p.size = 2.6F;
        p.color = 0xFFE8E8F0;
    }

    /** 画面を一瞬光らせる。 */
    void flash(int color, int ms) {
        long now = System.currentTimeMillis();
        flashStart = now;
        flashUntil = now + ms;
        flashColor = color;
    }

    /** 揺らす（power はピクセル）。 */
    void shake(int ms, float power) {
        shakeUntil = System.currentTimeMillis() + ms;
        shakePower = power;
    }

    int shakeX(long now) {
        return now < shakeUntil ? Math.round((float) Math.sin(now / 23.0) * shakePower * (shakeUntil - now) / 400.0F) : 0;
    }

    int shakeY(long now) {
        return now < shakeUntil ? Math.round((float) Math.cos(now / 29.0) * shakePower * 0.6F * (shakeUntil - now) / 400.0F) : 0;
    }

    void clear() {
        list.clear();
        flashUntil = 0;
        shakeUntil = 0;
    }

    private P add(int kind, float x, float y, int life) {
        if (list.size() >= MAX) {
            list.remove(0);
        }
        P p = new P();
        p.kind = kind;
        p.x = x;
        p.y = y;
        p.born = System.currentTimeMillis();
        p.life = life;
        p.angle = random.nextFloat() * Mth.TWO_PI;
        list.add(p);
        return p;
    }

    private int pick(int[] colors) {
        int[] from = colors == null || colors.length == 0 ? RAINBOW : colors;
        return from[random.nextInt(from.length)];
    }

    // ---------------------------------------------------------------- 描く

    /** 動かして描く（clip の四角の外は描かない）。 */
    void render(GuiGraphics g, long now, int x0, int y0, int x1, int y1) {
        long dt = last == 0 ? 16 : Math.min(60, now - last);
        last = now;
        if (list.isEmpty()) {
            return;
        }
        g.enableScissor(x0, y0, x1, y1);
        for (int i = list.size() - 1; i >= 0; i--) {
            P p = list.get(i);
            long age = now - p.born;
            if (age > p.life) {
                list.remove(i);
                continue;
            }
            p.vy += p.gravity * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.angle += p.spin * dt;
            float k = age / (float) p.life;
            float a = k < 0.7F ? 1.0F : 1.0F - (k - 0.7F) / 0.3F;
            switch (p.kind) {
                case CONFETTI -> {
                    float w = Math.max(0.6F, Math.abs((float) Math.cos(p.angle)) * p.size * 1.6F);
                    int px = Math.round(p.x);
                    int py = Math.round(p.y);
                    g.fill(px, py, px + Math.max(1, Math.round(w)), py + Math.max(1, Math.round(p.size)), ParlorDraw.alpha(p.color, a));
                }
                case SPARK -> ParlorDraw.disc(g, p.x, p.y, p.size * (1.0F - k * 0.5F), ParlorDraw.alpha(p.color, a));
                case STAR -> {
                    int px = Math.round(p.x);
                    int py = Math.round(p.y);
                    int r = Math.max(1, Math.round(p.size * (1.0F - k * 0.4F)));
                    int c = ParlorDraw.alpha(p.color, a);
                    g.fill(px - r, py, px + r + 1, py + 1, c);
                    g.fill(px, py - r, px + 1, py + r + 1, c);
                    g.fill(px, py, px + 1, py + 1, ParlorDraw.alpha(0xFFFFFF, a));
                }
                case RING -> {
                    float r = p.size * easeOut(k);
                    int n = Mth.clamp((int) (r * 2.2F), 12, 90);
                    int c = ParlorDraw.alpha(p.color, (1.0F - k) * 0.9F);
                    for (int s = 0; s < n; s++) {
                        double ang = s / (double) n * Math.PI * 2.0;
                        int px = Math.round(p.x + (float) Math.cos(ang) * r);
                        int py = Math.round(p.y + (float) Math.sin(ang) * r);
                        g.fill(px, py, px + 1, py + 1, c);
                    }
                }
                default -> {
                    ParlorDraw.disc(g, p.x, p.y, p.size, ParlorDraw.alpha(0xFF9A9AA8, a));
                    ParlorDraw.disc(g, p.x - 0.4F, p.y - 0.4F, p.size - 0.8F, ParlorDraw.alpha(p.color, a));
                }
            }
        }
        g.disableScissor();
    }

    /** 光（フラッシュ）を、四角の中に重ねる。 */
    void renderFlash(GuiGraphics g, int x, int y, int w, int h, long now) {
        if (now >= flashUntil) {
            return;
        }
        float k = (now - flashStart) / (float) Math.max(1, flashUntil - flashStart);
        g.fill(x, y, x + w, y + h, ParlorDraw.alpha(flashColor, 0.75F * (1.0F - k)));
    }

    /** メダルが下の線（y）より下に落ちた数を数えて消す（受け皿に入った）。 */
    int landedMedals(float floorY) {
        int count = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            P p = list.get(i);
            if (p.kind == MEDAL && p.vy > 0 && p.y >= floorY) {
                list.remove(i);
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- やわらげ方

    static float easeOut(float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - (1.0F - k) * (1.0F - k);
    }

    static float easeIn(float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F);
        return k * k;
    }

    /** 少し行き過ぎて戻る。 */
    static float easeOutBack(float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F) - 1.0F;
        float c = 1.70158F;
        return 1.0F + (c + 1.0F) * k * k * k + c * k * k;
    }

    /** はずんで止まる。 */
    static float bounce(float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F);
        if (k < 1 / 2.75F) {
            return 7.5625F * k * k;
        } else if (k < 2 / 2.75F) {
            k -= 1.5F / 2.75F;
            return 7.5625F * k * k + 0.75F;
        } else if (k < 2.5 / 2.75F) {
            k -= 2.25F / 2.75F;
            return 7.5625F * k * k + 0.9375F;
        }
        k -= 2.625F / 2.75F;
        return 7.5625F * k * k + 0.984375F;
    }
}
