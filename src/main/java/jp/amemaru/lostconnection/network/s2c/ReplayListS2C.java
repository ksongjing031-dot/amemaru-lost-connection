package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 大結晶の「記憶の再生」で見直せるストーリーイベントの一覧（本人が見たものと、サーバーで起きたもの）。 */
public record ReplayListS2C(List<Entry> entries) {
    public record Entry(ResourceLocation event, String title) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeResourceLocation(e.event());
            buf.writeUtf(e.title(), 128);
        }
    }

    public static ReplayListS2C decode(FriendlyByteBuf buf) {
        int n = Math.min(256, buf.readVarInt());
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readResourceLocation(), buf.readUtf(128)));
        }
        return new ReplayListS2C(List.copyOf(list));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleReplayList(this));
    }
}
