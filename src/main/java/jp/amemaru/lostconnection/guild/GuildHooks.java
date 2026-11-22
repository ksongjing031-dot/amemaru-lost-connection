package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.connection.ConnectionActions;
import jp.amemaru.lostconnection.connection.ConnectionService;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * ほかの仕組み（戦闘・工房・フリマ・パーティ・ダウン・塔・所持金）から、ギルド（称号の数・クエストの目標・縁帳）へ知らせる所
 * （Phase 6 その3）。知らせる側は、ここの1行を呼ぶだけにする。
 */
public final class GuildHooks {
    private GuildHooks() {
    }

    private static int day(MinecraftServer server) {
        return ProgressionData.get(server).day();
    }

    /** 敵を倒した（倒した人と、近くにいたパーティの仲間）。 */
    public static void onKill(ServerPlayer player, ResourceLocation enemy, int floor) {
        QuestService.onKill(player, enemy, floor);
        TitleService.check(player);
        // Phase 7 その1：パーティ（2人以上）で敵を倒す（5体ごとに1CP）
        jp.amemaru.lostconnection.party.Party party = jp.amemaru.lostconnection.party.PartyService.partyOf(player);
        if (party != null && party.members().size() >= 2) {
            ConnectionService.award(player, ConnectionActions.PARTY_KILL, null, 1);
        }
    }

    /** 連携技が成立した（連携に加わった人。称号「連携の糸」の数）。 */
    public static void onLink(ServerLevel level, List<UUID> players) {
        for (UUID uuid : new HashSet<>(players)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            PlayerData data = player == null ? null : PlayerDataCapability.get(player).orElse(null);
            if (data != null) {
                data.getGuild().addCounter(GuildCounters.LINK_COMBOS, 1);
                TitleService.check(player);
                // Phase 7 その1：連携技の成立（1日30回まで）
                ConnectionService.award(player, ConnectionActions.LINK_COMBO);
            }
        }
    }

    /** 工房で装備を作った（称号「見習い職人」の数は、工房の記録から数える）。 */
    public static void onCrafted(ServerPlayer player) {
        TitleService.check(player);
    }

    /** 仲間を起こした（縁帳と称号「手を差し伸べる者」）。 */
    public static void onRevive(ServerPlayer reviver, ServerPlayer downed) {
        int day = day(reviver.server);
        PlayerDataCapability.get(reviver).ifPresent(data -> data.getGuild().addConnection(downed.getUUID(),
                downed.getGameProfile().getName(), GuildData.ConnectionKind.REVIVED, day));
        PlayerDataCapability.get(downed).ifPresent(data -> data.getGuild().addConnection(reviver.getUUID(),
                reviver.getGameProfile().getName(), GuildData.ConnectionKind.REVIVED_BY, day));
        TitleService.check(reviver);
        // Phase 7 その1：仲間の蘇生（同じ相手は1日3回）
        ConnectionService.award(reviver, ConnectionActions.REVIVE, downed.getUUID());
    }

    /** パーティに入った（縁帳：入った人と、もういた仲間のそれぞれ。称号「はじめまして」の系統）。 */
    public static void onPartyJoined(ServerPlayer joined, List<ServerPlayer> members) {
        int day = day(joined.server);
        PlayerData mine = PlayerDataCapability.get(joined).orElse(null);
        boolean joinedNew = jp.amemaru.lostconnection.connection.ConnectionSocial.isJunior(joined);
        for (ServerPlayer member : members) {
            if (member == joined || member.getUUID().equals(joined.getUUID())) {
                continue;
            }
            // Phase 7 その1：初めての人とパーティを組む（お互いに。1日5人まで）。縁帳に足す前に確かめる
            GuildData.Connection before = mine == null ? null : mine.getGuild().connection(member.getUUID());
            if (before == null || before.party() == 0) {
                ConnectionService.award(joined, ConnectionActions.FIRST_PARTY, member.getUUID());
                ConnectionService.award(member, ConnectionActions.FIRST_PARTY, joined.getUUID());
                // はじめましてボーナス（15章）：お互いの「今日初めて組んだ人」に足す
                jp.amemaru.lostconnection.connection.ConnectionSocial.onFirstParty(joined, member);
            }
            // 先導者（15章）：途中参加者・中央値より10レベル以上低い人と組んだ先導する側（1日3回）。称号の「先導」の数
            boolean memberNew = jp.amemaru.lostconnection.connection.ConnectionSocial.isJunior(member);
            if (joinedNew && !memberNew) {
                lead(member, joined);
            } else if (memberNew && !joinedNew) {
                lead(joined, member);
            }
            if (mine != null) {
                mine.getGuild().addConnection(member.getUUID(), member.getGameProfile().getName(), GuildData.ConnectionKind.PARTY, day);
            }
            PlayerDataCapability.get(member).ifPresent(data -> data.getGuild().addConnection(joined.getUUID(),
                    joined.getGameProfile().getName(), GuildData.ConnectionKind.PARTY, day));
            TitleService.check(member);
        }
        TitleService.check(joined);
    }

