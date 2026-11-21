package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.party.PartyService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * パーティの操作（Phase 0-B 5章 {@code PartyAction}）：誘う・参加する・断る・抜ける・外す・リーダーを渡す。
 * 人数（6人まで）と権限（誘う・外す・渡すのはリーダー）はサーバーで確かめる（{@link PartyService}）。
 */
public record PartyActionC2S(Action action, @Nullable UUID target) {
    public enum Action { INVITE, ACCEPT, DECLINE, LEAVE, KICK, PROMOTE }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeUUID(target);
        }
    }

    public static PartyActionC2S decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID target = buf.readBoolean() ? buf.readUUID() : null;
        return new PartyActionC2S(action, target);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        switch (action) {
            case INVITE -> {
                ServerPlayer other = target == null ? null : player.server.getPlayerList().getPlayer(target);
                if (other == null) {
                    player.sendSystemMessage(Component.translatable("message.amemaru.party.offline").withStyle(ChatFormatting.RED));
                } else {
                    PartyService.invite(player, other);
                }
            }
            case ACCEPT -> PartyService.accept(player, target);
            case DECLINE -> PartyService.decline(player, target);
            case LEAVE -> PartyService.leave(player);
            case KICK -> {
                if (target != null) {
                    PartyService.kick(player, target);
                }
            }
            case PROMOTE -> {
                if (target != null) {
                    PartyService.promote(player, target);
                }
            }
        }
    }
}
