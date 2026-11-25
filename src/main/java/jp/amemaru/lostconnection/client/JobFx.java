package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.fx.Fx;
import jp.amemaru.lostconnection.client.fx.FxTextures;
import jp.amemaru.lostconnection.client.fx.FxWorld;
import jp.amemaru.lostconnection.client.fx.FxWorld.Anchor;
import jp.amemaru.lostconnection.core.registry.ModParticles;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Phase 6 その2 で足したスキル（ヒーラー・アーチャー・ローグ・モンクと、ナイト・ウィザードの足した段）の演出（クライアント専用）。
 *
 * <p>{@link SkillFx} の各段（詠唱・溜め・放った・着弾・発動）で、決まった演出がないときにここを呼ぶ。演出の名前は
 * スキルの「fx」（初期値は {@code <職業>_<スキル>}）。職業ごとに色と形をそろえる：</p>
 * <ul>
 *   <li>ヒーラー：金と若葉の光・花びら・鈴の音の輪（{@link #chime}）・癒しの陣。</li>
 *   <li>アーチャー：白い光の矢・羽根・風の輪。矢そのものは {@code LampBoltRenderer} が描く。</li>
 *   <li>ローグ：紫の影・煙・すばやい斬撃の弧（{@link #slashes}）・毒の泡。</li>
 *   <li>モンク：橙の打撃の光（{@link #strike}）と、青い気。</li>
 * </ul>
 * <p>扱った演出なら true を返す（扱わなかったら {@link SkillFx} のふつうの演出になる）。</p>
 */
final class JobFx {
    private static final int WHITE = 0xFFFFFF;
    private static final int GOLD = 0xFFD27A;
    // ヒーラー
    private static final int HOLY = 0xFFF6D8;
    private static final int BELL = 0xFFE7A0;
    private static final int LIFE = 0xB8FF9C;
    private static final int SAGE = 0x9CF0C8;
    private static final int CALM = 0xA8D8FF;
    // アーチャー
    private static final int ARROW = 0xE6F4FF;
    private static final int WIND = 0xA8F0C8;
    private static final int MARK = 0xFF6A5A;
    private static final int STAR = 0xFFE27A;
    // ローグ
    private static final int SHADOW = 0x8A6CFF;
    private static final int DUSK = 0x3A2E5A;
    private static final int BLADE = 0xD8D0FF;
    private static final int VENOM = 0x9CF060;
    private static final int SMOKE_GRAY = 0x8C8A9A;
    // モンク
    private static final int FIST = 0xFFB46E;
    private static final int FIST_HOT = 0xFFE0A0;
    private static final int KI = 0x9FDCFF;
    private static final int THUNDER = 0xFFF2A0;
    // ナイト・ウィザード
    private static final int KNIGHT = 0x8FD3FF;
    private static final int STEEL = 0xDDE6F5;
    private static final int EARTH = 0xC8A070;
    private static final int ICE = 0xBFF2FF;
    private static final int FROST = 0x7FD8FF;

    private JobFx() {
    }

    // ================================================================ 詠唱・溜め

    /** 詠唱を始めたとき（ふつうの魔法陣の上に足す）。 */
    static void castStart(ClientLevel level, String fx, SkillFxS2C message, @Nullable Entity caster) {
        int id = message.casterId();
        int ticks = Math.max(1, message.ticks());
        switch (fx) {
            case "healer_call_back", "healer_great_call" -> {
                boolean great = fx.equals("healer_great_call");
                FxWorld.add(new FxWorld.Gather(Anchor.center(id, Vec3.ZERO), great ? 3.0F : 2.0F, LIFE, ticks + 4, great ? 16 : 10))
                        .tag(id, SkillFx.TAG_CAST);
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), great ? 2.6F : 1.6F, FxTextures.SIGIL_STAR, LIFE, ticks + 6,
                        -0.03F, 8, 0.8F)).tag(id, SkillFx.TAG_CAST);
                if (!great) {
                    // 呼び戻す相手（倒れた仲間）の足もとにも、小さな印
                    FxWorld.add(new FxWorld.Sigil(Anchor.at(message.to()), 1.2F, FxTextures.SIGIL_STAR, HOLY, ticks + 6, 0.05F, 8, 0.7F))
                            .tag(id, SkillFx.TAG_MARKER);
                }
            }
            case "archer_shooting_star" -> {
                FxWorld.add(new FxWorld.Gather(Anchor.center(id, Vec3.ZERO), 2.6F, STAR, ticks + 4, 14)).tag(id, SkillFx.TAG_CAST);
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 2.2F, FxTextures.SIGIL_STAR, STAR, ticks + 6, 0.04F, 8, 0.8F))
                        .tag(id, SkillFx.TAG_CAST);
                FxWorld.add(new FxWorld.LinePreview(id, Math.max(8.0F, message.radius()), 1.2F, STAR, ticks + 4)).tag(id, SkillFx.TAG_CAST);
            }
            case "wizard_comet" -> {
                FxWorld.add(new FxWorld.Gather(Anchor.center(id, new Vec3(0.0, 0.8, 0.0)), 2.4F, GOLD, ticks + 4, 14))
                        .tag(id, SkillFx.TAG_CAST);
                FxWorld.add(new FxWorld.Flare(Anchor.center(id, new Vec3(0.0, 1.6, 0.0)), 1.4F, GOLD, ticks + 4, FxTextures.STAR, 0.08F,
                        ticks)).tag(id, SkillFx.TAG_CAST);
            }
            default -> {
            }
        }
    }

    /** 溜めを始めたとき。扱ったら true。 */
    static boolean chargeStart(ClientLevel level, String fx, SkillFxS2C message, int hold) {
        int id = message.casterId();
        int ticks = Math.max(1, message.ticks());
        int color = GOLD;
        switch (fx) {
            case "archer_full_draw", "archer_sky_piercer" -> {
                color = fx.equals("archer_sky_piercer") ? STAR : ARROW;
                FxWorld.add(new FxWorld.Emitter(Anchor.center(id, Vec3.ZERO), hold, at ->
                        Fx.converge(level, ModParticles.GLOW.get(), at.add(0.0, 0.3, 0.0), 1.6, 1, WIND, 0.18F, 10))).tag(id, SkillFx.TAG_CAST);
            }
            case "healer_swelling_bell" -> {
                color = LIFE;
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 1.4F, FxTextures.SIGIL_STAR, LIFE, hold, 0.06F, ticks, 0.8F))
                        .tag(id, SkillFx.TAG_CAST);
            }
            case "monk_thunder_fist" -> {
                color = THUNDER;
                FxWorld.add(new FxWorld.Emitter(Anchor.center(id, Vec3.ZERO), hold, at -> {
                    if (level.random.nextInt(3) == 0) {
                        Vec3 a = at.add((level.random.nextDouble() - 0.5) * 1.2, (level.random.nextDouble() - 0.5) * 1.2,
                                (level.random.nextDouble() - 0.5) * 1.2);
                        Fx.burst(level, ModParticles.SPARK.get(), a, 2, THUNDER, 0.07F, 6, 0.3);
                    }
                })).tag(id, SkillFx.TAG_CAST);
            }
            default -> {
                return false;
            }
        }
        FxWorld.add(new FxWorld.Gather(Anchor.center(id, Vec3.ZERO), 2.0F, color, hold, 10)).tag(id, SkillFx.TAG_CAST);
        SkillFx.fullChargeFlash(level, id, ticks, color);
        return true;
    }

    // ================================================================ 放ったとき

    static boolean release(ClientLevel level, String fx, SkillFxS2C message, @Nullable Entity caster,
                           @Nullable SyncDefinitionsS2C.SkillInfo skill) {
        int id = message.casterId();
        Vec3 from = message.from();
        Vec3 to = message.to();
        float radius = message.radius();
        float power = message.power();
        int ticks = message.ticks();
        int[] targets = message.targets();
        RandomSource random = level.random;
        switch (fx) {
            // ------------------------------------------------ ヒーラー：癒し
            case "healer_bell_heal" -> {
                beams(id, targets, LIFE, 0.07F, 10);
                chime(caster != null ? caster.position() : from, BELL, 1.4F, 2);
                for (int target : targets) {
                    bloom(level, target, LIFE, 1.0F);
                }
            }
            case "healer_bond_mend" -> {
                beams(id, targets, SAGE, 0.07F, 10);
                for (int target : targets) {
                    bloom(level, target, SAGE, 1.0F);
                    cleanseSparkle(level, target);
                }
            }
            case "healer_swelling_bell" -> {
                float size = 1.0F + Mth.clamp(power, 0.0F, 1.0F);
                beams(id, targets, LIFE, 0.08F + 0.06F * power, 12);
                for (int target : targets) {
                    bloom(level, target, LIFE, size);
                    if (power >= 0.999F) {
                        FxWorld.add(new FxWorld.Pillar(Anchor.entity(target, Vec3.ZERO), 0.7F, 5.0F, LIFE, 18));
                    }
                }
                chime(from, BELL, 1.2F + power, 3);
            }
            case "healer_life_thread" -> {
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, Vec3.ZERO), Anchor.center(target, Vec3.ZERO), 0.05F, FxTextures.BEAM, GOLD,
                            40, 1.2F, 0.08F).extend(8));
                    bloom(level, target, GOLD, 0.8F);
                }
            }
            case "healer_guardian_bell" -> {
                for (int target : targets) {
                    Entity ally = level.getEntity(target);
                    float height = ally == null ? 1.8F : ally.getBbHeight();
                    FxWorld.add(new FxWorld.Flare(Anchor.entity(target, new Vec3(0.0, height + 0.6, 0.0)), 1.4F, BELL, 24, FxTextures.STAR,
                            0.1F, 4));
                    FxWorld.add(new FxWorld.Sigil(Anchor.entity(target, Vec3.ZERO), 1.0F, FxTextures.SIGIL_OATH, BELL, 24, 0.05F, 4, 0.8F));
                    chime(ally != null ? ally.position() : to, BELL, 1.2F, 2);
                }
                beams(id, targets, BELL, 0.06F, 10);
            }
            case "healer_blessed_shield" -> {
                beams(id, targets, GOLD, 0.07F, 10);
                for (int target : targets) {
                    Entity ally = level.getEntity(target);
                    float size = ally == null ? 1.2F : Math.max(ally.getBbWidth(), ally.getBbHeight()) * 0.65F + 0.2F;
                    FxWorld.add(new FxWorld.Dome(Anchor.center(target, Vec3.ZERO), size, GOLD, 18, false, 0.45F, 5));
                    bloom(level, target, GOLD, 0.7F);
                }
            }
            case "healer_call_back" -> {
                FxWorld.removeIf(v -> v.owner == id && v.tag == SkillFx.TAG_MARKER);
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Pillar(Anchor.entity(target, Vec3.ZERO), 0.9F, 9.0F, HOLY, 26));
                    bloom(level, target, LIFE, 1.6F);
                    Entity ally = level.getEntity(target);
                    if (ally != null) {
                        Fx.fountain(level, ModParticles.PETAL.get(), ally.position(), 24, LIFE, 0.22F, 40, 0.3, 0.12);
                    }
                }
                FxWorld.shake(to, 3, 0.3F);
            }
            case "healer_prayer_circle", "healer_resonant_healing", "healer_silence_sweep" -> {
                int color = fx.equals("healer_silence_sweep") ? WHITE : fx.equals("healer_resonant_healing") ? SAGE : LIFE;
                float r = Math.max(2.0F, radius);
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), r, FxTextures.SIGIL_STAR, color, 22, 0.03F, 5, 0.75F).noInner());
                FxWorld.add(new FxWorld.Shockwave(from, 0.4F, r, 14, color, 0.8F, 0.8F));
                if (!fx.equals("healer_prayer_circle")) {
                    chime(from, color, r, 3);
                }
                for (int target : targets) {
                    bloom(level, target, color, 0.9F);
                    if (!fx.equals("healer_prayer_circle")) {
                        cleanseSparkle(level, target);
                    }
                }
            }
            case "healer_blessing_light" -> {
                float r = Math.max(2.0F, radius);
                FxWorld.add(new FxWorld.Flare(Anchor.entity(id, new Vec3(0.0, 2.6, 0.0)), 2.2F, GOLD, 24, FxTextures.GLOW, 0.0F, 4));
                FxWorld.add(new FxWorld.Shockwave(from, 0.4F, r, 16, GOLD, 0.5F, 0.6F));
                for (int target : targets) {
                    bloom(level, target, GOLD, 0.8F);
                }
            }
            case "healer_great_call" -> {
                float r = Math.max(3.0F, radius);
                FxWorld.add(new FxWorld.Pillar(Anchor.at(from), r * 0.4F, 14.0F, HOLY, 34));
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), r, FxTextures.SIGIL_STAR, LIFE, 40, 0.02F, 8, 0.85F));
                FxWorld.add(new FxWorld.Shockwave(from, 0.5F, r * 1.2F, 20, LIFE, 1.6F, 0.9F));
                chime(from, BELL, r, 4);
                for (int target : targets) {
                    bloom(level, target, LIFE, 1.4F);
                    FxWorld.add(new FxWorld.Pillar(Anchor.entity(target, Vec3.ZERO), 0.6F, 6.0F, LIFE, 20));
                }
                Fx.fountain(level, ModParticles.PETAL.get(), from, 60, LIFE, 0.22F, 50, 0.45, 0.35);
                FxWorld.shake(from, 6, 0.5F);
            }
            case "healer_bond_sanctuary" -> healZone(level, from, radius, ticks, GOLD, false, FxTextures.SIGIL_OATH);
            case "healer_healing_rain" -> healZone(level, to, radius, ticks, LIFE, true, FxTextures.SIGIL_STAR);
            case "healer_calming_circle" -> {
                healZone(level, to, radius, ticks, CALM, false, FxTextures.SIGIL_FROST);
                chime(to, CALM, radius, 3);
            }
            // ------------------------------------------------ ヒーラー：攻め
            case "healer_admonishing_bell" -> {
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, Vec3.ZERO), Anchor.center(target, Vec3.ZERO), 0.06F, FxTextures.BEAM, BELL,
                            8, 1.0F, 0.2F).extend(3));
                    Entity enemy = level.getEntity(target);
                    if (enemy != null) {
                        chime(enemy.position(), BELL, 1.6F, 2);
                    }
                    strike(level, target, BELL, 1.0F, false);
                }
            }
            case "healer_purifying_chime" -> {
                Vec3 dir = to.subtract(from);
                float yaw = SkillFx.yawOf(dir);
                float angle = skill != null && skill.angle().length > 0 && skill.angle()[0] > 0 ? skill.angle()[0] / 2.0F : 45.0F;
                for (int i = 0; i < 3; i++) {
                    float r = Math.max(2.0F, radius) * (0.45F + 0.25F * i);
                    int delay = 1 + i * 2;
                    int color = i == 2 ? WHITE : BELL;
                    FxWorld.later(delay, () -> FxWorld.add(new FxWorld.Slash(Anchor.at(from.add(0.0, 1.1, 0.0)), yaw, 0.0F, r, 0.35F, -angle,
                            angle, 8, 1, color)));
                }
                SkillFx.hitSparks(level, targets, BELL, 8);
            }
            case "healer_heavens_bell" -> {
                // 天から鈴が落ちてくる（着弾で鳴り響く）
                int travel = Math.max(6, ticks);
                FxWorld.add(new FxWorld.Sigil(Anchor.at(to), Math.max(1.5F, radius), FxTextures.SIGIL_STAR, BELL, travel + 6, 0.04F, 6, 0.8F))
                        .tag(id, SkillFx.TAG_MARKER);
                FxWorld.add(new FxWorld.Meteor(to.add(0.0, 14.0, 0.0), to.add(0.0, 0.8, 0.0), travel, 1.8F, BELL, null))
                        .tag(id, SkillFx.TAG_MARKER);
            }
            // ------------------------------------------------ アーチャー
            case "archer_marking_arrow", "archer_pinning_arrow", "archer_burst_arrow", "archer_piercing_shot", "archer_spread_arrows",
                 "archer_triple_shot", "archer_back_leap" -> {
                int color = fx.equals("archer_marking_arrow") ? MARK : fx.equals("archer_piercing_shot") ? STAR : ARROW;
                float size = fx.equals("archer_piercing_shot") ? 1.6F : 1.0F;
                bowFlash(level, caster, from, to, color, size);
                if (fx.equals("archer_back_leap") && caster != null) {
                    Fx.burst(level, ModParticles.SMOKE.get(), caster.position().add(0.0, 0.2, 0.0), 8, 0xB8B0A0, 0.4F, 18, 0.12);
                    SkillFx.debris(level, caster.position(), 10);
                }
                if (fx.equals("archer_spread_arrows") || fx.equals("archer_triple_shot")) {
                    Vec3 dir = to.subtract(from);
                    float yaw = SkillFx.yawOf(dir);
                    float half = Math.max(8.0F, radius / 2.0F);
                    FxWorld.add(new FxWorld.Slash(Anchor.at(from.add(0.0, -0.2, 0.0)), yaw, 0.0F, 1.6F, 0.25F, -half, half, 6, 1, WIND));
                }
            }
            case "archer_drill_arrow", "archer_full_draw", "archer_sky_piercer", "archer_shooting_star" -> {
                boolean star = fx.equals("archer_shooting_star") || fx.equals("archer_sky_piercer");
                float charge = fx.equals("archer_full_draw") || fx.equals("archer_sky_piercer") ? Mth.clamp(power, 0.0F, 1.0F) : 1.0F;
                int color = star ? STAR : ARROW;
                float width = (star ? 0.7F : 0.35F) * (0.6F + 0.6F * charge);
                FxWorld.removeIf(v -> v.owner == id && v.tag == SkillFx.TAG_CAST);
                FxWorld.add(new FxWorld.Beam(Anchor.at(from), Anchor.at(to), width, FxTextures.BEAM, color, 10, 0.5F, 0.6F).extend(2));
                FxWorld.add(new FxWorld.Beam(Anchor.at(from), Anchor.at(to), width * 0.4F, FxTextures.LIGHTNING, WHITE, 8, 1.0F, 1.0F)
                        .extend(2));
                spiral(level, from, to, star ? STAR : WIND, star ? 3 : 2);
                Fx.line(level, ModParticles.FEATHER.get(), from, to, 10, 0xE8FFF0, 0.14F, 24, 0.06);
                bowFlash(level, caster, from, to, color, 1.4F + charge);
                SkillFx.hitSparks(level, targets, color, 12);
                if (star) {
                    Fx.line(level, ModParticles.STAR.get(), from, to, 24, STAR, 0.3F, 26, 0.1);
                    FxWorld.shake(from, 5, 0.6F * charge + 0.2F);
                }
            }
            case "archer_ricochet" -> {
                Anchor last = Anchor.at(from);
                for (int i = 0; i < targets.length; i++) {
                    Anchor next = Anchor.center(targets[i], Vec3.ZERO);
                    Anchor start = last;
                    int target = targets[i];
                    FxWorld.later(1 + i * 2, () -> {
                        FxWorld.add(new FxWorld.Beam(start, next, 0.12F, FxTextures.BEAM, ARROW, 6, 1.0F, 0.5F).extend(2));
                        SkillFx.hitSparks(level, new int[]{target}, ARROW, 8);
                    });
                    last = next;
                }
                bowFlash(level, caster, from, to, ARROW, 1.0F);
            }
            case "archer_whirl_arrows" -> {
                float yaw = caster != null ? caster.getYRot() : 0.0F;
                float r = Math.max(2.0F, radius);
                FxWorld.add(new FxWorld.Slash(Anchor.entity(id, new Vec3(0.0, 1.0, 0.0)), yaw, 0.0F, r * 0.85F, 0.5F, 0.0F, 360.0F, 10, 4,
                        ARROW));
                FxWorld.add(new FxWorld.Shockwave(from, 0.5F, r, 10, WIND, 0.4F, 0.6F));
                Fx.ring(level, ModParticles.FEATHER.get(), from.add(0.0, 1.0, 0.0), r * 0.6, 16, 0xE8FFF0, 0.16F, 26, 0.12, 0.03);
                SkillFx.hitSparks(level, targets, ARROW, 8);
            }
            case "archer_arrow_rain" -> {
                int life = Math.max(20, ticks);
                float r = Math.max(1.5F, radius);
                FxWorld.add(new FxWorld.Sigil(Anchor.at(to), r, FxTextures.SIGIL_STAR, WIND, life, 0.01F, 6, 0.6F));
                FxWorld.add(new FxWorld.Emitter(Anchor.at(to), life, center -> {
                    for (int i = 0; i < 2; i++) {
                        double angle = random.nextDouble() * Math.PI * 2.0;
                        double d = Math.sqrt(random.nextDouble()) * r;
                        Vec3 ground = center.add(Math.cos(angle) * d, 0.05, Math.sin(angle) * d);
                        FxWorld.add(new FxWorld.Spear(ground.add(0.6, 9.0, 0.3), ground, 6, ARROW));
                    }
                }));
                bowFlash(level, caster, from, to.add(0.0, 10.0, 0.0), ARROW, 1.2F);
            }
            case "archer_arrow_storm" -> {
                int life = Math.max(10, ticks);
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 2.4F, FxTextures.SIGIL_STAR, WIND, life, 0.08F, 6, 0.75F));
                FxWorld.add(new FxWorld.Emitter(Anchor.entity(id, Vec3.ZERO), life, at ->
                        Fx.ring(level, ModParticles.FEATHER.get(), at.add(0.0, 0.4, 0.0), 1.6, 2, 0xE8FFF0, 0.14F, 20, 0.05, 0.08)));
            }
            case "archer_gale_dance", "archer_hawks_gaze", "archer_sniper_stance" -> {
                int life = Math.max(20, Math.min(ticks, 200));
                switch (fx) {
                    case "archer_gale_dance" -> {
                        FxWorld.add(new FxWorld.Shockwave(from, 0.3F, 2.6F, 14, WIND, 1.2F, 0.8F));
                        FxWorld.add(new FxWorld.Emitter(Anchor.entity(id, Vec3.ZERO), Math.min(life, 60), at ->
                                Fx.ring(level, ModParticles.FEATHER.get(), at.add(0.0, 0.2 + random.nextDouble() * 1.6, 0.0), 0.9, 1,
                                        0xE8FFF0, 0.14F, 18, 0.0, 0.05)));
                    }
                    case "archer_hawks_gaze" -> {
                        Entity self = level.getEntity(id);
                        float height = self == null ? 1.8F : self.getBbHeight();
                        FxWorld.add(new FxWorld.Flare(Anchor.entity(id, new Vec3(0.0, height + 0.5, 0.0)), 1.6F, STAR, 24, FxTextures.STAR, 0.15F,
                                3));
                        FxWorld.add(new FxWorld.Shockwave(from, 0.4F, 6.0F, 18, STAR, 0.3F, 0.5F));
                    }
                    default -> {
                        FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 1.5F, FxTextures.SIGIL_STAR, WIND, Math.min(life, 80),
                                0.02F, 6, 0.8F));
                        FxWorld.add(new FxWorld.LinePreview(id, 14.0F, 0.6F, WIND, 24));
                    }
                }
            }
            // ------------------------------------------------ ローグ
            case "rogue_flurry" -> slashesOn(level, targets, BLADE, 4, 2, 1.0F);
            case "rogue_venom_blade" -> {
                slashesOn(level, targets, VENOM, 2, 2, 1.0F);
                poisonDrip(level, targets);
            }
            case "rogue_corroding_edge" -> {
                for (int target : targets) {
                    cross(level, target, VENOM);
                }
                poisonDrip(level, targets);
            }
            case "rogue_vital_strike" -> {
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, Vec3.ZERO), Anchor.center(target, Vec3.ZERO), 0.25F, FxTextures.BEAM, BLADE,
                            5, 0.5F, 0.0F).extend(2));
                    strike(level, target, BLADE, 1.0F + 0.3F * Math.max(0.0F, power - 1.0F), true);
                }
            }
            case "rogue_shadow_burial" -> {
                for (int target : targets) {
                    cross(level, target, SHADOW);
                    Entity enemy = level.getEntity(target);
                    if (enemy != null) {
                        Fx.burst(level, ModParticles.SMOKE.get(), enemy.position().add(0.0, 0.5, 0.0), 16, DUSK, 0.6F, 30, 0.15);
                        FxWorld.add(new FxWorld.Shockwave(enemy.position(), 0.3F, 2.6F, 12, SHADOW, 0.6F, 0.8F));
                        FxWorld.shake(enemy.position(), 4, 0.4F);
                    }
                }
            }
            case "rogue_toxic_burst" -> {
                for (int target : targets) {
                    Entity enemy = level.getEntity(target);
                    if (enemy == null) {
                        continue;
                    }
                    Vec3 c = enemy.position().add(0.0, enemy.getBbHeight() * 0.5, 0.0);
                    FxWorld.add(new FxWorld.Shockwave(enemy.position(), 0.3F, 3.0F, 12, VENOM, 0.8F, 0.9F));
                    FxWorld.add(new FxWorld.Flare(Anchor.at(c), 2.4F, VENOM, 8, FxTextures.GLOW, 0.0F, 1));
                    Fx.burst(level, ModParticles.BUBBLE.get(), c, 24, VENOM, 0.18F, 24, 0.35);
                    Fx.burst(level, ModParticles.SMOKE.get(), c, 12, 0x6A9A40, 0.5F, 26, 0.15);
                }
            }
            case "rogue_shadow_strike", "monk_shrink_step" -> {
                boolean monk = fx.startsWith("monk");
                Fx.burst(level, ModParticles.SMOKE.get(), from.add(0.0, 0.8, 0.0), 12, monk ? 0xC8C0B0 : DUSK, 0.5F, 24, 0.1);
                FxWorld.add(new FxWorld.Flare(Anchor.at(from.add(0.0, 1.0, 0.0)), 1.6F, monk ? KI : SHADOW, 8, FxTextures.GLOW, 0.0F, 1));
                Fx.burst(level, ModParticles.SMOKE.get(), to.add(0.0, 0.8, 0.0), 10, monk ? 0xC8C0B0 : DUSK, 0.45F, 20, 0.1);
                for (int target : targets) {
                    if (monk) {
                        strike(level, target, FIST, 1.1F, true);
                    } else {
                        cross(level, target, BLADE);
                    }
                }
            }
            case "rogue_shadow_run" -> {
                Fx.line(level, ModParticles.SMOKE.get(), from.add(0.0, 0.6, 0.0), to.add(0.0, 0.6, 0.0), 14, DUSK, 0.45F, 24, 0.05);
                FxWorld.add(new FxWorld.Beam(Anchor.at(from.add(0.0, 0.9, 0.0)), Anchor.at(to.add(0.0, 0.9, 0.0)), 0.8F, FxTextures.BEAM,
                        SHADOW, 8, 0.5F, 0.0F));
            }
            case "rogue_shadow_stitch" -> {
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Beam(Anchor.at(from), Anchor.center(target, Vec3.ZERO), 0.12F, FxTextures.BEAM, SHADOW, 10, 1.0F,
                            0.3F).extend(3));
                    Entity ally = level.getEntity(target);
                    if (ally != null) {
                        Fx.burst(level, ModParticles.SMOKE.get(), ally.position().add(0.0, 0.6, 0.0), 10, DUSK, 0.45F, 22, 0.1);
                    }
                }
                Fx.burst(level, ModParticles.SMOKE.get(), from, 12, DUSK, 0.5F, 24, 0.1);
            }
            case "rogue_phantom_blades", "rogue_thousand_blades" -> {
                boolean thousand = fx.equals("rogue_thousand_blades");
                float r = Math.max(2.0F, radius);
                int count = thousand ? 10 : 5;
                for (int i = 0; i < count; i++) {
                    float yaw = random.nextFloat() * 360.0F;
                    float tilt = (random.nextFloat() - 0.5F) * 50.0F;
                    int delay = 1 + i * (thousand ? 1 : 2);
                    int color = i % 2 == 0 ? BLADE : SHADOW;
                    FxWorld.later(delay, () -> FxWorld.add(new FxWorld.Slash(Anchor.entity(id, new Vec3(0.0, 1.0, 0.0)), yaw, tilt, r * 0.8F,
                            0.35F, -60.0F, 60.0F, 7, 2, color)));
                }
                if (thousand) {
                    FxWorld.add(new FxWorld.Shockwave(from, 0.5F, r * 1.1F, 16, SHADOW, 1.0F, 0.9F));
                    for (int target : targets) {
                        slashes(level, target, BLADE, 5, 2, 1.2F);
                    }
                    FxWorld.shake(from, 6, 0.6F);
                } else {
                    SkillFx.hitSparks(level, targets, BLADE, 8);
                }
            }
            case "rogue_smoke_bomb" -> {
                int life = Math.max(20, ticks);
                float r = Math.max(2.0F, radius);
                Fx.burst(level, ModParticles.SMOKE.get(), from.add(0.0, 0.8, 0.0), 40, SMOKE_GRAY, 0.9F, 40, 0.25);
                FxWorld.add(new FxWorld.Flare(Anchor.at(from.add(0.0, 0.8, 0.0)), 2.4F, WHITE, 5, FxTextures.GLOW, 0.0F, 1));
                FxWorld.add(new FxWorld.Emitter(Anchor.at(from), life, center ->
                        Fx.scatter(level, ModParticles.SMOKE.get(), center, r, 2, SMOKE_GRAY, 0.8F, 34, 1.6, new Vec3(0.0, 0.01, 0.0))));
            }
            case "rogue_poison_bloom" -> {
                int life = Math.max(20, ticks);
                float r = Math.max(2.0F, radius);
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), r, FxTextures.SIGIL_STAR, VENOM, life, 0.01F, 8, 0.7F));
                FxWorld.add(new FxWorld.Shockwave(from, 0.4F, r, 16, VENOM, 1.0F, 0.9F));
                Fx.ring(level, ModParticles.PETAL.get(), from.add(0.0, 0.3, 0.0), r * 0.5, 24, VENOM, 0.24F, 40, 0.1, 0.06);
                poisonCloud(level, from, r, life);
            }
            case "rogue_poison_mist" -> poisonCloud(level, to, Math.max(1.5F, radius), Math.max(20, ticks));
            case "rogue_binding_threads" -> {
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, Vec3.ZERO), Anchor.center(target, Vec3.ZERO), 0.05F, FxTextures.BEAM, BLADE,
                            16, 1.5F, 0.0F).extend(4));
                    Entity enemy = level.getEntity(target);
                    if (enemy != null) {
                        Fx.ring(level, ModParticles.RUNE.get(), enemy.position().add(0.0, 0.1, 0.0), enemy.getBbWidth() * 0.6 + 0.3, 6, BLADE,
                                0.2F, 20, 0.0, 0.01);
                    }
                }
            }
            case "rogue_numbing_needle" -> bowFlash(level, caster, from, to, VENOM, 0.6F);
            case "rogue_envenom" -> {
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 1.1F, FxTextures.SIGIL_STAR, VENOM, 20, 0.05F, 4, 0.7F));
                if (caster != null) {
                    Fx.fountain(level, ModParticles.BUBBLE.get(), caster.position().add(0.0, 0.8, 0.0), 14, VENOM, 0.16F, 22, 0.15, 0.08);
                }
            }
            case "rogue_shadow_double" -> {
                FxWorld.add(new FxWorld.Flare(Anchor.center(id, Vec3.ZERO), 3.0F, SHADOW, 10, FxTextures.GLOW, 0.0F, 2));
                Fx.ring(level, ModParticles.SMOKE.get(), from.add(0.0, 0.3, 0.0), 0.9, 12, DUSK, 0.45F, 26, 0.06, 0.02);
            }
            // ------------------------------------------------ モンク
            case "monk_jab_combo" -> strikesOn(level, targets, FIST, 0.8F, false);
            case "monk_palm_strike" -> {
                strikesOn(level, targets, FIST, 1.0F, true);
                pushWave(level, from, targets, FIST);
            }
            case "monk_hakkei" -> {
                for (int target : targets) {
                    strike(level, target, KI, 1.6F + 0.3F * Math.max(0.0F, power - 1.0F), true);
                    Entity enemy = level.getEntity(target);
                    if (enemy != null) {
                        FxWorld.add(new FxWorld.Shockwave(enemy.position().add(0.0, enemy.getBbHeight() * 0.5, 0.0), 0.3F, 2.2F, 9, KI, 0.3F,
                                0.8F));
                        Fx.burst(level, ModParticles.GLOW.get(), enemy.position().add(0.0, enemy.getBbHeight() * 0.5, 0.0), 12, KI, 0.35F, 14,
                                0.3);
                    }
                }
            }
            case "monk_inch_punch" -> {
                strikesOn(level, targets, FIST_HOT, 1.2F, true);
                FxWorld.add(new FxWorld.Dome(Anchor.center(id, Vec3.ZERO), 1.2F, GOLD, 12, false, 0.35F, 3));
            }
            case "monk_hundred_bond" -> {
                strikesOn(level, targets, FIST, 1.0F, true);
                FxWorld.add(new FxWorld.Flare(Anchor.center(id, Vec3.ZERO), 2.2F, FIST_HOT, 10, FxTextures.STAR, 0.2F, 2));
            }
            case "monk_thunder_fist" -> {
                float charge = Mth.clamp(power, 0.0F, 1.0F);
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Lightning(Anchor.center(id, Vec3.ZERO), Anchor.center(target, Vec3.ZERO), 0.12F + 0.12F * charge,
                            THUNDER, 7, 0.8F));
                    strike(level, target, THUNDER, 1.2F + charge, true);
                }
            }
            case "monk_turning_throw" -> {
                for (int target : targets) {
                    Entity enemy = level.getEntity(target);
                    float yaw = caster != null ? caster.getYRot() : 0.0F;
                    FxWorld.add(new FxWorld.Slash(Anchor.center(target, Vec3.ZERO), yaw + 90.0F, 90.0F, 1.4F, 0.5F, -90.0F, 90.0F, 10, 4, FIST));
                    if (enemy != null) {
                        int t = target;
                        FxWorld.later(8, () -> {
                            Entity landed = level.getEntity(t);
                            if (landed != null) {
                                SkillFx.debris(level, landed.position(), 16);
                                FxWorld.add(new FxWorld.Shockwave(landed.position(), 0.3F, 2.0F, 10, FIST, 0.4F, 0.8F));
                                FxWorld.shake(landed.position(), 4, 0.4F);
                            }
                        });
                    }
                }
            }
            case "monk_ki_share" -> {
                beams(id, targets, KI, 0.09F, 12);
                for (int target : targets) {
                    bloom(level, target, KI, 0.9F);
                }
            }
            case "monk_collapsing_fist" -> {
                FxWorld.add(new FxWorld.Beam(Anchor.at(from.add(0.0, 0.9, 0.0)), Anchor.at(to.add(0.0, 0.9, 0.0)), 0.9F, FxTextures.BEAM,
                        FIST, 8, 0.5F, 0.0F));
                Fx.line(level, ModParticles.GLOW.get(), from.add(0.0, 0.2, 0.0), to.add(0.0, 0.2, 0.0), 10, FIST, 0.35F, 12, 0.04);
                strikesOn(level, targets, FIST_HOT, 1.4F, true);
            }
            case "monk_mountain_breaker" -> {
                FxWorld.add(new FxWorld.Emitter(Anchor.center(id, Vec3.ZERO), Math.max(4, ticks), at ->
                        Fx.add(level, Fx.glow(FIST, 0.3F, 10), at, Vec3.ZERO))).tag(id, SkillFx.TAG_LEAP);
                FxWorld.add(new FxWorld.Sigil(Anchor.at(to), Math.max(1.5F, radius), FxTextures.SIGIL_OATH, FIST, Math.max(4, ticks) + 4, 0.1F, 3,
                        0.6F)).tag(id, SkillFx.TAG_LEAP);
            }
            case "monk_spin_kick" -> {
                float yaw = caster != null ? caster.getYRot() : 0.0F;
                float r = Math.max(2.0F, radius);
                FxWorld.add(new FxWorld.Slash(Anchor.entity(id, new Vec3(0.0, 0.5, 0.0)), yaw, 0.0F, r * 0.85F, 0.6F, 0.0F, 360.0F, 9, 4, FIST));
                Fx.ring(level, ModParticles.SMOKE.get(), from.add(0.0, 0.1, 0.0), r * 0.5, 14, 0xC8B8A0, 0.4F, 18, 0.15, 0.01);
                strikesOn(level, targets, FIST, 0.8F, false);
                FxWorld.shake(from, 3, 0.3F);
            }
            case "monk_rising_dragon" -> {
                float r = Math.max(2.0F, radius);
                FxWorld.add(new FxWorld.Pillar(Anchor.at(from), r * 0.45F, 6.0F, FIST, 16));
                FxWorld.add(new FxWorld.Emitter(Anchor.at(from), 14, at -> {
                    for (int i = 0; i < 3; i++) {
                        double angle = (level.getGameTime() * 0.6 + i * 2.1) % (Math.PI * 2.0);
                        Vec3 p = at.add(Math.cos(angle) * r * 0.5, 0.2, Math.sin(angle) * r * 0.5);
                        Fx.add(level, Fx.glow(i == 0 ? FIST_HOT : FIST, 0.35F, 16), p, new Vec3(0.0, 0.35, 0.0));
                    }
                }));
                strikesOn(level, targets, FIST_HOT, 1.0F, true);
            }
            case "monk_ki_vortex" -> {
                float r = Math.max(2.0F, radius);
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), r, FxTextures.SIGIL_ARCANE, KI, 18, -0.12F, 3, 0.75F));
                FxWorld.add(new FxWorld.Shockwave(from, r, 0.3F, 12, KI, 0.8F, 0.8F));
                Fx.converge(level, ModParticles.GLOW.get(), from.add(0.0, 0.8, 0.0), r, 30, KI, 0.3F, 12);
                strikesOn(level, targets, KI, 0.9F, false);
            }
            case "monk_ki_bullet" -> {
                Vec3 dir = to.subtract(from);
                Vec3 at = from.add(dir.lengthSqr() < 1.0E-6 ? Vec3.ZERO : dir.normalize().scale(0.9));
                FxWorld.add(new FxWorld.Flare(Anchor.at(at), 1.4F, KI, 6, FxTextures.GLOW, 0.0F, 1));
                FxWorld.add(new FxWorld.Shockwave(at.add(0.0, -0.4, 0.0), 0.2F, 1.0F, 6, KI, 0.3F, 0.6F));
            }
            case "monk_flowing_stance" -> {
                int life = Math.max(10, Math.min(ticks, 100));
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 1.4F, FxTextures.SIGIL_FROST, KI, life, 0.04F, 4, 0.7F));
                FxWorld.add(new FxWorld.Emitter(Anchor.entity(id, Vec3.ZERO), life, at -> {
                    if (random.nextInt(2) == 0) {
                        Fx.ring(level, ModParticles.GLOW.get(), at.add(0.0, 0.6 + random.nextDouble(), 0.0), 0.9, 1, KI, 0.2F, 14, 0.0, 0.0);
                    }
                }));
            }
            case "monk_heaven_earth" -> {
                int life = Math.max(20, Math.min(ticks, 200));
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 3.0F, FxTextures.SIGIL_OATH, FIST, life, 0.03F, 8, 0.8F));
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, new Vec3(0.0, 0.05, 0.0)), 2.2F, FxTextures.SIGIL_FROST, KI, life, -0.05F, 8,
                        0.8F));
                FxWorld.add(new FxWorld.Pillar(Anchor.at(from), 1.0F, 10.0F, FIST_HOT, 24));
                FxWorld.add(new FxWorld.Shockwave(from, 0.5F, 6.0F, 20, FIST, 1.6F, 0.9F));
                FxWorld.shake(from, 8, 0.7F);
            }
            case "monk_hundred_fists" -> {
                int life = Math.max(10, ticks);
                FxWorld.add(new FxWorld.Emitter(Anchor.entity(id, Vec3.ZERO), life, at -> {
                    Entity self = level.getEntity(id);
                    if (self == null) {
                        return;
                    }
                    Vec3 front = at.add(SkillFx.lookOffset(self, 1.3 + random.nextDouble() * 1.4))
                            .add((random.nextDouble() - 0.5) * 1.2, 0.7 + random.nextDouble() * 0.8, (random.nextDouble() - 0.5) * 1.2);
                    Fx.add(level, Fx.glow(random.nextBoolean() ? FIST : FIST_HOT, 0.5F, 4), front, Vec3.ZERO);
                    Fx.burst(level, ModParticles.SPARK.get(), front, 2, FIST_HOT, 0.06F, 6, 0.3);
                }));
            }
            // ------------------------------------------------ ナイト（足した段）
            case "knight_iron_pledge" -> {
                for (int target : targets) {
                    if (target == id) {
                        continue;
                    }
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, Vec3.ZERO), Anchor.center(target, Vec3.ZERO), 0.08F, FxTextures.BEAM, GOLD,
                            Math.max(20, Math.min(ticks, 100)), 0.8F, 0.05F).extend(5));
                    FxWorld.add(new FxWorld.Flare(Anchor.center(target, Vec3.ZERO), 1.5F, KNIGHT, 14, FxTextures.SHIELD, 0.0F, 3));
                }
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), Math.max(2.0F, radius), FxTextures.SIGIL_OATH, KNIGHT, 24, 0.03F, 5, 0.8F));
                FxWorld.add(new FxWorld.Shockwave(from, 0.4F, Math.max(2.0F, radius), 14, GOLD, 0.8F, 0.7F));
            }
            case "knight_shield_bash" -> {
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Flare(Anchor.center(target, Vec3.ZERO), 1.8F, KNIGHT, 8, FxTextures.SHIELD, 0.0F, 1));
                    strike(level, target, STEEL, 1.1F, true);
                    SkillFx.tauntMark(level, target, 18);
                }
            }
            case "knight_great_wheel" -> {
                int life = Math.max(10, ticks);
                boolean empowered = power > 1.01F;
                int color = empowered ? GOLD : STEEL;
                FxWorld.add(new FxWorld.Emitter(Anchor.entity(id, Vec3.ZERO), life, at -> {
                    if (level.getGameTime() % 4 == 0) {
                        Entity self = level.getEntity(id);
                        float yaw = self == null ? 0.0F : self.getYRot() + level.getGameTime() * 40.0F;
                        FxWorld.add(new FxWorld.Slash(Anchor.entity(id, new Vec3(0.0, 0.95, 0.0)), yaw, 0.0F, 3.0F, 0.9F, 0.0F, 360.0F, 6, 3,
                                color));
                        Fx.ring(level, ModParticles.SPARK.get(), at.add(0.0, 0.9, 0.0), 2.6, 6, color, 0.08F, 8, 0.2, 0.04);
                    }
                }));
                if (empowered) {
                    FxWorld.add(new FxWorld.Flare(Anchor.center(id, Vec3.ZERO), 3.0F, GOLD, 8, FxTextures.STAR, 0.2F, 2));
                }
            }
            case "knight_earth_splitter" -> {
                FxWorld.add(new FxWorld.GroundCut(from, to, Math.max(1.0F, radius), EARTH, 30, 6));
                long seed = random.nextLong();
                Vec3 dir = to.subtract(from);
                double length = dir.length();
                int count = Math.max(2, (int) (length / 1.6));
                for (int i = 1; i <= count; i++) {
                    Vec3 spot = from.add(dir.scale(i / (double) count));
                    long s = seed + i;
                    FxWorld.later(i, () -> {
                        FxWorld.add(new FxWorld.Spikes(spot, 0.5F, 4, 1.4F, EARTH, 24, s));
                        SkillFx.debris(level, spot, 8);
                    });
                }
                SkillFx.hitSparks(level, targets, EARTH, 10);
                FxWorld.shake(from, 7, 0.8F);
            }
            // ------------------------------------------------ ウィザード（足した段）
            case "wizard_star_ring" -> {
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 1.8F, FxTextures.SIGIL_STAR, GOLD, 24, 0.06F, 4, 0.8F));
                Fx.ring(level, ModParticles.STAR.get(), from.add(0.0, 1.1, 0.0), 1.2, 12, GOLD, 0.25F, 20, 0.05, 0.02);
            }
            case "wizard_comet" -> {
                FxWorld.removeIf(v -> v.owner == id && v.tag == SkillFx.TAG_CAST);
                Vec3 dir = to.subtract(from);
                Vec3 at = from.add(dir.lengthSqr() < 1.0E-6 ? Vec3.ZERO : dir.normalize().scale(1.2));
                FxWorld.add(new FxWorld.Flare(Anchor.at(at), 3.0F, GOLD, 8, FxTextures.STAR, 0.3F, 1));
                Fx.burst(level, ModParticles.STAR.get(), at, 12, GOLD, 0.3F, 18, 0.3);
            }
            case "wizard_blizzard" -> {
                int life = Math.max(20, ticks);
                float r = Math.max(2.0F, radius);
                FxWorld.add(new FxWorld.Sigil(Anchor.at(to), r, FxTextures.SIGIL_FROST, ICE, life, 0.01F, 10, 0.8F));
                FxWorld.add(new FxWorld.Rim(to, r, 2.2F, FROST, life, 0.3F));
                FxWorld.add(new FxWorld.Emitter(Anchor.at(to), life, center -> {
                    Fx.scatter(level, ModParticles.SNOW.get(), center.add(-1.2, 4.0, -0.6), r, 6, ICE, 0.16F, 30, 0.4,
                            new Vec3(0.14, -0.16, 0.06));
                    if (random.nextInt(3) == 0) {
                        Fx.scatter(level, ModParticles.SHARD.get(), center.add(0.0, 2.5, 0.0), r, 1, ICE, 0.2F, 18, 0.3,
                                new Vec3(0.08, -0.25, 0.03));
                    }
                }));
            }
            case "wizard_absolute_zero" -> {
                float r = Math.max(3.0F, radius);
                FxWorld.add(new FxWorld.Flare(Anchor.at(from.add(0.0, 1.0, 0.0)), 8.0F, WHITE, 10, FxTextures.STAR, 0.1F, 1));
                FxWorld.add(new FxWorld.Shockwave(from, 0.5F, r * 1.2F, 22, ICE, 2.2F, 1.0F));
                FxWorld.add(new FxWorld.Shockwave(from, 0.3F, r * 0.7F, 16, WHITE, 1.0F, 0.9F));
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), r, FxTextures.SIGIL_FROST, ICE, 60, 0.004F, 4, 0.85F));
                FxWorld.add(new FxWorld.Spikes(from, r, 36, 2.6F, ICE, 80, random.nextLong()));
                FxWorld.add(new FxWorld.Spikes(from, r * 0.6F, 20, 1.8F, FROST, 70, random.nextLong()));
                Fx.burst(level, ModParticles.SNOW.get(), from.add(0.0, 1.0, 0.0), 80, ICE, 0.22F, 40, 0.8);
                Fx.burst(level, ModParticles.SHARD.get(), from.add(0.0, 1.0, 0.0), 40, ICE, 0.3F, 30, 0.7);
                FxWorld.shake(from, 14, 1.4F);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ================================================================ 着弾

    static boolean impact(ClientLevel level, String fx, SkillFxS2C message, @Nullable Entity caster) {
        int id = message.casterId();
        Vec3 at = message.to();
        float radius = message.radius();
        int[] targets = message.targets();
        RandomSource random = level.random;
        switch (fx) {
            case "archer_marking_arrow" -> {
                arrowHit(level, at, MARK);
                for (int target : targets) {
                    Entity enemy = level.getEntity(target);
                    float height = enemy == null ? 1.8F : enemy.getBbHeight();
                    FxWorld.add(new FxWorld.Flare(Anchor.entity(target, new Vec3(0.0, height + 0.5, 0.0)), 1.0F, MARK, 30, FxTextures.STAR, 0.1F,
                            3));
                }
            }
            case "archer_pinning_arrow" -> {
                arrowHit(level, at, ARROW);
                for (int target : targets) {
                    Entity enemy = level.getEntity(target);
                    if (enemy != null) {
                        for (int i = 0; i < 3; i++) {
                            double angle = i * Math.PI * 2.0 / 3.0 + random.nextDouble();
                            Vec3 ground = enemy.position().add(Math.cos(angle) * 0.9, 0.0, Math.sin(angle) * 0.9);
                            FxWorld.add(new FxWorld.Beam(Anchor.center(target, Vec3.ZERO), Anchor.at(ground), 0.14F, FxTextures.CHAIN, 0xE0E6F2,
                                    30, 2.0F, 0.0F).extend(3));
                        }
                    }
                }
            }
            case "archer_burst_arrow" -> {
                float r = Math.max(1.5F, radius);
                FxWorld.add(new FxWorld.Shockwave(at, 0.3F, r * 1.2F, 12, WIND, 0.8F, 0.9F));
                FxWorld.add(new FxWorld.Flare(Anchor.at(at), r * 1.4F, WHITE, 6, FxTextures.GLOW, 0.0F, 1));
                Fx.burst(level, ModParticles.FEATHER.get(), at, 16, 0xE8FFF0, 0.16F, 26, 0.35);
                Fx.burst(level, ModParticles.SPARK.get(), at, 20, ARROW, 0.08F, 10, 0.6);
                if (SkillFx.farFromCamera(at, 4.0)) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION, at.x, at.y, at.z, 0.0, 0.0, 0.0);
                }
                FxWorld.shake(at, 4, 0.4F);
            }
            case "archer_piercing_shot", "archer_spread_arrows", "archer_triple_shot", "archer_back_leap" ->
                    arrowHit(level, at, fx.equals("archer_piercing_shot") ? STAR : ARROW);
            case "rogue_numbing_needle" -> {
                Fx.burst(level, ModParticles.BUBBLE.get(), at, 8, VENOM, 0.14F, 20, 0.2);
                Fx.burst(level, ModParticles.SPARK.get(), at, 6, VENOM, 0.06F, 8, 0.3);
            }
            case "monk_ki_bullet" -> {
                FxWorld.add(new FxWorld.Flare(Anchor.at(at), 1.8F, KI, 6, FxTextures.STAR, 0.4F, 1));
                FxWorld.add(new FxWorld.Shockwave(at.add(0.0, -0.5, 0.0), 0.2F, 1.6F, 8, KI, 0.4F, 0.7F));
                Fx.burst(level, ModParticles.GLOW.get(), at, 10, KI, 0.3F, 12, 0.3);
            }
            case "monk_mountain_breaker" -> {
                FxWorld.removeIf(v -> v.owner == id && v.tag == SkillFx.TAG_LEAP);
                if (caster instanceof AbstractClientPlayer player) {
                    PlayerAnimations.play(player, "monk_slam_land");
                }
                float r = Math.max(2.0F, radius);
                FxWorld.add(new FxWorld.Shockwave(at, 0.5F, r * 1.3F, 14, FIST, 1.4F, 1.0F));
                FxWorld.add(new FxWorld.Shockwave(at, 0.3F, r * 0.8F, 10, FIST_HOT, 0.6F, 0.8F));
                FxWorld.add(new FxWorld.Flare(Anchor.at(at.add(0.0, 0.5, 0.0)), 3.0F, FIST_HOT, 6, FxTextures.STAR, 0.2F, 1));
                FxWorld.add(new FxWorld.Spikes(at, r * 0.7F, 10, 1.0F, EARTH, 24, random.nextLong()));
                Fx.fountain(level, ModParticles.SPARK.get(), at, 24, FIST, 0.1F, 14, 0.6, 0.5);
                SkillFx.debris(level, at, 40);
                FxWorld.shake(at, 9, 1.1F);
            }
            case "healer_heavens_bell" -> {
                FxWorld.removeIf(v -> v.owner == id && v.tag == SkillFx.TAG_MARKER);
                float r = Math.max(2.0F, radius);
                FxWorld.add(new FxWorld.Flare(Anchor.at(at.add(0.0, 1.2, 0.0)), 6.0F, WHITE, 10, FxTextures.STAR, 0.1F, 1));
                FxWorld.add(new FxWorld.Shockwave(at, 0.5F, r * 1.3F, 18, BELL, 1.6F, 1.0F));
                chime(at, BELL, r * 1.5F, 4);
                FxWorld.add(new FxWorld.Sigil(Anchor.at(at), r, FxTextures.SIGIL_STAR, BELL, 24, 0.04F, 2, 0.8F).noInner());
                Fx.burst(level, ModParticles.STAR.get(), at.add(0.0, 1.0, 0.0), 30, BELL, 0.3F, 24, 0.5);
                Fx.fountain(level, ModParticles.PETAL.get(), at, 20, LIFE, 0.2F, 40, 0.4, 0.3);
                SkillFx.debris(level, at, 20);
                FxWorld.shake(at, 8, 0.9F);
            }
            case "wizard_comet" -> {
                float r = Math.max(2.0F, radius);
                FxWorld.add(new FxWorld.Flare(Anchor.at(at.add(0.0, 0.8, 0.0)), 5.0F, WHITE, 10, FxTextures.STAR, 0.1F, 1));
                FxWorld.add(new FxWorld.Shockwave(at, 0.5F, r * 1.3F, 16, GOLD, 1.4F, 1.0F));
                FxWorld.add(new FxWorld.Sigil(Anchor.at(at), r, FxTextures.SIGIL_STAR, GOLD, 20, 0.05F, 2, 0.8F).noInner());
                Fx.burst(level, ModParticles.STAR.get(), at.add(0.0, 0.8, 0.0), 40, GOLD, 0.35F, 26, 0.55);
                Fx.fountain(level, ModParticles.SPARK.get(), at, 30, GOLD, 0.1F, 16, 0.7, 0.4);
                if (SkillFx.farFromCamera(at, 5.0)) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 0.0, 0.0, 0.0);
                }
                SkillFx.debris(level, at, 24);
                FxWorld.shake(at, 9, 1.0F);
            }
            default -> {
                // 弾が当たった：職業の色で小さく光る
                if (fx.startsWith("archer_")) {
                    arrowHit(level, at, ARROW);
                } else if (fx.startsWith("rogue_")) {
                    Fx.burst(level, ModParticles.SPARK.get(), at, 8, BLADE, 0.07F, 8, 0.4);
                } else if (fx.startsWith("monk_")) {
                    Fx.burst(level, ModParticles.GLOW.get(), at, 8, KI, 0.3F, 10, 0.25);
                } else if (fx.startsWith("healer_")) {
                    Fx.burst(level, ModParticles.GLOW.get(), at, 10, BELL, 0.3F, 12, 0.2);
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    // ================================================================ 発動（陣の一撃・連打の一撃・星の環など）

    static boolean trigger(ClientLevel level, String fx, SkillFxS2C message, @Nullable Entity caster) {
        int id = message.casterId();
        Vec3 at = message.to();
        int[] targets = message.targets();
        boolean last = message.power() >= 0.99F;
        switch (fx) {
            case "archer_arrow_storm" -> {
                // 矢嵐：いちばん近い敵へ、光の矢が飛ぶ
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, new Vec3(0.0, 0.4, 0.0)), Anchor.center(target, Vec3.ZERO), 0.12F,
                            FxTextures.BEAM, ARROW, 5, 1.0F, 0.6F).extend(2));
                }
                arrowHit(level, at, ARROW);
            }
            case "wizard_star_ring" -> {
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, new Vec3(0.0, 0.3, 0.0)), Anchor.center(target, Vec3.ZERO), 0.1F,
                            FxTextures.BEAM, GOLD, 6, 1.0F, 0.5F).extend(2));
                }
                FxWorld.add(new FxWorld.Flare(Anchor.at(at), 1.4F, GOLD, 6, FxTextures.STAR, 0.4F, 1));
                Fx.burst(level, ModParticles.STAR.get(), at, 6, GOLD, 0.2F, 14, 0.25);
            }
            default -> {
                // 何度も当てる技の、1回ごとの当たり
                if (fx.startsWith("monk_")) {
                    for (int target : targets) {
                        strike(level, target, last ? FIST_HOT : FIST, last ? 1.2F : 0.7F, last);
                    }
                } else if (fx.startsWith("rogue_")) {
                    for (int target : targets) {
                        slashes(level, target, last ? WHITE : BLADE, 1, 0, last ? 1.2F : 0.9F);
                    }
                } else if (fx.startsWith("archer_")) {
                    arrowHit(level, at, ARROW);
                } else if (fx.startsWith("healer_")) {
                    chime(at.add(0.0, -0.8, 0.0), BELL, 1.2F, 1);
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    // ================================================================ 形の部品

    /** 使った人から相手たちへ、細い光の糸。 */
    private static void beams(int casterId, int[] targets, int color, float width, int life) {
        for (int target : targets) {
            if (target == casterId) {
                continue;
            }
            FxWorld.add(new FxWorld.Beam(Anchor.center(casterId, Vec3.ZERO), Anchor.center(target, Vec3.ZERO), width, FxTextures.BEAM, color,
                    life, 1.0F, 0.3F).extend(3));
        }
    }

    /** 癒しの光（味方の体に）：光の玉・足もとの小さな印・舞い上がる花びら。 */
    private static void bloom(ClientLevel level, int targetId, int color, float size) {
        Entity entity = level.getEntity(targetId);
        if (entity == null) {
            return;
        }
        Vec3 feet = entity.position();
        FxWorld.add(new FxWorld.Flare(Anchor.center(targetId, Vec3.ZERO), 1.5F * size, color, 12, FxTextures.GLOW, 0.0F, 3));
        FxWorld.add(new FxWorld.Sigil(Anchor.entity(targetId, Vec3.ZERO), 0.8F * size, FxTextures.SIGIL_STAR, color, 18, 0.05F, 4, 0.7F));
        Fx.ring(level, ModParticles.PETAL.get(), feet.add(0.0, 0.15, 0.0), 0.55 * size, (int) (8 * size) + 2, color, 0.2F, 30, 0.015, 0.07);
        Fx.fountain(level, ModParticles.GLOW.get(), feet.add(0.0, 0.2, 0.0), (int) (6 * size) + 2, HOLY, 0.28F, 18, 0.12, 0.05);
    }

    /** ほどいた（状態異常を消した）きらめき。 */
    private static void cleanseSparkle(ClientLevel level, int targetId) {
        Entity entity = level.getEntity(targetId);
        if (entity == null) {
            return;
        }
        Fx.burst(level, ModParticles.STAR.get(), entity.position().add(0.0, entity.getBbHeight() * 0.6, 0.0), 8, WHITE, 0.2F, 16, 0.25);
    }

    /** 鈴の響き：音の輪が少しずつ広がる。 */
    private static void chime(Vec3 at, int color, float radius, int rings) {
        for (int i = 0; i < rings; i++) {
            float r = radius * (0.6F + 0.4F * i / Math.max(1, rings - 1));
            FxWorld.later(1 + i * 3, () -> FxWorld.add(new FxWorld.Shockwave(at.add(0.0, 0.1, 0.0), 0.3F, r, 12, color, 0.35F, 0.7F)));
        }
        FxWorld.add(new FxWorld.Flare(Anchor.at(at.add(0.0, 1.3, 0.0)), 1.1F, color, 8, FxTextures.STAR, 0.4F, 1));
    }

    /** 癒し・守りの陣（地面の印と、舞う花びら。rain なら光の雨も）。 */
    private static void healZone(ClientLevel level, Vec3 at, float radius, int ticks, int color, boolean rain,
                                 net.minecraft.resources.ResourceLocation sigil) {
        int life = Math.max(20, ticks);
        float r = Math.max(1.5F, radius);
        FxWorld.add(new FxWorld.Sigil(Anchor.at(at), r, sigil, color, life, 0.008F, 10, 0.75F));
        FxWorld.add(new FxWorld.Rim(at, r, 0.8F, color, life, 0.35F));
        FxWorld.add(new FxWorld.Shockwave(at, 0.4F, r, 14, color, 0.8F, 0.8F));
        FxWorld.add(new FxWorld.Emitter(Anchor.at(at), life, center -> {
            if (rain) {
                Fx.scatter(level, ModParticles.GLOW.get(), center.add(0.0, 3.2, 0.0), r, 2, color, 0.16F, 22, 0.3, new Vec3(0.0, -0.14, 0.0));
            }
            if (level.random.nextInt(2) == 0) {
                Fx.scatter(level, ModParticles.PETAL.get(), center.add(0.0, 0.1, 0.0), r, 1, color, 0.2F, 40, 0.2, new Vec3(0.0, 0.04, 0.0));
            }
        }));
    }

    /** 弓を放った手もとの光と、散る羽根。 */
    private static void bowFlash(ClientLevel level, @Nullable Entity caster, Vec3 from, Vec3 to, int color, float size) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-6) {
            dir = caster != null ? caster.getLookAngle() : new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 at = from.add(dir.normalize().scale(0.9)).add(0.0, -0.2, 0.0);
        FxWorld.add(new FxWorld.Flare(Anchor.at(at), 1.1F * size, color, 5, FxTextures.STAR, 0.6F, 1));
        Fx.burst(level, ModParticles.FEATHER.get(), at, Math.max(1, (int) (3 * size)), 0xE8FFF0, 0.14F, 24, 0.08);
        Fx.burst(level, ModParticles.SPARK.get(), at, Math.max(2, (int) (5 * size)), color, 0.06F, 8, 0.25);
    }

    /** 矢が当たった光。 */
    private static void arrowHit(ClientLevel level, Vec3 at, int color) {
        FxWorld.add(new FxWorld.Flare(Anchor.at(at), 1.0F, color, 5, FxTextures.STAR, 0.5F, 1));
        Fx.burst(level, ModParticles.SPARK.get(), at, 8, color, 0.07F, 8, 0.4);
        Fx.burst(level, ModParticles.FEATHER.get(), at, 2, 0xE8FFF0, 0.12F, 20, 0.1);
    }

    /** 線に沿って、らせんを描く光の粒。 */
    private static void spiral(ClientLevel level, Vec3 from, Vec3 to, int color, int turns) {
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 1.0E-3) {
            return;
        }
        Vec3 forward = dir.scale(1.0 / length);
        Vec3 side = forward.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        }
        side = side.normalize();
        Vec3 up = side.cross(forward).normalize();
        int steps = (int) Math.min(80, length * 3);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double angle = t * turns * Math.PI * 2.0 * Math.max(1.0, length / 8.0);
            Vec3 p = from.add(dir.scale(t)).add(side.scale(Math.cos(angle) * 0.35)).add(up.scale(Math.sin(angle) * 0.35));
            Fx.add(level, Fx.glow(color, 0.16F, 12), p, Vec3.ZERO);
        }
    }

    /** 相手の体に、すばやい斬撃の弧を何度か（gap tick ごと）。 */
    private static void slashes(ClientLevel level, int targetId, int color, int count, int gap, float size) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            float yaw = random.nextFloat() * 360.0F;
            float tilt = (random.nextFloat() - 0.5F) * 120.0F;
            Runnable cut = () -> {
                FxWorld.add(new FxWorld.Slash(Anchor.center(targetId, Vec3.ZERO), yaw, tilt, 0.9F * size, 0.3F * size, -70.0F, 70.0F, 6, 2,
                        color));
                Entity target = level.getEntity(targetId);
                if (target != null) {
                    Fx.burst(level, ModParticles.SPARK.get(), target.position().add(0.0, target.getBbHeight() * 0.55, 0.0), 5, BLADE, 0.07F, 8,
                            0.4);
                }
            };
            int delay = i * gap;
            if (delay <= 0) {
                cut.run();
            } else {
                FxWorld.later(delay, cut);
            }
        }
    }

    private static void slashesOn(ClientLevel level, int[] targets, int color, int count, int gap, float size) {
        for (int target : targets) {
            slashes(level, target, color, count, gap, size);
        }
    }

    /** 十字の斬り（2本の弧を交差させる）。 */
    private static void cross(ClientLevel level, int targetId, int color) {
        float yaw = level.random.nextFloat() * 360.0F;
        FxWorld.add(new FxWorld.Slash(Anchor.center(targetId, Vec3.ZERO), yaw, 45.0F, 1.1F, 0.4F, -70.0F, 70.0F, 8, 2, color));
        FxWorld.later(2, () -> FxWorld.add(new FxWorld.Slash(Anchor.center(targetId, Vec3.ZERO), yaw, -45.0F, 1.1F, 0.4F, -70.0F, 70.0F, 8, 2,
                color)));
        Entity entity = level.getEntity(targetId);
        if (entity != null) {
            Fx.burst(level, ModParticles.SPARK.get(), entity.position().add(0.0, entity.getBbHeight() * 0.55, 0.0), 10, color, 0.08F, 10, 0.5);
        }
    }

    /** 毒がしたたる泡。 */
    private static void poisonDrip(ClientLevel level, int[] targets) {
        for (int target : targets) {
            Entity entity = level.getEntity(target);
            if (entity != null) {
                Fx.burst(level, ModParticles.BUBBLE.get(), entity.position().add(0.0, entity.getBbHeight() * 0.6, 0.0), 8, VENOM, 0.14F, 20,
                        0.15);
            }
        }
    }

    /** 毒の霧（地面に低くたまる緑の煙と、はじける泡）。 */
    private static void poisonCloud(ClientLevel level, Vec3 at, float radius, int life) {
        FxWorld.add(new FxWorld.Sigil(Anchor.at(at), radius, FxTextures.SIGIL_ARCANE, VENOM, life, 0.006F, 8, 0.45F));
        FxWorld.add(new FxWorld.Emitter(Anchor.at(at), life, center -> {
            Fx.scatter(level, ModParticles.SMOKE.get(), center, radius, 2, 0x7AB040, 0.7F, 36, 0.6, new Vec3(0.0, 0.01, 0.0));
            if (level.random.nextInt(3) == 0) {
                Fx.scatter(level, ModParticles.BUBBLE.get(), center, radius, 1, VENOM, 0.14F, 24, 0.3, new Vec3(0.0, 0.03, 0.0));
            }
        }));
    }

    /** 拳・掌の当たり：星の光・小さな波・火花。 */
    private static void strike(ClientLevel level, int targetId, int color, float size, boolean shake) {
        Entity entity = level.getEntity(targetId);
        if (entity == null) {
            return;
        }
        Vec3 c = entity.position().add(0.0, entity.getBbHeight() * 0.55, 0.0);
        FxWorld.add(new FxWorld.Flare(Anchor.at(c), 1.6F * size, color, 6, FxTextures.STAR, 0.5F, 1));
        FxWorld.add(new FxWorld.Flare(Anchor.at(c), 0.9F * size, WHITE, 4, FxTextures.GLOW, 0.0F, 1));
        FxWorld.add(new FxWorld.Shockwave(entity.position().add(0.0, entity.getBbHeight() * 0.45, 0.0), 0.2F, 1.1F * size, 7, color, 0.25F,
                0.6F));
        Fx.burst(level, ModParticles.SPARK.get(), c, Math.max(4, (int) (10 * size)), color, 0.09F, 10, 0.5 * size);
        if (shake) {
            FxWorld.shake(c, 3, 0.25F * size);
        }
    }

    private static void strikesOn(ClientLevel level, int[] targets, int color, float size, boolean shake) {
        for (int target : targets) {
            strike(level, target, color, size, shake);
        }
    }

    /** 押し出す波（掌打）：使った人から相手へ、地面を走る輪。 */
    private static void pushWave(ClientLevel level, Vec3 from, int[] targets, int color) {
        for (int target : targets) {
            Entity entity = level.getEntity(target);
            if (entity != null) {
                Vec3 mid = from.lerp(entity.position(), 0.5);
                FxWorld.add(new FxWorld.Shockwave(mid, 0.3F, 1.4F, 8, color, 0.3F, 0.6F));
                Fx.line(level, ModParticles.GLOW.get(), from.add(0.0, 1.0, 0.0), entity.position().add(0.0, 1.0, 0.0), 6, color, 0.25F, 8, 0.02);
            }
        }
    }
}
