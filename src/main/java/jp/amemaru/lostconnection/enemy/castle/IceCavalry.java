package jp.amemaru.lostconnection.enemy.castle;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.ChargeGoal;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 氷の騎兵（9階の雑魚。Phase 0-A 9章の9階「氷の騎兵」）：凍った馬に乗った、近衛の騎士。槍と馬鎧は氷、乗り手の兜の中は空白【補完】。
 *
 * <ul>
 *   <li>槍の突撃（{@code skills.charge}）：止まって地面に長い線を見せ、まっすぐ駆け抜ける（1階の欠け牙の狼と同じ突進）。
 *       当たると凍える。<b>線の途中で壁にぶつかると、馬が転んでよろめく</b>（{@code behavior.stagger_seconds} 秒動けず、
 *       受けるダメージ {@code behavior.stagger_taken} 倍）。突撃を壁ぎわへ誘うのが攻略【補完】</li>
 *   <li>蹄の踏み鳴らし（{@code skills.stomp}）：まわりにいる人へ、まわり全部の円</li>
 *   <li>霜蹄の旗手（名のある魔物。定義 {@code ice_cavalry_bearer}）：続けて {@code behavior.chain} 回突撃する。HPが半分を切ると
 *       旗を振って号令をかけ、練兵場の凍った兵を {@code behavior.call} 体目覚めさせる（「今も号令を待つ」）</li>
 * </ul>
 */
public class IceCavalry extends AmemaruMob {
    private int stagger;
    private int chained;
    private boolean called;
    /** 号令で目覚めさせた兵（自動テスト用。保存しない）。 */
    private final List<FrozenGuard> calledGuards = new ArrayList<>();

    public IceCavalry(EntityType<? extends IceCavalry> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "ice_cavalry";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_charge", "skill_stomp", "skill_stagger", "skill_rally"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ChargeGoal(this, SoundEvents.HORSE_ANGRY) {
            @Override
            public boolean canUse() {
                return stagger <= 0 && super.canUse();
            }
        });
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "stomp", "skill_stomp", TELEGRAPH_CIRCLE, 1.1, SoundEvents.HORSE_LAND) {
            @Override
            public boolean canUse() {
                return stagger <= 0 && super.canUse();
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** よろめいているか。 */
    public boolean staggered() {
        return stagger > 0;
    }

    @Override
    public int onChargeEnded(boolean hitWall) {
        EnemyDef def = def();
        if (!hitWall || def == null) {
            return 0;
        }
        // 壁にぶつかった：馬が転ぶ
        stagger = (int) Math.round(def.behavior("stagger_seconds", 3.0) * 20);
        chained = 0;
        setBusy(stagger);
        playAction("skill_stagger");
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.HORSE_DEATH, SoundSource.HOSTILE, 1.0F, 1.4F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.2F, 0.8F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), getX(),
                    getY() + 1.0, getZ(), 24, 0.8, 0.5, 0.8, 0.05);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2.0, getZ(), 10, 0.6, 0.3, 0.6, 0.05);
        }
        return stagger;
    }

    @Override
    public int chargeCooldown(int normal) {
        EnemyDef def = def();
        int chain = def == null ? 1 : (int) Math.round(def.behavior("chain", 1.0));
        if (stagger <= 0 && ++chained < chain) {
            return 16;      // 旗手：すぐに向きを変えて、もう一度突撃する
        }
        chained = 0;
        return normal;
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        EnemyDef def = def();
        PlayerColdService.chill(player, def == null ? 1 : (int) Math.round(def.behavior("chill_stacks", 1.0)),
                (int) Math.round((def == null ? 4.0 : def.behavior("chill_seconds", 4.0)) * 20));
    }

    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        EnemyDef def = def();
        return stagger > 0 && def != null ? def.behavior("stagger_taken", 1.5) : 1.0;
    }

    @Override
    public void onDamagedBy(@Nullable Entity attacker, double amount) {
        super.onDamagedBy(attacker, amount);
        EnemyDef def = def();
        int count = def == null ? 0 : (int) Math.round(def.behavior("call", 0.0));
        if (count > 0 && !called && getEnemyHp() <= getEnemyMaxHp() * 0.5 && level() instanceof ServerLevel level) {
            called = true;
            rally(level, count);
        }
    }

    /** 旗の号令：凍った兵を目覚めさせる。 */
    private void rally(ServerLevel level, int count) {
        playAction("skill_rally");
        setBusy(20);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.6F, 1.2F);
        ServerPlayer target = topThreat();
        for (int i = 0; i < count; i++) {
            FrozenGuard guard = ModEntities.FROZEN_GUARD.get().create(level);
            if (guard == null) {
                continue;
            }
            double a = Math.PI * 2.0 * i / count + random.nextDouble() * 0.5;
            Vec3 at = position().add(Math.cos(a) * 3.5, 0.0, Math.sin(a) * 3.5);
            guard.moveTo(at.x, getY(), at.z, (float) Math.toDegrees(Math.atan2(-(getX() - at.x), getZ() - at.z)), 0.0F);
            guard.setHome(BlockPos.containing(at));
            guard.setTransient(true);
            guard.wakeNow();
            guard.addTag("amemaru_lineage_woken");
            level.addFreshEntity(guard);
            calledGuards.add(guard);
            if (target != null) {
                guard.addThreat(target, 1.0);
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x, getY() + 1.0,
                    at.z, 20, 0.4, 0.6, 0.4, 0.05);
            level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, getY() + 1.0, at.z, 12, 0.4, 0.6, 0.4, 0.02);
        }
    }

    /** 自動テスト用：号令で目覚めさせた兵。 */
    public List<FrozenGuard> testCalled() {
        return List.copyOf(calledGuards);
    }

    @Override
    public Map<String, Boolean> boneToggles() {
        // 旗手だけ、背に青い旗
        net.minecraft.resources.ResourceLocation id = variantId();
        return Map.of("banner", id != null && id.getPath().endsWith("_bearer"));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (isAlive() && random.nextInt(4) == 0) {
                level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.8), getY() + 0.1, getRandomZ(0.8), 0.0, 0.01, 0.0);
            }
            return;
        }
        if (stagger > 0) {
            stagger--;
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
            if (stagger % 10 == 0 && level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2.4, getZ(), 4, 0.4, 0.1, 0.4, 0.02);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HORSE_BREATHE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HORSE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_HORSE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.HORSE_GALLOP, 0.35F, 0.9F);
    }

    @Override
    public float getVoicePitch() {
        return 0.7F + (random.nextFloat() - 0.5F) * 0.1F;
    }
}
