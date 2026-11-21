package jp.amemaru.lostconnection.party;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.PartyStateS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * パーティの決まりと知らせ（Phase 4。Phase 0-B 5章 {@code PartyAction}・{@code PartyState}）。
 *
 * <ul>
 *   <li>6人まで（config の {@code party.maxMembers}）。パーティに入っていない人どうしなら、誘った人がリーダーの
 *       パーティができる。パーティに入っているときに誘えるのはリーダーだけ（権限）【補完】。</li>
 *   <li>誘いは60秒で消える（{@code party.inviteSeconds}）【補完】。チャットの［参加する］［断る］か、パーティの画面で答える。</li>
 *   <li>抜ける・外す（リーダー）・リーダーを渡す（リーダー）。リーダーが抜けたら、次に入った人がリーダー。
 *       1人になったら解散する。いない間（ログアウト中）もメンバーのまま。</li>
 *   <li>メンバーの名前・レベル・HPの割合・ダウンの様子は、パーティのメンバーにだけ送る（1秒ごと、変わったときはすぐ）。</li>
 * </ul>
 *
 * <p>本来は、ギルドの「結び紐」を交わして組む（Phase 0-A 7章【補完】）。ギルドの窓口はPhase 6で作るので、それまでは
 * パーティの画面（Uキー）と {@code /amemaru party} で組む。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class PartyService {
    /** すぐに送り直すパーティ */
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static int ticks;

    private PartyService() {
    }

    // ================================================================ 調べる

    @Nullable
    public static Party partyOf(ServerPlayer player) {
        return PartyManager.get(player.server).partyOf(player.getUUID());
    }

    /** 同じパーティか（同じ人なら、パーティに入っていれば true）。 */
    public static boolean sameParty(Player a, Player b) {
        if (!(a instanceof ServerPlayer sa) || !(b instanceof ServerPlayer)) {
            return false;
        }
        Party party = PartyManager.get(sa.server).partyOf(a.getUUID());
        return party != null && party.contains(b.getUUID());
    }

    /** パーティのメンバーで、いまいる人（本人も入る。パーティでなければ空）。 */
    public static List<ServerPlayer> onlineMembers(ServerPlayer player) {
        Party party = partyOf(player);
        List<ServerPlayer> list = new ArrayList<>();
        if (party == null) {
            return list;
        }
        for (UUID member : party.members()) {
            ServerPlayer online = player.server.getPlayerList().getPlayer(member);
            if (online != null) {
                list.add(online);
            }
        }
        return list;
    }

    // ================================================================ 誘う・答える

    public static void invite(ServerPlayer inviter, ServerPlayer target) {
        PartyManager manager = PartyManager.get(inviter.server);
        long now = now(inviter.server);
        Component name = target.getDisplayName();
        if (inviter == target || inviter.getUUID().equals(target.getUUID())) {
            tell(inviter, Component.translatable("message.amemaru.party.invite.self"), true);
            return;
        }
        Party mine = manager.partyOf(inviter.getUUID());
        if (mine != null && !mine.isLeader(inviter.getUUID())) {
            tell(inviter, Component.translatable("message.amemaru.party.invite.not_leader"), true);
            return;
        }
        Party theirs = manager.partyOf(target.getUUID());
        if (theirs != null) {
            tell(inviter, Component.translatable(theirs == mine ? "message.amemaru.party.invite.member"
                    : "message.amemaru.party.invite.in_party", name), true);
            return;
        }
        int max = ServerConfig.PARTY_MAX_MEMBERS.get();
        if (mine != null && mine.size() >= max) {
            tell(inviter, Component.translatable("message.amemaru.party.full", max), true);
            return;
        }
        if (manager.invite(target.getUUID(), inviter.getUUID(), now) != null) {
            tell(inviter, Component.translatable("message.amemaru.party.invite.already", name), true);
            return;
        }
        int seconds = ServerConfig.PARTY_INVITE_SECONDS.get();
        manager.invite(target.getUUID(), inviter.getUUID(), inviter.getGameProfile().getName(), now + seconds * 20L);
        tell(inviter, Component.translatable("message.amemaru.party.invite.sent", name, seconds), false);
        String inviterName = inviter.getGameProfile().getName();
        MutableComponent message = Component.translatable("message.amemaru.party.invite.received", inviter.getDisplayName())
                .withStyle(ChatFormatting.AQUA)
                .append(" ")
                .append(button("message.amemaru.party.invite.button_accept", "/amemaru party accept " + inviterName, ChatFormatting.GREEN))
                .append(" ")
                .append(button("message.amemaru.party.invite.button_decline", "/amemaru party decline " + inviterName, ChatFormatting.GRAY));
        target.sendSystemMessage(message);
        sendState(target);
        AmemaruMod.LOGGER.info("[amemaru][party] {} invited {}", inviterName, target.getGameProfile().getName());
    }

    private static Component button(String key, String command, ChatFormatting color) {
        return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    /** 誘いに答える（inviter が null なら、いちばん新しい誘い）。 */
    public static void accept(ServerPlayer target, @Nullable UUID inviter) {
        PartyManager manager = PartyManager.get(target.server);
        long now = now(target.server);
        PartyManager.Invite invite = pickInvite(manager, target, inviter, now);
        if (invite == null) {
            tell(target, Component.translatable("message.amemaru.party.accept.none"), true);
            return;
        }
        if (manager.partyOf(target.getUUID()) != null) {
            tell(target, Component.translatable("message.amemaru.party.accept.in_party"), true);
            return;
        }
        Party party = manager.partyOf(invite.inviter());
        if (party != null && !party.isLeader(invite.inviter())) {
            // 誘った後で、誘った人がほかのパーティに入った（リーダーではない）
            manager.removeInvite(target.getUUID(), invite.inviter());
            tell(target, Component.translatable("message.amemaru.party.accept.stale", invite.inviterName()), true);
            sendState(target);
            return;
        }
        int max = ServerConfig.PARTY_MAX_MEMBERS.get();
        if (party != null && party.size() >= max) {
            tell(target, Component.translatable("message.amemaru.party.full", max), true);
            return;
        }
        if (party == null) {
            party = manager.create(invite.inviter(), invite.inviterName());
        }
        manager.add(party, target.getUUID(), target.getGameProfile().getName());
        manager.clearInvitesFor(target.getUUID());
        Component name = target.getDisplayName();
        for (ServerPlayer member : online(target.server, party)) {
            if (member == target) {
                tell(member, Component.translatable("message.amemaru.party.joined_you", party.name(party.leader())), false);
            } else {
                tell(member, Component.translatable("message.amemaru.party.joined", name), false);
            }
        }
        AmemaruMod.LOGGER.info("[amemaru][party] {} joined {}", target.getGameProfile().getName(), party);
        syncNow(target.server, party);
    }

    public static void decline(ServerPlayer target, @Nullable UUID inviter) {
        PartyManager manager = PartyManager.get(target.server);
        PartyManager.Invite invite = pickInvite(manager, target, inviter, now(target.server));
        if (invite == null) {
            tell(target, Component.translatable("message.amemaru.party.accept.none"), true);
            return;
        }
        manager.removeInvite(target.getUUID(), invite.inviter());
        tell(target, Component.translatable("message.amemaru.party.decline.you", invite.inviterName()), false);
        ServerPlayer from = target.server.getPlayerList().getPlayer(invite.inviter());
        if (from != null) {
            tell(from, Component.translatable("message.amemaru.party.decline.inviter", target.getDisplayName()), false);
        }
        sendState(target);
    }

    @Nullable
    private static PartyManager.Invite pickInvite(PartyManager manager, ServerPlayer target, @Nullable UUID inviter, long now) {
        if (inviter != null) {
            return manager.invite(target.getUUID(), inviter, now);
        }
        List<PartyManager.Invite> list = manager.invitesFor(target.getUUID(), now);
        return list.isEmpty() ? null : list.get(0);
    }

    // ================================================================ 抜ける・外す・リーダー

    public static void leave(ServerPlayer player) {
        PartyManager manager = PartyManager.get(player.server);
        Party party = manager.partyOf(player.getUUID());
        if (party == null) {
            tell(player, Component.translatable("message.amemaru.party.none"), true);
            return;
        }
        UUID leaderBefore = party.leader();
        List<ServerPlayer> others = online(player.server, party);
        others.remove(player);
        boolean disbanded = manager.remove(party, player.getUUID());
        tell(player, Component.translatable("message.amemaru.party.left_you"), false);
        afterRemoval(player.server, party, others, player.getDisplayName(), "message.amemaru.party.left", leaderBefore, disbanded);
        sendState(player);
        AmemaruMod.LOGGER.info("[amemaru][party] {} left {}", player.getGameProfile().getName(), party);
    }

    public static void kick(ServerPlayer leader, UUID target) {
        PartyManager manager = PartyManager.get(leader.server);
        Party party = manager.partyOf(leader.getUUID());
        if (party == null) {
            tell(leader, Component.translatable("message.amemaru.party.none"), true);
            return;
        }
        if (!party.isLeader(leader.getUUID())) {
            tell(leader, Component.translatable("message.amemaru.party.kick.not_leader"), true);
            return;
        }
        if (target.equals(leader.getUUID())) {
            tell(leader, Component.translatable("message.amemaru.party.kick.self"), true);
            return;
        }
        if (!party.contains(target)) {
            tell(leader, Component.translatable("message.amemaru.party.not_member"), true);
            return;
        }
        Component name = Component.literal(party.name(target));
        ServerPlayer kicked = leader.server.getPlayerList().getPlayer(target);
        List<ServerPlayer> others = online(leader.server, party);
        if (kicked != null) {
            others.remove(kicked);
            name = kicked.getDisplayName();
        }
        boolean disbanded = manager.remove(party, target);
        if (kicked != null) {
            tell(kicked, Component.translatable("message.amemaru.party.kick.you"), true);
            sendState(kicked);
        }
        afterRemoval(leader.server, party, others, name, "message.amemaru.party.kick", party.leader(), disbanded);
        AmemaruMod.LOGGER.info("[amemaru][party] {} kicked {} from {}", leader.getGameProfile().getName(), name.getString(), party);
    }

    public static void promote(ServerPlayer leader, UUID target) {
        PartyManager manager = PartyManager.get(leader.server);
        Party party = manager.partyOf(leader.getUUID());
        if (party == null) {
            tell(leader, Component.translatable("message.amemaru.party.none"), true);
            return;
        }
        if (!party.isLeader(leader.getUUID())) {
            tell(leader, Component.translatable("message.amemaru.party.promote.not_leader"), true);
            return;
        }
        if (!party.contains(target) || target.equals(leader.getUUID())) {
            tell(leader, Component.translatable("message.amemaru.party.not_member"), true);
            return;
        }
        manager.setLeader(party, target);
        Component name = Component.literal(party.name(target));
        for (ServerPlayer member : online(leader.server, party)) {
            tell(member, Component.translatable("message.amemaru.party.leader", name), false);
        }
        syncNow(leader.server, party);
    }

    /** 抜けた・外したあと：残りのメンバーに知らせる（リーダーが変わった・解散した）。 */
    private static void afterRemoval(MinecraftServer server, Party party, List<ServerPlayer> others, Component name, String key,
                                     UUID leaderBefore, boolean disbanded) {
        for (ServerPlayer member : others) {
            tell(member, Component.translatable(key, name), false);
            if (disbanded) {
                tell(member, Component.translatable("message.amemaru.party.disbanded"), false);
            } else if (!party.leader().equals(leaderBefore)) {
                tell(member, Component.translatable("message.amemaru.party.leader", Component.literal(party.name(party.leader()))), false);
            }
        }
        if (disbanded) {
            for (ServerPlayer member : others) {
                sendState(member);
            }
        } else {
            syncNow(server, party);
        }
    }

    // ================================================================ 送る

    /** パーティの様子が変わった（HP・ダウンなど）：近いうちに送り直す。 */
    public static void markChanged(ServerPlayer player) {
        Party party = partyOf(player);
        if (party != null) {
            DIRTY.add(party.id());
        }
    }

    private static void syncNow(MinecraftServer server, Party party) {
        for (ServerPlayer member : online(server, party)) {
            sendState(member);
        }
    }

    /** その人の画面に、パーティの様子（メンバー・HP・ダウン）と届いている誘いを送る。 */
    public static void sendState(ServerPlayer player) {
        ModNetwork.sendTo(player, buildState(player));
    }

    public static PartyStateS2C buildState(ServerPlayer player) {
        MinecraftServer server = player.server;
        PartyManager manager = PartyManager.get(server);
        long now = now(server);
        List<PartyStateS2C.Member> members = new ArrayList<>();
        Party party = manager.partyOf(player.getUUID());
        if (party != null) {
            for (UUID id : party.members()) {
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                if (online == null) {
                    members.add(new PartyStateS2C.Member(id, party.name(id), party.isLeader(id), false, 0, 0.0F, false, 0, 0, false));
                    continue;
                }
                PlayerData data = PlayerDataCapability.get(online).orElse(null);
                int level = data == null ? 0 : data.getLevel();
                float hp = 0.0F;
                boolean down = false;
                int downLeft = 0;
                int downLength = 0;
                if (data != null && data.isStatsReady() && data.getStats().maxHp() > 0) {
                    hp = (float) Math.max(0.0, Math.min(1.0, data.getHp() / data.getStats().maxHp()));
                    down = data.down().isDown();
                    downLeft = data.down().ticksLeft(online.level().getGameTime());
                    downLength = data.down().length();
                }
                members.add(new PartyStateS2C.Member(id, online.getGameProfile().getName(), party.isLeader(id), true, level, hp, down,
                        downLeft, downLength, online.level() == player.level()));
            }
        }
        List<PartyStateS2C.Invite> invites = new ArrayList<>();
        for (PartyManager.Invite invite : manager.invitesFor(player.getUUID(), now)) {
            invites.add(new PartyStateS2C.Invite(invite.inviter(), invite.inviterName(), (int) Math.max(0, invite.expires() - now)));
        }
        return new PartyStateS2C(party != null, members, invites);
    }

    private static List<ServerPlayer> online(MinecraftServer server, Party party) {
        List<ServerPlayer> list = new ArrayList<>();
        for (UUID member : party.members()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                list.add(online);
            }
        }
        return list;
    }

    private static void tell(ServerPlayer player, Component message, boolean problem) {
        player.sendSystemMessage(message.copy().withStyle(problem ? ChatFormatting.RED : ChatFormatting.AQUA));
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ================================================================ 毎 tick・入る・出る

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        ticks++;
        PartyManager manager = PartyManager.get(server);
        if (ticks % 20 == 0) {
            // 消えた誘い：誘われた人の画面を直す
            for (Map.Entry<UUID, PartyManager.Invite> expired : manager.pruneInvites(now(server))) {
                ServerPlayer target = server.getPlayerList().getPlayer(expired.getKey());
                if (target != null) {
                    sendState(target);
                }
            }
            // HPなどは1秒ごとに送る
            for (Party party : manager.parties()) {
                syncNow(server, party);
            }
            DIRTY.clear();
        } else if (ticks % 5 == 0 && !DIRTY.isEmpty()) {
            for (UUID id : DIRTY) {
                Party party = manager.party(id);
                if (party != null) {
                    syncNow(server, party);
                }
            }
            DIRTY.clear();
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PartyManager manager = PartyManager.get(player.server);
            Party party = manager.partyOf(player.getUUID());
            if (party != null) {
                manager.remember(party, player.getUUID(), player.getGameProfile().getName());
                syncNow(player.server, party);
            } else {
                sendState(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Party party = partyOf(player);
            if (party != null) {
                DIRTY.add(party.id());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DIRTY.clear();
        ticks = 0;
    }
}
