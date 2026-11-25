package jp.amemaru.lostconnection.enemy.village;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.temple.BrokenBeast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 霜の欠け熊（6階の大型の雑魚。Phase 0-A 9章の6階「霜の欠け熊（大型）」）：霜の毛並みの大きな熊。体のところどころが白く欠けて
 * いる（ロスト）。爪の薙ぎ払い（扇）と突進、仲間を奮い立たせる咆哮（4階の欠けた聖獣と同じ動き）。爪に当たると強く凍える
 * （behavior.chill_stacks 段）【補完】。名のある魔物「霜の欠け熊の主」（variant frost_bear_elder）は、もっと大きく、咆哮が2回。
 */
public class FrostBear extends BrokenBeast {
    public FrostBear(EntityType<? extends FrostBear> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "frost_bear";
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        EnemyDef def = def();
        int stacks = def == null ? 2 : (int) Math.round(def.behavior("chill_stacks", 2.0));
        double seconds = def == null ? 5.0 : def.behavior("chill_seconds", 5.0);
        PlayerColdService.chill(player, stacks, (int) Math.round(seconds * 20));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive() && random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.8), getY() + getBbHeight() * 0.8, getRandomZ(0.8), 0.0, -0.02, 0.0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.POLAR_BEAR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.POLAR_BEAR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.POLAR_BEAR_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.POLAR_BEAR_STEP, 0.6F, 0.8F);
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + (random.nextFloat() - 0.5F) * 0.1F;
    }
}
