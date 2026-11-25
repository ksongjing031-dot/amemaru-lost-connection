package jp.amemaru.lostconnection.enemy.lake;

import jp.amemaru.lostconnection.combat.LostService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.enemy.CrystalBee;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;

/**
 * 氷霧の亡霊（8階の雑魚。Phase 0-A 9章の8階）：霧の衣をまとった、足のない亡霊。湖の霧にまぎれて近づき、冷たい手で触れる（claw）。
 * 触れられると、ロスト状態「断線」（パーティの欄から仲間のHPが消える。13章の表「主な原因は8階」）。ときどき霧に溶けて、狙った人の
 * そばへ移る（blink。消える前に霧が濃くなる）【補完】。
 */
public class FogWraith extends CrystalBee {
    public FogWraith(EntityType<? extends FogWraith> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "fog_wraith";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BlinkGoal());
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "claw", "attack", TELEGRAPH_WINDUP, 1.15, SoundEvents.VEX_CHARGE));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_blink"};
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        EnemyDef def = def();
        double seconds = def == null ? 10.0 : def.behavior("sever_seconds", 10.0);
        LostService.inflict(player, ModEffects.LOST_SEVER.get(), (int) Math.round(seconds * 20));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive() && random.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.CLOUD, getRandomX(0.8), getY() + random.nextDouble() * 0.6, getRandomZ(0.8), 0.0, -0.01, 0.0);
        }
    }

    private EnemyDef.Skill blink() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(0.0, 0.8, 9.0, 16.0, Map.of()) : def.skill("blink", 0.0, 0.8, 9.0, 16.0);
    }

    /** 霧に溶けて、狙った人のそば（後ろ）へ移る。 */
    private final class BlinkGoal extends Goal {
        private int windup = -1;

        BlinkGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || isBusy() || isReturning() || !ready("blink") || def() == null) {
                return false;
            }
            double d = distanceTo(target);
            return d >= blink().param("min_range", 6.0) && d <= blink().range();
        }

        @Override
        public boolean canContinueToUse() {
            return windup >= 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            windup = Math.max(1, blink().windupTicks());
            setBusy(windup + 6);
            getNavigation().stop();
            playAction("skill_blink");
            if (level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 30, 0.6, 0.6, 0.6, 0.02);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.HOSTILE, 1.0F, 0.5F);
            }
        }

        @Override
        public void stop() {
            windup = -1;
        }

        @Override
        public void tick() {
            getNavigation().stop();
            if (windup-- > 0) {
                return;
            }
            LivingEntity target = getTarget();
            if (target != null && level() instanceof ServerLevel level) {
                Vec3 behind = target.position().subtract(Vec3.directionFromRotation(0.0F, target.getYRot()).scale(2.5));
                BlockPos at = BlockPos.containing(behind.x, target.getY() + 0.5, behind.z);
                if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
                    level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 20, 0.5, 0.5, 0.5, 0.02);
                    teleportTo(behind.x, target.getY() + 0.3, behind.z);
                    faceTowards(target.position());
                    level.sendParticles(ParticleTypes.CLOUD, behind.x, target.getY() + 1.0, behind.z, 30, 0.6, 0.6, 0.6, 0.02);
                }
            }
            startCooldown("blink", blink().cooldownTicks());
            windup = -1;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }
}
