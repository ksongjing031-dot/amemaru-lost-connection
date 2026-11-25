package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientFishing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 釣りの知らせ（Phase 7 その2）。{@code kind} ごとに中身（NBT）が違う：{@code start}（魚がかかった。糸の張りのミニゲームを始める）・
 * {@code end}（ミニゲームを終える）・{@code screen}（ポッカの釣り具屋の画面：図鑑・売る・交換・大会）・{@code tournament}
 * （釣り大会の残り時間と順位）。
 */
public record FishingS2C(String kind, CompoundTag data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind, 32);
        buf.writeNbt(data);
    }

    public static FishingS2C decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        CompoundTag data = buf.readNbt();
        return new FishingS2C(kind, data == null ? new CompoundTag() : data);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientFishing.handle(kind, data));
    }
}
