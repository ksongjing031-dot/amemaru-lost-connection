package jp.amemaru.lostconnection.enemy.waterway;

import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.ChargeGoal;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 水門ネズミ（3階の雑魚。Phase 0-A 9章「群れ」）。水門の歯車のすき間に住みついた、灰色の大きなネズミ。5〜6匹の群れで出る。
 *
 * <ul>
 *   <li>1匹は弱いが、速い。1匹が気づくと、まわりの群れもみな向かってくる</li>
 *   <li>噛みつき（前）と、少し離れた所からの飛びかかり（短い線の予兆。{@link ChargeGoal}）</li>
 *   <li>ネズミの親分（名のある魔物。定義の {@code behavior.summon}）：HPが70%と40%を下回ると、子分を呼ぶ【補完】</li>
 * </ul>
 */
public class SuimonNezumi extends AmemaruMob {
    /** 子分を呼んだ回数（0〜2）。 */
    private int summonStage;

    public SuimonNezumi(EntityType<? extends SuimonNezumi> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "suimon_nezumi";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ChargeGoal(this, SoundEvents.SILVERFISH_AMBIENT));
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "bite", "attack", TELEGRAPH_WINDUP, 1.4, SoundEvents.SILVERFISH_HURT));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_charge", "skill_call"};
    }

    /** 1匹が気づくと、群れ（behavior.swarm_radius の中のネズミ）がみな向かってくる。 */
    @Override
    protected void onAggro(ServerPlayer player, boolean hurt) {
        EnemyDef def = def();
        double radius = def == null ? 10.0 : def.behavior("swarm_radius", 10.0);
        for (SuimonNezumi other : level().getEntitiesOfClass(SuimonNezumi.class, getBoundingBox().inflate(radius))) {
            if (other != this && other.isAlive() && other.getTarget() == null) {
                other.addThreat(player, 1.0);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && isAlive()) {
            tickSummon(level, true);
        }
    }

    /** ネズミの親分：HPが70%・40%を下回ると、子分を behavior.summon 匹呼ぶ。全快に戻ったら、また呼べる。 */
    private void tickSummon(ServerLevel level, boolean needTarget) {
        EnemyDef def = def();
        int count = def == null ? 0 : (int) Math.round(def.behavior("summon", 0.0));
        if (count <= 0) {
            return;
        }
        double ratio = getEnemyHp() / getEnemyMaxHp();
        if (needTarget && getTarget() == null) {
            if (ratio > 0.999) {
                summonStage = 0;
            }
            return;
        }
        int stage = ratio <= 0.4 ? 2 : ratio <= 0.7 ? 1 : 0;
        if (stage <= summonStage) {
            return;
        }
        summonStage = stage;
        playAction("skill_call");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8F, 1.8F);
        for (int i = 0; i < count; i++) {
            SuimonNezumi pup = ModEntities.SUIMON_NEZUMI.get().create(level);
            if (pup == null) {
                continue;
            }
            double a = random.nextDouble() * Math.PI * 2.0;
            double x = getX() + Math.cos(a) * 1.5;
            double z = getZ() + Math.sin(a) * 1.5;
            pup.moveTo(x, getY(), z, random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(pup)) {
                pup.moveTo(getX(), getY(), getZ(), random.nextFloat() * 360.0F, 0.0F);
            }
            pup.setHome(BlockPos.containing(position()));
            pup.setTransient(true);
            level.addFreshEntity(pup);
            if (getTarget() instanceof ServerPlayer target) {
                pup.addThreat(target, 1.0);
            }
            level.sendParticles(ParticleTypes.POOF, pup.getX(), pup.getY() + 0.3, pup.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
        }
    }

    /** 自動テスト用：狙いがなくても、HPを見て子分を呼ぶ。 */
    public void testSummon() {
        if (level() instanceof ServerLevel level) {
            tickSummon(level, false);
        }
    }

    /** 自動テスト用：子分を呼んだ回数。 */
    public int summonStage() {
        return summonStage;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SILVERFISH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SILVERFISH_STEP, 0.3F, 1.2F);
    }

    @Override
    public float getVoicePitch() {
        return 0.75F + (random.nextFloat() - 0.5F) * 0.15F;
    }
}
