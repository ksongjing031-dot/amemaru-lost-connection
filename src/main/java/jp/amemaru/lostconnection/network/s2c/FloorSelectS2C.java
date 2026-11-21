package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 塔の門で、行く階を選ぶ画面を開く（Phase 0-A 7章「塔の門：解放済みの階層を選んで入る。管理者が予告した次の解放の表示」）。
 * まだ開いていない階の名前は出さない（「？？？」）【補完】。
 */
public record FloorSelectS2C(int day, List<Entry> floors) {
    /** 階の様子。 */
    public enum State { OPEN, NOT_BUILT, NEXT, LOCKED }

    /** 1つの階（{@code firstKill} は初撃破したパーティのメンバーの名前。まだなら空）。 */
    public record Entry(int floor, int chapter, String name, State state, int levelMin, int levelMax, String firstKill,
                        long nextMillis) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(day);
        buf.writeVarInt(floors.size());
        for (Entry e : floors) {
            buf.writeVarInt(e.floor());
            buf.writeVarInt(e.chapter());
            buf.writeUtf(e.name(), 64);
            buf.writeEnum(e.state());
            buf.writeVarInt(e.levelMin());
            buf.writeVarInt(e.levelMax());
            buf.writeUtf(e.firstKill(), 256);
            buf.writeVarLong(Math.max(0L, e.nextMillis()));
        }
    }

    public static FloorSelectS2C decode(FriendlyByteBuf buf) {
        int day = buf.readVarInt();
        int n = Math.min(64, buf.readVarInt());
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readEnum(State.class), buf.readVarInt(),
                    buf.readVarInt(), buf.readUtf(256), buf.readVarLong()));
        }
        return new FloorSelectS2C(day, List.copyOf(list));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleFloorSelect(this));
    }
}
