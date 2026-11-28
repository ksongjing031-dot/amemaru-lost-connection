package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.fx.Fx;
import jp.amemaru.lostconnection.client.fx.FxTextures;
import jp.amemaru.lostconnection.client.fx.FxWorld;
import jp.amemaru.lostconnection.client.fx.FxWorld.Anchor;
import jp.amemaru.lostconnection.core.registry.ModParticles;
import jp.amemaru.lostconnection.network.s2c.GearFxS2C;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.SyncUniqueSkillsS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * ユニーク武器の固有スキルと、装備の特殊効果の演出（11月28日のご指示「ユニーク武器や装備には特殊効果や特殊スキルをつけましょう」
 * 【補完】。クライアント専用）。
 *
 * <ul>
 *   <li>固有スキル：使った瞬間、武器が目を覚ます合図（足もとに武器の色の星の陣・手もとの光・星の輪）のあと、武器ごとの演出
 *       （大地の角・千年樹の根と花・水門の水柱・聖歌の波・翠の誓環・雪灯の拳・鏡の写し身・湖の波紋）。</li>
 *   <li>特殊効果（{@link GearFxS2C}）：追加ダメージの衝撃の輪・二重の当たりの写しの刃・吸収の光の流れ・処刑の環・
 *       認証の盾と足もとの環。</li>
 * </ul>
 * <p>{@link SkillFx} は、職業のスキル（{@link JobFx}）で描かなかった演出をここに回す。演出の名前はどれも {@code unique_} で始まる。</p>
 */
public final class UniqueSkillFx {
    /** 認証（第七の門鍵）の足もとの環の印。 */
    private static final int TAG_WARD = 41;
    private static final int TAG_MARKER = SkillFx.TAG_MARKER;
    private static final int WHITE = 0xFFFFFF;
    private static final int VIOLET = 0xC8A0FF;
    private static final int BLOSSOM = 0xFFC2DC;
    private static final int BARK = 0x7A5A36;
    private static final int HEARTH = 0xFFB070;
    private static final int FROST = 0xCFEFFF;
    private static final int[] PRISM = {0xC8D8FF, 0xFFC8E8, 0xFFFFFF};

    private UniqueSkillFx() {
    }

    public static void reset() {
        ClientUniqueSkills.reset();
    }

    /** 演出の色（固有スキルの武器の色。わからなければ金）。 */
    private static int color(SkillFxS2C message) {
        SyncUniqueSkillsS2C.Entry entry = ClientUniqueSkills.entry(message.skillIndex());
        return entry == null ? 0xFFE08A : entry.color();
    }

    // ---------------------------------------------------------------- 放ったとき

