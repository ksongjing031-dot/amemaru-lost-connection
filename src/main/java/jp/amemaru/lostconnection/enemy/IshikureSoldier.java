package jp.amemaru.lostconnection.enemy;

import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import jp.amemaru.lostconnection.enemy.ai.PatrolGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 石くれ兵（1階の雑魚。Phase 0-A 9章）：決まった道を巡回する古代の見張り。前の視野（{@code behavior.vision_angle}）に
 * プレイヤーを見つけると、鐘のような音とともに頭の上に「！」を出し、近くの石くれ兵を呼ぶ（{@code behavior.call_radius}）。
 * 攻撃は、腕を振り上げてからの叩きつけ（前の広い範囲）。
 *
 * <p>巡回の道は、NBT の {@code Amemaru.Patrol}（点の一覧。Phase 5で階層を作るときに置く）。なければ、生まれた場所の
 * まわりの四角（{@code behavior.patrol_size}）を回る。</p>
 */
public class IshikureSoldier extends AmemaruMob implements Patroller {
    private final List<BlockPos> patrol = new ArrayList<>();
    private boolean alerted;
    private int calmTimer;

    public IshikureSoldier(EntityType<? extends IshikureSoldier> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "ishikure_soldier";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "slam", "attack", TELEGRAPH_CIRCLE, 1.0,
                SoundEvents.IRON_GOLEM_ATTACK));
        goalSelector.addGoal(4, new PatrolGoal(this, this::route, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** 巡回の道（NBTになければ、生まれた場所のまわりの四角）。 */
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

    /** 巡回の道を決める（塔の階の湧く所の {@code route}。Phase 5）。 */
    @Override
    public void setPatrol(List<BlockPos> points) {
        patrol.clear();
        patrol.addAll(points);
    }

    /** 見つけたら、仲間を呼ぶ（呼ばれた仲間は、さらに呼ばない）。 */
    @Override
    protected void onAggro(ServerPlayer player, boolean hurt) {
        calmTimer = 200;
        if (alerted) {
            return;
        }
        alerted = true;
        playAction("skill_call");
        setTelegraph(TELEGRAPH_ALERT, 30, getYRot(), 0.0F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.0F, 1.4F);
        EnemyDef def = def();
        double radius = def == null ? 16.0 : def.behavior("call_radius", 16.0);
        for (IshikureSoldier other : level().getEntitiesOfClass(IshikureSoldier.class, getBoundingBox().inflate(radius))) {
            if (other != this && other.isAlive() && !other.alerted) {
                other.alerted = true;
                other.calmTimer = 200;
                other.addThreat(player, 1.0);
                other.setTelegraph(TELEGRAPH_ALERT, 20, other.getYRot(), 0.0F);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && alerted && getTarget() == null && --calmTimer <= 0) {
            alerted = false;
        }
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_call"};
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.STONE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STONE_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.STONE_STEP, 0.6F, 0.7F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (BlockPos pos : patrol) {
            list.add(NbtUtils.writeBlockPos(pos));
        }
        tag.getCompound("Amemaru").put("Patrol", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        patrol.clear();
        ListTag list = tag.getCompound("Amemaru").getList("Patrol", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            patrol.add(NbtUtils.readBlockPos(list.getCompound(i)));
        }
    }
}
