package jp.amemaru.lostconnection.entity;

import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.combat.CombatTargets;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModParticles;
import jp.amemaru.lostconnection.job.SkillContext;
import jp.amemaru.lostconnection.job.SkillExecutor;
import jp.amemaru.lostconnection.particle.FxParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * スキルの弾（ウィザードの「灯火弾」、アーチャーの矢、ローグの針、モンクの気弾、彗星）。まっすぐ飛び、最初に当たった敵にスキルの効果を出す。
 *
 * <p>重さはなく、射程を過ぎるか、壁に当たると消える。ワールドには保存しない。見た目は種類（{@link Kind}）で変わる
 * （灯火：光の玉と光の粒、矢：光る矢の筋、針：細い緑の筋、気弾：青い渦、彗星：長い尾）。弾ける弾（爆ぜ矢・彗星）は、
 * 当たった所（壁でも）のまわりの敵すべてに効く。弓の通常の射撃（Phase 6 その2）もこの弾で、スキルの代わりに攻撃のステータスで当てる。</p>
 */
public class LampBolt extends Projectile {
    /** 弾の見た目の種類（番号を変えないこと）。 */
    public enum Kind {
        LAMP, ARROW, NEEDLE, KI, COMET;

        private static final Kind[] VALUES = values();

        public static Kind byIndex(int index) {
            return index >= 0 && index < VALUES.length ? VALUES[index] : LAMP;
        }

        public static Kind byId(String id) {
            return switch (id) {
                case "arrow" -> ARROW;
                case "needle" -> NEEDLE;
                case "ki" -> KI;
                case "comet" -> COMET;
                default -> LAMP;
            };
        }
    }

    /** 尾の光の粒（使うときに初めて作る：登録が済む前に作らないため）。 */
    private static final class Trail {
        static final FxParticleOptions GLOW = new FxParticleOptions(ModParticles.GLOW.get(), 1.0F, 0.62F, 0.25F, 0.55F, 9);
        static final FxParticleOptions CORE = new FxParticleOptions(ModParticles.GLOW.get(), 1.0F, 0.95F, 0.8F, 0.3F, 4);
        static final FxParticleOptions SPARK = new FxParticleOptions(ModParticles.SPARK.get(), 1.0F, 0.8F, 0.4F, 0.07F, 8);
        static final FxParticleOptions ARROW = new FxParticleOptions(ModParticles.GLOW.get(), 0.75F, 0.9F, 1.0F, 0.16F, 6);
        static final FxParticleOptions ARROW_TIP = new FxParticleOptions(ModParticles.SPARK.get(), 0.9F, 0.97F, 1.0F, 0.06F, 5);
        static final FxParticleOptions FEATHER = new FxParticleOptions(ModParticles.FEATHER.get(), 0.85F, 0.95F, 1.0F, 0.12F, 18);
        static final FxParticleOptions NEEDLE = new FxParticleOptions(ModParticles.GLOW.get(), 0.6F, 1.0F, 0.4F, 0.1F, 5);
        static final FxParticleOptions KI = new FxParticleOptions(ModParticles.GLOW.get(), 0.5F, 0.85F, 1.0F, 0.45F, 8);
        static final FxParticleOptions KI_CORE = new FxParticleOptions(ModParticles.GLOW.get(), 0.9F, 0.98F, 1.0F, 0.28F, 4);
        static final FxParticleOptions COMET = new FxParticleOptions(ModParticles.GLOW.get(), 1.0F, 0.8F, 0.45F, 0.8F, 16);
        static final FxParticleOptions COMET_STAR = new FxParticleOptions(ModParticles.STAR.get(), 1.0F, 0.9F, 0.6F, 0.2F, 18);

        private Trail() {
        }
    }

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(LampBolt.class, EntityDataSerializers.BYTE);
    private static final int MAX_LIFE = 200;

    @Nullable
    private SkillContext context;
    private double maxDistance = 24.0;
    private double hitRadius;
    private double explode;
    /** 弓の通常の射撃のときの、攻撃の係数（スキルのときは0）。 */
    private double basicCoefficient;
    @Nullable
    private Vec3 start;
    private int life;

    public LampBolt(EntityType<? extends LampBolt> type, Level level) {
        super(type, level);
    }

    /** 使った人の目の前から、{@code direction} の方向へ撃つ（灯火の連弾は、扇に広げて何発も撃つ）。 */
    public static void launch(SkillContext context, ServerPlayer caster, Vec3 direction, double speed, double range, double hitRadius) {
        launch(context, caster, direction, speed, range, hitRadius, "lamp", 0.0);
    }

    /** 種類と、弾ける半径を決めて撃つ（Phase 6 その2）。 */
    public static void launch(SkillContext context, ServerPlayer caster, Vec3 direction, double speed, double range, double hitRadius,
                              String kind, double explode) {
        LampBolt bolt = create(caster, direction, speed, range, hitRadius, Kind.byId(kind));
        bolt.context = context;
        bolt.explode = explode;
        caster.level().addFreshEntity(bolt);
    }

    /** 弓の通常の射撃（攻撃 × {@code coefficient} のダメージ）。 */
    public static void launchBasic(ServerPlayer caster, Vec3 direction, double speed, double range, double coefficient) {
        LampBolt bolt = create(caster, direction, speed, range, 0.2, Kind.ARROW);
        bolt.basicCoefficient = coefficient;
        caster.level().addFreshEntity(bolt);
    }

