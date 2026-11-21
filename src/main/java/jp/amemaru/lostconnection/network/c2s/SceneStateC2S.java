package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.story.OpeningService;
import jp.amemaru.lostconnection.story.StoryEventManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 演出の進み（クライアント → サーバー）。
 * <ul>
 *   <li>{@code READY}：ワールドが読み込めて、まわりが描けるようになった（オープニングは、ここから始める。ラグで入るのが遅れても、
 *       見えないうちに演出が進まないように）</li>
 *   <li>{@code DONE}：演出を見終えた（{@code scene} は送られた演出の番号）</li>
 * </ul>
 */
public record SceneStateC2S(State state, int scene) {
    public enum State { READY, DONE }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(state);
        buf.writeVarInt(scene);
    }

    public static SceneStateC2S decode(FriendlyByteBuf buf) {
        return new SceneStateC2S(buf.readEnum(State.class), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        switch (state) {
            case READY -> OpeningService.onClientReady(player);
            case DONE -> StoryEventManager.onSceneDone(player, scene);
        }
    }
}
