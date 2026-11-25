package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 出品の画面に出す、持ち物のその枠の品の相場（Phase 6）：出せない理由（言語の鍵。出せるなら空）・相場・値段の下限と上限（1個あたり）・
 * 手数料（出品・売れたとき。%）・今日は出品の手数料が無料か・最近の取引の値段・今の出品の数と上限。
 */
public record MarketPriceS2C(int slot, String problem, long reference, long min, long max, double listingFee, double saleFee,
                             boolean freeDay, long[] recent, int listings, int limit) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
        buf.writeUtf(problem, 128);
        buf.writeVarLong(reference);
        buf.writeVarLong(min);
        buf.writeVarLong(max);
        buf.writeDouble(listingFee);
        buf.writeDouble(saleFee);
        buf.writeBoolean(freeDay);
        buf.writeVarInt(recent.length);
        for (long price : recent) {
            buf.writeVarLong(price);
        }
        buf.writeVarInt(listings);
        buf.writeVarInt(limit);
    }

    public static MarketPriceS2C decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        String problem = buf.readUtf(128);
        long reference = buf.readVarLong();
        long min = buf.readVarLong();
        long max = buf.readVarLong();
        double listingFee = buf.readDouble();
        double saleFee = buf.readDouble();
        boolean freeDay = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), 16);
        long[] recent = new long[n];
        for (int i = 0; i < n; i++) {
            recent[i] = buf.readVarLong();
        }
        return new MarketPriceS2C(slot, problem, reference, min, max, listingFee, saleFee, freeDay, recent, buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleMarketPrice(this));
    }
}
