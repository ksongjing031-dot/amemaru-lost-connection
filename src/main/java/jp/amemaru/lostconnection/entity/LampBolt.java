package jp.amemaru.lostconnection.entity;

import jp.amemaru.lostconnection.combat.CombatTargets;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.job.SkillContext;
import jp.amemaru.lostconnection.job.SkillExecutor;
import jp.amemaru.lostconnection.core.registry.ModParticles;
import jp.amemaru.lostconnection.particle.FxParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
 * スキルの弾（ウィザードの「灯火弾」など）。まっすぐ飛び、最初に当たった敵にスキルの効果を出す。
 *
 * <p>重さはなく、射程を過ぎるか、壁に当たると消える。ワールドには保存しない。見た目は光の玉
 * （{@code textures/entity/lamp_bolt.png}）と、通った後の光の粒。</p>
 */
public class LampBolt extends Projectile {
    /** 尾の光の粒（使うときに初めて作る：登録が済む前に作らないため）。 */
    private static final class Trail {
        static final FxParticleOptions GLOW = new FxParticleOptions(ModParticles.GLOW.get(), 1.0F, 0.62F, 0.25F, 0.55F, 9);
        static final FxParticleOptions CORE = new FxParticleOptions(ModParticles.GLOW.get(), 1.0F, 0.95F, 0.8F, 0.3F, 4);
        static final FxParticleOptions SPARK = new FxParticleOptions(ModParticles.SPARK.get(), 1.0F, 0.8F, 0.4F, 0.07F, 8);

        private Trail() {
        }
    }
    private static final int MAX_LIFE = 200;

    @Nullable
    private SkillContext context;
    private double maxDistance = 24.0;
    private double hitRadius;
    @Nullable
    private Vec3 start;
    private int life;

    public LampBolt(EntityType<? extends LampBolt> type, Level level) {
        super(type, level);
    }

    /** 使った人の目の前から、{@code direction} の方向へ撃つ（灯火の連弾は、扇に広げて何発も撃つ）。 */
    public static void launch(SkillContext context, ServerPlayer caster, Vec3 direction, double speed, double range, double hitRadius) {
        LampBolt bolt = new LampBolt(ModEntities.LAMP_BOLT.get(), caster.level());
        bolt.setOwner(caster);
        bolt.context = context;
        bolt.maxDistance = range;
        bolt.hitRadius = hitRadius;
        Vec3 look = direction.normalize();
        Vec3 eye = caster.getEyePosition();
        bolt.setPos(eye.x + look.x * 0.7, eye.y - 0.15 + look.y * 0.7, eye.z + look.z * 0.7);
        bolt.start = bolt.position();
        bolt.setDeltaMovement(look.scale(speed));
        caster.level().addFreshEntity(bolt);
    }

    @Override
    protected void defineSynchedData() {
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
            if (context == null || life > MAX_LIFE || (start != null && position().distanceToSqr(start) > maxDistance * maxDistance)) {
                fizzle();
                return;
            }
        } else {
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
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        ProjectileUtil.rotateTowardsMovement(this, 1.0F);
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
        if (context != null && result.getEntity() instanceof LivingEntity living) {
            SkillExecutor.projectileHit(context, living, this);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        fizzle();
    }

    private void fizzle() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.02);
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
