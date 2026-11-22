package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.EventCommands;
import jp.amemaru.lostconnection.casino.CasinoData;
import jp.amemaru.lostconnection.casino.CasinoGames;
import jp.amemaru.lostconnection.casino.CasinoRules;
import jp.amemaru.lostconnection.casino.CasinoService;
import jp.amemaru.lostconnection.casino.CasinoTable;
import jp.amemaru.lostconnection.connection.ConnectionCategory;
import jp.amemaru.lostconnection.connection.ConnectionService;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.fishing.FishDef;
import jp.amemaru.lostconnection.fishing.FishDefs;
import jp.amemaru.lostconnection.fishing.FishingService;
import jp.amemaru.lostconnection.guild.TitleDefs;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Phase 7 その3：カジノ（決まりの読み込み・ルーレットの配当・チップの両替と1日の上限・卓の勝負と4人以上の卓・カジノナイト・称号・
 * ハイ＆ロー・魚レース・景品）。偽のプレイヤーはサーバーの一覧にいないので、卓は賭けた人の席から払う。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CasinoGameTests {
    private static final String EMPTY = "empty3x3x3";

    private CasinoGameTests() {
    }

    /** 決まり：倍率と上限・右クリックの所6つ・景品（品物があり、称号がある。見た目の品は強さを持たない）。 */
    @GameTest(template = EMPTY)
    public static void casinoDefinitionsLoad(GameTestHelper helper) {
        check(helper, CasinoRules.loader().errors().isEmpty() && CasinoRules.loader().get(CasinoRules.DEFAULT_ID).isPresent(),
                "カジノの決まりが読めません：" + CasinoRules.loader().errors());
        CasinoRules rules = CasinoRules.current();
        check(helper, rules.openDay() == 3 && rules.dailyChipLimit() > 0 && rules.knotsPerChip() > 0, "開場の日・1日の上限・チップの値がおかしいです");
        check(helper, rules.zones().keySet().containsAll(CasinoService.ZONES), "右クリックの所が足りません：" + rules.zones().keySet());
        check(helper, !rules.prizes().isEmpty(), "景品がありません");
        boolean thread = false;
        boolean title = false;
        boolean cosmetic = false;
        for (CasinoRules.Prize p : rules.prizes()) {
            if (p.title() != null) {
                check(helper, TitleDefs.get(p.title()) != null, "景品の称号がありません：" + p.title());
                title = true;
                continue;
            }
            ItemStack stack = CasinoService.prizeStack(p);
            check(helper, !stack.isEmpty(), "景品の品物がありません：" + p.id());
            thread |= AmemaruMod.id("binding_thread").equals(p.item());
            if (p.cosmetic()) {
                cosmetic = true;
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    check(helper, stack.getAttributeModifiers(slot).get(Attributes.ARMOR).isEmpty()
                            && stack.getAttributeModifiers(slot).get(Attributes.ARMOR_TOUGHNESS).isEmpty(),
                            "見た目の品に守りの値があります：" + p.id());
                }
                check(helper, stack.getTag() != null && stack.getTag().getBoolean("Unbreakable"), "見た目の品が壊れます：" + p.id());
            }
        }
        check(helper, thread && title && cosmetic, "景品に留め糸・称号・見た目の品がそろっていません（15章）");
        helper.succeed();
    }

    /** ルーレット：37の目（0が一つ）・赤18・賭け方ごとの当たりと倍率・胴元の取り分（どの賭けも 36/37）。 */
    @GameTest(template = EMPTY)
    public static void casinoRouletteRules(GameTestHelper helper) {
        check(helper, CasinoGames.WHEEL.size() == 37 && new HashSet<>(CasinoGames.WHEEL).size() == 37 && CasinoGames.RED.size() == 18,
                "輪の目がおかしいです");
        String[] kinds = {"number", "red", "black", "odd", "even", "low", "high", "dozen", "column"};
        int[] values = {17, 0, 0, 0, 0, 0, 0, 2, 2};
        for (int k = 0; k < kinds.length; k++) {
            double mult = CasinoGames.rouletteMultiplier(kinds[k], values[k]);
            int wins = 0;
            for (int n = 0; n <= 36; n++) {
                if (CasinoGames.rouletteWins(kinds[k], values[k], n)) {
                    wins++;
                }
            }
            check(helper, Math.abs(wins * mult - 36.0) < 1e-9, kinds[k] + " の当たりの数と倍率が合いません（" + wins + "×" + mult + "）");
            check(helper, !CasinoGames.rouletteWins(kinds[k], values[k], 0) || "number".equals(kinds[k]) && values[k] == 0,
                    "0 で外の賭けが当たりました：" + kinds[k]);
        }
        check(helper, CasinoGames.rouletteWins("column", 1, 34) && CasinoGames.rouletteWins("dozen", 3, 25)
                && CasinoGames.rouletteWins("red", 0, 32) && CasinoGames.rouletteWins("black", 0, 33), "列・ダズン・赤黒の決まりがちがいます");
        check(helper, CasinoGames.rouletteMultiplier("number", 37) == 0 && CasinoGames.rouletteMultiplier("dozen", 4) == 0
                && CasinoGames.rouletteMultiplier("split", 1) == 0, "ない賭け方に倍率がつきました");
        helper.succeed();
    }

    /** 両替：ノットでチップを買う（1日の上限。4時に戻る）・ノットに戻す・景品と取りかえる（留め糸・称号）。 */
    @GameTest(template = EMPTY)
    public static void casinoChipsAndPrizes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer a = fake(helper, "cas_chips_a");
        PlayerData data = data(helper, a);
        CasinoRules rules = CasinoRules.current();
        try {
            ConnectionService.setClockForTest(() -> LocalDateTime.of(2026, 11, 23, 12, 0));
            data.getCasino().clear();
            a.getInventory().clearContent();
            Wallet.take(a, data, Wallet.balance(data), "gametest");
            Wallet.give(a, data, rules.dailyChipLimit() * rules.knotsPerChip() * 3, "gametest");
            check(helper, CasinoService.buy(a, 100) == null && data.getCasino().chips() == 100, "チップが買えません");
            check(helper, CasinoService.buy(a, Long.MAX_VALUE) == null && data.getCasino().chips() == rules.dailyChipLimit(),
                    "1日の上限まで買えないか、上限をこえて買えました：" + data.getCasino().chips());
            check(helper, "casino.amemaru.error.daily_limit".equals(CasinoService.buy(a, 1)), "上限のあとも買えました");
            ConnectionService.setClockForTest(() -> LocalDateTime.of(2026, 11, 24, 4, 30));
            check(helper, CasinoService.buy(a, 1) == null, "次の日（4時のあと）に買えません");
            long knots = Wallet.balance(data);
            check(helper, CasinoService.sell(a, 11) == null && Wallet.balance(data) == knots + 11 * rules.knotsPerChip(), "ノットに戻せません");
            long chips = data.getCasino().chips();
            CasinoRules.Prize thread = rules.prize("thread");
            check(helper, thread != null && CasinoService.exchange(a, "thread") == null
                    && data.getCasino().chips() == chips - thread.chips()
                    && a.getInventory().countItem(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(AmemaruMod.id("binding_thread"))) == 1,
                    "留め糸と取りかえられません");
            data.getCasino().setChips(5000);
            check(helper, CasinoService.exchange(a, "regular") == null && data.getGuild().hasTitle(AmemaruMod.id("lucky_regular")),
                    "称号と取りかえられません");
            check(helper, "casino.amemaru.error.owned".equals(CasinoService.exchange(a, "regular")), "持っている称号をもう一度取りかえられました");
            data.getCasino().setChips(0);
            check(helper, "casino.amemaru.error.no_chips".equals(CasinoService.exchange(a, "thread")), "チップがなくても取りかえられました");
        } finally {
            ConnectionService.setClockForTest(null);
            data.getCasino().clear();
            a.getInventory().clearContent();
        }
        helper.succeed();
    }

    /**
     * ルーレットの卓：4人で賭けて、決めた結果（17）で払う（一点・奇数・赤）。4人以上の卓のCP・称号「大当たり！」・カジノナイトの
     * おまけ。10回続けて外すと「運命の女神に笑われた」。賭けを取り消すとチップが戻る。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void casinoRouletteRound(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<FakePlayer> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            players.add(fake(helper, "cas_roul_" + i));
        }
        CasinoData cd = CasinoData.get(server);
        try {
            ConnectionService.setClockForTest(() -> LocalDateTime.of(2026, 11, 23, 12, 0));
            ConnectionService.adminReset(server);
            CasinoService.endNight(server);
            CasinoService.resetTable(server, "roulette");
            for (FakePlayer p : players) {
                PlayerData d = data(helper, p);
                d.getCasino().clear();
                d.getCasino().setChips(1000);
                d.getConnection().load(new net.minecraft.nbt.CompoundTag());
            }
            CasinoService.startForTest(server, "roulette");
            FakePlayer a = players.get(0);
            check(helper, CasinoService.bet(a, "roulette", "red", 0, 50) == null && CasinoService.clear(a, "roulette") == null
                    && data(helper, a).getCasino().chips() == 1000, "賭けを取り消しても、チップが戻りません");
            check(helper, "casino.amemaru.error.bet_range".equals(CasinoService.bet(a, "roulette", "red", 0, 100_000)),
                    "上限をこえて賭けられました");
            check(helper, CasinoService.bet(a, "roulette", "number", 17, 10) == null, "一点賭けができません");
            check(helper, CasinoService.bet(players.get(1), "roulette", "odd", 0, 100) == null, "奇数に賭けられません");
            check(helper, CasinoService.bet(players.get(2), "roulette", "red", 0, 100) == null, "赤に賭けられません");
            check(helper, CasinoService.bet(players.get(3), "roulette", "dozen", 2, 30) == null, "ダズンに賭けられません");
            CasinoService.forceNext("roulette", 17);
            CasinoService.finishForTest(server, "roulette");
            check(helper, data(helper, a).getCasino().chips() == 1000 - 10 + 360, "一点賭けの払い戻しがちがいます：" + data(helper, a).getCasino().chips());
            check(helper, data(helper, players.get(1)).getCasino().chips() == 1100, "奇数の払い戻しがちがいます");
            check(helper, data(helper, players.get(2)).getCasino().chips() == 900, "17は黒なのに、赤が当たりました");
            check(helper, data(helper, players.get(3)).getCasino().chips() == 1000 - 30 + 90, "ダズンの払い戻しがちがいます");
            check(helper, data(helper, a).getGuild().hasTitle(AmemaruMod.id("jackpot")), "一点賭けを当てても「大当たり！」がつきません");
            for (FakePlayer p : players) {
                check(helper, data(helper, p).getConnection().today(ConnectionCategory.PLAY) == 1, "4人以上の卓のCPが入りません：" + p.getGameProfile().getName());
            }
            // カジノナイト：4人以上の卓は、勝ちのチップがおまけで増える（10%）
            check(helper, EventCommands.start(server, "casino_night", 5) && cd.night(), "カジノナイトが始まりません");
            CasinoService.resetTable(server, "roulette");
            CasinoService.startForTest(server, "roulette");
            for (FakePlayer p : players) {
                check(helper, CasinoService.bet(p, "roulette", "black", 0, 100) == null, "黒に賭けられません");
            }
            long before = data(helper, a).getCasino().chips();
            CasinoService.forceNext("roulette", 2);
            CasinoService.finishForTest(server, "roulette");
            long bonus = (long) Math.floor(100 * CasinoRules.current().nightBonus());
            check(helper, data(helper, a).getCasino().chips() == before - 100 + 200 + bonus, "カジノナイトのおまけがちがいます："
                    + (data(helper, a).getCasino().chips() - before));
            check(helper, data(helper, a).getConnection().today(ConnectionCategory.PLAY) == 2 + 20, "カジノナイトに参加のCPが入りません："
                    + data(helper, a).getConnection().today(ConnectionCategory.PLAY));
            check(helper, EventCommands.stop(server, "casino_night") && !cd.night(), "カジノナイトが終わりません");
            // 10回続けて外す
            FakePlayer b = players.get(2);
            data(helper, b).getCasino().setLoseStreak(0);
            for (int i = 0; i < 10; i++) {
                CasinoService.resetTable(server, "roulette");
                CasinoService.startForTest(server, "roulette");
                check(helper, CasinoService.bet(b, "roulette", "number", 5, 1) == null, "賭けられません（" + i + "回目）");
                CasinoService.forceNext("roulette", 6);
                CasinoService.finishForTest(server, "roulette");
            }
            check(helper, data(helper, b).getCasino().loseStreak() == 10 && data(helper, b).getGuild().hasTitle(AmemaruMod.id("laughed_at")),
                    "10回続けて外しても「運命の女神に笑われた」がつきません");
        } finally {
            CasinoService.endNight(server);
            CasinoService.resetTable(server, "roulette");
            ConnectionService.setClockForTest(null);
            ConnectionService.adminReset(server);
            for (FakePlayer p : players) {
                data(helper, p).getCasino().clear();
            }
        }
        helper.succeed();
    }

    /** ハイ＆ロー：共同事業②の印までは遊べない。今のカード7でハイは ×2.10、次が10なら当たり。同じ数ははずれ。 */
    @GameTest(template = EMPTY)
    public static void casinoHighLow(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer a = fake(helper, "cas_hl_a");
        StoryState story = StoryState.get(server);
        boolean had = story.has(CasinoRules.current().highLowFlag());
        try {
            story.remove(CasinoRules.current().highLowFlag());
            data(helper, a).getCasino().setChips(1000);
            CasinoService.resetTable(server, "highlow_a");
            CasinoService.startForTest(server, "highlow_a");
            check(helper, "casino.amemaru.error.locked".equals(CasinoService.bet(a, "highlow_a", "high", 0, 10)), "印の前にハイ＆ローで遊べました");
            story.add(CasinoRules.current().highLowFlag());
            check(helper, CasinoGames.highLowMultiplier("high", 7, 0.97) == 2.10 && CasinoGames.highLowMultiplier("low", 1, 0.97) == 0
                    && CasinoGames.highLowMultiplier("high", 1, 0.97) == 1.05, "ハイ＆ローの倍率がちがいます");
            CasinoService.setCardForTest("highlow_a", 7);
            check(helper, CasinoService.bet(a, "highlow_a", "high", 0, 100) == null, "ハイに賭けられません");
            CasinoService.forceNext("highlow_a", 10);
            CasinoService.finishForTest(server, "highlow_a");
            check(helper, data(helper, a).getCasino().chips() == 1000 - 100 + 210, "ハイの払い戻しがちがいます：" + data(helper, a).getCasino().chips());
            CasinoService.resetTable(server, "highlow_a");
            CasinoService.startForTest(server, "highlow_a");
            CasinoService.setCardForTest("highlow_a", 7);
            check(helper, CasinoService.bet(a, "highlow_a", "low", 0, 100) == null, "ローに賭けられません");
            CasinoService.forceNext("highlow_a", 7);
            CasinoService.finishForTest(server, "highlow_a");
            check(helper, data(helper, a).getCasino().chips() == 1110 - 100, "同じ数で当たりになりました");
        } finally {
            if (!had) {
                story.remove(CasinoRules.current().highLowFlag());
            }
            CasinoService.resetTable(server, "highlow_a");
            data(helper, a).getCasino().clear();
        }
        helper.succeed();
    }

    /** 魚レース：共同事業⑥の印のあと。倍率は見込みから（胴元の取り分つき）。釣った魚を出せる（だれかが賭ける前まで）。勝てば払う。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void casinoFishRace(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer a = fake(helper, "cas_race_a");
        FakePlayer b = fake(helper, "cas_race_b");
        StoryState story = StoryState.get(server);
        boolean had = story.has(CasinoRules.current().fishRaceFlag());
        try {
            story.add(CasinoRules.current().fishRaceFlag());
            CasinoService.resetTable(server, "fishrace");
            CasinoService.startForTest(server, "fishrace");
            CasinoTable table = CasinoService.table("fishrace");
            check(helper, table != null && table.runners().size() == 4, "走る魚が4匹いません");
            FishDef dace = FishDefs.get(AmemaruMod.id("pebble_dace"));
            CasinoService.setRunnersForTest("fishrace", List.of(CasinoGames.Runner.of(dace, 0.5, null, ""),
                    CasinoGames.Runner.of(dace, 0.5, null, ""), CasinoGames.Runner.of(dace, 0.5, null, ""), CasinoGames.Runner.of(dace, 0.5, null, "")));
            for (int i = 0; i < 4; i++) {
                double m = table.multiplier(i);
                check(helper, m >= 3.0 && m <= 4.8, "同じ魚4匹の倍率が ×3.8 前後になりません：" + m);
            }
            double[] chances = CasinoGames.raceChances(table.runners(), 7L, 3000);
            double sum = 0;
            for (double c : chances) {
                sum += c;
            }
            check(helper, Math.abs(sum - 1.0) < 1e-9, "勝つ見込みの合計が1になりません");
            // 魚を出す（持っている魚。賭けの前まで）
            a.getInventory().clearContent();
            a.getInventory().add(new ItemStack(FishingService.itemOf(FishDefs.get(AmemaruMod.id("amber_carp")))));
            check(helper, CasinoService.enter(a, "fishrace", 0) == null && a.getUUID().equals(table.runners().get(0).owner()),
                    "魚を出せません");
            check(helper, "casino.amemaru.error.entered".equals(CasinoService.enter(a, "fishrace", 0)), "同じ勝負に2匹出せました");
            data(helper, b).getCasino().setChips(500);
            check(helper, CasinoService.bet(b, "fishrace", "lane", 2, 100) == null, "走路に賭けられません");
            double mult = table.multiplier(2);
            b.getInventory().clearContent();
            b.getInventory().add(new ItemStack(FishingService.itemOf(dace)));
            check(helper, "casino.amemaru.error.entry_closed".equals(CasinoService.enter(b, "fishrace", 0)), "賭けのあとに魚を出せました");
            CasinoService.forceNext("fishrace", 2);
            CasinoService.finishForTest(server, "fishrace");
            check(helper, table.outcome() == 2 && data(helper, b).getCasino().chips() == 400 + (long) Math.floor(100 * mult),
                    "魚レースの払い戻しがちがいます：" + data(helper, b).getCasino().chips() + "（×" + mult + "）");
        } finally {
            if (!had) {
                story.remove(CasinoRules.current().fishRaceFlag());
            }
            CasinoService.resetTable(server, "fishrace");
            data(helper, a).getCasino().clear();
            data(helper, b).getCasino().clear();
            a.getInventory().clearContent();
            b.getInventory().clearContent();
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

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
