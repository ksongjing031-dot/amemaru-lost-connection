package jp.amemaru.lostconnection.gametest;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.job.def.SkillTreeDef;
import jp.amemaru.lostconnection.player.GrowthRules;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.player.VitalsService;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * プレイヤーデータの自動テスト（GameTest）。GitHub Actions で {@code gradlew runGameTestServer} として毎回動かす。
 *
 * <p>テストのプレイヤーには、Forgeの「偽のプレイヤー」（{@link FakePlayer}）を使う。テストごとに別の名前にして、
 * 同時に動くテストどうしが混ざらないようにする。</p>
 *
 * <p>数値は同梱の {@code growth/default.json} の値が前提。遊ぶサーバーではテストは動かない
 * （{@code forge.enabledGameTestNamespaces} を付けて起動したときだけ動く）。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerDataGameTests {
    /** 中身が空の 3×3×3 の構造物（{@code data/amemaru/structures/empty3x3x3.nbt}）。 */
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;

    private PlayerDataGameTests() {
    }

    /**
     * /kill などで死んで復活しても、レベル・スキルツリーで取った節が残り、HPとMPが全快になる。
     *
     * <p>Forgeのリスポーンと同じ順番で動かす：古い体が消える（Capabilityが無効になる）→
     * {@link PlayerEvent.Clone} → {@link PlayerEvent.PlayerRespawnEvent}。Phase 1で見つかった不具合
     * （古い体のデータを読めず、新しい体が初期値になる）を確かめるテスト。11月24日からは、振り分けの代わりにスキルツリーの節。</p>
     */
    @GameTest(template = EMPTY)
    public static void dataSurvivesRespawn(GameTestHelper helper) {
        FakePlayer oldBody = fake(helper, "respawn_old");
        PlayerData data = data(helper, oldBody);
        LevelService.setLevel(oldBody, data, 10);
        JobService.setJobForced(oldBody, data, knight(helper));
        SkillTreeDef tree = TreeTestUtil.tree(AmemaruMod.id("knight"));
        String node = TreeTestUtil.statNextToCore(tree);
        double hpBefore = data.getStats().maxHp();
        TreeTestUtil.take(helper, oldBody, data, tree, node);
        double hpWithNode = data.getStats().maxHp();
        helper.assertTrue(hpWithNode > hpBefore, "体力の節を取っても最大HPが増えません：" + hpBefore + " → " + hpWithNode);
        data.setHp(10);

        FakePlayer newBody = fake(helper, "respawn_new");
        oldBody.invalidateCaps();
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(newBody, oldBody, true));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(newBody, false));

        PlayerData after = data(helper, newBody);
        helper.assertTrue(after.getLevel() == 10, "復活後のレベルが10ではありません：" + after.getLevel());
        helper.assertTrue(after.hasTreeNode(node), "復活後に、スキルツリーで取った節が消えました：" + after.getTreeNodes());
        helper.assertTrue(Math.abs(after.getStats().maxHp() - hpWithNode) < EPSILON,
                "復活後の最大HPが " + hpWithNode + " ではありません：" + after.getStats().maxHp());
        helper.assertTrue(Math.abs(after.getHp() - after.getStats().maxHp()) < EPSILON,
                "復活後のHPが全快ではありません：" + after.getHp());
        helper.succeed();
    }

    /** 保存して読み直しても、レベル・経験値・スキルツリーで取った節・HPとMPが同じ。 */
    @GameTest(template = EMPTY)
    public static void saveAndLoadKeepsData(GameTestHelper helper) {
        ServerPlayer player = fake(helper, "saveAndLoadKeepsData");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 7);
        LevelService.addExp(player, data, 42, LevelService.ExpSource.ADMIN);
        data.addTreeNode("b39");
        data.addTreeNode("n:guardian:1");
        data.setHp(100);
        data.setMp(50);

        CompoundTag saved = data.save();
        PlayerData loaded = new PlayerData();
        loaded.load(saved);
        helper.assertTrue(loaded.getLevel() == 7, "読み直したレベルが7ではありません：" + loaded.getLevel());
        helper.assertTrue(loaded.getExp() == 42, "読み直した経験値が42ではありません：" + loaded.getExp());
        helper.assertTrue(loaded.getTreeNodes().equals(data.getTreeNodes()), "読み直したスキルツリーの節が違います：" + loaded.getTreeNodes());
        helper.assertTrue(loaded.getTreeVersion() == PlayerData.TREE_VERSION,
                "読み直したスキルツリーの版が " + PlayerData.TREE_VERSION + " ではありません：" + loaded.getTreeVersion());
        helper.assertTrue(Math.abs(loaded.getHp() - 100) < EPSILON, "読み直したHPが100ではありません：" + loaded.getHp());
        helper.assertTrue(Math.abs(loaded.getMp() - 50) < EPSILON, "読み直したMPが50ではありません：" + loaded.getMp());
        helper.succeed();
    }

    /**
     * レベル10のステータスが、同梱の成長の定義どおりになる（11月24日：レベルで上がるのは HP18・MP4・攻撃0.9・魔力0.9・防御0.45。
     * ステータスポイントはなくなり、ツリーのポイントは1レベルごとに2・5の倍数のレベルでさらに1）。
     */
    @GameTest(template = EMPTY)
    public static void statsAtLevelTen(GameTestHelper helper) {
        ServerPlayer player = fake(helper, "statsAtLevelTen");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        expectStat(helper, data, StatType.HP, 362);
        expectStat(helper, data, StatType.MP, 136);
        expectStat(helper, data, StatType.ATTACK, 18);
        expectStat(helper, data, StatType.MAGIC, 18);
        expectStat(helper, data, StatType.DEFENSE, 9);
        expectStat(helper, data, StatType.SPEED, 10);
        expectStat(helper, data, StatType.CRIT, 5);
        expectStat(helper, data, StatType.CRIT_DAMAGE, 50);
        int statPoints = LevelService.availableStatPoints(data, GrowthRules.current());
        int skillPoints = LevelService.availableSkillPoints(data, GrowthRules.current());
        helper.assertTrue(statPoints == 0, "ステータスポイントが0ではありません：" + statPoints);
        helper.assertTrue(skillPoints == 20, "ツリーのポイントが20ではありません：" + skillPoints);
        helper.succeed();
    }

    /** スキルツリーの体力の節を取ると、最大HPと今のHPが同じだけ増える（11月24日。前は振り分け）。 */
    @GameTest(template = EMPTY)
    public static void treeNodeRaisesHp(GameTestHelper helper) {
        ServerPlayer player = fake(helper, "treeNodeRaisesHp");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        JobService.setJobForced(player, data, knight(helper));
        // 見習い装備は持たない（何も持たないナイトで数える）
        player.getInventory().clearContent();
        LevelService.recompute(player, data);
        SkillTreeDef tree = TreeTestUtil.tree(AmemaruMod.id("knight"));
        List<String> hpNodes = new ArrayList<>();
        for (String id : tree.neighbors(tree.core().id())) {
            SkillTreeDef.Node node = tree.node(id);
            if (node != null && node.isStatNode() && node.flat().containsKey(StatType.HP)) {
                hpNodes.add(id);
            }
        }
        helper.assertTrue(hpNodes.size() >= 2, "ナイトの核のとなりに、体力の節が2つありません：" + hpNodes);
        // 何も持たないナイト（HP+20%）：434 → 体力+20の節で 458
        expectStat(helper, data, StatType.HP, 434);
        TreeTestUtil.take(helper, player, data, tree, hpNodes.get(0));
        expectStat(helper, data, StatType.HP, 458);
        helper.assertTrue(Math.abs(data.getHp() - 458) < EPSILON, "今のHPが458ではありません：" + data.getHp());

        data.setHp(300);
        TreeTestUtil.take(helper, player, data, tree, hpNodes.get(1));
        expectStat(helper, data, StatType.HP, 482);
        helper.assertTrue(Math.abs(data.getHp() - 324) < EPSILON, "今のHPが324ではありません：" + data.getHp());
        helper.succeed();
    }

    /** バニラの体力が減った・増えた割合が、独自のHPに反映される。 */
    @GameTest(template = EMPTY)
    public static void vanillaHealthIsConvertedByRatio(GameTestHelper helper) {
        ServerPlayer player = fake(helper, "vanillaHealthIsConvertedByRatio");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        helper.assertTrue(Math.abs(player.getHealth() - player.getMaxHealth()) < 1.0E-3F,
                "バニラの体力が満タンではありません：" + player.getHealth());

        // バニラの体力が2割減る（落下などと同じ）
        player.setHealth(player.getMaxHealth() * 0.8F);
        VitalsService.tick(player, data);
        helper.assertTrue(Math.abs(data.getHp() - 289.6) < 0.5, "2割減った後のHPが289.6ではありません：" + data.getHp());

        // バニラの体力が1割戻る（回復薬などと同じ）
        player.setHealth(player.getMaxHealth() * 0.9F);
        VitalsService.tick(player, data);
        helper.assertTrue(Math.abs(data.getHp() - 325.8) < 0.5, "1割戻った後のHPが325.8ではありません：" + data.getHp());
        helper.succeed();
    }

    /**
     * 11月27日のバグ「防具をつけていると、落下以外のダメージが通らない」：敵の攻撃で防具の耐久が減ると、持ち物が変わった扱いで
     * ステータスの計算し直しが走る。その前にバニラの体力が減っていても、そのダメージが独自のHPに写る（消えない）。
     */
    @GameTest(template = EMPTY)
    public static void damageSurvivesRecompute(GameTestHelper helper) {
        ServerPlayer player = fake(helper, "damageSurvivesRecompute");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        VitalsService.tick(player, data);
        double max = data.getStats().maxHp();
        helper.assertTrue(Math.abs(data.getHp() - max) < 0.5, "HPが満タンではありません：" + data.getHp());

        // 敵の攻撃：バニラの体力が3割減り、同じ tick に防具の耐久が減って、計算し直しの印がつく
        player.setHealth(player.getMaxHealth() * 0.7F);
        data.invalidateStats();
        VitalsService.tick(player, data);
        helper.assertTrue(data.isStatsReady(), "計算し直されていません");
        helper.assertTrue(Math.abs(data.getHp() - max * 0.7) < 0.5,
                "計算し直しで、受けたダメージが消えました：" + max + " → " + data.getHp() + "（" + (max * 0.7) + " のはず）");
        helper.assertTrue(Math.abs(player.getHealth() - player.getMaxHealth() * 0.7F) < 0.05F,
                "バニラの体力が、減った分に合っていません：" + player.getHealth());

        // スキルを使うときの計算し直し（LevelService.recompute を直接）でも消えない
        player.setHealth(player.getMaxHealth() * 0.5F);
        LevelService.recompute(player, data);
        helper.assertTrue(Math.abs(data.getHp() - max * 0.5) < 0.5, "計算し直しで、受けたダメージが消えました（2回目）：" + data.getHp());

        // 受けた直後（写す前）に回復が入っても、ダメージは消えない（回復は、まだ減っていないHPに足される）
        player.setHealth(player.getMaxHealth() * 0.4F);
        data.setHp(data.getHp() + max * 0.05);
        VitalsService.pushVanillaHealth(player, data);
        helper.assertTrue(Math.abs(data.getHp() - max * 0.45) < 0.5,
                "回復と同じ tick のダメージが消えました：" + data.getHp() + "（" + (max * 0.45) + " のはず）");
        helper.succeed();
    }

    /** 経験値でレベルが上がり、最大レベルでは経験値がたまらない。 */
    @GameTest(template = EMPTY)
    public static void expRaisesLevel(GameTestHelper helper) {
        ServerPlayer player = fake(helper, "expRaisesLevel");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        // 11月24日：次のレベルまでの経験値は 55×1.038^レベル（レベル10→11で80、19→20で112）
        int gained = LevelService.addExp(player, data, 1000, LevelService.ExpSource.ADMIN);
        helper.assertTrue(gained == 10, "上がったレベルが10ではありません：" + gained);
        helper.assertTrue(data.getLevel() == 20 && data.getExp() == 49,
                "レベル20・経験値49になっていません：レベル" + data.getLevel() + "・経験値" + data.getExp());

        int max = GrowthRules.current().maxLevel();
        helper.assertTrue(max == 100, "最大レベルが100ではありません：" + max);
        LevelService.setLevel(player, data, max);
        LevelService.addExp(player, data, 5000, LevelService.ExpSource.ADMIN);
        helper.assertTrue(data.getLevel() == max && data.getExp() == 0,
                "最大レベルで経験値がたまっています：経験値" + data.getExp());
        helper.succeed();
    }

    // ---------------------------------------------------------------- 共通

    /** テストごとに別の名前の偽のプレイヤーを作る（同じ名前だと同じプレイヤーが返るため）。 */
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

    private static JobDef knight(GameTestHelper helper) {
        JobDef job = SkillRegistry.get().job(AmemaruMod.id("knight"));
        helper.assertTrue(job != null, "ナイトの職業がありません");
        return job;
    }

    private static void expectStat(GameTestHelper helper, PlayerData data, StatType type, double expected) {
        double actual = data.getStats().get(type);
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-6,
                type.id() + " が " + expected + " ではありません：" + actual);
    }
}
