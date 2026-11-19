package jp.amemaru.lostconnection.enemy.ai;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.entity.CrystalNeedle;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 空を飛び、相手から少し離れた高さに浮いて、光る予兆の後に針を撃つ（結晶蜂）。相手がいないときは、生まれた場所の
 * まわりをゆっくり漂う。
 *
 * <p>数値は敵の定義：技 {@code skills.needle}（power・windup・cooldown・range（撃つ距離）・speed（針の速さ））と、
 * 行動 {@code behavior}（keep_distance：保つ距離、hover_height：相手の目より上に浮く高さ）。</p>
 */
public class HoverShootGoal extends Goal {
    private final AmemaruMob mob;
    private int windup = -1;
    private int wanderTimer;
    private Vec3 wander = Vec3.ZERO;

    public HoverShootGoal(AmemaruMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !mob.isReturning() && mob.def() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        if (windup >= 0) {
            mob.clearTelegraph();
        }
        windup = -1;
    }

    @Override
    public void tick() {
        EnemyDef def = mob.def();
        if (def == null) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            wanderAroundHome();
            return;
        }
        EnemyDef.Skill skill = def.skill("needle", 1.0, 0.7, 2.5, 14.0);
        double keep = def.behavior("keep_distance", 7.0);
        double height = def.behavior("hover_height", 2.5);

        // 相手から keep だけ離れた、少し高い場所へ
        Vec3 eye = target.getEyePosition();
        Vec3 away = mob.position().subtract(target.position());
        Vec3 flat = new Vec3(away.x, 0.0, away.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
        Vec3 wanted = eye.add(flat.scale(keep)).add(0.0, height - 0.5, 0.0);
        mob.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, windup >= 0 ? 0.2 : 1.0);
        mob.getLookControl().setLookAt(target, 40.0F, 40.0F);

        if (windup >= 0) {
            if (windup-- == 0) {
                mob.clearTelegraph();
                shoot(target, skill);
                mob.startCooldown("needle", skill.cooldownTicks());
            }
            return;
        }
        double distance = mob.distanceTo(target);
        if (mob.ready("needle") && !mob.isBusy() && distance <= skill.range() && mob.hasLineOfSight(target)) {
            windup = Math.max(1, skill.windupTicks());
            mob.setBusy(windup + 4);
            mob.playAction("skill_needle");
            mob.setTelegraph(AmemaruMob.TELEGRAPH_WINDUP, windup, mob.getYRot(), 0.0F);
            mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.HOSTILE, 0.8F, 1.6F);
        }
    }

    private void shoot(LivingEntity target, EnemyDef.Skill skill) {
        Vec3 from = mob.position().add(0.0, mob.getBbHeight() * 0.4, 0.0);
        // 少しだけ動きを読む
        Vec3 lead = target.getDeltaMovement().multiply(4.0, 0.0, 4.0);
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0).add(lead);
        CrystalNeedle.shoot(mob, from, to.subtract(from), skill.param("speed", 0.9), skill.power());
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_CLUSTER_HIT,
                SoundSource.HOSTILE, 1.0F, 1.4F);
    }

    private void wanderAroundHome() {
        if (--wanderTimer <= 0 || mob.position().distanceToSqr(wander) < 1.0) {
            wanderTimer = 60 + mob.getRandom().nextInt(60);
            BlockPos home = mob.home();
            wander = new Vec3(home.getX() + 0.5 + (mob.getRandom().nextDouble() - 0.5) * 10.0,
                    home.getY() + 1.5 + mob.getRandom().nextDouble() * 3.0,
                    home.getZ() + 0.5 + (mob.getRandom().nextDouble() - 0.5) * 10.0);
        }
        mob.getMoveControl().setWantedPosition(wander.x, wander.y, wander.z, 0.5);
    }
}
