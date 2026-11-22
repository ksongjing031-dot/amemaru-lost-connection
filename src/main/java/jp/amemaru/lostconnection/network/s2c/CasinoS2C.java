package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientCasino;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * カジノの知らせ（Phase 7 その3）。{@code kind} ごとに中身（NBT）が違う：{@code screen}（カジノの画面を開く・チップと景品を
 * 書きかえる）・{@code table}（卓の様子：賭ける時間・回る・結果・賭けた人）。
 */
public record CasinoS2C(String kind, CompoundTag data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind, 32);
        buf.writeNbt(data);
    }

    public static CasinoS2C decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        CompoundTag data = buf.readNbt();
        return new CasinoS2C(kind, data == null ? new CompoundTag() : data);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCasino.handle(kind, data));
    }
}
