package jp.amemaru.lostconnection.enemy.iceforest;

import jp.amemaru.lostconnection.combat.LostService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.village.FrostBird;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 吹雪の精（7階の雑魚。Phase 0-A 9章の7階）：雪の衣をまとった、顔のない精。浮いて氷のつぶてを撃ち（needle）、狙った人のまわりに
 * 吹雪を起こす（6階の凍て鳥と同じ動き）。この吹雪に打たれると、ロスト状態「薄れ」（味方の名前が「？？？」に見える）になる
 * （Phase 0-A 13章「薄れ：主な原因は7階」）【補完】。名のある魔物「吹雪の大精」は大きく、吹雪が広い。
 */
public class BlizzardSpirit extends FrostBird {
    public BlizzardSpirit(EntityType<? extends BlizzardSpirit> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "blizzard_spirit";
    }

    @Override
    protected void onStormHit(ServerPlayer player) {
        EnemyDef def = def();
        double seconds = def == null ? 8.0 : def.behavior("fade_seconds", 8.0);
        LostService.inflict(player, ModEffects.LOST_FADE.get(), (int) Math.round(seconds * 20));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive() && random.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.WHITE_ASH, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0.0, -0.02, 0.0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SNOW_GOLEM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SNOW_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SNOW_GOLEM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + (random.nextFloat() - 0.5F) * 0.1F;
    }
}
