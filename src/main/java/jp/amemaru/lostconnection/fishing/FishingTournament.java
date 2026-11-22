package jp.amemaru.lostconnection.fishing;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.connection.ConnectionActions;
import jp.amemaru.lostconnection.connection.ConnectionService;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.guild.GuildState;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.FishingS2C;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 釣り大会（Phase 7 その2。Phase 0-A 15章「夜の催し：釣り大会（3日目ほか）。制限時間内の大物・珍しい魚を競う。湖畔の釣り場の
 * 完成後は会場が広がる」・11章「夜の催しは、管理者が好きなときに始める。始める前に、全員の画面に予告とカウントダウンを出せる」）。
 *
 * <ul>
 *   <li>管理者が始める（{@code /amemaru admin fishing tournament start [分] [始まるまでの秒]}）。予告のあと、カウントダウンで始まる</li>
 *   <li>会場は釣り堀（湖畔の釣り場ができていれば湖も）。会場で、大会の間に釣った一匹ごとに点：★の倍率 ×（50 ＋ 50 × 大きさの割合）。
 *       人ごとに、いちばん点の高い一匹で順位を決める【補完】</li>
 *   <li>終わると、上位3人に賞品（ノットと留め糸。いない人には次に入ったときに）、1匹でも釣った人に「釣り大会に参加」のCP（20）。
 *       冒険日誌に優勝者を残す</li>
 * </ul>
 */
public final class FishingTournament {
    private static int lastCountdown = -1;
    private static boolean startAnnounced;
    private static long lastSync;

    private FishingTournament() {
    }

