package jp.amemaru.lostconnection.enemy.castle;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 忘れられた侍女（9階の雑魚。Phase 0-A 9章の9階「忘れられた侍女」）：仕える相手の名前を忘れたまま、今も城の仕事を続ける侍女の残響。
 * 白い前掛けの上半身ははっきりしているが、顔は白い空白。手に銀の盆【補完】。
 *
 * <ul>
 *   <li>介抱（{@code skills.tend}）：まわり（{@code skills.tend.range}）の、傷ついた城の敵（近衛兵・騎兵・書架霊・猟犬）のそばへ行き、
 *       1秒ごとに最大HPの {@code skills.tend.power} を治す（最長 {@code skills.tend.windup} 秒）。介抱している間は、相手の足もとに
 *       金の輪。大きな傷（最大HPの {@code behavior.interrupt}）を受けると、介抱をやめて逃げる。<b>先に倒す</b>相手</li>
 *   <li>人が近づくと（{@code behavior.flee_range}）、離れる。追いつめられると、銀の盆で叩いて押し返す（{@code skills.tray}）</li>
 *   <li>倒れると、そばの人に、最後のことばが聞こえる（城の物語。一人一度）</li>
 * </ul>
 */
public class ForgottenMaid extends AmemaruMob {
    private static final EntityDataAccessor<Integer> DATA_PATIENT = SynchedEntityData.defineId(ForgottenMaid.class, EntityDataSerializers.INT);
    private static final Set<UUID> HEARD = new HashSet<>();
    /** 介抱の間に受けた傷。 */
    private double hurtWhileTending;
    /** 逃げる残り（tick）。 */
    private int fleeing;
    /** 治した回数（自動テスト用）。 */
    private int mended;
    /** 介抱する相手のそばへ向かっているか（向かう間は、人が近くても逃げない）。 */
    private boolean approaching;

