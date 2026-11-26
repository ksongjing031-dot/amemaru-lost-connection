package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.casino.CasinoService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * カジノの操作（Phase 7 その3）：卓を見る・賭ける・賭けを取り消す・チップを買う・ノットに戻す・景品と取りかえる・地下遊技場の持ち玉と持ちメダルをチップに戻す。
 * クライアントが送るのは「何をしたいか」だけで、できるかどうかはサーバーで確かめる（{@link CasinoService#onAction}）。
 */
public record CasinoC2S(String action, CompoundTag args) {
    private static final int MAX_KEYS = 8;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action, 32);
        buf.writeNbt(args);
    }

    public static CasinoC2S decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(32);
        CompoundTag args = buf.readNbt();
        if (args == null || args.getAllKeys().size() > MAX_KEYS) {
            args = new CompoundTag();
        }
        return new CasinoC2S(action, args);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            CasinoService.onAction(player, action, args);
        }
    }
}
