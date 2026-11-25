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

/** 取引所の一覧の1ページ（Phase 6。Phase 0-B の ListPage）。stall は露店の画面（0なら取引所）。 */
public record MarketPageS2C(int stall, int page, int pages, int total, List<Row> rows) {
    /** 1件の出品。sellerStall は売り手が借りている露店（0なら借りていない）。reference は今の相場、recent は最近の取引の値段 */
    public record Row(long id, ItemStack item, long unitPrice, String seller, int sellerStall, boolean mine, long reference, long[] recent,
                      int expiresDay) {
        public long total() {
            return unitPrice > Long.MAX_VALUE / Math.max(1, item.getCount()) ? Long.MAX_VALUE : unitPrice * item.getCount();
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeItem(item);
            buf.writeVarLong(unitPrice);
            buf.writeUtf(seller, 64);
            buf.writeVarInt(sellerStall);
            buf.writeBoolean(mine);
            buf.writeVarLong(reference);
            buf.writeVarInt(recent.length);
            for (long price : recent) {
                buf.writeVarLong(price);
            }
            buf.writeVarInt(expiresDay);
        }

        public static Row read(FriendlyByteBuf buf) {
            long id = buf.readVarLong();
            ItemStack item = buf.readItem();
            long unitPrice = buf.readVarLong();
            String seller = buf.readUtf(64);
            int sellerStall = buf.readVarInt();
            boolean mine = buf.readBoolean();
            long reference = buf.readVarLong();
            int n = Math.min(buf.readVarInt(), 16);
            long[] recent = new long[n];
            for (int i = 0; i < n; i++) {
                recent[i] = buf.readVarLong();
            }
            return new Row(id, item, unitPrice, seller, sellerStall, mine, reference, recent, buf.readVarInt());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(stall);
        buf.writeVarInt(page);
        buf.writeVarInt(pages);
        buf.writeVarInt(total);
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            row.write(buf);
        }
    }

    public static MarketPageS2C decode(FriendlyByteBuf buf) {
        int stall = buf.readVarInt();
        int page = buf.readVarInt();
        int pages = buf.readVarInt();
        int total = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 64);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(Row.read(buf));
        }
        return new MarketPageS2C(stall, page, pages, total, rows);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleMarketPage(this));
    }
}
