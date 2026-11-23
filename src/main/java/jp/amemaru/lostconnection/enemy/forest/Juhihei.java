package jp.amemaru.lostconnection.enemy.forest;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.Patroller;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import jp.amemaru.lostconnection.enemy.ai.PatrolGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 樹皮兵（2階の雑魚。Phase 0-A 9章「古代の植樹管理人形」）。苔むした樹皮の体の古い人形。決まった道を巡回する。
 *
 * <ul>
 *   <li>硬い（守りが高い）。腕の打ち下ろし（前）</li>
 *   <li>地面の叩きつけ：周りに円の予兆を出してから、まわりを打つ。当たると、根が足に絡んで2秒ほど遅くなる【補完】</li>
 * </ul>
 */
public class Juhihei extends AmemaruMob implements Patroller {
    private final List<BlockPos> patrol = new ArrayList<>();

    public Juhihei(EntityType<? extends Juhihei> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "juhihei";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new EnemyMeleeGoal(this, "slam", "skill_slam", TELEGRAPH_CIRCLE, 0.9, SoundEvents.ROOTED_DIRT_BREAK));
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "strike", "attack", TELEGRAPH_CONE, 0.9, SoundEvents.WOOD_BREAK));
        goalSelector.addGoal(4, new PatrolGoal(this, this::route, 0.6));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_slam"};
    }

    /** 巡回の道（なければ、生まれた場所のまわりの四角）。 */
    public List<BlockPos> route() {
        if (patrol.isEmpty()) {
            EnemyDef def = def();
            int half = (int) Math.round((def == null ? 8.0 : def.behavior("patrol_size", 8.0)) / 2.0);
            BlockPos home = home();
            patrol.add(home.offset(-half, 0, -half));
            patrol.add(home.offset(half, 0, -half));
            patrol.add(home.offset(half, 0, half));
            patrol.add(home.offset(-half, 0, half));
        }
        return patrol;
    }

    @Override
    public void setPatrol(List<BlockPos> points) {
        patrol.clear();
        patrol.addAll(points);
    }

    @Override
    public void onSkillHit(String skill, ServerPlayer player) {
        if ("slam".equals(skill)) {
            EnemyDef def = def();
            int ticks = (int) Math.round((def == null ? 2.0 : def.behavior("root_seconds", 2.0)) * 20);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 3, false, true, true));
            player.displayClientMessage(Component.translatable("message.amemaru.enemy.rooted").withStyle(ChatFormatting.DARK_GREEN), true);
            if (level() instanceof ServerLevel level) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()),
                        player.getX(), player.getY() + 0.2, player.getZ(), 16, 0.3, 0.1, 0.3, 0.1);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOOD_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOOD_STEP, 0.6F, 0.7F);
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }
}
