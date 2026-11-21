package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 本人の所持金（ノット。Phase 4。Phase 0-B 4.1「所持金」）。 */
public record SyncWalletS2C(long knots) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(Math.max(0L, knots));
    }

    public static SyncWalletS2C decode(FriendlyByteBuf buf) {
        return new SyncWalletS2C(buf.readVarLong());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleWallet(this));
    }
}