    /** 始める（予告とカウントダウンのあと）。始めたら true（もう開いていれば false）。 */
    public static boolean start(MinecraftServer server, int minutes, int countdownSeconds) {
        FishingData fd = FishingData.get(server);
        if (fd.tournamentOn()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long start = now + Math.max(0, countdownSeconds) * 1000L;
        long end = start + Math.max(1, minutes) * 60_000L;
        boolean lake = FishingService.lakeOpen(server);
        fd.startTournament(start, end, lake);
        lastCountdown = -1;
        startAnnounced = false;
        Component venue = Component.translatable(lake ? "fishing.amemaru.tournament.venue_lake" : "fishing.amemaru.tournament.venue_pond");
        broadcast(server, Component.translatable("fishing.amemaru.tournament.announce", fd.held(), venue, minutes, countdownSeconds)
                .withStyle(ChatFormatting.GOLD));
        titleAll(server, Component.translatable("fishing.amemaru.tournament.title", fd.held()),
                Component.translatable("fishing.amemaru.tournament.soon", countdownSeconds));
        syncAll(server);
        AmemaruMod.LOGGER.info("[amemaru] Fishing tournament #{} announced ({} min, starts in {} s, lake {})", fd.held(), minutes,
                countdownSeconds, lake);
        return true;
    }

    /** 大会の最中か（始まる前は false）。 */
    public static boolean running(MinecraftServer server) {
        FishingData fd = FishingData.get(server);
        long now = System.currentTimeMillis();
        return fd.tournamentOn() && now >= fd.startMillis() && now < fd.endMillis();
    }

    /** 一匹の点。 */
    public static long score(FishDef def, double size) {
        return Math.round(FishingRules.current().tournamentMultiplier(def.rarity()) * (50.0 + 50.0 * def.sizeRatio(size)));
    }

    /** 釣り上げた一匹（会場で、大会の間に釣ったものだけ数える）。 */
    static void onCatch(ServerPlayer player, FishDef def, double size, String spot) {
        MinecraftServer server = player.server;
        if (!running(server)) {
            return;
        }
        FishingData fd = FishingData.get(server);
        if (!("pond".equals(spot) || ("lake".equals(spot) && fd.lakeVenue()))) {
            player.displayClientMessage(Component.translatable("fishing.amemaru.tournament.outside").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long score = score(def, size);
        fd.score(player.getUUID(), player.getGameProfile().getName(), def.id().toString(), size, score);
        FishingData.Best best = fd.best(player.getUUID());
        player.displayClientMessage(Component.translatable("fishing.amemaru.tournament.scored", score, best == null ? score : best.score())
                .withStyle(ChatFormatting.GOLD), true);
        syncAll(server);
    }

    /** 1秒ごと（{@link FishingService#onServerTick}）：カウントダウン・始め・終わり・順位の同期。 */
    static void tick(MinecraftServer server) {
        FishingData fd = FishingData.get(server);
        if (!fd.tournamentOn()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < fd.startMillis()) {
            int left = (int) Math.ceil((fd.startMillis() - now) / 1000.0);
            if (left != lastCountdown && (left <= 5 || left == 10 || left % 30 == 0)) {
                lastCountdown = left;
                if (left <= 5) {
                    titleAll(server, Component.literal(String.valueOf(left)).withStyle(ChatFormatting.GOLD), Component.empty());
                } else {
                    broadcast(server, Component.translatable("fishing.amemaru.tournament.countdown", left).withStyle(ChatFormatting.YELLOW));
                }
            }
            return;
        }
        if (!startAnnounced) {
            startAnnounced = true;
            titleAll(server, Component.translatable("fishing.amemaru.tournament.go").withStyle(ChatFormatting.GOLD),
                    Component.translatable("fishing.amemaru.tournament.go_sub"));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
            }
            syncAll(server);
        }
        if (now >= fd.endMillis()) {
            finish(server, true);
            return;
        }
        if (now - lastSync >= 5000) {
            syncAll(server);
        }
    }

    /**
     * 終える。{@code award} なら順位と賞品と、参加のCP（いない人には、次に入ったときに）。管理者が取りやめたときは何も渡さない。
     */
    public static void finish(MinecraftServer server, boolean award) {
        FishingData fd = FishingData.get(server);
        if (!fd.tournamentOn()) {
            return;
        }
        int number = fd.held();
        List<FishingData.Best> ranking = fd.ranking();
        int day = ProgressionData.currentDay(server);
        if (award) {
            broadcast(server, Component.translatable("fishing.amemaru.tournament.end", number).withStyle(ChatFormatting.GOLD));
            if (ranking.isEmpty()) {
                broadcast(server, Component.translatable("fishing.amemaru.tournament.nobody").withStyle(ChatFormatting.GRAY));
            }
            List<FishingRules.Prize> prizes = FishingRules.current().prizes();
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                FishingData.Best b = ranking.get(i);
                broadcast(server, Component.translatable("fishing.amemaru.tournament.rank", i + 1, b.name(), b.score(), fishName(b.fish()),
                        String.format(Locale.ROOT, "%.1f", b.size())).withStyle(i == 0 ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
                if (i < prizes.size()) {
                    prize(server, b.who(), prizes.get(i));
                }
            }
            UUID eventKey = UUID.nameUUIDFromBytes(("amemaru-fishing-tournament:" + number).getBytes(StandardCharsets.UTF_8));
            for (FishingData.Best b : ranking) {
                if (b.catches() > 0) {
                    ConnectionService.awardLater(server, b.who(), ConnectionActions.EVENT_JOIN, eventKey, 1);
                }
            }
            if (!ranking.isEmpty()) {
                FishingData.Best w = ranking.get(0);
                GuildState.get(server).chronicle(day, System.currentTimeMillis(), "chronicle.amemaru.fishing_tournament",
                        List.of(String.valueOf(number), w.name(), fishName(w.fish()).getString(), String.format(Locale.ROOT, "%.1f", w.size())));
            }
            titleAll(server, Component.translatable("fishing.amemaru.tournament.end_title"), ranking.isEmpty() ? Component.empty()
                    : Component.translatable("fishing.amemaru.tournament.winner", ranking.get(0).name()));
        } else {
            broadcast(server, Component.translatable("fishing.amemaru.tournament.cancelled", number).withStyle(ChatFormatting.GRAY));
        }
        fd.endTournament();
        syncAll(server);
        AmemaruMod.LOGGER.info("[amemaru] Fishing tournament #{} finished ({} anglers, award {})", number, ranking.size(), award);
    }

    private static Component fishName(String fishId) {
        FishDef def = FishDefs.get(ResourceLocation.tryParse(fishId));
        if (def == null) {
            return Component.literal(fishId);
        }
        Item item = FishingService.itemOf(def);
        return item == null ? Component.literal(def.name()) : new ItemStack(item).getHoverName();
    }

    /** 賞品を渡す（いなければ、次に入ったときに）。 */
    private static void prize(MinecraftServer server, UUID who, FishingRules.Prize prize) {
        ServerPlayer player = server.getPlayerList().getPlayer(who);
        if (player == null) {
            FishingData.get(server).addPrize(who, prize.knots(), prize.threads());
            return;
        }
        givePrize(player, prize.knots(), prize.threads());
    }

    private static void givePrize(ServerPlayer player, long knots, long threads) {
        PlayerDataCapability.get(player).ifPresent(data -> Wallet.give(player, data, knots, "fishing tournament"));
        if (threads > 0) {
            Item thread = BuiltInRegistries.ITEM.get(AmemaruMod.id("binding_thread"));
            ItemStack stack = new ItemStack(thread, (int) Math.min(thread.getMaxStackSize(), threads));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.sendSystemMessage(Component.translatable("fishing.amemaru.tournament.prize", knots, threads).withStyle(ChatFormatting.GOLD));
    }

    /** 入ったとき：預かっていた賞品を渡し、大会の最中なら順位を送る。 */
    static void onLoggedIn(ServerPlayer player) {
        for (long[] p : FishingData.get(player.server).takePrizes(player.getUUID())) {
            givePrize(player, p[0], p[1]);
        }
        if (FishingData.get(player.server).tournamentOn()) {
            ModNetwork.sendTo(player, new FishingS2C("tournament", stateTag(player.server, player)));
        }
    }

    /** 画面に出す大会のようす（残り時間・上位3人・自分の順位）。 */
    public static CompoundTag stateTag(MinecraftServer server, ServerPlayer viewer) {
        FishingData fd = FishingData.get(server);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("on", fd.tournamentOn());
        tag.putInt("number", fd.held());
        if (!fd.tournamentOn()) {
            return tag;
        }
        long now = System.currentTimeMillis();
        tag.putLong("startIn", Math.max(0, fd.startMillis() - now));
        tag.putLong("endIn", Math.max(0, fd.endMillis() - now));
        tag.putBoolean("lake", fd.lakeVenue());
        List<FishingData.Best> ranking = fd.ranking();
        ListTag top = new ListTag();
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            top.add(bestTag(ranking.get(i), i + 1));
        }
        tag.put("top", top);
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).who().equals(viewer.getUUID())) {
                tag.put("mine", bestTag(ranking.get(i), i + 1));
                break;
            }
        }
        return tag;
    }

    private static CompoundTag bestTag(FishingData.Best b, int rank) {
        CompoundTag one = new CompoundTag();
        one.putInt("rank", rank);
        one.putString("name", b.name());
        one.putString("fish", fishName(b.fish()).getString());
        one.putDouble("size", b.size());
        one.putLong("score", b.score());
        one.putInt("catches", b.catches());
        return one;
    }

    private static void syncAll(MinecraftServer server) {
        lastSync = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetwork.sendTo(player, new FishingS2C("tournament", stateTag(server, player)));
        }
    }

    private static void broadcast(MinecraftServer server, Component line) {
        server.getPlayerList().broadcastSystemMessage(line, false);
    }

    private static void titleAll(MinecraftServer server, Component title, Component subtitle) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }
}
