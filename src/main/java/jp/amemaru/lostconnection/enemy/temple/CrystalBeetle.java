package jp.amemaru.lostconnection.enemy.temple;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.ChargeGoal;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 結晶甲虫（4階の雑魚。Phase 0-A 9章「硬いが魔法に弱い」）。背中に青白い結晶の殻を背負った、大きな甲虫。
 *
 * <ul>
 *   <li>結晶の殻：魔力でないダメージ（剣・矢・拳と、攻撃で決まるスキル）は {@code behavior.physical_taken}、魔力のダメージは
 *       {@code behavior.magic_taken}（11月24日。スキルの効果が魔力で決まるかで分ける）</li>
 *   <li>噛みつき（前）と、殻を丸めた体当たり（短い線の予兆）</li>
 *   <li>魔法で殻にひびが入ると、殻のかけらが散る（当たった印）</li>
 * </ul>
 */
public class CrystalBeetle extends AmemaruMob {
    public CrystalBeetle(EntityType<? extends CrystalBeetle> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "crystal_beetle";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_charge"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ChargeGoal(this, SoundEvents.SHULKER_SHOOT));
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "bite", "attack", TELEGRAPH_WINDUP, 1.0, SoundEvents.SPIDER_HURT));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** 殻：魔力でないダメージは少し、魔力のダメージは多く通る。 */
    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker, boolean magic) {
        EnemyDef def = def();
        double physical = def == null ? 0.35 : def.behavior("physical_taken", 0.35);
        double magical = def == null ? 1.6 : def.behavior("magic_taken", 1.6);
        if (magic && level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.8, getZ(), 6, 0.4, 0.2, 0.4, 0.05);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 0.8F, 1.2F);
        }
        return magic ? magical : physical;
    }

    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        return damageTakenMultiplier(attacker, false);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SILVERFISH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_BLOCK_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SPIDER_STEP, 0.5F, 0.8F);
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }
}
