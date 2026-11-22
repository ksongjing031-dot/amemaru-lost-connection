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
import net.minecraftforge.registries.ForgeRegistries;
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
        // 街の7人（リリア・ガルド・セレナ・ドラン・バルド、Phase 6 その3のノア・エルネスト）・会話40（Phase 5 の11と、その3の親密度・
        // ノア・エルネストの29）・ストーリーイベント4（目覚め・2度目の目覚め・1階に入る・初撃破）
        check(helper, NpcDefs.all().size() == 7, "NPCの数が7ではありません：" + NpcDefs.all().keySet());
        check(helper, DialogueDefs.all().size() == 40, "会話の数が40ではありません：" + DialogueDefs.all().size() + " " + DialogueDefs.all().keySet());
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
        // 戦いの間は、門の通路（封印の1ブロック手前）も部屋の中に数える。技で押し出されても、戦いがはじめからにならない（11月22日）
        Vec3 sealCenter = o.add((boss.sealMin().getX() + boss.sealMax().getX() + 1) / 2.0, boss.sealMin().getY(),
                (boss.sealMin().getZ() + boss.sealMax().getZ() + 1) / 2.0);
        Vec3 center = o.add(boss.arenaCenter());
        Vec3 toCenter = new Vec3(center.x - sealCenter.x, 0.0, center.z - sealCenter.z).normalize();
        Vec3 passage = sealCenter.add(toCenter);
        check(helper, !boss.inside(passage, o) && boss.insideFight(passage, o),
                "門の通路が、戦いの間の部屋の中に数えられていません：" + passage);
        check(helper, !boss.insideFight(o.add(def.entrance().pos()), o) && !boss.insideFight(sealCenter.subtract(toCenter.scale(3.0)), o),
                "部屋の外（入口・封印の外）が、戦いの間の部屋の中に数えられています");
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
                data.getStory().wakePillar(1, i);         // 起動する前の課題（11月22日）は pillarRequirements で確かめる
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

    /**
     * 11月22日の不具合「ボス戦で倒れるとボスが増える」：保存しない敵は、書き出さないが区画を閉じる一覧には入る（区画と一緒に消える）。
     * 部屋が手放した（今出していない）ボスは、次の tick で消える。部屋のないボス（コマンドで出した）は消えない。
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void transientEnemiesAndStaleBoss(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        jp.amemaru.lostconnection.enemy.Tsunomofu mofu = jp.amemaru.lostconnection.core.registry.ModEntities.TSUNOMOFU.get().create(level);
        check(helper, mofu != null, "ツノモフを作れません");
        mofu.setTransient(true);
        check(helper, mofu.shouldBeSaved(), "保存しない敵が、区画を閉じるときの一覧から外れています（区画を閉じても消えず、また出てきます）");
        check(helper, !mofu.save(new CompoundTag()) && !mofu.saveAsPassenger(new CompoundTag()), "保存しない敵が書き出されました");
        mofu.setTransient(false);
        check(helper, mofu.save(new CompoundTag()), "ふつうの敵が書き出されません");
        FloorDef floor = FloorDefs.byNumber(1);
        check(helper, floor != null && floor.bossRoom() != null, "1階のボスの部屋の定義がありません");
        jp.amemaru.lostconnection.tower.BossRoom room = new jp.amemaru.lostconnection.tower.BossRoom(floor, floor.bossRoom());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        jp.amemaru.lostconnection.enemy.boss.Veldhorn stale = jp.amemaru.lostconnection.core.registry.ModEntities.VELDHORN.get().create(level);
        jp.amemaru.lostconnection.enemy.boss.Veldhorn free = jp.amemaru.lostconnection.core.registry.ModEntities.VELDHORN.get().create(level);
        check(helper, stale != null && free != null, "ヴェルドホーンを作れません");
        stale.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        stale.setRoom(room);
        free.moveTo(at.x + 5.0, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(stale);
        level.addFreshEntity(free);
        check(helper, !room.owns(stale), "部屋が出していないボスを、部屋のものとしています");
        helper.runAfterDelay(4, () -> {
            boolean staleGone = stale.isRemoved();
            boolean freeStays = !free.isRemoved();
            free.discard();
            stale.discard();
            check(helper, staleGone, "部屋が手放したボスが消えていません（ボスが増える不具合）");
            check(helper, freeStays, "部屋のないボスが消えました");
            helper.succeed();
        });
    }

    /**
     * 11月22日：ボス戦で倒れて教会へ戻ると、ボスのHPバーが街でも出たままになり、また挑むとバーが2本になっていた。
     * ボスが見えなくなった人からはバーを外し、区画が閉じてボスが消えたときもバーを外す。
     */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void bossBarLeavesWithViewer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        jp.amemaru.lostconnection.enemy.boss.Veldhorn boss = jp.amemaru.lostconnection.core.registry.ModEntities.VELDHORN.get().create(level);
        check(helper, boss != null, "ヴェルドホーンを作れません");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        boss.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        FakePlayer viewer = fake(helper, "bossbar");
        boss.setParticipants(List.of(viewer));
        check(helper, boss.barViewers() == 1, "戦いの参加者に、ボスバーが出ていません");
        // 倒れて教会へ戻った（ほかの世界へ移り、ボスが見えなくなった）
        boss.stopSeenByPlayer(viewer);
        check(helper, boss.barViewers() == 0, "ボスが見えなくなった人に、ボスバーが残っています（街に戻ってもバーが出たまま）");
        // 生き返った人は別のものになる：前のものが残っていても、同じ人なら外す
        FakePlayer again = new FakePlayer(level, new GameProfile(viewer.getUUID(), "amt_bossbar2"));
        check(helper, again != viewer, "生き返った人の代わりを作れません");
        boss.setParticipants(List.of(viewer));
        boss.stopSeenByPlayer(again);
        check(helper, boss.barViewers() == 0, "生き返った人の前のボスバーが残っています");
        // 区画が閉じてボスが消えた（remove は呼ばれない）
        boss.setParticipants(List.of(viewer));
        boss.onRemovedFromWorld();
        check(helper, boss.barViewers() == 0, "区画が閉じて消えたボスのバーが残っています（出し直したボスのバーと2本になる）");
        boss.discard();
        helper.succeed();
    }

    /**
     * 11月22日：街のNPCは凹凸のある立体のモデル。5人とも、モデル・動き（idle と walk）・絵（表情の差分も）がjarに入っていて、
     * モデルに頭とまぶたの骨がある（頭は見ている人を向き、まぶたでまばたきする）。
     */
    @GameTest(template = EMPTY)
    public static void npcModelsPresent(GameTestHelper helper) {
        for (jp.amemaru.lostconnection.npc.NpcDef def : NpcDefs.all().values()) {
            check(helper, def.model() != null, def.name() + " に立体のモデル（model）がありません");
            String ns = def.model().getNamespace();
            String path = def.model().getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            String geo = read("/assets/" + ns + "/geo/entity/" + path + ".geo.json");
            check(helper, geo != null && geo.contains("\"name\": \"head\"") && geo.contains("\"name\": \"eyelids\""),
                    def.name() + " のモデルがないか、頭・まぶたの骨がありません");
            String anim = read("/assets/" + ns + "/animations/entity/" + path + ".animation.json");
            check(helper, anim != null && anim.contains("animation.npc_" + name + ".idle") && anim.contains("animation.npc_" + name + ".walk"),
                    def.name() + " の動き（idle・walk）がありません");
            check(helper, read("/assets/" + def.skin().getNamespace() + "/" + def.skin().getPath()) != null, def.name() + " の絵がありません");
            for (String expression : def.expressions()) {
                ResourceLocation tex = def.skinFor(expression);
                check(helper, read("/assets/" + tex.getNamespace() + "/" + tex.getPath()) != null,
                        def.name() + " の表情「" + expression + "」の絵がありません");
            }
            check(helper, def.modelScale() > 0.5F && def.modelScale() < 1.5F && def.portraitY() > 0.8F && def.portraitY() < 1.8F,
                    def.name() + " のモデルの大きさか、会話の顔の高さがおかしいです");
        }
        helper.succeed();
    }

    @javax.annotation.Nullable
    private static String read(String resource) {
        try (java.io.InputStream in = StoryTowerGameTests.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
        }
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

    /**
     * 記憶の再生：オープニングが終わった人が「目覚め」を見返すと、そのときのリリアの会話が開く（ギルドでのあいさつにならない。
     * 11月22日）。読んだ記録がなければ1日目の会話、遅れて来た人の会話を読んでいればそれ。進めても名簿や職業の画面の処理はしない。
     */
    @GameTest(template = EMPTY)
    public static void replayOpensTheOriginalDialogue(GameTestHelper helper) {
        FakePlayer player = fake(helper, "replay_p");
        PlayerStory story = data(helper, player).getStory();
        ResourceLocation opening = AmemaruMod.id("lilia/opening");
        ResourceLocation late = AmemaruMod.id("lilia/opening_late");
        try {
            story.setOpening(PlayerStory.OPENING_DONE);
            story.markSeen(OpeningService.OPENING);
            // 今の状態で選ぶと、オープニングの会話にはならない（直す前は、これが開いていた）
            DialogueDef now = DialogueDefs.choose(player, OpeningService.LILIA);
            check(helper, now == null || !OpeningService.isOpeningDialogue(now.id()), "オープニングの後なのに、目覚めの会話が選ばれます");
            // 読んだ記録がない：1日目の会話
            check(helper, StoryEventManager.play(player, OpeningService.OPENING, true), "目覚めを見返せません");
            StoryEventManager.onSceneDone(player, StoryEventManager.sceneOf(player));
            check(helper, opening.equals(DialogueManager.dialogueOf(player)),
                    "見返した目覚めの会話が違います：" + DialogueManager.dialogueOf(player));
            DialogueManager.onChoice(player, DialogueManager.sessionOf(player), 0);
            DialogueManager.onChoice(player, DialogueManager.sessionOf(player), -1);
            check(helper, !DialogueManager.isTalking(player) && story.opening() == PlayerStory.OPENING_DONE && !story.read(opening),
                    "見返した会話で、オープニングの処理か既読の記録をしました");
            // 遅れて来た人の会話を読んでいた：それを開く
            story.markRead(late);
            StoryEventManager.play(player, OpeningService.OPENING, true);
            StoryEventManager.onSceneDone(player, StoryEventManager.sceneOf(player));
            check(helper, late.equals(DialogueManager.dialogueOf(player)),
                    "読んだ会話（遅れて来た人）を見返せません：" + DialogueManager.dialogueOf(player));
        } finally {
            int session = DialogueManager.sessionOf(player);
            if (session >= 0) {
                DialogueManager.onChoice(player, session, -2);
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 11月22日：1階の課題・仕掛け石と門・古代の宝箱

    /** 1階の仕掛け（西の丘の守り手・石柱の課題・光のかけら・東の遺跡の仕掛け石と門・古代の宝箱10）の定義がそろっている。 */
    @GameTest(template = EMPTY)
    public static void floor1Extras(GameTestHelper helper) {
        FloorDef def = FloorDefs.byNumber(1);
        check(helper, def != null && def.bossRoom() != null && def.pillars().size() == 3, "1階の定義がありません");
        FloorDef.Extras extras = def.extras();
        Vec3 o = def.originVec();
        FloorDef.Guardian guardian = extras.guardian("hill_alpha");
        check(helper, guardian != null && guardian.pillar() == 0, "西の丘の守り手がありません");
        check(helper, AmemaruMod.id("tsunomofu").equals(guardian.enemy()) && AmemaruMod.id("tsunomofu_alpha").equals(guardian.variant())
                && jp.amemaru.lostconnection.enemy.EnemyDefs.get(guardian.variant()) != null, "守り手の敵が違います");
        Vec3 guardianAt = def.world(guardian.pos());
        check(helper, def.contains(guardianAt) && !def.bossRoom().inside(guardianAt, o), "守り手の出る所が違います");
        FloorDef.Requirement hill = def.pillars().get(0).requires();
        FloorDef.Requirement woods = def.pillars().get(2).requires();
        check(helper, hill != null && FloorDef.Requirement.GUARDIAN.equals(hill.type()) && "hill_alpha".equals(hill.guardian())
                && !hill.hint().isEmpty(), "西の丘の石柱の課題が違います");
        check(helper, def.pillars().get(1).requires() == null, "東の遺跡の石柱は、課題なし（門が課題）のはずです");
        check(helper, woods != null && FloorDef.Requirement.ITEMS.equals(woods.type()) && AmemaruMod.id("light_shard").equals(woods.item())
                && woods.count() == 3 && !woods.hint().isEmpty(), "北西の林の石柱の課題が違います");
        check(helper, ForgeRegistries.ITEMS.containsKey(AmemaruMod.id("light_shard")), "光のかけらの品がありません");
        check(helper, extras.drops().size() == 1 && AmemaruMod.id("kakekiba_wolf").equals(extras.drops().get(0).enemy())
                && AmemaruMod.id("light_shard").equals(extras.drops().get(0).item()) && extras.drops().get(0).pillar() == 2
                && extras.drops().get(0).keep() == 3, "光のかけらのドロップが違います");
        FloorDef.SwitchGroup group = extras.switchGroup("ruins");
        check(helper, group != null && group.stones().size() == 2 && group.windowSeconds() >= 8 && group.openSeconds() >= 30,
                "東の遺跡の仕掛け石が違います");
        net.minecraft.core.BlockPos min = group.gateMin();
        net.minecraft.core.BlockPos max = group.gateMax();
        check(helper, (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1) >= 8, "門が小さすぎます");
        double apart = Math.sqrt(group.stones().get(0).distSqr(group.stones().get(1)));
        check(helper, apart >= 16.0 && apart <= 60.0, "2つの仕掛け石の間が違います：" + apart);
        for (net.minecraft.core.BlockPos stone : group.stones()) {
            check(helper, def.contains(Vec3.atBottomCenterOf(def.world(stone))), "仕掛け石が階の外です：" + stone);
        }
        check(helper, extras.chests().size() == 10, "古代の宝箱が10個ではありません：" + extras.chests().size());
        ResourceLocation loot = AmemaruMod.id("chests/floor1_ancient");
        check(helper, helper.getLevel().getServer().getLootData().getLootTable(loot) != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
                "宝箱の決まり（ルートテーブル）がありません");
        for (FloorDef.ChestDef chest : extras.chests()) {
            Vec3 at = Vec3.atBottomCenterOf(def.world(chest.pos()));
            check(helper, def.contains(at) && !def.bossRoom().inside(at, o), "宝箱 " + (chest.index() + 1) + " の場所が違います");
            check(helper, loot.equals(chest.lootTable()), "宝箱 " + (chest.index() + 1) + " の決まりが違います");
        }
        helper.succeed();
    }

    /**
     * 石柱の課題：西の丘は守り手を倒す前・北西の林は光のかけらを3つ持つ前は起動できない。倒した人（とパーティ）には印がつき、
     * かけらは使ってなくなる。東の遺跡は課題なし。石柱を戻すと、課題の印も消える。
     */
    @GameTest(template = EMPTY)
    public static void pillarRequirements(GameTestHelper helper) {
        FakePlayer player = fake(helper, "pillar_task");
        PlayerData data = data(helper, player);
        FloorDef def = FloorDefs.byNumber(1);
        check(helper, def != null && def.pillars().size() == 3, "1階の石柱の定義がありません");
        net.minecraft.world.item.Item shard = ForgeRegistries.ITEMS.getValue(AmemaruMod.id("light_shard"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        data.getStory().resetPillars(1);
        player.getInventory().clearContent();
        try {
            TowerService.activatePillar(player, 1, 0, def.world(def.pillars().get(0).pos()));
            check(helper, (data.getStory().pillars(1) & 1) == 0, "守り手を倒す前に、西の丘の石柱を起動できました");
            jp.amemaru.lostconnection.enemy.AmemaruMob alpha =
                    jp.amemaru.lostconnection.core.registry.ModEntities.TSUNOMOFU.get().create(helper.getLevel());
            check(helper, alpha != null, "ツノモフを作れません");
            alpha.setVariant(AmemaruMod.id("tsunomofu_alpha"));
            alpha.setGuardian(1, "hill_alpha");
            check(helper, alpha.def() != null && "群れの長".equals(alpha.def().name()) && alpha.def().behavior().getOrDefault("scale", 1.0) > 1.0,
                    "群れの長の定義が使われていません");
            TowerService.creditGuardian(alpha, List.of(player));
            alpha.discard();
            check(helper, data.getStory().pillarAwake(1, 0), "守り手を倒した印がつきません");
            TowerService.activatePillar(player, 1, 0, def.world(def.pillars().get(0).pos()));
            check(helper, (data.getStory().pillars(1) & 1) != 0, "守り手を倒した後に、西の丘の石柱を起動できません");
            TowerService.activatePillar(player, 1, 1, def.world(def.pillars().get(1).pos()));
            check(helper, (data.getStory().pillars(1) & 2) != 0, "東の遺跡の石柱（課題なし）を起動できません");
            player.getInventory().add(new net.minecraft.world.item.ItemStack(shard, 2));
            TowerService.activatePillar(player, 1, 2, def.world(def.pillars().get(2).pos()));
            check(helper, (data.getStory().pillars(1) & 4) == 0 && player.getInventory().countItem(shard) == 2,
                    "光のかけらが2つなのに、北西の林の石柱を起動したか、かけらが減りました");
            player.getInventory().add(new net.minecraft.world.item.ItemStack(shard, 2));
            TowerService.activatePillar(player, 1, 2, def.world(def.pillars().get(2).pos()));
            check(helper, (data.getStory().pillars(1) & 4) != 0 && player.getInventory().countItem(shard) == 1,
                    "光のかけら3つで起動しないか、使った数が違います：残り " + player.getInventory().countItem(shard));
            check(helper, data.getStory().pillarAwake(1, 2) && TowerService.pillarsComplete(player, 1), "3本起動したのに封印を通れません");
            data.getStory().resetPillars(1);
            check(helper, !data.getStory().pillarAwake(1, 0) && !data.getStory().pillarAwake(1, 2), "石柱を戻しても、課題の印が残っています");
        } finally {
            data.getStory().resetPillars(1);
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    /** 仕掛け石：決まった秒数のうちにすべて押すと門がひらき、決まった秒数で閉じる。押す間があきすぎると、ひらかない。 */
    @GameTest(template = EMPTY)
    public static void switchesOpenGate(GameTestHelper helper) {
        check(helper, jp.amemaru.lostconnection.tower.FloorGates.litCount(new long[]{0, 0}, 100, 12) == 0, "押していない石を数えました");
        check(helper, jp.amemaru.lostconnection.tower.FloorGates.litCount(new long[]{100, 0}, 300, 12) == 1, "押した石の数え方が違います");
        check(helper, jp.amemaru.lostconnection.tower.FloorGates.litCount(new long[]{100, 300}, 300, 12) == 2, "秒数のうちの2つを数えません");
        check(helper, jp.amemaru.lostconnection.tower.FloorGates.litCount(new long[]{100, 400}, 400, 12) == 1, "秒数の過ぎた石を数えました");
        FloorDef def = FloorDefs.byNumber(1);
        FloorDef.SwitchGroup group = def == null ? null : def.extras().switchGroup("ruins");
        check(helper, group != null, "東の遺跡の仕掛け石の定義がありません");
        jp.amemaru.lostconnection.tower.FloorGates gates = new jp.amemaru.lostconnection.tower.FloorGates(def);
        FakePlayer player = fake(helper, "switch");
        ServerLevel level = helper.getLevel();
        long t = 1000;
        gates.press(level, player, "ruins", 0, t);
        check(helper, !gates.isOpen("ruins", t), "1つ押しただけで門がひらきました");
        long late = t + 20L * (group.windowSeconds() + 2);
        gates.press(level, player, "ruins", 1, late);
        check(helper, !gates.isOpen("ruins", late), "押す間があきすぎたのに、門がひらきました");
        long t2 = late + 20;
        gates.press(level, player, "ruins", 0, t2);
        check(helper, gates.isOpen("ruins", t2) && gates.isOpen("ruins", t2 + 20L * group.openSeconds() - 1),
                "決まった秒数のうちに2つ押したのに、門がひらきません");
        check(helper, !gates.isOpen("ruins", t2 + 20L * group.openSeconds() + 1), "門が決まった秒数で閉じません");
        helper.succeed();
    }

    /** 古代の宝箱：人それぞれ1度だけ開けられる（2度目は何も出ない）。ほかの人は同じ宝箱を開けられる。数は保存して読み直しても同じ。 */
    @GameTest(template = EMPTY)
    public static void chestsPerPlayer(GameTestHelper helper) {
        FakePlayer a = fake(helper, "chest_a");
        FakePlayer b = fake(helper, "chest_b");
        PlayerData da = data(helper, a);
        PlayerData db = data(helper, b);
        da.getStory().resetChests(1);
        db.getStory().resetChests(1);
        net.minecraft.core.BlockPos at = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(at).inflate(3.0);
        ServerLevel level = helper.getLevel();
        try {
            int before = level.getEntitiesOfClass(jp.amemaru.lostconnection.loot.LootItemEntity.class, around).size();
            TowerService.openChest(a, 1, 0, at);
            int after = level.getEntitiesOfClass(jp.amemaru.lostconnection.loot.LootItemEntity.class, around).size();
            check(helper, da.getStory().chestOpened(1, 0) && da.getStory().chestCount(1) == 1, "宝箱を開けた記録がつきません");
            check(helper, after > before, "宝箱の中身が出ません");
            TowerService.openChest(a, 1, 0, at);
            int again = level.getEntitiesOfClass(jp.amemaru.lostconnection.loot.LootItemEntity.class, around).size();
            check(helper, again == after && da.getStory().chestCount(1) == 1, "同じ人が同じ宝箱を2度開けられました");
            TowerService.openChest(b, 1, 0, at);
            check(helper, db.getStory().chestOpened(1, 0) && !db.getStory().chestOpened(1, 1), "ほかの人が同じ宝箱を開けられません");
            PlayerStory loaded = new PlayerStory(() -> { });
            loaded.load(da.getStory().save());
            check(helper, loaded.chestOpened(1, 0) && loaded.chestCount(1) == 1 && !loaded.chestOpened(1, 1), "読み直した宝箱の記録が違います");
        } finally {
            da.getStory().resetChests(1);
            db.getStory().resetChests(1);
            for (jp.amemaru.lostconnection.loot.LootItemEntity item : level.getEntitiesOfClass(jp.amemaru.lostconnection.loot.LootItemEntity.class, around)) {
                item.discard();
            }
        }
        helper.succeed();
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
