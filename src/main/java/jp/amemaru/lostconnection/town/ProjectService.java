package jp.amemaru.lostconnection.town;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.connection.ConnectionActions;
import jp.amemaru.lostconnection.connection.ConnectionService;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.guild.GuildState;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.ProjectS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 寄付と共同事業（Phase 7 その4。Phase 0-A 7章「共同事業（寄付による施設の発展）【提案】：寄付（お金）と大結晶の輝き段階（コネクション量）の
 * 両方がそろうと完成します。寄付目標は参加人数に合わせて自動で増減します。寄付した人の名前は寄付台の銘板に刻まれ、エンディングの
 * スタッフロールにも出ます」。Phase 0-B 13章「CPがたまり、輝き段階と施設が変わる」）。
 *
 * <ul>
 *   <li>中央広場の南東の縁の「寄付の台座」を右クリックすると、寄付台の画面が開く（7つの共同事業・寄付の帯・必要な輝き段階・銘板）</li>
 *   <li>寄付は、その共同事業の「開始日の目安」の進行日から。寄付したノットは世界から消える（Phase 0-A 20章の回収先）。
 *       施設への寄付のCP（100ノットごとに1、1日50。13章）</li>
 *   <li>目標（25人基準 × 人数補正）と輝き段階の両方がそろうと「完成」。完成した次の朝（ゲームの時刻の6時。または次の進行日）に、
 *       物語の印が付き、育ったあとの形が街に重なる（11月20日に決定）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class ProjectService {
    /** 1回に寄付できる額の上限（押しまちがいを防ぐ）。 */
    public static final long MAX_ONCE = 1_000_000L;
    /** 寄付の台座（中央広場の真ん中から。tools/mapgen/build_city.py の「寄付の台座」。両端を含む）。 */
    public static final int[] STAND = {14, 1, 14, 16, 2, 16};
    private static final long OPEN_TICKS = 20L * 60 * 30;
    private static final double OPEN_RANGE = 16.0;

    private record Open(Vec3 at, long tick, boolean admin) {
    }

    private static final Map<UUID, Open> OPEN = new HashMap<>();

    /** 自動テスト：進行日と輝き段階を決める（null なら本物）。 */
    @Nullable
    private static Integer testDay;
    @Nullable
    private static Integer testStage;

    private ProjectService() {
    }

    /** 自動テスト：進行日と輝き段階を決める（null に戻すと本物）。 */
    public static void setForTest(@Nullable Integer day, @Nullable Integer stage) {
        testDay = day;
        testStage = stage;
    }

    static int day(MinecraftServer server) {
        return testDay != null ? testDay : ProgressionData.currentDay(server);
    }

    static int stage(MinecraftServer server) {
        return testStage != null ? testStage : ConnectionService.stage(server);
    }

    /**
     * 朝（ゲームの時刻の6時。時刻 0）を回ったか：1日の中の時刻（0〜23999）が前に見たときより小さくなった。
     * 時が進んで 24000 を越えたときも、管理者が {@code /time set day} で朝にしたときも、朝を回ったことになる。
     */
    public static boolean morningPassed(long lastTimeOfDay, long timeOfDay) {
        return lastTimeOfDay >= 0 && timeOfDay < lastTimeOfDay;
    }

    /** 前に見た1日の中の時刻（サーバーを開いてから。-1 ＝ まだ見ていない）。 */
    private static long lastTimeOfDay = -1;

    // ================================================================ 状態

    /** 今の目標（25人基準 × 人数補正。13章の人数補正と同じ）。 */
    public static long goal(MinecraftServer server, ProjectDef def) {
        return Math.max(1L, Math.round(def.goal() * ConnectionService.scale(server)));
    }

    /** 状態：locked（受付前）・open（受付中）・waiting（寄付は届いた。輝き段階を待つ）・complete（完成。次の朝に変わる）・built（変わった）。 */
    public static String status(MinecraftServer server, ProjectDef def) {
        TownData.Project p = TownData.get(server).project(def.id());
        if (p.built()) {
            return "built";
        }
        if (p.completed()) {
            return "complete";
        }
        if (day(server) < def.startDay()) {
            return "locked";
        }
        return p.donated() >= goal(server, def) ? "waiting" : "open";
    }

    /** 完成した（施設が変わった）共同事業の、育ったあとの形（街を建て直しても重ねる）。 */
    public static Set<String> builtOverlays(MinecraftServer server) {
        Set<String> out = new LinkedHashSet<>();
        TownData town = TownData.get(server);
        for (ProjectDef def : ProjectDefs.ordered()) {
            if (!def.overlay().isEmpty() && town.project(def.id()).built()) {
                out.add(def.overlay());
            }
        }
        return out;
    }

    /** 完成した共同事業が上げる「結晶の加護」の段（⑦。最終決戦は Phase 9）。 */
    public static int blessing(MinecraftServer server) {
        int sum = 0;
        TownData town = TownData.get(server);
        for (ProjectDef def : ProjectDefs.ordered()) {
            if (town.project(def.id()).built()) {
                sum += def.blessing();
            }
        }
        return sum;
    }

    // ================================================================ 寄付台

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
                || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        BlockPos o = CityCommands.builtOrigin(player.serverLevel()).orElse(null);
        if (o == null) {
            return;
        }
        BlockPos pos = event.getPos();
        int x = pos.getX() - o.getX();
        int y = pos.getY() - o.getY();
        int z = pos.getZ() - o.getZ();
        if (x < STAND[0] || x > STAND[3] || y < STAND[1] || y > STAND[4] || z < STAND[2] || z > STAND[5]) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            open(player, "", false);
        }
    }

    /** 寄付台の画面を開く（select：はじめに選ぶ共同事業。空なら受付中のいちばん前）。 */
    public static void open(ServerPlayer player, String select, boolean admin) {
        OPEN.put(player.getUUID(), new Open(player.position(), player.server.getTickCount(), admin));
        CompoundTag tag = screenTag(player);
        tag.putString("select", select);
        ModNetwork.sendTo(player, new ProjectS2C("screen", tag));
    }

    static boolean canUse(ServerPlayer player) {
        Open open = OPEN.get(player.getUUID());
        if (open == null || player.server.getTickCount() - open.tick() > OPEN_TICKS) {
            return false;
        }
        return open.admin() || player.position().distanceTo(open.at()) <= OPEN_RANGE;
    }

    static CompoundTag screenTag(ServerPlayer player) {
        MinecraftServer server = player.server;
        PlayerData data = PlayerDataCapability.get(player).orElseThrow();
        TownData town = TownData.get(server);
        CompoundTag tag = new CompoundTag();
        tag.putLong("knots", Wallet.balance(data));
        tag.putInt("stage", stage(server));
        tag.putInt("day", day(server));
        tag.putDouble("scale", ConnectionService.scale(server));
        ListTag list = new ListTag();
        for (ProjectDef def : ProjectDefs.ordered()) {
            TownData.Project p = town.project(def.id());
            CompoundTag one = new CompoundTag();
            one.putString("id", def.id().toString());
            one.putInt("order", def.order());
            one.putString("name", def.name());
            one.putString("short", def.shortName());
            one.putString("result", def.result());
            one.putString("description", def.description());
            one.putLong("goal", goal(server, def));
            one.putLong("baseGoal", def.goal());
            one.putLong("donated", p.donated());
            one.putInt("needStage", def.stage());
            one.putInt("startDay", def.startDay());
            one.putString("status", status(server, def));
            one.putLong("mine", p.donatedBy(player.getUUID()));
            one.putInt("donors", p.plate().size());
            one.putInt("completedDay", p.completedDay());
            one.putInt("builtDay", p.builtDay());
            ListTag plate = new ListTag();
            for (TownData.Donor d : p.plate()) {
                if (plate.size() >= 8) {
                    break;
                }
                CompoundTag dt = new CompoundTag();
                dt.putString("name", d.name());
                dt.putLong("amount", d.amount());
                plate.add(dt);
            }
            one.put("plate", plate);
            list.add(one);
        }
        tag.put("projects", list);
        return tag;
    }

    /** 画面の操作（{@link jp.amemaru.lostconnection.network.c2s.ProjectC2S}）。 */
    public static void onAction(ServerPlayer player, String action, CompoundTag args) {
        if (!canUse(player)) {
            player.displayClientMessage(Component.translatable("project.amemaru.far").withStyle(ChatFormatting.RED), true);
            return;
        }
        if ("donate".equals(action)) {
            ProjectDef def = ProjectDefs.get(net.minecraft.resources.ResourceLocation.tryParse(args.getString("id")));
            String error = def == null ? "project.amemaru.error.unknown" : donate(player, def, args.getLong("amount"));
            if (error != null) {
                player.displayClientMessage(Component.translatable(error).withStyle(ChatFormatting.RED), true);
            }
            CompoundTag tag = screenTag(player);
            tag.putBoolean("refresh", true);
            ModNetwork.sendTo(player, new ProjectS2C("screen", tag));
        }
    }

    /**
     * 寄付する（目標までの残りの額まで。Long.MAX_VALUE なら残りすべて）。うまくいかなければ、わけの翻訳キー。
     */
    @Nullable
    public static String donate(ServerPlayer player, ProjectDef def, long amount) {
        MinecraftServer server = player.server;
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || amount <= 0) {
            return "project.amemaru.error.amount";
        }
        String status = status(server, def);
        switch (status) {
            case "locked" -> {
                return "project.amemaru.error.locked";
            }
            case "waiting" -> {
                return "project.amemaru.error.reached";
            }
            case "complete", "built" -> {
                return "project.amemaru.error.done";
            }
            default -> {
            }
        }
        TownData town = TownData.get(server);
        long left = goal(server, def) - town.project(def.id()).donated();
        long pay = Math.min(Math.min(amount, MAX_ONCE), Math.min(left, Wallet.balance(data)));
        if (pay <= 0) {
            return "project.amemaru.error.no_knots";
        }
        if (!Wallet.pay(player, data, pay, "project " + def.id().getPath())) {
            return "project.amemaru.error.no_knots";
        }
        int day = day(server);
        town.donate(def.id(), player.getUUID(), player.getGameProfile().getName(), pay, day);
        player.displayClientMessage(Component.translatable("project.amemaru.thanks", def.shortName(), pay).withStyle(ChatFormatting.GOLD), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.1F);
        // 13章「施設への寄付：100ノットごとに1、1日50」
        ConnectionService.award(player, ConnectionActions.DONATION, null, (int) Math.min(Integer.MAX_VALUE, pay));
        check(server, def);
        return null;
    }

    /** 管理者・自動テスト：寄付があったことにする（ノットは減らさない）。 */
    public static void adminDonate(MinecraftServer server, ProjectDef def, UUID who, String name, long amount) {
        TownData.get(server).donate(def.id(), who, name, amount, day(server));
        check(server, def);
    }

    // ================================================================ 完成・施設が変わる

    /** 目標と輝き段階がそろったか確かめ、そろえば完成にする。 */
    public static void check(MinecraftServer server, ProjectDef def) {
        TownData town = TownData.get(server);
        TownData.Project p = town.project(def.id());
        if (p.completed() || day(server) < def.startDay()) {
            return;
        }
        boolean money = p.donated() >= goal(server, def);
        boolean stage = stage(server) >= def.stage();
        if (!money) {
            return;
        }
        if (!stage) {
            if (!WAITING_TOLD.contains(def.id().toString())) {
                WAITING_TOLD.add(def.id().toString());
                broadcast(server, Component.translatable("project.amemaru.waiting", def.shortName(), def.stage())
                        .withStyle(ChatFormatting.YELLOW));
            }
            return;
        }
        int day = day(server);
        town.complete(def.id(), day, gameDay(server));
        broadcast(server, Component.translatable("project.amemaru.completed", def.shortName()).withStyle(ChatFormatting.GOLD));
        GuildState.get(server).chronicle(day, System.currentTimeMillis(), "chronicle.amemaru.project_completed", List.of(def.shortName()));
        AmemaruMod.LOGGER.info("[amemaru][town] project {} completed on day {}", def.id(), day);
    }

    /** 管理者：輝き段階が足りなくても完成にする（次の朝に施設が変わる）。 */
    public static void forceComplete(MinecraftServer server, ProjectDef def) {
        TownData town = TownData.get(server);
        if (town.project(def.id()).completed()) {
            return;
        }
        int day = day(server);
        town.complete(def.id(), day, gameDay(server));
        broadcast(server, Component.translatable("project.amemaru.completed", def.shortName()).withStyle(ChatFormatting.GOLD));
        GuildState.get(server).chronicle(day, System.currentTimeMillis(), "chronicle.amemaru.project_completed", List.of(def.shortName()));
    }

    /** 「輝き段階を待っています」を一度だけ知らせた共同事業（サーバーを開き直すまで）。 */
    private static final Set<String> WAITING_TOLD = new LinkedHashSet<>();

    /** すべての共同事業を確かめる（寄付・輝き段階が上がった・人数補正が変わった・日が変わった）。 */
    public static void checkAll(MinecraftServer server) {
        for (ProjectDef def : ProjectDefs.ordered()) {
            check(server, def);
        }
    }

    /** 完成した共同事業の施設を変える（物語の印・育ったあとの形・知らせ・冒険日誌）。もう変わっていれば false。 */
    public static boolean build(MinecraftServer server, ProjectDef def) {
        TownData town = TownData.get(server);
        TownData.Project p = town.project(def.id());
        if (p.built()) {
            return false;
        }
        int day = day(server);
        if (!p.completed()) {
            town.complete(def.id(), day, gameDay(server));
        }
        town.build(def.id(), day);
        StoryState.get(server).add(def.flag());
        // 自動テストの間（進行日を決めているとき）は、街に形を重ねない（街を建てるテストと重ならないように）
        boolean placed = testDay == null && !def.overlay().isEmpty() && CityCommands.placeOverlay(server.overworld(), def.overlay());
        // 物語の印で現れるNPC（料理屋のペトラ・ポム）を、立つ所に置く（育ったあとの形を重ねたあと）
        if (testDay == null) {
            CityCommands.builtOrigin(server.overworld()).ifPresent(o -> jp.amemaru.lostconnection.npc.NpcService.spawnAll(server.overworld(), o));
        }
        broadcast(server, Component.translatable("project.amemaru.built", def.shortName(), def.result()).withStyle(ChatFormatting.GOLD));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(def.result()).withStyle(ChatFormatting.YELLOW)));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("project.amemaru.built_title", def.shortName())
                    .withStyle(ChatFormatting.GOLD)));
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.6F, 1.0F);
        }
        GuildState.get(server).chronicle(day, System.currentTimeMillis(), "chronicle.amemaru.project_built",
                List.of(def.shortName(), def.result()));
        AmemaruMod.LOGGER.info("[amemaru][town] project {} built on day {} (overlay {}: {})", def.id(), day, def.overlay(), placed);
        return true;
    }

    /** 寄付の台座の上の光（右クリックできる所の目じるし。近くの人だけに送る）。 */
    private static void sparkleStand(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos o = CityCommands.builtOrigin(level).orElse(null);
        if (o == null) {
            return;
        }
        double x = o.getX() + 15.5;
        double y = o.getY() + 2.3;
        double z = o.getZ() + 15.5;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) < 32 * 32) {
                level.sendParticles(player, net.minecraft.core.particles.ParticleTypes.END_ROD, false, x, y, z, 3, 0.25, 0.3, 0.25, 0.01);
            }
        }
    }

    /** ワールドのゲームの日（時刻 ÷ 24000。0 時が朝の6時）。 */
    static long gameDay(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 7) {
            return;
        }
        MinecraftServer server = event.getServer();
        TownData town = TownData.get(server);
        if (server.getTickCount() % 40 == 7) {
            sparkleStand(server);
        }
        long timeOfDay = server.overworld().getDayTime() % 24000L;
        boolean morning = morningPassed(lastTimeOfDay, timeOfDay);
        lastTimeOfDay = timeOfDay;
        if (!morning) {
            return;
        }
        for (ProjectDef def : ProjectDefs.ordered()) {
            TownData.Project p = town.project(def.id());
            // 完成した次の朝（ゲームの時刻の6時）に、施設が変わる
            if (p.completed() && !p.built()) {
                build(server, def);
            }
        }
    }

    /** 進行日が変わった（{@code ProgressionService}）：受付の始まりを知らせ、完成していた共同事業の施設を変え、確かめ直す。 */
    public static void onDayChanged(MinecraftServer server, int before, int now) {
        for (ProjectDef def : ProjectDefs.ordered()) {
            if (before < def.startDay() && now >= def.startDay() && !TownData.get(server).project(def.id()).completed()) {
                broadcast(server, Component.translatable("project.amemaru.opened", def.shortName(), goal(server, def), def.stage())
                        .withStyle(ChatFormatting.AQUA));
                GuildState.get(server).chronicle(now, System.currentTimeMillis(), "chronicle.amemaru.project_opened", List.of(def.shortName()));
            }
            TownData.Project p = TownData.get(server).project(def.id());
            if (now > before && p.completed() && !p.built()) {
                build(server, def);
            }
        }
        checkAll(server);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        OPEN.remove(event.getEntity().getUUID());
    }

    /** 管理者・自動テスト：共同事業を初めに戻す（寄付も消える。重ねた形は、街を建て直すと消える）。 */
    public static void reset(MinecraftServer server, ProjectDef def) {
        TownData.get(server).reset(def.id());
        StoryState.get(server).remove(def.flag());
        WAITING_TOLD.remove(def.id().toString());
    }

    private static void broadcast(MinecraftServer server, Component line) {
        server.getPlayerList().broadcastSystemMessage(line, false);
    }
}
