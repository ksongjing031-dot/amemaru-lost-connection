package jp.amemaru.lostconnection.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.economy.MarketData;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.guild.BondService;
import jp.amemaru.lostconnection.guild.GuildCounters;
import jp.amemaru.lostconnection.guild.GuildData;
import jp.amemaru.lostconnection.guild.GuildHooks;
import jp.amemaru.lostconnection.guild.GuildState;
import jp.amemaru.lostconnection.guild.PartyBoard;
import jp.amemaru.lostconnection.guild.QuestDef;
import jp.amemaru.lostconnection.guild.QuestDefs;
import jp.amemaru.lostconnection.guild.QuestService;
import jp.amemaru.lostconnection.guild.RecordDefs;
import jp.amemaru.lostconnection.guild.RecordService;
import jp.amemaru.lostconnection.guild.StallDonation;
import jp.amemaru.lostconnection.guild.TitleDef;
import jp.amemaru.lostconnection.guild.TitleDefs;
import jp.amemaru.lostconnection.guild.TitleService;
import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.story.Conditions;
import jp.amemaru.lostconnection.story.DialogueDefs;
import jp.amemaru.lostconnection.story.PlayerStory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Phase 6 その3：ギルド（称号・限定称号・古代記録と記録帳・親密度・クエスト・パーティ募集・縁帳・露店の区画の寄付）。
 * 偽のプレイヤーはサーバーの一覧にいないので、「いない人には次に入ったときに渡す」の道（預かり）も、ここで確かめられる。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuildGameTests {
    private static final String EMPTY = "empty3x3x3";

    private GuildGameTests() {
    }

    /** 定義：称号63・古代記録30・クエスト28が、問題なく読める。ノアとエルネストがいて、親密度と現れる日がある。会話も読める。 */
    @GameTest(template = EMPTY)
    public static void guildDefinitionsLoad(GameTestHelper helper) {
        check(helper, TitleDefs.loader().errors().isEmpty() && TitleDefs.all().size() == 63,
                "称号の定義が読めません（" + TitleDefs.all().size() + "）：" + TitleDefs.loader().errors());
        check(helper, RecordDefs.loader().errors().isEmpty() && RecordDefs.all().size() == 30,
                "古代記録の定義が読めません（" + RecordDefs.all().size() + "）：" + RecordDefs.loader().errors());
        check(helper, QuestDefs.loader().errors().isEmpty() && QuestDefs.all().size() == 28,
                "クエストの定義が読めません（" + QuestDefs.all().size() + "）：" + QuestDefs.loader().errors());
        check(helper, DialogueDefs.loader().errors().isEmpty(), "会話の定義に問題があります：" + DialogueDefs.loader().errors());
        for (String name : new String[]{"lilia", "gard", "doran", "serena", "ernesto", "noa"}) {
            NpcDef def = NpcDefs.get(id(name));
            check(helper, def != null && def.bond() != null && def.bond().keepsake() != null, name + " の親密度の定義がありません");
        }
        check(helper, NpcDefs.get(id("noa")).appears() != null && NpcDefs.get(id("ernesto")).appears() != null,
                "ノアとエルネストの現れる日がありません");
        TitleDef pioneer = TitleDefs.get(id("market_pioneer"));
        check(helper, pioneer != null && pioneer.kind() == TitleDef.Kind.LIMITED && pioneer.color() == TitleDef.Kind.LIMITED.color(),
                "限定称号「市場の開拓者」の種類か色が違います");
        TitleDef top = TitleDefs.get(id("hunter_3"));
        check(helper, top != null && top.topTier() && top.color() == 0xF2C94C, "段階の最上位の称号が金ではありません");
        helper.succeed();
    }

    /**
     * 称号：数の条件（取引10回で「露店デビュー」）・表示する（名前の前に【】）・持っていない称号は表示できない・取り消し・
     * できごと（完璧なアクセサリー）・隠し称号「ゼロからの一歩」（手持ち0から1万ノット）・組んだ人数の条件は25人基準で変わる・
     * ユニーク称号（その場で作る。1人限定）。
     */
    @GameTest(template = EMPTY)
    public static void guildTitles(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer player = fake(helper, "gd_title");
        PlayerData data = data(helper, player);
        data.getGuild().load(new CompoundTag());
        int participants = ServerConfig.GUILD_PARTICIPANTS.get();
        GuildState.CustomTitle custom = null;
        try {
            data.getGuild().setCounter(GuildCounters.TRADES, 10);
            TitleService.check(player);
            check(helper, data.getGuild().hasTitle(id("trader_1")) && !data.getGuild().hasTitle(id("trader_2")),
                    "取引10回で「露店デビュー」だけになりません");
            check(helper, TitleService.setDisplayed(player, id("trader_1")) && id("trader_1").equals(data.getGuild().displayedTitle()),
                    "称号を表示できません");
            MutableComponent prefix = TitleService.prefix(player);
            check(helper, prefix != null && prefix.getString().equals("【露店デビュー】"), "名前の前の称号が違います：" + prefix);
            check(helper, !TitleService.setDisplayed(player, id("trader_3")), "持っていない称号を表示できました");
            check(helper, TitleService.revoke(player, id("trader_1")) && !data.getGuild().hasTitle(id("trader_1"))
                    && data.getGuild().displayedTitle() == null, "称号を取り消しても、表示が残っています");

            TitleService.onEvent(player, "perfect_accessory");
            check(helper, data.getGuild().hasTitle(id("perfect_crystal")), "完璧なアクセサリーで「完璧の結晶」がもらえません");

            Wallet.set(player, data, 500, "gametest");
            Wallet.set(player, data, 0, "gametest");
            Wallet.give(player, data, 9999, "gametest");
            check(helper, !data.getGuild().hasTitle(id("zero_step")), "1万ノットに届く前に「ゼロからの一歩」がもらえました");
            Wallet.give(player, data, 1, "gametest");
            check(helper, data.getGuild().hasTitle(id("zero_step")), "手持ち0から1万ノット稼いでも「ゼロからの一歩」がもらえません");

            check(helper, TitleService.required(TitleDefs.get(id("hello_3"))) == 20 * participants / 25 || participants != 25,
                    "25人のとき「コネリアの縁結び」が20人ではありません");
            ServerConfig.GUILD_PARTICIPANTS.set(10);
            check(helper, TitleService.required(TitleDefs.get(id("hello_3"))) == 8 && TitleService.required(TitleDefs.get(id("hello_1"))) == 2,
                    "10人のときの「組んだ人数」の条件が 8・2 ではありません");
            ServerConfig.GUILD_PARTICIPANTS.set(3);
            check(helper, TitleService.required(TitleDefs.get(id("hello_3"))) == 2, "3人のとき、自分のほかの人数より多い条件になっています");
            ServerConfig.GUILD_PARTICIPANTS.set(participants);

            custom = TitleService.createUnique(server, "名場面メーカー", "ゲームテスト", -1, true, "gametest");
            TitleDef def = TitleService.def(server, custom.id());
            check(helper, def != null && def.kind() == TitleDef.Kind.UNIQUE && def.rainbow(), "ユニーク称号が虹色のユニークになりません");
            check(helper, !TitleService.uniqueTaken(server, custom.id()) && TitleService.grant(player, custom.id(), "gametest")
                    && TitleService.uniqueTaken(server, custom.id()), "1人限定のユニーク称号の「もう持っている人がいる」が働きません");
        } finally {
            ServerConfig.GUILD_PARTICIPANTS.set(participants);
            if (custom != null) {
                GuildState.get(server).removeCustom(custom.id());
            }
            data.getGuild().load(new CompoundTag());
        }
        helper.succeed();
    }

    /**
     * 限定称号「市場の開拓者」：フリマの最初の取引の売り手と買い手の2人だけ。サーバーにいない人には預かって、次に入ったときに渡す
     * （取引の数と縁帳も）。2回目の取引では取った人が変わらない。
     */
    @GameTest(template = EMPTY)
    public static void guildLimitedTitles(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GuildState state = GuildState.get(server);
        ResourceLocation pioneer = id("market_pioneer");
        FakePlayer seller = fake(helper, "gd_seller");
        FakePlayer buyer = fake(helper, "gd_buyer");
        FakePlayer late = fake(helper, "gd_late");
        PlayerData sd = data(helper, seller);
        PlayerData bd = data(helper, buyer);
        List<GuildState.Holder> before = List.copyOf(state.holders(pioneer));
        try {
            state.clearHolders(pioneer);
            sd.getGuild().load(new CompoundTag());
            bd.getGuild().load(new CompoundTag());
            GuildHooks.onMarketTrade(server, buyer.getUUID(), "gd_buyer", seller.getUUID(), "gd_seller");
            check(helper, state.limitedTaken(pioneer) && state.holders(pioneer).size() == 2, "最初の取引の2人が、取った人になっていません");
            check(helper, !sd.getGuild().hasTitle(pioneer), "いない人に、その場で称号が渡りました（預かりのはず）");
            GuildHooks.applyPending(seller, sd);
            GuildHooks.applyPending(buyer, bd);
            check(helper, sd.getGuild().hasTitle(pioneer) && bd.getGuild().hasTitle(pioneer), "次に入ったときに、限定称号が渡りません");
            check(helper, sd.getGuild().counter(GuildCounters.TRADES) == 1 && bd.getGuild().counter(GuildCounters.TRADES) == 1,
                    "取引の数が、預かりから数えられていません");
            GuildData.Connection connection = sd.getGuild().connection(buyer.getUUID());
            check(helper, connection != null && connection.trades() == 1, "縁帳に、取引した相手が残っていません");
            GuildHooks.onMarketTrade(server, late.getUUID(), "gd_late", seller.getUUID(), "gd_seller");
            check(helper, state.holders(pioneer).size() == 2 && state.holders(pioneer).stream().noneMatch(h -> h.uuid().equals(late.getUUID())),
                    "2回目の取引で、限定称号の取った人が変わりました");
        } finally {
            state.clearHolders(pioneer);
            if (!before.isEmpty()) {
                state.setHolders(pioneer, before);
            }
            for (ServerPlayer p : new ServerPlayer[]{seller, buyer, late}) {
                state.takePending(p.getUUID());
            }
            sd.getGuild().load(new CompoundTag());
            bd.getGuild().load(new CompoundTag());
        }
        helper.succeed();
    }

    /**
     * 古代記録：見つけると「未解読」（2回目は増えない）・収集率・エルネストに渡すと解読（記録帳がなければ渡す）・記録帳の中身
     * （30の記録、1階に1つ・15階に8つ）・階を決めた「記録を見つける」クエストは、受ける前に見つけた分も数える。
     */
    @GameTest(template = EMPTY)
    public static void guildRecords(GameTestHelper helper) {
        FakePlayer player = fake(helper, "gd_record");
        PlayerData data = data(helper, player);
        data.getGuild().load(new CompoundTag());
        player.getInventory().clearContent();
        ResourceLocation tablet = id("f01_waystone");
        try {
            check(helper, RecordService.find(player, tablet) && !RecordService.find(player, tablet), "記録が見つからないか、2回見つかりました");
            GuildData.RecordEntry entry = data.getGuild().record(tablet);
            check(helper, entry != null && !entry.deciphered(), "見つけた記録が「未解読」になっていません");
            check(helper, RecordService.percent(data) == 3, "30のうち1つで、収集率が3%ではありません：" + RecordService.percent(data));
            check(helper, RecordService.decipher(player) == 1 && data.getGuild().record(tablet).deciphered(), "解読できません");
            check(helper, data.getGuild().hasRecordBook() && player.getInventory().countItem(ModItems.RECORD_BOOK.get()) == 1,
                    "解読のときに、記録帳が渡りません");
            CompoundTag book = RecordService.bookTag(player, data);
            int[] total = book.getIntArray("total");
            int[] found = book.getIntArray("found");
            check(helper, book.getList("entries", Tag.TAG_COMPOUND).size() == 30 && total[1] == 1 && found[1] == 1 && total[15] == 8
                    && total[20] == 0, "記録帳の中身（数）が違います");
            boolean read = false;
            for (Tag tag : book.getList("entries", Tag.TAG_COMPOUND)) {
                CompoundTag one = (CompoundTag) tag;
                if (one.getString("id").equals(tablet.toString())) {
                    read = "read".equals(one.getString("state")) && !one.getList("pages", Tag.TAG_STRING).isEmpty()
                            && !one.getString("comment").isEmpty();
                } else if ("read".equals(one.getString("state")) || one.contains("pages")) {
                    helper.fail("見つけていない記録の本文が送られています：" + one.getString("id"));
                }
            }
            check(helper, read, "解読した記録の本文とひと言が、記録帳にありません");
            QuestDef quest = QuestDefs.get(id("guild_tablet"));
            data.getGuild().startQuest(quest.id(), quest.objectives().size(), 1);
            check(helper, QuestService.done(player, quest, data.getGuild().quest(quest.id())),
                    "受ける前に見つけた1階の記録が、クエストに数えられていません");
        } finally {
            data.getGuild().load(new CompoundTag());
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    /**
     * 親密度：話すと会った（段階1）・1日1回+10。段階が上がると、3で小さな贈り物、4でゆかりの小物（一度だけ）、5で称号。
     * 会話・クエストの条件の affinity_min は、話しているNPCについて見る。
     */
    @GameTest(template = EMPTY)
    public static void guildBonds(GameTestHelper helper) {
        FakePlayer player = fake(helper, "gd_bond");
        PlayerData data = data(helper, player);
        data.getGuild().load(new CompoundTag());
        player.getInventory().clearContent();
        ResourceLocation lilia = id("lilia");
        try {
            BondService.onTalk(player, lilia);
            check(helper, BondService.points(data, lilia) == 10 && BondService.stage(data, lilia) == 1, "話しても親密度が+10・段階1になりません");
            BondService.onTalk(player, lilia);
            check(helper, BondService.points(data, lilia) == 10, "同じ日に2回話して、親密度が2回上がりました");
            BondService.add(player, lilia, 20, "gametest");
            check(helper, BondService.stage(data, lilia) == 2, "30点で段階2（知り合い）になりません");
            BondService.add(player, lilia, 130, "gametest");
            check(helper, BondService.stage(data, lilia) == 4, "160点で段階4（親友）になりません");
            Item keepsake = ModItems.KEEPSAKE_LILIA.get();
            check(helper, player.getInventory().countItem(Items.COOKIE) == 8 && player.getInventory().countItem(keepsake) == 1,
                    "段階3の贈り物か、段階4のゆかりの小物が渡りません");
            Conditions three = Conditions.parse(JsonParser.parseString("{\"affinity_min\": 3}").getAsJsonObject());
            check(helper, three.test(player, lilia) && !three.test(player, id("gard")), "affinity_min が、話しているNPCについて見ていません");
            BondService.set(player, lilia, 0);
            BondService.add(player, lilia, 160, "gametest");
            check(helper, player.getInventory().countItem(keepsake) == 1, "ゆかりの小物が2回渡りました");
            BondService.add(player, lilia, 500, "gametest");
            check(helper, BondService.points(data, lilia) == BondService.POINTS_CAP && BondService.stage(data, lilia) == 5
                    && data.getGuild().hasTitle(id("friend_lilia")), "段階5で称号「リリアの友」がもらえません");
        } finally {
            data.getGuild().load(new CompoundTag());
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    /**
     * クエスト：職業を登録したら街めぐりを自動で受ける（あきらめられない）・NPCと話す目標・石柱（受ける前の分も数える）を報告して
     * 報酬・一度きりのクエストは2回受けられない・今日のデイリー3つから1つを終えて報告・個人的な依頼は親密度2から。
     */
    @GameTest(template = EMPTY)
    public static void guildQuests(GameTestHelper helper) {
        FakePlayer player = fake(helper, "gd_quest");
        PlayerData data = data(helper, player);
        data.getGuild().load(new CompoundTag());
        player.getInventory().clearContent();
        ResourceLocation oldJob = data.getCombatJob();
        int oldOpening = data.getStory().opening();
        try {
            data.setCombatJob(id("knight"));
            data.getStory().setOpening(PlayerStory.OPENING_DONE);
            QuestService.tick(player, data);
            check(helper, data.getGuild().quest(QuestService.TOWN_TOUR) != null, "職業を登録しても、街めぐりを受けていません");
            QuestService.onTalk(player, id("doran"));
            check(helper, data.getGuild().quest(QuestService.TOWN_TOUR).count(0) == 1, "ドランと話しても、街めぐりの目標が進みません");
            check(helper, !QuestService.abandon(player, QuestService.TOWN_TOUR), "街めぐりをあきらめられました");

            for (int i = 0; i < 3; i++) {
                data.getStory().activatePillar(1, i);
            }
            ResourceLocation pillars = id("guild_pillars");
            check(helper, QuestService.accept(player, pillars, null), "「三本の石柱」を受けられません");
            long knots = data.getKnots();
            check(helper, QuestService.report(player, pillars, null) == 1 && data.getKnots() == knots + 200
                    && data.getGuild().completed(pillars) != null, "受ける前に起動した石柱で、報告できないか報酬が違います");
            check(helper, !QuestService.accept(player, pillars, null), "一度きりのクエストを、2回受けられました");

            QuestService.ensureDaily(player, data);
            check(helper, data.getGuild().dailyOffer().size() == 3, "今日のデイリーが3つではありません：" + data.getGuild().dailyOffer());
            ResourceLocation daily = data.getGuild().dailyOffer().get(0);
            QuestDef def = QuestDefs.get(daily);
            check(helper, QuestService.accept(player, daily, null), "デイリーを受けられません");
            for (QuestDef.Objective o : def.objectives()) {
                switch (o.type()) {
                    case "kill" -> {
                        for (int i = 0; i < o.count(); i++) {
                            QuestService.onKill(player, o.target(), 1);
                        }
                    }
                    case "kill_any" -> {
                        for (int i = 0; i < o.count(); i++) {
                            QuestService.onKill(player, id("tsunomofu"), 1);
                        }
                    }
                    case "collect" -> player.getInventory().add(new ItemStack(ForgeRegistries.ITEMS.getValue(o.target()), o.count()));
                    default -> helper.fail("デイリーに、確かめていない目標があります：" + o.type());
                }
            }
            check(helper, QuestService.done(player, def, data.getGuild().quest(daily)), "デイリーの目標を果たしても、終わっていません：" + daily);
            check(helper, QuestService.report(player, daily, null) == 1 && data.getGuild().dailyDone() == 1, "デイリーを報告できません");

            QuestDef personal = QuestDefs.get(id("lilia_1"));
            check(helper, QuestService.state(player, data, personal) == QuestService.State.LOCKED, "親密度1で、個人的な依頼が受けられます");
            BondService.set(player, id("lilia"), 40);
            check(helper, QuestService.state(player, data, personal) == QuestService.State.AVAILABLE, "親密度2で、個人的な依頼が受けられません");
        } finally {
            data.setCombatJob(oldJob);
            data.getStory().setOpening(oldOpening);
            data.getGuild().load(new CompoundTag());
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    /** パーティ募集（出す・申し込む・申し込みは1回・断る・取り下げる）と、縁帳（起こした・受け渡し・組んだ）。 */
    @GameTest(template = EMPTY)
    public static void guildPartyBoardAndConnections(GameTestHelper helper) {
        FakePlayer owner = fake(helper, "gd_owner");
        FakePlayer applicant = fake(helper, "gd_applicant");
        PlayerData od = data(helper, owner);
        PlayerData ad = data(helper, applicant);
        od.getGuild().load(new CompoundTag());
        ad.getGuild().load(new CompoundTag());
        PartyBoard.clear();
        try {
            check(helper, PartyBoard.post(owner, "1階のボスに行きます", 1, 1) && PartyBoard.owners().contains(owner.getUUID()),
                    "パーティ募集を出せません");
            check(helper, PartyBoard.apply(applicant, owner.getUUID()) && !PartyBoard.apply(applicant, owner.getUUID()),
                    "申し込めないか、2回申し込めました");
            ListTag board = PartyBoard.tag(owner);
            CompoundTag mine = board.getCompound(0);
            check(helper, mine.getBoolean("mine") && mine.getList("applicants", Tag.TAG_COMPOUND).size() == 1,
                    "自分の募集に、申し込んだ人が出ていません");
            check(helper, !PartyBoard.tag(applicant).getCompound(0).contains("applicants"), "ほかの人の募集の申し込み者が見えています");
            check(helper, PartyBoard.answer(owner, applicant.getUUID(), false)
                    && PartyBoard.tag(owner).getCompound(0).getList("applicants", Tag.TAG_COMPOUND).isEmpty(), "申し込みを断れません");
            check(helper, PartyBoard.cancel(owner) && PartyBoard.owners().isEmpty(), "募集を取り下げられません");

            GuildHooks.onRevive(owner, applicant);
            GuildHooks.onKnotTransfer(owner, applicant);
            GuildHooks.onPartyJoined(applicant, List.<ServerPlayer>of(owner, applicant));
            GuildData.Connection mineToApplicant = od.getGuild().connection(applicant.getUUID());
            GuildData.Connection applicantToMine = ad.getGuild().connection(owner.getUUID());
            check(helper, mineToApplicant != null && mineToApplicant.revived() == 1 && mineToApplicant.trades() == 1 && mineToApplicant.party() == 1,
                    "縁帳（起こした・取引・組んだ）が残っていません");
            check(helper, applicantToMine != null && applicantToMine.revivedBy() == 1 && applicantToMine.party() == 1
                    && ad.getGuild().partners() == 1, "相手の縁帳（起こしてくれた・組んだ）が残っていません");
        } finally {
            PartyBoard.clear();
            od.getGuild().load(new CompoundTag());
            ad.getGuild().load(new CompoundTag());
        }
        helper.succeed();
    }

    /** 露店の区画を増やす寄付：払える分だけ・目標に届いたら締め切る・届いた日の次の朝に区画が増える。 */
    @GameTest(template = EMPTY)
    public static void guildStallDonation(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GuildState state = GuildState.get(server);
        MarketData market = MarketData.get(server);
        boolean expanded = market.expanded();
        FakePlayer player = fake(helper, "gd_donor");
        PlayerData data = data(helper, player);
        try {
            state.resetStallDonation();
            market.setExpanded(false);
            long goal = StallDonation.goal();
            check(helper, goal == 20000L * ServerConfig.GUILD_PARTICIPANTS.get() / 25, "寄付の目標が、25人で2万の割合ではありません：" + goal);
            Wallet.set(player, data, goal + 10000, "gametest");
            check(helper, StallDonation.donate(player, 5000) && state.stallDonated() == 5000 && data.getKnots() == goal + 5000,
                    "寄付できないか、所持金が違います");
            check(helper, !StallDonation.donate(player, 0), "0ノットを寄付できました");
            check(helper, StallDonation.donate(player, goal * 10) && state.stallDonated() == goal && data.getKnots() == 10000
                    && state.stallReachedDay() > 0, "目標を超えて取られたか、目標に届いても締め切られません");
            check(helper, !StallDonation.donate(player, 100), "目標に届いたあとも寄付できました");
            int day = state.stallReachedDay();
            StallDonation.onDay(server, day);
            check(helper, !market.expanded(), "目標に届いた日のうちに、区画が増えました");
            StallDonation.onDay(server, day + 1);
            check(helper, market.expanded(), "目標に届いた次の朝に、区画が増えません");
        } finally {
            state.resetStallDonation();
            market.setExpanded(expanded);
        }
        helper.succeed();
    }

    // ================================================================ 道具

    private static ResourceLocation id(String path) {
        return AmemaruMod.id(path);
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
