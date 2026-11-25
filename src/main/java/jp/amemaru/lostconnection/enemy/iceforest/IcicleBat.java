package jp.amemaru.lostconnection.enemy.iceforest;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.enemy.CrystalBee;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * つらら蝙蝠（7階の雑魚。Phase 0-A 9章の7階）：氷の木々の枝から舞い降りる蝙蝠。相手から少し離れた高さに浮き、翼を広げて
 * つららを撃つ（1階の結晶蜂と同じ動き。{@code needle}）。つららに当たると凍える【補完】。
 */
public class IcicleBat extends CrystalBee {
    public IcicleBat(EntityType<? extends IcicleBat> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "icicle_bat";
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        PlayerColdService.chill(player, 1, 60);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive() && random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.5), getY(), getRandomZ(0.5), 0.0, -0.05, 0.0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BAT_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.75F + (random.nextFloat() - 0.5F) * 0.1F;
    }
}
