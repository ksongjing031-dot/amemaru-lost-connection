package jp.amemaru.lostconnection.enemy;

import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * ツノモフ（1階「始まりの草原」の雑魚。Phase 0-A 9章）：角のある毛玉。ふだんはおとなしく草原を歩き回るが、
 * 叩かれると、近くのツノモフが群れで反撃してくる（{@code behavior.herd_radius}）。攻撃は、身を縮めてからの頭突き。
 */
public class Tsunomofu extends AmemaruMob {
    public Tsunomofu(EntityType<? extends Tsunomofu> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "tsunomofu";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "headbutt", "attack", TELEGRAPH_WINDUP, 1.25,
                SoundEvents.GOAT_RAM_IMPACT));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** 叩かれたら、まわりのツノモフも同じ相手に向かう（群れで反撃）。 */
    @Override
    protected void onAggro(ServerPlayer player, boolean hurt) {
        if (!hurt) {
            return;
        }
        EnemyDef def = def();
        double radius = def == null ? 12.0 : def.behavior("herd_radius", 12.0);
        for (Tsunomofu other : level().getEntitiesOfClass(Tsunomofu.class, getBoundingBox().inflate(radius))) {
            if (other != this && other.isAlive() && other.getTarget() == null) {
                other.addThreat(player, 1.0);
                other.playAction("skill_alert");
            }
        }
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_alert"};
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
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOOL_STEP, 0.3F, 1.2F);
    }

    @Override
    public float getVoicePitch() {
        // 大きな個体（群れの長）は低い声（11月22日）
        return (1.4F + (random.nextFloat() - 0.5F) * 0.2F) / Math.max(1.0F, modelScale() * 0.75F);
    }
}
