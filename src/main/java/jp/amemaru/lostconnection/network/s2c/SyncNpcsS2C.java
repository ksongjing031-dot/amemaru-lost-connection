package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** NPCの見た目に要る分（名前・肩書き・スキン・表情）を配る（ログインしたときと、データパックを読み直したとき）。 */
public record SyncNpcsS2C(List<NpcDef> npcs) {
    public static SyncNpcsS2C current() {
        return new SyncNpcsS2C(List.copyOf(NpcDefs.all().values()));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(npcs.size());
        for (NpcDef def : npcs) {
            def.write(buf);
        }
    }

    public static SyncNpcsS2C decode(FriendlyByteBuf buf) {
        int n = Math.min(256, buf.readVarInt());
        List<NpcDef> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(NpcDef.read(buf));
        }
        return new SyncNpcsS2C(List.copyOf(list));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleNpcs(this));
    }
}
