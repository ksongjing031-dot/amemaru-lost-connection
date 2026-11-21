package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.player.DownState;
import jp.amemaru.lostconnection.player.PlayerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ある人のダウンの様子（Phase 4）を、本人とまわりの人に送る：ダウン中か、教会へ戻るまでの残り（tick）、起こしている人と、
 * たまった量・要る量（tick）。まわりの人の画面では、倒れた姿・頭の上の残り時間・起こす手がかりに使う。
 */
public record DownStateS2C(int entityId, boolean down, int ticksLeft, int length, int progress, int needed, int reviverId) {
    public static DownStateS2C of(ServerPlayer player, PlayerData data) {
        DownState state = data.down();
        long now = player.level().getGameTime();
        int reviverId = -1;
        if (state.reviver() != null) {
            ServerPlayer reviver = player.server.getPlayerList().getPlayer(state.reviver());
            if (reviver != null) {
                reviverId = reviver.getId();
            }
        }
        return new DownStateS2C(player.getId(), state.isDown(), state.ticksLeft(now), state.length(), state.progress(), state.needed(),
                reviverId);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(down);
        buf.writeVarInt(Math.max(0, ticksLeft));
        buf.writeVarInt(Math.max(0, length));
        buf.writeVarInt(Math.max(0, progress));
        buf.writeVarInt(Math.max(0, needed));
        buf.writeVarInt(reviverId + 1);
    }

    public static DownStateS2C decode(FriendlyByteBuf buf) {
        return new DownStateS2C(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt() - 1);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleDownState(this));
    }
}
