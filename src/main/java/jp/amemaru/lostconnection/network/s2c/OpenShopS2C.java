package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.economy.ShopDef;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 店の画面を開く（Phase 6）：カウンターの場所と、店の定義（値段は決めた額）。 */
public record OpenShopS2C(BlockPos pos, ShopDef shop) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        shop.write(buf);
    }

    public static OpenShopS2C decode(FriendlyByteBuf buf) {
        return new OpenShopS2C(buf.readBlockPos(), ShopDef.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleOpenShop(this));
    }
}