    public ForgottenMaid(EntityType<? extends ForgottenMaid> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PATIENT, -1);
    }

    @Override
    public String assetName() {
        return "forgotten_maid";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_tend", "skill_tray", "skill_flee"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeGoal());
        goalSelector.addGoal(2, new TendGoal());
        goalSelector.addGoal(3, new EnemyMeleeGoal(this, "tray", "skill_tray", TELEGRAPH_CONE, 1.0, SoundEvents.ANVIL_LAND) {
            @Override
            public boolean canUse() {
                return getTarget() != null && distanceTo(getTarget()) < 3.0 && super.canUse();
            }
        });
        goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** 介抱している相手の番号（していなければ -1。クライアントでも読める）。 */
    public int patientId() {
        return entityData.get(DATA_PATIENT);
    }

    /** 介抱しているか。 */
    public boolean tending() {
        return patientId() >= 0;
    }

    /** 治せる相手か（9階の城の敵）。 */
    public static boolean castleAlly(AmemaruMob mob) {
        return mob instanceof FrozenGuard || mob instanceof IceCavalry || mob instanceof ShelfWraith || mob instanceof FrostHound;
    }

    private EnemyDef.Skill tend() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(0.05, 6.0, 8.0, 14.0, Map.of()) : def.skill("tend", 0.05, 6.0, 8.0, 14.0);
    }

    /** 1回治す（介抱の1秒ごと）。自動テストからも呼べる。治したら true。 */
    public boolean mendOnce(ServerLevel level, AmemaruMob patient) {
        if (!patient.isAlive() || patient.getEnemyHp() >= patient.getEnemyMaxHp()) {
            return false;
        }
        patient.setEnemyHp(patient.getEnemyHp() + patient.getEnemyMaxHp() * tend().power());
        mended++;
        level.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight() + 0.3, patient.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, patient.getX(), patient.getY() + patient.getBbHeight() * 0.5, patient.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 0.8F, 1.5F);
        return true;
    }

    /** 自動テスト用：治した回数。 */
    public int testMended() {
        return mended;
    }

    @Override
    public void onDamagedBy(@Nullable Entity attacker, double amount) {
        super.onDamagedBy(attacker, amount);
        EnemyDef def = def();
        if (tending()) {
            hurtWhileTending += amount;
            double limit = getEnemyMaxHp() * (def == null ? 0.15 : def.behavior("interrupt", 0.15));
            if (hurtWhileTending >= limit) {
                stopTending(true);
            }
        } else if (attacker instanceof ServerPlayer) {
            fleeing = Math.max(fleeing, 30);
        }
    }

    private void stopTending(boolean interrupted) {
        entityData.set(DATA_PATIENT, -1);
        hurtWhileTending = 0;
        if (interrupted) {
            fleeing = 50;
            playAction("skill_flee");
            if (level() instanceof ServerLevel level) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8F, 1.6F);
                level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            }
        }
        startCooldown("tend", tend().cooldownTicks());
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            ServerPlayer nearest = null;
            double best = 12.0 * 12.0;
            for (ServerPlayer player : level.players()) {
                double d = player.distanceToSqr(this);
                if (d < best && !HEARD.contains(player.getUUID())) {
                    nearest = player;
                    best = d;
                }
            }
            if (nearest != null) {
                HEARD.add(nearest.getUUID());
                nearest.displayClientMessage(Component.translatable("message.amemaru.forgotten_maid.last_words")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (isAlive() && random.nextInt(5) == 0) {
                level().addParticle(ParticleTypes.WHITE_ASH, getRandomX(0.5), getY() + getBbHeight() * 0.85, getRandomZ(0.5), 0.0, -0.01, 0.0);
            }
            // 介抱している相手の足もとに、金の輪
            if (tending() && level().getEntity(patientId()) instanceof AmemaruMob patient && tickCount % 3 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = (tickCount * 0.15) + Math.PI * 2.0 * i / 6.0;
                    level().addParticle(ParticleTypes.WAX_ON, patient.getX() + Math.cos(a) * 1.0, patient.getY() + 0.1,
                            patient.getZ() + Math.sin(a) * 1.0, 0.0, 0.01, 0.0);
                }
            }
            return;
        }
        if (fleeing > 0) {
            fleeing--;
        }
    }

    /** 人が近い・叩かれた：離れる（介抱している間は、近づかれただけでは離れない）。 */
    private final class FleeGoal extends Goal {
        private int repath;

        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Nullable
        private ServerPlayer near() {
            EnemyDef def = def();
            double range = def == null ? 5.0 : def.behavior("flee_range", 5.0);
            ServerPlayer best = null;
            double bestDistance = range * range;
            for (ServerPlayer player : level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range))) {
                double d = player.distanceToSqr(ForgottenMaid.this);
                if (player.isAlive() && !player.isSpectator() && !player.isCreative() && d < bestDistance) {
                    best = player;
                    bestDistance = d;
                }
            }
            return best;
        }

        @Override
        public boolean canUse() {
            if (isBusy() || def() == null) {
                return false;
            }
            return fleeing > 0 || (!tending() && !approaching && near() != null);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (--repath > 0) {
                return;
            }
            repath = 10;
            ServerPlayer player = near();
            Entity from = player != null ? player : getTarget();
            if (from == null) {
                return;
            }
            Vec3 away = position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            Vec3 to = position().add(away.yRot((float) ((random.nextDouble() - 0.5) * 0.8)).scale(6.0));
            getNavigation().moveTo(to.x, to.y, to.z, def() == null ? 1.2 : def().behavior("flee_speed", 1.3));
        }
    }

    /** 介抱：傷ついた仲間のそばへ行き、1秒ごとに治す。 */
    private final class TendGoal extends Goal {
        @Nullable
        private AmemaruMob patient;
        private int timer;
        private int repath;

        TendGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private AmemaruMob find() {
            EnemyDef.Skill skill = tend();
            AmemaruMob best = null;
            double worst = 0.95;
            for (AmemaruMob mob : level().getEntitiesOfClass(AmemaruMob.class, getBoundingBox().inflate(skill.range()))) {
                if (mob == ForgottenMaid.this || !mob.isAlive() || !castleAlly(mob) || mob.distanceTo(ForgottenMaid.this) > skill.range()) {
                    continue;
                }
                double ratio = mob.getEnemyHp() / mob.getEnemyMaxHp();
                if (ratio < worst) {
                    worst = ratio;
                    best = mob;
                }
            }
            return best;
        }

        @Override
        public boolean canUse() {
            if (isBusy() || fleeing > 0 || !ready("tend") || def() == null || isReturning()) {
                return false;
            }
            patient = find();
            return patient != null;
        }

        @Override
        public boolean canContinueToUse() {
            return patient != null && patient.isAlive() && fleeing <= 0 && timer > 0
                    && patient.getEnemyHp() < patient.getEnemyMaxHp();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            timer = (int) Math.round(tend().windup() * 20) + 100;   // 着くまでの猶予（5秒）＋介抱
            repath = 0;
            hurtWhileTending = 0;
            approaching = true;
        }

        @Override
        public void stop() {
            if (tending()) {
                stopTending(false);
            }
            approaching = false;
            patient = null;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (patient == null || !(level() instanceof ServerLevel level)) {
                return;
            }
            timer--;
            getLookControl().setLookAt(patient, 30.0F, 30.0F);
            double distance = distanceTo(patient) - patient.getBbWidth() / 2.0;
            if (distance > 1.8) {
                if (--repath <= 0) {
                    repath = 8;
                    getNavigation().moveTo(patient, 1.2);
                }
                return;
            }
            getNavigation().stop();
            if (!tending()) {
                entityData.set(DATA_PATIENT, patient.getId());
                timer = Math.min(timer, (int) Math.round(tend().windup() * 20));
                playAction("skill_tend");
            }
            if (timer % 20 == 0) {
                mendOnce(level, patient);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.8F + (random.nextFloat() - 0.5F) * 0.1F;
    }
}
