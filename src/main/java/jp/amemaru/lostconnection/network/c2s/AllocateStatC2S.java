package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ステータスポイントの振り分けを頼む（Phase 0-B 5章 {@code AllocateStat}）。
 *
 * <p>送るのは「どのステータスに・いくつ」だけ。残りポイントはサーバーで確かめる。</p>
 */
public final class AllocateStatC2S {
    /** 1回の要求で振り分けられる最大の数。 */
    public static final int MAX_PER_REQUEST = 10;

    private final int statIndex;
    private final int amount;

    public AllocateStatC2S(StatType type, int amount) {
        this(type.ordinal(), amount);
    }

    private AllocateStatC2S(int statIndex, int amount) {
        this.statIndex = statIndex;
        this.amount = amount;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(statIndex);
        buf.writeVarInt(amount);
    }

    public static AllocateStatC2S decode(FriendlyByteBuf buf) {
        return new AllocateStatC2S(buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        StatType type = StatType.byIndex(statIndex);
        if (type == null || !type.isAllocatable()) {
            return;
        }
        int requested = Math.max(1, Math.min(amount, MAX_PER_REQUEST));
        PlayerDataCapability.get(player).ifPresent(data -> {
            int used = LevelService.allocate(player, data, type, requested);
            if (used <= 0) {
                player.displayClientMessage(Component.translatable("message.amemaru.no_stat_points")
                        .withStyle(ChatFormatting.RED), true);
            }
        });
    }
}
