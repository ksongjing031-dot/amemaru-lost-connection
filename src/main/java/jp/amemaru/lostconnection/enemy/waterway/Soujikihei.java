package jp.amemaru.lostconnection.enemy.waterway;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.Patroller;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import jp.amemaru.lostconnection.enemy.ai.PatrolGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 水路掃除機兵（3階の雑魚。Phase 0-A 9章「古代の清掃機械」）。水路の底のごみを掃いてきた、青銅の機械。決まった道を巡回する。
 *
 * <ul>
 *   <li>正面に厚い装甲の板。前から受けるダメージは半分（定義の {@code behavior.front_armor}。後ろか横から攻める）【補完】</li>
 *   <li>回るブラシの薙ぎ払い（前の扇）と、水の噴射（一直線の予兆のあと、線の上の人を押し流す）</li>
 * </ul>
 */
public class Soujikihei extends AmemaruMob implements Patroller {
    private final List<BlockPos> patrol = new ArrayList<>();
    /** 正面の装甲に弾かれたことを、もう知らせた人（1人に1回）。 */
    private static final Set<UUID> TOLD = new HashSet<>();
    private int lastClang;

    public Soujikihei(EntityType<? extends Soujikihei> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "soujikihei";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new JetGoal());
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "sweep", "attack", TELEGRAPH_CONE, 0.9, SoundEvents.GRINDSTONE_USE));
        goalSelector.addGoal(4, new PatrolGoal(this, this::route, 0.6));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() {
                return patrol.isEmpty() && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_jet"};
    }

    /** 巡回の道（湧く所の route。なければ、その場のまわりを歩く）。 */
    public List<BlockPos> route() {
        return patrol;
    }

    @Override
    public void setPatrol(List<BlockPos> points) {
        patrol.clear();
        patrol.addAll(points);
    }

    // ---------------------------------------------------------------- 正面の装甲

    /** 打った人が前（左右 behavior.front_angle/2 度の内）にいれば、装甲で弾く（behavior.front_armor 倍）。 */
    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        if (attacker == null) {
            return 1.0;
        }
        EnemyDef def = def();
        double angle = def == null ? 140.0 : def.behavior("front_angle", 140.0);
        if (!inFront(attacker.position(), angle / 2.0)) {
            return 1.0;
        }
        double armor = def == null ? 0.5 : def.behavior("front_armor", 0.5);
        clang(attacker);
        return armor;
    }

    /** 装甲に弾かれた：火花と金属の音。はじめての人には、手がかりを出す。 */
    private void clang(Entity attacker) {
        if (!(level() instanceof ServerLevel level) || tickCount - lastClang < 4) {
            return;
        }
        lastClang = tickCount;
        Vec3 front = position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(getBbWidth() * 0.6)).add(0.0, getBbHeight() * 0.55, 0.0);
        level.sendParticles(ParticleTypes.CRIT, front.x, front.y, front.z, 8, 0.25, 0.3, 0.25, 0.2);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.35F, 1.6F);
        if (attacker instanceof ServerPlayer player && TOLD.add(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.amemaru.enemy.front_armor").withStyle(ChatFormatting.GOLD), true);
        }
    }

    // ---------------------------------------------------------------- 水の噴射

    /** 水の噴射：止まって一直線の予兆を出し、線の上の人に当てて押し流す。 */
    private class JetGoal extends Goal {
        private int windup = -1;
        private float yaw;
        private Vec3 dir = Vec3.ZERO;
        private double length;

        JetGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private EnemyDef.Skill skill() {
            EnemyDef def = def();
            return def == null ? new EnemyDef.Skill(1.3, 1.2, 7.0, 9.0, java.util.Map.of()) : def.skill("jet", 1.3, 1.2, 7.0, 9.0);
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || isReturning() || isBusy() || !ready("jet") || def() == null) {
                return false;
            }
            EnemyDef.Skill skill = skill();
            double distance = distanceTo(target);
            return distance >= skill.param("min_range", 3.0) && distance <= skill.range() && hasLineOfSight(target);
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
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            EnemyDef.Skill skill = skill();
            getNavigation().stop();
            faceTowards(target.position());
            yaw = getYRot();
            Vec3 to = target.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            dir = to.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, yaw) : to.normalize();
            length = Math.min(skill.range(), to.length() + 2.0);
            windup = Math.max(1, skill.windupTicks());
            setBusy(windup + 10);
            playAction("skill_jet");
            setTelegraph(TELEGRAPH_LINE, windup, yaw, (float) length, (float) skill.param("width", 1.6));
            if (level() instanceof ServerLevel level) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 1.4F, 0.8F);
            }
        }

        @Override
        public void tick() {
            if (windup < 0) {
                return;
            }
            setYRot(yaw);
            setYBodyRot(yaw);
            setYHeadRot(yaw);
            getNavigation().stop();
            if (level() instanceof ServerLevel level && windup % 3 == 0) {
                // ためる間：筒の口に水が集まる
                Vec3 mouth = position().add(dir.scale(getBbWidth() * 0.7)).add(0.0, getBbHeight() * 0.55, 0.0);
                level.sendParticles(ParticleTypes.BUBBLE_POP, mouth.x, mouth.y, mouth.z, 4, 0.15, 0.15, 0.15, 0.02);
                level.sendParticles(ParticleTypes.DRIPPING_WATER, mouth.x, mouth.y, mouth.z, 2, 0.1, 0.1, 0.1, 0.0);
            }
            if (windup-- == 0) {
                fire();
            }
        }

        private void fire() {
            clearTelegraph();
            windup = -1;
            if (!(level() instanceof ServerLevel level)) {
                return;
            }
            EnemyDef.Skill skill = skill();
            double half = skill.param("width", 1.6) / 2.0 + 0.4;
            double push = skill.param("push", 1.0);
            AABB box = getBoundingBox().inflate(length + 1.0, 2.0, length + 1.0);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
                Vec3 rel = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                double along = rel.dot(dir);
                double side = rel.subtract(dir.scale(along)).length();
                if (along >= 0.0 && along <= length + 0.6 && side <= half && Math.abs(player.getY() - getY()) < 2.5
                        && EnemyCombat.strike(Soujikihei.this, player, skill.power(), null)) {
                    player.push(dir.x * push, 0.25, dir.z * push);
                    player.hurtMarked = true;
                }
            }
            for (double t = 1.0; t <= length; t += 0.7) {
                Vec3 at = position().add(dir.scale(t));
                level.sendParticles(ParticleTypes.SPLASH, at.x, getY() + 1.0, at.z, 8, 0.25, 0.25, 0.25, 0.2);
                level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, getY() + 1.0, at.z, 2, 0.2, 0.2, 0.2, 0.05);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.4F, 0.9F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 1.0F, 1.0F);
            startCooldown("jet", skill.cooldownTicks());
        }

        @Override
        public void stop() {
            if (windup >= 0) {
                clearTelegraph();
            }
            windup = -1;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.IRON_GOLEM_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.4F, 1.3F);
    }

    @Override
    public float getVoicePitch() {
        return 1.2F;
    }
}
