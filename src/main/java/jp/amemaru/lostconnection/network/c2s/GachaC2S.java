package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.gacha.GachaService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 祈願を引く（11月27日）。クライアントが送るのは祈願のIDと回数だけで、引けるかどうか（祈願札の数・巫女のそば）はサーバーで確かめる。
 */
public record GachaC2S(String banner, int count) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(banner, 64);
        buf.writeVarInt(count);
    }

    public static GachaC2S decode(FriendlyByteBuf buf) {
        return new GachaC2S(buf.readUtf(64), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            GachaService.onPull(player, banner, count);
        }
    }
}
