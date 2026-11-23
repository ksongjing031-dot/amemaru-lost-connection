package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ダウン中のリタイア（11月22日のご指示：決まったキーを数秒押し続けると、30秒を待たずに教会へ戻る）。キーを押し続けた長さは
 * クライアントで数える（早く送っても、時間切れと同じ代価を払って教会へ戻るだけなので、サーバーでは「ダウン中か」だけを確かめる）。
 */
public record RetireC2S() {
    public void encode(FriendlyByteBuf buf) {
    }

    public static RetireC2S decode(FriendlyByteBuf buf) {
        return new RetireC2S();
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            PlayerDataCapability.get(player).ifPresent(data -> DownHandler.retire(player, data));
        }
    }
}
