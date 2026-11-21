package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.tower.TowerService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 塔の門で、行く階を選んだ（{@code floor}）。転移の円の上にいるか・その階が開いているか・建ててあるかは、サーバーで確かめる。
 */
public record FloorSelectC2S(int floor) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(floor);
    }

    public static FloorSelectC2S decode(FriendlyByteBuf buf) {
        return new FloorSelectC2S(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            TowerService.onFloorSelected(player, floor);
        }
    }
}
