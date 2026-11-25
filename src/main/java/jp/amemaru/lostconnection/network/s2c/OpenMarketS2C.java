package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.economy.MarketService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 取引所の画面を開く（Phase 6）：手帳から・市場監督の小屋で・露店で（区画の番号）。市場が開いているか、開く日、今日。 */
public record OpenMarketS2C(MarketService.Mode mode, int stall, boolean open, int openDay, int today) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeVarInt(stall);
        buf.writeBoolean(open);
        buf.writeVarInt(openDay);
        buf.writeVarInt(today);
    }

    public static OpenMarketS2C decode(FriendlyByteBuf buf) {
        return new OpenMarketS2C(buf.readEnum(MarketService.Mode.class), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleOpenMarket(this));
    }
}
