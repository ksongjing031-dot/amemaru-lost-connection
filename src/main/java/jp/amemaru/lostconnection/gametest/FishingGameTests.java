package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.connection.ConnectionCategory;
import jp.amemaru.lostconnection.connection.ConnectionService;
import jp.amemaru.lostconnection.economy.MarketPrices;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.fishing.FishDef;
import jp.amemaru.lostconnection.fishing.FishDefs;
import jp.amemaru.lostconnection.fishing.FishItem;
import jp.amemaru.lostconnection.fishing.FishingData;
import jp.amemaru.lostconnection.fishing.FishingRules;
import jp.amemaru.lostconnection.fishing.FishingService;
import jp.amemaru.lostconnection.fishing.FishingTournament;
import jp.amemaru.lostconnection.guild.RecordDefs;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 7 その2：釣り（魚の表・かかる魚の決め方・釣り上げたあと・売る・留め糸と交換・ミニゲームの結果・釣り大会）。
 * 偽のプレイヤーはサーバーの一覧にいないので、「いない人には次に入ったときに」（大会の賞品と参加のCP）の道も、ここで確かめられる。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FishingGameTests {
    private static final String EMPTY = "empty3x3x3";

    private FishingGameTests() {
    }

    /** 魚の表：40種（章ごとに10種・記憶魚4・湖の主1）が読める。アイテムがあり、★が合う。記憶魚の記録がある。 */
    @GameTest(template = EMPTY)
    public static void fishingDefinitionsLoad(GameTestHelper helper) {
        check(helper, FishDefs.loader().errors().isEmpty() && FishDefs.all().size() == 40,
                "魚の表が読めません（" + FishDefs.all().size() + "）：" + FishDefs.loader().errors());
        check(helper, FishingRules.loader().errors().isEmpty() && FishingRules.loader().get(FishingRules.DEFAULT_ID).isPresent(),
                "釣りの決まりが読めません：" + FishingRules.loader().errors());
        int memories = 0;
        int lords = 0;
        Map<Integer, Integer> chapters = new HashMap<>();
        for (FishDef def : FishDefs.all().values()) {
            Item item = FishingService.itemOf(def);
            check(helper, item instanceof FishItem fish && fish.stars() == def.rarity(), def.id() + " のアイテムがないか、★が合いません");
            if (def.isMemory()) {
                memories++;
                check(helper, RecordDefs.get(def.memory()) != null && RecordDefs.get(def.memory()).floor() == 0,
                        def.id() + " の記憶の記録（塔の外）がありません");
            }
            if (def.legendary()) {
                lords++;
                check(helper, def.spots().size() == 1 && def.spots().contains("lake"), "湖の主が湖だけにいません");
            } else {
                chapters.merge(def.chapter(), 1, Integer::sum);
            }
        }
        check(helper, memories == 4 && lords == 1, "記憶魚が4種・湖の主が1種ではありません（" + memories + "・" + lords + "）");
        for (int chapter = 1; chapter <= 4; chapter++) {
            check(helper, chapters.getOrDefault(chapter, 0) == 10, "第" + chapter + "章の魚が10種ではありません：" + chapters);
        }
        helper.succeed();
    }

    /** かかる魚：釣り場と章で決まる。川では記憶魚・湖の主・湖だけの魚はかからない。★1がいちばん多い。 */
    @GameTest(template = EMPTY)
    public static void fishingRoll(GameTestHelper helper) {
        RandomSource random = RandomSource.create(20261123L);
        int common = 0;
        int n = 3000;
        for (int i = 0; i < n; i++) {
            FishDef pond = FishingService.roll(random, "pond", 1);
            check(helper, pond != null && pond.chapter() == 1 && pond.spots().contains("pond") && !pond.legendary(),
                    "第1章の釣り堀で、ほかの章か、釣り堀にいない魚がかかりました：" + pond);
            if (pond.rarity() == 1) {
                common++;
            }
            FishDef river = FishingService.roll(random, "river", 4);
            check(helper, river != null && river.spots().contains("river") && !river.isMemory() && !river.legendary(),
                    "川で、川にいない魚・記憶魚・湖の主がかかりました：" + river);
        }
        double share = common * 1.0 / n;
        check(helper, share > 0.45 && share < 0.80, "★1の割合がおかしいです（" + share + "）");
        boolean chapter2 = false;
        for (int i = 0; i < 400 && !chapter2; i++) {
            FishDef def = FishingService.roll(random, "pond", 2);
            chapter2 = def != null && def.chapter() == 2;
        }
        check(helper, chapter2, "第2章になっても、第2章の魚がかかりません");
        FishDef lord = FishDefs.get(AmemaruMod.id("memory_lord"));
        double size = FishingService.rollSize(random, lord);
        check(helper, size >= lord.sizeMin() && size <= lord.sizeMax(), "大きさが範囲の外です：" + size);
        helper.succeed();
    }

    /** 釣り上げた：アイテム・図鑑・初めての魚のCP・記憶魚の記録と称号・湖の主（最初の一人と限定称号）・図鑑の称号。 */
    @GameTest(template = EMPTY)
    public static void fishingCatch(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ConnectionService.setClockForTest(() -> LocalDateTime.of(2026, 11, 23, 9, 0));
        FakePlayer a = fake(helper, "fish_catch_a");
        try {
            ConnectionService.adminReset(server);
            FishingService.resetLord(server);
            PlayerData data = data(helper, a);
            data.getFishing().clear();
            FishDef dace = FishDefs.get(AmemaruMod.id("pebble_dace"));
            FishingService.catchFish(a, dace, 12.0, "pond", true);
            check(helper, a.getInventory().countItem(FishingService.itemOf(dace)) == 1 && data.getFishing().has(dace.id())
                    && data.getFishing().entry(dace.id()).best() == 12.0, "釣った魚が持ち物と図鑑に入りません");
            check(helper, data.getConnection().today(ConnectionCategory.PLAY) == 5, "初めての魚で、魚図鑑のCP（5）が入りません（"
                    + data.getConnection().today(ConnectionCategory.PLAY) + "）");
            FishingService.catchFish(a, dace, 14.9, "pond", true);
            check(helper, data.getConnection().today(ConnectionCategory.PLAY) == 5 && data.getFishing().entry(dace.id()).count() == 2
                    && data.getFishing().entry(dace.id()).best() == 14.9, "2匹目で図鑑のCPが入ったか、数といちばん大きいのが変わりません");
            // 大物（大きさの割合が 0.9 以上）は大きさを持ち、ほかと重ならない
            ItemStack big = findBig(a, dace);
            check(helper, big != null && FishItem.size(big) == 14.9, "大物の印がついていません");
            // 記憶魚：記録帳に記憶が入る。4種そろうと称号「記憶を釣り上げた者」
            for (FishDef def : FishDefs.ordered()) {
                if (def.isMemory()) {
                    FishingService.catchFish(a, def, 25.0, "pond", false);
                    check(helper, data.getGuild().records().containsKey(def.memory()), def.id() + " の記憶が記録帳に入りません");
                }
            }
            check(helper, data.getGuild().hasTitle(AmemaruMod.id("memory_angler")), "記憶魚をそろえても、称号がつきません");
            // 湖の主：最初の一人を覚え、限定称号「最初の大物」
            FishDef lord = FishDefs.get(AmemaruMod.id("memory_lord"));
            FishingService.catchFish(a, lord, 250.0, "lake", false);
            check(helper, FishingData.get(server).lord() != null && a.getUUID().equals(FishingData.get(server).lord().who())
                    && data.getGuild().hasTitle(AmemaruMod.id("first_big_catch")), "湖の主の最初の一人を覚えていないか、限定称号がつきません");
            // 図鑑をすべて埋めると、釣り見習い・釣り名人・湖を知る者
            for (FishDef def : FishDefs.ordered()) {
                data.getFishing().record(def.id(), def.sizeMin(), 1);
            }
            jp.amemaru.lostconnection.guild.TitleService.check(a);
            check(helper, FishingService.percent(data) == 100 && data.getGuild().hasTitle(AmemaruMod.id("fisher_1"))
                    && data.getGuild().hasTitle(AmemaruMod.id("fisher_3")), "図鑑を埋めても、釣りの称号がつきません");
        } finally {
            FishingService.resetLord(server);
            ConnectionService.setClockForTest(null);
            ConnectionService.adminReset(server);
        }
        helper.succeed();
    }

    /** 売る（大物は1.3倍。まとめて売るときは記憶魚と湖の主を除く）と、留め糸と交換（10点で1本、端数は残す）。 */
    @GameTest(template = EMPTY)
    public static void fishingSellAndTrade(GameTestHelper helper) {
        FakePlayer a = fake(helper, "fish_sell_a");
        PlayerData data = data(helper, a);
        a.getInventory().clearContent();
        data.getFishing().clear();
        FishDef dace = FishDefs.get(AmemaruMod.id("pebble_dace"));
        FishDef crystal = FishDefs.get(AmemaruMod.id("crystal_trout"));
        FishDef memory = FishDefs.get(AmemaruMod.id("memory_dawn"));
        ItemStack plain = new ItemStack(FishingService.itemOf(dace), 3);
        ItemStack big = new ItemStack(FishingService.itemOf(dace));
        FishItem.markBig(big, 14.8, "amt_fish_sell_a", 1);
        check(helper, FishingService.price(plain) == dace.price() && FishingService.price(big) == Math.round(dace.price() * 1.3),
                "魚の値段（大物は1.3倍）がちがいます");
        check(helper, MarketPrices.base(plain) == dace.price(), "フリーマーケットの基準の値に、魚の値段が入っていません");
        a.getInventory().setItem(0, plain);
        a.getInventory().setItem(1, big);
        a.getInventory().setItem(2, new ItemStack(FishingService.itemOf(memory)));
        long before = Wallet.balance(data);
        long sold = FishingService.sell(a, 0);
        check(helper, sold == dace.price() * 3 && Wallet.balance(data) == before + sold && a.getInventory().getItem(0).isEmpty(),
                "1か所の魚を売れません");
        long all = FishingService.sellAll(a);
        check(helper, all == Math.round(dace.price() * 1.3) && !a.getInventory().getItem(2).isEmpty(),
                "まとめて売るときに、記憶魚まで売ったか、大物を売っていません");
        // 交換：★2（2点）を6匹 ＝ 12点 → 留め糸1本、のこり2点。記憶魚は交換しない
        a.getInventory().setItem(3, new ItemStack(FishingService.itemOf(crystal), 6));
        int points = FishingService.tradeAll(a);
        Item thread = BuiltInRegistries.ITEM.get(AmemaruMod.id("binding_thread"));
        check(helper, points == 12 && a.getInventory().countItem(thread) == 1 && data.getFishing().points() == 2
                && !a.getInventory().getItem(2).isEmpty(), "留め糸との交換がちがいます（" + points + "点・のこり " + data.getFishing().points() + "）");
        a.getInventory().clearContent();
        helper.succeed();
    }

    /** ミニゲーム：かけた魚は、勝てば釣り上げ、負ければ何も入らない。早すぎる「勝った」は数えない。 */
    @GameTest(template = EMPTY)
    public static void fishingMinigameResult(GameTestHelper helper) {
        FakePlayer a = fake(helper, "fish_game_a");
        PlayerData data = data(helper, a);
        a.getInventory().clearContent();
        data.getFishing().clear();
        FishDef dace = FishDefs.get(AmemaruMod.id("silver_stripe"));
        FishingService.startSession(a, dace, 20.0, "pond");
        check(helper, FishingService.inSession(a), "ミニゲームが始まりません");
        FishingService.finishSessionForTest(a, false);
        check(helper, !FishingService.inSession(a) && a.getInventory().countItem(FishingService.itemOf(dace)) == 0, "負けたのに魚が入りました");
        FishingService.startSession(a, dace, 20.0, "pond");
        FishingService.finishSessionForTest(a, true);
        check(helper, a.getInventory().countItem(FishingService.itemOf(dace)) == 1 && data.getFishing().has(dace.id()), "勝っても魚が入りません");
        // 始めてすぐの「勝った」（クライアントが送ってきても、時間が足りない）
        FishingService.startSession(a, dace, 20.0, "pond");
        net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
        args.putInt("id", -1);
        args.putBoolean("success", true);
        FishingService.onAction(a, "result", args);
        check(helper, FishingService.inSession(a), "ちがう番号の結果で、ミニゲームが終わりました");
        FishingService.clearSessionForTest(a);
        a.getInventory().clearContent();
        helper.succeed();
    }

    /** 釣り大会：会場で釣った一匹の点（★の倍率 ×（50＋50×大きさの割合））・いちばんの一匹で順位・会場の外は数えない・賞品と参加のCP。 */
    @GameTest(template = EMPTY)
    public static void fishingTournament(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer a = fake(helper, "fish_tour_a");
        FakePlayer b = fake(helper, "fish_tour_b");
        try {
            FishingTournament.finish(server, false);
            check(helper, FishingTournament.start(server, 1, 0) && FishingTournament.running(server), "釣り大会が始まりません");
            check(helper, !FishingTournament.start(server, 1, 0), "大会の最中に、もう一つ始まりました");
            FishDef dace = FishDefs.get(AmemaruMod.id("pebble_dace"));
            FishDef carp = FishDefs.get(AmemaruMod.id("amber_carp"));
            FishingService.catchFish(a, dace, dace.sizeMax(), "pond", true);
            FishingService.catchFish(a, carp, carp.sizeMin(), "pond", true);
            FishingService.catchFish(b, carp, carp.sizeMax(), "river", true);
            FishingData fd = FishingData.get(server);
            long expected = FishingTournament.score(carp, carp.sizeMin());
            check(helper, expected == Math.round(FishingRules.current().tournamentMultiplier(3) * 50.0)
                    && FishingTournament.score(dace, dace.sizeMax()) == 100, "大会の点の計算がちがいます");
            check(helper, fd.ranking().size() == 1 && fd.best(a.getUUID()) != null && fd.best(a.getUUID()).score() == Math.max(100, expected)
                    && fd.best(a.getUUID()).catches() == 2, "いちばんの一匹で数えていないか、会場の外の一匹を数えました");
            FishingTournament.finish(server, true);
            check(helper, !fd.tournamentOn(), "大会が終わりません");
        } finally {
            FishingTournament.finish(server, false);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    private static ItemStack findBig(ServerPlayer player, FishDef def) {
        for (ItemStack stack : player.getInventory().items) {
            if (FishDefs.of(stack) == def && FishItem.isBig(stack)) {
                return stack;
            }
        }
        return null;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "amt_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, profileName));
    }

    private static PlayerData data(GameTestHelper helper, ServerPlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> {
            helper.fail("プレイヤーデータが付いていません");
            return new IllegalStateException("no player data");
        });
    }
}
