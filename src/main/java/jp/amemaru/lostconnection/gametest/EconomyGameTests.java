package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.economy.EconomyLog;
import jp.amemaru.lostconnection.economy.KnotTransfer;
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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Phase 6 の経済：出し入れの記録（EconomyLog）と、プレイヤー同士のノットの受け渡し（11月22日に決定）。
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

        long before = ServerConfig.TRANSFER_MAX.get();
        try {
            ServerConfig.TRANSFER_MAX.set(500L);
            check(helper, KnotTransfer.transfer(a, b, 600) == KnotTransfer.Result.OVER_LIMIT, "1回の上限をこえて渡せました");
            check(helper, KnotTransfer.transfer(a, b, 500) == KnotTransfer.Result.OK, "上限ちょうどを渡せません");
        } finally {
            ServerConfig.TRANSFER_MAX.set(before);
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
