package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientSocial;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 交流の知らせ（Phase 6 その4。Phase 0-B 2章の「交流（social）」）。{@code kind} ごとに中身（NBT）が違う：
 * {@code commissions}（依頼台の一覧）・{@code duel}（決闘の申し込み・観戦できる決闘・戦績）・{@code duel_hud}（決闘の間の残り時間と
 * 相手のHP）・{@code screen}（依頼台・決闘の画面を開く）。
 */
public record SocialS2C(String kind, CompoundTag data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind, 32);
        buf.writeNbt(data);
    }

    public static SocialS2C decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        CompoundTag data = buf.readNbt();
        return new SocialS2C(kind, data == null ? new CompoundTag() : data);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSocial.handle(kind, data));
    }
}
