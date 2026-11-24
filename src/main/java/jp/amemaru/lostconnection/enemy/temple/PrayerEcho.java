package jp.amemaru.lostconnection.enemy.temple;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;

/**
 * 祈り手の残響（4階の雑魚。Phase 0-A 9章「祈りの歌で仲間を回復させるので先に倒す」）。神殿で祈っていた人の、
 * 声だけが残った淡い姿。白い衣の上半身が浮き、下は霧になって消えている。
 *
 * <ul>
 *   <li>祈りの歌：少し離れた所で止まり、{@code skills.hymn.windup} 秒歌うと、まわり（{@code skills.hymn.range}）の神殿の敵の
 *       HPを {@code skills.hymn.power}（最大HPの割合）回復する。歌っている間は、足もとに金の輪（予兆）。
 *       歌の間に大きな傷（最大HPの {@code behavior.interrupt}）を受けると、歌が止まる【補完】</li>
 *   <li>嘆きの声：近くの相手に、まわりへ広がる声（円）</li>
 *   <li>宙に浮いて動く（落ちない）。体は弱い</li>
 *   <li>祈りの導き手（名のある魔物）：歌が速く、よく治す</li>
 * </ul>
 */
public class PrayerEcho extends AmemaruMob {
    /** 歌っている残り（tick。歌っていなければ -1）。 */
    private int singing = -1;
    /** 歌の間に受けた傷。 */
    private double hurtWhileSinging;

    public PrayerEcho(EntityType<? extends PrayerEcho> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createEchoAttributes() {
        return createAttributes().add(Attributes.FLYING_SPEED, 0.4);
    }

    @Override
    public String assetName() {
        return "prayer_echo";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_hymn"};
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
        goalSelector.addGoal(1, new HymnGoal());
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "wail", "attack", TELEGRAPH_CIRCLE, 0.9, SoundEvents.ALLAY_HURT));
    }

    private EnemyDef.Skill hymn() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(0.12, 3.0, 12.0, 9.0, Map.of()) : def.skill("hymn", 0.12, 3.0, 12.0, 9.0);
    }

    /** 歌っているか（自動テスト・クライアントの演出）。 */
    public boolean singing() {
        return singing >= 0;
    }

    @Override
    public void onDamagedBy(@javax.annotation.Nullable net.minecraft.world.entity.Entity attacker, double amount) {
        super.onDamagedBy(attacker, amount);
        if (singing >= 0) {
            hurtWhileSinging += amount;
            EnemyDef def = def();
            double limit = getEnemyMaxHp() * (def == null ? 0.12 : def.behavior("interrupt", 0.12));
            if (hurtWhileSinging >= limit && level() instanceof ServerLevel level) {
                // 歌が止まる
                singing = -1;
                clearTelegraph();
                clearBusy();
                startCooldown("hymn", hymn().cooldownTicks() / 2);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_HURT, SoundSource.HOSTILE, 1.4F, 0.6F);
                level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    /** 歌い終えた：まわりの神殿の敵を治す。自動テストからも呼べる（治した数を返す）。 */
    public int finishHymn(ServerLevel level) {
        EnemyDef.Skill skill = hymn();
        int healed = 0;
        for (AmemaruMob ally : level.getEntitiesOfClass(AmemaruMob.class, getBoundingBox().inflate(skill.range()))) {
            if (!ally.isAlive() || ally.distanceTo(this) > skill.range()) {
                continue;
            }
            boolean boss = ally instanceof jp.amemaru.lostconnection.enemy.boss.Cantare;
            if (!(boss || ally instanceof SilentStatue || ally instanceof PrayerEcho || ally instanceof CrystalBeetle || ally instanceof BrokenBeast)) {
                continue;
            }
            if (ally.getEnemyHp() < ally.getEnemyMaxHp()) {
                // 4階のボス（カンターレ）も治す。ただしHPが大きいので、治す割合は behavior.boss_heal 倍【補完】
                double share = boss ? (def() == null ? 0.15 : def().behavior("boss_heal", 0.15)) : 1.0;
                ally.setEnemyHp(ally.getEnemyHp() + ally.getEnemyMaxHp() * skill.power() * share);
                healed++;
                level.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + ally.getBbHeight() + 0.3, ally.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
            }
            level.sendParticles(ParticleTypes.END_ROD, ally.getX(), ally.getY() + ally.getBbHeight() * 0.5, ally.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.6F, 1.3F);
        return healed;
    }

    /** 自動テスト用：すぐに歌い終える。 */
    public int testHymn() {
        return level() instanceof ServerLevel level ? finishHymn(level) : 0;
    }

    /** 祈りの歌（止まって歌い、終わるとまわりの仲間を治す）。 */
    private final class HymnGoal extends Goal {
        HymnGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (isBusy() || !ready("hymn") || getTarget() == null || !(level() instanceof ServerLevel level)) {
                return false;
            }
            EnemyDef.Skill skill = hymn();
            // まわりに、傷ついた神殿の仲間がいるときだけ歌う（自分も入る）
            for (AmemaruMob ally : level.getEntitiesOfClass(AmemaruMob.class, getBoundingBox().inflate(skill.range()))) {
                if (ally.isAlive() && ally.getEnemyHp() < ally.getEnemyMaxHp() * 0.85
                        && (ally instanceof SilentStatue || ally instanceof PrayerEcho || ally instanceof CrystalBeetle || ally instanceof BrokenBeast)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return singing >= 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            EnemyDef.Skill skill = hymn();
            singing = Math.max(10, skill.windupTicks());
            hurtWhileSinging = 0.0;
            setBusy(singing + 4);
            getNavigation().stop();
            playAction("skill_hymn");
            setTelegraph(TELEGRAPH_ALERT, singing, getYRot(), (float) skill.range());
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_AMBIENT_WITH_ITEM, SoundSource.HOSTILE, 1.6F, 0.7F);
        }

        @Override
        public void stop() {
            if (singing >= 0) {
                clearTelegraph();
            }
            singing = -1;
        }

        @Override
        public void tick() {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().scale(0.6));
            if (!(level() instanceof ServerLevel level)) {
                return;
            }
            if (singing % 8 == 0) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 1.0F,
                        0.8F + random.nextFloat() * 0.6F);
                level.sendParticles(ParticleTypes.NOTE, getX(), getY() + getBbHeight() + 0.3, getZ(), 2, 0.4, 0.2, 0.4, random.nextDouble());
            }
            if (singing-- > 0) {
                return;
            }
            clearTelegraph();
            finishHymn(level);
            startCooldown("hymn", hymn().cooldownTicks());
            singing = -1;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && isAlive() && tickCount % 10 == 0) {
            // 下半身は霧：足もとに薄い霧
            level.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 0.2, getZ(), 3, 0.3, 0.1, 0.3, 0.0);
        }
        // 浮いている高さを保つ（相手の目の高さあたり）
        LivingEntity target = getTarget();
        if (!level().isClientSide() && target != null && singing < 0) {
            double want = target.getEyeY() + 0.4;
            Vec3 m = getDeltaMovement();
            setDeltaMovement(m.x, m.y + Math.signum(want - getY()) * 0.02, m.z);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
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
        return 0.7F;
    }
}
