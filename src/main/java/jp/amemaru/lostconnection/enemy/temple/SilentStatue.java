package jp.amemaru.lostconnection.enemy.temple;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Map;

/**
 * 沈黙の石像（4階の雑魚。Phase 0-A 9章「近づくと動き出す」）。顔の削れた、白い石の神殿の像。
 *
 * <ul>
 *   <li>眠っている間は、ただの像（動かず、気づかない。叩くと硬い：受けるダメージ {@code behavior.dormant_taken}）。
 *       プレイヤーが {@code behavior.wake_range} まで近づく・叩くと、石がきしんで目覚める（1秒）【補完】</li>
 *   <li>目覚めると、ゆっくり追ってきて、重い石の拳で叩く（前）。ときどき両腕を振り上げて、まわりを叩きつける（円）</li>
 *   <li>石の体：受けるダメージ {@code behavior.stone_taken}（ふつうより少し硬い）</li>
 *   <li>しばらく誰もいないと、元の場所へ戻って、また像になる</li>
 *   <li>門の大石像（名のある魔物）：大きく、叩きつけの範囲が広い</li>
 * </ul>
 */
public class SilentStatue extends AmemaruMob {
    private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(SilentStatue.class,
            EntityDataSerializers.BOOLEAN);
    /** 目覚める動きの残り（tick）。 */
    private int waking;
    /** 誰もいなくなってからの tick。 */
    private int idle;

    public SilentStatue(EntityType<? extends SilentStatue> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_DORMANT, true);
    }

    @Override
    public String assetName() {
        return "silent_statue";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_wake", "skill_quake"};
    }

    /** 眠っている（像のまま）か。クライアントでも読める（像の姿勢）。 */
    public boolean dormant() {
        return entityData.get(DATA_DORMANT);
    }

    private void setDormant(boolean value) {
        if (dormant() != value) {
            entityData.set(DATA_DORMANT, value);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new QuakeGoal());
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "fist", "attack", TELEGRAPH_WINDUP, 0.9, SoundEvents.IRON_GOLEM_ATTACK) {
            @Override
            public boolean canUse() {
                return !dormant() && waking <= 0 && super.canUse();
            }
        });
    }

    @Override
    public java.util.Map<String, Boolean> boneToggles() {
        // 眠っている間はほこりと苔、目覚めると胸のひびが光る
        return Map.of("dust", dormant(), "glow", !dormant());
    }

    @Nullable
    @Override
    protected ServerPlayer findAggroTarget(ServerLevel level) {
        if (dormant()) {
            EnemyDef def = def();
            double wake = def == null ? 5.0 : def.behavior("wake_range", 5.0);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(wake))) {
                if (player.isAlive() && !player.isSpectator() && !player.isCreative() && player.distanceTo(this) <= wake
                        && hasLineOfSight(player)) {
                    wake(level);
                    return player;
                }
            }
            return null;
        }
        return super.findAggroTarget(level);
    }

    @Override
    protected void onAggro(ServerPlayer player, boolean hurt) {
        if (dormant() && level() instanceof ServerLevel level) {
            wake(level);
        }
    }

    /** 目覚める：石がきしみ、ほこりが落ちる。 */
    private void wake(ServerLevel level) {
        if (!dormant()) {
            return;
        }
        setDormant(false);
        waking = 20;
        setBusy(waking);
        idle = 0;
        playAction("skill_wake");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 1.6F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), getX(), getY() + getBbHeight() * 0.7,
                getZ(), 30, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.05);
    }

    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        EnemyDef def = def();
        if (def == null) {
            return 1.0;
        }
        return dormant() ? def.behavior("dormant_taken", 0.3) : def.behavior("stone_taken", 0.85);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        if (waking > 0) {
            waking--;
            getNavigation().stop();
            return;
        }
        if (!dormant()) {
            if (getTarget() == null) {
                idle++;
                BlockPos home = home();
                if (idle > 200 && home != null && home.closerToCenterThan(position(), 1.5)) {
                    // 元の場所で、また像になる
                    setDormant(true);
                    getNavigation().stop();
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 1.2F, 0.5F);
                } else if (idle > 60 && home != null && idle % 40 == 0) {
                    getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
                }
            } else {
                idle = 0;
            }
        } else {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        }
    }

    /** 両腕を振り上げて、まわりを叩きつける（円の予兆）。 */
    private final class QuakeGoal extends Goal {
        private int windup = -1;

        QuakeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private EnemyDef.Skill skill() {
            EnemyDef def = def();
            return def == null ? new EnemyDef.Skill(1.6, 1.4, 9.0, 3.5, Map.of()) : def.skill("quake", 1.6, 1.4, 9.0, 3.5);
        }

        @Override
        public boolean canUse() {
            if (dormant() || waking > 0 || isBusy() || !ready("quake") || getTarget() == null) {
                return false;
            }
            return distanceTo(getTarget()) <= skill().range() + 0.5;
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
            EnemyDef.Skill skill = skill();
            windup = Math.max(1, skill.windupTicks());
            setBusy(windup + 10);
            getNavigation().stop();
            playAction("skill_quake");
            setTelegraph(TELEGRAPH_CIRCLE, windup, getYRot(), (float) skill.range());
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.4F, 0.5F);
        }

        @Override
        public void stop() {
            windup = -1;
        }

        @Override
        public void tick() {
            getNavigation().stop();
            if (windup-- > 0) {
                return;
            }
            clearTelegraph();
            EnemyDef.Skill skill = skill();
            if (level() instanceof ServerLevel level) {
                double range = skill.range() + 0.5;
                for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(blockPosition()).inflate(range, 2.0, range))) {
                    if (player.distanceTo(SilentStatue.this) <= range + player.getBbWidth() / 2.0
                            && EnemyCombat.strike(SilentStatue.this, player, skill.power(), null)) {
                        player.push(0.0, 0.45, 0.0);
                        player.hurtMarked = true;
                    }
                }
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), getX(), getY() + 0.2,
                        getZ(), 50, range * 0.5, 0.1, range * 0.5, 0.15);
                level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0F, 0.7F);
            }
            startCooldown("quake", skill.cooldownTicks());
            windup = -1;
        }
    }

    @Override
    public boolean isPushable() {
        return !dormant() && super.isPushable();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return dormant() ? null : SoundEvents.STONE_HIT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.STONE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STONE_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.STONE_STEP, 0.8F, 0.5F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return target instanceof Player && super.doHurtTarget(target);
    }
}
