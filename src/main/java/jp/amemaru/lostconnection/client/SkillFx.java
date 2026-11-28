package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.fx.Fx;
import jp.amemaru.lostconnection.client.fx.FxDraw;
import jp.amemaru.lostconnection.client.fx.FxTextures;
import jp.amemaru.lostconnection.client.fx.FxWorld;
import jp.amemaru.lostconnection.client.fx.FxWorld.Anchor;
import jp.amemaru.lostconnection.client.fx.StatusVisuals;
import jp.amemaru.lostconnection.core.registry.ModParticles;
import jp.amemaru.lostconnection.core.registry.ModSounds;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * スキルの演出（クライアント専用）：魔法陣・波紋・斬撃の弧・稲妻・流れ星・氷の棘・光の柱・障壁・パーティクル・画面の揺れ・モーション。
 *
 * <p>サーバーからは「誰が・どのスキルを・どこで・どれくらい強く」（{@link SkillFxS2C}）だけが届き、ここで形を作る。
 * 形は {@link FxWorld}、敵や味方にかかった状態の見た目は {@link StatusVisuals} が描く。演出の名前はスキルの「fx」
 * （初期値は {@code <職業>_<スキル>}）。仮素材のテクスチャは {@code textures/fx/}・{@code textures/particle/}。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class SkillFx {
    /** 自分が押した瞬間に始めたモーション（スキルの番号 → 時刻）。サーバーからの演出で二重に始めないため。 */
    private static final Map<Integer, Long> PREDICTED = new HashMap<>();

    /** 形の印：詠唱・溜めの間だけのもの／着弾の目印／跳躍の軌跡。 */
    static final int TAG_CAST = 1;
    static final int TAG_MARKER = 2;
    static final int TAG_LEAP = 3;

    // 色
    private static final int WHITE = 0xFFFFFF;
    private static final int GOLD = 0xFFD27A;
    private static final int GOLD_LIGHT = 0xFFF2A8;
    private static final int KNIGHT = 0x8FD3FF;
    private static final int STEEL = 0xDDE6F5;
    private static final int EMBER = 0xFF8C40;
    private static final int FIRE = 0xFFB050;
    private static final int ICE = 0xBFF2FF;
    private static final int FROST = 0x7FD8FF;
    private static final int VIOLET = 0xB57CFF;
    private static final int MANA = 0x9FD0FF;
    private static final int THUNDER = 0xFFF2A0;
    private static final int TAUNT = 0xFF5A40;
    private static final int LIFE = 0xB8FF9C;

    private SkillFx() {
    }

    /** 自分がキーを押した（離した）瞬間に、モーションを先に始めたことを覚える。 */
    public static void markPredicted(int skillIndex) {
        PREDICTED.put(skillIndex, System.currentTimeMillis());
    }

    private static boolean wasPredicted(int skillIndex) {
        Long at = PREDICTED.get(skillIndex);
        return at != null && System.currentTimeMillis() - at < 1500;
    }

    /** 詠唱のあるスキルか（1段目で見る）。 */
    static boolean hasCast(SyncDefinitionsS2C.SkillInfo skill) {
        return skill.cast().length > 0 && skill.cast()[0] > 0;
    }

    /** ログアウトしたときに消す。 */
    public static void reset() {
        PREDICTED.clear();
        FxWorld.reset();
        StatusVisuals.reset();
    }

    // ---------------------------------------------------------------- 受け取り

    public static void handle(SkillFxS2C message) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        SyncDefinitionsS2C.SkillInfo skill = ClientDefinitions.get().skill(message.skillIndex());
        Entity caster = level.getEntity(message.casterId());
        boolean self = caster != null && caster == minecraft.player;
        String fx = skill == null ? "" : skill.fx();
        int id = message.casterId();
        switch (message.phase()) {
            case CAST_START -> {
                if (caster instanceof AbstractClientPlayer player && skill != null && !(self && wasPredicted(skill.index()))) {
                    PlayerAnimations.play(player, skill.castAnimation());
                }
                castStart(level, fx, message, caster);
            }
            case CHARGE_START -> {
                if (caster instanceof AbstractClientPlayer player && skill != null && !(self && wasPredicted(skill.index()))) {
                    PlayerAnimations.play(player, skill.chargeAnimation());
                }
                chargeStart(level, fx, message, skill);
            }
            case CAST_CANCEL -> {
                if (caster instanceof AbstractClientPlayer player) {
                    PlayerAnimations.stop(player);
                }
                FxWorld.removeIf(v -> v.owner == id && (v.tag == TAG_CAST || v.tag == TAG_MARKER));
            }
            case RELEASE -> {
                FxWorld.removeIf(v -> v.owner == id && v.tag == TAG_CAST);
                boolean predictedRelease = self && skill != null && wasPredicted(skill.index()) && !hasCast(skill);
                if (caster instanceof AbstractClientPlayer player && skill != null && !predictedRelease) {
                    PlayerAnimations.play(player, skill.animation());
                }
                release(level, fx, message, caster, skill);
            }
            case IMPACT -> impact(level, fx, message, caster);
            case TRIGGER -> trigger(level, fx, message, caster);
        }
    }

    /** ダメージの数字が届いたとき：当たったところに火花を散らす。 */
    public static void onCombatText(CombatTextS2C message) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(message.entityId());
        if (entity == null) {
            return;
        }
        Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.6, 0.0);
        switch (message.kind()) {
            case DAMAGE -> {
                Fx.burst(level, ModParticles.SPARK.get(), center, 6, 0xFFF0C8, 0.07F, 8, 0.4);
                Fx.add(level, Fx.glow(0xFFE8B0, 0.6F, 4), center, Vec3.ZERO);
            }
            case CRIT -> {
                Fx.burst(level, ModParticles.SPARK.get(), center, 16, 0xFFD84A, 0.1F, 10, 0.6);
                FxWorld.add(new FxWorld.Flare(Anchor.at(center), 1.6F, 0xFFE27A, 6, FxTextures.STAR, 0.5F, 1));
            }
            case ABSORBED -> Fx.burst(level, ModParticles.STAR.get(), center, 4, GOLD_LIGHT, 0.25F, 10, 0.2);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- 詠唱・溜め

    private static void castStart(ClientLevel level, String fx, SkillFxS2C message, Entity caster) {
        int id = message.casterId();
        int ticks = Math.max(1, message.ticks());
        boolean wizard = fx.startsWith("wizard");
        int color = wizard ? VIOLET : GOLD;
        FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), wizard ? 1.6F : 1.3F, FxTextures.SIGIL_ARCANE, color, ticks + 6,
                0.05F, 6, 0.9F)).tag(id, TAG_CAST);
        FxWorld.add(new FxWorld.Emitter(Anchor.entity(id, Vec3.ZERO), ticks + 4, at -> {
            if (level.random.nextInt(2) == 0) {
                Fx.ring(level, ModParticles.RUNE.get(), at.add(0.0, 0.1, 0.0), 1.2, 1, color, 0.3F, 26, 0.0, 0.035);
            }
            Fx.converge(level, ModParticles.GLOW.get(), at.add(0.0, 1.9, 0.0), 1.4, 1, wizard ? MANA : GOLD_LIGHT, 0.18F, 12);
        })).tag(id, TAG_CAST);
        switch (fx) {
            case "wizard_meteor_shower" -> {
                FxWorld.add(new FxWorld.Sigil(Anchor.at(message.to()), Math.max(1.0F, message.radius()), FxTextures.SIGIL_STAR, EMBER,
                        ticks + 40, 0.02F, 12, 0.8F)).tag(id, TAG_MARKER);
            }
            case "wizard_starfall" -> {
                float radius = Math.max(1.0F, message.radius());
                FxWorld.add(new FxWorld.Sigil(Anchor.at(message.to()), radius, FxTextures.SIGIL_STAR, GOLD, ticks + 60, 0.012F, 20, 0.9F))
                        .tag(id, TAG_MARKER);
                FxWorld.add(new FxWorld.Rim(message.to(), radius, 1.8F, GOLD, ticks + 40, 0.5F)).tag(id, TAG_MARKER);
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 2.4F, FxTextures.SIGIL_STAR, GOLD, ticks + 6, -0.03F, 10, 0.8F))
                        .tag(id, TAG_CAST);
                FxWorld.add(new FxWorld.Emitter(Anchor.at(message.to()), ticks + 20, at -> {
                    Fx.scatter(level, ModParticles.STAR.get(), at, radius, 1, GOLD_LIGHT, 0.3F, 30, 0.2, new Vec3(0.0, 0.08, 0.0));
                })).tag(id, TAG_MARKER);
            }
            case "knight_heaven_cleave" -> {
                // 頭上に、光の大剣が上から形づくられていく。足もとから誓いの糸（光）が昇り、地面には断つ向きの印
                float yaw = caster != null ? caster.getYRot() : 0.0F;
                float length = Math.max(4.0F, message.radius());
                FxWorld.add(new FxWorld.GiantSword(Anchor.entity(id, new Vec3(0.0, 2.7, 0.0)), 6.5F, 2.4F, yaw, GOLD, ticks + 4,
                        Math.max(6, ticks - 4))).tag(id, TAG_CAST);
                FxWorld.add(new FxWorld.LinePreview(id, length, 3.0F, EMBER, ticks + 4)).tag(id, TAG_CAST);
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 2.2F, FxTextures.SIGIL_OATH, GOLD, ticks + 6, 0.06F, 8, 0.9F))
                        .tag(id, TAG_CAST);
                FxWorld.add(new FxWorld.Emitter(Anchor.entity(id, Vec3.ZERO), ticks + 2, at -> {
                    double angle = level.random.nextDouble() * Math.PI * 2.0;
                    Vec3 start = at.add(Math.cos(angle) * 2.0, 0.2, Math.sin(angle) * 2.0);
                    Vec3 goal = at.add(0.0, 6.0, 0.0);
                    Fx.add(level, Fx.glow(GOLD, 0.15F, 14), start, goal.subtract(start).scale(1.0 / 14.0));
                    Fx.add(level, Fx.spark(GOLD_LIGHT, 0.06F, 12), start, goal.subtract(start).scale(1.0 / 12.0));
                })).tag(id, TAG_CAST);
            }
            case "wizard_ice_garden" -> {
                // 狙った地点に氷の陣。冷気が渦を巻いて集まり、詠唱の半ばで花のつぼみが顔を出す
                float radius = Math.max(1.0F, message.radius());
                Vec3 at = message.to();
                FxWorld.add(new FxWorld.Sigil(Anchor.at(at), radius, FxTextures.SIGIL_FROST, ICE, ticks + 20, 0.012F, 16, 0.85F))
                        .tag(id, TAG_MARKER);
                FxWorld.add(new FxWorld.Rim(at, radius, 1.0F, FROST, ticks + 16, 0.4F)).tag(id, TAG_MARKER);
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 2.0F, FxTextures.SIGIL_FROST, ICE, ticks + 6, -0.03F, 10, 0.8F))
                        .tag(id, TAG_CAST);
                FxWorld.add(new FxWorld.Emitter(Anchor.at(at), ticks + 10, center -> {
                    for (int i = 0; i < 2; i++) {
                        double angle = level.random.nextDouble() * Math.PI * 2.0;
                        Vec3 edge = center.add(Math.cos(angle) * radius, 0.3 + level.random.nextDouble() * 1.4, Math.sin(angle) * radius);
                        Vec3 swirl = new Vec3(-Math.sin(angle), 0.0, Math.cos(angle)).scale(0.14);
                        Vec3 inward = center.add(0.0, 0.6, 0.0).subtract(edge).scale(1.0 / 28.0);
                        Fx.add(level, Fx.snow(ICE, 0.16F, 28), edge, inward.add(swirl));
                    }
                })).tag(id, TAG_MARKER);
                long seed = level.random.nextLong();
                FxWorld.later(ticks / 2, () -> FxWorld.add(new FxWorld.Spikes(at, radius * 0.5F, 6, 0.55F, ICE, ticks / 2 + 14, seed))
                        .tag(id, TAG_MARKER)).tag(id, TAG_MARKER);
            }
            default -> JobFx.castStart(level, fx, message, caster);
        }
    }

    private static void chargeStart(ClientLevel level, String fx, SkillFxS2C message, SyncDefinitionsS2C.SkillInfo skill) {
        int id = message.casterId();
        int ticks = Math.max(1, message.ticks());
        int hold = ticks + 22;
        switch (fx) {
            case "knight_heavy_slash" -> {
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 1.5F, FxTextures.SIGIL_OATH, EMBER, hold, 0.08F, ticks, 0.8F))
                        .tag(id, TAG_CAST);
                FxWorld.add(new FxWorld.Gather(Anchor.center(id, Vec3.ZERO), 2.4F, FIRE, hold, 14)).tag(id, TAG_CAST);
                FxWorld.add(new FxWorld.Emitter(Anchor.center(id, Vec3.ZERO), hold, at ->
                        Fx.converge(level, ModParticles.GLOW.get(), at, 1.8, 2, EMBER, 0.22F, 10))).tag(id, TAG_CAST);
                fullChargeFlash(level, id, ticks, FIRE);
            }
            case "wizard_lamp_barrage" -> {
                int max = skill != null && skill.countCharged().length > 0 ? Math.max(1, skill.countCharged()[0]) : 5;
                FxWorld.add(new OrbCharge(id, hold, ticks, max)).tag(id, TAG_CAST);
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 1.3F, FxTextures.SIGIL_STAR, FIRE, hold, 0.06F, 6, 0.8F))
                        .tag(id, TAG_CAST);
                fullChargeFlash(level, id, ticks, FIRE);
            }
            default -> {
                if (!JobFx.chargeStart(level, fx, message, hold)) {
                    FxWorld.add(new FxWorld.Gather(Anchor.center(id, Vec3.ZERO), 2.0F, GOLD, hold, 10)).tag(id, TAG_CAST);
                    fullChargeFlash(level, id, ticks, GOLD);
                }
            }
        }
    }

    /** 溜めきった瞬間の光（溜めの途中で離したら、詠唱の印ごと消える）。 */
    static void fullChargeFlash(ClientLevel level, int id, int ticks, int color) {
        FxWorld.later(ticks, () -> {
            Entity entity = level.getEntity(id);
            if (entity == null) {
                return;
            }
            Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.6, 0.0);
            FxWorld.add(new FxWorld.Flare(Anchor.center(id, Vec3.ZERO), 2.4F, WHITE, 7, FxTextures.STAR, 0.4F, 1));
            FxWorld.add(new FxWorld.Shockwave(entity.position(), 0.3F, 1.8F, 8, color, 0.8F, 0.8F));
            Fx.burst(level, ModParticles.SPARK.get(), center, 14, color, 0.09F, 10, 0.45);
            level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), ModSounds.get("skill.charge_full"), SoundSource.PLAYERS, 0.8F,
                    1.0F, false);
        }).tag(id, TAG_CAST);
        // 溜めきった後は、光の輪が脈を打つ
        FxWorld.add(new FxWorld.Visual(ticks + 22) {
            @Override
            protected void onTick(ClientLevel lvl) {
                if (age > ticks && age % 4 == 0) {
                    Entity entity = lvl.getEntity(id);
                    if (entity != null) {
                        Fx.ring(lvl, ModParticles.GLOW.get(), entity.position().add(0.0, entity.getBbHeight() * 0.55, 0.0), 0.9, 6, color,
                                0.25F, 8, 0.05, 0.0);
                    }
                }
            }

            @Override
            public void render(FxDraw draw, ClientLevel lvl) {
            }
        }).tag(id, TAG_CAST);
    }

    // ---------------------------------------------------------------- 放ったとき

    private static void release(ClientLevel level, String fx, SkillFxS2C message, Entity caster, SyncDefinitionsS2C.SkillInfo skill) {
        RandomSource random = level.random;
        int id = message.casterId();
        Vec3 from = message.from();
        Vec3 to = message.to();
        float radius = message.radius();
        float power = message.power();
        int[] targets = message.targets();
        switch (fx) {
            // ------------------------------------------------ ナイト
            case "knight_call" -> {
                FxWorld.add(new FxWorld.Shockwave(from, 0.5F, radius, 14, GOLD, 1.4F, 0.9F));
                FxWorld.add(new FxWorld.Shockwave(from, 0.3F, radius * 0.6F, 10, WHITE, 0.6F, 0.6F));
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), 2.2F, FxTextures.SIGIL_OATH, GOLD, 18, 0.08F, 4, 0.8F));
                Fx.ring(level, ModParticles.GLOW.get(), from.add(0.0, 0.3, 0.0), radius * 0.3, 28, GOLD, 0.35F, 16, 0.3, 0.02);
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Beam(Anchor.center(target, Vec3.ZERO), Anchor.center(id, Vec3.ZERO), 0.1F, FxTextures.BEAM,
                            TAUNT, 10, 1.0F, 0.3F).extend(4));
                    tauntMark(level, target, 18);
                }
                FxWorld.shake(from, 3, 0.25F);
            }
            case "knight_shield_bond" -> {
                int ticks = Math.max(20, message.ticks());
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, Vec3.ZERO), Anchor.center(target, Vec3.ZERO), 0.08F, FxTextures.BEAM,
                            GOLD, ticks, 0.8F, 0.05F).extend(5));
                    FxWorld.add(new FxWorld.Flare(Anchor.center(target, Vec3.ZERO), 1.7F, KNIGHT, 16, FxTextures.SHIELD, 0.0F, 3));
                    Entity ally = level.getEntity(target);
                    if (ally != null) {
                        Fx.burst(level, ModParticles.STAR.get(), ally.position().add(0.0, 1.0, 0.0), 10, GOLD_LIGHT, 0.25F, 16, 0.2);
                    }
                }
                Fx.line(level, ModParticles.STAR.get(), from, to, 12, GOLD_LIGHT, 0.2F, 14, 0.02);
            }
            case "knight_binding_chain" -> {
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, new Vec3(0.0, 0.1, 0.0)), Anchor.center(target, Vec3.ZERO), 0.22F,
                            FxTextures.CHAIN, 0xE0E6F2, 14, 2.0F, 0.0F).extend(3));
                    FxWorld.later(3, () -> {
                        Entity enemy = level.getEntity(target);
                        if (enemy != null) {
                            Vec3 c = enemy.position().add(0.0, enemy.getBbHeight() * 0.5, 0.0);
                            Fx.burst(level, ModParticles.SPARK.get(), c, 12, 0xFFE0A0, 0.09F, 10, 0.5);
                            FxWorld.add(new FxWorld.Flare(Anchor.at(c), 1.2F, WHITE, 5, FxTextures.STAR, 0.3F, 1));
                        }
                    });
                    tauntMark(level, target, 20);
                }
                Fx.burst(level, ModParticles.SPARK.get(), from, 6, STEEL, 0.07F, 8, 0.3);
            }
            case "knight_rampart" -> {
                FxWorld.add(new FxWorld.Shockwave(from, 0.2F, 1.8F, 8, KNIGHT, 1.0F, 0.7F));
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 1.4F, FxTextures.SIGIL_OATH, KNIGHT, 14, 0.04F, 3, 0.7F));
                Fx.burst(level, ModParticles.GLOW.get(), from.add(0.0, 1.0, 0.0), 10, KNIGHT, 0.4F, 12, 0.12);
            }
            case "knight_parry" -> {
                int ticks = Math.max(6, message.ticks());
                FxWorld.add(new FxWorld.FrontWall(id, 1.0F, 1.9F, 80.0F, WHITE, ticks, 0.55F, FxTextures.DOME));
                FxWorld.add(new FxWorld.Flare(Anchor.center(id, lookOffset(caster, 0.8)), 1.2F, KNIGHT, 6, FxTextures.SHIELD, 0.0F, 1));
                Fx.burst(level, ModParticles.GLOW.get(), from.add(0.0, 1.1, 0.0), 6, WHITE, 0.3F, 8, 0.1);
            }
            case "knight_oath_circle" -> {
                int ticks = Math.max(20, message.ticks());
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), radius, FxTextures.SIGIL_OATH, KNIGHT, ticks, 0.006F, 10, 0.9F));
                FxWorld.add(new FxWorld.Dome(Anchor.at(from), radius, KNIGHT, ticks, true, 0.2F, 10));
                FxWorld.add(new FxWorld.Rim(from, radius, 1.3F, KNIGHT, ticks, 0.6F));
                FxWorld.add(new FxWorld.Pillar(Anchor.at(from), 0.5F, 6.0F, WHITE, 18));
                FxWorld.add(new FxWorld.Shockwave(from, 0.5F, radius, 12, KNIGHT, 1.0F, 0.8F));
                FxWorld.add(new FxWorld.Emitter(Anchor.at(from), ticks, at -> {
                    if (level.random.nextInt(3) == 0) {
                        double angle = level.random.nextDouble() * Math.PI * 2.0;
                        Vec3 edge = at.add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
                        Fx.add(level, Fx.rune(KNIGHT, 0.3F, 30), edge, new Vec3(0.0, 0.03, 0.0));
                    }
                }));
                FxWorld.shake(from, 4, 0.35F);
            }
            case "knight_charge_slash" -> {
                Vec3 dir = to.subtract(from);
                float yaw = yawOf(dir);
                FxWorld.add(new FxWorld.Beam(Anchor.at(from.add(0.0, 0.9, 0.0)), Anchor.at(to.add(0.0, 0.9, 0.0)), 1.1F, FxTextures.BEAM,
                        STEEL, 8, 0.5F, 0.0F));
                FxWorld.add(new FxWorld.Slash(Anchor.at(to.add(0.0, 1.0, 0.0)), yaw, -35.0F, 2.0F, 0.9F, -80.0F, 80.0F, 9, 3, STEEL));
                Fx.line(level, ModParticles.GLOW.get(), from.add(0.0, 0.15, 0.0), to.add(0.0, 0.15, 0.0), 12, 0xC8D0DC, 0.45F, 12, 0.04);
                hitSparks(level, targets, STEEL, 12);
                FxWorld.shake(to, 3, 0.3F);
            }
            case "knight_whirlwind" -> {
                boolean empowered = power > 1.01F;
                int color = empowered ? GOLD : STEEL;
                float yaw = caster != null ? caster.getYRot() : 0.0F;
                FxWorld.add(new FxWorld.Slash(Anchor.entity(id, new Vec3(0.0, 0.95, 0.0)), yaw, 0.0F, radius * 0.9F, 1.1F, 0.0F, 360.0F,
                        11, 5, color));
                FxWorld.add(new FxWorld.Slash(Anchor.entity(id, new Vec3(0.0, 0.75, 0.0)), yaw + 30.0F, 8.0F, radius * 0.65F, 0.6F, 0.0F,
                        360.0F, 10, 5, WHITE));
                FxWorld.add(new FxWorld.Shockwave(from, 0.5F, radius, 10, color, 0.6F, 0.7F));
                Fx.ring(level, ModParticles.SPARK.get(), from.add(0.0, 0.9, 0.0), radius * 0.8, 20, color, 0.09F, 10, 0.3, 0.05);
                if (empowered) {
                    FxWorld.add(new FxWorld.Flare(Anchor.center(id, Vec3.ZERO), 3.0F, GOLD, 8, FxTextures.STAR, 0.2F, 2));
                    Fx.ring(level, ModParticles.GLOW.get(), from.add(0.0, 0.2, 0.0), radius * 0.5, 24, GOLD, 0.4F, 14, 0.35, 0.04);
                }
                hitSparks(level, targets, color, 10);
                FxWorld.shake(from, 4, empowered ? 0.6F : 0.35F);
            }
            case "knight_heavy_slash" -> {
                Vec3 dir = to.subtract(from);
                float yaw = yawOf(dir);
                float angle = skill != null && skill.angle().length > 0 && skill.angle()[0] > 0 ? skill.angle()[0] : 60.0F;
                float ratio = Mth.clamp(power, 0.0F, 1.0F);
                int color = Fx.mix(STEEL, EMBER, ratio);
                FxWorld.add(new FxWorld.Slash(Anchor.at(from.add(0.0, 1.0, 0.0)), yaw, 10.0F, radius * 0.95F, 1.0F + 1.4F * ratio, -angle,
                        angle, 13, 4, color));
                FxWorld.add(new FxWorld.Slash(Anchor.at(from.add(0.0, 0.9, 0.0)), yaw, 4.0F, radius * 0.7F, 0.5F, -angle * 0.9F,
                        angle * 0.9F, 11, 4, WHITE));
                for (int i = 0; i < 8 + (int) (12 * ratio); i++) {
                    double w = (yaw - (random.nextFloat() * 2.0F - 1.0F) * angle) * Mth.DEG_TO_RAD;
                    double r = radius * (0.4 + random.nextDouble() * 0.6);
                    Vec3 at = from.add(-Math.sin(w) * r, 0.1, Math.cos(w) * r);
                    Fx.add(level, Fx.spark(color, 0.1F, 12), at, new Vec3(0.0, 0.25 + random.nextDouble() * 0.3, 0.0));
                }
                if (ratio >= 0.999F) {
                    FxWorld.add(new FxWorld.Shockwave(from, 0.5F, radius * 1.15F, 12, EMBER, 1.3F, 0.9F));
                    FxWorld.add(new FxWorld.Flare(Anchor.at(from.add(dir.normalize().scale(1.5)).add(0.0, 1.0, 0.0)), 3.5F, WHITE, 6,
                            FxTextures.STAR, 0.3F, 1));
                    debris(level, from.add(dir.normalize().scale(radius * 0.6)), 24);
                }
                hitSparks(level, targets, color, 10 + (int) (10 * ratio));
                FxWorld.shake(from, 3 + (int) (5 * ratio), 0.3F + 0.7F * ratio);
            }
            case "knight_leap_slash" -> {
                int ticks = Math.max(8, message.ticks());
                FxWorld.add(new FxWorld.Shockwave(from, 0.3F, 2.0F, 8, STEEL, 0.4F, 0.6F));
                debris(level, from, 16);
                FxWorld.add(new FxWorld.Sigil(Anchor.at(to), Math.max(1.0F, radius), FxTextures.SIGIL_OATH, EMBER, ticks + 12, 0.08F, 6,
                        0.75F)).tag(id, TAG_LEAP);
                FxWorld.add(new FxWorld.Emitter(Anchor.center(id, Vec3.ZERO), ticks + 12, at -> {
                    Fx.add(level, Fx.glow(EMBER, 0.5F, 10), at, Vec3.ZERO);
                    Fx.add(level, Fx.spark(FIRE, 0.07F, 8), at, new Vec3((level.random.nextDouble() - 0.5) * 0.2, -0.1,
                            (level.random.nextDouble() - 0.5) * 0.2));
                })).tag(id, TAG_LEAP);
            }
            case "knight_unyielding_vow" -> {
                FxWorld.add(new FxWorld.Pillar(Anchor.entity(id, Vec3.ZERO), 0.9F, 7.0F, GOLD, 22));
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 1.8F, FxTextures.SIGIL_OATH, GOLD, 30, 0.06F, 6, 0.9F));
                Fx.fountain(level, ModParticles.GLOW.get(), from, 20, GOLD, 0.4F, 24, 0.3, 0.08);
                Fx.burst(level, ModParticles.STAR.get(), from.add(0.0, 1.2, 0.0), 14, GOLD_LIGHT, 0.35F, 20, 0.25);
                FxWorld.shake(from, 4, 0.4F);
            }
            case "knight_oath_release" -> {
                float scale = Mth.clamp((power - 1.0F) / 4.0F, 0.0F, 1.0F);
                int color = Fx.mix(GOLD, WHITE, scale * 0.5F);
                for (int target : targets) {
                    Entity enemy = level.getEntity(target);
                    if (enemy == null) {
                        continue;
                    }
                    Vec3 c = enemy.position().add(0.0, enemy.getBbHeight() * 0.55, 0.0);
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, new Vec3(0.0, 0.1, 0.0)), Anchor.center(target, Vec3.ZERO),
                            0.5F + 0.9F * scale, FxTextures.BEAM, color, 8, 0.5F, 0.3F));
                    FxWorld.add(new FxWorld.Flare(Anchor.at(c), 2.0F + 3.5F * scale, color, 10, FxTextures.STAR, 0.25F, 2));
                    FxWorld.add(new FxWorld.Shockwave(enemy.position(), 0.4F, 2.0F + 2.5F * scale, 12, GOLD, 1.0F + scale, 0.9F));
                    Fx.burst(level, ModParticles.SPARK.get(), c, 16 + (int) (30 * scale), GOLD_LIGHT, 0.1F, 12, 0.7);
                    Fx.burst(level, ModParticles.STAR.get(), c, 6 + (int) (10 * scale), GOLD, 0.3F, 18, 0.3);
                    // 吸収：光が使った人へ戻る
                    if (caster != null) {
                        Vec3 back = caster.position().add(0.0, 1.0, 0.0).subtract(c).scale(1.0 / 12.0);
                        for (int i = 0; i < 10; i++) {
                            Vec3 start = c.add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6,
                                    (random.nextDouble() - 0.5) * 0.6);
                            Fx.add(level, Fx.glow(LIFE, 0.3F, 12), start, back);
                        }
                    }
                }
                FxWorld.shake(from, 4 + (int) (6 * scale), 0.4F + 0.8F * scale);
            }
            case "knight_unfallen_castle" -> {
                int ticks = Math.max(40, message.ticks());
                FxWorld.add(new FxWorld.Dome(Anchor.at(from), radius, 0xE8F4FF, ticks, true, 0.28F, 12));
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), radius, FxTextures.SIGIL_OATH, GOLD, ticks, 0.006F, 12, 0.9F));
                FxWorld.add(new FxWorld.Rim(from, radius, 2.6F, WHITE, ticks, 0.55F));
                FxWorld.add(new FxWorld.Pillar(Anchor.at(from), 1.3F, 14.0F, WHITE, 28));
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2.0;
                    FxWorld.add(new FxWorld.Pillar(Anchor.at(from.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius)), 0.5F, 8.0F, GOLD,
                            26));
                }
                FxWorld.add(new FxWorld.Shockwave(from, 1.0F, radius * 1.2F, 16, GOLD, 2.0F, 1.0F));
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Flare(Anchor.center(target, Vec3.ZERO), 1.6F, GOLD_LIGHT, 18, FxTextures.SHIELD, 0.0F, 4));
                }
                FxWorld.shake(from, 10, 1.2F);
            }
            // ------------------------------------------------ ウィザード
            case "wizard_lamp_bolt" -> {
                Vec3 muzzle = from.add(to.subtract(from).normalize().scale(0.7)).add(0.0, -0.15, 0.0);
                FxWorld.add(new FxWorld.Flare(Anchor.at(muzzle), 1.0F, FIRE, 5, FxTextures.GLOW, 0.0F, 1));
                Fx.burst(level, ModParticles.GLOW.get(), muzzle, 6, FIRE, 0.25F, 8, 0.06);
            }
            case "wizard_lamp_barrage" -> {
                int count = Math.max(1, message.ticks());
                Vec3 look = to.subtract(from).normalize();
                for (int i = 0; i < count; i++) {
                    double offset = count == 1 ? 0.0 : -radius / 2.0 + radius * i / (count - 1);
                    Vec3 dir = look.yRot((float) Math.toRadians(-offset));
                    Vec3 muzzle = from.add(dir.scale(0.8)).add(0.0, -0.15, 0.0);
                    FxWorld.add(new FxWorld.Flare(Anchor.at(muzzle), 0.9F, FIRE, 5, FxTextures.STAR, 0.3F, 1));
                    Fx.burst(level, ModParticles.GLOW.get(), muzzle, 3, FIRE, 0.22F, 8, 0.05);
                }
                FxWorld.shake(from, 2, 0.15F + 0.05F * count);
            }
            case "wizard_meteor_shower" -> {
                int delay = Math.max(4, message.ticks());
                for (int i = 0; i < 8; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double r = Math.sqrt(random.nextDouble()) * radius * 0.85;
                    Vec3 land = to.add(Math.cos(angle) * r, 0.2, Math.sin(angle) * r);
                    Vec3 sky = land.add(5.0 + random.nextDouble() * 2.0, 18.0 + random.nextDouble() * 6.0, -2.0 + random.nextDouble() * 4.0);
                    int travel = Math.max(2, delay - (i % 4));
                    FxWorld.add(new FxWorld.Meteor(sky, land, travel, 0.45F + random.nextFloat() * 0.2F, EMBER, () -> {
                        Fx.burst(level, ModParticles.GLOW.get(), land, 4, FIRE, 0.6F, 10, 0.1);
                        Fx.fountain(level, ModParticles.SPARK.get(), land, 6, FIRE, 0.08F, 12, 0.35, 0.2);
                    }));
                }
            }
            case "wizard_starfall" -> {
                int delay = Math.max(6, message.ticks());
                FxWorld.add(new FxWorld.Meteor(to.add(10.0, 36.0, 3.0), to.add(0.0, 0.6, 0.0), delay, 1.8F, GOLD, null));
                for (int i = 0; i < 4; i++) {
                    Vec3 land = to.add((random.nextDouble() - 0.5) * radius, 0.3, (random.nextDouble() - 0.5) * radius);
                    FxWorld.add(new FxWorld.Meteor(land.add(9.0, 30.0 + i * 2.0, 2.0), land, Math.max(2, delay - 2 - i), 0.5F, GOLD_LIGHT,
                            null));
                }
            }
            case "wizard_ice_garden" -> {
                // 杖から狙った地点へ、冷気の帯が伸びる（花が咲くのは着弾のとき）
                int delay = Math.max(4, message.ticks());
                FxWorld.add(new FxWorld.Beam(Anchor.center(id, new Vec3(0.0, 0.3, 0.0)), Anchor.at(to.add(0.0, 0.4, 0.0)), 0.35F, FxTextures.BEAM,
                        ICE, delay + 4, 1.0F, 0.4F).extend(Math.max(2, delay - 2)));
                Fx.line(level, ModParticles.SNOW.get(), from.add(0.0, 1.2, 0.0), to.add(0.0, 0.5, 0.0), 24, ICE, 0.14F, 20, 0.05);
                FxWorld.add(new FxWorld.Flare(Anchor.at(to.add(0.0, 0.6, 0.0)), 2.4F, ICE, delay + 2, FxTextures.STAR, 0.2F, delay));
            }
            case "knight_heaven_cleave" -> {
                // 頭上の大剣が、前の地面へ突き立つ。切り口が線の先まで走り、光の柱が順に噴き上がる
                Vec3 dir = new Vec3(to.x - from.x, 0.0, to.z - from.z);
                double length = Math.max(1.0, dir.length());
                Vec3 flat = dir.scale(1.0 / length);
                float bladeYaw = (float) Math.toDegrees(Math.atan2(flat.z, flat.x));
                float width = radius > 0.0F ? radius : 3.0F;
                Vec3 impact = from.add(flat.scale(Math.min(2.5, length)));
                FxWorld.add(new FxWorld.GiantSword(Anchor.at(impact), 6.5F, 2.4F, bladeYaw, GOLD, 30, 0).drop(8.0F, 3, 1.6F));
                FxWorld.add(new FxWorld.GroundCut(from.add(flat.scale(0.8)), to, width, EMBER, 34, 7));
                FxWorld.add(new FxWorld.GroundCut(from.add(flat.scale(0.8)), to, width * 0.4F, GOLD_LIGHT, 26, 6));
                FxWorld.later(3, () -> {
                    FxWorld.add(new FxWorld.Flare(Anchor.at(impact.add(0.0, 1.2, 0.0)), 6.5F, WHITE, 8, FxTextures.STAR, 0.2F, 1));
                    FxWorld.add(new FxWorld.Shockwave(impact, 0.5F, 4.5F, 14, GOLD, 1.8F, 1.0F));
                    FxWorld.add(new FxWorld.Shockwave(impact, 0.3F, 2.6F, 10, WHITE, 0.8F, 0.8F));
                    Fx.fountain(level, ModParticles.SPARK.get(), impact, 40, FIRE, 0.12F, 18, 0.7, 0.35);
                    Fx.burst(level, ModParticles.GLOW.get(), impact.add(0.0, 0.6, 0.0), 14, GOLD, 0.7F, 14, 0.2);
                    debris(level, impact, 24);
                    FxWorld.shake(impact, 10, 1.4F);
                });
                int steps = Math.max(2, (int) (length / 2.0));
                for (int i = 1; i <= steps; i++) {
                    Vec3 point = from.add(flat.scale(Math.min(length, 3.0 + (length - 3.0) * i / steps)));
                    FxWorld.later(3 + i, () -> {
                        FxWorld.add(new FxWorld.Pillar(Anchor.at(point), 0.45F, 3.4F, GOLD_LIGHT, 10));
                        FxWorld.add(new FxWorld.Shockwave(point, 0.2F, 1.7F, 8, EMBER, 0.6F, 0.8F));
                        Fx.burst(level, ModParticles.SPARK.get(), point.add(0.0, 0.3, 0.0), 6, FIRE, 0.08F, 10, 0.4);
                        debris(level, point, 6);
                    });
                }
                FxWorld.later(4 + steps, () -> FxWorld.add(new FxWorld.Flare(Anchor.at(to.add(0.0, 1.0, 0.0)), 3.2F, GOLD, 8, FxTextures.STAR,
                        0.3F, 1)));
                FxWorld.later(4, () -> hitSparks(level, targets, GOLD, 18));
            }
            case "wizard_mana_thread" -> {
                for (int target : targets) {
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, Vec3.ZERO), Anchor.center(target, Vec3.ZERO), 0.12F, FxTextures.BEAM,
                            VIOLET, 18, 1.0F, 0.3F).extend(4));
                    FxWorld.add(new FxWorld.Emitter(Anchor.center(id, Vec3.ZERO), 12, at -> {
                        Entity ally = level.getEntity(target);
                        if (ally != null) {
                            Vec3 v = ally.position().add(0.0, ally.getBbHeight() * 0.55, 0.0).subtract(at).scale(1.0 / 10.0);
                            Fx.add(level, Fx.glow(MANA, 0.3F, 10), at, v);
                        }
                    }));
                    FxWorld.add(new FxWorld.Flare(Anchor.center(target, Vec3.ZERO), 1.5F, MANA, 14, FxTextures.STAR, 0.2F, 4));
                }
            }
            case "wizard_light_veil" -> {
                for (int target : targets) {
                    if (target != id) {
                        FxWorld.add(new FxWorld.Beam(Anchor.center(id, Vec3.ZERO), Anchor.center(target, Vec3.ZERO), 0.18F, FxTextures.BEAM,
                                GOLD_LIGHT, 10, 1.0F, 0.4F).extend(3));
                    }
                    Entity ally = level.getEntity(target);
                    if (ally != null) {
                        FxWorld.add(new FxWorld.Dome(Anchor.center(target, Vec3.ZERO), 1.2F, GOLD, 12, false, 0.7F, 6));
                        FxWorld.add(new FxWorld.Shockwave(ally.position(), 0.3F, 2.0F, 10, GOLD, 1.0F, 0.8F));
                        Fx.burst(level, ModParticles.STAR.get(), ally.position().add(0.0, 1.0, 0.0), 14, GOLD_LIGHT, 0.3F, 18, 0.25);
                    }
                }
            }
            case "wizard_star_burst" -> {
                FxWorld.add(new FxWorld.Flare(Anchor.center(id, new Vec3(0.0, 1.2, 0.0)), 1.8F, GOLD, 10, FxTextures.STAR, 0.3F, 2));
                FxWorld.add(new FxWorld.Sigil(Anchor.entity(id, Vec3.ZERO), 1.6F, FxTextures.SIGIL_STAR, GOLD, 16, 0.08F, 4, 0.8F));
                for (int target : targets) {
                    int stacks = Math.max(1, StatusVisuals.level(target, StatusFxS2C.Kind.BRAND));
                    float size = 1.0F + 0.55F * stacks;
                    FxWorld.add(new FxWorld.Beam(Anchor.center(id, new Vec3(0.0, 0.9, 0.0)), Anchor.center(target, Vec3.ZERO), 0.06F,
                            FxTextures.BEAM, GOLD_LIGHT, 7, 1.0F, 0.5F).extend(2));
                    FxWorld.add(new FxWorld.Flare(Anchor.center(target, Vec3.ZERO), size * 0.8F, WHITE, 3, FxTextures.GLOW, 0.0F, 3));
                    FxWorld.later(3, () -> {
                        Entity enemy = level.getEntity(target);
                        Vec3 c = enemy == null ? null : enemy.position().add(0.0, enemy.getBbHeight() * 0.55, 0.0);
                        if (c == null) {
                            return;
                        }
                        FxWorld.add(new FxWorld.Flare(Anchor.at(c), size * 2.2F, GOLD, 12, FxTextures.STAR, 0.25F, 2));
                        FxWorld.add(new FxWorld.Shockwave(enemy.position(), 0.3F, size + 0.5F, 10, GOLD, 0.8F, 0.9F));
                        Fx.burst(level, ModParticles.STAR.get(), c, 6 + stacks * 4, GOLD_LIGHT, 0.3F, 18, 0.25 + stacks * 0.05);
                    });
                }
                FxWorld.shake(from, 3, 0.4F);
            }
            case "wizard_frost_circle" -> {
                int ticks = Math.max(20, message.ticks());
                FxWorld.add(new FxWorld.Sigil(Anchor.at(to), radius, FxTextures.SIGIL_FROST, ICE, ticks, 0.012F, 8, 0.85F));
                FxWorld.add(new FxWorld.Shockwave(to, 0.3F, radius, 12, ICE, 1.0F, 0.9F));
                FxWorld.add(new FxWorld.Spikes(to, radius * 0.92F, 9, 0.9F, ICE, 16, random.nextLong()));
                Fx.burst(level, ModParticles.SNOW.get(), to.add(0.0, 0.5, 0.0), 26, ICE, 0.16F, 30, 0.25);
                FxWorld.add(new FxWorld.Emitter(Anchor.at(to), ticks, at -> {
                    Fx.scatter(level, ModParticles.SNOW.get(), at.add(0.0, 0.2, 0.0), radius, 2, ICE, 0.12F, 30, 1.4,
                            new Vec3(0.0, -0.02, 0.0));
                    if (level.random.nextInt(3) == 0) {
                        Fx.scatter(level, ModParticles.GLOW.get(), at, radius, 1, FROST, 0.6F, 24, 0.2, new Vec3(0.0, 0.01, 0.0));
                    }
                }));
            }
            case "wizard_chain_lightning" -> {
                boolean empowered = power > 0.5F;
                int color = empowered ? 0xD8B0FF : 0xA8E6FF;
                float width = empowered ? 0.2F : 0.12F;
                Anchor previous = Anchor.center(id, new Vec3(0.0, 0.2, 0.0));
                for (int i = 0; i < targets.length; i++) {
                    int target = targets[i];
                    Anchor next = Anchor.center(target, Vec3.ZERO);
                    Anchor start = previous;
                    FxWorld.later(Math.max(1, i), () -> {
                        FxWorld.add(new FxWorld.Lightning(start, next, width, color, 9, 0.7F));
                        Entity enemy = level.getEntity(target);
                        if (enemy != null) {
                            Vec3 c = enemy.position().add(0.0, enemy.getBbHeight() * 0.55, 0.0);
                            Fx.burst(level, ModParticles.SPARK.get(), c, 10, THUNDER, 0.08F, 10, 0.5);
                            FxWorld.add(new FxWorld.Flare(Anchor.at(c), 1.3F, color, 5, FxTextures.STAR, 0.4F, 1));
                        }
                    });
                    previous = next;
                }
                if (empowered && targets.length > 0) {
                    FxWorld.add(new FxWorld.Flare(Anchor.center(targets[0], Vec3.ZERO), 3.0F, 0xE8D8FF, 8, FxTextures.STAR, 0.3F, 1));
                    FxWorld.shake(to, 3, 0.4F);
                }
            }
            case "wizard_blink" -> {
                Vec3 a = from.add(0.0, 1.0, 0.0);
                Vec3 b = to.add(0.0, 1.0, 0.0);
                FxWorld.add(new FxWorld.Flare(Anchor.at(a), 2.2F, VIOLET, 7, FxTextures.STAR, 0.4F, 1));
                FxWorld.add(new FxWorld.Flare(Anchor.at(b), 1.8F, MANA, 7, FxTextures.GLOW, 0.0F, 2));
                FxWorld.add(new FxWorld.Beam(Anchor.at(a), Anchor.at(b), 0.7F, FxTextures.BEAM, VIOLET, 7, 0.5F, 0.0F));
                Fx.burst(level, ModParticles.RUNE.get(), a, 8, VIOLET, 0.3F, 20, 0.12);
                Fx.burst(level, ModParticles.GLOW.get(), b, 10, MANA, 0.35F, 10, 0.12);
            }
            case "wizard_icicle" -> {
                double distance = from.distanceTo(to);
                int travel = Math.max(2, (int) Math.ceil(distance / 3.5));
                FxWorld.add(new FxWorld.Spear(from, to, travel, ICE));
                Fx.line(level, ModParticles.SNOW.get(), from, to, (int) (distance * 1.5), ICE, 0.12F, 20, 0.04);
                FxWorld.later(travel, () -> Fx.burst(level, ModParticles.SHARD.get(), to, 10, ICE, 0.22F, 20, 0.3));
                for (int target : targets) {
                    Entity enemy = level.getEntity(target);
                    if (enemy == null) {
                        continue;
                    }
                    int at = Math.max(1, (int) (travel * Math.min(1.0, enemy.position().distanceTo(from) / Math.max(0.1, distance))));
                    FxWorld.later(at, () -> {
                        Vec3 c = enemy.position().add(0.0, enemy.getBbHeight() * 0.55, 0.0);
                        Fx.burst(level, ModParticles.SHARD.get(), c, 12, ICE, 0.24F, 22, 0.4);
                        FxWorld.add(new FxWorld.Flare(Anchor.at(c), 1.4F, ICE, 5, FxTextures.STAR, 0.3F, 1));
                    });
                }
            }
            case "wizard_frost_wave" -> {
                FxWorld.add(new FxWorld.Shockwave(from, 0.5F, radius, 12, ICE, 1.4F, 1.0F));
                FxWorld.add(new FxWorld.Shockwave(from, 0.3F, radius * 0.7F, 10, WHITE, 0.6F, 0.8F));
                FxWorld.add(new FxWorld.Spikes(from, radius * 0.78F, 14, 1.6F, ICE, 18, random.nextLong()));
                FxWorld.add(new FxWorld.Spikes(from, radius * 0.45F, 8, 1.0F, ICE, 16, random.nextLong()));
                FxWorld.add(new FxWorld.Sigil(Anchor.at(from), radius, FxTextures.SIGIL_FROST, ICE, 16, 0.05F, 4, 0.7F));
                Fx.ring(level, ModParticles.SNOW.get(), from.add(0.0, 0.2, 0.0), 0.5, 40, ICE, 0.16F, 30, 0.45, 0.05);
                Fx.burst(level, ModParticles.GLOW.get(), from.add(0.0, 0.6, 0.0), 12, FROST, 0.6F, 12, 0.2);
                FxWorld.shake(from, 4, 0.5F);
            }
            case "wizard_thunderbolt" -> {
                int ticks = Math.max(4, message.ticks());
                FxWorld.add(new FxWorld.Sigil(Anchor.at(to), Math.max(1.0F, radius), FxTextures.SIGIL_THUNDER, THUNDER, ticks + 10, 0.1F, 4,
                        0.9F)).tag(id, TAG_MARKER);
                FxWorld.add(new FxWorld.Flare(Anchor.at(to.add(0.0, 14.0, 0.0)), 6.0F, 0x8A7CFF, ticks + 4, FxTextures.GLOW, 0.0F, ticks))
                        .tag(id, TAG_MARKER);
                FxWorld.add(new FxWorld.Flare(Anchor.center(id, new Vec3(0.0, 1.0, 0.0)), 1.2F, THUNDER, 6, FxTextures.STAR, 0.5F, 1));
            }
            default -> {
                if (!JobFx.release(level, fx, message, caster, skill) && !UniqueSkillFx.release(level, fx, message, caster)) {
                    Fx.burst(level, ModParticles.GLOW.get(), to, 12, GOLD_LIGHT, 0.3F, 12, 0.15);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 着弾

    private static void impact(ClientLevel level, String fx, SkillFxS2C message, Entity caster) {
        int id = message.casterId();
        Vec3 at = message.to();
        float radius = message.radius();
        RandomSource random = level.random;
        switch (fx) {
            case "wizard_lamp_bolt", "wizard_lamp_barrage" -> {
                FxWorld.add(new FxWorld.Flare(Anchor.at(at), 1.3F, FIRE, 6, FxTextures.STAR, 0.3F, 1));
                Fx.burst(level, ModParticles.SPARK.get(), at, 10, 0xFFD080, 0.08F, 10, 0.4);
                Fx.burst(level, ModParticles.GLOW.get(), at, 5, EMBER, 0.35F, 10, 0.08);
            }
            case "wizard_meteor_shower" -> {
                FxWorld.removeIf(v -> v.owner == id && v.tag == TAG_MARKER);
                FxWorld.add(new FxWorld.Shockwave(at, 0.5F, radius * 1.15F, 16, EMBER, 1.6F, 1.0F));
                FxWorld.add(new FxWorld.Shockwave(at, 0.3F, radius * 0.7F, 12, GOLD_LIGHT, 0.8F, 0.8F));
                for (int i = 0; i < 6; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double r = Math.sqrt(random.nextDouble()) * radius * 0.8;
                    Vec3 spot = at.add(Math.cos(angle) * r, 0.5, Math.sin(angle) * r);
                    FxWorld.add(new FxWorld.Flare(Anchor.at(spot), 2.6F, FIRE, 8, FxTextures.GLOW, 0.0F, 2));
                    if (i % 2 == 0 && farFromCamera(spot, 4.0)) {
                        level.addParticle(ParticleTypes.EXPLOSION, spot.x, spot.y, spot.z, 0.0, 0.0, 0.0);
                    }
                }
                Fx.fountain(level, ModParticles.SPARK.get(), at, 40, FIRE, 0.1F, 18, 0.6, 0.35);
                Fx.burst(level, ModParticles.GLOW.get(), at.add(0.0, 0.5, 0.0), 20, EMBER, 0.6F, 16, 0.25);
                debris(level, at, 20);
                FxWorld.shake(at, 8, 0.9F);
            }
            case "wizard_starfall" -> {
                FxWorld.removeIf(v -> v.owner == id && v.tag == TAG_MARKER);
                FxWorld.add(new FxWorld.Flare(Anchor.at(at.add(0.0, 1.0, 0.0)), 9.0F, WHITE, 12, FxTextures.STAR, 0.1F, 2));
                FxWorld.add(new FxWorld.Pillar(Anchor.at(at), 2.5F, 22.0F, GOLD, 30));
                FxWorld.add(new FxWorld.Shockwave(at, 1.0F, radius * 1.3F, 22, GOLD, 3.0F, 1.0F));
                FxWorld.add(new FxWorld.Shockwave(at, 0.5F, radius * 0.8F, 16, WHITE, 1.5F, 0.9F));
                FxWorld.add(new FxWorld.Sigil(Anchor.at(at), radius, FxTextures.SIGIL_STAR, GOLD, 24, 0.04F, 2, 0.8F).noInner());
                Fx.burst(level, ModParticles.STAR.get(), at.add(0.0, 1.0, 0.0), 60, GOLD_LIGHT, 0.45F, 30, 0.6);
                Fx.fountain(level, ModParticles.SPARK.get(), at, 60, GOLD, 0.12F, 20, 0.9, 0.5);
                if (farFromCamera(at, 7.0)) {
                    level.addParticle(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 0.0, 0.0, 0.0);
                } else {
                    Fx.burst(level, ModParticles.GLOW.get(), at.add(0.0, 0.8, 0.0), 24, GOLD, 0.8F, 18, 0.35);
                }
                debris(level, at, 30);
                FxWorld.shake(at, 16, 1.6F);
            }
            case "wizard_ice_garden" -> {
                // 氷の花が一斉に咲く：真ん中に大輪、まわりに花びらの輪、ふちに檻の氷柱。花は凍えの続く間（8秒）残る
                FxWorld.removeIf(v -> v.owner == id && v.tag == TAG_MARKER);
                int life = 160;
                FxWorld.add(new FxWorld.Flare(Anchor.at(at.add(0.0, 1.0, 0.0)), 7.0F, WHITE, 8, FxTextures.STAR, 0.15F, 1));
                FxWorld.add(new FxWorld.Shockwave(at, 0.5F, radius * 1.25F, 18, ICE, 1.8F, 1.0F));
                FxWorld.add(new FxWorld.Shockwave(at, 0.3F, radius * 0.7F, 12, WHITE, 0.8F, 0.8F));
                FxWorld.add(new FxWorld.Sigil(Anchor.at(at), radius, FxTextures.SIGIL_FROST, ICE, life, 0.006F, 4, 0.75F));
                FxWorld.add(new FxWorld.Rim(at, radius, 1.6F, FROST, life, 0.35F));
                FxWorld.add(new FxWorld.Spikes(at, 0.45F, 7, 2.4F, ICE, life, random.nextLong()));
                for (int i = 0; i < 6; i++) {
                    double angle = i * Math.PI / 3.0;
                    Vec3 petal = at.add(Math.cos(angle) * radius * 0.5, 0.0, Math.sin(angle) * radius * 0.5);
                    FxWorld.add(new FxWorld.Spikes(petal, 0.35F, 5, 1.5F, ICE, life - i, random.nextLong()));
                }
                for (int i = 0; i < 10; i++) {
                    double angle = (i + 0.5) * Math.PI / 5.0;
                    Vec3 bud = at.add(Math.cos(angle) * radius * 0.82, 0.0, Math.sin(angle) * radius * 0.82);
                    FxWorld.add(new FxWorld.Spikes(bud, 0.28F, 4, 0.9F, FROST, life - 10 - i, random.nextLong()));
                }
                FxWorld.add(new FxWorld.Spikes(at, radius * 1.02F, 30, 3.0F, ICE, life, random.nextLong()));
                FxWorld.add(new FxWorld.Emitter(Anchor.at(at), life, center ->
                        Fx.scatter(level, ModParticles.SNOW.get(), center.add(0.0, 2.8, 0.0), radius, 2, ICE, 0.14F, 36, 0.1,
                                new Vec3(0.0, -0.05, 0.0))));
                Fx.burst(level, ModParticles.SNOW.get(), at.add(0.0, 1.0, 0.0), 40, ICE, 0.2F, 30, 0.5);
                Fx.burst(level, ModParticles.SHARD.get(), at.add(0.0, 0.8, 0.0), 24, ICE, 0.26F, 24, 0.55);
                FxWorld.shake(at, 6, 0.7F);
            }
            case "knight_leap_slash" -> {
                FxWorld.removeIf(v -> v.owner == id && v.tag == TAG_LEAP);
                if (caster instanceof AbstractClientPlayer player) {
                    PlayerAnimations.play(player, "knight_leap_land");
                }
                float yaw = caster != null ? caster.getYRot() : 0.0F;
                FxWorld.add(new FxWorld.Shockwave(at, 0.5F, radius * 1.3F, 14, EMBER, 1.4F, 1.0F));
                FxWorld.add(new FxWorld.Shockwave(at, 0.3F, radius * 0.8F, 10, WHITE, 0.6F, 0.8F));
                FxWorld.add(new FxWorld.Slash(Anchor.at(at.add(0.0, 0.3, 0.0)), yaw, 90.0F, 2.2F, 1.0F, -60.0F, 60.0F, 8, 2, EMBER));
                FxWorld.add(new FxWorld.Sigil(Anchor.at(at), radius, FxTextures.SIGIL_OATH, EMBER, 14, 0.1F, 2, 0.8F).noInner());
                Fx.fountain(level, ModParticles.SPARK.get(), at, 30, 0xFFC080, 0.1F, 14, 0.6, 0.5);
                debris(level, at, 36);
                FxWorld.shake(at, 8, 1.0F);
            }
            case "wizard_thunderbolt" -> {
                FxWorld.removeIf(v -> v.owner == id && v.tag == TAG_MARKER);
                Vec3 sky = at.add((random.nextDouble() - 0.5) * 3.0, 20.0, (random.nextDouble() - 0.5) * 3.0);
                FxWorld.add(new FxWorld.Lightning(Anchor.at(sky), Anchor.at(at), 0.35F, 0xD8E8FF, 9, 2.2F));
                FxWorld.add(new FxWorld.Lightning(Anchor.at(sky.add(1.0, 0.0, -0.5)), Anchor.at(at), 0.16F, 0xB8C8FF, 7, 2.8F));
                FxWorld.add(new FxWorld.Flare(Anchor.at(at.add(0.0, 0.6, 0.0)), 5.5F, WHITE, 6, FxTextures.GLOW, 0.0F, 1));
                FxWorld.add(new FxWorld.Shockwave(at, 0.3F, radius * 1.4F, 12, THUNDER, 1.0F, 1.0F));
                FxWorld.add(new FxWorld.Sigil(Anchor.at(at), radius, FxTextures.SIGIL_THUNDER, THUNDER, 12, 0.2F, 1, 0.9F).noInner());
                Fx.burst(level, ModParticles.SPARK.get(), at.add(0.0, 0.5, 0.0), 30, THUNDER, 0.1F, 12, 0.8);
                Fx.fountain(level, ModParticles.SPARK.get(), at, 16, 0xFFFFFF, 0.08F, 10, 0.5, 0.4);
                FxWorld.shake(at, 7, 1.0F);
            }
            default -> {
                if (!JobFx.impact(level, fx, message, caster) && !UniqueSkillFx.impact(level, fx, message)) {
                    Fx.burst(level, ModParticles.GLOW.get(), at, 16, GOLD_LIGHT, 0.35F, 12, 0.2);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 構えていたスキルの発動

    private static void trigger(ClientLevel level, String fx, SkillFxS2C message, Entity caster) {
        Vec3 at = message.to();
        if (fx.equals("knight_parry") && caster != null) {
            float yaw = caster.getYRot();
            FxWorld.add(new FxWorld.Slash(Anchor.entity(caster.getId(), new Vec3(0.0, 1.0, 0.0)), yaw, 20.0F, 2.0F, 0.9F, -75.0F, 75.0F,
                    9, 3, KNIGHT));
            FxWorld.add(new FxWorld.Flare(Anchor.at(at), 2.2F, WHITE, 6, FxTextures.STAR, 0.5F, 1));
            Fx.burst(level, ModParticles.SPARK.get(), at, 16, 0xFFF6D0, 0.1F, 12, 0.6);
            return;
        }
        if (JobFx.trigger(level, fx, message, caster) || UniqueSkillFx.trigger(level, fx, message)) {
            return;
        }
        Fx.burst(level, ModParticles.GLOW.get(), at, 10, GOLD_LIGHT, 0.3F, 10, 0.2);
    }

    // ---------------------------------------------------------------- 道具

    /** 挑発の印（頭の上の赤い光）。 */
    static void tauntMark(ClientLevel level, int target, int ticks) {
        Entity enemy = level.getEntity(target);
        float height = enemy == null ? 1.8F : enemy.getBbHeight();
        FxWorld.add(new FxWorld.Flare(Anchor.entity(target, new Vec3(0.0, height + 0.7, 0.0)), 0.8F, TAUNT, ticks, FxTextures.STAR, 0.2F,
                3));
    }

    /** 当たった敵に火花。 */
    static void hitSparks(ClientLevel level, int[] targets, int color, int count) {
        for (int target : targets) {
            Entity enemy = level.getEntity(target);
            if (enemy != null) {
                Vec3 c = enemy.position().add(0.0, enemy.getBbHeight() * 0.55, 0.0);
                Fx.burst(level, ModParticles.SPARK.get(), c, count, color, 0.09F, 10, 0.55);
                FxWorld.add(new FxWorld.Flare(Anchor.at(c), 1.2F, WHITE, 4, FxTextures.STAR, 0.4F, 1));
            }
        }
    }

    /** 地面のかけら（足もとのブロックの破片が飛ぶ）。 */
    static void debris(ClientLevel level, Vec3 at, int count) {
        BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
        BlockState state = level.getBlockState(below);
        if (state.isAir()) {
            return;
        }
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.15 + random.nextDouble() * 0.25;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + 0.1, at.z, Math.cos(angle) * speed,
                    0.25 + random.nextDouble() * 0.35, Math.sin(angle) * speed);
        }
    }

    /** 向き（ベクトル）から、プレイヤーと同じ決まりの角度（度。0で南）。 */
    static float yawOf(Vec3 direction) {
        return (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
    }

    /**
     * カメラから distance より遠いか。バニラの爆発の煙は、カメラの近くに出ると画面をふさぐ灰色の大きな塊に見えるため、
     * 近いときは出さない（光の演出だけにする）。
     */
    static boolean farFromCamera(Vec3 at, double distance) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return camera.distanceToSqr(at) > distance * distance;
    }

    /** エンティティの前（見ている向き）へのずれ。 */
    static Vec3 lookOffset(Entity entity, double distance) {
        if (entity == null) {
            return Vec3.ZERO;
        }
        Vec3 look = entity.getLookAngle();
        return new Vec3(look.x, 0.0, look.z).normalize().scale(distance);
    }

    /** 溜めている間、まわりを回る灯火（溜めるほど増える）。 */
    private static final class OrbCharge extends FxWorld.Visual {
        private final int entityId;
        private final int full;
        private final int max;

        OrbCharge(int entityId, int life, int full, int max) {
            super(life);
            this.entityId = entityId;
            this.full = Math.max(1, full);
            this.max = max;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            Entity entity = level.getEntity(entityId);
            if (entity == null) {
                return;
            }
            float partial = draw.partialTick();
            float t = age + partial;
            float ratio = Mth.clamp(t / full, 0.0F, 1.0F);
            int count = 1 + (int) Math.floor((max - 1) * ratio + 1.0E-4);
            Vec3 center = entity.getPosition(partial).add(0.0, entity.getBbHeight() * 0.62, 0.0);
            for (int i = 0; i < count; i++) {
                float angle = t * 0.18F + i * Mth.TWO_PI / count;
                Vec3 at = center.add(Mth.cos(angle) * 0.85, Mth.sin(t * 0.3F + i) * 0.12, Mth.sin(angle) * 0.85);
                draw.billboard(draw.glow(FxTextures.GLOW), at, 0.7F, 0.0F, FIRE, 0.9F);
                draw.billboard(draw.glow(FxTextures.GLOW), at, 0.3F, 0.0F, WHITE, 1.0F);
            }
        }

        @Override
        protected boolean alive(ClientLevel level) {
            return level.getEntity(entityId) != null;
        }
    }
}
