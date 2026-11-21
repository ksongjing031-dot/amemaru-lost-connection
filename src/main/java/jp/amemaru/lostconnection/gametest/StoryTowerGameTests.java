package jp.amemaru.lostconnection.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.DaySchedule;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.Conditions;
import jp.amemaru.lostconnection.story.DialogueDef;
import jp.amemaru.lostconnection.story.DialogueDefs;
import jp.amemaru.lostconnection.story.DialogueManager;
import jp.amemaru.lostconnection.story.GuideService;
import jp.amemaru.lostconnection.story.OpeningService;
import jp.amemaru.lostconnection.story.PlayerStory;
import jp.amemaru.lostconnection.story.StoryEventManager;
import jp.amemaru.lostconnection.story.StoryState;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorSealBlock;
import jp.amemaru.lostconnection.tower.TowerService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Phase 5：定義（NPC・会話・ストーリーイベント・手引き・進行日・階）・進行日と階の解放と予告と初撃破・物語の記録・条件・
 * その人ごとのオープニング（遅れて入った人も、別々に最初から）・石柱と封印・1階の形の自動テスト。
 *
 * <p>テスト用の体（FakePlayer）はサーバーの人の一覧にいないので、知らせとパケット（演出・会話の画面）は届かない。クライアントからの
 * 「準備できた」「見終えた」「選んだ」の知らせは、テストが代わりにサーバーの受け口へ渡す（決まりだけを確かめる）。演出と画面の
 * 見た目は、CIの写真（phase5_*）で見る。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StoryTowerGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final ResourceLocation JOB_DIALOGUE = AmemaruMod.id("lilia/job");

    private StoryTowerGameTests() {
    }

    // ---------------------------------------------------------------- 定義

    /** Phase 5 の定義が、問題なく読み込まれている（数・オープニングの演出と会話・1階）。 */
    @GameTest(template = EMPTY)
    public static void phase5DefinitionsLoad(GameTestHelper helper) {
        check(helper, NpcDefs.loader().errors().isEmpty(), "NPCの定義に問題があります：" + NpcDefs.loader().errors());
        check(helper, DialogueDefs.loader().errors().isEmpty(), "会話の定義に問題があります：" + DialogueDefs.loader().errors());
        check(helper, StoryEventManager.loader().errors().isEmpty(),
                "ストーリーイベントの定義に問題があります：" + StoryEventManager.loader().errors());
        check(helper, GuideService.loader().errors().isEmpty(), "手引きの定義に問題があります：" + GuideService.loader().errors());
        check(helper, DaySchedule.loader().errors().isEmpty(), "進行日の定義に問題があります：" + DaySchedule.loader().errors());
        check(helper, FloorDefs.loader().errors().isEmpty(), "階の定義に問題があります：" + FloorDefs.loader().errors());
        // 街の5人（リリア・ガルド・セレナ・ドラン・バルド）・会話11・ストーリーイベント4（目覚め・2度目の目覚め・1階に入る・初撃破）
        check(helper, NpcDefs.all().size() == 5, "NPCの数が5ではありません：" + NpcDefs.all().keySet());
        check(helper, DialogueDefs.all().size() == 11, "会話の数が11ではありません：" + DialogueDefs.all().keySet());
        check(helper, StoryEventManager.all().size() == 4, "ストーリーイベントの数が4ではありません");
        for (DialogueDef dialogue : DialogueDefs.all().values()) {
            check(helper, NpcDefs.get(dialogue.npc()) != null, "会話 " + dialogue.id() + " のNPC " + dialogue.npc() + " の定義がありません");
        }
        check(helper, DialogueDefs.get(OpeningService.OPENING_DIALOGUE) != null, "オープニングのリリアの会話がありません");
        check(helper, StoryEventManager.get(OpeningService.OPENING) != null && StoryEventManager.get(OpeningService.OPENING_AGAIN) != null,
                "オープニングの演出（目覚め・2度目の目覚め）がありません");
        // N日目にN階（1日目は1階だけ）
        check(helper, DaySchedule.floorsUntil(1).equals(List.of(1)), "1日目に開く階が1階だけではありません：" + DaySchedule.floorsUntil(1));
        check(helper, DaySchedule.floorsUntil(2).contains(2) && !DaySchedule.floorsUntil(2).contains(3),
                "2日目に開く階が違います：" + DaySchedule.floorsUntil(2));
        FloorDef floor1 = FloorDefs.byNumber(1);
        check(helper, floor1 != null, "1階の定義がありません");
        check(helper, floor1.pillars().size() == 3 && floor1.bossRoom() != null && floor1.tablet() != null && !floor1.spawns().isEmpty(),
                "1階に石柱3本・ボスの部屋・石板・湧く所がそろっていません");
        helper.succeed();
    }

    /** 1階の形：広い（384×384）。入口・石柱・石板・湧く所・ボスの部屋が階の中。ボスと出口の円は部屋の中、湧く所と石柱は部屋の外。 */
    @GameTest(template = EMPTY)
    public static void floor1Layout(GameTestHelper helper) {
        FloorDef def = FloorDefs.byNumber(1);
        check(helper, def != null && def.bossRoom() != null, "1階の定義がありません");
        check(helper, def.sizeX() >= 256 && def.sizeZ() >= 256, "1階が狭すぎます（" + def.sizeX() + "×" + def.sizeZ() + "）");
        Vec3 o = def.originVec();
        FloorDef.BossRoomDef boss = def.bossRoom();
        check(helper, def.contains(o.add(def.entrance().pos())), "入口が階の外です");
        check(helper, !boss.inside(o.add(def.entrance().pos()), o), "入口がボスの部屋の中です");
        Vec3 bossSpawn = o.add(boss.spawn().pos());
        check(helper, def.contains(bossSpawn) && boss.inside(bossSpawn, o), "ボスの出る所が部屋の中ではありません");
        Vec3 exit = o.add(boss.exit().center());
        check(helper, boss.inside(exit, o), "出口の円がボスの部屋の中ではありません");
        for (FloorDef.PillarDef pillar : def.pillars()) {
            Vec3 at = Vec3.atBottomCenterOf(def.world(pillar.pos()));
            check(helper, def.contains(at) && !boss.inside(at, o), "石柱 " + (pillar.index() + 1) + " の場所が違います（階の外か、ボスの部屋の中）");
            check(helper, !pillar.line().isEmpty(), "石柱 " + (pillar.index() + 1) + " に古代文字の一行がありません");
        }
        for (FloorDef.SpawnZone zone : def.spawns()) {
            Vec3 at = o.add(zone.center());
            check(helper, def.contains(at), "湧く所 " + zone.id() + " が階の外です");
            check(helper, !boss.inside(at, o), "湧く所 " + zone.id() + " がボスの部屋の中です（部屋には敵を湧かせない）");
        }
        check(helper, def.contains(Vec3.atBottomCenterOf(def.world(def.tablet().pos()))), "石板が階の外です");
        // 封印は入口の門の幅（両端を含む）で、部屋の縁にある
        BlockPosBox seal = new BlockPosBox(def.world(boss.sealMin()), def.world(boss.sealMax()));
        check(helper, seal.width() >= 3 && seal.height() >= 3, "封印の幅か高さが足りません：" + seal);
        helper.succeed();
    }

    // ---------------------------------------------------------------- 進行日・階の解放・予告・初撃破・加護

    /** 階の解放と閉じる・予告の順番と取り出し・初撃破は最初の1回だけ・加護は0〜4・保存して読み直しても同じ。 */
    @GameTest(template = EMPTY)
    public static void progressionRules(GameTestHelper helper) {
        ProgressionData data = new ProgressionData();
        data.setDay(1);
        check(helper, data.unlock(1) && !data.unlock(1) && data.isUnlocked(1), "1階を開けません（2度目は false のはず）");
        check(helper, data.unlock(2) && data.lock(2) && !data.lock(2) && !data.isUnlocked(2), "2階を閉じられません");
        ProgressionData.Scheduled late = data.schedule(ProgressionData.ScheduleKind.UNLOCK_FLOOR, 3, "", 5_000L);
        ProgressionData.Scheduled early = data.schedule(ProgressionData.ScheduleKind.UNLOCK_FLOOR, 2, "", 1_000L);
        check(helper, data.nextFloorUnlock() == early, "いちばん早い階の解放の予告が違います：" + data.nextFloorUnlock());
        List<ProgressionData.Scheduled> due = data.takeDue(2_000L);
        check(helper, due.size() == 1 && due.get(0) == early && data.scheduled().size() == 1, "時刻になった予告の取り出しが違います：" + due);
        check(helper, data.cancel(late.id()) && data.scheduled().isEmpty() && data.nextFloorUnlock() == null, "予告を取り消せません");
        check(helper, data.recordFirstKill(1, 123L, List.of("A", "B")) && !data.recordFirstKill(1, 456L, List.of("C")),
                "初撃破が2度記録されました");
        ProgressionData.FirstKill first = data.firstKill(1);
        check(helper, first != null && first.millis() == 123L && first.members().equals(List.of("A", "B")) && first.day() == 1,
                "初撃破の記録が違います：" + first);
        data.setBlessing(1, 9);
        check(helper, data.blessing(1) == ProgressionData.MAX_BLESSING, "加護が上限を超えました：" + data.blessing(1));
        data.setBlessing(1, -3);
        check(helper, data.blessing(1) == 0, "加護が0より小さくなりました：" + data.blessing(1));
        data.setBlessing(1, 2);
        data.setDay(999);
        check(helper, data.day() == ProgressionData.MAX_DAY, "進行日が上限を超えました：" + data.day());
        data.setDay(3);
        data.schedule(ProgressionData.ScheduleKind.UNLOCK_FLOOR, 4, "予告", 9_000L);
        ProgressionData loaded = ProgressionData.load(data.save(new CompoundTag()));
        check(helper, loaded.day() == 3 && loaded.isUnlocked(1) && !loaded.isUnlocked(2) && loaded.blessing(1) == 2,
                "読み直した進行日・解放・加護が違います");
        check(helper, loaded.firstKill(1) != null && loaded.firstKill(1).members().equals(List.of("A", "B")), "読み直した初撃破が違います");
        check(helper, loaded.scheduled().size() == 1 && loaded.scheduled().get(0).value() == 4
                && "予告".equals(loaded.scheduled().get(0).text()), "読み直した予告が違います：" + loaded.scheduled());
        helper.succeed();
    }

    // ---------------------------------------------------------------- 本人の物語の記録・条件

    /** 石柱は同じものを2度数えない・階の訪問・ボスの撃破・既読・見た演出・手引き・フラグ。保存して読み直しても同じ。 */
    @GameTest(template = EMPTY)
    public static void playerStoryRules(GameTestHelper helper) {
        int[] changes = new int[1];
        PlayerStory story = new PlayerStory(() -> changes[0]++);
        ResourceLocation flag = AmemaruMod.id("test/story_flag");
        check(helper, story.add(flag) && !story.add(flag) && story.has(flag), "フラグの付け方が違います");
        check(helper, story.activatePillar(1, 0) && !story.activatePillar(1, 0) && story.activatePillar(1, 2), "石柱の起動の数え方が違います");
        check(helper, story.pillars(1) == 0b101 && story.pillars(2) == 0, "石柱の記録が違います：" + story.pillars(1));
        check(helper, story.markVisited(1) && !story.markVisited(1) && story.visited(1) && !story.visited(2), "訪れた階の記録が違います");
        story.addBossKill(1);
        story.addBossKill(1);
        check(helper, story.bossKills(1) == 2, "ボスの撃破の数が違います：" + story.bossKills(1));
        story.markRead(OpeningService.OPENING_DIALOGUE);
        story.markSeen(OpeningService.OPENING);
        story.setOpening(PlayerStory.OPENING_AWAKENED);
        story.setGuide(3);
        check(helper, changes[0] > 0, "記録が変わったことを知らせていません");
        PlayerStory loaded = new PlayerStory(() -> { });
        loaded.load(story.save());
        check(helper, loaded.has(flag) && loaded.pillars(1) == 0b101 && loaded.visited(1) && loaded.bossKills(1) == 2,
                "読み直したフラグ・石柱・訪問・撃破が違います");
        check(helper, loaded.read(OpeningService.OPENING_DIALOGUE) && loaded.seen(OpeningService.OPENING)
                && loaded.opening() == PlayerStory.OPENING_AWAKENED && loaded.guide() == 3, "読み直した既読・演出・オープニング・手引きが違います");
        PlayerStory copied = new PlayerStory(() -> { });
        copied.copyFrom(loaded);
        check(helper, copied.pillars(1) == 0b101 && copied.has(flag), "写した記録が違います（死んで戻ったときに消える）");
        loaded.resetPillars(1);
        check(helper, loaded.pillars(1) == 0, "石柱を戻せません");
        helper.succeed();
    }

    /** 条件：本人のフラグとサーバー全体のフラグ・オープニングの進み・進行日・職業。 */
    @GameTest(template = EMPTY)
    public static void conditionsRules(GameTestHelper helper) {
        FakePlayer player = fake(helper, "cond");
        PlayerData data = data(helper, player);
        StoryState state = StoryState.get(helper.getLevel().getServer());
        ResourceLocation mine = AmemaruMod.id("test/cond_mine");
        ResourceLocation world = AmemaruMod.id("test/cond_world");
        ResourceLocation never = AmemaruMod.id("test/cond_never");
        data.getStory().remove(mine);
        state.remove(world);
        try {
            Conditions both = parse("{\"flags_all\":[\"amemaru:test/cond_mine\",\"amemaru:test/cond_world\"],"
                    + "\"flags_none\":[\"amemaru:test/cond_never\"]}");
            check(helper, !both.test(player), "フラグがないのに条件に合いました");
            data.getStory().add(mine);
            check(helper, !both.test(player), "サーバー全体のフラグがないのに条件に合いました");
            state.add(world);
            check(helper, both.test(player), "本人とサーバー全体のフラグがあるのに条件に合いません");
            data.getStory().add(never);
            check(helper, !both.test(player), "あってはいけないフラグがあるのに条件に合いました");
            data.getStory().remove(never);
            check(helper, parse("{\"flags_any\":[\"amemaru:test/cond_never\",\"amemaru:test/cond_world\"]}").test(player),
                    "どれか1つのフラグ（flags_any）で合いません");
            // オープニングの進み
            data.getStory().setOpening(PlayerStory.OPENING_DONE);
            check(helper, !parse("{\"opening_max\":1}").test(player) && parse("{\"opening\":2}").test(player),
                    "オープニングの進みの条件が違います");
            data.getStory().setOpening(PlayerStory.OPENING_NONE);
            check(helper, parse("{\"opening_max\":1}").test(player) && !parse("{\"opening\":2}").test(player),
                    "オープニングの前の条件が違います");
            // 進行日（今の日を使う）
            int day = ProgressionData.get(helper.getLevel().getServer()).day();
            check(helper, parse("{\"day_min\":" + day + ",\"day_max\":" + day + "}").test(player)
                    && !parse("{\"day_max\":" + (day - 1) + "}").test(player), "進行日の条件が違います（今は" + day + "日目）");
            if (day < ProgressionData.MAX_DAY) {
                check(helper, !parse("{\"day_min\":" + (day + 1) + "}").test(player), "進行日の条件が違います（今は" + day + "日目）");
            }
            // 職業（テストの体は戦闘職を選んでいない）
            check(helper, data.getCombatJob() != null || parse("{\"has_job\":false}").test(player) && !parse("{\"has_job\":true}").test(player),
                    "職業の条件が違います");
        } finally {
            data.getStory().remove(mine);
            data.getStory().remove(never);
            state.remove(world);
            OpeningService.reset(player);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- オープニング（その人ごと）

    /**
     * オープニング：準備を待つ → 準備ができた知らせで演出が始まる → 見終えた知らせでリリアの会話 → 名前を答えると名簿に書かれて
     * オープニングが終わる → 最後まで読むと職業の画面（会話から開いた扱い）。後から別の人が始めても、前の人の進みと混ざらない。
     */
    @GameTest(template = EMPTY)
    public static void openingPerPlayer(GameTestHelper helper) {
        FakePlayer first = fake(helper, "open_a");
        FakePlayer late = fake(helper, "open_b");
        ServerLevel level = helper.getLevel();
        StoryState state = StoryState.get(level.getServer());
        boolean hadAwaken = state.has(OpeningService.FLAG_AWAKEN);
        boolean hadOpening = state.happened(OpeningService.OPENING);
        boolean hadAgain = state.happened(OpeningService.OPENING_AGAIN);
        OpeningService.reset(first);
        OpeningService.reset(late);
        try {
            // 1人目：準備を待つ（この間はダメージを受けない）
            OpeningService.begin(first);
            check(helper, OpeningService.isPending(first) && OpeningService.inOpening(first), "準備を待っていません");
            check(helper, !OpeningService.isPending(late), "始めていない人まで準備を待っています");
            OpeningService.onClientReady(first);
            check(helper, !OpeningService.isPending(first) && StoryEventManager.isPlaying(first)
                    && OpeningService.OPENING.equals(StoryEventManager.eventOf(first)), "準備ができても、目覚めの演出が始まりません");
            // 2人目は遅れて始める（1人目が演出の途中でも、自分の演出を最初から）
            OpeningService.begin(late);
            OpeningService.onClientReady(late);
            int sceneA = StoryEventManager.sceneOf(first);
            int sceneB = StoryEventManager.sceneOf(late);
            check(helper, StoryEventManager.isPlaying(late) && sceneB != sceneA, "遅れて入った人の演出が、前の人と同じになっています");
            // 2人目の見終えた知らせを、1人目の演出の番号で送っても、1人目は終わらない
            StoryEventManager.onSceneDone(late, sceneA);
            check(helper, StoryEventManager.isPlaying(late) && StoryEventManager.isPlaying(first), "違う演出の番号で、演出が終わりました");
            // 1人目が見終えた：目覚めた（AWAKENED）・リリアの会話が始まる
            StoryEventManager.onSceneDone(first, sceneA);
            PlayerData a = data(helper, first);
            check(helper, !StoryEventManager.isPlaying(first) && a.getStory().opening() == PlayerStory.OPENING_AWAKENED
                    && a.getStory().has(OpeningService.FLAG_AWAKENED) && a.getStory().seen(OpeningService.OPENING),
                    "見終えても目覚めていません：" + a.getStory().opening());
            ResourceLocation dialogue = DialogueManager.dialogueOf(first);
            check(helper, dialogue != null && OpeningService.isOpeningDialogue(dialogue), "目覚めた後に、リリアの会話が始まりません：" + dialogue);
            check(helper, data(helper, late).getStory().opening() == PlayerStory.OPENING_NONE && !DialogueManager.isTalking(late),
                    "まだ見ている2人目まで、目覚めた扱いになっています");
            // 名前を答える → 名簿に書かれる（オープニングの終わり）
            DialogueManager.onChoice(first, DialogueManager.sessionOf(first), 0);
            check(helper, a.getStory().opening() == PlayerStory.OPENING_DONE && a.getStory().has(OpeningService.FLAG_REGISTERED),
                    "名前を答えても名簿に書かれていません");
            // 最後まで読む → 職業の画面（会話から開いた）・既読
            DialogueManager.onChoice(first, DialogueManager.sessionOf(first), -1);
            check(helper, !DialogueManager.isTalking(first) && a.getStory().read(dialogue), "最後まで読んでも会話が終わりません");
            check(helper, a.isOathFromDialogue(), "会話の終わりに、職業の画面を（会話から）開いていません");
            check(helper, !OpeningService.inOpening(first), "オープニングが終わっていません");
            // 終わった後のリリアは、職業を選ぶように勧める会話（戦闘職がなければ）
            DialogueDef next = DialogueDefs.choose(first, OpeningService.LILIA);
            check(helper, a.getCombatJob() != null || next != null && JOB_DIALOGUE.equals(next.id()),
                    "オープニングの後のリリアの会話が違います：" + (next == null ? null : next.id()));
            // 2人目は、まだ自分の演出の途中のまま。見終えると、自分の会話が始まる
            check(helper, StoryEventManager.isPlaying(late), "1人目が終わったら、2人目の演出まで止まりました");
            StoryEventManager.onSceneDone(late, sceneB);
            check(helper, data(helper, late).getStory().opening() == PlayerStory.OPENING_AWAKENED && DialogueManager.isTalking(late),
                    "2人目が見終えても、目覚めて会話が始まりません");
            // 2人目は途中で閉じた：オープニングは終わっていない（手引きでリリアの所を示す）。もう一度始めると「2度目の目覚め」
            DialogueManager.onChoice(late, DialogueManager.sessionOf(late), -2);
            check(helper, !DialogueManager.isTalking(late) && data(helper, late).getStory().opening() == PlayerStory.OPENING_AWAKENED,
                    "会話を閉じたらオープニングが終わった扱いになりました");
            OpeningService.begin(late);
            OpeningService.onClientReady(late);
            check(helper, OpeningService.OPENING_AGAIN.equals(StoryEventManager.eventOf(late)),
                    "目覚めた後にもう一度始めると、2度目の目覚めになりません：" + StoryEventManager.eventOf(late));
            StoryEventManager.onSceneDone(late, StoryEventManager.sceneOf(late));
        } finally {
            for (FakePlayer p : List.of(first, late)) {
                int session = DialogueManager.sessionOf(p);
                if (session >= 0) {
                    DialogueManager.onChoice(p, session, -2);
                }
                int scene = StoryEventManager.sceneOf(p);
                if (scene >= 0) {
                    StoryEventManager.onSceneDone(p, scene);
                    int again = DialogueManager.sessionOf(p);
                    if (again >= 0) {
                        DialogueManager.onChoice(p, again, -2);
                    }
                }
                OpeningService.skip(p);
                OpeningService.reset(p);
                PlayerDataCapability.get(p).ifPresent(d -> d.setOathCrystal(null, null, 0L));
            }
            if (!hadAwaken) {
                state.remove(OpeningService.FLAG_AWAKEN);
            }
            if (!hadOpening) {
                state.forget(OpeningService.OPENING);
            }
            if (!hadAgain) {
                state.forget(OpeningService.OPENING_AGAIN);
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 石柱と封印

    /** 1階の石柱3本を起動するまで、封印は通れない（1本ずつ数える。同じ石柱は2度数えない）。クリエイティブは通れる。 */
    @GameTest(template = EMPTY)
    public static void pillarsOpenSeal(GameTestHelper helper) {
        FakePlayer player = fake(helper, "pillar");
        PlayerData data = data(helper, player);
        FloorDef def = FloorDefs.byNumber(1);
        check(helper, def != null && def.pillars().size() == 3, "1階の石柱の定義がありません");
        // テストの体は、はじめはサバイバル（クリエイティブ・スペクテイターは封印を通れる）
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        data.getStory().resetPillars(1);
        try {
            check(helper, !TowerService.pillarsComplete(player, 1) && !FloorSealBlock.canPass(player, 1), "石柱を起動する前に封印を通れます");
            for (int i = 0; i < def.pillars().size(); i++) {
                TowerService.activatePillar(player, 1, i, def.world(def.pillars().get(i).pos()));
                TowerService.activatePillar(player, 1, i, def.world(def.pillars().get(i).pos()));
                boolean last = i == def.pillars().size() - 1;
                check(helper, Integer.bitCount(data.getStory().pillars(1)) == i + 1, "起動した石柱の数が違います：" + data.getStory().pillars(1));
                check(helper, TowerService.pillarsComplete(player, 1) == last && FloorSealBlock.canPass(player, 1) == last,
                        (i + 1) + "本目の後の封印が違います");
            }
            // 知らない石柱の番号は数えない
            TowerService.activatePillar(player, 1, 7, def.world(def.pillars().get(0).pos()));
            check(helper, Integer.bitCount(data.getStory().pillars(1)) == 3, "ない石柱の番号を数えました");
            data.getStory().resetPillars(1);
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            check(helper, FloorSealBlock.canPass(player, 1), "クリエイティブで封印を通れません");
        } finally {
            data.getStory().resetPillars(1);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 小物

    private record BlockPosBox(net.minecraft.core.BlockPos min, net.minecraft.core.BlockPos max) {
        int width() {
            return Math.max(Math.abs(max.getX() - min.getX()), Math.abs(max.getZ() - min.getZ())) + 1;
        }

        int height() {
            return Math.abs(max.getY() - min.getY()) + 1;
        }
    }

    private static Conditions parse(String json) {
        return Conditions.parse(JsonParser.parseString(json).getAsJsonObject());
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

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
