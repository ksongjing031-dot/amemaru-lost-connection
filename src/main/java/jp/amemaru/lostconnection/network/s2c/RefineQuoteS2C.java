package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientRefine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 装飾工房の台に置いたアクセサリーの、厳選の見積もり（Phase 6 その4）：振り直し・強化の費用、固定の数、振り直すほどの上乗せ、
 * 装飾師の値引き、分解で戻る素材。費用は進行日や熟練度で変わるので、サーバーが計算して送る。
 */
public record RefineQuoteS2C(CompoundTag data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public static RefineQuoteS2C decode(FriendlyByteBuf buf) {
        CompoundTag data = buf.readNbt();
        return new RefineQuoteS2C(data == null ? new CompoundTag() : data);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRefine.setQuote(data));
    }
}
