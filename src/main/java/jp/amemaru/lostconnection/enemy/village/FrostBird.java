package jp.amemaru.lostconnection.enemy.village;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.enemy.CrystalBee;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.TowerS2C;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Map;

/**
 * 凍て鳥（6階の雑魚。Phase 0-A 9章の6階「凍て鳥（吹雪を起こす）」）：白と氷青の羽の、大きな鳥。空に浮き、氷の羽根を撃つ
 * （1階の結晶蜂と同じ動き。技 needle）。ときどき羽を大きく広げ（予兆）、狙った人のまわりに吹雪を起こす（技 blizzard）：
 * 地面の円の予兆のあと、seconds 秒のあいだ、円の中の人は1秒ごとに power の傷と凍えを受け、近くが見えにくくなる【補完】。
 */
public class FrostBird extends CrystalBee {
    @Nullable
    private Vec3 storm;
    private int stormTicks;

    public FrostBird(EntityType<? extends FrostBird> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "frost_bird";
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new BlizzardGoal());
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_needle", "skill_blizzard"};
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        PlayerColdService.chill(player, 1, 60);
    }

    private EnemyDef.Skill blizzard() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(0.3, 1.8, 16.0, 16.0, Map.of()) : def.skill("blizzard", 0.3, 1.8, 16.0, 16.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (isAlive() && random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0.0, -0.03, 0.0);
            }
            return;
        }
        if (storm == null || !(level() instanceof ServerLevel level)) {
            return;
        }
        EnemyDef.Skill skill = blizzard();
        double radius = skill.param("radius", 6.0);
        if (stormTicks % 4 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, storm.x, storm.y + 1.5, storm.z, 30, radius * 0.6, 1.2, radius * 0.6, 0.15);
            level.sendParticles(ParticleTypes.CLOUD, storm.x, storm.y + 0.8, storm.z, 6, radius * 0.5, 0.6, radius * 0.5, 0.05);
        }
        if (stormTicks % 20 == 0) {
            level.playSound(null, storm.x, storm.y, storm.z, SoundEvents.SNOW_BREAK, SoundSource.HOSTILE, 1.4F, 0.5F);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(storm, storm).inflate(radius, 4.0, radius))) {
                if (player.position().distanceTo(new Vec3(storm.x, player.getY(), storm.z)) > radius) {
                    continue;
                }
                EnemyCombat.strike(this, player, skill.power(), null);
                PlayerColdService.chill(player, 1, (int) Math.round(skill.param("chill_seconds", 4.0) * 20));
                onStormHit(player);
                CompoundTag tag = new CompoundTag();
                tag.putFloat("strength", 0.8F);
                tag.putInt("ticks", 30);
                ModNetwork.sendTo(player, new TowerS2C("blizzard", tag));
            }
        }
        if (--stormTicks <= 0) {
            storm = null;
        }
    }

    /** 吹雪に打たれた人への、ほかの効き目（7階の吹雪の精は「薄れ」。11月25日）。 */
    protected void onStormHit(ServerPlayer player) {
    }

    /** 自動テスト・管理者：その場所に、すぐ吹雪を起こす。 */
    public void testStartStorm(Vec3 at) {
        storm = at;
        stormTicks = (int) Math.round(blizzard().param("seconds", 5.0) * 20);
    }

    /** 自動テスト用：吹雪の真ん中（なければ null）。 */
    @Nullable
    public Vec3 testStorm() {
        return storm;
    }

    /** 羽を広げて、狙った人のまわりに吹雪を起こす。 */
    private final class BlizzardGoal extends Goal {
        private int windup = -1;
        @Nullable
        private Vec3 at;

        BlizzardGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || isBusy() || !ready("blizzard") || storm != null) {
                return false;
            }
            return distanceTo(target) <= blizzard().range();
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
            EnemyDef.Skill skill = blizzard();
            LivingEntity target = getTarget();
            at = target == null ? position() : target.position();
            windup = Math.max(1, skill.windupTicks());
            setBusy(windup + 10);
            getNavigation().stop();
            playAction("skill_blizzard");
            if (level() instanceof ServerLevel level) {
                GroundMarker.place(level, at, (float) skill.param("radius", 6.0), windup, GroundMarker.KIND_DANGER);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.6F, 0.6F);
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
            EnemyDef.Skill skill = blizzard();
            storm = at;
            stormTicks = (int) Math.round(skill.param("seconds", 5.0) * 20);
            startCooldown("blizzard", skill.cooldownTicks());
            windup = -1;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PARROT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + (random.nextFloat() - 0.5F) * 0.1F;
    }
}
