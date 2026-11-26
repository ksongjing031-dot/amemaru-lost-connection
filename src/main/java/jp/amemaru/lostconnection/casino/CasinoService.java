package jp.amemaru.lostconnection.casino;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.casino.CasinoGames.Bet;
import jp.amemaru.lostconnection.casino.CasinoGames.Game;
import jp.amemaru.lostconnection.casino.CasinoTable.Phase;
import jp.amemaru.lostconnection.connection.ConnectionActions;
import jp.amemaru.lostconnection.connection.ConnectionService;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.guild.TitleDef;
import jp.amemaru.lostconnection.guild.TitleDefs;
import jp.amemaru.lostconnection.guild.TitleService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.CasinoS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * カジノ「遊戯館ラッキーノット」（Phase 7 その3。Phase 0-A 7章「ルーレット（ゲーム内通貨のみ）、景品交換。寄付で新しいゲーム」・
 * 15章「娯楽の設計（カジノ）」。Phase 0-B 13章「カジノ（右クリックでGUIを開いて使う。【11月22日に決定】）」）。
 *
 * <ul>
 *   <li>館の中の卓・窓口（{@code zones}）を右クリックすると画面が開く：ルーレットの卓・カードの卓（ハイ＆ロー）・両替の窓口・
 *       景品の交換所。支配人ジゼルの会話からも開ける。11月27日のご指示「ルーレットとハイ＆ロー以外を廃止」で魚レースはやめ、
 *       地下遊技場にパチンコとスロット（{@link jp.amemaru.lostconnection.casino.parlor.ParlorService}）</li>
 *   <li>チップ：ノットで買う（1日の上限）・ノットに戻す。賭けるのはチップだけ（ゲーム内通貨のみ）</li>
 *   <li>卓：みんなで同じ勝負を囲む（{@link CasinoTable}）。4人以上で賭けた勝負は、賭けた人にCP（13章）。カジノナイトは配当におまけ</li>
 *   <li>景品：留め糸・見た目の品（強さのない服と冠・ラッキーノットの旗・絵・楽の円盤）・称号</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class CasinoService {
    /** 右クリックで画面が開く所の名前。 */
    public static final List<String> ZONES = List.of("roulette", "highlow_a", "highlow_b", "cashier", "prizes");
    /** 画面のタブ。 */
    public static final List<String> TABS = List.of("roulette", "highlow", "cashier", "prizes");
    /** 画面を開いてから操作できる時間（離れてよい距離は {@code watch_range}）。 */
    private static final long OPEN_TICKS = 20L * 60 * 30;

    private record Open(Vec3 at, long tick, boolean admin) {
    }

    private static final Map<String, CasinoTable> TABLES = new LinkedHashMap<>();
    private static final Map<UUID, Open> OPEN = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();
    /** 次の勝負の結果を決めておく（管理者のコマンド・自動テスト・CIの写真）。 */
    private static final Map<String, Integer> FORCED = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    static {
        TABLES.put("roulette", new CasinoTable("roulette", Game.ROULETTE));
        TABLES.put("highlow_a", new CasinoTable("highlow_a", Game.HIGHLOW));
        TABLES.put("highlow_b", new CasinoTable("highlow_b", Game.HIGHLOW));
    }

    private CasinoService() {
    }

    @Nullable
    public static CasinoTable table(String id) {
        return TABLES.get(id);
    }

    static String nameOf(UUID who) {
        return NAMES.getOrDefault(who, "?");
    }

    // ================================================================ 開いているか・遊べるか

    /** 開場しているか（{@code open_day} から）。 */
    public static boolean isOpen(MinecraftServer server) {
        return ProgressionData.currentDay(server) >= CasinoRules.current().openDay();
    }

    /** その遊びができるか（ルーレットはいつでも、ハイ＆ローと魚レースは共同事業②・⑥の印のあと）。 */
    public static boolean unlocked(MinecraftServer server, Game game) {
        CasinoRules rules = CasinoRules.current();
        return switch (game) {
            case ROULETTE -> true;
            case HIGHLOW -> StoryState.get(server).has(rules.highLowFlag());
        };
    }

    /** その場所の右クリックで開く所（{@code zones} の名前）。なければ null。 */
    @Nullable
    public static String zoneAt(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) {
            return null;
        }
        BlockPos o = CityCommands.builtOrigin(level).orElse(null);
        if (o == null) {
            return null;
        }
        int x = pos.getX() - o.getX();
        int y = pos.getY() - o.getY();
        int z = pos.getZ() - o.getZ();
        for (CasinoRules.Zone zone : CasinoRules.current().zones().values()) {
            if (zone.contains(x, y, z)) {
                return zone.id();
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || !ServerConfig.CASINO_ENABLED.get()) {
            return;
        }
        String zone = zoneAt(player.serverLevel(), event.getPos());
        if (zone == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            open(player, zone, false);
        }
    }

    // ================================================================ 画面

    /**
     * 画面を開く。where は {@link #ZONES} の名前か {@link #TABS} の名前（ハイ＆ローは highlow_a）。admin（管理者のコマンド）なら
     * 開場の日と距離を見ない。
     */
    public static void open(ServerPlayer player, String where, boolean admin) {
        MinecraftServer server = player.server;
        if (!admin && !isOpen(server)) {
            player.displayClientMessage(Component.translatable("casino.amemaru.closed", CasinoRules.current().openDay())
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        String table = switch (where) {
            case "roulette" -> "roulette";
            case "highlow", "highlow_a" -> "highlow_a";
            case "highlow_b" -> "highlow_b";
            default -> "";
        };
        String tab = switch (where) {
            case "highlow_a", "highlow_b" -> "highlow";
            default -> TABS.contains(where) ? where : "roulette";
        };
        NAMES.put(player.getUUID(), player.getGameProfile().getName());
        OPEN.put(player.getUUID(), new Open(player.position(), server.getTickCount(), admin));
        unwatchAll(player.getUUID());
        if (!table.isEmpty()) {
            watch(player, table);
        }
        CompoundTag tag = screenTag(player);
        tag.putString("tab", tab);
        if (!table.isEmpty()) {
            tag.put("state", tableTag(player, TABLES.get(table)));
        }
        ModNetwork.sendTo(player, new CasinoS2C("screen", tag));
    }

    /** 画面を使えるか（開いてから30分のうちで、開いた所から watch_range（24ブロック）以内。管理者が開いたときは距離を見ない）。 */
    static boolean canUse(ServerPlayer player) {
        Open open = OPEN.get(player.getUUID());
        if (open == null || player.server.getTickCount() - open.tick() > OPEN_TICKS) {
            return false;
        }
        return open.admin() || player.position().distanceTo(open.at()) <= CasinoRules.current().watchRange();
    }

    /** 画面の中身（チップ・両替・景品・遊べる遊び・カジノナイト）。 */
    static CompoundTag screenTag(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElseThrow();
        CasinoRecord rec = data.getCasino();
        CasinoRules rules = CasinoRules.current();
        MinecraftServer server = player.server;
        CompoundTag tag = new CompoundTag();
        tag.putLong("chips", rec.chips());
        tag.putLong("knots", Wallet.balance(data));
        tag.putLong("rate", rules.knotsPerChip());
        tag.putLong("limit", rules.dailyChipLimit());
        tag.putLong("bought", rec.boughtOn(ConnectionService.today()));
        tag.putLong("minBet", rules.minBet());
        tag.putLong("maxBet", rules.maxBet());
        tag.putLong("maxRound", rules.maxRoundBet());
        tag.putInt("loseStreak", rec.loseStreak());
        // 11月27日：地下遊技場の持ち玉・持ちメダル（両替の窓口でチップに戻せる）
        tag.putLong("balls", rec.balls());
        tag.putLong("medals", rec.medals());
        tag.putInt("ballsPerChip", rules.parlor().ballsPerChip());
        tag.putInt("medalsPerChip", rules.parlor().medalsPerChip());
        tag.putBoolean("highlow", unlocked(server, Game.HIGHLOW));
        CasinoData cd = CasinoData.get(server);
        tag.putBoolean("night", cd.night());
        tag.putInt("nightHeld", cd.nightHeld());
        tag.putInt("tablePlayers", rules.tablePlayers());
        tag.putDouble("nightBonus", rules.nightBonus());
        ListTag prizes = new ListTag();
        for (CasinoRules.Prize p : rules.prizes()) {
            CompoundTag one = new CompoundTag();
            one.putString("id", p.id());
            one.putString("kind", p.kind());
            one.putLong("chips", p.chips());
            if (p.title() != null) {
                TitleDef def = TitleDefs.get(p.title());
                one.putString("name", def == null ? p.title().toString() : def.name());
                one.putBoolean("owned", data.getGuild().hasTitle(p.title()));
            } else {
                ItemStack stack = prizeStack(p);
                one.put("stack", stack.save(new CompoundTag()));
            }
            prizes.add(one);
        }
        tag.put("prizes", prizes);
        return tag;
    }

    /** 景品の品物（見た目の品は、強さを持たない：守りの値を消し、壊れない）。 */
    public static ItemStack prizeStack(CasinoRules.Prize p) {
        Item item = p.item() == null ? null : ForgeRegistries.ITEMS.getValue(p.item());
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, p.count());
        CompoundTag tag = stack.getOrCreateTag();
        if (p.color() != 0) {
            CompoundTag display = tag.getCompound("display");
            display.putInt("color", p.color());
            tag.put("display", display);
        }
        if ("banner".equals(p.kind())) {
            // ラッキーノットの旗（docs/map_style.md のカジノの旗：紫の地に黄の菱形、黒の飾り縁、白い縁）
            CompoundTag be = new CompoundTag();
            ListTag patterns = new ListTag();
            patterns.add(pattern("mr", 4));
            patterns.add(pattern("cbo", 15));
            patterns.add(pattern("bo", 0));
            be.put("Patterns", patterns);
            tag.put("BlockEntityTag", be);
        }
        if (p.cosmetic()) {
            tag.put("AttributeModifiers", new ListTag());
            tag.putBoolean("Unbreakable", true);
            tag.putInt("HideFlags", 6);
        }
        CompoundTag display = tag.getCompound("display");
        display.putString("Name", Component.Serializer.toJson(Component.translatable("casino.amemaru.prize." + p.id())
                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE))));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable(p.cosmetic() ? "casino.amemaru.prize_lore.cosmetic"
                : "casino.amemaru.prize_lore.prize").withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))));
        display.put("Lore", lore);
        tag.put("display", display);
        return stack;
    }

    private static CompoundTag pattern(String code, int color) {
        CompoundTag one = new CompoundTag();
        one.putString("Pattern", code);
        one.putInt("Color", color);
        return one;
    }

    // ================================================================ 卓を見る

    static void watch(ServerPlayer player, String tableId) {
        CasinoTable table = TABLES.get(tableId);
        if (table == null) {
            return;
        }
        unwatchAll(player.getUUID());
        if (!unlocked(player.server, table.game())) {
            return;
        }
        table.watchers.add(player.getUUID());
        if (table.phase == Phase.IDLE) {
            startBetting(player.server, table);
        }
    }

    static void unwatchAll(UUID who) {
        for (CasinoTable t : TABLES.values()) {
            t.watchers.remove(who);
        }
    }

    private static void startBetting(MinecraftServer server, CasinoTable table) {
        CasinoRules rules = CasinoRules.current();
        table.startBetting(RANDOM, rules.betTicks());
        sync(server, table);
    }

    // ================================================================ 操作

    /** 画面の操作（{@link jp.amemaru.lostconnection.network.c2s.CasinoC2S}）。 */
    public static void onAction(ServerPlayer player, String action, CompoundTag args) {
        if ("unwatch".equals(action)) {
            unwatchAll(player.getUUID());
            return;
        }
        if (!canUse(player)) {
            player.displayClientMessage(Component.translatable("casino.amemaru.far").withStyle(ChatFormatting.RED), true);
            return;
        }
        String error = switch (action) {
            case "watch" -> {
                watch(player, args.getString("table"));
                CasinoTable t = TABLES.get(args.getString("table"));
                if (t != null) {
                    ModNetwork.sendTo(player, new CasinoS2C("table", tableTag(player, t)));
                }
                yield null;
            }
            case "bet" -> bet(player, args.getString("table"), args.getString("kind"), args.getInt("value"), args.getLong("chips"));
            case "clear" -> clear(player, args.getString("table"));
            case "buy" -> buy(player, args.getLong("chips"));
            case "sell" -> sell(player, args.getLong("chips"));
            case "prize" -> exchange(player, args.getString("id"));
            // 11月27日：地下遊技場の持ち玉・持ちメダルを、両替の窓口でチップに戻す
            case "cash_balls" -> {
                jp.amemaru.lostconnection.casino.parlor.ParlorService.cashout(player, "balls");
                yield null;
            }
            case "cash_medals" -> {
                jp.amemaru.lostconnection.casino.parlor.ParlorService.cashout(player, "medals");
                yield null;
            }
            default -> null;
        };
        if (error != null) {
            player.displayClientMessage(Component.translatable(error).withStyle(ChatFormatting.RED), true);
        }
        if (List.of("buy", "sell", "prize", "bet", "clear", "cash_balls", "cash_medals").contains(action)) {
            CompoundTag tag = screenTag(player);
            tag.putBoolean("refresh", true);
            ModNetwork.sendTo(player, new CasinoS2C("screen", tag));
        }
    }

    /** 賭ける。うまくいかなければ、わけの翻訳キー。 */
    @Nullable
    public static String bet(ServerPlayer player, String tableId, String kind, int value, long chips) {
        CasinoTable table = TABLES.get(tableId);
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (table == null || data == null) {
            return "casino.amemaru.error.table";
        }
        if (!unlocked(player.server, table.game())) {
            return "casino.amemaru.error.locked";
        }
        if (table.phase != Phase.BETTING) {
            return "casino.amemaru.error.not_betting";
        }
        CasinoRules rules = CasinoRules.current();
        if (chips < rules.minBet() || chips > rules.maxBet()) {
            return "casino.amemaru.error.bet_range";
        }
        if (table.stakeOf(player.getUUID()) + chips > rules.maxRoundBet()) {
            return "casino.amemaru.error.round_limit";
        }
        if (data.getCasino().chips() < chips) {
            return "casino.amemaru.error.no_chips";
        }
        double mult = switch (table.game()) {
            case ROULETTE -> CasinoGames.rouletteMultiplier(kind, value);
            case HIGHLOW -> CasinoGames.highLowMultiplier(kind, table.card, rules.highLowEdge());
        };
        if (mult <= 0) {
            return "casino.amemaru.error.bad_bet";
        }
        data.getCasino().addChips(-chips);
        List<Bet> mine = table.bets.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        boolean merged = false;
        for (int i = 0; i < mine.size(); i++) {
            Bet b = mine.get(i);
            if (b.kind().equals(kind) && b.value() == value) {
                mine.set(i, new Bet(kind, value, b.chips() + chips, b.multiplier()));
                merged = true;
                break;
            }
        }
        if (!merged) {
            mine.add(new Bet(kind, value, chips, mult));
        }
        table.names.put(player.getUUID(), player.getGameProfile().getName());
        table.seats.put(player.getUUID(), player);
        NAMES.put(player.getUUID(), player.getGameProfile().getName());
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.5F, 1.6F);
        sync(player.server, table);
        return null;
    }

    /** 自分の賭けを取り消す（賭ける時間のうち）。 */
    @Nullable
    public static String clear(ServerPlayer player, String tableId) {
        CasinoTable table = TABLES.get(tableId);
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (table == null || data == null || table.phase != Phase.BETTING) {
            return "casino.amemaru.error.not_betting";
        }
        long stake = table.stakeOf(player.getUUID());
        table.bets.remove(player.getUUID());
        data.getCasino().addChips(stake);
        sync(player.server, table);
        return null;
    }

    /** チップを買う（1日の上限まで）。 */
    @Nullable
    public static String buy(ServerPlayer player, long chips) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        CasinoRules rules = CasinoRules.current();
        if (data == null || chips <= 0) {
            return "casino.amemaru.error.amount";
        }
        String today = ConnectionService.today();
        long left = rules.dailyChipLimit() - data.getCasino().boughtOn(today);
        if (left <= 0) {
            return "casino.amemaru.error.daily_limit";
        }
        long n = Math.min(chips, left);
        if (!Wallet.pay(player, data, n * rules.knotsPerChip(), "casino chips")) {
            return "casino.amemaru.error.no_knots";
        }
        data.getCasino().addChips(n);
        data.getCasino().addBought(today, n);
        player.displayClientMessage(Component.translatable("casino.amemaru.bought", n, n * rules.knotsPerChip())
                .withStyle(ChatFormatting.GREEN), true);
        return null;
    }

    /** チップをノットに戻す。 */
    @Nullable
    public static String sell(ServerPlayer player, long chips) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || chips <= 0) {
            return "casino.amemaru.error.amount";
        }
        long n = Math.min(chips, data.getCasino().chips());
        if (n <= 0) {
            return "casino.amemaru.error.no_chips";
        }
        data.getCasino().addChips(-n);
        long knots = n * CasinoRules.current().knotsPerChip();
        Wallet.give(player, data, knots, "casino cash out");
        player.displayClientMessage(Component.translatable("casino.amemaru.sold", n, knots).withStyle(ChatFormatting.GREEN), true);
        return null;
    }

    /** 景品と取りかえる。 */
    @Nullable
    public static String exchange(ServerPlayer player, String prizeId) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        CasinoRules.Prize prize = CasinoRules.current().prize(prizeId);
        if (data == null || prize == null) {
            return "casino.amemaru.error.prize";
        }
        if (prize.title() != null && data.getGuild().hasTitle(prize.title())) {
            return "casino.amemaru.error.owned";
        }
        if (data.getCasino().chips() < prize.chips()) {
            return "casino.amemaru.error.no_chips";
        }
        data.getCasino().addChips(-prize.chips());
        if (prize.title() != null) {
            TitleService.grant(player, prize.title(), "casino prize");
        } else {
            ItemStack stack = prizeStack(prize);
            Component name = stack.getHoverName();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            player.sendSystemMessage(Component.translatable("casino.amemaru.prize_got", name, prize.chips()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.2F);
        return null;
    }

    // ================================================================ 卓の流れ

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        for (CasinoTable table : TABLES.values()) {
            if (now % 20 == 0) {
                dropFarWatchers(server, table);
            }
            tick(server, table);
        }
        if (now % 20 == 0) {
            CasinoData cd = CasinoData.get(server);
            if (cd.night() && cd.nightEndMillis() > 0 && System.currentTimeMillis() >= cd.nightEndMillis()) {
                endNight(server);
            }
        }
    }

    private static void dropFarWatchers(MinecraftServer server, CasinoTable table) {
        table.watchers.removeIf(who -> {
            ServerPlayer p = server.getPlayerList().getPlayer(who);
            if (p == null) {
                return true;
            }
            Open open = OPEN.get(who);
            return open == null || (!open.admin() && p.position().distanceTo(open.at()) > CasinoRules.current().watchRange());
        });
    }

    /** 1 tick 進める（自動テストからも）。 */
    public static void tick(MinecraftServer server, CasinoTable table) {
        if (table.phase == Phase.IDLE) {
            return;
        }
        if (--table.timer > 0) {
            if (table.phase == Phase.BETTING && table.timer % 20 == 0) {
                sync(server, table);
            }
            return;
        }
        CasinoRules rules = CasinoRules.current();
        switch (table.phase) {
            case BETTING -> {
                if (!table.anyBet()) {
                    if (table.watchers.isEmpty()) {
                        table.phase = Phase.IDLE;
                        table.card = 0;
                    } else {
                        table.timer = rules.betTicks();
                    }
                    sync(server, table);
                    return;
                }
                roll(server, table);
            }
            case ROLLING -> settle(server, table);
            case RESULT -> {
                if (table.watchers.isEmpty()) {
                    table.phase = Phase.IDLE;
                    table.card = 0;
                    sync(server, table);
                } else {
                    if (table.game() == Game.HIGHLOW && table.outcome > 0) {
                        table.card = table.outcome;
                    }
                    startBetting(server, table);
                }
            }
            default -> {
            }
        }
    }

    /** 賭けを締めて、結果を決める（画面は結果へ向けて回る・めくる・走る）。 */
    static void roll(MinecraftServer server, CasinoTable table) {
        CasinoRules rules = CasinoRules.current();
        Integer forced = FORCED.remove(table.id());
        switch (table.game()) {
            case ROULETTE -> table.outcome = forced != null && forced >= 0 && forced <= 36 ? forced : CasinoGames.spinRoulette(RANDOM);
            case HIGHLOW -> table.outcome = forced != null && forced >= 1 && forced <= 13 ? forced : CasinoGames.drawCard(RANDOM);
        }
        table.phase = Phase.ROLLING;
        table.timer = rules.rollTicks();
        table.bettorsThisRound = table.bets.size();
        if (table.game() == Game.ROULETTE) {
            // ジゼルの口ぐせ（Phase 0-A 8章「さあ、運命の輪を回しましょう」）
            for (UUID who : table.watchers) {
                ServerPlayer p = server.getPlayerList().getPlayer(who);
                if (p != null) {
                    p.displayClientMessage(Component.translatable("casino.amemaru.spin_voice").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            }
        }
        sync(server, table);
    }

    /** 結果を払う。 */
    static void settle(MinecraftServer server, CasinoTable table) {
        CasinoRules rules = CasinoRules.current();
        for (UUID who : new ArrayList<>(table.bets.keySet())) {
            if (!table.settled.contains(who)) {
                ServerPlayer p = server.getPlayerList().getPlayer(who);
                if (p == null && table.seats.get(who) instanceof FakePlayer fake) {
                    p = fake;
                }
                if (p != null) {
                    settleOne(p, table);
                }
            }
        }
        table.remember(table.outcome);
        table.phase = Phase.RESULT;
        table.timer = rules.resultTicks();
        sync(server, table);
    }

    /** 1人の賭けを払う（結果の時と、回っている間にいなくなった時）。 */
    static void settleOne(ServerPlayer player, CasinoTable table) {
        UUID who = player.getUUID();
        if (table.settled.contains(who) || !table.bets.containsKey(who) || table.outcome < 0) {
            return;
        }
        table.settled.add(who);
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        CasinoRules rules = CasinoRules.current();
        MinecraftServer server = player.server;
        long stake = table.stakeOf(who);
        long back = table.payoutOf(who);
        boolean full = table.bettorsThisRound >= rules.tablePlayers();
        CasinoData cd = CasinoData.get(server);
        long bonus = 0;
        if (cd.night() && full && back > stake) {
            bonus = (long) Math.floor((back - stake) * rules.nightBonus());
        }
        back += bonus;
        table.payouts.put(who, back);
        CasinoRecord rec = data.getCasino();
        rec.addChips(back);
        rec.recordRound(back - stake);
        cd.account(stake, back);
        if (back > 0) {
            player.sendSystemMessage(Component.translatable(bonus > 0 ? "casino.amemaru.win_bonus" : "casino.amemaru.win",
                    outcomeText(table), back, back - stake, bonus).withStyle(ChatFormatting.GOLD));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        } else {
            player.sendSystemMessage(Component.translatable("casino.amemaru.lose", outcomeText(table), stake).withStyle(ChatFormatting.GRAY));
        }
        // 称号：一点賭けの当たり（大当たり！）・ルーレットで10回続けて外す（運命の女神に笑われた）
        if (table.game() == Game.ROULETTE) {
            boolean straight = false;
            for (Bet b : table.bets.get(who)) {
                if ("number".equals(b.kind()) && table.wins(b)) {
                    straight = true;
                    break;
                }
            }
            if (straight) {
                TitleService.onEvent(player, "roulette_straight_win");
            }
            if (back == 0) {
                rec.setLoseStreak(rec.loseStreak() + 1);
                if (rec.loseStreak() >= 10) {
                    TitleService.onEvent(player, "roulette_lose_streak_10");
                }
            } else {
                rec.setLoseStreak(0);
            }
        }
        // CP：4人以上の卓（13章「カジノで4人以上の卓に参加：1回ごとに1」）・カジノナイトに参加（1夜に1回）
        if (full) {
            ConnectionService.award(player, ConnectionActions.CASINO_TABLE);
        }
        if (cd.night()) {
            UUID nightKey = UUID.nameUUIDFromBytes(("amemaru-casino-night:" + cd.nightHeld()).getBytes(StandardCharsets.UTF_8));
            ConnectionService.award(player, ConnectionActions.EVENT_JOIN, nightKey, 1);
        }
    }

    /** 結果の短い文（ルーレット：17 黒・ハイ＆ロー：Q）。 */
    static Component outcomeText(CasinoTable table) {
        return switch (table.game()) {
            case ROULETTE -> Component.translatable(table.outcome == 0 ? "casino.amemaru.number.green"
                    : CasinoGames.RED.contains(table.outcome) ? "casino.amemaru.number.red" : "casino.amemaru.number.black", table.outcome);
            case HIGHLOW -> Component.literal(cardName(table.card) + " → " + cardName(table.outcome));
        };
    }

    public static String cardName(int card) {
        return switch (card) {
            case 1 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> String.valueOf(card);
        };
    }

    /** 卓を見ている人に、卓の様子を送る。 */
    static void sync(MinecraftServer server, CasinoTable table) {
        for (UUID who : table.watchers) {
            ServerPlayer p = server.getPlayerList().getPlayer(who);
            if (p != null) {
                ModNetwork.sendTo(p, new CasinoS2C("table", tableTag(p, table)));
            }
        }
    }

    /** 画面へ送る卓の様子と、その人の今のチップ（払い戻しのあとの数を画面に出す）。 */
    static CompoundTag tableTag(ServerPlayer player, CasinoTable table) {
        CompoundTag tag = table.stateTag(player.getUUID(), player.getGameProfile().getName());
        tag.putLong("chips", chips(player));
        return tag;
    }

    // ================================================================ カジノナイト

    /** カジノナイトを始める（minutes が 0 なら決まりの長さ）。もう最中なら false。 */
    public static boolean startNight(MinecraftServer server, int minutes) {
        CasinoData cd = CasinoData.get(server);
        if (cd.night()) {
            return false;
        }
        int m = minutes > 0 ? minutes : CasinoRules.current().nightMinutes();
        cd.startNight(System.currentTimeMillis() + m * 60_000L);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("casino.amemaru.night_start", cd.nightHeld(), m,
                Math.round(CasinoRules.current().nightBonus() * 100), CasinoRules.current().tablePlayers()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        titleAll(server, Component.translatable("casino.amemaru.night_title"), Component.translatable("casino.amemaru.night_sub"));
        return true;
    }

    /** カジノナイトを終える。最中でなければ false。 */
    public static boolean endNight(MinecraftServer server) {
        CasinoData cd = CasinoData.get(server);
        if (!cd.night()) {
            return false;
        }
        cd.endNight();
        server.getPlayerList().broadcastSystemMessage(Component.translatable("casino.amemaru.night_end", cd.nightHeld())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return true;
    }

    private static void titleAll(MinecraftServer server, Component title, Component subtitle) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    // ================================================================ 入る・出る

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        leave(player);
        OPEN.remove(player.getUUID());
    }

    /** 卓を離れる：賭ける時間なら賭けを返し、結果が決まっていれば今払う。 */
    static void leave(ServerPlayer player) {
        UUID who = player.getUUID();
        for (CasinoTable table : TABLES.values()) {
            table.watchers.remove(who);
            if (!table.bets.containsKey(who)) {
                continue;
            }
            if (table.phase == Phase.BETTING) {
                long stake = table.stakeOf(who);
                table.bets.remove(who);
                PlayerDataCapability.get(player).ifPresent(d -> d.getCasino().addChips(stake));
                sync(player.server, table);
            } else {
                settleOne(player, table);
            }
        }
    }

    // ================================================================ 管理者・自動テスト

    /** 次の勝負の結果を決めておく（ルーレット：0〜36・ハイ＆ロー：1〜13・魚レース：走路 0〜3）。 */
    public static void forceNext(String tableId, int value) {
        FORCED.put(tableId, value);
    }

    /** 卓を初めに戻す（賭けは返す）。 */
    public static void resetTable(MinecraftServer server, String tableId) {
        CasinoTable table = TABLES.get(tableId);
        if (table == null) {
            return;
        }
        if (table.phase == Phase.BETTING) {
            for (UUID who : new ArrayList<>(table.bets.keySet())) {
                ServerPlayer p = server.getPlayerList().getPlayer(who);
                long stake = table.stakeOf(who);
                if (p != null) {
                    PlayerDataCapability.get(p).ifPresent(d -> d.getCasino().addChips(stake));
                }
            }
        }
        table.bets.clear();
        table.watchers.clear();
        table.settled.clear();
        table.payouts.clear();
        table.phase = Phase.IDLE;
        table.card = 0;
        table.outcome = -1;
        table.history.clear();
        FORCED.remove(tableId);
    }

    /** 自動テスト：見ている人がいなくても、賭ける時間を始める。 */
    public static void startForTest(MinecraftServer server, String tableId) {
        CasinoTable table = TABLES.get(tableId);
        if (table != null) {
            startBetting(server, table);
        }
    }

    /** 自動テスト：賭ける時間を終えて結果まで進める（結果を払う）。 */
    public static void finishForTest(MinecraftServer server, String tableId) {
        CasinoTable table = TABLES.get(tableId);
        if (table == null || table.phase != Phase.BETTING) {
            return;
        }
        roll(server, table);
        settle(server, table);
    }

    /** 自動テスト・管理者：今の卓の賭けを締めて、すぐ回す（CIの写真）。 */
    public static boolean spinNow(MinecraftServer server, String tableId) {
        CasinoTable table = TABLES.get(tableId);
        if (table == null || table.phase != Phase.BETTING || !table.anyBet()) {
            return false;
        }
        roll(server, table);
        return true;
    }

    /** 自動テスト：ハイ＆ローの今のカードを決める。 */
    public static void setCardForTest(String tableId, int card) {
        CasinoTable table = TABLES.get(tableId);
        if (table != null) {
            table.card = card;
        }
    }

    /** 管理者：ハイ＆ローの今のカードを決める（賭ける時間で、だれも賭けていないとき）。決めたら true。 */
    public static boolean setCard(MinecraftServer server, String tableId, int card) {
        CasinoTable table = TABLES.get(tableId);
        if (table == null || table.game() != Game.HIGHLOW || table.phase != Phase.BETTING || table.anyBet() || card < 1 || card > 13) {
            return false;
        }
        table.card = card;
        sync(server, table);
        return true;
    }

    /** チップの数（管理者のコマンドなど）。 */
    public static long chips(ServerPlayer player) {
        return PlayerDataCapability.get(player).map(d -> d.getCasino().chips()).orElse(0L);
    }
}
