package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.party.Party;
import jp.amemaru.lostconnection.party.PartyManager;
import jp.amemaru.lostconnection.party.PartyService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ギルドの掲示板の「パーティ募集」（Phase 0-A 7章「冒険者ギルド：パーティ募集」。Phase 6 その3）。
 *
 * <ul>
 *   <li>募集を出す：ひとこと（40字まで）・行く階・募集する最低レベル。1人1件。30分で消える【補完】</li>
 *   <li>申し込む：募集を出した人に知らせが届く。出した人が「迎える」と、そのままパーティに入る（申し込んだ時点で同意しているので、
 *       招待の受け直しはしない）【補完】</li>
 *   <li>パーティがいっぱいになる・出した人が抜ける・パーティを離れると消える</li>
 *   <li>サーバーを止めると消える（保存しない）</li>
 * </ul>
 */
public final class PartyBoard {
    public static final int TEXT_MAX = 40;
    /** 募集が残る時間（ゲーム内の tick。30分）。 */
    public static final long LIFETIME_TICKS = 20L * 60 * 30;
    public static final int APPLICANTS_MAX = 12;

    private static final class Post {
        final UUID owner;
        final String ownerName;
        final String text;
        final int floor;
        final int minLevel;
        final long created;
        final Set<UUID> applicants = new LinkedHashSet<>();
        final Map<UUID, String> applicantNames = new LinkedHashMap<>();

