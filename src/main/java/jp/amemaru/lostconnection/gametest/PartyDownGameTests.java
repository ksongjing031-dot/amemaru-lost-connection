package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.CombatTargets;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.party.Party;
import jp.amemaru.lostconnection.party.PartyExp;
import jp.amemaru.lostconnection.party.PartyManager;
import jp.amemaru.lostconnection.party.PartyService;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 4：パーティ・ダウンと蘇生・教会での復活・デスペナルティ・所持金の自動テスト。
 *
 * <p>テスト用の体（FakePlayer）はサーバーの人の一覧にいないので、知らせとパケットは届かない（決まりだけを確かめる）。
 * 起こす人を探す・しゃがむ・画面は、LANの2人の確かめで見る。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartyDownGameTests {
    private static final String EMPTY = "empty3x3x3";

    private PartyDownGameTests() {
    }

    /** 誘う・参加する・リーダーを渡す・抜ける・外す・解散。パーティに入っているときに誘えるのはリーダーだけ。 */
    @GameTest(template = EMPTY)
    public static void partyLifecycle(GameTestHelper helper) {
        FakePlayer a = fake(helper, "party_a");
        FakePlayer b = fake(helper, "party_b");
        FakePlayer c = fake(helper, "party_c");
        PartyManager manager = PartyManager.get(helper.getLevel().getServer());
        cleanup(helper, a, b, c);

        PartyService.invite(a, b);
        check(helper, manager.invitesFor(b.getUUID(), now(helper)).size() == 1, "誘いが届いていません");
        PartyService.accept(b, a.getUUID());
        Party party = manager.partyOf(a.getUUID());
        check(helper, party != null && party.size() == 2 && party.isLeader(a.getUUID()) && party.contains(b.getUUID()),
                "誘った人がリーダーの、2人のパーティになっていません：" + party);
        check(helper, manager.invitesFor(b.getUUID(), now(helper)).isEmpty(), "参加した後も誘いが残っています");

        // リーダーでない人は誘えない
        PartyService.invite(b, c);
        check(helper, manager.invitesFor(c.getUUID(), now(helper)).isEmpty(), "リーダーでない人が誘えてしまいました");
        PartyService.invite(a, c);
        PartyService.accept(c, null);
        check(helper, party.size() == 3, "3人目が入っていません：" + party);
        check(helper, PartyService.sameParty(a, c) && PartyService.sameParty(b, c), "同じパーティとみなされていません");

        // リーダーを渡す（リーダーだけ）
        PartyService.promote(b, c.getUUID());
        check(helper, party.isLeader(a.getUUID()), "リーダーでない人がリーダーを渡せてしまいました");
        PartyService.promote(a, b.getUUID());
        check(helper, party.isLeader(b.getUUID()), "リーダーが渡っていません：" + party);
        // リーダーが抜けると、先に入った人がリーダーになる
        PartyService.leave(b);
        check(helper, !party.contains(b.getUUID()) && party.isLeader(a.getUUID()), "リーダーが抜けた後のリーダーが違います：" + party);
        // 外して1人になったら解散
        PartyService.kick(a, c.getUUID());
        check(helper, manager.partyOf(a.getUUID()) == null && manager.partyOf(c.getUUID()) == null, "1人になっても解散していません");
        helper.succeed();
    }

    /** 6人まで。7人目は入れない。 */
    @GameTest(template = EMPTY)
    public static void partyLimit(GameTestHelper helper) {
        List<FakePlayer> players = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            players.add(fake(helper, "limit_" + i));
        }
        cleanup(helper, players.toArray(new FakePlayer[0]));
        FakePlayer leader = players.get(0);
        for (int i = 1; i < 7; i++) {
            PartyService.invite(leader, players.get(i));
            PartyService.accept(players.get(i), leader.getUUID());
        }
        PartyManager manager = PartyManager.get(helper.getLevel().getServer());
        Party party = manager.partyOf(leader.getUUID());
        check(helper, party != null && party.size() == 6, "6人のパーティになっていません：" + party);
        check(helper, manager.partyOf(players.get(6).getUUID()) == null, "7人目が入ってしまいました");
        cleanup(helper, players.toArray(new FakePlayer[0]));
        helper.succeed();
    }

    /** 保存して読み直しても、メンバーとリーダーが同じ。 */
    @GameTest(template = EMPTY)
    public static void partySaveLoad(GameTestHelper helper) {
        FakePlayer a = fake(helper, "save_a");
        FakePlayer b = fake(helper, "save_b");
        cleanup(helper, a, b);
        PartyService.invite(a, b);
        PartyService.accept(b, a.getUUID());
        PartyManager manager = PartyManager.get(helper.getLevel().getServer());
        CompoundTag saved = manager.save(new CompoundTag());
        PartyManager loaded = PartyManager.load(saved);
        Party party = loaded.partyOf(b.getUUID());
        check(helper, party != null && party.isLeader(a.getUUID()) && party.size() == 2 && "amt_save_b".equals(party.name(b.getUUID())),
                "読み直したパーティが違います：" + party);
        cleanup(helper, a, b);
        helper.succeed();
    }

    /** 経験値：受け取る人の数で分け、パーティの人数のボーナスをかける（合計が 1＋0.2×（人数−1）倍）。 */
    @GameTest(template = EMPTY)
    public static void expShareWithBonus(GameTestHelper helper) {
        check(helper, PartyExp.share(100, 1, 1, 0.2) == 100, "ソロの経験値が違います");
        check(helper, PartyExp.share(7, 2, 1, 0.2) == 4, "パーティでない2人の経験値が、Phase 3と違います（切り上げ）");
        check(helper, PartyExp.share(100, 3, 3, 0.2) == 47, "3人のパーティの1人分が47ではありません：" + PartyExp.share(100, 3, 3, 0.2));
        check(helper, PartyExp.share(60, 4, 2, 0.2) == 18, "2人のパーティ（ほかに2人）の1人分が18ではありません");
        check(helper, PartyExp.share(10, 6, 6, 0.2) == 4, "6人のパーティの1人分が4ではありません（2倍を6人で分けて切り上げ）");
        check(helper, PartyExp.share(0, 3, 3, 0.2) == 0, "経験値0の敵で経験値が出ました");
        helper.succeed();
    }

    /** ダウン：HPは0、回復の相手にならない。起こすと、HPは最大の30%、起こした人の記録が増え、少しの間ダメージを受けない。 */
    @GameTest(template = EMPTY)
    public static void downAndRevive(GameTestHelper helper) {
        FakePlayer downed = fake(helper, "down_p");
        FakePlayer helperPlayer = fake(helper, "down_r");
        PlayerData data = data(helper, downed);
        PlayerData helperData = data(helper, helperPlayer);
        LevelService.recompute(downed, data);
        LevelService.recompute(helperPlayer, helperData);
        int revivesBefore = helperData.getReviveCount();
        DownHandler.goDown(downed, data);
        check(helper, data.down().isDown() && data.getHp() == 0, "ダウンになっていません");
        check(helper, !CombatTargets.isAlly(helperPlayer, downed, false), "ダウンしている人が、回復・強化の相手になっています");
        check(helper, DownHandler.reviveTicks(helperPlayer) == 60, "起こすのにかかる時間が3秒ではありません：" + DownHandler.reviveTicks(helperPlayer));
        DownHandler.revive(downed, data, helperPlayer);
        double expected = Math.floor(data.getStats().maxHp() * 30.0 / 100.0);
        check(helper, !data.down().isDown(), "起き上がっていません");
        check(helper, Math.abs(data.getHp() - Math.max(1.0, expected)) < 1.0E-6, "起き上がったHPが最大の30%ではありません：" + data.getHp());
        check(helper, helperData.getReviveCount() == revivesBefore + 1, "起こした人の記録が増えていません");
        check(helper, data.down().invulnerable(downed.level().getGameTime()), "起き上がってすぐの無敵がありません");
        helper.succeed();
    }

    /** 教会へ戻る：所持金の5%を失い、HPとMPは全快、衰弱でステータスが10%下がる。経験値は失わない。 */
    @GameTest(template = EMPTY)
    public static void churchReturnPenalty(GameTestHelper helper) {
        FakePlayer player = fake(helper, "church_p");
        PlayerData data = data(helper, player);
        player.removeAllEffects();
        LevelService.setLevel(player, data, 10);
        LevelService.addExp(player, data, 30, LevelService.ExpSource.ADMIN);
        LevelService.recompute(player, data);
        double attackBefore = data.getStats().get(StatType.ATTACK);
        int expBefore = data.getExp();
        Wallet.set(player, data, 1000, "gametest");
        int churchBefore = data.getChurchCount();
        DownHandler.goDown(player, data);
        DownHandler.returnToChurch(player, data);
        check(helper, data.getKnots() == 950, "所持金が5%減っていません：" + data.getKnots());
        check(helper, !data.down().isDown(), "教会に戻ってもダウンのままです");
        check(helper, data.getChurchCount() == churchBefore + 1, "教会に戻った回数が増えていません");
        check(helper, player.hasEffect(ModEffects.WEAKENED.get()), "衰弱になっていません");
        double attackAfter = data.getStats().get(StatType.ATTACK);
        check(helper, attackAfter < attackBefore && attackAfter >= Math.floor(attackBefore * 0.9) - 1,
                "衰弱の攻撃が10%下がっていません：" + attackBefore + " → " + attackAfter);
        check(helper, Math.abs(data.getHp() - data.getStats().maxHp()) < 1.0E-6, "HPが全快ではありません");
        check(helper, data.getExp() == expBefore && data.getLevel() == 10, "経験値かレベルが減りました");
        player.removeAllEffects();
        helper.succeed();
    }

    /** 所持金・ダウン・記録は保存され、ダウン中に抜けた印は「入り直したら教会へ」になる。 */
    @GameTest(template = EMPTY)
    public static void walletAndDownSaved(GameTestHelper helper) {
        FakePlayer player = fake(helper, "save_down");
        PlayerData data = data(helper, player);
        LevelService.recompute(player, data);
        Wallet.set(player, data, 1234, "gametest");
        DownHandler.goDown(player, data);
        CompoundTag saved = data.save();
        PlayerData loaded = new PlayerData();
        loaded.load(saved);
        check(helper, loaded.getKnots() == 1234, "所持金が保存されていません：" + loaded.getKnots());
        check(helper, loaded.getDownCount() == data.getDownCount() && loaded.getDownCount() >= 1, "ダウンの回数が保存されていません");
        check(helper, loaded.down().leftWhileDown() && !loaded.down().isDown(), "ダウン中に抜けた印になっていません");
        DownHandler.revive(player, data, null);
        check(helper, Wallet.percentOf(1234, 5.0) == 61 && Wallet.percentOf(0, 5.0) == 0, "5%の計算（切り捨て）が違います");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 小物

    /** 前のテストの残り（パーティ）を消す。 */
    private static void cleanup(GameTestHelper helper, FakePlayer... players) {
        PartyManager manager = PartyManager.get(helper.getLevel().getServer());
        for (FakePlayer player : players) {
            for (int i = 0; i < 8 && manager.partyOf(player.getUUID()) != null; i++) {
                PartyService.leave(player);
            }
        }
    }

    private static long now(GameTestHelper helper) {
        return helper.getLevel().getServer().overworld().getGameTime();
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
