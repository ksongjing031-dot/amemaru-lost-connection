package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.story.DialogueManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 会話の選択（Phase 0-B 5章 {@code DialogueChoice}：NPC・会話・選んだ番号）。{@code choice} が −1 なら最後まで読んだ、
 * −2 なら途中で閉じた。その選択肢が今出ているか・NPCとの距離は、サーバーで確かめる。
 */
public record DialogueChoiceC2S(int session, int choice) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(session);
        buf.writeVarInt(choice + 2);
    }

    public static DialogueChoiceC2S decode(FriendlyByteBuf buf) {
        return new DialogueChoiceC2S(buf.readVarInt(), buf.readVarInt() - 2);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            DialogueManager.onChoice(player, session, choice);
        }
    }
}
