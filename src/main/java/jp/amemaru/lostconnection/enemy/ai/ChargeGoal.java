package jp.amemaru.lostconnection.enemy.ai;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 突進（欠け牙の狼など）：止まって、地面に突進の線を大きく見せ（予兆）、その線の上をまっすぐ走り抜ける。
 * 線の上にいたプレイヤーに1回ずつ当たる。走り終わった後は少しの間すきができる。
 *
 * <p>数値は敵の定義の技 {@code skills.charge}：power（倍率）・windup（予兆の秒）・cooldown・range（線の長さの上限）・
 * min_range（これより近いと使わない）・speed（1 tick に進むブロック）・width（当たる幅）・recover（すきの秒）。</p>
 */
public class ChargeGoal extends Goal {
    private final AmemaruMob mob;
    @Nullable
    private final SoundEvent sound;

    private int phase;
    private int timer;
    private float yaw;
    private double length;
    private double traveled;
    private Vec3 direction = Vec3.ZERO;
    private final Set<UUID> hit = new HashSet<>();

    public ChargeGoal(AmemaruMob mob, @Nullable SoundEvent sound) {
        this.mob = mob;
        this.sound = sound;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private EnemyDef.Skill skill() {
        EnemyDef def = mob.def();
        return def == null ? new EnemyDef.Skill(1.8, 1.2, 8.0, 12.0, java.util.Map.of())
                : def.skill("charge", 1.8, 1.2, 8.0, 12.0);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || mob.isReturning() || mob.isBusy() || !mob.ready("charge") || mob.def() == null) {
            return false;
        }
        EnemyDef.Skill skill = skill();
        double distance = mob.distanceTo(target);
        return distance >= skill.param("min_range", 5.0) && distance <= skill.range() && mob.hasLineOfSight(target)
                && mob.onGround();
    }

    @Override
    public boolean canContinueToUse() {
        return phase > 0 && mob.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        EnemyDef.Skill skill = skill();
        mob.getNavigation().stop();
        if (target != null) {
            mob.faceTowards(target.position());
            length = Math.min(skill.range(), mob.distanceTo(target) + 3.0);
        } else {
            length = skill.range();
        }
        yaw = mob.getYRot();
        direction = Vec3.directionFromRotation(0.0F, yaw).normalize();
        timer = Math.max(1, skill.windupTicks());
        phase = 1;
        traveled = 0.0;
        hit.clear();
        mob.setBusy(timer + 60);
        mob.playAction("skill_charge");
        mob.setTelegraph(AmemaruMob.TELEGRAPH_LINE, timer, yaw, (float) length);
    }

    @Override
    public void stop() {
        phase = 0;
        mob.clearTelegraph();
        mob.clearBusy();
        mob.startCooldown("charge", mob.chargeCooldown(skill().cooldownTicks()));
    }

    @Override
    public void tick() {
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        EnemyDef.Skill skill = skill();
        if (phase == 1) {
            // 予兆：止まって、地面に線を見せる
            mob.getNavigation().stop();
            mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
            if (--timer <= 0) {
                phase = 2;
                mob.clearTelegraph();
                if (sound != null) {
                    mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.HOSTILE, 1.2F, 0.8F);
                }
            }
            return;
        }
        if (phase == 2) {
            double speed = skill.param("speed", 0.85);
            mob.setDeltaMovement(direction.x * speed, mob.getDeltaMovement().y, direction.z * speed);
            mob.hasImpulse = true;
            traveled += speed;
            double width = skill.param("width", 1.2);
            if (mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.2, mob.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
                for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(width))) {
                    if (hit.add(player.getUUID())) {
                        EnemyCombat.strike(mob, player, skill.power(), null);
                    }
                }
            }
            if (traveled >= length || mob.horizontalCollision) {
                phase = 3;
                timer = (int) Math.round(skill.param("recover", 1.0) * 20.0);
                // 11月28日：9階の氷の騎兵は、壁にぶつかるとよろめく
                timer = Math.max(timer, mob.onChargeEnded(mob.horizontalCollision && traveled < length));
                mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
            }
            return;
        }
        // すき：その場で息を整える
        mob.getNavigation().stop();
        if (--timer <= 0) {
            phase = 0;
        }
    }
}
