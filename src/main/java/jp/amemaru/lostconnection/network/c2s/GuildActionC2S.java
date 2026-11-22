package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.guild.GuildService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ギルドの操作（Phase 6 その3）。クライアントが送るのは「何をしたいか」だけで、できるかどうかはサーバーで確かめる
 * （{@link GuildService#onAction}）。
 */
public record GuildActionC2S(String action, CompoundTag args) {
    /** 1つの操作の中身の項目の数の上限（押しすぎないように）。 */
    private static final int MAX_KEYS = 32;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action, 32);
        buf.writeNbt(args);
    }

    public static GuildActionC2S decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(32);
        CompoundTag args = buf.readNbt();
        if (args == null || args.getAllKeys().size() > MAX_KEYS) {
            args = new CompoundTag();
        }
        return new GuildActionC2S(action, args);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            GuildService.onAction(player, action, args);
        }
    }
}
