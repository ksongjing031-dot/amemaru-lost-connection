package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.economy.EconomyLog;
import jp.amemaru.lostconnection.economy.MarketData;
import jp.amemaru.lostconnection.economy.MarketService;
import jp.amemaru.lostconnection.economy.MarketStalls;
import jp.amemaru.lostconnection.economy.StorageData;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Phase 6 のフリーマーケット：出品（値段の幅・手数料・出せない品）・買う（一度に処理・自分の出品は買えない・値段が変わったら断る）・
 * 売上の受け取り・出品の上限・露店（借りる・ひとりひとつ・露店で買うと手数料が半分）・相場の変動・期限。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final MarketService.Access EXCHANGE = new MarketService.Access(true, false, 0);
    private static final MarketService.Access BOOTH = new MarketService.Access(true, true, 0);

    private MarketGameTests() {
    }

    /**
     * 出品：1個の値段は相場の50〜200%、出品の手数料2%（切り上げ）。結晶果・光のかけら・取引不可の印の品は出せない。
     * 買う：値段が変わっていたら断る。払う（手数料5%は回収、残りは売り手への移動）・品は受け取り箱へ・売上に入る、を一度に。
     * 同じ出品は2回買えない。自分の出品は買えない。売上は「受け取る」で所持金に入る（2回目はなし）。
     */
    @GameTest(template = EMPTY)
    public static void marketListBuyCollect(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int openDay = ServerConfig.MARKET_OPEN_DAY.get();
        ServerConfig.MARKET_OPEN_DAY.set(1);
        FakePlayer seller = fake(helper, "mkt_seller");
        FakePlayer buyer = fake(helper, "mkt_buyer");
        try {
            MarketData market = MarketData.get(server);
            clear(server, seller, buyer);
            PlayerData sd = data(helper, seller);
            PlayerData bd = data(helper, buyer);
            Wallet.set(seller, sd, 100, "gametest");
            Wallet.set(buyer, bd, 1000, "gametest");
            boolean free = MarketService.freeListingDay(server);
            EconomyLog log = EconomyLog.get(server);
            int day = ProgressionData.currentDay(server);
            long moved = log.day(day).moved();

            Item moss = item("moss_iron");
            seller.getInventory().items.set(0, new ItemStack(moss, 5));
            check(helper, MarketService.list(seller, EXCHANGE, 0, 5, 81, "amemaru:moss_iron") == MarketService.Result.OUT_OF_RANGE,
                    "相場（40）の200%を超える値段で出品できました");
            check(helper, MarketService.list(seller, EXCHANGE, 0, 5, 19, "amemaru:moss_iron") == MarketService.Result.OUT_OF_RANGE,
                    "相場（40）の50%より安い値段で出品できました");
            check(helper, MarketService.list(seller, EXCHANGE, 0, 5, 40, "amemaru:ancient_stone") == MarketService.Result.INVALID,
                    "枠の品と違う品の名前で出品できました");
            check(helper, MarketService.list(seller, EXCHANGE, 0, 5, 40, "amemaru:moss_iron") == MarketService.Result.OK,
                    "苔鉄を相場で出品できません");
            long fee = free ? 0 : 4;
            check(helper, sd.getKnots() == 100 - fee && seller.getInventory().items.get(0).isEmpty(),
                    "出品の手数料（2%）か、持ち物から品が抜けていません：" + sd.getKnots());
            check(helper, market.listingsOf(seller.getUUID()).size() == 1, "出品が台帳にありません");
            long id = market.listingsOf(seller.getUUID()).get(0).id;

            seller.getInventory().items.set(1, new ItemStack(item("crystal_fruit_crimson")));
            check(helper, MarketService.list(seller, EXCHANGE, 1, 1, 30, "amemaru:crystal_fruit_crimson") == MarketService.Result.UNTRADEABLE,
                    "結晶果が出品できました");
            seller.getInventory().items.set(1, new ItemStack(item("light_shard")));
            check(helper, MarketService.list(seller, EXCHANGE, 1, 1, 100, "amemaru:light_shard") == MarketService.Result.UNTRADEABLE,
                    "クエストの品（光のかけら）が出品できました");
            seller.getInventory().items.set(1, GearData.bind(new ItemStack(item("ancient_stone_sword"))));
            check(helper, MarketService.list(seller, EXCHANGE, 1, 1, 500, "amemaru:ancient_stone_sword") == MarketService.Result.UNTRADEABLE,
                    "取引不可の印の装備が出品できました");
            seller.getInventory().items.set(1, ItemStack.EMPTY);

            check(helper, MarketService.buy(buyer, EXCHANGE, id, 39, 0) == MarketService.Result.PRICE_CHANGED && bd.getKnots() == 1000,
                    "見ていた値段と違うのに買えました");
            check(helper, MarketService.buy(buyer, MarketService.Access.NONE, id, 40, 0) == MarketService.Result.NO_ACCESS,
                    "手帳も小屋もないのに買えました");
            check(helper, MarketService.buy(seller, EXCHANGE, id, 40, 0) == MarketService.Result.OWN, "自分の出品を買えました");
            check(helper, MarketService.buy(buyer, EXCHANGE, id, 40, 0) == MarketService.Result.OK, "買えません");
            check(helper, bd.getKnots() == 800, "買ったあとの所持金が違います：" + bd.getKnots());
            check(helper, MarketService.buy(buyer, EXCHANGE, id, 40, 0) == MarketService.Result.NOT_FOUND && bd.getKnots() == 800,
                    "同じ出品を2回買えました");
            StorageData.Box box = StorageData.get(server).peek(buyer.getUUID());
            check(helper, box != null && box.received().stream().anyMatch(s -> s.is(moss) && s.getCount() == 5),
                    "買った品が受け取り箱に届いていません");
            check(helper, market.proceeds(seller.getUUID()) == 190, "売上が、手数料5%を引いた190ではありません：" + market.proceeds(seller.getUUID()));
            check(helper, log.day(day).moved() - moved == 190, "売り手への移動が記録されていません");
            check(helper, !market.trades("amemaru:moss_iron").isEmpty() && market.trades("amemaru:moss_iron").get(0).unitPrice() == 40,
                    "取引が記録されていません");

            check(helper, MarketService.collect(seller, EXCHANGE) == MarketService.Result.OK && sd.getKnots() == 100 - fee + 190,
                    "売上を受け取れないか、所持金が違います：" + sd.getKnots());
            check(helper, MarketService.collect(seller, EXCHANGE) == MarketService.Result.NOTHING, "売上を2回受け取れました");
            check(helper, market.history(seller.getUUID()).get(0).kind() == MarketData.Kind.COLLECTED
                    && market.history(buyer.getUUID()).get(0).kind() == MarketData.Kind.BOUGHT, "履歴が残っていません");
        } finally {
            ServerConfig.MARKET_OPEN_DAY.set(openDay);
            clear(server, seller, buyer);
        }
        helper.succeed();
    }

    /**
     * 出品の上限（20件、露店を借りていれば30件）。露店：小屋で借りる（ひとりひとつ・借りられている区画は借りられない・寄付で開く
     * 区画はまだ借りられない・店名の飾りの記号は消す）。露店で直接買うと、売り手の手数料が半分（2.5%）。返すと空く。
     */
    @GameTest(template = EMPTY)
    public static void marketLimitsAndStalls(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int openDay = ServerConfig.MARKET_OPEN_DAY.get();
        ServerConfig.MARKET_OPEN_DAY.set(1);
        FakePlayer owner = fake(helper, "mkt_owner");
        FakePlayer other = fake(helper, "mkt_other");
        try {
            check(helper, MarketStalls.loader().errors().isEmpty() && MarketStalls.all().size() == 20 && MarketStalls.booth() != null,
                    "露店の区画（20）と小屋の定義が読めません：" + MarketStalls.loader().errors());
            MarketData market = MarketData.get(server);
            clear(server, owner, other);
            boolean expanded = market.expanded();
            market.setExpanded(false);
            PlayerData od = data(helper, owner);
            PlayerData xd = data(helper, other);
            Wallet.set(owner, od, 5000, "gametest");
            Wallet.set(other, xd, 5000, "gametest");
            int day = ProgressionData.currentDay(server);
            for (int i = 0; i < 20; i++) {
                market.add(owner.getUUID(), "amt_mkt_owner", new ItemStack(item("ancient_stone")), 10, day, 0, i);
            }
            owner.getInventory().items.set(0, new ItemStack(item("moss_iron"), 4));
            check(helper, MarketService.list(owner, EXCHANGE, 0, 4, 40, "amemaru:moss_iron") == MarketService.Result.LIMIT,
                    "20件を超えて出品できました");

            int free = 0;
            for (int n = 1; n <= 12 && free == 0; n++) {
                if (market.stall(n) == null) {
                    free = n;
                }
            }
            check(helper, free > 0, "空いている区画がありません");
            check(helper, MarketService.rent(owner, EXCHANGE, free, "x") == MarketService.Result.TOO_FAR, "小屋でないのに借りられました");
            check(helper, MarketService.rent(owner, BOOTH, 13, "x") == MarketService.Result.STALL_CLOSED, "寄付で開く区画を借りられました");
            check(helper, MarketService.rent(owner, BOOTH, free, "テスト§c屋") == MarketService.Result.OK, "露店を借りられません");
            MarketData.StallRent rent = market.stall(free);
            check(helper, rent != null && rent.owner.equals(owner.getUUID()) && "テストc屋".equals(rent.name)
                    && rent.lastDay == day + ServerConfig.MARKET_STALL_DAYS.get() - 1, "借りた露店の記録が違います");
            check(helper, od.getKnots() == 5000 - ServerConfig.MARKET_STALL_RENT.get(), "借りる代金を払っていません");
            check(helper, MarketService.rent(owner, BOOTH, free == 1 ? 2 : 1, "y") == MarketService.Result.HAS_STALL, "露店を2つ借りられました");
            check(helper, MarketService.rent(other, BOOTH, free, "z") == MarketService.Result.STALL_TAKEN, "借りられている区画を借りられました");
            check(helper, MarketService.extend(owner, BOOTH) == MarketService.Result.NOT_YET, "残りが1回分あるのに延ばせました");
            check(helper, MarketService.limit(server, owner.getUUID()) == 30, "露店を借りても出品の上限が30になりません");
            check(helper, MarketService.list(owner, EXCHANGE, 0, 4, 40, "amemaru:moss_iron") == MarketService.Result.OK,
                    "露店を借りたのに、21件目を出品できません");
            MarketData.Listing listing = market.listingsOf(owner.getUUID()).get(market.listingsOf(owner.getUUID()).size() - 1);

            MarketService.Access atStall = new MarketService.Access(true, false, free);
            MarketService.Access otherStall = new MarketService.Access(true, false, free == 1 ? 2 : 1);
            check(helper, MarketService.buy(other, otherStall, listing.id, 40, free) == MarketService.Result.TOO_FAR,
                    "その露店にいないのに、露店で買えました");
            check(helper, MarketService.buy(other, atStall, listing.id, 40, free) == MarketService.Result.OK, "露店で買えません");
            check(helper, market.proceeds(owner.getUUID()) == 160 - 4, "露店で買ったときの手数料が半分（2.5%）ではありません："
                    + market.proceeds(owner.getUUID()));

            check(helper, MarketService.release(owner, BOOTH) == MarketService.Result.OK && market.stall(free) == null, "露店を返せません");
            check(helper, MarketService.limit(server, owner.getUUID()) == 20, "露店を返しても、出品の上限が30のままです");
            check(helper, "abcdefghijkl".equals(MarketService.cleanName("abcdefghijklmnop", "x"))
                    && "xの露店".equals(MarketService.cleanName("  ", "x")), "店名の長さの上限か、空の店名の扱いが違います");
            market.setExpanded(expanded);
        } finally {
            ServerConfig.MARKET_OPEN_DAY.set(openDay);
            clear(server, owner, other);
        }
        helper.succeed();
    }

    /**
     * 相場の変動：その日の取引が5件以上の品だけ、値段÷基準価格の中央値に向けて、1日±10%まで動く（4件では動かない）。
     * 期限：下がる日の来た出品は、品が出品者の受け取り箱に戻る。借りる日の過ぎた露店は空く。
     */
    @GameTest(template = EMPTY)
    public static void marketDriftAndExpiry(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer seller = fake(helper, "mkt_expire");
        String key = "amemaru:gametest_drift_item";
        MarketData market = MarketData.get(server);
        try {
            clear(server, seller, seller);
            market.setFactor(key, 1.0);
            int testDay = 9001;
            for (int i = 0; i < 4; i++) {
                market.recordTrade(key, new MarketData.Trade(testDay, 200, 1, 100));
            }
            MarketService.drift(server, market, testDay);
            check(helper, market.factor(key) == 1.0, "取引が4件で相場が動きました");
            market.recordTrade(key, new MarketData.Trade(testDay, 200, 1, 100));
            MarketService.drift(server, market, testDay);
            check(helper, Math.abs(market.factor(key) - 1.1) < 1.0E-9, "5件の取引で、相場が1日の上限（+10%）だけ動きません：" + market.factor(key));
            for (int i = 0; i < 5; i++) {
                market.recordTrade(key, new MarketData.Trade(testDay + 1, 105, 1, 100));
            }
            MarketService.drift(server, market, testDay + 1);
            check(helper, Math.abs(market.factor(key) - 1.05) < 1.0E-9, "相場が取引の中央値（105%）に向かいません：" + market.factor(key));

            int now = ProgressionData.currentDay(server);
            MarketData.Listing old = market.add(seller.getUUID(), "amt_mkt_expire", new ItemStack(item("moss_iron"), 3), 40, now - 3, now, 0);
            MarketData.Listing young = market.add(seller.getUUID(), "amt_mkt_expire", new ItemStack(item("moss_iron"), 2), 40, now, now + 3, 0);
            market.setStall(20, new MarketData.StallRent(seller.getUUID(), "amt_mkt_expire", "期限の店", now - 1));
            MarketService.expire(server, market, now);
            check(helper, market.listing(old.id) == null && market.listing(young.id) != null, "期限の来た出品だけが下がっていません");
            StorageData.Box box = StorageData.get(server).peek(seller.getUUID());
            check(helper, box != null && box.received().stream().anyMatch(s -> s.is(item("moss_iron")) && s.getCount() == 3),
                    "期限で下がった品が受け取り箱に戻っていません");
            check(helper, market.stall(20) == null, "借りる日の過ぎた露店が空いていません");
            check(helper, market.history(seller.getUUID()).stream().anyMatch(h -> h.kind() == MarketData.Kind.EXPIRED), "期限の履歴がありません");
        } finally {
            market.setFactor(key, 1.0);
            market.allTrades().remove(key);
            clear(server, seller, seller);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 小物

    /** 台帳と受け取り箱から、テストの人の分を消す（ほかのテストや遊ぶ人に残さない）。 */
    private static void clear(MinecraftServer server, ServerPlayer... players) {
        MarketData market = MarketData.get(server);
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            for (MarketData.Listing listing : market.listingsOf(id)) {
                market.remove(listing.id);
            }
            market.takeProceeds(id);
            int num = market.stallOf(id);
            if (num > 0) {
                market.setStall(num, null);
            }
            StorageData.Box box = StorageData.get(server).peek(id);
            if (box != null) {
                box.received().clear();
                StorageData.get(server).setDirty();
            }
            player.getInventory().clearContent();
            PlayerDataCapability.get(player).ifPresent(data -> Wallet.set(player, data, 0, "gametest"));
        }
    }

    private static Item item(String name) {
        return ModItems.MANIFEST.get(name).get();
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
