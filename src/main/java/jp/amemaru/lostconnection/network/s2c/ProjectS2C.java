package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientProject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 寄付台の知らせ（Phase 7 その4）：{@code screen}（寄付台の画面を開く・寄付のあとに書きかえる。共同事業の寄付・目標・
 * 輝き段階・銘板）。
 */
public record ProjectS2C(String kind, CompoundTag data) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(kind, 32);
        buf.writeNbt(data);
    }

    public static ProjectS2C decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        CompoundTag data = buf.readNbt();
        return new ProjectS2C(kind, data == null ? new CompoundTag() : data);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientProject.handle(kind, data));
    }
}
