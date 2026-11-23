package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientTowerEnv;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 塔の階の知らせ（Phase 8 その1）。{@code kind} ごとに中身（NBT）が違う：{@code env}（今いる階の霧。{@code fogStart}・
 * {@code fogEnd}・{@code fogColor}。空なら霧を戻す）・{@code dark}（ボスの部屋が暗い：{@code on}）。
 */
public record TowerS2C(String kind, CompoundTag data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind, 32);
        buf.writeNbt(data);
    }

    public static TowerS2C decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        CompoundTag data = buf.readNbt();
        return new TowerS2C(kind, data == null ? new CompoundTag() : data);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientTowerEnv.handle(kind, data));
    }
}
