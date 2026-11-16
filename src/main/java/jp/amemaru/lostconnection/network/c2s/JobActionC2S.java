package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 職業選択の画面での操作：職業を選ぶ・上位職に昇格する。誓約の結晶の近くにいるか・費用はサーバーで確かめる。
 */
public final class JobActionC2S {
    public enum Action {
        /** 職業を選ぶ・変える。 */
        SELECT,
        /** 上位職に昇格する（一度昇格したことがある人だけ）。 */
        PROMOTE
    }

    private final Action action;
    private final int jobIndex;

    public JobActionC2S(Action action, int jobIndex) {
        this.action = action;
        this.jobIndex = jobIndex;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeVarInt(jobIndex + 1);
    }

    public static JobActionC2S decode(FriendlyByteBuf buf) {
        return new JobActionC2S(buf.readEnum(Action.class), buf.readVarInt() - 1);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(data -> {
            switch (action) {
                case SELECT -> JobService.selectFromScreen(player, data, jobIndex);
                case PROMOTE -> JobService.promoteFromScreen(player, data);
            }
        });
    }
}