    /** 放ったとき。固有スキルの演出なら描いて true。 */
    static boolean release(ClientLevel level, String fx, SkillFxS2C message, @Nullable Entity caster) {
        if (!fx.startsWith("unique_")) {
            return false;
        }
        int id = message.casterId();
        int color = color(message);
        Vec3 from = message.from();
        Vec3 to = message.to();
        float radius = message.radius();
        int ticks = message.ticks();
        int[] targets = message.targets();
        RandomSource random = level.random;
        awaken(level, id, caster, color);
        switch (fx) {
            case "unique_earth_horn" -> {
                // 大地の角が、刃の先から一直線に地面を突き破って立ち上がる
                FxWorld.add(new FxWorld.GroundCut(from, to, Math.max(1.2F, radius), color, 34, 8));
                long seed = random.nextLong();
                Vec3 dir = to.subtract(from);
                int count = Math.max(3, (int) (dir.length() / 1.3));
                for (int i = 1; i <= count; i++) {
                    Vec3 spot = from.add(dir.scale(i / (double) count));
                    long s = seed + i;
                    FxWorld.later(i, () -> {
                        FxWorld.add(new FxWorld.Spikes(spot, 0.7F, 5, 2.3F, color, 26, s));
                        SkillFx.debris(level, spot, 10);
                        Fx.fountain(level, ModParticles.SPARK.get(), spot, 6, color, 0.09F, 12, 0.5, 0.3);
                    });
                }
                FxWorld.later(count + 1, () -> {
                    FxWorld.add(new FxWorld.Shockwave(to, 0.5F, 3.5F, 16, color, 1.4F, 0.9F));
                    FxWorld.add(new FxWorld.Flare(Anchor.at(to.add(0.0, 1.0, 0.0)), 4.0F, color, 10, FxTextures.GLOW, 0.0F, 2));
                });
                SkillFx.hitSparks(level, targets, color, 12);
                FxWorld.shake(from, 10, 1.1F);
            }
            case "unique_millennium" -> {
                // 種の光が、杖の先から狙った地へ飛ぶ（着くと、根と花が開く）
                int delay = Math.max(4, ticks);
                FxWorld.add(new FxWorld.Sigil(Anchor.at(to), Math.max(1.0F, radius), FxTextures.SIGIL_ARCANE, color, delay + 8, 0.04F, 4,
                        0.75F)).tag(id, TAG_MARKER);
                FxWorld.add(new FxWorld.Beam(Anchor.at(from), Anchor.at(to.add(0.0, 0.3, 0.0)), 0.25F, FxTextures.BEAM, color, delay, 1.0F,
                        0.4F));
                Fx.line(level, ModParticles.PETAL.get(), from, to, 14, BLOSSOM, 0.16F, 22, 0.06);
            }
            case "unique_tide_chime" -> {
                // 弓の握りの水門が開き、水の輪が広がる（矢は水をまとって飛ぶ）
                Vec3 bow = from.add(to.subtract(from).normalize().scale(0.9));
                Fx.ring(level, ModParticles.BUBBLE.get(), bow, 0.7, 14, color, 0.2F, 14, 0.09, 0.0);
                FxWorld.add(new FxWorld.Flare(Anchor.at(bow), 2.2F, color, 7, FxTextures.GLOW, 0.0F, 1));
                FxWorld.add(new FxWorld.Beam(Anchor.at(bow), Anchor.at(bow.add(to.subtract(from).normalize().scale(6.0))), 0.3F,
                        FxTextures.BEAM, color, 6, 1.0F, 0.8F));
            }
            case "unique_hymn_bell" -> {
                // 大鈴の音が、金と紫の波になって三度広がる。音符と羽が舞い、仲間の上に光が降りる
                for (int k = 0; k < 3; k++) {
                    int ring = k;
                    FxWorld.later(1 + k * 5, () -> FxWorld.add(new FxWorld.Shockwave(from, 0.5F, Math.max(2.0F, radius), 18,
                            ring % 2 == 0 ? color : VIOLET, 1.6F, 0.8F)));
                }
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), Math.max(1.5F, radius * 0.6F), FxTextures.SIGIL_STAR, color, 30, 0.03F, 6,
                        0.8F));
                for (int i = 0; i < 14; i++) {
                    double a = random.nextDouble() * Math.PI * 2.0;
                    double r = random.nextDouble() * Math.max(1.0, radius * 0.8);
                    level.addParticle(ParticleTypes.NOTE, from.x + Math.cos(a) * r, from.y + 1.6 + random.nextDouble(), from.z + Math.sin(a) * r,
                            random.nextInt(24) / 24.0, 0.0, 0.0);
                }
                Fx.fountain(level, ModParticles.FEATHER.get(), from, 16, 0xEEE2FF, 0.25F, 40, 0.25, 0.6);
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Flare(Anchor.center(target, new Vec3(0.0, 0.6, 0.0)), 1.6F, color, 14, FxTextures.STAR, 0.3F, 3));
                    Entity ally = level.getEntity(target);
                    if (ally != null) {
                        Fx.converge(level, ModParticles.GLOW.get(), ally.position().add(0.0, ally.getBbHeight() * 0.5, 0.0), 1.2, 8, color, 0.2F,
                                12);
                    }
                }
            }
            case "unique_jade_oath" -> {
                // 翠の誓環：足もとに誓いの陣と光の壁。中の仲間の上に盾の印
                int life = ticks > 0 ? ticks : 120;
                FxWorld.add(new FxWorld.Rim(from, Math.max(1.5F, radius), 1.2F, color, life, 0.6F));
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), Math.max(1.5F, radius), FxTextures.SIGIL_OATH, color, life, 0.02F, 8, 0.55F));
                FxWorld.add(new FxWorld.Emitter(Anchor.at(from), life, p -> {
                    if (level.random.nextInt(3) == 0) {
                        Fx.ring(level, ModParticles.SHARD.get(), p.add(0.0, 0.3, 0.0), Math.max(1.5, radius) * 0.95, 2, color, 0.2F, 20, 0.0,
                                0.05);
                    }
                }));
                for (int target : targets) {
                    Entity ally = level.getEntity(target);
                    float height = ally == null ? 1.8F : ally.getBbHeight();
                    FxWorld.add(new FxWorld.Flare(Anchor.entity(target, new Vec3(0.0, height + 0.6, 0.0)), 1.1F, color, 24, FxTextures.SHIELD,
                            0.0F, 3));
                }
                FxWorld.shake(from, 5, 0.5F);
            }
            case "unique_snowlight" -> {
                // 雪灯の拳：暖炉の火の色と雪の白の拳の光が、前へ何度も走る。最後に氷の棘
                Vec3 dir = to.subtract(from);
                double length = Math.max(1.0, dir.length());
                Vec3 look = dir.scale(1.0 / length);
                Vec3 side = new Vec3(-look.z, 0.0, look.x);
                for (int k = 0; k < 6; k++) {
                    int hit = k;
                    FxWorld.later(1 + k * 3, () -> {
                        double reach = 1.2 + level.random.nextDouble() * length * 0.7;
                        double off = (level.random.nextDouble() - 0.5) * 1.6;
                        Vec3 p = from.add(look.scale(reach)).add(side.scale(off)).add(0.0, 1.0 + level.random.nextDouble() * 0.4, 0.0);
                        FxWorld.add(new FxWorld.Flare(Anchor.at(p), 1.5F, hit % 2 == 0 ? HEARTH : WHITE, 5, FxTextures.STAR, 0.6F, 1));
                        Fx.burst(level, ModParticles.SNOW.get(), p, 8, WHITE, 0.14F, 16, 0.35);
                    });
                }
                long seed = random.nextLong();
                FxWorld.later(19, () -> {
                    FxWorld.add(new FxWorld.Shockwave(to, 0.3F, Math.max(1.5F, radius * 0.8F), 12, FROST, 0.8F, 0.8F));
                    FxWorld.add(new FxWorld.Spikes(to, 0.8F, 6, 1.4F, FROST, 30, seed));
                    Fx.burst(level, ModParticles.SNOW.get(), to.add(0.0, 0.8, 0.0), 24, WHITE, 0.16F, 24, 0.5);
                });
                SkillFx.hitSparks(level, targets, HEARTH, 10);
            }
            case "unique_mirror_moon" -> {
                // 鏡月の写し身：元いた所に鏡の残像が割れて散り、相手のまわりを三色の刃が三度走る
                FxWorld.add(new FxWorld.Flare(Anchor.at(from.add(0.0, 1.0, 0.0)), 2.2F, color, 10, FxTextures.GLOW, 0.0F, 1));
                Fx.burst(level, ModParticles.SHARD.get(), from.add(0.0, 1.0, 0.0), 18, color, 0.14F, 16, 0.45);
                for (int target : targets) {
                    for (int k = 0; k < 3; k++) {
                        int cut = k;
                        FxWorld.later(1 + k * 4, () -> {
                            float yaw = level.random.nextFloat() * 360.0F;
                            float tilt = (cut - 1) * 40.0F;
                            FxWorld.add(new FxWorld.Slash(Anchor.center(target, Vec3.ZERO), yaw, tilt, 1.3F, 0.5F, -80.0F, 80.0F, 8, 3,
                                    PRISM[cut % PRISM.length]));
                            Entity enemy = level.getEntity(target);
                            if (enemy != null) {
                                Fx.burst(level, ModParticles.SHARD.get(), enemy.position().add(0.0, enemy.getBbHeight() * 0.55, 0.0), 8,
                                        PRISM[cut % PRISM.length], 0.12F, 12, 0.5);
                            }
                        });
                    }
                }
            }
            case "unique_still_lake" -> {
                // 静湖の真珠：狙った地の上に真珠の光が灯り、湖面の陣が広がる（落ちると波紋）
                int delay = Math.max(4, ticks);
                FxWorld.add(new FxWorld.Sigil(Anchor.at(to), Math.max(1.0F, radius), FxTextures.SIGIL_FROST, color, delay + 6, 0.02F, 4, 0.7F))
                        .tag(id, TAG_MARKER);
                FxWorld.add(new FxWorld.Flare(Anchor.at(to.add(0.0, 3.0, 0.0)), 1.8F, 0xF4F8FF, delay, FxTextures.GLOW, 0.0F, delay));
                Fx.line(level, ModParticles.GLOW.get(), from, to.add(0.0, 3.0, 0.0), 10, color, 0.14F, 14, 0.02);
            }
            default -> {
                Fx.burst(level, ModParticles.STAR.get(), to, 16, color, 0.25F, 14, 0.25);
            }
        }
        return true;
    }

    /** 武器が目を覚ます合図（どの固有スキルにも出す）。 */
    private static void awaken(ClientLevel level, int id, @Nullable Entity caster, int color) {
        FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 1.5F, FxTextures.SIGIL_STAR, color, 16, 0.08F, 3, 0.85F));
        FxWorld.add(new FxWorld.Flare(Anchor.center(id, new Vec3(0.0, 0.3, 0.0)), 2.4F, color, 8, FxTextures.STAR, 0.4F, 2));
        if (caster != null) {
            Fx.ring(level, ModParticles.STAR.get(), caster.position().add(0.0, 0.2, 0.0), 1.0, 14, color, 0.22F, 18, 0.06, 0.04);
        }
    }

    // ---------------------------------------------------------------- 着いたとき

    static boolean impact(ClientLevel level, String fx, SkillFxS2C message) {
        if (!fx.startsWith("unique_")) {
            return false;
        }
        int id = message.casterId();
        int color = color(message);
        Vec3 at = message.to();
        float radius = Math.max(1.0F, message.radius());
        int ticks = message.ticks();
        RandomSource random = level.random;
        FxWorld.removeIf(v -> v.owner == id && v.tag == TAG_MARKER);
        switch (fx) {
            case "unique_millennium" -> {
                // 根が輪になって突き出し、花びらが噴き上がる。陣の間、花と光が舞いつづける
                FxWorld.add(new FxWorld.Shockwave(at, 0.4F, radius * 1.2F, 14, color, 1.0F, 0.9F));
                long seed = random.nextLong();
                for (int k = 0; k < 10; k++) {
                    double a = k / 10.0 * Math.PI * 2.0;
                    Vec3 spot = at.add(Math.cos(a) * radius * 0.7, 0.0, Math.sin(a) * radius * 0.7);
                    FxWorld.add(new FxWorld.Spikes(spot, 0.4F, 3, 1.6F, k % 2 == 0 ? BARK : color, 40, seed + k));
                }
                Fx.fountain(level, ModParticles.PETAL.get(), at, 30, BLOSSOM, 0.2F, 40, 0.5, 0.6);
                FxWorld.add(new FxWorld.Pillar(Anchor.at(at), radius * 0.35F, 5.0F, color, 18));
                int life = ticks > 0 ? ticks : 100;
                FxWorld.add(new FxWorld.Emitter(Anchor.at(at), life, p -> {
                    if (level.random.nextInt(2) == 0) {
                        Fx.scatter(level, ModParticles.PETAL.get(), p, radius, 2, BLOSSOM, 0.16F, 30, 0.4, new Vec3(0.0, 0.03, 0.0));
                        Fx.scatter(level, ModParticles.GLOW.get(), p, radius, 1, color, 0.14F, 24, 0.2, new Vec3(0.0, 0.05, 0.0));
                    }
                }));
                FxWorld.shake(at, 5, 0.5F);
            }
            case "unique_tide_chime" -> {
                // 着いた所で、水門が開いたような水柱と波
                FxWorld.add(new FxWorld.Shockwave(at, 0.3F, radius * 1.3F, 14, color, 1.2F, 0.9F));
                FxWorld.add(new FxWorld.Dome(Anchor.at(at), radius * 0.8F, color, 12, true, 0.35F, 3));
                FxWorld.add(new FxWorld.Pillar(Anchor.at(at), 0.8F, 4.0F, color, 12));
                Fx.fountain(level, ModParticles.BUBBLE.get(), at, 40, color, 0.22F, 26, 0.7, 0.8);
                Fx.burst(level, ModParticles.SPARK.get(), at.add(0.0, 0.5, 0.0), 14, WHITE, 0.09F, 10, 0.6);
                FxWorld.shake(at, 6, 0.8F);
            }
            case "unique_still_lake" -> {
                // 真珠が落ち、湖の波紋が三重に広がる
                for (int k = 0; k < 3; k++) {
                    int ring = k;
                    FxWorld.later(1 + k * 5, () -> FxWorld.add(new FxWorld.Shockwave(at, 0.3F, radius * (0.6F + 0.25F * ring), 20, color, 0.6F,
                            0.85F)));
                }
                FxWorld.add(new FxWorld.Flare(Anchor.at(at.add(0.0, 0.6, 0.0)), 5.0F, 0xF4F8FF, 8, FxTextures.GLOW, 0.0F, 1));
                FxWorld.add(new FxWorld.Dome(Anchor.at(at), radius * 0.6F, color, 14, true, 0.3F, 4));
                Fx.fountain(level, ModParticles.BUBBLE.get(), at, 30, color, 0.2F, 24, 0.6, 1.0);
                FxWorld.shake(at, 8, 1.0F);
            }
            default -> {
                FxWorld.add(new FxWorld.Shockwave(at, 0.3F, radius, 12, color, 0.8F, 0.8F));
                Fx.burst(level, ModParticles.STAR.get(), at, 16, color, 0.25F, 14, 0.3);
            }
        }
        return true;
    }

    /** 陣が働いたときなど（{@code TRIGGER}）。 */
    static boolean trigger(ClientLevel level, String fx, SkillFxS2C message) {
        if (!fx.startsWith("unique_")) {
            return false;
        }
        int color = color(message);
        Vec3 at = message.to();
        Fx.burst(level, ModParticles.GLOW.get(), at, 6, color, 0.2F, 10, 0.15);
        return true;
    }

    // ---------------------------------------------------------------- 装備の特殊効果

    public static void gear(GearFxS2C message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity target = level.getEntity(message.targetId());
        Entity source = level.getEntity(message.sourceId());
        int color = message.color();
        switch (message.kind()) {
            case EXTRA -> {
                if (target == null) {
                    return;
                }
                Vec3 c = center(target);
                FxWorld.add(new FxWorld.Shockwave(target.position(), 0.3F, 1.6F, 10, color, 0.9F, 0.9F));
                FxWorld.add(new FxWorld.Flare(Anchor.at(c), 1.8F, color, 6, FxTextures.STAR, 0.5F, 1));
                Fx.burst(level, ModParticles.SPARK.get(), c, 12, color, 0.1F, 12, 0.6);
            }
            case DOUBLE -> {
                if (target == null) {
                    return;
                }
                float yaw = level.random.nextFloat() * 360.0F;
                FxWorld.add(new FxWorld.Slash(Anchor.center(target.getId(), Vec3.ZERO), yaw, 30.0F, 1.2F, 0.45F, -70.0F, 70.0F, 7, 2, color));
                Fx.burst(level, ModParticles.SHARD.get(), center(target), 10, color, 0.12F, 12, 0.45);
            }
            case DRAIN -> {
                if (target == null || source == null) {
                    return;
                }
                FxWorld.add(new FxWorld.Beam(Anchor.center(target.getId(), Vec3.ZERO), Anchor.center(source.getId(), Vec3.ZERO), 0.15F,
                        FxTextures.BEAM, color, 8, 1.0F, 0.2F));
                Fx.line(level, ModParticles.GLOW.get(), center(target), center(source), 10, color, 0.16F, 14, 0.02);
                FxWorld.add(new FxWorld.Flare(Anchor.center(source.getId(), Vec3.ZERO), 1.4F, color, 8, FxTextures.GLOW, 0.0F, 2));
            }
            case EXECUTE -> {
                if (target == null) {
                    return;
                }
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(target.getId(), Vec3.ZERO), Math.max(0.8F, target.getBbWidth() * 1.2F),
                        FxTextures.SIGIL_OATH, color, 14, 0.15F, 2, 0.9F));
                FxWorld.add(new FxWorld.Flare(Anchor.entity(target.getId(), new Vec3(0.0, target.getBbHeight() + 0.5, 0.0)), 1.2F, color, 12,
                        FxTextures.STAR, 0.3F, 2));
            }
            case WARD -> {
                if (target == null) {
                    return;
                }
                FxWorld.add(new FxWorld.Flare(Anchor.center(target.getId(), Vec3.ZERO), 2.0F, color, 8, FxTextures.SHIELD, 0.0F, 2));
            }
            case WARD_ON -> {
                int id = message.sourceId();
                FxWorld.removeIf(v -> v.owner == id && v.tag == TAG_WARD);
                if (source == null) {
                    return;
                }
                // 付けている人の足もとに、ゆっくり回る翠の陣と、立ちのぼる粒（認証が働いている間）
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, new Vec3(0.0, 0.02, 0.0)), 1.1F, FxTextures.SIGIL_OATH, color, 20 * 60 * 30,
                        0.02F, 6, 0.35F)).tag(id, TAG_WARD);
                FxWorld.add(new FxWorld.Emitter(Anchor.entity(id, Vec3.ZERO), 20 * 60 * 30, p -> {
                    if (level.random.nextInt(4) == 0) {
                        Fx.ring(level, ModParticles.RUNE.get(), p.add(0.0, 0.1, 0.0), 0.9, 2, color, 0.16F, 24, 0.0, 0.03);
                    }
                })).tag(id, TAG_WARD);
                FxWorld.add(new FxWorld.Shockwave(source.position(), 0.3F, 8.0F, 20, color, 0.8F, 0.6F));
            }
            case WARD_OFF -> {
                int id = message.sourceId();
                FxWorld.removeIf(v -> v.owner == id && v.tag == TAG_WARD);
            }
        }
    }

    private static Vec3 center(Entity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.55, 0.0);
    }
}
