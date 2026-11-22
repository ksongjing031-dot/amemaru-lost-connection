package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.connection.ConnectionActionsHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * コネクション量の操作（Phase 7 その1：大結晶の輝きの画面・結び札）。クライアントが送るのは「何をしたいか」だけで、
 * できるかどうかはサーバーで確かめる（{@link ConnectionActionsHandler#onAction}）。
 */
public record ConnectionActionC2S(String action, CompoundTag args) {
    private static final int MAX_KEYS = 16;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action, 32);
        buf.writeNbt(args);
    }

    public static ConnectionActionC2S decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(32);
        CompoundTag args = buf.readNbt();
        if (args == null || args.getAllKeys().size() > MAX_KEYS) {
            args = new CompoundTag();
        }
        return new ConnectionActionC2S(action, args);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            ConnectionActionsHandler.onAction(player, action, args);
        }
    }
}
