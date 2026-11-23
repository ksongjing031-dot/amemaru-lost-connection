package jp.amemaru.lostconnection.enemy.forest;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 葉影ギツネ（2階の雑魚。Phase 0-A 9章「素早く、背後を狙う」）。2〜3匹の群れで出る。
 *
 * <ul>
 *   <li>相手の背中へ回り込み（相手が向きを変えると回り直す）、背後から噛みつく。背後からの噛みつきは1.5倍【補完】</li>
 *   <li>噛んだら、すぐに5〜7ブロック離れ、また回り込む（3秒で背中へ回れなければ、前から噛む）</li>
 * </ul>
 */
public class HakageKitsune extends AmemaruMob implements jp.amemaru.lostconnection.enemy.Patroller {
    /**
     * 歩く輪（2階の白い木立の「帰り道を忘れた獣」。Phase 0-A 9章のロストの侵食：同じ場所をぐるぐる歩く）。湧く所の
     * {@code route} があるときだけ。なければ、ふつうにうろつく。
     */
    private final java.util.List<net.minecraft.core.BlockPos> loop = new java.util.ArrayList<>();
    public HakageKitsune(EntityType<? extends HakageKitsune> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "hakage_kitsune";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FlankGoal(this));
        goalSelector.addGoal(4, new jp.amemaru.lostconnection.enemy.ai.PatrolGoal(this, () -> loop, 0.7));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override
            public boolean canUse() {
                return loop.isEmpty() && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public void setPatrol(java.util.List<net.minecraft.core.BlockPos> points) {
        loop.clear();
        loop.addAll(points);
    }

    /** 歩く輪（自動テスト用）。 */
    public java.util.List<net.minecraft.core.BlockPos> loop() {
        return loop;
    }

    /** 相手の背中の側にいるか（相手の向きから見て、真後ろ±60度ほど）。 */
    public static boolean behind(LivingEntity target, Vec3 from) {
        Vec3 look = Vec3.directionFromRotation(0.0F, target.getYRot());
        Vec3 to = from.subtract(target.position()).multiply(1.0, 0.0, 1.0);
        if (to.lengthSqr() < 1.0E-4) {
            return false;
        }
        return look.dot(to.normalize()) < -0.45;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FOX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.FOX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    /** 回り込む → 噛む → 離れる、をくり返す。 */
    static class FlankGoal extends Goal {
        private enum Step { CIRCLE, WINDUP, RETREAT }

        private final HakageKitsune fox;
        private Step step = Step.CIRCLE;
        private int timer;
        private int repath;
        private float lockedYaw;

        FlankGoal(HakageKitsune fox) {
            this.fox = fox;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = fox.getTarget();
            return target != null && target.isAlive() && !fox.isReturning() && fox.def() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return step == Step.WINDUP || canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            step = Step.CIRCLE;
            timer = 0;
            repath = 0;
        }

        @Override
        public void stop() {
            if (step == Step.WINDUP) {
                fox.clearTelegraph();
            }
            step = Step.CIRCLE;
            fox.getNavigation().stop();
        }

        @Override
        public void tick() {
            EnemyDef def = fox.def();
            LivingEntity target = fox.getTarget();
            if (def == null) {
                return;
            }
            EnemyDef.Skill bite = def.skill("bite", 1.0, 0.25, 1.4, 1.8);
            switch (step) {
                case CIRCLE -> {
                    if (target == null) {
                        return;
                    }
                    timer++;
                    fox.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    boolean back = behind(target, fox.position());
                    double distance = fox.distanceTo(target) - target.getBbWidth() / 2.0;
                    double patience = def.behavior("flank_seconds", 3.0) * 20;
                    if (distance <= bite.range() && fox.ready("bite") && (back || timer > patience)) {
                        fox.getNavigation().stop();
                        fox.faceTowards(target.position());
                        lockedYaw = fox.getYRot();
                        step = Step.WINDUP;
                        timer = Math.max(1, bite.windupTicks());
                        fox.playAction("attack");
                        fox.setTelegraph(TELEGRAPH_WINDUP, timer, lockedYaw, (float) bite.range());
                        return;
                    }
                    if (--repath <= 0) {
                        repath = 6;
                        // 背中の後ろ2.5ブロック（もう背中の側なら、まっすぐ）
                        Vec3 look = Vec3.directionFromRotation(0.0F, target.getYRot());
                        Vec3 point = back ? target.position() : target.position().subtract(look.scale(2.5));
                        if (!back && fox.position().distanceTo(point) < 1.2) {
                            point = target.position();
                        }
                        fox.getNavigation().moveTo(point.x, point.y, point.z, def.behavior("flank_speed", 1.5));
                    }
                }
                case WINDUP -> {
                    fox.setYRot(lockedYaw);
                    fox.setYBodyRot(lockedYaw);
                    fox.setYHeadRot(lockedYaw);
                    fox.getNavigation().stop();
                    if (--timer > 0) {
                        return;
                    }
                    fox.clearTelegraph();
                    if (target instanceof ServerPlayer player && fox.distanceTo(player) - player.getBbWidth() / 2.0 <= bite.range() + 0.6
                            && fox.inFront(player.position(), 60.0)) {
                        boolean back = behind(player, fox.position());
                        double power = bite.power() * (back ? def.behavior("back_multiplier", 1.5) : 1.0);
                        EnemyCombat.strike(fox, player, power, null);
                        if (back && fox.level() instanceof ServerLevel level) {
                            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                        }
                    }
                    fox.level().playSound(null, fox.getX(), fox.getY(), fox.getZ(), SoundEvents.FOX_BITE, SoundSource.HOSTILE, 1.0F, 1.1F);
                    fox.startCooldown("bite", bite.cooldownTicks());
                    step = Step.RETREAT;
                    timer = (int) Math.round(def.behavior("retreat_seconds", 1.5) * 20);
                    repath = 0;
                }
                case RETREAT -> {
                    if (target == null || --timer <= 0) {
                        step = Step.CIRCLE;
                        timer = 0;
                        return;
                    }
                    if (--repath <= 0) {
                        repath = 8;
                        Vec3 away = fox.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
                        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                        double dist = 5.0 + fox.getRandom().nextDouble() * 2.0;
                        Vec3 to = target.position().add(away.yRot((float) ((fox.getRandom().nextDouble() - 0.5) * 1.2)).scale(dist));
                        fox.getNavigation().moveTo(to.x, to.y, to.z, def.behavior("flank_speed", 1.5));
                    }
                }
            }
        }
    }
}
