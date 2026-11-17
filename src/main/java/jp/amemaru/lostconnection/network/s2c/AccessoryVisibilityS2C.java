package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ある人の、体に表示するアクセサリーのオン・オフ（枠ごとのビット。1なら表示しない）を、本人とまわりの人に送る
 * （Phase 0-B 5章 {@code PublicInfo} のうち、アクセサリーの表示の分）。
 */
public record AccessoryVisibilityS2C(int entityId, int hiddenBits) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(hiddenBits);
    }

    public static AccessoryVisibilityS2C decode(FriendlyByteBuf buf) {
        return new AccessoryVisibilityS2C(buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleAccessoryVisibility(this));
    }
}
