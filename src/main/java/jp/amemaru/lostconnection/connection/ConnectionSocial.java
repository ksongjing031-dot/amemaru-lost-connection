package jp.amemaru.lostconnection.connection;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.guild.GuildData;
import jp.amemaru.lostconnection.party.Party;
import jp.amemaru.lostconnection.party.PartyService;
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
import net.minecraftforge.common.util.FakePlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 交流の常設の仕組み（Phase 7 その1。Phase 0-A 15章「常設の仕組み」・14章「先導者」）。
 *
 * <ul>
 *   <li>はじめましてボーナス：初めて組む相手がいるパーティは、経験値とドロップの判定が、その人数×10%上がる（最大+30%、
 *       1日5人まで）。「初めて組む相手」は、その日に初めてパーティを組んだ人（縁帳に「組んだ」がなかった人）</li>
 *   <li>先導者：途中参加者（来てから3日のうち）や、サーバーの中央値より10レベル以上低い人と組むと、先導する側にCPと
 *       称号「先導者」の数。組まれた側には「初めての人とパーティ」のCPと、はじめましてボーナスが入る（14章の「双方に報酬と
 *       コネクション」【補完】）</li>
 *   <li>今日の相棒：毎日ログインしたときに、まだ組んだことのない人を1人「今日の相棒」として示す（いる人から先に。いなければ
 *       最近来た人から）。相手の相棒がまだ決まっていなければ、お互いを相棒にする。依頼は「相棒と同じパーティで敵を10体倒す」
 *       【補完】。終えると、ふたりにノットとCP（小さな報酬）</li>
 * </ul>
 */
public final class ConnectionSocial {
    private ConnectionSocial() {
    }

    // ================================================================ はじめましてボーナス・先導者

    /**
     * パーティを組んだ（{@link jp.amemaru.lostconnection.guild.GuildHooks#onPartyJoined}。縁帳に足す前）。初めて組む人どうしなら、
     * お互いの「今日初めて組んだ人」に足す（1日5人まで）。足したら知らせる。
     */
    public static void onFirstParty(ServerPlayer a, ServerPlayer b) {
        meet(a, b);
        meet(b, a);
    }

