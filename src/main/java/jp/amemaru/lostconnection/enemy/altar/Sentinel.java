package jp.amemaru.lostconnection.enemy.altar;

import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.ChargeGoal;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 防衛兵センチネル（5階の雑魚。Phase 0-A 9章「盾持ち」）。翠の結晶の目を持つ、白い石と青銅の兵。左腕に大きな塔の盾、右手に槍。
 *
 * <ul>
 *   <li>塔の盾：前（左右 {@code behavior.shield_angle}/2 度の内）から受けるダメージは {@code behavior.shield_taken}。
 *       後ろか横から攻める（はじめて弾かれた人に、手がかりを出す）</li>
 *   <li>盾打ち（bash。前の扇。押し返す）と、槍の突進（charge。短い一直線）</li>
 *   <li>防衛隊長（名のある魔物）：大きく、盾がさらに硬い。HPが半分を下回ると一度、防衛兵を {@code behavior.call} 体呼ぶ</li>
 * </ul>
 */
public class Sentinel extends AmemaruMob {
    private static final Set<UUID> TOLD = new HashSet<>();
    private int lastClang;
    private boolean called;

    public Sentinel(EntityType<? extends Sentinel> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "sentinel";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_charge", "skill_call"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ChargeGoal(this, SoundEvents.IRON_GOLEM_ATTACK));
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "bash", "attack", TELEGRAPH_CONE, 1.0, SoundEvents.SHIELD_BLOCK));
    }

    /** 打った人が前にいれば、塔の盾で弾く。 */
    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        if (attacker == null) {
            return 1.0;
        }
        EnemyDef def = def();
        double angle = def == null ? 120.0 : def.behavior("shield_angle", 120.0);
        if (!inFront(attacker.position(), angle / 2.0)) {
            return 1.0;
        }
        clang(attacker);
        return def == null ? 0.25 : def.behavior("shield_taken", 0.25);
    }

    private void clang(Entity attacker) {
        if (!(level() instanceof ServerLevel level) || tickCount - lastClang < 4) {
            return;
        }
        lastClang = tickCount;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 0.8F + random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.CRIT, getX() - Math.sin(Math.toRadians(getYRot())) * 0.8, getY() + 1.2,
                getZ() + Math.cos(Math.toRadians(getYRot())) * 0.8, 6, 0.3, 0.4, 0.3, 0.1);
        if (attacker instanceof ServerPlayer player && TOLD.add(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.amemaru.sentinel.shield").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    @Override
    public void onDamagedBy(@Nullable Entity attacker, double amount) {
        super.onDamagedBy(attacker, amount);
        EnemyDef def = def();
        int count = def == null ? 0 : (int) Math.round(def.behavior("call", 0.0));
        if (count <= 0 || called || getEnemyHp() > getEnemyMaxHp() * 0.5 || !(level() instanceof ServerLevel level)) {
            return;
        }
        called = true;
        playAction("skill_call");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.2F, 1.4F);
        for (int i = 0; i < count; i++) {
            Sentinel guard = ModEntities.SENTINEL.get().create(level);
            if (guard == null) {
                continue;
            }
            double a = Math.PI * 2.0 * i / count + random.nextDouble();
            guard.moveTo(getX() + Math.cos(a) * 3.0, getY(), getZ() + Math.sin(a) * 3.0, random.nextFloat() * 360.0F, 0.0F);
            guard.setHome(BlockPos.containing(position()));
            guard.setTransient(true);
            level.addFreshEntity(guard);
            ServerPlayer target = topThreat();
            if (target != null) {
                guard.addThreat(target, 1.0);
            }
            level.sendParticles(ParticleTypes.CLOUD, guard.getX(), guard.getY() + 0.5, guard.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** 自動テスト用：呼んだか。 */
    public boolean testCalled() {
        return called;
    }
}
