package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientGuild;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ギルドの知らせ（Phase 6 その3）。{@code kind} ごとに中身（NBT）が違う：
 * {@code self}（本人の称号・古代記録・親密度・クエスト・縁帳）・{@code catalog}（称号と古代記録の一覧）・
 * {@code shown}（ほかの人が表示している称号）・{@code toast}（称号を取った・クエストを終えた などの演出）・
 * {@code screen}（ギルドの窓口・称号の一覧・記録帳・縁帳・寄付の画面を開く）。
 */
public record GuildS2C(String kind, CompoundTag data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind, 32);
        buf.writeNbt(data);
    }

    public static GuildS2C decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        CompoundTag data = buf.readNbt();
        return new GuildS2C(kind, data == null ? new CompoundTag() : data);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientGuild.handle(kind, data));
    }
}
