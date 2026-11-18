package jp.amemaru.lostconnection.enemy;

import jp.amemaru.lostconnection.enemy.ai.ChargeGoal;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 欠け牙の狼（1階の雑魚。Phase 0-A 9章）：体の一部が白く欠けた狼。ロストを初めて見せる敵。
 *
 * <p>近いと噛みつき、少し離れると突進する。突進の前は、地面に大きな線の予兆を出して止まる（線から外れれば避けられる）。
 * 欠けた部分からは、白い光の粒が時々こぼれる（ロスト状態を与える仕組みはPhase 8）。</p>
 */
public class KakekibaWolf extends AmemaruMob {
    public KakekibaWolf(EntityType<? extends KakekibaWolf> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "kakekiba_wolf";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ChargeGoal(this, SoundEvents.WOLF_HOWL));
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "bite", "attack", TELEGRAPH_WINDUP, 1.35, SoundEvents.WOLF_GROWL));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_charge"};
    }

    @Override
    public void tick() {
        super.tick();
        // ロスト：欠けた部分から、白い光の粒がこぼれる
        if (level().isClientSide() && isAlive() && random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.WHITE_ASH, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0.0, 0.02, 0.0);
            if (random.nextInt(6) == 0) {
                level().addParticle(ParticleTypes.END_ROD, getRandomX(0.4), getY() + getBbHeight() * 0.7, getRandomZ(0.4),
                        0.0, 0.01, 0.0);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOLF_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.8F + (random.nextFloat() - 0.5F) * 0.1F;
    }
}
