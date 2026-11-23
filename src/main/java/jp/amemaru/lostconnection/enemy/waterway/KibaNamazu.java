package jp.amemaru.lostconnection.enemy.waterway;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 牙ナマズ（3階の雑魚。Phase 0-A 9章「水中から跳ねる」）。深い水路の底に潜む、牙の生えた大ナマズ。
 *
 * <ul>
 *   <li>水の底でじっと待ち、水辺に人が来ると、一直線の予兆のあとで跳ね上がって噛みつく</li>
 *   <li>跳んだあとは、陸の上で少しの間もがく（このときが攻めどき）。そのあと、すみかの水へ跳んで戻る</li>
 *   <li>水の中にいる間は、受けるダメージが半分（定義の {@code behavior.water_guard}）【補完】</li>
 * </ul>
 */
public class KibaNamazu extends AmemaruMob {
    private enum State { WAIT, WINDUP, LEAP, LANDED, BACK }

    private State state = State.WAIT;

    public KibaNamazu(EntityType<? extends KibaNamazu> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "kiba_namazu";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LeapGoal());
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_leap", "skill_flop"};
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected boolean usesLeash() {
        // 帰り道は自分で跳んで戻る
        return false;
    }

    /** 水の中では、受けるダメージが behavior.water_guard 倍。 */
    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        if (!isInWater()) {
            return 1.0;
        }
        EnemyDef def = def();
        return def == null ? 0.5 : def.behavior("water_guard", 0.5);
    }

    /** 自動テスト用：陸の上でもがいているか。 */
    public boolean landed() {
        return state == State.LANDED;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive() && isInWater() && random.nextInt(5) == 0) {
            level().addParticle(ParticleTypes.BUBBLE, getRandomX(0.6), getY() + getBbHeight() * 0.5, getRandomZ(0.6), 0.0, 0.05, 0.0);
        }
    }

    /** 放物線で、{@code to} の方へ跳ぶ（{@code ticks} ほどで着く）。 */
    private void jumpTo(Vec3 to, int ticks) {
        Vec3 d = to.subtract(position());
        double t = Math.max(6, ticks);
        double vy = 0.04 * t + d.y / t;
        // 水の中から跳ぶときは、水の抵抗のぶん強く
        double boost = isInWater() ? 1.35 : 1.0;
        setDeltaMovement(d.x / t * boost, Math.min(1.4, Math.max(0.45, vy * boost)), d.z / t * boost);
        hasImpulse = true;
    }

    /** 跳び上がって噛みつき、陸でもがき、水へ戻る。 */
    private class LeapGoal extends Goal {
        private int timer;
        private float yaw;
        private Vec3 landing = Vec3.ZERO;
        private final Set<UUID> bitten = new HashSet<>();

        LeapGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        private EnemyDef.Skill skill() {
            EnemyDef def = def();
            return def == null ? new EnemyDef.Skill(1.4, 0.9, 4.0, 9.0, java.util.Map.of()) : def.skill("leap", 1.4, 0.9, 4.0, 9.0);
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || def() == null || isBusy() || !ready("leap") || !isInWater()) {
                return false;
            }
            return distanceTo(target) <= skill().range();
        }

        @Override
        public boolean canContinueToUse() {
            return state != State.WAIT;
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
            state = State.WINDUP;
            getNavigation().stop();
            faceTowards(target.position());
            yaw = getYRot();
            landing = target.position();
            double length = landing.subtract(position()).multiply(1.0, 0.0, 1.0).length() + 1.0;
            timer = Math.max(1, skill.windupTicks());
            setBusy(timer + 80);
            playAction("skill_leap");
            setTelegraph(TELEGRAPH_LINE, timer, yaw, (float) length, (float) skill.param("width", 1.6));
            if (level() instanceof ServerLevel level) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.FISH_SWIM, SoundSource.HOSTILE, 1.6F, 0.6F);
                level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 0.5, getZ(), 16, 0.5, 0.3, 0.5, 0.05);
            }
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel level)) {
                return;
            }
            switch (state) {
                case WINDUP -> {
                    setYRot(yaw);
                    setYBodyRot(yaw);
                    if (timer % 4 == 0) {
                        level.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 1.0, getZ(), 6, 0.6, 0.2, 0.6, 0.02);
                    }
                    if (--timer <= 0) {
                        clearTelegraph();
                        bitten.clear();
                        jumpTo(landing, 14);
                        state = State.LEAP;
                        timer = 30;
                        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.4F, 0.8F);
                        level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1.0, getZ(), 30, 0.8, 0.4, 0.8, 0.2);
                    }
                }
                case LEAP -> {
                    EnemyDef.Skill skill = skill();
                    for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(0.8))) {
                        if (bitten.add(player.getUUID()) && EnemyCombat.strike(KibaNamazu.this, player, skill.power(), null)) {
                            level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.0F, 1.3F);
                        }
                    }
                    timer--;
                    if ((onGround() && !isInWater() && timer < 26) || timer <= 0) {
                        if (isInWater()) {
                            // 届かずに水へ落ちた：そのまま待つ
                            finish(skill);
                        } else {
                            state = State.LANDED;
                            EnemyDef def = def();
                            timer = (int) Math.round((def == null ? 2.5 : def.behavior("land_seconds", 2.5)) * 20);
                            playAction("skill_flop");
                            level.playSound(null, getX(), getY(), getZ(), SoundEvents.COD_FLOP, SoundSource.HOSTILE, 1.4F, 0.6F);
                        }
                    }
                }
                case LANDED -> {
                    getNavigation().stop();
                    if (timer % 10 == 0) {
                        level.playSound(null, getX(), getY(), getZ(), SoundEvents.COD_FLOP, SoundSource.HOSTILE, 1.0F, 0.6F);
                        setDeltaMovement(getDeltaMovement().add((random.nextDouble() - 0.5) * 0.1, 0.18, (random.nextDouble() - 0.5) * 0.1));
                    }
                    if (--timer <= 0) {
                        state = State.BACK;
                        timer = 50;
                        Vec3 home = Vec3.atBottomCenterOf(home());
                        faceTowards(home);
                        jumpTo(home, 16);
                    }
                }
                case BACK -> {
                    if (isInWater()) {
                        finish(skill());
                        return;
                    }
                    if (--timer <= 0) {
                        // 跳んでも戻れなかった：歩いて（はって）すみかへ
                        if (onGround()) {
                            BlockPos home = home();
                            getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
                        }
                        if (timer < -200 || position().distanceToSqr(Vec3.atBottomCenterOf(home())) < 2.0) {
                            finish(skill());
                        }
                    }
                }
                default -> {
                }
            }
        }

        private void finish(EnemyDef.Skill skill) {
            state = State.WAIT;
            clearBusy();
            startCooldown("leap", skill.cooldownTicks());
        }

        @Override
        public void stop() {
            if (state == State.WINDUP) {
                clearTelegraph();
            }
            if (state != State.WAIT) {
                state = State.WAIT;
                clearBusy();
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isInWater() ? SoundEvents.FISH_SWIM : SoundEvents.COD_FLOP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }
}