    private static LampBolt create(ServerPlayer caster, Vec3 direction, double speed, double range, double hitRadius, Kind kind) {
        LampBolt bolt = new LampBolt(ModEntities.LAMP_BOLT.get(), caster.level());
        bolt.setOwner(caster);
        bolt.maxDistance = range;
        bolt.hitRadius = hitRadius;
        bolt.entityData.set(KIND, (byte) kind.ordinal());
        Vec3 look = direction.normalize();
        Vec3 eye = caster.getEyePosition();
        bolt.setPos(eye.x + look.x * 0.7, eye.y - 0.15 + look.y * 0.7, eye.z + look.z * 0.7);
        bolt.start = bolt.position();
        bolt.setDeltaMovement(look.scale(speed));
        ProjectileUtil.rotateTowardsMovement(bolt, 1.0F);
        return bolt;
    }

    /** 弾の見た目の種類。 */
    public Kind kind() {
        return Kind.byIndex(entityData.get(KIND));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, (byte) 0);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (!level().isClientSide()) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() == HitResult.Type.MISS && hitRadius > 0) {
                hit = nearbyHit();
            }
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                if (isRemoved()) {
                    return;
                }
            }
            life++;
            boolean alive = context != null || basicCoefficient > 0;
            if (!alive || life > MAX_LIFE || (start != null && position().distanceToSqr(start) > maxDistance * maxDistance)) {
                fizzle();
                return;
            }
        } else {
            trail(motion);
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        ProjectileUtil.rotateTowardsMovement(this, 1.0F);
    }

    /** 尾を引く光（種類ごと）。 */
    private void trail(Vec3 motion) {
        switch (kind()) {
            case ARROW -> {
                for (int i = 0; i < 4; i++) {
                    double t = i / 4.0;
                    level().addParticle(Trail.ARROW, getX() - motion.x * t, getY() - motion.y * t, getZ() - motion.z * t, 0.0, 0.0, 0.0);
                }
                level().addParticle(Trail.ARROW_TIP, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
                if (random.nextInt(6) == 0) {
                    level().addParticle(Trail.FEATHER, getX(), getY(), getZ(), (random.nextDouble() - 0.5) * 0.04, -0.02,
                            (random.nextDouble() - 0.5) * 0.04);
                }
            }
            case NEEDLE -> {
                for (int i = 0; i < 3; i++) {
                    double t = i / 3.0;
                    level().addParticle(Trail.NEEDLE, getX() - motion.x * t, getY() - motion.y * t, getZ() - motion.z * t, 0.0, 0.0, 0.0);
                }
            }
            case KI -> {
                double spin = tickCount * 0.9;
                for (int i = 0; i < 2; i++) {
                    double angle = spin + i * Math.PI;
                    level().addParticle(Trail.KI, getX() + Math.cos(angle) * 0.25, getY() + Math.sin(angle) * 0.25, getZ(), 0.0, 0.0, 0.0);
                }
                level().addParticle(Trail.KI_CORE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
            }
            case COMET -> {
                for (int i = 0; i < 5; i++) {
                    double t = i / 5.0;
                    level().addParticle(Trail.COMET, getX() - motion.x * t * 1.5, getY() - motion.y * t * 1.5, getZ() - motion.z * t * 1.5,
                            0.0, 0.0, 0.0);
                }
                if (random.nextInt(2) == 0) {
                    level().addParticle(Trail.COMET_STAR, getX(), getY(), getZ(), (random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.1,
                            (random.nextDouble() - 0.5) * 0.1);
                }
            }
            default -> {
                // 尾を引く灯火（このModの光の粒。2つの大きさを重ねて、芯を白く見せる）
                for (int i = 0; i < 3; i++) {
                    double t = i / 3.0;
                    level().addParticle(Trail.GLOW, getX() - motion.x * t, getY() - motion.y * t, getZ() - motion.z * t, 0.0, 0.0, 0.0);
                }
                level().addParticle(Trail.CORE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
                if (random.nextInt(3) == 0) {
                    level().addParticle(Trail.SPARK, getX(), getY(), getZ(), (random.nextDouble() - 0.5) * 0.1, -0.05,
                            (random.nextDouble() - 0.5) * 0.1);
                }
            }
        }
    }

    /** 弾の大きさの分、近くを通った敵にも当てる。 */
    private HitResult nearbyHit() {
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        for (Entity entity : level().getEntities(this, getBoundingBox().expandTowards(getDeltaMovement()).inflate(hitRadius), this::canHitEntity)) {
            if (entity.getBoundingBox().inflate(hitRadius).clip(from, to).isPresent()) {
                return new EntityHitResult(entity);
            }
        }
        return BlockHitResult.miss(to, getDirection(), blockPosition());
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && CombatTargets.isEnemy(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (result.getEntity() instanceof LivingEntity living) {
            if (context != null) {
                SkillExecutor.projectileHit(context, living, this, explode);
            } else if (basicCoefficient > 0 && getOwner() instanceof ServerPlayer owner) {
                CombatService.dealBasicShot(owner, living, basicCoefficient, this);
            }
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (context != null && explode > 0) {
            SkillExecutor.projectileBurst(context, result.getLocation(), this, explode);
            discard();
            return;
        }
        fizzle();
    }

    private void fizzle() {
        if (level() instanceof ServerLevel serverLevel) {
            if (kind() == Kind.ARROW || kind() == Kind.NEEDLE) {
                serverLevel.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 3, 0.05, 0.05, 0.05, 0.05);
            } else {
                serverLevel.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.02);
            }
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }
}
