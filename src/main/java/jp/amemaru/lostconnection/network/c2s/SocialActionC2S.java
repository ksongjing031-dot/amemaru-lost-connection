package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.social.SocialActions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 交流の操作（Phase 6 その4：依頼台と決闘）。クライアントが送るのは「何をしたいか」だけで、できるかどうかはサーバーで確かめる
 * （{@link SocialActions#onAction}）。
 */
public record SocialActionC2S(String action, CompoundTag args) {
    private static final int MAX_KEYS = 32;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action, 32);
        buf.writeNbt(args);
    }

    public static SocialActionC2S decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(32);
        CompoundTag args = buf.readNbt();
        if (args == null || args.getAllKeys().size() > MAX_KEYS) {
            args = new CompoundTag();
        }
        return new SocialActionC2S(action, args);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            SocialActions.onAction(player, action, args);
        }
    }
}
