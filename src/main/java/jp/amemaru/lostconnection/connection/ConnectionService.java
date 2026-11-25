package jp.amemaru.lostconnection.connection;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.ConnectionS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * コネクション量（CP）と大結晶の輝き段階（Phase 7 その1。Phase 0-A 13章・Phase 0-B 2章「connection」）。
 *
 * <ul>
 *   <li>CPを足すのはサーバーだけ。知らせる側は {@link #award} を1行呼ぶ（行動のIDは {@link ConnectionActions}）</li>
 *   <li>1人1日の上限：分野ごとに150・合計600（config）。行動ごとの1日の回数・同じ相手の回数（データ）。日は毎日4時に切り替わる</li>
 *   <li>人数補正：段階のしきい値は「直近3日の平均アクティブ人数 ÷ 25」倍（0.4〜2.0倍）。一度灯った段階は消えない</li>
 *   <li>段階が上がると、全員に大結晶の一言（だんだんはっきりする）と、新しい効果を知らせる。大結晶は段階に合わせて光る</li>
 *   <li>いない人に届いたCP（フリマで売れた・露店に人が来た・結び札をもらった）は、次に入ったときに数える</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class ConnectionService {
    /** 1日の切り替えに使う時計（自動テストで日を進めるため、差しかえられる）。 */
    private static Supplier<LocalDateTime> clock = LocalDateTime::now;
    /** 全員に送る「サーバー全体の輝き」の知らせが要るか（CPが増えるたびに送らず、まとめて送る）。 */
    private static boolean broadcastPending;
    private static long lastBroadcastTick;
    /** 最後に見た日（つながったまま4時をまたいだら、今日の相棒を決め直す）。 */
    private static String lastSeenDay = "";

    /** 大結晶の真ん中（中央広場の真ん中からの位置。光の粒を出す所）。 */
    private static final double CRYSTAL_Y = 9.0;
    /** 大結晶の光が見える距離（ブロック。光の粒は遠くても送る）。 */
    private static final double GLOW_RANGE = 160.0;
    /** 結界の柱（8本）の場所（中央広場の真ん中から。tools/mapgen/build_city.py の barrier_pillars）。結晶の頭のシーランタンの高さ。 */
    private static final int[][] PILLARS = {{-62, -62}, {62, -62}, {62, 62}, {-62, 62}, {8, -62}, {62, 8}, {-8, 62}, {-62, -8}};
    private static final int PILLAR_LANTERN_Y = 8;
    /** 塔前広場（中央広場の真ん中から x −23〜23、z −147〜−116。docs/map_style.md）。 */
    private static final int PLAZA_MIN_X = -23;
    private static final int PLAZA_MAX_X = 23;
    private static final int PLAZA_MIN_Z = -147;
    private static final int PLAZA_MAX_Z = -116;

    private ConnectionService() {
    }

    // ================================================================ 日とアクティブな人

    /** 自動テスト：時計を差しかえる（null で戻す）。 */
    public static void setClockForTest(@Nullable Supplier<LocalDateTime> testClock) {
        clock = testClock == null ? LocalDateTime::now : testClock;
    }

    /** 今日（毎日 resetHour 時に切り替わる日。例：2026-11-23）。 */
    public static String today() {
        return clock.get().minusHours(ServerConfig.CONNECTION_RESET_HOUR.get()).toLocalDate().toString();
    }

    static String daysBefore(int days) {
        return clock.get().minusHours(ServerConfig.CONNECTION_RESET_HOUR.get()).toLocalDate().minusDays(days).toString();
    }

    /** 直近の日（今日を含む）の平均アクティブ人数。数えた日がなければ、今つながっている人数。 */
    public static double averageActive(MinecraftServer server) {
        ConnectionData data = ConnectionData.get(server);
        int days = ServerConfig.CONNECTION_ACTIVE_DAYS.get();
        int sum = 0;
        int counted = 0;
        for (int d = 0; d < days; d++) {
            int n = data.activeOn(daysBefore(d));
            if (n > 0) {
                sum += n;
                counted++;
            }
        }
        if (counted == 0) {
            return Math.max(1, server.getPlayerList().getPlayerCount());
        }
        return (double) sum / counted;
    }

    /** しきい値に掛ける人数補正の倍率（0.4〜2.0）。 */
    public static double scale(MinecraftServer server) {
        double ratio = averageActive(server) / ServerConfig.CONNECTION_BASE_PLAYERS.get();
        return Math.max(ServerConfig.CONNECTION_SCALE_MIN.get(), Math.min(ServerConfig.CONNECTION_SCALE_MAX.get(), ratio));
    }

    // ================================================================ 段階と効果

    /** 今の輝き段階（1〜10。届いたいちばん上）。 */
    public static int stage(MinecraftServer server) {
        return ConnectionData.get(server).stageReached();
    }

    public static int stars(MinecraftServer server) {
        return ConnectionData.get(server).starsReached();
    }

    /** 段階の効果（全員・累積）。{@code lost_resist}・{@code exp}・{@code barrier}・{@code down_seconds}・{@code blessing}。 */
    public static double effect(MinecraftServer server, String key) {
        return ConnectionRules.current().effect(key, stage(server));
    }

    /** ロスト耐性（%。段階3で+3%、段階7で+6%）。 */
    public static double lostResistBonus(MinecraftServer server) {
        return effect(server, "lost_resist");
    }

    /** 経験値（%。段階4で+3%、段階8で+6%）。 */
    public static double expBonusPercent(MinecraftServer server) {
        return effect(server, "exp");
    }

    /** ダウン（蘇生待ち）の秒数に足す秒（段階6で+5秒）。 */
    public static int downBonusSeconds(MinecraftServer server) {
        return (int) Math.round(effect(server, "down_seconds"));
    }

    /** 襲撃時の結界の耐久（%。段階5で+20%、段階9で+40%。襲撃は後のPhaseで使う）。 */
    public static double barrierBonusPercent(MinecraftServer server) {
        return effect(server, "barrier");
    }

    /** 結界が塔前広場まで広がっているか（段階2から。結界の範囲を使う襲撃は後のPhase。今は広場のふちに光の幕を見せる）。 */
    public static boolean plazaInBarrier(MinecraftServer server) {
        return effect(server, "barrier_plaza") > 0;
    }

    /** 最終決戦の加護（段階10で1。共同事業⑦「大結晶の強化」で、もう一段。最終決戦は後のPhaseで使う）。 */
    public static double finalBlessing(MinecraftServer server) {
        return effect(server, "blessing") + jp.amemaru.lostconnection.town.ProjectService.blessing(server);
    }

    /** 段階と星を、累計と人数補正から計算し直す（上がったら知らせる）。 */
    public static void recheck(MinecraftServer server) {
        ConnectionData data = ConnectionData.get(server);
        ConnectionRules rules = ConnectionRules.current();
        double scale = scale(server);
        int now = rules.stageFor(data.total(), scale);
        int before = data.stageReached();
        if (now > before) {
            data.setStageReached(now, System.currentTimeMillis());
            for (int s = before + 1; s <= now; s++) {
                announceStage(server, s, rules);
            }
            // Phase 7 その4：輝き段階を待っていた共同事業が完成するか
            jp.amemaru.lostconnection.town.ProjectService.checkAll(server);
        }
        if (data.stageReached() >= rules.maxStage()) {
            int stars = rules.starsFor(data.total(), scale);
            if (stars > data.starsReached()) {
                int old = data.starsReached();
                data.setStarsReached(stars);
                for (int k = old + 1; k <= stars; k++) {
                    announceStar(server, k, rules);
                }
            }
        }
        broadcastPending = true;
    }

    private static void announceStage(MinecraftServer server, int stage, ConnectionRules rules) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stage", stage);
        tag.putString("voice", rules.voice(stage));
        ListTag effects = new ListTag();
        for (Map.Entry<String, Double> e : rules.effectsAt(stage).entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString("key", e.getKey());
            one.putDouble("value", e.getValue());
            effects.add(one);
        }
        tag.put("effects", effects);
        ModNetwork.sendToAll(new ConnectionS2C("stage", tag));
        Component line = Component.translatable("connection.amemaru.stage_up", stage).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(line);
            for (Map.Entry<String, Double> e : rules.effectsAt(stage).entrySet()) {
                player.sendSystemMessage(effectText(e.getKey(), e.getValue()).copy().withStyle(ChatFormatting.GRAY));
            }
        }
        burst(server, 60);
        // 効果（ロスト耐性など）が変わるので、全員のステータスを計算し直す
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
        }
        // 物語の条件に使える印（段階ごと）
        jp.amemaru.lostconnection.story.StoryState.get(server).add(AmemaruMod.id("connection/stage_" + stage));
        AmemaruMod.LOGGER.info("[amemaru] The great crystal reached glow stage {} (total {} CP)", stage, ConnectionData.get(server).total());
    }

    private static void announceStar(MinecraftServer server, int star, ConnectionRules rules) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("star", star);
        tag.putString("voice", rules.starVoice());
        ModNetwork.sendToAll(new ConnectionS2C("star", tag));
        Component line = Component.translatable("connection.amemaru.star_up", star, rules.maxStars()).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(line);
        }
        burst(server, 40);
    }

    /** 効果の一行（「ロスト耐性 +3%」など）。 */
    public static Component effectText(String key, double value) {
        String number = value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
        return Component.translatable("connection.amemaru.effect." + key, number);
    }

    // ================================================================ CPを足す

    /** CPを足す（相手なし・1回）。 */
    public static int award(ServerPlayer player, ResourceLocation action) {
        return award(player, action, null, 1);
    }

    /** CPを足す（相手あり・1回）。 */
    public static int award(ServerPlayer player, ResourceLocation action, @Nullable UUID target) {
        return award(player, action, target, 1);
    }

    /**
     * CPを足す。行動の決まり（1回分のCP・「この数ごとに1回分」・1日の回数・同じ相手の回数）と、1人1日の上限（分野ごと・合計）を
     * 確かめ、足せた分だけ本人とサーバー全体に足す。
     *
     * @param target 相手（同じ相手の回数を数える。なければ null）
     * @param count  数（「5体ごとに1」の行動は、倒した数）
     * @return 足したCP
     */
    public static int award(ServerPlayer player, ResourceLocation action, @Nullable UUID target, int count) {
        if (!ServerConfig.CONNECTION_ENABLED.get() || count <= 0 || player.isSpectator()) {
            return 0;
        }
        ConnectionActionDef def = ConnectionActionDefs.get(action);
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (def == null || data == null) {
            if (def == null) {
                AmemaruMod.LOGGER.debug("[amemaru] Unknown connection action {}", action);
            }
            return 0;
        }
        ConnectionRecord record = data.getConnection();
        record.rollover(today());
        if (target != null && target.equals(player.getUUID())) {
            return 0;
        }
        // 「この数ごとに1回分」：次の1回分までの数を足す
        int progress = record.progress(action) + count;
        int times = progress / def.per();
        record.setProgress(action, progress % def.per());
        if (times <= 0) {
            return 0;
        }
        if (def.dailyLimit() > 0) {
            times = Math.min(times, def.dailyLimit() - record.actionsToday(action));
        }
        if (target != null && def.sameTargetDaily() > 0) {
            times = Math.min(times, def.sameTargetDaily() - record.targetToday(action, target));
        }
        if (times <= 0) {
            return 0;
        }
        int want = (int) Math.round(def.cp() * times * ServerConfig.CONNECTION_MULTIPLIER.get());
        int categoryRoom = ServerConfig.CONNECTION_DAILY_CATEGORY.get() - record.today(def.category());
        int totalRoom = ServerConfig.CONNECTION_DAILY_TOTAL.get() - record.todayTotal();
        int granted = Math.max(0, Math.min(want, Math.min(categoryRoom, totalRoom)));
        record.record(action, def.category(), times, target, granted);
        if (granted > 0) {
            ConnectionData.get(player.server).add(player.getUUID(), player.getGameProfile().getName(), def.category(), granted);
            player.displayClientMessage(Component.translatable("connection.amemaru.gain", granted,
                    Component.translatable(def.category().translationKey()), def.name()).withStyle(ChatFormatting.AQUA), true);
            recheck(player.server);
        }
        if (want > granted) {
            String key = totalRoom <= categoryRoom ? "total" : def.category().key();
            if (!record.noticeCap(key)) {
                player.sendSystemMessage(("total".equals(key)
                        ? Component.translatable("connection.amemaru.cap_total", ServerConfig.CONNECTION_DAILY_TOTAL.get())
                        : Component.translatable("connection.amemaru.cap_category", Component.translatable(def.category().translationKey()),
                        ServerConfig.CONNECTION_DAILY_CATEGORY.get())).withStyle(ChatFormatting.GRAY));
            }
        }
        return granted;
    }

    /**
     * いないかもしれない人にCPを足す（いれば今、いなければ次に入ったときに）。
     *
     * @return 今足したCP（あとで足すときは 0）
     */
    public static int awardLater(MinecraftServer server, UUID who, ResourceLocation action, @Nullable UUID target, int count) {
        ServerPlayer player = server.getPlayerList().getPlayer(who);
        if (player != null) {
            return award(player, action, target, count);
        }
        CompoundTag pending = new CompoundTag();
        pending.putString("action", action.toString());
        if (target != null) {
            pending.putUUID("target", target);
        }
        pending.putInt("count", count);
        ConnectionData.get(server).addPending(who, pending);
        return 0;
    }

    // ================================================================ 管理者

    /** 管理者のコマンド：サーバー全体の累計に足す（人には付かない）。 */
    public static void adminAdd(MinecraftServer server, long cp) {
        ConnectionData data = ConnectionData.get(server);
        data.setTotal(data.total() + cp);
        recheck(server);
    }

    /** 管理者のコマンド：サーバー全体の累計を決める（段階は下げない）。 */
    public static void adminSet(MinecraftServer server, long cp) {
        ConnectionData.get(server).setTotal(cp);
        recheck(server);
    }

    /** 管理者のコマンド：段階・星・累計・貢献を初めに戻す。 */
    public static void adminReset(MinecraftServer server) {
        int before = ConnectionData.get(server).stageReached();
        ConnectionData.get(server).reset();
        afterLowered(server, before, 1);
    }

    /**
     * 管理者のコマンド：段階を決める。上げるときは、上がった段階を順に知らせる（累計は、その段階のしきい値まで足す）。
     * 下げるときは、累計をその段階のしきい値にし、上の段階の印（物語の条件）と星を消す。
     */
    public static void adminStage(MinecraftServer server, int stage) {
        ConnectionData data = ConnectionData.get(server);
        ConnectionRules rules = ConnectionRules.current();
        int target = Math.max(1, Math.min(rules.maxStage(), stage));
        long at = Math.round(rules.threshold(target) * scale(server));
        int before = data.stageReached();
        if (target > before) {
            data.setTotal(Math.max(data.total(), at));
            data.setStageReached(target, System.currentTimeMillis());
            for (int s = before + 1; s <= target; s++) {
                announceStage(server, s, rules);
            }
            broadcastPending = true;
            jp.amemaru.lostconnection.town.ProjectService.checkAll(server);
        } else if (target < before) {
            data.setTotal(at);
            data.lowerStage(target);
            afterLowered(server, before, target);
        }
    }

    /** 段階を下げたあと：上の段階の印を消し、全員のステータスを計算し直して、知らせる。 */
    private static void afterLowered(MinecraftServer server, int before, int now) {
        jp.amemaru.lostconnection.story.StoryState story = jp.amemaru.lostconnection.story.StoryState.get(server);
        for (int s = now + 1; s <= Math.max(before, ConnectionRules.current().maxStage()); s++) {
            story.remove(AmemaruMod.id("connection/stage_" + s));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
        }
        broadcastPending = true;
    }

    /** 管理者のコマンド・自動テスト：アクティブな人として数える。 */
    public static void markActive(MinecraftServer server, UUID who) {
        ConnectionData.get(server).markActive(today(), who);
    }

    // ================================================================ 途中参加者

    /**
     * 途中参加者か（Phase 0-A 14章「途中参加・見逃し対策」。【補完】2日目から初めて来た人で、来てから3日のうち）。
     */
    public static boolean isNewcomer(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        int join = data.getConnection().joinDay();
        int day = ProgressionData.get(player.server).day();
        return join >= 2 && day - join <= 2;
    }

    // ================================================================ 同期

    /** 本人の画面へ（今日の分野別CP・上限・累計。サーバー全体の輝きも添える）。 */
    public static void syncSelf(ServerPlayer player, PlayerData data) {
        CompoundTag tag = serverTag(player.server);
        ConnectionRecord record = data.getConnection();
        record.rollover(today());
        int[] todayCp = new int[ConnectionCategory.values().length];
        long[] lifetime = new long[ConnectionCategory.values().length];
        for (ConnectionCategory c : ConnectionCategory.values()) {
            todayCp[c.ordinal()] = record.today(c);
            lifetime[c.ordinal()] = record.lifetime(c);
        }
        tag.put("today", new IntArrayTag(todayCp));
        tag.put("lifetime", new LongArrayTag(lifetime));
        tag.putInt("capTotal", ServerConfig.CONNECTION_DAILY_TOTAL.get());
        tag.putInt("capCategory", ServerConfig.CONNECTION_DAILY_CATEGORY.get());
        tag.putInt("thanksLeft", Math.max(0, thanksDailyLimit() - record.actionsToday(ConnectionActions.THANKS_SEND)));
        // 交流の常設の仕組み：今日の相棒・はじめましてボーナス（15章）
        tag.put("partner", ConnectionSocial.partnerTag(player));
        tag.putInt("firstMeets", record.firstMeetCount());
        tag.putInt("firstMeetDaily", ServerConfig.FIRST_MEET_DAILY.get());
        tag.putDouble("firstMeetBonus", ConnectionSocial.firstMeetBonus(player));
        ModNetwork.sendTo(player, new ConnectionS2C("state", tag));
    }

    /** 結び札を1日に贈れる枚数（行動 thanks_send の1日の回数。なければ3）。 */
    public static int thanksDailyLimit() {
        ConnectionActionDef def = ConnectionActionDefs.get(ConnectionActions.THANKS_SEND);
        return def == null || def.dailyLimit() <= 0 ? 3 : def.dailyLimit();
    }

    /** サーバー全体の輝き（全員に同じ）。 */
    public static CompoundTag serverTag(MinecraftServer server) {
        ConnectionData data = ConnectionData.get(server);
        ConnectionRules rules = ConnectionRules.current();
        double scale = scale(server);
        int stage = data.stageReached();
        CompoundTag tag = new CompoundTag();
        tag.putLong("total", data.total());
        tag.putInt("stage", stage);
        tag.putInt("maxStage", rules.maxStage());
        tag.putInt("stars", data.starsReached());
        tag.putInt("maxStars", rules.maxStars());
        tag.putDouble("scale", scale);
        tag.putDouble("active", averageActive(server));
        tag.putInt("base", ServerConfig.CONNECTION_BASE_PLAYERS.get());
        tag.putLong("from", Math.round(rules.threshold(stage) * scale));
        if (stage < rules.maxStage()) {
            tag.putLong("next", Math.round(rules.threshold(stage + 1) * scale));
        } else {
            long top = Math.round(rules.threshold(rules.maxStage()) * scale);
            long step = Math.max(1, Math.round(rules.starStep() * scale));
            tag.putLong("from", top + step * data.starsReached());
            tag.putLong("next", data.starsReached() >= rules.maxStars() ? 0 : top + step * (data.starsReached() + 1));
        }
        long[] cats = new long[ConnectionCategory.values().length];
        for (ConnectionCategory c : ConnectionCategory.values()) {
            cats[c.ordinal()] = data.category(c);
        }
        tag.put("categories", new LongArrayTag(cats));
        ListTag effects = new ListTag();
        for (String key : List.of("barrier_plaza", "lost_resist", "exp", "barrier", "down_seconds", "blessing")) {
            double value = rules.effect(key, stage);
            if (value > 0) {
                CompoundTag one = new CompoundTag();
                one.putString("key", key);
                one.putDouble("value", value);
                effects.add(one);
            }
        }
        tag.put("effects", effects);
        // 次の段階で効くもの
        if (stage < rules.maxStage()) {
            ListTag upcoming = new ListTag();
            for (Map.Entry<String, Double> e : rules.effectsAt(stage + 1).entrySet()) {
                CompoundTag one = new CompoundTag();
                one.putString("key", e.getKey());
                one.putDouble("value", e.getValue());
                upcoming.add(one);
            }
            tag.put("upcoming", upcoming);
        }
        // 分野ごとの貢献の上位（3人ずつ）
        CompoundTag top = new CompoundTag();
        for (ConnectionCategory c : ConnectionCategory.values()) {
            List<Map.Entry<UUID, ConnectionData.Contributor>> list = new ArrayList<>(data.contributors().entrySet());
            list.removeIf(e -> e.getValue().get(c) <= 0);
            list.sort(Comparator.comparingLong((Map.Entry<UUID, ConnectionData.Contributor> e) -> e.getValue().get(c)).reversed());
            ListTag names = new ListTag();
            for (int i = 0; i < Math.min(3, list.size()); i++) {
                CompoundTag one = new CompoundTag();
                one.putString("name", list.get(i).getValue().name());
                one.putLong("cp", list.get(i).getValue().get(c));
                names.add(one);
            }
            top.put(c.key(), names);
        }
        tag.put("top", top);
        return tag;
    }

    /** 全員に送る（本人の分も添える）。 */
    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerDataCapability.get(player).ifPresent(data -> data.markDirty(PlayerData.SYNC_CONNECTION));
        }
        broadcastPending = false;
    }

    // ================================================================ 大結晶の光

    /** 大結晶の真ん中（街を建てていなければ空）。 */
    public static Optional<BlockPos> crystal(MinecraftServer server) {
        return jp.amemaru.lostconnection.admin.CityCommands.builtOrigin(server.overworld())
                .map(origin -> origin.offset(0, (int) CRYSTAL_Y, 0));
    }

    /** 大結晶のまわりに、段階に合わせた光の粒（ふだん）。結界の柱の頭と、段階2からは塔前広場のふちにも。 */
    private static void glow(MinecraftServer server) {
        Optional<BlockPos> at = crystal(server);
        if (at.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        if (level.players().isEmpty()) {
            return;
        }
        int stage = stage(server);
        BlockPos p = at.get();
        // 段階1は小さく、段階10は大きく、星の数だけきらめきを足す
        int count = 2 + stage * 2 + stars(server) * 2;
        double spread = 0.8 + stage * 0.25;
        particles(level, ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, count, spread, spread * 1.6, spread, 0.01);
        if (stage >= 5) {
            particles(level, ParticleTypes.GLOW, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, stage - 3, spread, spread * 1.4, spread, 0.02);
        }
        // 結界の柱：結晶の頭が、段階に合わせて光る（柱が建っている所だけ）
        BlockPos origin = p.offset(0, -(int) CRYSTAL_Y, 0);
        int pillarCount = 1 + stage / 3;
        for (int[] spot : PILLARS) {
            BlockPos head = origin.offset(spot[0], PILLAR_LANTERN_Y, spot[1]);
            if (level.isLoaded(head) && level.getBlockState(head).is(net.minecraft.world.level.block.Blocks.SEA_LANTERN)) {
                particles(level, ParticleTypes.END_ROD, head.getX() + 0.5, head.getY() + 1.8, head.getZ() + 0.5, pillarCount, 0.25, 0.7,
                        0.25, 0.01);
            }
        }
        // 段階2：結界が塔前広場まで広がる（広場のふちに、3ブロックおきに立ちのぼる光の幕）
        if (plazaInBarrier(server)) {
            int w = PLAZA_MAX_X - PLAZA_MIN_X;
            int d = PLAZA_MAX_Z - PLAZA_MIN_Z;
            int perimeter = 2 * (w + d);
            int phase = level.random.nextInt(3);
            for (int t = phase; t < perimeter; t += 3) {
                int x;
                int z;
                if (t < w) {
                    x = PLAZA_MIN_X + t;
                    z = PLAZA_MIN_Z;
                } else if (t < w + d) {
                    x = PLAZA_MAX_X;
                    z = PLAZA_MIN_Z + (t - w);
                } else if (t < 2 * w + d) {
                    x = PLAZA_MAX_X - (t - w - d);
                    z = PLAZA_MAX_Z;
                } else {
                    x = PLAZA_MIN_X;
                    z = PLAZA_MAX_Z - (t - 2 * w - d);
                }
                // 広場の段（+0〜+3）より上に、腰から頭の高さの幕
                particles(level, ParticleTypes.END_ROD, origin.getX() + x + 0.5, origin.getY() + 4.0, origin.getZ() + z + 0.5, 1, 0.05, 1.0,
                        0.05, 0.004);
            }
        }
    }

    /** 近くの人だけに、遠くても見える光の粒を送る（ふつうの送り方は32ブロックまで）。 */
    private static void particles(ServerLevel level, net.minecraft.core.particles.ParticleOptions type, double x, double y, double z, int count,
                                  double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= GLOW_RANGE * GLOW_RANGE) {
                level.sendParticles(player, type, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    /** 段階が上がったときの、大きな光（と音）。 */
    private static void burst(MinecraftServer server, int count) {
        Optional<BlockPos> at = crystal(server);
        if (at.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        BlockPos p = at.get();
        particles(level, ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, count, 2.5, 4.0, 2.5, 0.12);
        particles(level, ParticleTypes.FIREWORK, p.getX() + 0.5, p.getY() + 3.0, p.getZ() + 0.5, count / 2, 1.5, 2.0, 1.5, 0.08);
        level.playSound(null, p, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 3.0F, 0.8F);
        level.playSound(null, p, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2.0F, 1.2F);
    }

    // ================================================================ イベント

    /** 輝き段階のロスト耐性（段階3で+3%、段階7で+6%）を、全員のステータスに足す。 */
    @SubscribeEvent
    public static void onCollectStats(jp.amemaru.lostconnection.player.CollectStatsEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        double lost = lostResistBonus(player.server);
        if (lost > 0) {
            event.addFlat(jp.amemaru.lostconnection.player.StatType.LOST_RESIST, lost);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        markActive(server, player.getUUID());
        PlayerDataCapability.get(player).ifPresent(data -> {
            ConnectionRecord record = data.getConnection();
            record.rollover(today());
            if (record.joinDay() <= 0) {
                // 初めて数える人：オープニングを見る前なら、今日が来た日（途中参加者）。前からいた人は1日目
                boolean fresh = data.getStory().opening() < jp.amemaru.lostconnection.story.PlayerStory.OPENING_DONE;
                record.setJoinDay(fresh ? ProgressionData.get(server).day() : 1);
            }
        });
        for (CompoundTag pending : ConnectionData.get(server).takePending(player.getUUID())) {
            ResourceLocation action = ResourceLocation.tryParse(pending.getString("action"));
            if (action != null) {
                award(player, action, pending.hasUUID("target") ? pending.getUUID("target") : null, Math.max(1, pending.getInt("count")));
            }
        }
        recheck(server);
        // 今日の相棒（15章）：入ったときに示す。相棒がまだ決まっていない人には、入ってきたこの人も候補になる
        ConnectionData.get(server).rememberName(player.getUUID(), player.getGameProfile().getName());
        ConnectionSocial.ensurePartner(player, true);
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != player && ConnectionData.get(server).partnerOf(other.getUUID()) == null) {
                // 新しく決まったときだけ知らせる（見つからなかったことは、入ったときにだけ知らせる）
                ConnectionSocial.ensurePartner(other, false);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();
        if (tick % 40 == 0) {
            glow(server);
        }
        // 1分ごと：日が変わったら（毎日4時）、つながっている人の今日の分を空にし、今日の相棒を決め直す
        if (tick % 1200 == 0) {
            String now = today();
            if (!now.equals(lastSeenDay)) {
                boolean first = lastSeenDay.isEmpty();
                lastSeenDay = now;
                if (!first) {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        PlayerDataCapability.get(player).ifPresent(data -> {
                            data.getConnection().rollover(now);
                            data.markDirty(PlayerData.SYNC_CONNECTION);
                        });
                        ConnectionData.get(server).markActive(now, player.getUUID());
                        ConnectionSocial.ensurePartner(player, true);
                    }
                }
            }
        }
        // 5分ごと：つながっている人をアクティブとして数える（日をまたいでも数える）。人数補正が変わると段階が上がることがある
        if (tick % 6000 == 0) {
            boolean added = false;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                added |= ConnectionData.get(server).markActive(today(), player.getUUID());
            }
            if (added) {
                recheck(server);
            }
        }
        if (broadcastPending && tick - lastBroadcastTick >= 40) {
            lastBroadcastTick = tick;
            broadcast(server);
        }
    }
}
