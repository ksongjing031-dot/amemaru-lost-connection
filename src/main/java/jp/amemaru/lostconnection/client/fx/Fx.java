package jp.amemaru.lostconnection.client.fx;

import jp.amemaru.lostconnection.core.registry.ModParticles;
import jp.amemaru.lostconnection.particle.FxParticleOptions;
import jp.amemaru.lostconnection.particle.FxParticleType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 演出用パーティクルを出す道具（クライアント専用）。色は 0xRRGGBB。
 */
public final class Fx {
    private Fx() {
    }

    public static FxParticleOptions glow(int rgb, float size, int life) {
        return FxParticleOptions.of(ModParticles.GLOW.get(), rgb, size, life);
    }

    public static FxParticleOptions spark(int rgb, float size, int life) {
        return FxParticleOptions.of(ModParticles.SPARK.get(), rgb, size, life);
    }

    public static FxParticleOptions shard(int rgb, float size, int life) {
        return FxParticleOptions.of(ModParticles.SHARD.get(), rgb, size, life);
    }

    public static FxParticleOptions star(int rgb, float size, int life) {
        return FxParticleOptions.of(ModParticles.STAR.get(), rgb, size, life);
    }

    public static FxParticleOptions rune(int rgb, float size, int life) {
        return FxParticleOptions.of(ModParticles.RUNE.get(), rgb, size, life);
    }

    public static FxParticleOptions snow(int rgb, float size, int life) {
        return FxParticleOptions.of(ModParticles.SNOW.get(), rgb, size, life);
    }

    /** 1つ出す。 */
    public static void add(ClientLevel level, FxParticleOptions options, Vec3 at, Vec3 velocity) {
        level.addParticle(options, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }

    /** まわりへ散らす（大きさと時間は少しずつばらつく）。 */
    public static void burst(ClientLevel level, FxParticleType type, Vec3 at, int count, int rgb, float size, int life, double speed) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 dir = randomDirection(random);
            double s = speed * (0.4 + random.nextDouble() * 0.8);
            FxParticleOptions options = FxParticleOptions.of(type, rgb, size * (0.7F + random.nextFloat() * 0.6F),
                    Math.max(1, (int) (life * (0.7F + random.nextFloat() * 0.6F))));
            level.addParticle(options, at.x, at.y, at.z, dir.x * s, dir.y * s, dir.z * s);
        }
    }

    /** 上向きに散らす（地面からはねる火花など）。 */
    public static void fountain(ClientLevel level, FxParticleType type, Vec3 at, int count, int rgb, float size, int life, double speed,
                                double spread) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double horizontal = random.nextDouble() * spread;
            double s = speed * (0.6 + random.nextDouble() * 0.6);
            FxParticleOptions options = FxParticleOptions.of(type, rgb, size * (0.7F + random.nextFloat() * 0.6F),
                    Math.max(1, (int) (life * (0.7F + random.nextFloat() * 0.6F))));
            level.addParticle(options, at.x, at.y, at.z, Math.cos(angle) * horizontal, s, Math.sin(angle) * horizontal);
        }
    }

    /** 水平の輪に並べる（outward で外へ、up で上へ動く）。 */
    public static void ring(ClientLevel level, FxParticleType type, Vec3 center, double radius, int count, int rgb, float size, int life,
                            double outward, double up) {
        for (int i = 0; i < count; i++) {
            double angle = i / (double) count * Math.PI * 2.0;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            level.addParticle(FxParticleOptions.of(type, rgb, size, life), center.x + cos * radius, center.y, center.z + sin * radius,
                    cos * outward, up, sin * outward);
        }
    }

    /** 2点の間に並べる。 */
    public static void line(ClientLevel level, FxParticleType type, Vec3 from, Vec3 to, int count, int rgb, float size, int life,
                            double jitter) {
        RandomSource random = level.random;
        for (int i = 0; i <= count; i++) {
            Vec3 at = from.lerp(to, i / (double) Math.max(1, count));
            level.addParticle(FxParticleOptions.of(type, rgb, size, life), at.x, at.y, at.z, (random.nextDouble() - 0.5) * jitter,
                    (random.nextDouble() - 0.5) * jitter, (random.nextDouble() - 0.5) * jitter);
        }
    }

    /** 円の中に散らばるように置く（陣の中に舞う雪など）。 */
    public static void scatter(ClientLevel level, FxParticleType type, Vec3 center, double radius, int count, int rgb, float size, int life,
                               double height, Vec3 velocity) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(random.nextDouble()) * radius;
            level.addParticle(FxParticleOptions.of(type, rgb, size, life), center.x + Math.cos(angle) * r,
                    center.y + random.nextDouble() * height, center.z + Math.sin(angle) * r, velocity.x, velocity.y, velocity.z);
        }
    }

    /** 中心へ吸い込まれる（力を溜めるとき）。 */
    public static void converge(ClientLevel level, FxParticleType type, Vec3 center, double radius, int count, int rgb, float size,
                                int life) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 dir = randomDirection(random);
            Vec3 from = center.add(dir.scale(radius));
            double speed = radius / Math.max(1, life) * 1.35;
            level.addParticle(FxParticleOptions.of(type, rgb, size, life), from.x, from.y, from.z, -dir.x * speed, -dir.y * speed,
                    -dir.z * speed);
        }
    }

    public static Vec3 randomDirection(RandomSource random) {
        double z = random.nextDouble() * 2.0 - 1.0;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(1.0 - z * z);
        return new Vec3(r * Math.cos(angle), z, r * Math.sin(angle));
    }

    /** 0xRRGGBB の2色を混ぜる。 */
    public static int mix(int a, int b, float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F);
        int r = (int) Mth.lerp(k, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(k, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(k, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }
}