    /**
     * 途中参加者・中央値より10レベル以上低い人と組んだ（先導者のCPと、称号の「先導」の数）。同じ相手は1日1回【補完】。
     * CPが上限で0でも、先導の数は数える。
     */
    private static void lead(ServerPlayer leader, ServerPlayer newcomer) {
        PlayerData data = PlayerDataCapability.get(leader).orElse(null);
        if (data == null) {
            return;
        }
        jp.amemaru.lostconnection.connection.ConnectionRecord record = data.getConnection();
        record.rollover(ConnectionService.today());
        if (record.targetToday(ConnectionActions.NEWCOMER_PARTY, newcomer.getUUID()) > 0) {
            return;
        }
        int before = record.actionsToday(ConnectionActions.NEWCOMER_PARTY);
        ConnectionService.award(leader, ConnectionActions.NEWCOMER_PARTY, newcomer.getUUID());
        if (record.actionsToday(ConnectionActions.NEWCOMER_PARTY) == before) {
            record.countOnly(ConnectionActions.NEWCOMER_PARTY, newcomer.getUUID());
        }
        data.getGuild().addCounter(GuildCounters.LEADS, 1);
        TitleService.check(leader);
    }

    /**
     * フリーマーケットで取引が成立した（売り手・買い手それぞれ。称号「露店デビュー」の数と縁帳。限定称号「市場の開拓者」）。
     * 売り手がいないときは、次に入ったときに数える。
     */
    public static void onMarketTrade(MinecraftServer server, UUID buyer, String buyerName, UUID seller, String sellerName) {
        if (buyer.equals(seller)) {
            return;
        }
        int day = day(server);
        countTrade(server, buyer, seller, sellerName, day);
        countTrade(server, seller, buyer, buyerName, day);
        // Phase 7 その1：フリマの取引（売り手・買い手それぞれ。同じ相手とは1日3回。いない人には次に入ったときに）
        ConnectionService.awardLater(server, buyer, ConnectionActions.MARKET_TRADE, seller, 1);
        ConnectionService.awardLater(server, seller, ConnectionActions.MARKET_TRADE, buyer, 1);
        // 限定称号：フリマで最初の取引を成立させた2人（売り手と買い手）。Phase 6 その1 で約束した分
        TitleService.onFirst(server, "first_market_trade", List.of(seller, buyer), List.of(sellerName, buyerName));
    }

    private static void countTrade(MinecraftServer server, UUID who, UUID other, String otherName, int day) {
        ServerPlayer player = server.getPlayerList().getPlayer(who);
        PlayerData data = player == null ? null : PlayerDataCapability.get(player).orElse(null);
        if (data != null) {
            data.getGuild().addCounter(GuildCounters.TRADES, 1);
            data.getGuild().addConnection(other, otherName, GuildData.ConnectionKind.TRADE, day);
            TitleService.check(player);
            return;
        }
        CompoundTag pending = new CompoundTag();
        pending.putString("type", "trade");
        pending.putUUID("with", other);
        pending.putString("name", otherName);
        pending.putInt("day", day);
        GuildState.get(server).addPending(who, pending);
    }

    /** 人から人へノットを受け渡した（縁帳の「取引」。11月22日のノットの受け渡し）。 */
    public static void onKnotTransfer(ServerPlayer from, ServerPlayer to) {
        int day = day(from.server);
        PlayerDataCapability.get(from).ifPresent(data -> data.getGuild().addConnection(to.getUUID(), to.getGameProfile().getName(),
                GuildData.ConnectionKind.TRADE, day));
        PlayerDataCapability.get(to).ifPresent(data -> data.getGuild().addConnection(from.getUUID(), from.getGameProfile().getName(),
                GuildData.ConnectionKind.TRADE, day));
    }

    /** 露店に人が来た（露店の持ち主の称号「名物露店」。1日に1人1回）。 */
    public static void onStallVisit(ServerPlayer visitor, UUID owner) {
        if (visitor.getUUID().equals(owner)) {
            return;
        }
        int day = day(visitor.server);
        ServerPlayer player = visitor.server.getPlayerList().getPlayer(owner);
        PlayerData data = player == null ? null : PlayerDataCapability.get(player).orElse(null);
        if (data != null) {
            if (data.getGuild().countVisitor(day, visitor.getUUID())) {
                TitleService.check(player);
                // Phase 7 その1：露店への来客（1日に1人1回、10人ごとに5CP）
                ConnectionService.award(player, ConnectionActions.STALL_VISITORS, null, 1);
            }
            return;
        }
        CompoundTag pending = new CompoundTag();
        pending.putString("type", "visitor");
        pending.putUUID("who", visitor.getUUID());
        pending.putInt("day", day);
        GuildState.get(visitor.server).addPending(owner, pending);
    }

