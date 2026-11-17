package jp.amemaru.lostconnection.enemy.ai;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * 近づいて、予兆の後に前を打つ（体当たり・叩きつけ・噛みつき）。
 *
 * <p>予兆の間は向きを固定する（予兆を見て横へ動けば避けられる）。予兆の長さ・威力・届く距離は、敵の定義の技
 * （{@code skills.<名前>}）で決める。{@code angle} は前のどれだけを打つか（度。360なら周り全部）。</p>
 */
public class EnemyMeleeGoal extends Goal {
    private final AmemaruMob mob;
    private final String skillName;
    private final String animation;
    private final int telegraphKind;
    private final double speed;
    @Nullable
    private final SoundEvent sound;

    private int windup = -1;
    private float lockedYaw;
    private int repath;

    public EnemyMeleeGoal(AmemaruMob mob, String skillName, String animation, int telegraphKind, double speed,
                          @Nullable SoundEvent sound) {
        this.mob = mob;
        this.skillName = skillName;
        this.animation = animation;
        this.telegraphKind = telegraphKind;
        this.speed = speed;
        this.sound = sound;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && !mob.isReturning() && mob.def() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return windup >= 0 || canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        windup = -1;
        repath = 0;
    }

    @Override
    public void stop() {
        if (windup >= 0) {
            mob.clearTelegraph();
        }
        windup = -1;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        EnemyDef def = mob.def();
        if (def == null) {
            return;
        }
        EnemyDef.Skill skill = def.skill(skillName, 1.0, 0.5, 1.5, 2.5);
        if (windup >= 0) {
            // 予兆の間は向きを固定して止まる
            mob.setYRot(lockedYaw);
            mob.setYBodyRot(lockedYaw);
            mob.setYHeadRot(lockedYaw);
            mob.getNavigation().stop();
            if (windup-- == 0) {
                strike(skill);
                mob.startCooldown(skillName, skill.cooldownTicks());
            }
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = mob.distanceTo(target) - target.getBbWidth() / 2.0;
        if (distance <= skill.range() && mob.ready(skillName) && !mob.isBusy()) {
            mob.getNavigation().stop();
            mob.faceTowards(target.position());
            lockedYaw = mob.getYRot();
            windup = Math.max(1, skill.windupTicks());
            mob.setBusy(windup + 8);
            mob.playAction(animation);
            mob.setTelegraph(telegraphKind, windup, lockedYaw, (float) skill.range());
            return;
        }
        if (distance > skill.range() * 0.8) {
            if (--repath <= 0) {
                repath = 10;
                mob.getNavigation().moveTo(target, speed);
            }
        } else {
            mob.getNavigation().stop();
        }
    }

    /** 予兆が終わったら、前（または周り）のプレイヤーに当てる。 */
    private void strike(EnemyDef.Skill skill) {
        mob.clearTelegraph();
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        double range = skill.range() + 0.6;
        double halfAngle = skill.param("angle", 100.0) / 2.0;
        AABB box = mob.getBoundingBox().inflate(range, 1.5, range);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            Vec3 center = player.position();
            double dx = center.x - mob.getX();
            double dz = center.z - mob.getZ();
            double reach = Math.sqrt(dx * dx + dz * dz) - player.getBbWidth() / 2.0;
            if (reach > range || (halfAngle < 180.0 && !mob.inFront(center, halfAngle))) {
                continue;
            }
            EnemyCombat.strike(mob, player, skill.power(), null);
        }
        if (sound != null) {
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.HOSTILE, 1.0F,
                    0.9F + mob.getRandom().nextFloat() * 0.2F);
        }
    }
}
