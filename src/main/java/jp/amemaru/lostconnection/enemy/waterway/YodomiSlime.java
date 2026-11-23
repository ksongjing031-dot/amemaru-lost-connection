package jp.amemaru.lostconnection.enemy.waterway;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 澱みスライム（3階の雑魚。Phase 0-A 9章「止まった水が魔物になったもの。分裂する」）。流れの止まった水路の底にたまった、
 * 濁った水のかたまり。
 *
 * <ul>
 *   <li>体当たり（前）と、跳ねて押しつぶす（まわりの円）。当たると、ねばる水が足にからみつき、少しの間遅くなる【補完】</li>
 *   <li>倒すと、小さな澱みスライム（{@link #SMALL}）に分かれる（定義の {@code behavior.split} 匹。小さいものは分かれない）</li>
 * </ul>
 */
public class YodomiSlime extends AmemaruMob {
    /** 分かれたあとの小さなスライムの定義。 */
    public static final ResourceLocation SMALL = AmemaruMod.id("yodomi_slime_small");

    public YodomiSlime(EntityType<? extends YodomiSlime> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "yodomi_slime";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new EnemyMeleeGoal(this, "slam", "skill_slam", TELEGRAPH_CIRCLE, 0.9, SoundEvents.SLIME_BLOCK_FALL));
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "touch", "attack", TELEGRAPH_WINDUP, 1.0, SoundEvents.SLIME_ATTACK));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_slam"};
    }

    /** 当たると、ねばる水で遅くなる（押しつぶしは強く、体当たりは弱く）。 */
    @Override
    public void onSkillHit(String skill, ServerPlayer player) {
        EnemyDef def = def();
        boolean slam = "slam".equals(skill);
        double seconds = def == null ? 2.0 : def.behavior(slam ? "slow_seconds" : "touch_slow_seconds", slam ? 3.0 : 1.5);
        if (seconds <= 0.0) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) Math.round(seconds * 20), slam ? 1 : 0, false, true));
        if (slam) {
            player.displayClientMessage(Component.translatable("message.amemaru.enemy.sticky").withStyle(ChatFormatting.DARK_AQUA), true);
        }
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, player.getX(), player.getY() + 0.3, player.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
        }
    }

    /** 分かれる（大きなものだけ）。小さなものは、倒した人を狙う。 */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        EnemyDef def = def();
        int count = def == null ? 0 : (int) Math.round(def.behavior("split", 0.0));
        for (int i = 0; i < count; i++) {
            YodomiSlime small = ModEntities.YODOMI_SLIME.get().create(level);
            if (small == null) {
                continue;
            }
            double a = Math.PI * 2.0 * i / Math.max(1, count) + random.nextDouble() * 0.6;
            small.moveTo(getX() + Math.cos(a) * 0.8, getY() + 0.2, getZ() + Math.sin(a) * 0.8, random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(small)) {
                small.moveTo(getX(), getY() + 0.2, getZ(), random.nextFloat() * 360.0F, 0.0F);
            }
            small.setVariant(SMALL);
            small.setHome(BlockPos.containing(position()));
            small.setTransient(true);
            small.setDeltaMovement(Math.cos(a) * 0.25, 0.35, Math.sin(a) * 0.25);
            level.addFreshEntity(small);
            if (source.getEntity() instanceof ServerPlayer killer) {
                small.addThreat(killer, 1.0);
            }
        }
        if (count > 0) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.5, getZ(), 24, 0.5, 0.4, 0.5, 0.1);
            level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 30, 0.6, 0.3, 0.6, 0.1);
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 濁った水のしずく
        if (level().isClientSide() && isAlive() && random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.DRIPPING_WATER, getRandomX(0.5), getY() + getBbHeight() * 0.6, getRandomZ(0.5), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SLIME_SQUISH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SLIME_JUMP, 0.4F, 0.7F);
    }

    @Override
    public float getVoicePitch() {
        return modelScale() < 0.8F ? 1.3F : 0.7F;
    }
}
