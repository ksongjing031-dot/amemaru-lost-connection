package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.connection.ConnectionActionDefs;
import jp.amemaru.lostconnection.connection.ConnectionActions;
import jp.amemaru.lostconnection.connection.ConnectionActionsHandler;
import jp.amemaru.lostconnection.connection.ConnectionCategory;
import jp.amemaru.lostconnection.connection.ConnectionData;
import jp.amemaru.lostconnection.connection.ConnectionHandover;
import jp.amemaru.lostconnection.connection.ConnectionRecord;
import jp.amemaru.lostconnection.connection.ConnectionRules;
import jp.amemaru.lostconnection.connection.ConnectionService;
import jp.amemaru.lostconnection.connection.ConnectionSocial;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.guild.GuildData;
import jp.amemaru.lostconnection.guild.GuildHooks;
import jp.amemaru.lostconnection.party.PartyService;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 7 その1：コネクション量（CP）と大結晶の輝き段階。
 *
 * <p>サーバー全体の累計と段階は、ほかの自動テストと同じものを使う。どのテストも、終わるとき（失敗しても）に初めに戻し、時計も戻す。
 * 偽のプレイヤーはサーバーの一覧にいないので、「いない人には次に入ったときに数える」の道も、ここで確かめられる。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConnectionGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1e-6;

    private ConnectionGameTests() {
    }

    /** 定義：行動25（13章の24と、15章の今日の相棒）・輝き段階の決まりが読める。しきい値・人数補正・星・効果（累積は、届いたいちばん上の段階の値）。 */
    @GameTest(template = EMPTY)
    public static void connectionDefinitionsLoad(GameTestHelper helper) {
        check(helper, ConnectionActionDefs.loader().errors().isEmpty() && ConnectionActionDefs.all().size() == 25,
                "コネクションの行動の定義が読めません（" + ConnectionActionDefs.all().size() + "）：" + ConnectionActionDefs.loader().errors());
        for (ResourceLocation action : List.of(ConnectionActions.PARTY_KILL, ConnectionActions.FLOOR_BOSS, ConnectionActions.CHAPTER_BOSS,
                ConnectionActions.LINK_COMBO, ConnectionActions.CRAFT_FOR_OTHER, ConnectionActions.REPAIR_FOR_OTHER,
                ConnectionActions.REFINE_COMMISSION, ConnectionActions.MARKET_TRADE, ConnectionActions.STALL_VISITORS, ConnectionActions.REVIVE,
                ConnectionActions.FIRST_PARTY, ConnectionActions.THANKS_SEND, ConnectionActions.THANKS_RECEIVE, ConnectionActions.NEWCOMER_PARTY,
                ConnectionActions.FISH_NEAR_OTHERS, ConnectionActions.FISH_DEX, ConnectionActions.CASINO_TABLE, ConnectionActions.EVENT_JOIN,
                ConnectionActions.DONATION, ConnectionActions.RAID_SUPPORT, ConnectionActions.NPC_REQUEST, ConnectionActions.STORY_QUEST,
                ConnectionActions.RECORD_FOUND, ConnectionActions.DAILY_QUEST, ConnectionActions.TODAY_PARTNER)) {
            check(helper, ConnectionActionDefs.get(action) != null, "行動の定義がありません：" + action);
        }
        ConnectionRules rules = ConnectionRules.current();
        check(helper, rules.maxStage() == 10 && rules.threshold(1) == 0 && rules.threshold(2) == 5000 && rules.threshold(10) == 165000,
                "輝き段階のしきい値がちがいます");
        check(helper, rules.stageFor(4999, 1.0) == 1 && rules.stageFor(5000, 1.0) == 2 && rules.stageFor(165000, 1.0) == 10,
                "累計から段階が正しく決まりません");
        check(helper, rules.stageFor(2000, 0.4) == 2 && rules.stageFor(9999, 2.0) == 1 && rules.stageFor(10000, 2.0) == 2,
                "人数補正（0.4〜2.0倍）が、しきい値に掛かっていません");
        check(helper, rules.starsFor(169999, 1.0) == 0 && rules.starsFor(170000, 1.0) == 1 && rules.starsFor(10_000_000L, 1.0) == 5,
                "段階10のあとの星（5000ごと、5つまで）が正しく決まりません");
        check(helper, rules.effect("lost_resist", 2) == 0 && rules.effect("lost_resist", 3) == 3 && rules.effect("lost_resist", 10) == 6,
                "ロスト耐性の効果（段階3で+3%、段階7で+6%）がちがいます");
        check(helper, rules.effect("exp", 3) == 0 && rules.effect("exp", 4) == 3 && rules.effect("exp", 8) == 6, "経験値の効果がちがいます");
        check(helper, rules.effect("barrier_plaza", 1) == 0 && rules.effect("barrier_plaza", 2) == 1 && rules.effect("down_seconds", 6) == 5
                && rules.effect("barrier", 9) == 40 && rules.effect("blessing", 10) == 1, "段階2・5・6・9・10の効果がちがいます");
        check(helper, !rules.voice(2).isEmpty() && !rules.voice(10).isEmpty(), "段階が上がったときの大結晶の一言がありません");
        helper.succeed();
    }

    /**
     * 1人1日の上限：行動ごとの回数（連携技30回）・同じ相手（フリマ3回）・「この数ごとに1」（パーティで5体ごと）・分野150・合計600。
     * 日は毎日4時に切り替わる。
     */
    @GameTest(template = EMPTY)
    public static void connectionDailyCaps(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        LocalDateTime[] now = {LocalDateTime.of(2026, 11, 23, 10, 0)};
        ConnectionService.setClockForTest(() -> now[0]);
        try {
            ConnectionService.adminReset(server);
            FakePlayer a = fake(helper, "cp_caps_a");
            ConnectionRecord record = data(helper, a).getConnection();
            long before = ConnectionData.get(server).total();
            check(helper, ConnectionService.award(a, ConnectionActions.PARTY_KILL, null, 4) == 0
                    && ConnectionService.award(a, ConnectionActions.PARTY_KILL, null, 1) == 1, "パーティで倒した5体ごとに1になっていません");
            int link = 0;
            for (int i = 0; i < 31; i++) {
                link += ConnectionService.award(a, ConnectionActions.LINK_COMBO);
            }
            check(helper, link == 30, "連携技が1日30回で止まりません（" + link + "）");
            UUID partner = uuid("cp_caps_partner");
            int trade = 0;
            for (int i = 0; i < 4; i++) {
                trade += ConnectionService.award(a, ConnectionActions.MARKET_TRADE, partner);
            }
            check(helper, trade == 15, "同じ相手とのフリマの取引が1日3回で止まりません（" + trade + "）");
            check(helper, ConnectionService.award(a, ConnectionActions.MARKET_TRADE, a.getUUID()) == 0, "自分を相手にしてCPが増えました");
            int boss = 0;
            for (int i = 0; i < 4; i++) {
                boss += ConnectionService.award(a, ConnectionActions.CHAPTER_BOSS, uuid("cp_caps_boss" + i));
            }
            check(helper, boss == 119 && record.today(ConnectionCategory.COMBAT) == 150,
                    "戦闘の分野が1日150で止まりません（章ボス " + boss + "・戦闘 " + record.today(ConnectionCategory.COMBAT) + "）");
            for (int i = 0; i < 10; i++) {
                ConnectionService.award(a, ConnectionActions.CRAFT_FOR_OTHER, uuid("cp_caps_craft" + i));
                ConnectionService.award(a, ConnectionActions.NPC_REQUEST);
            }
            for (int i = 0; i < 5; i++) {
                ConnectionService.award(a, ConnectionActions.STORY_QUEST);
            }
            check(helper, record.today(ConnectionCategory.PRODUCTION) == 150 && record.today(ConnectionCategory.TOWN) == 150
                            && record.today(ConnectionCategory.STORY) == 135 && record.todayTotal() == 600,
                    "合計が1日600で止まりません（生産 " + record.today(ConnectionCategory.PRODUCTION) + "・街 " + record.today(ConnectionCategory.TOWN)
                            + "・物語 " + record.today(ConnectionCategory.STORY) + "・合計 " + record.todayTotal() + "）");
            check(helper, ConnectionService.award(a, ConnectionActions.FIRST_PARTY, uuid("cp_caps_first0")) == 0,
                    "合計の上限のあとにCPが増えました");
            check(helper, ConnectionData.get(server).total() - before == 600, "サーバー全体の累計に、足したCPがそのまま入っていません");
            check(helper, ConnectionData.get(server).contributors().get(a.getUUID()) != null
                    && ConnectionData.get(server).contributors().get(a.getUUID()).total() == 600, "みんなの貢献に数えられていません");
            // 日の切り替えは4時
            now[0] = LocalDateTime.of(2026, 11, 24, 3, 59);
            check(helper, "2026-11-23".equals(ConnectionService.today()), "4時より前なのに日が変わりました：" + ConnectionService.today());
            check(helper, ConnectionService.award(a, ConnectionActions.FIRST_PARTY, uuid("cp_caps_first1")) == 0, "4時より前に上限が戻りました");
            now[0] = LocalDateTime.of(2026, 11, 24, 4, 0);
            check(helper, "2026-11-24".equals(ConnectionService.today()), "4時に日が変わりません：" + ConnectionService.today());
            check(helper, ConnectionService.award(a, ConnectionActions.FIRST_PARTY, uuid("cp_caps_first2")) == 10
                    && record.todayTotal() == 10 && record.lifetimeTotal() == 610, "日が変わっても上限が戻りません");
        } finally {
            ConnectionService.setClockForTest(null);
            ConnectionService.adminReset(server);
        }
        helper.succeed();
    }

    /** 輝き段階：効果（ロスト耐性はステータスに入る）・一度灯った段階は消えない・段階10のあとは星・管理者は段階を下げられる。 */
    @GameTest(template = EMPTY)
    public static void connectionStageEffects(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StoryState story = StoryState.get(server);
        try {
            ConnectionService.adminReset(server);
            check(helper, ConnectionService.stage(server) == 1 && ConnectionService.lostResistBonus(server) == 0
                    && !ConnectionService.plazaInBarrier(server), "初めの段階が1ではありません");
            FakePlayer a = fake(helper, "cp_stage_a");
            PlayerData data = data(helper, a);
            LevelService.recompute(a, data);
            double lost = data.getStats().get(StatType.LOST_RESIST);

            ConnectionService.adminStage(server, 3);
            check(helper, ConnectionService.stage(server) == 3 && ConnectionService.plazaInBarrier(server)
                    && ConnectionService.lostResistBonus(server) == 3 && ConnectionService.expBonusPercent(server) == 0,
                    "段階3の効果がちがいます");
            LevelService.recompute(a, data);
            check(helper, Math.abs(data.getStats().get(StatType.LOST_RESIST) - (lost + 3)) < EPSILON,
                    "段階3のロスト耐性+3%がステータスに入っていません（" + lost + " → " + data.getStats().get(StatType.LOST_RESIST) + "）");
            check(helper, story.has(AmemaruMod.id("connection/stage_2")) && story.has(AmemaruMod.id("connection/stage_3")),
                    "段階の印（物語の条件）が付いていません");

            // 一度灯った段階は消えない（累計が減っても、人数補正が変わっても）
            ConnectionService.adminSet(server, 0);
            check(helper, ConnectionService.stage(server) == 3, "累計を0にしたら段階が下がりました");

            ConnectionService.adminStage(server, 10);
            check(helper, ConnectionService.stage(server) == 10 && ConnectionService.lostResistBonus(server) == 6
                            && ConnectionService.expBonusPercent(server) == 6 && ConnectionService.downBonusSeconds(server) == 5
                            && ConnectionService.barrierBonusPercent(server) == 40 && ConnectionService.finalBlessing(server) == 1,
                    "段階10の効果（累積）がちがいます");
            ConnectionRules rules = ConnectionRules.current();
            long step = Math.round(rules.starStep() * ConnectionService.scale(server));
            ConnectionService.adminAdd(server, step);
            check(helper, ConnectionService.stars(server) == 1, "段階10のあと、星が灯りません（" + ConnectionService.stars(server) + "）");
            ConnectionService.adminAdd(server, step * 10);
            check(helper, ConnectionService.stars(server) == 5, "星が5つを超えました（" + ConnectionService.stars(server) + "）");

            // 管理者：段階を下げる（上の段階の印と星と効果が消える）
            ConnectionService.adminStage(server, 2);
            check(helper, ConnectionService.stage(server) == 2 && ConnectionService.stars(server) == 0
                    && ConnectionService.lostResistBonus(server) == 0 && ConnectionService.plazaInBarrier(server)
                    && !story.has(AmemaruMod.id("connection/stage_3")) && story.has(AmemaruMod.id("connection/stage_2")),
                    "段階を下げられません");
            LevelService.recompute(a, data);
            check(helper, Math.abs(data.getStats().get(StatType.LOST_RESIST) - lost) < EPSILON, "段階を下げても、ロスト耐性が残っています");
        } finally {
            ConnectionService.adminReset(server);
        }
        check(helper, !story.has(AmemaruMod.id("connection/stage_2")), "初めに戻しても、段階の印が残っています");
        helper.succeed();
    }

    /** 結び札：縁帳の人へ、1日3枚・同じ人へは1日1枚。いない人には、次に入ったときに数える。次の日（4時）にまた贈れる。 */
    @GameTest(template = EMPTY)
    public static void connectionThanks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        LocalDateTime[] now = {LocalDateTime.of(2026, 11, 23, 12, 0)};
        ConnectionService.setClockForTest(() -> now[0]);
        try {
            ConnectionService.adminReset(server);
            FakePlayer a = fake(helper, "cp_thx_a");
            FakePlayer b = fake(helper, "cp_thx_b");
            FakePlayer c = fake(helper, "cp_thx_c");
            FakePlayer d = fake(helper, "cp_thx_d");
            FakePlayer e = fake(helper, "cp_thx_e");
            FakePlayer stranger = fake(helper, "cp_thx_x");
            PlayerData da = data(helper, a);
            int day = ProgressionData.get(server).day();
            for (FakePlayer p : List.of(b, c, d, e)) {
                da.getGuild().addConnection(p.getUUID(), p.getGameProfile().getName(), GuildData.ConnectionKind.PARTY, day);
            }
            check(helper, !ConnectionActionsHandler.sendThanks(a, stranger.getUUID()), "縁帳にいない人に結び札を贈れました");
            check(helper, !ConnectionActionsHandler.sendThanks(a, a.getUUID()), "自分に結び札を贈れました");
            check(helper, ConnectionActionsHandler.sendThanks(a, b.getUUID()), "縁帳の人に結び札を贈れません");
            check(helper, !ConnectionActionsHandler.sendThanks(a, b.getUUID()), "同じ人に1日2枚贈れました");
            check(helper, ConnectionActionsHandler.sendThanks(a, c.getUUID()) && ConnectionActionsHandler.sendThanks(a, d.getUUID()),
                    "3枚目までの結び札を贈れません");
            check(helper, !ConnectionActionsHandler.sendThanks(a, e.getUUID()), "1日に4枚贈れました");
            check(helper, da.getConnection().today(ConnectionCategory.SOCIAL) == 9, "贈った人の交流のCPがちがいます（"
                    + da.getConnection().today(ConnectionCategory.SOCIAL) + "）");
            // いない人（偽のプレイヤーは一覧にいない）への結び札は、次に入ったときに数える
            PlayerData db = data(helper, b);
            check(helper, db.getConnection().today(ConnectionCategory.SOCIAL) == 0, "いない人に、すぐCPが入りました");
            ConnectionService.onLoggedIn(new PlayerEvent.PlayerLoggedInEvent(b));
            check(helper, db.getConnection().today(ConnectionCategory.SOCIAL) == 3, "入ったときに、もらった結び札のCPが数えられません（"
                    + db.getConnection().today(ConnectionCategory.SOCIAL) + "）");
            now[0] = LocalDateTime.of(2026, 11, 24, 4, 1);
            check(helper, ConnectionActionsHandler.sendThanks(a, e.getUUID()), "次の日に結び札を贈れません");
        } finally {
            ConnectionService.setClockForTest(null);
            ConnectionService.adminReset(server);
        }
        helper.succeed();
    }

    /**
     * つながりの行動：初めての人とパーティ（お互いに、初めてのときだけ）・手渡し（作り手とちがう人が初めて身に着けた）・
     * 他人の装備の修理（使って耐久を減らした人とちがう人が直した）。
     */
    @GameTest(template = EMPTY)
    public static void connectionTogetherActions(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ConnectionService.setClockForTest(() -> LocalDateTime.of(2026, 11, 23, 15, 0));
        try {
            ConnectionService.adminReset(server);
            FakePlayer a = fake(helper, "cp_tog_a");
            FakePlayer b = fake(helper, "cp_tog_b");
            PlayerData da = data(helper, a);
            PlayerData db = data(helper, b);
            GuildHooks.onPartyJoined(a, List.of(b, a));
            check(helper, da.getConnection().today(ConnectionCategory.SOCIAL) == 10 && db.getConnection().today(ConnectionCategory.SOCIAL) == 10,
                    "初めての人とパーティを組んでも、お互いに10になりません（" + da.getConnection().today(ConnectionCategory.SOCIAL) + "・"
                            + db.getConnection().today(ConnectionCategory.SOCIAL) + "）");
            GuildHooks.onPartyJoined(a, List.of(b, a));
            check(helper, da.getConnection().today(ConnectionCategory.SOCIAL) == 10, "前に組んだ人と組んでも、初めてのCPが増えました");

            // 手渡し：作り手（b）が持っても数えない。ほかの人（a）が初めて身に着けたら、作り手に（いないので、次に入ったとき）
            ItemStack sword = new ItemStack(item("ancient_stone_sword"));
            CompoundTag gear = sword.getOrCreateTagElement(GearData.TAG);
            gear.putString("crafter", b.getGameProfile().getName());
            gear.putUUID("crafterId", b.getUUID());
            ConnectionHandover.onEquip(b, sword);
            check(helper, sword.getTag() == null || !sword.getTag().getBoolean(ConnectionHandover.HANDED), "作り手が持っただけで手渡しになりました");
            ConnectionHandover.onEquip(a, sword);
            check(helper, sword.getTag() != null && sword.getTag().getBoolean(ConnectionHandover.HANDED), "ほかの人が身に着けても手渡しになりません");
            ConnectionService.onLoggedIn(new PlayerEvent.PlayerLoggedInEvent(b));
            check(helper, db.getConnection().today(ConnectionCategory.PRODUCTION) == 15, "手渡しのCPが作り手に入りません（"
                    + db.getConnection().today(ConnectionCategory.PRODUCTION) + "）");
            ConnectionHandover.onEquip(a, sword);
            ConnectionService.onLoggedIn(new PlayerEvent.PlayerLoggedInEvent(b));
            check(helper, db.getConnection().today(ConnectionCategory.PRODUCTION) == 15, "同じ品の手渡しが2回数えられました");

            // 修理：使って耐久を減らした人（a）とちがう人（b）が直すと、直した人に。持ち主が自分で直しても数えない
            ConnectionHandover.markUser(a, sword);
            check(helper, a.getUUID().equals(ConnectionHandover.owner(sword)), "使った人が持ち主になりません");
            ConnectionHandover.onRepaired(a, sword);
            check(helper, da.getConnection().today(ConnectionCategory.PRODUCTION) == 0, "自分の装備の修理でCPが増えました");
            ConnectionHandover.onRepaired(b, sword);
            check(helper, db.getConnection().today(ConnectionCategory.PRODUCTION) == 20, "他人の装備の修理のCPが入りません（"
                    + db.getConnection().today(ConnectionCategory.PRODUCTION) + "）");
        } finally {
            ConnectionService.setClockForTest(null);
            ConnectionService.adminReset(server);
        }
        helper.succeed();
    }

    /**
     * 交流の常設の仕組み（15章）：はじめましてボーナス（今のパーティにいる「今日初めて組んだ人」×10%・1日5人まで・パーティを
     * 抜けると消える）・先導の相手の決まり・今日の相棒の依頼（同じパーティで敵を10体。1体につき1組1回。ふたりにノットとCP）。
     */
    @GameTest(template = EMPTY)
    public static void connectionSocialPerks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ConnectionService.setClockForTest(() -> LocalDateTime.of(2026, 11, 23, 16, 0));
        FakePlayer a = fake(helper, "cp_soc_a");
        FakePlayer b = fake(helper, "cp_soc_b");
        FakePlayer c = fake(helper, "cp_soc_c");
        leaveParties(a, b, c);
        try {
            ConnectionService.adminReset(server);
            PartyService.invite(a, b);
            PartyService.accept(b, a.getUUID());
            PartyService.invite(a, c);
            PartyService.accept(c, a.getUUID());
            check(helper, PartyService.partyOf(a) != null && PartyService.partyOf(a).members().size() == 3, "3人のパーティになりません");
            check(helper, ConnectionSocial.firstMeetBonus(a) == 0, "まだ初めて組んだ人を数えていないのに、ボーナスがあります");
            // 偽のプレイヤーはサーバーの一覧にいないので、参加の知らせ（縁帳に足す前）を、ここで送る
            GuildHooks.onPartyJoined(b, List.of(a, b));
            check(helper, ConnectionSocial.firstMeetBonus(a) == 10 && ConnectionSocial.firstMeetBonus(b) == 10
                    && ConnectionSocial.firstMeetBonus(c) == 0, "初めて組んだ1人で+10%になりません");
            GuildHooks.onPartyJoined(c, List.of(a, b, c));
            check(helper, ConnectionSocial.firstMeetBonus(a) == 20 && ConnectionSocial.firstMeetBonus(c) == 20,
                    "初めて組んだ2人で+20%になりません（" + ConnectionSocial.firstMeetBonus(a) + "）");
            ConnectionRecord ra = data(helper, a).getConnection();
            for (int i = 0; i < 6; i++) {
                ra.addFirstMeet(uuid("cp_soc_x" + i), 5);
            }
            check(helper, ra.firstMeetCount() == 5, "1日に数える「初めて組んだ人」が5人で止まりません（" + ra.firstMeetCount() + "）");
            PartyService.leave(c);
            check(helper, ConnectionSocial.firstMeetBonus(a) == 10, "パーティを抜けた人の分が残っています");

            // 先導の相手：途中参加者か、中央値より10レベル以上低い人
            check(helper, ConnectionSocial.juniorBy(true, 30, 30, 10) && ConnectionSocial.juniorBy(false, 10, 20, 10)
                    && !ConnectionSocial.juniorBy(false, 11, 20, 10), "先導の相手の決まりがちがいます");

            // 今日の相棒：a と b。同じパーティで敵を倒すと、1体につき1組1回進む。10体で、ふたりにノットとCP
            ConnectionSocial.adminSetPartners(server, a.getUUID(), a.getGameProfile().getName(), b.getUUID(), b.getGameProfile().getName());
            check(helper, b.getUUID().equals(ConnectionData.get(server).partnerOf(a.getUUID()))
                    && a.getUUID().equals(ConnectionData.get(server).partnerOf(b.getUUID())), "相棒がお互いになっていません");
            PlayerData da = data(helper, a);
            PlayerData db = data(helper, b);
            long knotsA = Wallet.balance(da);
            long knotsB = Wallet.balance(db);
            int socialA = da.getConnection().today(ConnectionCategory.SOCIAL);
            int goal = ServerConfig.PARTNER_GOAL.get();
            ConnectionSocial.onEnemyKilled(List.of(a, b));
            check(helper, ConnectionData.get(server).pairProgress(a.getUUID(), b.getUUID()) == 1, "1体で2つ進みました");
            for (int i = 1; i < goal - 1; i++) {
                ConnectionSocial.onEnemyKilled(List.of(b, a));
            }
            check(helper, !ConnectionData.get(server).pairDone(a.getUUID(), b.getUUID()) && Wallet.balance(da) == knotsA,
                    "目標の前に終わりました");
            ConnectionSocial.onEnemyKilled(List.of(a, b));
            long reward = ServerConfig.PARTNER_KNOTS.get();
            check(helper, ConnectionData.get(server).pairDone(a.getUUID(), b.getUUID()) && Wallet.balance(da) == knotsA + reward
                            && Wallet.balance(db) == knotsB + reward && da.getConnection().today(ConnectionCategory.SOCIAL) == socialA + 10,
                    "相棒の依頼を終えても、ふたりにお礼が入りません（" + Wallet.balance(da) + "・" + Wallet.balance(db) + "）");
            ConnectionSocial.onEnemyKilled(List.of(a, b));
            check(helper, Wallet.balance(da) == knotsA + reward, "終えた依頼のお礼が2回入りました");
            // 相棒がパーティにいなければ進まない
            ConnectionSocial.adminSetPartners(server, a.getUUID(), a.getGameProfile().getName(), c.getUUID(), c.getGameProfile().getName());
            ConnectionSocial.onEnemyKilled(List.of(a, c));
            check(helper, ConnectionData.get(server).pairProgress(a.getUUID(), c.getUUID()) == 0, "パーティにいない相棒と倒しても進みました");
        } finally {
            leaveParties(a, b, c);
            ConnectionService.setClockForTest(null);
            ConnectionService.adminReset(server);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    private static void leaveParties(FakePlayer... players) {
        for (FakePlayer player : players) {
            for (int i = 0; i < 8 && PartyService.partyOf(player) != null; i++) {
                PartyService.leave(player);
            }
        }
    }

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(("amemaru-gametest:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(AmemaruMod.id(path));
        if (item == null) {
            throw new IllegalStateException("アイテムが見つかりません：" + path);
        }
        return item;
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
        return FakePlayerFactory.get(level, new GameProfile(uuid(name), profileName));
    }

    private static PlayerData data(GameTestHelper helper, ServerPlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> {
            helper.fail("プレイヤーデータが付いていません");
            return new IllegalStateException("no player data");
        });
    }
}