    private static void meet(ServerPlayer player, ServerPlayer other) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        ConnectionRecord record = data.getConnection();
        record.rollover(ConnectionService.today());
        if (record.addFirstMeet(other.getUUID(), ServerConfig.FIRST_MEET_DAILY.get())) {
            double bonus = firstMeetBonus(player);
            player.sendSystemMessage(Component.translatable("connection.amemaru.first_meet", other.getGameProfile().getName(),
                    formatPercent(bonus)).withStyle(ChatFormatting.LIGHT_PURPLE));
            data.markDirty(PlayerData.SYNC_CONNECTION);
        }
    }

    /**
     * はじめましてボーナス（%）：今のパーティにいる「今日初めて組んだ人」の数 × 10%（30%まで）。パーティにいなければ 0。
     */
    public static double firstMeetBonus(ServerPlayer player) {
        Party party = PartyService.partyOf(player);
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (party == null || data == null) {
            return 0.0;
        }
        ConnectionRecord record = data.getConnection();
        record.rollover(ConnectionService.today());
        int count = 0;
        for (UUID member : party.members()) {
            if (!member.equals(player.getUUID()) && record.isFirstMeet(member)) {
                count++;
            }
        }
        return Math.min(ServerConfig.FIRST_MEET_MAX.get(), count * ServerConfig.FIRST_MEET_STEP.get());
    }

    /**
     * 先導の相手（後輩）か：途中参加者（来てから3日のうち）か、サーバーの中央値より10レベル以上低い人（Phase 0-A 15章）。
     */
    public static boolean isJunior(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        return juniorBy(ConnectionService.isNewcomer(player), data.getLevel(), ProgressionData.get(player.server).medianLevel(),
                ServerConfig.LEAD_LEVEL_GAP.get());
    }

    /** 先導の相手かの決まり（途中参加者か、レベルが「中央値 − 差」以下）。 */
    public static boolean juniorBy(boolean newcomer, int level, int median, int gap) {
        return newcomer || level <= median - gap;
    }

    // ================================================================ 今日の相棒

    /**
     * 今日の相棒を決める（まだなら）。決まった（決まっていた）相棒。まだ組んだことのない人がいなければ null。
     *
     * @param tell 本人に知らせるか（入ったとき）
     */
    @Nullable
    public static UUID ensurePartner(ServerPlayer player, boolean tell) {
        if (!ServerConfig.PARTNER_ENABLED.get() || player instanceof FakePlayer) {
            return null;
        }
        MinecraftServer server = player.server;
        ConnectionData cd = ConnectionData.get(server);
        cd.rolloverPartners(ConnectionService.today());
        cd.rememberName(player.getUUID(), player.getGameProfile().getName());
        UUID current = cd.partnerOf(player.getUUID());
        if (current != null) {
            if (tell) {
                tellPartner(player, current);
            }
            return current;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return null;
        }
        // いる人（相棒がまだの人 → 決まっている人）、いなければ最近来た人（同じ順）
        List<UUID> onlineFree = new ArrayList<>();
        List<UUID> onlineTaken = new ArrayList<>();
        List<UUID> recentFree = new ArrayList<>();
        List<UUID> recentTaken = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        seen.add(player.getUUID());
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other instanceof FakePlayer || !seen.add(other.getUUID()) || partied(data, other.getUUID())) {
                continue;
            }
            cd.rememberName(other.getUUID(), other.getGameProfile().getName());
            (cd.partnerOf(other.getUUID()) == null ? onlineFree : onlineTaken).add(other.getUUID());
        }
        List<String> days = new ArrayList<>();
        for (int d = 0; d < ServerConfig.CONNECTION_ACTIVE_DAYS.get(); d++) {
            days.add(ConnectionService.daysBefore(d));
        }
        for (UUID other : cd.activeOn(days)) {
            if (!seen.add(other) || partied(data, other) || cd.nameOf(other) == null) {
                continue;
            }
            (cd.partnerOf(other) == null ? recentFree : recentTaken).add(other);
        }
        List<UUID> pool = !onlineFree.isEmpty() ? onlineFree : !onlineTaken.isEmpty() ? onlineTaken
                : !recentFree.isEmpty() ? recentFree : recentTaken;
        if (pool.isEmpty()) {
            if (tell) {
                player.sendSystemMessage(Component.translatable("connection.amemaru.partner.none").withStyle(ChatFormatting.GRAY));
            }
            return null;
        }
        UUID pick = pool.get(player.getRandom().nextInt(pool.size()));
        cd.setPartner(player.getUUID(), pick);
        if (cd.partnerOf(pick) == null) {
            // 相手の相棒もまだなら、お互いを相棒にする
            cd.setPartner(pick, player.getUUID());
            ServerPlayer other = server.getPlayerList().getPlayer(pick);
            if (other != null) {
                tellPartner(other, player.getUUID());
                PlayerDataCapability.get(other).ifPresent(d -> d.markDirty(PlayerData.SYNC_CONNECTION));
            }
        }
        data.markDirty(PlayerData.SYNC_CONNECTION);
        tellPartner(player, pick);
        AmemaruMod.LOGGER.info("[amemaru] Today's partner of {} is {}", player.getGameProfile().getName(), nameOf(server, pick));
        return pick;
    }

    /** 管理者のコマンド・自動テスト：今日の相棒を、ふたりお互いに決める（前の相棒は外す）。 */
    public static void adminSetPartners(MinecraftServer server, UUID a, String aName, UUID b, String bName) {
        ConnectionData cd = ConnectionData.get(server);
        cd.rolloverPartners(ConnectionService.today());
        cd.rememberName(a, aName);
        cd.rememberName(b, bName);
        cd.setPartner(a, b);
        cd.setPartner(b, a);
        for (UUID who : new UUID[]{a, b}) {
            ServerPlayer online = server.getPlayerList().getPlayer(who);
            if (online != null) {
                tellPartner(online, who.equals(a) ? b : a);
                PlayerDataCapability.get(online).ifPresent(d -> d.markDirty(PlayerData.SYNC_CONNECTION));
            }
        }
    }

    /** 縁帳に「組んだ」がある人か（相棒には選ばない）。 */
    private static boolean partied(PlayerData data, UUID other) {
        GuildData.Connection c = data.getGuild().connection(other);
        return c != null && c.party() > 0;
    }

    /** 相棒の名前（いる人はその名前、いなければ覚えている名前）。 */
    public static String nameOf(MinecraftServer server, UUID who) {
        ServerPlayer online = server.getPlayerList().getPlayer(who);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        String known = ConnectionData.get(server).nameOf(who);
        return known == null ? "?" : known;
    }

    private static void tellPartner(ServerPlayer player, UUID partner) {
        MinecraftServer server = player.server;
        ConnectionData cd = ConnectionData.get(server);
        String name = nameOf(server, partner);
        if (cd.pairDone(player.getUUID(), partner)) {
            player.sendSystemMessage(Component.translatable("connection.amemaru.partner.done_already", name).withStyle(ChatFormatting.GRAY));
            return;
        }
        boolean here = server.getPlayerList().getPlayer(partner) != null;
        player.sendSystemMessage(Component.translatable("connection.amemaru.partner.today", name, ServerConfig.PARTNER_GOAL.get(),
                ServerConfig.PARTNER_KNOTS.get()).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!here) {
            player.sendSystemMessage(Component.translatable("connection.amemaru.partner.away").withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * 敵が倒れた（{@link jp.amemaru.lostconnection.loot.DropService}。経験値を受け取った人たち）。相棒どうしが同じパーティで、
     * どちらも受け取ったら、ふたりの数を1つ進める（1体につき1組1回）。
     */
    public static void onEnemyKilled(List<ServerPlayer> receivers) {
        if (receivers.size() < 2 || !ServerConfig.PARTNER_ENABLED.get()) {
            return;
        }
        MinecraftServer server = receivers.get(0).server;
        ConnectionData cd = ConnectionData.get(server);
        cd.rolloverPartners(ConnectionService.today());
        Map<UUID, ServerPlayer> byId = new HashMap<>();
        for (ServerPlayer player : receivers) {
            byId.put(player.getUUID(), player);
        }
        Set<String> counted = new HashSet<>();
        int goal = ServerConfig.PARTNER_GOAL.get();
        for (ServerPlayer player : receivers) {
            UUID partnerId = cd.partnerOf(player.getUUID());
            ServerPlayer partner = partnerId == null ? null : byId.get(partnerId);
            if (partner == null || cd.pairDone(player.getUUID(), partnerId)) {
                continue;
            }
            Party party = PartyService.partyOf(player);
            if (party == null || !party.members().contains(partnerId)
                    || !counted.add(ConnectionData.pairKey(player.getUUID(), partnerId))) {
                continue;
            }
            int now = cd.addPairProgress(player.getUUID(), partnerId, 1);
            if (now >= goal) {
                complete(player, partner);
            } else {
                Component line = Component.translatable("connection.amemaru.partner.progress", now, goal).withStyle(ChatFormatting.LIGHT_PURPLE);
                player.displayClientMessage(line, true);
                partner.displayClientMessage(line, true);
                PlayerDataCapability.get(player).ifPresent(d -> d.markDirty(PlayerData.SYNC_CONNECTION));
                PlayerDataCapability.get(partner).ifPresent(d -> d.markDirty(PlayerData.SYNC_CONNECTION));
            }
        }
    }

    /** 今日の相棒の依頼を終えた：ふたりに、ノットとCP。 */
    static void complete(ServerPlayer a, ServerPlayer b) {
        ConnectionData cd = ConnectionData.get(a.server);
        if (cd.pairDone(a.getUUID(), b.getUUID())) {
            return;
        }
        cd.markPairDone(a.getUUID(), b.getUUID());
        reward(a, b);
        reward(b, a);
        AmemaruMod.LOGGER.info("[amemaru] {} and {} finished today's partner request", a.getGameProfile().getName(), b.getGameProfile().getName());
    }

    private static void reward(ServerPlayer player, ServerPlayer other) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        long knots = ServerConfig.PARTNER_KNOTS.get();
        if (knots > 0) {
            Wallet.give(player, data, knots, "today partner");
        }
        ConnectionService.award(player, ConnectionActions.TODAY_PARTNER, other.getUUID());
        player.sendSystemMessage(Component.translatable("connection.amemaru.partner.complete", other.getGameProfile().getName(), knots)
                .withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.5F);
        data.markDirty(PlayerData.SYNC_CONNECTION);
    }

    /** 画面に出す今日の相棒（名前・ふたりで倒した数・目標・終えたか）。決まっていなければ空。 */
    public static CompoundTag partnerTag(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        if (!ServerConfig.PARTNER_ENABLED.get()) {
            return tag;
        }
        ConnectionData cd = ConnectionData.get(player.server);
        cd.rolloverPartners(ConnectionService.today());
        UUID partner = cd.partnerOf(player.getUUID());
        if (partner == null) {
            return tag;
        }
        tag.putString("name", nameOf(player.server, partner));
        tag.putInt("progress", cd.pairProgress(player.getUUID(), partner));
        tag.putInt("goal", ServerConfig.PARTNER_GOAL.get());
        tag.putBoolean("done", cd.pairDone(player.getUUID(), partner));
        tag.putBoolean("online", player.server.getPlayerList().getPlayer(partner) != null);
        return tag;
    }

    private static String formatPercent(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
