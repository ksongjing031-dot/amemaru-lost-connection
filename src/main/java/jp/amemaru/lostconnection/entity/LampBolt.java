package jp.amemaru.lostconnection.entity;

import jp.amemaru.lostconnection.combat.CombatTargets;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.job.SkillContext;
import jp.amemaru.lostconnection.job.SkillExecutor;
import net.minecraft.core.particles.DustParticleOptions;
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
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * スキルの弾（ウィザードの「灯火弾」など）。まっすぐ飛び、最初に当たった敵にスキルの効果を出す。
 *
 * <p>重さはなく、射程を過ぎるか、壁に当たると消える。ワールドには保存しない。見た目は光の玉
 * （{@code textures/entity/lamp_bolt.png}）と、通った後の光の粒。</p>
 */
public class LampBolt extends Projectile {
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.35F), 1.0F);
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

    /** 使った人の目の前から、見ている方向へ撃つ。 */
    public static void launch(SkillContext context, ServerPlayer caster, double speed, double range, double hitRadius) {
        LampBolt bolt = new LampBolt(ModEntities.LAMP_BOLT.get(), caster.level());
        bolt.setOwner(caster);
        bolt.context = context;
        bolt.maxDistance = range;
        bolt.hitRadius = hitRadius;
        Vec3 look = caster.getLookAngle();
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
            for (int i = 0; i < 2; i++) {
                double t = i / 2.0;
                level().addParticle(TRAIL, getX() - motion.x * t, getY() - motion.y * t, getZ() - motion.z * t, 0.0, 0.0, 0.0);
            }
            if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
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
