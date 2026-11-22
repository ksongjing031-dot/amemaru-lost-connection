package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.economy.EconomyLog;
import jp.amemaru.lostconnection.economy.KnotTransfer;
import jp.amemaru.lostconnection.economy.StorageData;
import jp.amemaru.lostconnection.economy.StorageMenu;
import jp.amemaru.lostconnection.economy.StorageService;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Phase 6 の経済：出し入れの記録（EconomyLog）と、プレイヤー同士のノットの受け渡し（11月22日に決定）、個人倉庫。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    private static final String EMPTY = "empty3x3x3";

    private EconomyGameTests() {
    }

    /** 渡す＝発行、取る＝回収、人から人へ＝移動。世界の合計は、発行で増え、回収で減り、移動では変わらない。保存して読み直せる。 */
    @GameTest(template = EMPTY)
    public static void walletRecordsFlows(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer a = fake(helper, "eco_a");
        FakePlayer b = fake(helper, "eco_b");
        PlayerData da = data(helper, a);
        PlayerData db = data(helper, b);
        Wallet.set(a, da, 0, "gametest");
        Wallet.set(b, db, 0, "gametest");
        EconomyLog log = EconomyLog.get(server);
        int day = ProgressionData.currentDay(server);
        long issued = log.day(day).issued();
        long collected = log.day(day).collected();
        long moved = log.day(day).moved();
        long supply = log.supply();

        Wallet.give(a, da, 1000, "gametest give");
        check(helper, log.day(day).issued() - issued == 1000 && log.supply() - supply == 1000,
                "渡したノットが「発行」として記録されていません");
        Wallet.take(a, da, 300, "gametest take");
        check(helper, log.day(day).collected() - collected == 300 && log.supply() - supply == 700,
                "取ったノットが「回収」として記録されていません");
        check(helper, Wallet.transfer(a, da, b, db, 200, "gametest transfer"), "足りているのに渡せません");
        check(helper, da.getKnots() == 500 && db.getKnots() == 200, "渡したあとの所持金が違います：" + da.getKnots() + "・" + db.getKnots());
        check(helper, log.day(day).moved() - moved == 200 && log.supply() - supply == 700,
                "人から人へ渡したノットが「移動」として記録されていないか、合計が変わりました");
        check(helper, !Wallet.transfer(a, da, b, db, 501, "gametest transfer"), "足りないのに渡せました");
        check(helper, da.getKnots() == 500 && db.getKnots() == 200, "足りないときに所持金が変わりました");
        check(helper, !Wallet.pay(b, db, 201, "gametest pay") && db.getKnots() == 200, "足りないのに払えました");
        check(helper, Wallet.pay(b, db, 200, "gametest pay") && db.getKnots() == 0, "ちょうどの額を払えません");
        check(helper, !log.recent(3).isEmpty() && log.recent(1).get(0).reason().equals("gametest pay"),
                "直近の出し入れに、最後の支払いがありません");

        // 保存して読み直す
        EconomyLog loaded = EconomyLogAccess.reload(log);
        check(helper, loaded.supply() == log.supply() && loaded.day(day).issued() == log.day(day).issued()
                && loaded.day(day).moved() == log.day(day).moved(), "保存して読み直すと、記録が変わります");
        Wallet.set(a, da, 0, "gametest");
        helper.succeed();
    }

    /** 受け渡しの決まり：相手がいる・自分でない・1以上・持っている分まで・上限まで・すぐ続けては渡せない。 */
    @GameTest(template = EMPTY)
    public static void knotTransferRules(GameTestHelper helper) {
        FakePlayer a = fake(helper, "give_a");
        FakePlayer b = fake(helper, "give_b");
        PlayerData da = data(helper, a);
        PlayerData db = data(helper, b);
        Wallet.set(a, da, 1000, "gametest");
        Wallet.set(b, db, 0, "gametest");
        KnotTransfer.resetCooldown(a.getUUID());

        check(helper, KnotTransfer.transfer(a, null, 100) == KnotTransfer.Result.OFFLINE, "いない人に渡せました");
        check(helper, KnotTransfer.transfer(a, a, 100) == KnotTransfer.Result.SELF, "自分に渡せました");
        check(helper, KnotTransfer.transfer(a, b, 0) == KnotTransfer.Result.INVALID_AMOUNT, "0ノットを渡せました");
        check(helper, KnotTransfer.transfer(a, b, 1001) == KnotTransfer.Result.NOT_ENOUGH, "持っているより多く渡せました");
        check(helper, da.getKnots() == 1000 && db.getKnots() == 0, "渡せなかったのに所持金が変わりました");

        check(helper, KnotTransfer.transfer(a, b, 300) == KnotTransfer.Result.OK, "渡せません");
        check(helper, da.getKnots() == 700 && db.getKnots() == 300, "渡したあとの所持金が違います：" + da.getKnots() + "・" + db.getKnots());
        check(helper, KnotTransfer.transfer(a, b, 100) == KnotTransfer.Result.TOO_FAST, "すぐ続けて渡せました（押しすぎを防いでいません）");
        KnotTransfer.resetCooldown(a.getUUID());

        try {
            ServerConfig.overrideForTest(ServerConfig.TRANSFER_MAX, 500L);
            check(helper, KnotTransfer.transfer(a, b, 600) == KnotTransfer.Result.OVER_LIMIT, "1回の上限をこえて渡せました");
            check(helper, KnotTransfer.transfer(a, b, 500) == KnotTransfer.Result.OK, "上限ちょうどを渡せません");
        } finally {
            ServerConfig.overrideForTest(ServerConfig.TRANSFER_MAX, null);
        }
        check(helper, da.getKnots() == 200 && db.getKnots() == 800, "上限まで渡したあとの所持金が違います");

        // 所持金は保存される
        CompoundTag tag = db.save();
        PlayerData copy = new PlayerData();
        copy.load(tag);
        check(helper, copy.getKnots() == 800, "受け取ったノットが保存されていません");
        KnotTransfer.resetCooldown(a.getUUID());
        Wallet.set(a, da, 0, "gametest");
        Wallet.set(b, db, 0, "gametest");
        helper.succeed();
    }

    /**
     * 個人倉庫の契約と拡張：値段（500・5,000・20,000・60,000・150,000）と上限5ページ、足りなければ契約できない、
     * 減らすと外に出る品は受け取り箱へ（なくさない）、受け取り箱の上限。
     */
    @GameTest(template = EMPTY)
    public static void storageContractAndPages(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer p = fake(helper, "store_a");
        PlayerData d = data(helper, p);
        StorageData storage = StorageData.get(server);
        StorageData.Box box = storage.box(p.getUUID());
        box.setPages(0);
        box.received().clear();

        Wallet.set(p, d, 499, "gametest");
        check(helper, StorageService.contract(p, box) == StorageService.Result.NOT_ENOUGH && !box.contracted() && d.getKnots() == 499,
                "足りないのに契約できました");
        check(helper, StorageService.expand(p, box) == StorageService.Result.NOT_CONTRACTED, "契約していないのにページを増やせました");
        Wallet.set(p, d, 5_500, "gametest");
        check(helper, StorageService.contract(p, box) == StorageService.Result.OK && box.pages() == 1 && box.capacity() == 27
                && d.getKnots() == 5_000, "契約できないか、値段・ページが違います：" + box.pages() + "・" + d.getKnots());
        check(helper, StorageService.contract(p, box) == StorageService.Result.ALREADY, "2度契約できました");
        check(helper, StorageService.expand(p, box) == StorageService.Result.OK && box.pages() == 2 && d.getKnots() == 0,
                "2ページ目が5,000ノットで増えません");
        check(helper, StorageService.nextPrice(0) == 500 && StorageService.nextPrice(2) == 20_000 && StorageService.nextPrice(3) == 60_000
                && StorageService.nextPrice(4) == 150_000 && StorageService.nextPrice(5) == -1 && StorageService.maxPages() == 5,
                "倉庫の値段か上限が設計書と違います");
        Wallet.set(p, d, 1_000_000, "gametest");
        for (int i = 0; i < 3; i++) {
            StorageService.expand(p, box);
        }
        check(helper, box.pages() == 5 && box.capacity() == 135 && d.getKnots() == 1_000_000 - 230_000,
                "5ページ（135枠）にならないか、値段が違います：" + box.pages() + "・" + d.getKnots());
        check(helper, StorageService.expand(p, box) == StorageService.Result.MAX && box.pages() == 5, "上限をこえて増やせました");

        // 減らすと、外に出る枠の品は受け取り箱へ
        box.set(27 * 4 + 3, new ItemStack(Items.DIAMOND, 5));
        box.setPages(4);
        check(helper, box.capacity() == 108 && box.receivedCount() == 1 && box.received().get(0).is(Items.DIAMOND)
                && box.received().get(0).getCount() == 5, "ページを減らしたときに、外に出た品が受け取り箱に移りません");

        // 受け取り箱に届ける（上限まで）
        try {
            ServerConfig.overrideForTest(ServerConfig.STORAGE_RECEIVED_MAX, 27);
            boolean all = true;
            for (int i = 0; i < 26; i++) {
                all &= StorageService.deliver(server, p.getUUID(), "amt_store_a", new ItemStack(Items.APPLE));
            }
            check(helper, all && box.receivedCount() == 27, "受け取り箱に届きません：" + box.receivedCount());
            check(helper, !StorageService.deliver(server, p.getUUID(), "amt_store_a", new ItemStack(Items.APPLE)),
                    "いっぱいの受け取り箱に届きました");
            check(helper, StorageService.receivedRoom(server, p.getUUID()) == 0, "受け取り箱の空きの数が違います");
        } finally {
            ServerConfig.overrideForTest(ServerConfig.STORAGE_RECEIVED_MAX, null);
        }

        // 保存して読み直す
        StorageData loaded = StorageData.fromTag(storage.save(new CompoundTag()));
        StorageData.Box copy = loaded.peek(p.getUUID());
        check(helper, copy != null && copy.pages() == 4 && copy.receivedCount() == 27 && copy.received().get(0).is(Items.DIAMOND),
                "倉庫を保存して読み直すと、中身が変わります");
        box.setPages(0);
        box.received().clear();
        Wallet.set(p, d, 0, "gametest");
        helper.succeed();
    }

    /** 並べ替え（同じ品をまとめ、IDの順に前から詰める）と、倉庫の画面の枠（ページの出し入れ・受け取り箱には置けない）。 */
    @GameTest(template = EMPTY)
    public static void storageSortAndMenu(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer p = fake(helper, "store_b");
        StorageData storage = StorageData.get(server);
        StorageData.Box box = storage.box(p.getUUID());
        box.setPages(0);
        box.received().clear();
        box.setPages(2);
        box.set(5, new ItemStack(Items.BREAD, 40));
        box.set(30, new ItemStack(Items.APPLE, 3));
        box.set(40, new ItemStack(Items.BREAD, 40));
        StorageService.sort(box);
        check(helper, box.get(0).is(Items.APPLE) && box.get(0).getCount() == 3 && box.get(1).is(Items.BREAD) && box.get(1).getCount() == 64
                && box.get(2).is(Items.BREAD) && box.get(2).getCount() == 16 && box.get(3).isEmpty() && box.get(30).isEmpty(),
                "並べ替えで、同じ品がまとまらないか、順が違います");

        net.minecraft.core.BlockPos pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        StorageMenu menu = new StorageMenu(1, p.getInventory(), storage, box, pos, 0);
        check(helper, menu.onStoragePage() && menu.pages() == 2 && menu.getSlot(1).getItem().is(Items.BREAD),
                "倉庫の画面の1ページ目に、倉庫の中身が出ません");
        menu.getSlot(10).set(new ItemStack(Items.DIAMOND, 2));
        check(helper, box.get(10).is(Items.DIAMOND), "画面の枠に置いた品が、倉庫に入りません");
        menu.clickMenuButton(p, StorageMenu.BUTTON_PAGE + 1);
        check(helper, menu.page() == 1 && menu.getSlot(10).getItem().isEmpty(), "2ページ目に切り替わりません");
        menu.getSlot(0).set(new ItemStack(Items.EMERALD));
        check(helper, box.get(27).is(Items.EMERALD), "2ページ目の枠が、倉庫の28番に対応していません");
        StorageService.deliver(server, p.getUUID(), "amt_store_b", new ItemStack(Items.GOLD_INGOT, 4));
        menu.clickMenuButton(p, StorageMenu.BUTTON_RECEIVE);
        check(helper, menu.onReceivePage() && menu.getSlot(0).getItem().is(Items.GOLD_INGOT), "受け取り箱の品が出ません");
        check(helper, !menu.getSlot(1).mayPlace(new ItemStack(Items.STONE)), "受け取り箱に品を置けてしまいます");
        menu.clickMenuButton(p, StorageMenu.BUTTON_CONTRACT_INFO);
        check(helper, menu.onContractPage() && !menu.getSlot(0).isActive(), "契約の案内で、枠が隠れません");
        menu.removed(p);
        box.setPages(0);
        box.received().clear();
        helper.succeed();
    }

    // ---------------------------------------------------------------- 小物

    /** EconomyLog を保存して読み直す（同じパッケージにないので、保存の形を通して確かめる）。 */
    private static final class EconomyLogAccess {
        static EconomyLog reload(EconomyLog log) {
            CompoundTag tag = log.save(new CompoundTag());
            return EconomyLog.fromTag(tag);
        }
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