    /**
     * 階のボスが倒れた（クエスト・称号・冒険日誌。初撃破なら限定称号「最初の一歩」など）。
     *
     * @param firstKill サーバーで初めて倒したか
     */
    public static void onBossDefeated(List<ServerPlayer> winners, int floor, ResourceLocation boss, String bossName, String floorTitle,
                                      boolean firstKill) {
        if (winners.isEmpty()) {
            return;
        }
        MinecraftServer server = winners.get(0).server;
        QuestService.onBoss(winners, floor);
        // Phase 7 その1：階層ボス（パーティ）・章ボス（5・10・15・20階）。同じボスは1日1回
        UUID bossKey = UUID.nameUUIDFromBytes(("amemaru-boss:" + boss).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean chapterBoss = floor > 0 && floor % 5 == 0;
        for (ServerPlayer player : winners) {
            if (chapterBoss) {
                ConnectionService.award(player, ConnectionActions.CHAPTER_BOSS, bossKey);
            } else if (winners.size() >= 2 || jp.amemaru.lostconnection.party.PartyService.partyOf(player) != null) {
                ConnectionService.award(player, ConnectionActions.FLOOR_BOSS, bossKey);
            }
        }
        // 章ボス（5・10・15・20階）の「倒れぬ心」「最後の砦」は、ボス戦の間のダウンと受け止めた量を数える Phase 8 で渡す。
        // それまでは章ボスとして扱わない（数えていないのに、ダウンしなかったことにしないため）
        TitleService.onBossKilled(winners, boss, false, new HashSet<>(), null);
        if (firstKill) {
            List<String> names = new ArrayList<>();
            for (ServerPlayer player : winners) {
                names.add(player.getGameProfile().getName());
            }
            GuildState.get(server).chronicle(day(server), System.currentTimeMillis(), "chronicle.amemaru.first_kill",
                    List.of(String.join("・", names), floorTitle, bossName));
            TitleService.onFirst(server, "first_boss_floor" + floor, winners);
        }
    }

    /** 古代の宝箱を開けた（クエスト）。 */
    public static void onChestOpened(ServerPlayer player) {
        QuestService.onChest(player);
    }

    /** 石柱を起動した（クエスト）。 */
    public static void onPillarActivated(ServerPlayer player) {
        QuestService.onPillar(player);
    }

    /**
     * 所持金が変わった（隠し称号「ゼロからの一歩」：手持ちが0ノットになってから1万ノット稼ぐ）。
     * {@code earned} は、稼いだ（増えた）額。受け渡し・売上・宝箱・クエストの報酬などを含む。
     */
    public static void onKnotsChanged(ServerPlayer player, PlayerData data, long before, long after) {
        if (after <= 0) {
            data.getGuild().markZero();
            return;
        }
        if (after > before) {
            data.getGuild().addEarnedSinceZero(after - before);
            TitleService.check(player);
        }
    }

    /** 完璧なアクセサリーを作った（称号「完璧の結晶」と、限定称号「最初の完璧」）。 */
    public static void onPerfectAccessory(ServerPlayer player) {
        TitleService.onEvent(player, "perfect_accessory");
        TitleService.onFirst(player.server, "first_perfect_accessory", List.of(player));
    }

    /** 入ったとき：いない間に届いた知らせ（取引の数・露店の来客・限定称号）を渡す。 */
    public static void applyPending(ServerPlayer player, PlayerData data) {
        List<CompoundTag> list = GuildState.get(player.server).takePending(player.getUUID());
        for (CompoundTag event : list) {
            switch (event.getString("type")) {
                case "trade" -> {
                    data.getGuild().addCounter(GuildCounters.TRADES, 1);
                    if (event.hasUUID("with")) {
                        data.getGuild().addConnection(event.getUUID("with"), event.getString("name"), GuildData.ConnectionKind.TRADE,
                                event.getInt("day"));
                    }
                }
                case "visitor" -> {
                    if (event.hasUUID("who") && data.getGuild().countVisitor(event.getInt("day"), event.getUUID("who"))) {
                        // Phase 7 その1：いない間の露店の来客も、CPに数える
                        ConnectionService.award(player, ConnectionActions.STALL_VISITORS, null, 1);
                    }
                }
                case "title" -> {
                    ResourceLocation id = ResourceLocation.tryParse(event.getString("id"));
                    if (id != null) {
                        TitleService.grant(player, id, "pending");
                    }
                }
                // いない間に起きたできごと（依頼で完璧なアクセサリーが仕上がった など。Phase 6 その4）
                case "event" -> TitleService.onEvent(player, event.getString("event"));
                // 縁帳（依頼台の依頼・決闘。Phase 6 その4）
                case "connection" -> {
                    if (event.hasUUID("with")) {
                        GuildData.ConnectionKind kind = GuildData.ConnectionKind.byName(event.getString("kind"));
                        if (kind != null) {
                            data.getGuild().addConnection(event.getUUID("with"), event.getString("name"), kind, event.getInt("day"));
                        }
                    }
                }
                case "counter" -> data.getGuild().addCounter(event.getString("key"), Math.max(0L, event.getLong("amount")));
                default -> AmemaruMod.LOGGER.warn("[amemaru] Unknown pending guild event {}", event.getString("type"));
            }
        }
        if (!list.isEmpty()) {
            TitleService.check(player);
        }
    }
}
