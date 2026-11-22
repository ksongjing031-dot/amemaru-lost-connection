package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 店で買った・売った結果（Phase 6）。店の画面が開いていれば、画面の下の行に出す（チャットにも同じ文が出る）。 */
public record ShopResultS2C(Component message, boolean ok) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeComponent(message);
        buf.writeBoolean(ok);
    }

    public static ShopResultS2C decode(FriendlyByteBuf buf) {
        return new ShopResultS2C(buf.readComponent(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleShopResult(this));
    }
}
