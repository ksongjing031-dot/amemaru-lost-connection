package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.story.StoryEventManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 大結晶の「記憶の再生」で、見返すストーリーイベントを選んだ（Phase 0-B 5章 {@code ReplayMemory}）。
 * すでに起きたもの（本人が見たか、サーバーで起きたもの）かは、サーバーで確かめる。
 */
public record ReplayMemoryC2S(ResourceLocation event) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(event);
    }

    public static ReplayMemoryC2S decode(FriendlyByteBuf buf) {
        return new ReplayMemoryC2S(buf.readResourceLocation());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            StoryEventManager.onReplayRequest(player, event);
        }
    }
}
