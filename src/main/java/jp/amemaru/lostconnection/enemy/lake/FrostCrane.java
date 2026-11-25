package jp.amemaru.lostconnection.enemy.lake;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.KakekibaWolf;
import net.minecraft.core.particles.ParticleTypes;
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

import java.util.EnumSet;
import java.util.Map;

/**
 * 凍て鶴（8階の雑魚。Phase 0-A 9章の8階）：白い羽に黒い風切り、頭に霜の冠をのせた鶴。くちばしで突き（bite）、低く翼を広げて
 * 滑るように突進し（charge。1階の欠け牙の狼と同じ動き）、翼を大きく振って前の扇に吹雪を叩きつける（gust。押し返し、凍える）【補完】。
 * 名のある魔物「霜冠」（frost_crane_lord）は大きく、吹雪が強い。
 */
public class FrostCrane extends KakekibaWolf {
    public FrostCrane(EntityType<? extends FrostCrane> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "frost_crane";
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new GustGoal());
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_charge", "skill_gust"};
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        EnemyDef def = def();
        int stacks = def == null ? 1 : (int) Math.round(def.behavior("chill_stacks", 1.0));
        double seconds = def == null ? 4.0 : def.behavior("chill_seconds", 4.0);
        PlayerColdService.chill(player, stacks, (int) Math.round(seconds * 20));
    }

    private EnemyDef.Skill gust() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(1.2, 1.1, 9.0, 6.0, Map.of()) : def.skill("gust", 1.2, 1.1, 9.0, 6.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive() && random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.6), getY() + getBbHeight() * 0.9, getRandomZ(0.6), 0.0, -0.02, 0.0);
        }
    }

    /** 翼を振って、前の扇に吹雪を叩きつける。 */
    private final class GustGoal extends Goal {
        private int windup = -1;
        private float yaw;

        GustGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || isBusy() || isReturning() || !ready("gust") || def() == null) {
                return false;
            }
            return distanceTo(target) <= gust().range() - 0.5;
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
            EnemyDef.Skill skill = gust();
            LivingEntity target = getTarget();
            if (target != null) {
                faceTowards(target.position());
            }
            yaw = getYRot();
            windup = Math.max(1, skill.windupTicks());
            setBusy(windup + 10);
            getNavigation().stop();
            playAction("skill_gust");
            setTelegraph(TELEGRAPH_CONE, windup, yaw, (float) skill.range(), (float) skill.param("angle", 100.0));
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.4F, 0.8F);
        }

        @Override
        public void stop() {
            if (windup >= 0) {
                clearTelegraph();
            }
            windup = -1;
        }

        @Override
        public void tick() {
            getNavigation().stop();
            setYRot(yaw);
            setYBodyRot(yaw);
            if (windup-- > 0) {
                return;
            }
            clearTelegraph();
            EnemyDef.Skill skill = gust();
            double half = skill.param("angle", 100.0) / 2.0;
            if (level() instanceof ServerLevel level) {
                for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(position(), position()).inflate(skill.range() + 1.0))) {
                    if (player.distanceTo(FrostCrane.this) > skill.range() + 0.6 || !inFront(player.position(), half)) {
                        continue;
                    }
                    if (EnemyCombat.strike(FrostCrane.this, player, skill.power(), null)) {
                        Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                        if (away.lengthSqr() > 1.0E-4) {
                            away = away.normalize().scale(skill.param("push", 1.0));
                            player.push(away.x, 0.25, away.z);
                            player.hurtMarked = true;
                        }
                        PlayerColdService.chill(player, (int) Math.round(skill.param("chill", 1.0)), 80);
                    }
                }
                double yawRad = Math.toRadians(yaw);
                for (int i = 0; i <= 8; i++) {
                    double a = yawRad + Math.toRadians(-half + half * 2.0 * i / 8.0);
                    for (double r = 1.0; r <= skill.range(); r += 1.0) {
                        level.sendParticles(ParticleTypes.SNOWFLAKE, getX() - Math.sin(a) * r, getY() + 0.8, getZ() + Math.cos(a) * r, 2, 0.2, 0.2,
                                0.2, 0.05);
                    }
                }
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.SNOW_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
            }
            startCooldown("gust", skill.cooldownTicks());
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
        return 0.5F + (random.nextFloat() - 0.5F) * 0.1F;
    }
}
