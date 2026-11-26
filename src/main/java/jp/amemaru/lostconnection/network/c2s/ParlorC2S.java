package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.casino.parlor.ParlorService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 地下遊技場の操作（11月27日【補完】）：玉・メダルを借りる・チップに戻す・ハンドル（打つ・強さ・右打ち）・レバー・止めボタン・
 * 台を離れる・画面を閉じる・島のデータ。できるかどうかはサーバーで確かめる（{@link ParlorService#onAction}）。
 */
public record ParlorC2S(String action, CompoundTag args) {
    private static final int MAX_KEYS = 8;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action, 32);
        buf.writeNbt(args);
    }

    public static ParlorC2S decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(32);
        CompoundTag args = buf.readNbt();
        if (args == null || args.getAllKeys().size() > MAX_KEYS) {
            args = new CompoundTag();
        }
        return new ParlorC2S(action, args);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            ParlorService.onAction(player, action, args);
        }
    }
}
