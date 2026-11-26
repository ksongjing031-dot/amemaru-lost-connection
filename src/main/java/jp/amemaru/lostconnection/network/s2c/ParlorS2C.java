package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientParlor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 地下遊技場の知らせ（11月27日【補完】）。{@code kind}：{@code open}（台の画面を開く）・{@code state}（台の様子）・{@code spin}
 * （パチンコの図柄が回り始める。見せ方の台本つき）・{@code slot}（スロットのレバー・止めボタン・払い出し）・{@code data}（島の
 * データの画面）・{@code close}（台の画面を閉じる）。
 */
public record ParlorS2C(String kind, CompoundTag data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind, 32);
        buf.writeNbt(data);
    }

    public static ParlorS2C decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        CompoundTag data = buf.readNbt();
        return new ParlorS2C(kind, data == null ? new CompoundTag() : data);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientParlor.handle(kind, data));
    }
}
