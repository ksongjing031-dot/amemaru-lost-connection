package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.fishing.FishingService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 釣りの操作（Phase 7 その2）：ミニゲームの結果・釣り具屋の画面（開く・売る・まとめて売る・留め糸と交換・竿を借りる）。
 * クライアントが送るのは「何をしたいか」だけで、できるかどうかはサーバーで確かめる（{@link FishingService#onAction}）。
 */
public record FishingC2S(String action, CompoundTag args) {
    private static final int MAX_KEYS = 16;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action, 32);
        buf.writeNbt(args);
    }

    public static FishingC2S decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(32);
        CompoundTag args = buf.readNbt();
        if (args == null || args.getAllKeys().size() > MAX_KEYS) {
            args = new CompoundTag();
        }
        return new FishingC2S(action, args);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            FishingService.onAction(player, action, args);
        }
    }
}
