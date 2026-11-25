package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.economy.KnotTransfer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * ノットの受け渡し（11月22日に決定。Phase 6）：相手と額。相手がいるか・自分でないか・所持金・上限・間隔は
 * サーバーで確かめる（{@link KnotTransfer}）。
 */
public record TransferKnotsC2S(UUID target, long amount) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(target);
        buf.writeVarLong(Math.max(0L, amount));
    }

    public static TransferKnotsC2S decode(FriendlyByteBuf buf) {
        return new TransferKnotsC2S(buf.readUUID(), buf.readVarLong());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        KnotTransfer.transfer(player, player.server.getPlayerList().getPlayer(target), amount);
    }
}
