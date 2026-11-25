package jp.amemaru.lostconnection.enemy.iceforest;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.KakekibaWolf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 氷角鹿（7階の雑魚。Phase 0-A 9章の7階）：氷の角を持つ鹿。近いと角で突き（bite）、少し離れると頭を下げて突進する（charge。
 * 1階の欠け牙の狼と同じ動き）。角に当たると凍える。名のある魔物「白角」は大きく、突進が速い【補完】。
 */
public class IceStag extends KakekibaWolf {
    public IceStag(EntityType<? extends IceStag> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "ice_stag";
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        EnemyDef def = def();
        int stacks = def == null ? 1 : (int) Math.round(def.behavior("chill_stacks", 1.0));
        double seconds = def == null ? 4.0 : def.behavior("chill_seconds", 4.0);
        PlayerColdService.chill(player, stacks, (int) Math.round(seconds * 20));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive() && random.nextInt(5) == 0) {
            level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.6), getY() + getBbHeight() * 0.95, getRandomZ(0.6), 0.0, -0.02, 0.0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GOAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GOAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GOAT_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F + (random.nextFloat() - 0.5F) * 0.1F;
    }
}
