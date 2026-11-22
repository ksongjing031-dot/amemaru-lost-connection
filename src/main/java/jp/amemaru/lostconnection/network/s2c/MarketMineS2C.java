package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 自分の出品・まだ受け取っていない売上・出品の上限・最近の履歴（Phase 6。Phase 0-A 20章「自分の出品と売上の履歴」）。 */
public record MarketMineS2C(List<MarketPageS2C.Row> listings, long proceeds, int limit, List<Line> history) {
    /** 履歴の1行。kind は MarketData.Kind の名前 */
    public record Line(int day, String kind, ItemStack item, long amount, long fee, String other) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(listings.size());
        for (MarketPageS2C.Row row : listings) {
            row.write(buf);
        }
        buf.writeVarLong(proceeds);
        buf.writeVarInt(limit);
        buf.writeVarInt(history.size());
        for (Line line : history) {
            buf.writeVarInt(line.day());
            buf.writeUtf(line.kind(), 32);
            buf.writeItem(line.item());
            buf.writeVarLong(line.amount());
            buf.writeVarLong(line.fee());
            buf.writeUtf(line.other(), 64);
        }
    }

    public static MarketMineS2C decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 512);
        List<MarketPageS2C.Row> listings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            listings.add(MarketPageS2C.Row.read(buf));
        }
        long proceeds = buf.readVarLong();
        int limit = buf.readVarInt();
        int m = Math.min(buf.readVarInt(), 128);
        List<Line> history = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            history.add(new Line(buf.readVarInt(), buf.readUtf(32), buf.readItem(), buf.readVarLong(), buf.readVarLong(), buf.readUtf(64)));
        }
        return new MarketMineS2C(listings, proceeds, limit, history);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleMarketMine(this));
    }
}
