package jp.amemaru.lostconnection.tower;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1つの階の動き（人がいる間だけ動かす）：敵の湧く所（{@link FloorSpawner}）、ボスの部屋（{@link BossRoom}）、
 * 封印の前で止まった人への知らせ。
 */
public final class FloorRuntime {
    private final FloorDef def;
    private final FloorSpawner spawner;
    @Nullable
    private final BossRoom bossRoom;
    private final Map<UUID, Long> sealNotice = new HashMap<>();

    public FloorRuntime(FloorDef def) {
        this.def = def;
        this.spawner = new FloorSpawner(def);
        this.bossRoom = def.bossRoom() == null ? null : new BossRoom(def, def.bossRoom());
    }

    public FloorDef def() {
        return def;
    }

    public FloorSpawner spawner() {
        return spawner;
    }

    @Nullable
    public BossRoom bossRoom() {
        return bossRoom;
    }

    public void tick(ServerLevel level, List<ServerPlayer> onFloor, long now) {
        if (bossRoom != null && now % 5 == 0) {
            bossRoom.tick(level, onFloor);
        }
        if (now % 40 == 0) {
            spawner.tick(level, onFloor);
        }
        if (now % 10 == 0) {
            noticeSeal(onFloor, now);
        }
    }

    /** 封印の前で止まっている人に、通れない理由を知らせる（3秒に1回まで）。 */
    private void noticeSeal(List<ServerPlayer> onFloor, long now) {
        FloorDef.BossRoomDef room = def.bossRoom();
        if (room == null) {
            return;
        }
        BlockPos min = def.world(room.sealMin());
        BlockPos max = def.world(room.sealMax());
        AABB box = new AABB(min.getX() - 1.5, min.getY() - 0.5, min.getZ() - 1.5, max.getX() + 2.5, max.getY() + 1.0, max.getZ() + 2.5);
        for (ServerPlayer player : onFloor) {
            if (!box.contains(player.position())) {
                continue;
            }
            if (now < sealNotice.getOrDefault(player.getUUID(), 0L)) {
                continue;
            }
            Component message = null;
            if (bossRoom != null && bossRoom.fighting() && !bossRoom.inside(player.position())) {
                message = Component.translatable("message.amemaru.seal.fighting").withStyle(ChatFormatting.RED);
            } else if (!FloorSealBlock.canPass(player, def.number())) {
                int count = Integer.bitCount(player.isCreative() ? 0
                        : jp.amemaru.lostconnection.player.PlayerDataCapability.get(player).map(d -> d.getStory().pillars(def.number())).orElse(0));
                message = Component.translatable("message.amemaru.seal.locked", count, def.pillars().size()).withStyle(ChatFormatting.YELLOW);
            }
            if (message != null) {
                player.displayClientMessage(message, true);
                sealNotice.put(player.getUUID(), now + 60);
            }
        }
    }

    /** 階を建て直したとき：湧いた敵を消し、ボスの部屋をはじめに戻す。 */
    public void reset(ServerLevel level) {
        spawner.clear(level);
        if (bossRoom != null) {
            bossRoom.reset(level);
        }
    }
}
