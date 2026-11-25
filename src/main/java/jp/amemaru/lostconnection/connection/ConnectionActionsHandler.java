package jp.amemaru.lostconnection.connection;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.guild.GuildCounters;
import jp.amemaru.lostconnection.guild.GuildData;
import jp.amemaru.lostconnection.guild.GuildState;
import jp.amemaru.lostconnection.guild.TitleService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.ConnectionS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

/**
 * コネクション量の操作（{@code ConnectionActionC2S}。Phase 7 その1）。
 *
 * <ul>
 *   <li>{@code open}：大結晶の輝きの画面を開く（大結晶の台座の「記憶の石」を右クリック）</li>
 *   <li>{@code replay}：同じ画面から「記憶の再生」の一覧へ</li>
 *   <li>{@code thanks}：結び札（ありがとう）を贈る（Phase 0-A 13章・15章。縁帳の人へ。1日3枚、同じ人へは1日1枚【補完】）。
 *       贈った人と受け取った人に、それぞれ交流のCP。受け取った人は称号「…」の数が増える。いない人へは、次に入ったときに届く</li>
 * </ul>
 */
public final class ConnectionActionsHandler {
    private ConnectionActionsHandler() {
    }

    public static void onAction(ServerPlayer player, String action, CompoundTag args) {
        switch (action) {
            case "open" -> open(player);
            case "replay" -> jp.amemaru.lostconnection.story.StoryEventManager.openReplayList(player);
            case "thanks" -> {
                if (args.hasUUID("to")) {
                    sendThanks(player, args.getUUID("to"));
                    if (args.getBoolean("refresh")) {
                        // 縁帳から贈ったときは、縁帳を開き直す（贈った印と残りの枚数）
                        jp.amemaru.lostconnection.guild.GuildService.openConnections(player);
                    }
                }
            }
            default -> AmemaruMod.LOGGER.debug("[amemaru] Unknown connection action {} from {}", action,
                    player.getGameProfile().getName());
        }
    }

    /** 大結晶の輝きの画面を開く。 */
    public static void open(ServerPlayer player) {
        PlayerDataCapability.get(player).ifPresent(data -> ConnectionService.syncSelf(player, data));
        ModNetwork.sendTo(player, new ConnectionS2C("screen", new CompoundTag()));
    }

    /** 結び札を贈れない理由（贈れるなら null）。 */
    public static String thanksProblem(ServerPlayer player, UUID to) {
        if (to.equals(player.getUUID())) {
            return "connection.amemaru.thanks.self";
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return "connection.amemaru.thanks.unknown";
        }
        GuildData.Connection known = data.getGuild().connection(to);
        if (known == null) {
            return "connection.amemaru.thanks.unknown";
        }
        ConnectionRecord record = data.getConnection();
        record.rollover(ConnectionService.today());
        if (record.actionsToday(ConnectionActions.THANKS_SEND) >= ConnectionService.thanksDailyLimit()) {
            return "connection.amemaru.thanks.limit";
        }
        if (record.targetToday(ConnectionActions.THANKS_SEND, to) > 0) {
            return "connection.amemaru.thanks.again";
        }
        return null;
    }

    /** 結び札を贈る。贈れたら true。 */
    public static boolean sendThanks(ServerPlayer player, UUID to) {
        String problem = thanksProblem(player, to);
        if (problem != null) {
            player.sendSystemMessage(Component.translatable(problem, ConnectionService.thanksDailyLimit()).withStyle(ChatFormatting.RED));
            return false;
        }
        MinecraftServer server = player.server;
        PlayerData data = PlayerDataCapability.get(player).orElseThrow();
        GuildData.Connection known = data.getGuild().connection(to);
        String toName = known == null ? "?" : known.name();
        String fromName = player.getGameProfile().getName();
        // 贈った人：回数は、CPを数えないとき（設定で止めた・定義がない）も数える（1日3枚の上限のため）
        int before = data.getConnection().actionsToday(ConnectionActions.THANKS_SEND);
        ConnectionService.award(player, ConnectionActions.THANKS_SEND, to);
        if (data.getConnection().actionsToday(ConnectionActions.THANKS_SEND) == before) {
            data.getConnection().countOnly(ConnectionActions.THANKS_SEND, to);
        }
        ConnectionService.awardLater(server, to, ConnectionActions.THANKS_RECEIVE, player.getUUID(), 1);
        ServerPlayer target = server.getPlayerList().getPlayer(to);
        if (target != null) {
            PlayerDataCapability.get(target).ifPresent(other -> {
                other.getGuild().addCounter(GuildCounters.THANKS_RECEIVED, 1);
                TitleService.check(target);
            });
            target.sendSystemMessage(Component.translatable("connection.amemaru.thanks.received", fromName).withStyle(ChatFormatting.LIGHT_PURPLE));
            target.level().playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
            CompoundTag note = new CompoundTag();
            note.putString("from", fromName);
            ModNetwork.sendTo(target, new ConnectionS2C("thanks", note));
        } else {
            CompoundTag pending = new CompoundTag();
            pending.putString("type", "counter");
            pending.putString("key", GuildCounters.THANKS_RECEIVED);
            pending.putLong("amount", 1);
            GuildState.get(server).addPending(to, pending);
        }
        player.sendSystemMessage(Component.translatable("connection.amemaru.thanks.sent", toName).withStyle(ChatFormatting.LIGHT_PURPLE));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
        data.markDirty(PlayerData.SYNC_CONNECTION | PlayerData.SYNC_GUILD);
        AmemaruMod.LOGGER.info("[amemaru] {} sent a thanks card to {} (day {})", fromName, toName, ProgressionData.get(server).day());
        return true;
    }
}
