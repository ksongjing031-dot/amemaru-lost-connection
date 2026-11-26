package jp.amemaru.lostconnection.network.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 祈願（ガチャ）の知らせ（11月27日）。{@code kind}：{@code screen}（画面を開く）・{@code result}（引いた結果と、書きかえた祈願の数）・
 * {@code error}（引けなかった）。中身は {@code GachaService.screenTag}。
 */
public record GachaS2C(String kind, CompoundTag data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind, 32);
        buf.writeNbt(data);
    }

    public static GachaS2C decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        CompoundTag data = buf.readNbt();
        return new GachaS2C(kind, data == null ? new CompoundTag() : data);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> jp.amemaru.lostconnection.client.screen.GachaScreen.handle(kind, data));
    }
}
