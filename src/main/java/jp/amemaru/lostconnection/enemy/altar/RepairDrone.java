package jp.amemaru.lostconnection.enemy.altar;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import jp.amemaru.lostconnection.enemy.boss.AncientGuardian;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Map;

/**
 * 修復ドローン（5階の雑魚。Phase 0-A 9章「守護者を回復するので最優先」）。青銅の小さな球に、4枚の羽根と、翠に光る修理の腕。
 *
 * <ul>
 *   <li>修理（repair）：近く（{@code skills.repair.range}）のいちばん傷ついた祭壇の機械（監視眼・防衛兵・ドローン・古代守護者）へ
 *       翠の光の糸を伸ばし、{@code skills.repair.windup} 秒のあと、最大HPの {@code skills.repair.power} を直す。古代守護者は
 *       HPが大きいので、その {@code behavior.boss_heal} 倍。糸を伸ばしている間に大きな傷（最大HPの {@code behavior.interrupt}）を
 *       受けると止まる</li>
 *   <li>人が近づくと離れる。宙に浮く。体はとてももろい。弱い電撃（zap。まわりの円）</li>
 * </ul>
 */
public class RepairDrone extends AmemaruMob {
    private static final DustParticleOptions JADE = new DustParticleOptions(new Vector3f(0.35F, 1.0F, 0.7F), 1.0F);
    private int repairing = -1;
    @Nullable
    private AmemaruMob patient;
    private double hurtWhileRepairing;

    public RepairDrone(EntityType<? extends RepairDrone> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createDroneAttributes() {
        return createAttributes().add(Attributes.FLYING_SPEED, 0.5);
    }

    @Override
    public String assetName() {
        return "repair_drone";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_repair"};
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new RepairGoal());
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "zap", "attack", TELEGRAPH_CIRCLE, 1.0, SoundEvents.BEE_STING));
    }

    private EnemyDef.Skill repair() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(0.08, 1.5, 6.0, 12.0, Map.of()) : def.skill("repair", 0.08, 1.5, 6.0, 12.0);
    }

    /** 直している最中か。 */
    public boolean repairing() {
        return repairing >= 0;
    }

    @Override
    public void onDamagedBy(@Nullable net.minecraft.world.entity.Entity attacker, double amount) {
        super.onDamagedBy(attacker, amount);
        if (repairing >= 0) {
            hurtWhileRepairing += amount;
            EnemyDef def = def();
            if (hurtWhileRepairing >= getEnemyMaxHp() * (def == null ? 0.2 : def.behavior("interrupt", 0.2)) && level() instanceof ServerLevel level) {
                repairing = -1;
                patient = null;
                clearTelegraph();
                clearBusy();
                startCooldown("repair", repair().cooldownTicks() / 2);
                level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.4, getZ(), 10, 0.2, 0.2, 0.2, 0.02);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0F, 1.4F);
            }
        }
    }

    /** いちばん傷ついた、近くの祭壇の機械（なければ null）。 */
    @Nullable
    private AmemaruMob findPatient(ServerLevel level, double range) {
        AmemaruMob best = null;
        double worst = 0.999;
        for (AmemaruMob mob : level.getEntitiesOfClass(AmemaruMob.class, getBoundingBox().inflate(range))) {
            if (mob == this || !mob.isAlive() || mob.distanceTo(this) > range) {
                continue;
            }
            if (!(mob instanceof Watcher || mob instanceof Sentinel || mob instanceof RepairDrone || mob instanceof AncientGuardian)) {
                continue;
            }
            if (mob instanceof AncientGuardian guardian && !guardian.repairable()) {
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

    /** 直し終えた（自動テストからも呼べる）。直した量を返す。 */
    public double finishRepair(ServerLevel level, @Nullable AmemaruMob target) {
        if (target == null || !target.isAlive()) {
            return 0.0;
        }
        EnemyDef def = def();
        double share = target instanceof AncientGuardian ? (def == null ? 0.1 : def.behavior("boss_heal", 0.1)) : 1.0;
        double amount = target.getEnemyMaxHp() * repair().power() * share;
        target.setEnemyHp(target.getEnemyHp() + amount);
        level.sendParticles(JADE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 16, 0.5, 0.6, 0.5, 0.0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_USE, SoundSource.HOSTILE, 0.5F, 1.8F);
        return amount;
    }

    /** 自動テスト用：すぐに近くの機械を直す。 */
    public double testRepair() {
        return level() instanceof ServerLevel level ? finishRepair(level, findPatient(level, repair().range())) : 0.0;
    }

    /** 修理：糸を伸ばして直す。人が近いと離れる。 */
    private final class RepairGoal extends Goal {
        private int drift;

        RepairGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return level() instanceof ServerLevel;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            repairing = -1;
            patient = null;
            clearTelegraph();
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel level)) {
                return;
            }
            EnemyDef.Skill skill = repair();
            if (repairing >= 0) {
                getNavigation().stop();
                if (patient == null || !patient.isAlive() || patient.distanceTo(RepairDrone.this) > skill.range() + 4.0) {
                    stop();
                    return;
                }
                getLookControl().setLookAt(patient, 30.0F, 30.0F);
                if (repairing % 3 == 0) {
                    Vec3 from = position().add(0.0, 0.3, 0.0);
                    Vec3 to = patient.position().add(0.0, patient.getBbHeight() * 0.6, 0.0);
                    Vec3 step = to.subtract(from);
                    int n = (int) Math.max(4, step.length() * 2);
                    for (int i = 0; i <= n; i++) {
                        Vec3 at = from.add(step.scale(i / (double) n));
                        level.sendParticles(JADE, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
                    }
                }
                if (--repairing > 0) {
                    return;
                }
                repairing = -1;
                clearTelegraph();
                finishRepair(level, patient);
                patient = null;
                startCooldown("repair", skill.cooldownTicks());
                return;
            }
            // 人が近いと離れる
            LivingEntity threat = getTarget();
            if (threat != null && threat.distanceTo(RepairDrone.this) < 4.0) {
                Vec3 away = position().subtract(threat.position()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0E-4) {
                    Vec3 to = position().add(away.normalize().scale(6.0)).add(0.0, 1.5, 0.0);
                    getNavigation().moveTo(to.x, to.y, to.z, 1.2);
                }
            }
            if (ready("repair") && !isBusy()) {
                AmemaruMob found = findPatient(level, skill.range());
                if (found != null) {
                    patient = found;
                    repairing = Math.max(4, skill.windupTicks());
                    hurtWhileRepairing = 0.0;
                    setBusy(repairing + 4);
                    playAction("skill_repair");
                    setTelegraph(TELEGRAPH_WINDUP, repairing, getYRot(), 0.0F);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.6F, 2.0F);
                    return;
                }
            }
            if (--drift <= 0 && threat == null) {
                drift = 60 + random.nextInt(60);
                Vec3 home = net.minecraft.world.phys.Vec3.atCenterOf(home());
                getNavigation().moveTo(home.x + random.nextInt(9) - 4, home.y + 2 + random.nextInt(3), home.z + random.nextInt(9) - 4, 0.8);
            }
        }
    }
}
