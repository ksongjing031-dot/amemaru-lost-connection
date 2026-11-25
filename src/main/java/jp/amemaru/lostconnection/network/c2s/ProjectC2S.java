package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.town.ProjectService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 寄付台の操作（Phase 7 その4）：共同事業に寄付する（donate）・ペトラ・ポムの料理屋で料理を作ってもらう（cook）。
 * クライアントが送るのは「何をしたいか」だけで、できるかどうかはサーバーで確かめる（{@link ProjectService#onAction}）。
 */
public record ProjectC2S(String action, CompoundTag args) {
    private static final int MAX_KEYS = 8;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action, 32);
        buf.writeNbt(args);
    }

    public static ProjectC2S decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(32);
        CompoundTag args = buf.readNbt();
        if (args == null || args.getAllKeys().size() > MAX_KEYS) {
            args = new CompoundTag();
        }
        return new ProjectC2S(action, args);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            if ("cook".equals(action)) {
                // ペトラ・ポムの料理屋（共同事業③のあと）
                jp.amemaru.lostconnection.town.RestaurantService.onAction(player, args);
            } else {
                ProjectService.onAction(player, action, args);
            }
        }
    }
}
