package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仕掛け石と門（11月22日【補完】。1階の東の遺跡）。組の石をすべて決まった秒数のうちに押すと、組の門がしばらくひらく。2人なら
 * それぞれの石で同時に、1人なら走って押す（塔は協力のための施設。Phase 0-A 9章）。
 */
public final class FloorGates {
    private final FloorDef def;
    /** 組ごとの、石を最後に押した時（ゲームの時間。0 は押していない）。 */
    private final Map<String, long[]> pressed = new HashMap<>();
    /** 組ごとの、門が閉じる時。 */
    private final Map<String, Long> openUntil = new HashMap<>();

    public FloorGates(FloorDef def) {
        this.def = def;
    }

    /** 石を押した。 */
    public void press(ServerLevel level, ServerPlayer player, String groupId, int index, long now) {
        FloorDef.SwitchGroup group = def.extras().switchGroup(groupId);
        if (group == null || index < 0 || index >= group.stones().size()) {
            return;
        }
        if (isOpen(groupId, now)) {
            player.displayClientMessage(Component.translatable("message.amemaru.switch.already_open",
                    (openUntil.get(groupId) - now + 19) / 20).withStyle(ChatFormatting.AQUA), true);
            return;
        }
        long[] times = pressed.computeIfAbsent(groupId, k -> new long[group.stones().size()]);
        times[index] = now;
        setLit(level, group.stones().get(index), true);
        level.playSound(null, def.world(group.stones().get(index)), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0F, 0.6F);
        int lit = litCount(times, now, group.windowSeconds());
        if (lit >= group.stones().size()) {
            open(level, group, now);
            return;
        }
        player.displayClientMessage(Component.translatable("message.amemaru.switch.lit", lit, group.stones().size(),
                group.windowSeconds()).withStyle(ChatFormatting.AQUA), true);
    }

    /** 決まった秒数のうちに押された石の数（自動テストでも使う）。 */
    public static int litCount(long[] times, long now, int windowSeconds) {
        int count = 0;
        for (long t : times) {
            if (t > 0 && now - t <= 20L * windowSeconds) {
                count++;
            }
        }
        return count;
    }

    public boolean isOpen(String groupId, long now) {
        return now < openUntil.getOrDefault(groupId, 0L);
    }

    /** 門をひらく（管理者・自動の確かめでも使う）。 */
    public void open(ServerLevel level, FloorDef.SwitchGroup group, long now) {
        openUntil.put(group.id(), now + 20L * group.openSeconds());
        pressed.remove(group.id());
        setGate(level, group, true);
        Vec3 center = gateCenter(group);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.2F, 0.6F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 0.8F);
        Component message = Component.translatable("message.amemaru.switch.open", group.openSeconds()).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= 64.0 * 64.0) {
                player.displayClientMessage(message, true);
            }
        }
        AmemaruMod.LOGGER.info("[amemaru] Gate {} of floor {} opened for {} s", group.id(), def.number(), group.openSeconds());
    }

    /** 毎秒：押してから時間の過ぎた石の光を消し、時間の過ぎた門を閉じる（門の中に人や敵がいれば、出るまで待つ）。 */
    public void tick(ServerLevel level, long now) {
        for (FloorDef.SwitchGroup group : def.extras().switches()) {
            long[] times = pressed.get(group.id());
            for (int i = 0; i < group.stones().size(); i++) {
                boolean on = isOpen(group.id(), now) || times != null && times[i] > 0 && now - times[i] <= 20L * group.windowSeconds();
                setLit(level, group.stones().get(i), on);
            }
            Long until = openUntil.get(group.id());
            if (until == null) {
                // 時間の決まっていない門は閉じておく（サーバーを止めたときにひらいていた門など。中に誰もいなければ）
                if (gateOpenInWorld(level, group) && !blocked(level, group)) {
                    setGate(level, group, false);
                }
            } else if (now >= until) {
                if (blocked(level, group)) {
                    openUntil.put(group.id(), now + 20);
                } else {
                    openUntil.remove(group.id());
                    setGate(level, group, false);
                    Vec3 center = gateCenter(group);
                    level.playSound(null, center.x, center.y, center.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.2F, 0.6F);
                }
            }
        }
    }

    /** すべての門を閉じ、石の光を消す（階を建て直したとき）。 */
    public void reset(ServerLevel level) {
        for (FloorDef.SwitchGroup group : def.extras().switches()) {
            setGate(level, group, false);
            for (BlockPos stone : group.stones()) {
                setLit(level, stone, false);
            }
        }
        pressed.clear();
        openUntil.clear();
    }

    private boolean blocked(ServerLevel level, FloorDef.SwitchGroup group) {
        BlockPos min = def.world(group.gateMin());
        BlockPos max = def.world(group.gateMax());
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        List<Entity> inside = level.getEntities((Entity) null, box, entity -> entity.isAlive() && !entity.isSpectator());
        return !inside.isEmpty();
    }

    private Vec3 gateCenter(FloorDef.SwitchGroup group) {
        BlockPos min = def.world(group.gateMin());
        BlockPos max = def.world(group.gateMax());
        return new Vec3((min.getX() + max.getX() + 1) / 2.0, (min.getY() + max.getY() + 1) / 2.0, (min.getZ() + max.getZ() + 1) / 2.0);
    }

    private boolean gateOpenInWorld(ServerLevel level, FloorDef.SwitchGroup group) {
        BlockPos min = def.world(group.gateMin());
        BlockPos max = def.world(group.gateMax());
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.hasChunkAt(pos)) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof TowerGateBlock && state.getValue(TowerGateBlock.OPEN)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void setGate(ServerLevel level, FloorDef.SwitchGroup group, boolean open) {
        BlockPos min = def.world(group.gateMin());
        BlockPos max = def.world(group.gateMax());
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof TowerGateBlock && state.getValue(TowerGateBlock.OPEN) != open) {
                level.setBlock(pos, state.setValue(TowerGateBlock.OPEN, open), 3);
            }
        }
    }

    private void setLit(ServerLevel level, BlockPos local, boolean lit) {
        BlockPos pos = def.world(local);
        if (!level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TowerSwitchBlock && state.getValue(TowerSwitchBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(TowerSwitchBlock.LIT, lit), 3);
        }
    }
}
