package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 取引所での結果（Phase 6）。取引所の画面が開いていれば下の行に出し、うまくいったら一覧を取り直す（チャットにも同じ文）。 */
public record MarketResultS2C(Component message, boolean ok) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeComponent(message);
        buf.writeBoolean(ok);
    }

    public static MarketResultS2C decode(FriendlyByteBuf buf) {
        return new MarketResultS2C(buf.readComponent(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleMarketResult(this));
    }
}
