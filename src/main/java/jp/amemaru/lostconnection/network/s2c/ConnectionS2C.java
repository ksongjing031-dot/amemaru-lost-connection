package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientConnection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * コネクション量の知らせ（Phase 7 その1）。{@code kind} ごとに中身（NBT）が違う：{@code state}（サーバー全体の輝きと、
 * 本人の今日の分野別CP）・{@code stage}（輝き段階が上がった。大結晶の一言と新しい効果）・{@code star}（加護の星が灯った）・
 * {@code screen}（大結晶の輝きの画面を開く）・{@code thanks}（結び札が届いた）。
 */
public record ConnectionS2C(String kind, CompoundTag data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind, 32);
        buf.writeNbt(data);
    }

    public static ConnectionS2C decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        CompoundTag data = buf.readNbt();
        return new ConnectionS2C(kind, data == null ? new CompoundTag() : data);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientConnection.handle(kind, data));
    }
}