        Post(UUID owner, String ownerName, String text, int floor, int minLevel, long created) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.text = text;
            this.floor = floor;
            this.minLevel = minLevel;
            this.created = created;
        }
    }

    private static final Map<UUID, Post> POSTS = new LinkedHashMap<>();

    private PartyBoard() {
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** 古い募集・出した人がいない募集・いっぱいの募集を消す。 */
    public static void prune(MinecraftServer server) {
        long now = now(server);
        int max = ServerConfig.PARTY_MAX_MEMBERS.get();
        PartyManager manager = PartyManager.get(server);
        POSTS.values().removeIf(post -> {
            if (now - post.created > LIFETIME_TICKS || server.getPlayerList().getPlayer(post.owner) == null) {
                return true;
            }
            Party party = manager.partyOf(post.owner);
            return party != null && (!party.isLeader(post.owner) || party.size() >= max);
        });
    }

    /** 募集を出す（出していれば出し直す）。 */
    public static boolean post(ServerPlayer player, String text, int floor, int minLevel) {
        prune(player.server);
        Party party = PartyManager.get(player.server).partyOf(player.getUUID());
        if (party != null && !party.isLeader(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.amemaru.board.not_leader").withStyle(ChatFormatting.RED), false);
            return false;
        }
        String cleaned = TitleService.sanitize(text, TEXT_MAX);
        if (cleaned.isEmpty()) {
            cleaned = Component.translatable("guild.amemaru.board.default_text").getString();
        }
        POSTS.put(player.getUUID(), new Post(player.getUUID(), player.getGameProfile().getName(), mask(cleaned),
                Math.max(0, Math.min(20, floor)), Math.max(0, Math.min(200, minLevel)), now(player.server)));
        player.displayClientMessage(Component.translatable("message.amemaru.board.posted").withStyle(ChatFormatting.AQUA), false);
        return true;
    }

    /** NGワードを伏せる（露店の店名と同じ決まり。Phase 0-B 不正対策）。 */
    private static String mask(String text) {
        String result = text;
        for (String ng : ServerConfig.MARKET_NG_WORDS.get()) {
            if (ng != null && !ng.isBlank()) {
                result = result.replace(ng, "＊".repeat(ng.length()));
            }
        }
        return result;
    }

    public static boolean cancel(ServerPlayer player) {
        boolean removed = POSTS.remove(player.getUUID()) != null;
        if (removed) {
            player.displayClientMessage(Component.translatable("message.amemaru.board.cancelled").withStyle(ChatFormatting.GRAY), false);
        }
        return removed;
    }

    /** 申し込む。 */
    public static boolean apply(ServerPlayer player, UUID owner) {
        prune(player.server);
        Post post = POSTS.get(owner);
        if (post == null || owner.equals(player.getUUID())) {
            return false;
        }
        if (PartyManager.get(player.server).partyOf(player.getUUID()) != null) {
            player.displayClientMessage(Component.translatable("message.amemaru.board.in_party").withStyle(ChatFormatting.RED), false);
            return false;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data != null && data.getLevel() < post.minLevel) {
            player.displayClientMessage(Component.translatable("message.amemaru.board.level", post.minLevel).withStyle(ChatFormatting.RED),
                    false);
            return false;
        }
        if (post.applicants.size() >= APPLICANTS_MAX || !post.applicants.add(player.getUUID())) {
            return false;
        }
        post.applicantNames.put(player.getUUID(), player.getGameProfile().getName());
        player.displayClientMessage(Component.translatable("message.amemaru.board.applied", post.ownerName).withStyle(ChatFormatting.AQUA),
                false);
        ServerPlayer ownerPlayer = player.server.getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) {
            ownerPlayer.sendSystemMessage(Component.translatable("message.amemaru.board.application", player.getGameProfile().getName())
                    .withStyle(ChatFormatting.GREEN));
        }
        return true;
    }

    /** 申し込みに答える（迎えるならパーティに入れる）。 */
    public static boolean answer(ServerPlayer owner, UUID applicant, boolean accept) {
        Post post = POSTS.get(owner.getUUID());
        if (post == null || !post.applicants.remove(applicant)) {
            return false;
        }
        String name = post.applicantNames.remove(applicant);
        ServerPlayer target = owner.server.getPlayerList().getPlayer(applicant);
        if (!accept) {
            if (target != null) {
                target.displayClientMessage(Component.translatable("message.amemaru.board.declined", post.ownerName)
                        .withStyle(ChatFormatting.GRAY), false);
            }
            return true;
        }
        if (target == null) {
            owner.displayClientMessage(Component.translatable("message.amemaru.board.gone", name == null ? "?" : name)
                    .withStyle(ChatFormatting.GRAY), false);
            return false;
        }
        // 申し込んだ時点で同意しているので、誘ってそのまま入れる
        PartyService.invite(owner, target);
        PartyService.accept(target, owner.getUUID());
        prune(owner.server);
        return true;
    }

    /** 掲示板の中身（だれでも見られる。自分の募集には申し込んだ人も）。 */
    public static ListTag tag(ServerPlayer viewer) {
        prune(viewer.server);
        ListTag list = new ListTag();
        long now = now(viewer.server);
        PartyManager manager = PartyManager.get(viewer.server);
        int max = ServerConfig.PARTY_MAX_MEMBERS.get();
        for (Post post : new ArrayList<>(POSTS.values())) {
            CompoundTag one = new CompoundTag();
            one.putUUID("owner", post.owner);
            one.putString("name", post.ownerName);
            one.putString("text", post.text);
            one.putInt("floor", post.floor);
            one.putInt("minLevel", post.minLevel);
            Party party = manager.partyOf(post.owner);
            one.putInt("size", party == null ? 1 : party.size());
            one.putInt("max", max);
            one.putInt("minutes", (int) Math.max(0, (LIFETIME_TICKS - (now - post.created)) / (20 * 60)));
            one.putBoolean("mine", post.owner.equals(viewer.getUUID()));
            one.putBoolean("applied", post.applicants.contains(viewer.getUUID()));
            if (post.owner.equals(viewer.getUUID())) {
                ListTag applicants = new ListTag();
                for (UUID uuid : post.applicants) {
                    CompoundTag a = new CompoundTag();
                    a.putUUID("uuid", uuid);
                    a.putString("name", post.applicantNames.getOrDefault(uuid, "?"));
                    ServerPlayer p = viewer.server.getPlayerList().getPlayer(uuid);
                    PlayerData data = p == null ? null : PlayerDataCapability.get(p).orElse(null);
                    a.putInt("level", data == null ? 0 : data.getLevel());
                    applicants.add(a);
                }
                one.put("applicants", applicants);
            }
            list.add(one);
        }
        return list;
    }

    /** 抜けた人の募集と申し込みを消す。 */
    public static void onLogout(UUID player) {
        POSTS.remove(player);
        for (Post post : POSTS.values()) {
            post.applicants.remove(player);
            post.applicantNames.remove(player);
        }
    }

    public static List<UUID> owners() {
        return List.copyOf(POSTS.keySet());
    }

    /** サーバーが止まるとき（GameTest の後など）。 */
    public static void clear() {
        POSTS.clear();
    }
}
