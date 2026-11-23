package jp.amemaru.lostconnection.enemy.forest;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * 迷い灯（2階の雑魚。Phase 0-A 9章「道から外れた方へ誘う光の玉」）。
 *
 * <ul>
 *   <li>人が近づくと、少し離れた所（9ブロックほど）を保って逃げ、近くの仲間（ほかの魔物）のいる方へ誘う【補完】</li>
 *   <li>追いつかれると、まわりへ光の火花（周りの円の予兆のあと）</li>
 *   <li>打たれると、まぶしく光って（近くの人は少しの間、目がくらむ）6〜8ブロック離れた所へ消えて現れる（6秒に1回）</li>
 * </ul>
 */
public class MayoiBi extends AmemaruMob {
    public MayoiBi(EntityType<? extends MayoiBi> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createLightAttributes() {
        return createAttributes().add(Attributes.FLYING_SPEED, 0.5);
    }

    @Override
    public String assetName() {
        return "mayoi_bi";
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LureGoal(this));
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "spark", "attack", TELEGRAPH_CIRCLE, 1.0, SoundEvents.AMETHYST_CLUSTER_BREAK));
    }

    @Override
    public void onDamagedBy(@Nullable Entity attacker, double amount) {
        super.onDamagedBy(attacker, amount);
        if (!(level() instanceof ServerLevel level) || isDeadOrDying() || !ready("blink")) {
            return;
        }
        EnemyDef def = def();
        startCooldown("blink", (int) Math.round((def == null ? 6.0 : def.behavior("blink_cooldown", 6.0)) * 20));
        // まぶしく光る（近くの人は少しの間、目がくらむ）
        level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 0.3, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.0F, 1.8F);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(5.0))) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false, true));
        }
        // 6〜8ブロック離れた所へ消えて現れる
        for (int tries = 0; tries < 12; tries++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 6.0 + random.nextDouble() * 2.0;
            double x = getX() + Math.cos(angle) * dist;
            double z = getZ() + Math.sin(angle) * dist;
            BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(x, getY(), z));
            double y = Math.min(getY() + 2.0, ground.getY() + 1.5);
            if (Math.abs(y - getY()) > 4.0) {
                continue;
            }
            Vec3 to = new Vec3(x, y, z);
            if (level.noCollision(this, getBoundingBox().move(to.subtract(position())))) {
                level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                teleportTo(x, y, z);
                level.sendParticles(ParticleTypes.END_ROD, x, y + 0.3, z, 8, 0.2, 0.2, 0.2, 0.05);
                break;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive() && random.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.GLOW, getRandomX(0.5), getY() + 0.3 + random.nextDouble() * 0.3, getRandomZ(0.5), 0.0, 0.01, 0.0);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_CLUSTER_BREAK;
    }

    /** 誘う：追いかけてくる人から、少し離れた所を保って逃げる（仲間のいる方へ）。追いつかれたら火花に任せる。 */
    static class LureGoal extends Goal {
        private final MayoiBi light;
        private int repath;

        LureGoal(MayoiBi light) {
            this.light = light;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = light.getTarget();
            return target != null && target.isAlive() && !light.isReturning() && !light.isBusy() && light.distanceTo(target) > 3.5;
        }

        @Override
        public void tick() {
            LivingEntity target = light.getTarget();
            if (target == null || --repath > 0) {
                return;
            }
            repath = 12;
            EnemyDef def = light.def();
            double keep = def == null ? 9.0 : def.behavior("lure_distance", 9.0);
            Vec3 away = light.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0E-4) {
                away = new Vec3(1, 0, 0);
            }
            away = away.normalize();
            // 近くの仲間のいる方へ、少し曲げる（誘い込み）
            AmemaruMob ally = null;
            double best = 24.0 * 24.0;
            for (AmemaruMob mob : light.level().getEntitiesOfClass(AmemaruMob.class, light.getBoundingBox().inflate(24.0))) {
                if (mob != light && !(mob instanceof MayoiBi) && mob.isAlive()) {
                    double d = mob.distanceToSqr(light);
                    if (d < best) {
                        best = d;
                        ally = mob;
                    }
                }
            }
            Vec3 dir = away;
            if (ally != null) {
                Vec3 toAlly = ally.position().subtract(light.position()).multiply(1.0, 0.0, 1.0);
                if (toAlly.lengthSqr() > 1.0E-4) {
                    dir = away.add(toAlly.normalize().scale(0.8)).normalize();
                }
            }
            Vec3 goal = target.position().add(dir.scale(keep)).add(0.0, 1.5, 0.0);
            light.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
        }

        @Override
        public void stop() {
            light.getNavigation().stop();
        }
    }
}
