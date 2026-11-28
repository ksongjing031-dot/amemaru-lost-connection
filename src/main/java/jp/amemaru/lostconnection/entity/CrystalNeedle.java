package jp.amemaru.lostconnection.entity;

import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 結晶蜂の針。まっすぐ飛び、最初に当たったプレイヤーに、撃った敵の攻撃 × 技の倍率のダメージを与える。
 * 重さはなく、3秒たつか壁に当たると消える。ワールドには保存しない。
 */
public class CrystalNeedle extends Projectile {
    private static final int MAX_LIFE = 60;
    /** 見た目（0：結晶の針、1：舞う頁。11月28日：9階の書架霊）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_STYLE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CrystalNeedle.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    public static final int STYLE_CRYSTAL = 0;
    public static final int STYLE_PAGE = 1;

    private double power = 1.0;
    /** 守りのいくらを通り抜けるか（11月28日。魔法の頁）。 */
    private double pierce;
    private int life;

    public CrystalNeedle(EntityType<? extends CrystalNeedle> type, Level level) {
        super(type, level);
    }

    /** 撃つ。 */
    public static void shoot(AmemaruMob owner, Vec3 from, Vec3 direction, double speed, double power) {
        CrystalNeedle needle = new CrystalNeedle(ModEntities.CRYSTAL_NEEDLE.get(), owner.level());
        needle.setOwner(owner);
        needle.power = power;
        // 11月28日：撃つ敵ごとの見た目と、守りを通り抜ける割合（9階の書架霊の頁）
        needle.entityData.set(DATA_STYLE, (byte) owner.projectileStyle());
        needle.pierce = owner.projectilePierce();
        needle.setPos(from.x, from.y, from.z);
        Vec3 motion = direction.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, speed) : direction.normalize().scale(speed);
        needle.setDeltaMovement(motion);
        ProjectileUtil.rotateTowardsMovement(needle, 1.0F);
        owner.level().addFreshEntity(needle);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_STYLE, (byte) STYLE_CRYSTAL);
    }

    /** 見た目（{@link #STYLE_CRYSTAL}・{@link #STYLE_PAGE}。クライアントでも読める）。 */
    public int style() {
        return entityData.get(DATA_STYLE);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (!level().isClientSide()) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                if (isRemoved()) {
                    return;
                }
            }
            if (++life > MAX_LIFE) {
                discard();
                return;
            }
        } else if (style() == STYLE_PAGE) {
            // 頁：青白い文字の粒がこぼれる
            level().addParticle(ParticleTypes.ENCHANT, getX() - motion.x * 0.5, getY() - motion.y * 0.5,
                    getZ() - motion.z * 0.5, 0.0, 0.0, 0.0);
        } else {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() - motion.x * 0.5, getY() - motion.y * 0.5,
                    getZ() - motion.z * 0.5, 0.0, 0.0, 0.0);
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        ProjectileUtil.rotateTowardsMovement(this, 1.0F);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof Player player && !player.isSpectator() && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (result.getEntity() instanceof ServerPlayer player && getOwner() instanceof AmemaruMob mob) {
            EnemyCombat.strike(mob, player, power, this, pierce);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WAX_OFF, getX(), getY(), getZ(), 4, 0.05, 0.05, 0.05, 0.02);
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
