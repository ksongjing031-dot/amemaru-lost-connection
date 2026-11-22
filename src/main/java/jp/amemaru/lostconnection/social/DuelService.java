package jp.amemaru.lostconnection.social;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.AfflictionService;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.guild.GuildData;
import jp.amemaru.lostconnection.item.ConsumableItem;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.SocialS2C;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.VitalsService;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.OpeningService;
import jp.amemaru.lostconnection.story.StoryEventManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 決闘（Phase 6 その4。11月22日に決定：ほかのプレイヤーに決闘を申し込み、相手が受けると1対1で戦う。街の中では戦わず
 * （街の中はPvP不可【確定】）、簡単な決闘場で戦う。ほかのプレイヤーは観戦できる。決闘の間は、武器や防具などの耐久は減らない）。
 *
 * <p>決めた細かいこと【補完】：</p>
 * <ul>
 *   <li>申し込みは街（地上）にいる人どうしで。塔の中・ダウン中・オープニング中・物語のイベント中は申し込めない</li>
 *   <li>受けると、二人とも決闘場（{@link DuelArena}）へ移り、HPとMPが満タンになる。3秒数えてから始め、制限時間は3分</li>
 *   <li>HPが0になる一撃を受けた方の負け（HPは1残る。ダウン・デスペナルティはない）。時間切れはHPの割合が多い方の勝ち。
 *       決闘場から出る・ログアウトするのは負け（降参もできる）</li>
 *   <li>決闘の間は、二人のほかの人からの攻撃・回復・強化が届かない。回復薬とマナ水は使えない（config で使えるようにもできる）</li>
 *   <li>終わったら、もとの場所に戻り、HPとMPも決闘の前に戻る。戦績（勝ち・負け・引き分け）と縁帳に残る</li>
 *   <li>観戦：決闘場の観客席へ移る（地上から）。決闘が終わるか「観戦をやめる」で、もとの場所に戻る</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class DuelService {
    /** 決闘の段階。 */
    public enum Phase {
        COUNTDOWN, FIGHT, END
    }

    /** 申し込み。 */
    static final class Challenge {
        UUID from;
        String fromName;
        UUID to;
        String toName;
        long until;
    }

    /** 戦う人。 */
    static final class Fighter {
        UUID uuid;
        String name;
    }

    /** 一つの決闘。 */
    public static final class Duel {
        int id;
        int arena;
        final Fighter[] fighters = new Fighter[2];
        Phase phase = Phase.COUNTDOWN;
        long phaseStart;
        long fightStart;
        @Nullable
        UUID winner;
        String why = "";
        final Set<UUID> spectators = new HashSet<>();

        public int id() {
            return id;
        }

        public Phase phase() {
            return phase;
        }

        @Nullable
        public UUID winner() {
            return winner;
        }

        public String why() {
            return why;
        }

        @Nullable
        UUID other(UUID uuid) {
            if (fighters[0].uuid.equals(uuid)) {
                return fighters[1].uuid;
            }
            if (fighters[1].uuid.equals(uuid)) {
                return fighters[0].uuid;
            }
            return null;
        }

        String name(UUID uuid) {
            for (Fighter fighter : fighters) {
                if (fighter.uuid.equals(uuid)) {
                    return fighter.name;
                }
            }
            return "?";
        }
    }

    /** 戻る場所（プレイヤーの永続データに残す。落ちても、次に入ったときに戻す）。 */
    static final String RETURN_KEY = "amemaru_duel_return";
    private static final int COUNTDOWN_TICKS = 60;
    private static final int END_TICKS = 80;

    private static final List<Challenge> CHALLENGES = new ArrayList<>();
    private static final Map<Integer, Duel> DUELS = new LinkedHashMap<>();
    private static final Map<UUID, Duel> BY_FIGHTER = new HashMap<>();
    private static final Map<UUID, Duel> BY_SPECTATOR = new HashMap<>();
    private static final Set<UUID> VIEWING = new HashSet<>();
    private static int nextId = 1;
    /** テストでは、決闘場へ移さずにその場で戦う。 */
    private static boolean testMode;
    private static final Map<UUID, ServerPlayer> TEST_PLAYERS = new HashMap<>();
    /** 決闘の間だけバニラの pvp を入れたときの、もとの値（入れていなければ null）。 */
    @Nullable
    private static Boolean vanillaPvp;

    private DuelService() {
    }

    // ================================================================ 調べる

    /** 決闘の相手どうしで、今戦っている（攻撃が届く）か。 */
    public static boolean areOpponents(ServerPlayer a, ServerPlayer b) {
        Duel duel = BY_FIGHTER.get(a.getUUID());
        return duel != null && duel.phase == Phase.FIGHT && b.getUUID().equals(duel.other(a.getUUID()));
    }

    /** 攻撃が届くか（{@link #areOpponents} と同じ）。 */
    public static boolean canHurt(ServerPlayer attacker, ServerPlayer victim) {
        return areOpponents(attacker, victim);
    }

    /** 決闘している（数えている間・終わって戻る前も含む）。ほかの人とは、攻撃も回復も届かない。 */
    public static boolean isolated(ServerPlayer player) {
        return BY_FIGHTER.containsKey(player.getUUID());
    }

    /** 決闘で戦っている最中か。 */
    public static boolean fighting(ServerPlayer player) {
        Duel duel = BY_FIGHTER.get(player.getUUID());
        return duel != null && duel.phase == Phase.FIGHT;
    }

    public static boolean spectating(ServerPlayer player) {
        return BY_SPECTATOR.containsKey(player.getUUID());
    }

    @Nullable
    public static Duel duelOf(ServerPlayer player) {
        Duel duel = BY_FIGHTER.get(player.getUUID());
        return duel != null ? duel : BY_SPECTATOR.get(player.getUUID());
    }

    /** 決闘を申し込める・受けられるか（だめなら理由の翻訳キー）。 */
    @Nullable
    static String unavailable(ServerPlayer player) {
        if (!ServerConfig.DUEL_ENABLED.get()) {
            return "message.amemaru.duel.disabled";
        }
        if (isolated(player) || spectating(player)) {
            return "message.amemaru.duel.busy";
        }
        if (!player.isAlive() || player.isSpectator() || DownHandler.isDown(player)) {
            return "message.amemaru.duel.not_ready";
        }
        if (!testMode && (player.level().dimension() != Level.OVERWORLD || OpeningService.inOpening(player)
                || StoryEventManager.isPlaying(player))) {
            return "message.amemaru.duel.not_in_town";
        }
        return null;
    }

    // ================================================================ 申し込む・答える

    public static boolean challenge(ServerPlayer from, ServerPlayer to) {
        if (from == to || from.getUUID().equals(to.getUUID())) {
            tell(from, "message.amemaru.duel.self");
            return false;
        }
        String mine = unavailable(from);
        if (mine != null) {
            tell(from, mine);
            return false;
        }
        String theirs = unavailable(to);
        if (theirs != null) {
            tell(from, "message.amemaru.duel.target_busy", to.getDisplayName());
            return false;
        }
        long now = from.server.getTickCount();
        prune(now);
        for (Challenge c : CHALLENGES) {
            if (c.from.equals(from.getUUID()) && c.to.equals(to.getUUID())) {
                tell(from, "message.amemaru.duel.already", to.getDisplayName());
                return false;
            }
        }
        Challenge c = new Challenge();
        c.from = from.getUUID();
        c.fromName = from.getGameProfile().getName();
        c.to = to.getUUID();
        c.toName = to.getGameProfile().getName();
        int seconds = ServerConfig.DUEL_CHALLENGE_SECONDS.get();
        c.until = now + seconds * 20L;
        CHALLENGES.add(c);
        from.sendSystemMessage(Component.translatable("message.amemaru.duel.sent", to.getDisplayName(), seconds).withStyle(ChatFormatting.AQUA));
        MutableComponent message = Component.translatable("message.amemaru.duel.received", from.getDisplayName()).withStyle(ChatFormatting.GOLD)
                .append(" ")
                .append(button("message.amemaru.duel.button_accept", "/amemaru duel accept " + c.fromName, ChatFormatting.GREEN))
                .append(" ")
                .append(button("message.amemaru.duel.button_decline", "/amemaru duel decline " + c.fromName, ChatFormatting.GRAY));
        to.sendSystemMessage(message);
        to.level().playSound(null, to.getX(), to.getY(), to.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
        AmemaruMod.LOGGER.info("[amemaru][duel] {} challenged {}", c.fromName, c.toName);
        refreshViewers(from.server);
        return true;
    }

    /** 申し込みを受ける（{@code from} が null なら、いちばん新しい申し込み）。 */
    public static boolean accept(ServerPlayer to, @Nullable UUID from) {
        long now = to.server.getTickCount();
        prune(now);
        Challenge found = find(to.getUUID(), from);
        if (found == null) {
            tell(to, "message.amemaru.duel.no_challenge");
            return false;
        }
        ServerPlayer challenger = online(to.server, found.from);
        CHALLENGES.remove(found);
        if (challenger == null) {
            tell(to, "message.amemaru.duel.gone");
            refreshViewers(to.server);
            return false;
        }
        String mine = unavailable(to);
        if (mine != null) {
            tell(to, mine);
            return false;
        }
        String theirs = unavailable(challenger);
        if (theirs != null) {
            tell(to, "message.amemaru.duel.target_busy", challenger.getDisplayName());
            return false;
        }
        boolean started = start(to.server, challenger, to);
        refreshViewers(to.server);
        return started;
    }

    public static boolean decline(ServerPlayer to, @Nullable UUID from) {
        Challenge found = find(to.getUUID(), from);
        if (found == null) {
            tell(to, "message.amemaru.duel.no_challenge");
            return false;
        }
        CHALLENGES.remove(found);
        to.sendSystemMessage(Component.translatable("message.amemaru.duel.declined", found.fromName).withStyle(ChatFormatting.GRAY));
        ServerPlayer challenger = online(to.server, found.from);
        if (challenger != null) {
            challenger.sendSystemMessage(Component.translatable("message.amemaru.duel.declined_by", to.getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
        }
        refreshViewers(to.server);
        return true;
    }

    @Nullable
    private static Challenge find(UUID to, @Nullable UUID from) {
        Challenge latest = null;
        for (Challenge c : CHALLENGES) {
            if (c.to.equals(to) && (from == null || c.from.equals(from)) && (latest == null || c.until > latest.until)) {
                latest = c;
            }
        }
        return latest;
    }

    private static void prune(long now) {
        CHALLENGES.removeIf(c -> c.until < now);
    }

    // ================================================================ 始める

    private static int freeArena() {
        int count = ServerConfig.DUEL_ARENAS.get();
        for (int i = 0; i < count; i++) {
            boolean used = false;
            for (Duel duel : DUELS.values()) {
                if (duel.arena == i) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return i;
            }
        }
        return -1;
    }

    /**
     * サーバーのバニラの pvp（server.properties の {@code pvp}）が切ってあると、プレイヤーどうしの攻撃はバニラが止めてしまい、
     * 決闘ができない。決闘があるあいだだけ入れ、決闘がなくなったら、もとに戻す【補完】。プレイヤーどうしの攻撃は、このModの
     * {@code allowPvp}（初期値 false）と決闘の決まり（{@code CombatService.pvpBlocked}）で止めるので、ほかの人どうしでは届かない。
     */
    static void updateVanillaPvp(MinecraftServer server) {
        if (!DUELS.isEmpty()) {
            if (vanillaPvp == null && !server.isPvpAllowed()) {
                vanillaPvp = Boolean.FALSE;
                server.setPvpAllowed(true);
                AmemaruMod.LOGGER.info("[amemaru][duel] vanilla pvp is off; turned it on while duels run (player attacks stay gated by allowPvp)");
            }
        } else if (vanillaPvp != null) {
            server.setPvpAllowed(vanillaPvp);
            vanillaPvp = null;
        }
    }

    /** 決闘を始める（二人を決闘場へ移し、HPとMPを満タンにして、3秒数える）。 */
    static boolean start(MinecraftServer server, ServerPlayer a, ServerPlayer b) {
        int arena = freeArena();
        if (arena < 0) {
            tell(a, "message.amemaru.duel.full");
            tell(b, "message.amemaru.duel.full");
            return false;
        }
        Duel duel = new Duel();
        duel.id = nextId++;
        duel.arena = arena;
        duel.phaseStart = server.getTickCount();
        ServerPlayer[] players = {a, b};
        if (!testMode) {
            DuelArena.ensureBuilt(server, arena);
        }
        for (int side = 0; side < 2; side++) {
            ServerPlayer player = players[side];
            Fighter fighter = new Fighter();
            fighter.uuid = player.getUUID();
            fighter.name = player.getGameProfile().getName();
            duel.fighters[side] = fighter;
            saveReturn(player, false);
            if (!testMode) {
                Vec3 spot = DuelArena.fighterSpot(arena, side);
                player.stopRiding();
                player.teleportTo(DuelArena.level(server), spot.x, spot.y, spot.z, DuelArena.fighterYaw(side), 0.0F);
            }
            PlayerDataCapability.get(player).ifPresent(data -> {
                data.setHp(data.getStats().maxHp());
                data.setMp(data.getStats().maxMp());
                data.getBuffs().clear();
                VitalsService.pushVanillaHealth(player, data);
            });
            AfflictionService.consumePoison(player);
            BY_FIGHTER.put(player.getUUID(), duel);
        }
        DUELS.put(duel.id, duel);
        updateVanillaPvp(server);
        CHALLENGES.removeIf(c -> c.from.equals(a.getUUID()) || c.to.equals(a.getUUID()) || c.from.equals(b.getUUID())
                || c.to.equals(b.getUUID()));
        for (ServerPlayer player : players) {
            player.sendSystemMessage(Component.translatable("message.amemaru.duel.start", duel.fighters[0].name, duel.fighters[1].name,
                    ServerConfig.DUEL_SECONDS.get()).withStyle(ChatFormatting.GOLD));
        }
        if (ServerConfig.DUEL_ANNOUNCE.get() && !testMode) {
            MutableComponent announce = Component.translatable("message.amemaru.duel.announce", duel.fighters[0].name, duel.fighters[1].name)
                    .withStyle(ChatFormatting.YELLOW).append(" ")
                    .append(button("message.amemaru.duel.button_watch", "/amemaru duel watch " + duel.id, ChatFormatting.AQUA));
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (other != a && other != b) {
                    other.sendSystemMessage(announce);
                }
            }
        }
        AmemaruMod.LOGGER.info("[amemaru][duel] #{} {} vs {} (arena {})", duel.id, duel.fighters[0].name, duel.fighters[1].name, arena);
        return true;
    }

    /** 戻る場所を残す（決闘する人はHPとMPも）。 */
    private static void saveReturn(ServerPlayer player, boolean spectator) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", player.level().dimension().location().toString());
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putFloat("yaw", player.getYRot());
        tag.putFloat("pitch", player.getXRot());
        tag.putBoolean("spectator", spectator);
        PlayerDataCapability.get(player).ifPresent(data -> {
            tag.putDouble("hp", data.getHp());
            tag.putDouble("mp", data.getMp());
        });
        player.getPersistentData().put(RETURN_KEY, tag);
    }

    /** もとの場所に戻す（決闘した人は、HPとMPも決闘の前に戻す）。 */
    static void restore(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(RETURN_KEY);
        player.getPersistentData().remove(RETURN_KEY);
        if (tag.isEmpty()) {
            return;
        }
        if (!testMode) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
            ServerLevel level = dim == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (level == null) {
                level = player.server.overworld();
            }
            player.teleportTo(level, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"));
        }
        if (!tag.getBoolean("spectator")) {
            PlayerDataCapability.get(player).ifPresent(data -> {
                double max = data.getStats().maxHp();
                data.setHp(Math.max(1.0, Math.min(max, tag.contains("hp") ? tag.getDouble("hp") : max)));
                data.setMp(Math.max(0.0, Math.min(data.getStats().maxMp(), tag.contains("mp") ? tag.getDouble("mp") : data.getMp())));
                data.getBuffs().clear();
                VitalsService.pushVanillaHealth(player, data);
            });
            AfflictionService.consumePoison(player);
        }
    }

    // ================================================================ 観戦

    public static boolean watch(ServerPlayer player, int id) {
        Duel duel = DUELS.get(id);
        if (duel == null || duel.phase == Phase.END) {
            tell(player, "message.amemaru.duel.no_duel");
            return false;
        }
        String reason = unavailable(player);
        if (reason != null) {
            tell(player, reason);
            return false;
        }
        saveReturn(player, true);
        Vec3 spot = DuelArena.spectatorSpot(duel.arena, duel.spectators.size());
        if (!testMode) {
            player.stopRiding();
            player.teleportTo(DuelArena.level(player.server), spot.x, spot.y, spot.z, DuelArena.spectatorYaw(duel.spectators.size()), 10.0F);
        }
        duel.spectators.add(player.getUUID());
        BY_SPECTATOR.put(player.getUUID(), duel);
        player.sendSystemMessage(Component.translatable("message.amemaru.duel.watching", duel.fighters[0].name, duel.fighters[1].name)
                .withStyle(ChatFormatting.AQUA).append(" ")
                .append(button("message.amemaru.duel.button_leave", "/amemaru duel leave", ChatFormatting.GRAY)));
        refreshViewers(player.server);
        return true;
    }

    /** 観戦をやめる・決闘を降参する。 */
    public static boolean leave(ServerPlayer player) {
        Duel watched = BY_SPECTATOR.remove(player.getUUID());
        if (watched != null) {
            watched.spectators.remove(player.getUUID());
            restore(player);
            refreshViewers(player.server);
            return true;
        }
        Duel duel = BY_FIGHTER.get(player.getUUID());
        if (duel != null && duel.phase != Phase.END) {
            end(player.server, duel, duel.other(player.getUUID()), "surrender");
            return true;
        }
        tell(player, "message.amemaru.duel.nothing");
        return false;
    }

    // ================================================================ 毎 tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DUELS.isEmpty() && CHALLENGES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        int before = CHALLENGES.size();
        prune(now);
        if (CHALLENGES.size() != before) {
            refreshViewers(server);
        }
        for (Duel duel : new ArrayList<>(DUELS.values())) {
            tick(server, duel, now);
        }
    }

    private static void tick(MinecraftServer server, Duel duel, long now) {
        long t = now - duel.phaseStart;
        switch (duel.phase) {
            case COUNTDOWN -> {
                if (t % 20 == 0 && t < COUNTDOWN_TICKS) {
                    title(server, duel, Component.literal(String.valueOf(3 - t / 20)).withStyle(ChatFormatting.GOLD), null);
                    sound(server, duel, 1.0F + t / 40.0F);
                }
                if (t >= COUNTDOWN_TICKS) {
                    duel.phase = Phase.FIGHT;
                    duel.phaseStart = now;
                    duel.fightStart = now;
                    title(server, duel, Component.translatable("message.amemaru.duel.go").withStyle(ChatFormatting.RED), null);
                    sound(server, duel, 2.0F);
                    hud(server, duel, now);
                    refreshViewers(server);
                }
            }
            case FIGHT -> {
                // 出た・いなくなった人は負け
                for (Fighter fighter : duel.fighters) {
                    ServerPlayer player = online(server, fighter.uuid);
                    if (player == null) {
                        end(server, duel, duel.other(fighter.uuid), "logout");
                        return;
                    }
                    if (!testMode && (player.level().dimension() != DuelArena.level(server).dimension()
                            || !DuelArena.insideRing(duel.arena, player.position()))) {
                        end(server, duel, duel.other(fighter.uuid), "left");
                        return;
                    }
                }
                if (now - duel.fightStart >= ServerConfig.DUEL_SECONDS.get() * 20L) {
                    endByTime(server, duel);
                    return;
                }
                if (t % 10 == 0) {
                    hud(server, duel, now);
                }
            }
            case END -> {
                if (t >= END_TICKS) {
                    finish(server, duel);
                }
            }
        }
    }

    // ================================================================ 終える

    /** 倒れるほどの一撃は、HPを1残して決闘を終える（{@code CombatService} から）。 */
    public static double clampLethal(ServerPlayer victim, PlayerData data, double damage) {
        Duel duel = BY_FIGHTER.get(victim.getUUID());
        if (duel == null || duel.phase != Phase.FIGHT || damage < data.getHp()) {
            return damage;
        }
        double clamped = Math.max(0.0, data.getHp() - 1.0);
        end(victim.server, duel, duel.other(victim.getUUID()), "knockout");
        return clamped;
    }

    /** 決闘している人が倒れそうになった（落下などの、決闘の一撃のほかのダメージ）。ダウンにせず、決闘を終える。 */
    public static boolean interceptDeath(ServerPlayer player) {
        Duel duel = BY_FIGHTER.get(player.getUUID());
        if (duel == null) {
            return false;
        }
        player.setHealth(Math.max(0.5F, player.getMaxHealth() * 0.05F));
        PlayerDataCapability.get(player).ifPresent(data -> {
            data.setHp(Math.max(1.0, data.getHp()));
            data.setExpectedVanillaHealth(player.getHealth());
        });
        if (duel.phase == Phase.FIGHT) {
            end(player.server, duel, duel.other(player.getUUID()), "knockout");
        }
        return true;
    }

    private static void endByTime(MinecraftServer server, Duel duel) {
        double[] ratio = new double[2];
        for (int i = 0; i < 2; i++) {
            ServerPlayer player = online(server, duel.fighters[i].uuid);
            PlayerData data = player == null ? null : PlayerDataCapability.get(player).orElse(null);
            ratio[i] = data == null ? 0.0 : data.getHp() / Math.max(1.0, data.getStats().maxHp());
        }
        UUID winner = Math.abs(ratio[0] - ratio[1]) < 1.0E-4 ? null : ratio[0] > ratio[1] ? duel.fighters[0].uuid : duel.fighters[1].uuid;
        end(server, duel, winner, "time");
    }

    /** 決闘の勝ち負けを決める（4秒たってから、もとの場所に戻す）。 */
    static void end(MinecraftServer server, Duel duel, @Nullable UUID winner, String why) {
        if (duel.phase == Phase.END) {
            return;
        }
        duel.phase = Phase.END;
        duel.phaseStart = server.getTickCount();
        duel.winner = winner;
        duel.why = why;
        int day = ProgressionData.currentDay(server);
        Component whyText = Component.translatable("message.amemaru.duel.why." + why);
        Component result = winner == null
                ? Component.translatable("message.amemaru.duel.result_draw", duel.fighters[0].name, duel.fighters[1].name, whyText)
                : Component.translatable("message.amemaru.duel.result", duel.name(winner), duel.name(duel.other(winner)), whyText);
        for (Fighter fighter : duel.fighters) {
            ServerPlayer player = online(server, fighter.uuid);
            String key = winner == null ? "draws" : winner.equals(fighter.uuid) ? "wins" : "losses";
            if (player != null) {
                PlayerDataCapability.get(player).ifPresent(data -> {
                    data.getGuild().addCounter("duel_" + key, 1);
                    UUID other = duel.other(fighter.uuid);
                    if (other != null) {
                        data.getGuild().addConnection(other, duel.name(other), GuildData.ConnectionKind.DUEL, day);
                    }
                });
                player.sendSystemMessage(result.copy().withStyle(ChatFormatting.GOLD));
                Component big = Component.translatable(winner == null ? "message.amemaru.duel.title_draw"
                        : winner.equals(fighter.uuid) ? "message.amemaru.duel.title_win" : "message.amemaru.duel.title_lose")
                        .withStyle(winner == null ? ChatFormatting.YELLOW : winner.equals(fighter.uuid) ? ChatFormatting.GOLD : ChatFormatting.GRAY);
                sendTitle(player, big, whyText);
            } else {
                CompoundTag pending = new CompoundTag();
                pending.putString("type", "counter");
                pending.putString("key", "duel_" + key);
                pending.putLong("amount", 1);
                jp.amemaru.lostconnection.guild.GuildState.get(server).addPending(fighter.uuid, pending);
                UUID other = duel.other(fighter.uuid);
                if (other != null) {
                    CompoundTag connection = new CompoundTag();
                    connection.putString("type", "connection");
                    connection.putUUID("with", other);
                    connection.putString("name", duel.name(other));
                    connection.putString("kind", GuildData.ConnectionKind.DUEL.name());
                    connection.putInt("day", day);
                    jp.amemaru.lostconnection.guild.GuildState.get(server).addPending(fighter.uuid, connection);
                }
            }
        }
        for (UUID uuid : duel.spectators) {
            ServerPlayer player = online(server, uuid);
            if (player != null) {
                player.sendSystemMessage(result.copy().withStyle(ChatFormatting.GOLD));
                sendTitle(player, Component.translatable(winner == null ? "message.amemaru.duel.title_draw" : "message.amemaru.duel.title_winner",
                        winner == null ? "" : duel.name(winner)).withStyle(ChatFormatting.GOLD), whyText);
            }
        }
        sound(server, duel, 0.8F);
        hud(server, duel, server.getTickCount());
        AmemaruMod.LOGGER.info("[amemaru][duel] #{} ended: winner {} ({})", duel.id, winner == null ? "draw" : duel.name(winner), why);
        refreshViewers(server);
    }

    /** 戻す：二人と観客を、もとの場所へ。 */
    static void finish(MinecraftServer server, Duel duel) {
        DUELS.remove(duel.id);
        updateVanillaPvp(server);
        for (Fighter fighter : duel.fighters) {
            BY_FIGHTER.remove(fighter.uuid);
            ServerPlayer player = online(server, fighter.uuid);
            if (player != null) {
                restore(player);
                ModNetwork.sendTo(player, new SocialS2C("duel_hud", new CompoundTag()));
            }
        }
        for (UUID uuid : duel.spectators) {
            BY_SPECTATOR.remove(uuid);
            ServerPlayer player = online(server, uuid);
            if (player != null) {
                restore(player);
                ModNetwork.sendTo(player, new SocialS2C("duel_hud", new CompoundTag()));
            }
        }
        refreshViewers(server);
    }

    // ================================================================ 入る・出る

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && player.getPersistentData().contains(RETURN_KEY) && !isolated(player) && !spectating(player)) {
            // 決闘・観戦の途中で抜けた：もとの場所へ戻す
            restore(player);
            player.sendSystemMessage(Component.translatable("message.amemaru.duel.returned").withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        VIEWING.remove(uuid);
        CHALLENGES.removeIf(c -> c.from.equals(uuid) || c.to.equals(uuid));
        Duel watched = BY_SPECTATOR.remove(uuid);
        if (watched != null) {
            watched.spectators.remove(uuid);
            restore(player);
        }
        Duel duel = BY_FIGHTER.get(uuid);
        if (duel != null) {
            if (duel.phase != Phase.END) {
                end(player.server, duel, duel.other(uuid), "logout");
            }
            // 抜けた人は、ここで戻しておく（入り直したときに決闘場にいないように）
            BY_FIGHTER.remove(uuid);
            restore(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        vanillaPvp = null;
        CHALLENGES.clear();
        DUELS.clear();
        BY_FIGHTER.clear();
        BY_SPECTATOR.clear();
        VIEWING.clear();
        DuelArena.forgetBuilt();
        testMode = false;
        TEST_PLAYERS.clear();
    }

    /** 決闘の間は、回復薬とマナ水を使えない（config で使えるようにもできる）。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && isolated(player) && !ServerConfig.DUEL_ALLOW_POTIONS.get()
                && event.getItemStack().getItem() instanceof ConsumableItem) {
            event.setCanceled(true);
            tell(player, "message.amemaru.duel.no_items");
        }
    }

    // ================================================================ 送る

    /** 決闘の画面（申し込み・観戦できる決闘・戦績・申し込める人）を送る。 */
    public static void sendState(ServerPlayer player, boolean openScreen) {
        MinecraftServer server = player.server;
        prune(server.getTickCount());
        CompoundTag tag = new CompoundTag();
        ListTag incoming = new ListTag();
        ListTag outgoing = new ListTag();
        for (Challenge c : CHALLENGES) {
            if (c.to.equals(player.getUUID())) {
                CompoundTag one = new CompoundTag();
                one.putString("name", c.fromName);
                one.putLong("left", Math.max(0, c.until - server.getTickCount()));
                incoming.add(one);
            } else if (c.from.equals(player.getUUID())) {
                CompoundTag one = new CompoundTag();
                one.putString("name", c.toName);
                one.putLong("left", Math.max(0, c.until - server.getTickCount()));
                outgoing.add(one);
            }
        }
        tag.put("incoming", incoming);
        tag.put("outgoing", outgoing);
        ListTag duels = new ListTag();
        for (Duel duel : DUELS.values()) {
            CompoundTag one = new CompoundTag();
            one.putInt("id", duel.id);
            one.putString("a", duel.fighters[0].name);
            one.putString("b", duel.fighters[1].name);
            one.putString("phase", duel.phase.name());
            one.putLong("left", duel.phase == Phase.FIGHT
                    ? Math.max(0, ServerConfig.DUEL_SECONDS.get() * 20L - (server.getTickCount() - duel.fightStart)) : 0);
            one.putInt("watchers", duel.spectators.size());
            duels.add(one);
        }
        tag.put("duels", duels);
        ListTag people = new ListTag();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player) {
                continue;
            }
            CompoundTag one = new CompoundTag();
            one.putString("name", other.getGameProfile().getName());
            String reason = unavailable(other);
            one.putBoolean("busy", reason != null);
            one.putBoolean("fighting", isolated(other));
            people.add(one);
        }
        tag.put("people", people);
        PlayerDataCapability.get(player).ifPresent(data -> {
            tag.putLong("wins", data.getGuild().counter("duel_wins"));
            tag.putLong("losses", data.getGuild().counter("duel_losses"));
            tag.putLong("draws", data.getGuild().counter("duel_draws"));
        });
        tag.putBoolean("spectating", spectating(player));
        tag.putBoolean("fighting", isolated(player));
        String mine = unavailable(player);
        tag.putString("unavailable", mine == null || isolated(player) || spectating(player) ? "" : mine);
        tag.putInt("seconds", ServerConfig.DUEL_SECONDS.get());
        tag.putBoolean("enabled", ServerConfig.DUEL_ENABLED.get());
        tag.putBoolean("screen", openScreen);
        if (openScreen) {
            VIEWING.add(player.getUUID());
        }
        ModNetwork.sendTo(player, new SocialS2C("duel", tag));
    }

    public static void close(ServerPlayer player) {
        VIEWING.remove(player.getUUID());
    }

    static void refreshViewers(MinecraftServer server) {
        for (UUID uuid : new ArrayList<>(VIEWING)) {
            ServerPlayer player = online(server, uuid);
            if (player == null) {
                VIEWING.remove(uuid);
            } else {
                sendState(player, false);
            }
        }
    }

    /** 決闘の間の表示（残り時間と二人のHPの割合）を、二人と観客に送る。 */
    private static void hud(MinecraftServer server, Duel duel, long now) {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", duel.phase.name());
        tag.putLong("left", duel.phase == Phase.FIGHT
                ? Math.max(0, ServerConfig.DUEL_SECONDS.get() * 20L - (now - duel.fightStart)) : 0);
        ListTag fighters = new ListTag();
        for (Fighter fighter : duel.fighters) {
            CompoundTag one = new CompoundTag();
            one.putString("name", fighter.name);
            ServerPlayer player = online(server, fighter.uuid);
            PlayerData data = player == null ? null : PlayerDataCapability.get(player).orElse(null);
            one.putDouble("ratio", data == null ? 0.0 : data.getHp() / Math.max(1.0, data.getStats().maxHp()));
            one.putInt("entity", player == null ? -1 : player.getId());
            one.putBoolean("winner", fighter.uuid.equals(duel.winner));
            fighters.add(one);
        }
        tag.put("fighters", fighters);
        for (Fighter fighter : duel.fighters) {
            ServerPlayer player = online(server, fighter.uuid);
            if (player != null) {
                CompoundTag mine = tag.copy();
                mine.putString("me", fighter.name);
                ModNetwork.sendTo(player, new SocialS2C("duel_hud", mine));
            }
        }
        for (UUID uuid : duel.spectators) {
            ServerPlayer player = online(server, uuid);
            if (player != null) {
                ModNetwork.sendTo(player, new SocialS2C("duel_hud", tag));
            }
        }
    }

    private static void title(MinecraftServer server, Duel duel, Component title, @Nullable Component subtitle) {
        for (UUID uuid : everyone(duel)) {
            ServerPlayer player = online(server, uuid);
            if (player != null) {
                sendTitle(player, title, subtitle);
            }
        }
    }

    private static void sendTitle(ServerPlayer player, Component title, @Nullable Component subtitle) {
        if (player instanceof FakePlayer) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(2, 16, 4));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        if (subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    private static void sound(MinecraftServer server, Duel duel, float pitch) {
        for (UUID uuid : everyone(duel)) {
            ServerPlayer player = online(server, uuid);
            if (player != null && !(player instanceof FakePlayer)) {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, pitch);
            }
        }
    }

    private static List<UUID> everyone(Duel duel) {
        List<UUID> list = new ArrayList<>();
        for (Fighter fighter : duel.fighters) {
            list.add(fighter.uuid);
        }
        list.addAll(duel.spectators);
        return list;
    }

    private static Component button(String key, String command, ChatFormatting color) {
        return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
    }

    // ================================================================ 管理者・テスト

    /** 管理者：今の決闘の一覧の文。 */
    public static List<Component> describe() {
        List<Component> lines = new ArrayList<>();
        for (Duel duel : DUELS.values()) {
            lines.add(Component.literal("#" + duel.id + " " + duel.fighters[0].name + " vs " + duel.fighters[1].name + " " + duel.phase
                    + " arena " + duel.arena + " watchers " + duel.spectators.size()));
        }
        return lines;
    }

    /** 管理者：決闘を止める（引き分け）。 */
    public static boolean adminStop(MinecraftServer server, int id) {
        Duel duel = DUELS.get(id);
        if (duel == null) {
            return false;
        }
        if (duel.phase != Phase.END) {
            end(server, duel, null, "admin");
        }
        finish(server, duel);
        return true;
    }

    /** テスト用：決闘場へ移さず、街にいなくても申し込める。 */
    public static void setTestMode(boolean on) {
        testMode = on;
        if (!on) {
            TEST_PLAYERS.clear();
        }
    }

    /** テスト用：サーバーの一覧にいない偽のプレイヤーを、いる人として扱う。 */
    public static void testPlayers(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            TEST_PLAYERS.put(player.getUUID(), player);
        }
    }

    /** 入っている人（テストでは偽のプレイヤーも）。 */
    @Nullable
    private static ServerPlayer online(MinecraftServer server, @Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        return player != null ? player : TEST_PLAYERS.get(uuid);
    }

    /** テスト用：その人の決闘（なければ null）。 */
    @Nullable
    public static Duel duelForTest(ServerPlayer player) {
        return BY_FIGHTER.get(player.getUUID());
    }

    /** テスト用：決闘場へ移さずに、その場で決闘を始める（数える間を飛ばして、すぐ戦える）。 */
    public static Duel startForTest(ServerPlayer a, ServerPlayer b) {
        testMode = true;
        start(a.server, a, b);
        Duel duel = BY_FIGHTER.get(a.getUUID());
        duel.phase = Phase.FIGHT;
        duel.fightStart = a.server.getTickCount();
        duel.phaseStart = duel.fightStart;
        return duel;
    }

    /** テスト用：決闘を片づける。 */
    public static void finishForTest(MinecraftServer server, Duel duel) {
        finish(server, duel);
    }
}
