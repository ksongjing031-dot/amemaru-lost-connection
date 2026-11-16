package jp.amemaru.lostconnection.gametest;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.player.GrowthRules;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.player.VitalsService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * プレイヤーデータの自動テスト（GameTest）。GitHub Actions で {@code gradlew runGameTestServer} として毎回動かす。
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

    /** /kill などで死んで復活しても、レベル・振り分けが残り、HPとMPが全快になる。 */
    @GameTest(template = EMPTY)
    public static void dataSurvivesRespawn(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        int used = LevelService.allocate(player, data, StatType.ATTACK, 5);
        helper.assertTrue(used == 5, "振り分けた数が5ではありません：" + used);
        data.setHp(10);

        ServerPlayer respawned = helper.getLevel().getServer().getPlayerList().respawn(player, false);
        PlayerData after = data(helper, respawned);
        helper.assertTrue(after.getLevel() == 10, "復活後のレベルが10ではありません：" + after.getLevel());
        helper.assertTrue(after.getAllocated(StatType.ATTACK) == 5,
                "復活後の攻撃の振り分けが5ではありません：" + after.getAllocated(StatType.ATTACK));
        helper.assertTrue(after.getStats().get(StatType.ATTACK) == 33,
                "復活後の攻撃が33ではありません：" + after.getStats().get(StatType.ATTACK));
        helper.assertTrue(Math.abs(after.getHp() - after.getStats().maxHp()) < EPSILON,
                "復活後のHPが全快ではありません：" + after.getHp());
        remove(helper, respawned);
        helper.succeed();
    }

    /** 保存して読み直しても、レベル・経験値・振り分け・HPとMPが同じ。 */
    @GameTest(template = EMPTY)
    public static void saveAndLoadKeepsData(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 7);
        LevelService.addExp(player, data, 42, LevelService.ExpSource.ADMIN);
        LevelService.allocate(player, data, StatType.HP, 3);
        data.setHp(100);
        data.setMp(50);

        CompoundTag saved = data.save();
        PlayerData loaded = new PlayerData();
        loaded.load(saved);
        helper.assertTrue(loaded.getLevel() == 7, "読み直したレベルが7ではありません：" + loaded.getLevel());
        helper.assertTrue(loaded.getExp() == 42, "読み直した経験値が42ではありません：" + loaded.getExp());
        helper.assertTrue(loaded.getAllocated(StatType.HP) == 3, "読み直したHPの振り分けが3ではありません");
        helper.assertTrue(Math.abs(loaded.getHp() - 100) < EPSILON, "読み直したHPが100ではありません：" + loaded.getHp());
        helper.assertTrue(Math.abs(loaded.getMp() - 50) < EPSILON, "読み直したMPが50ではありません：" + loaded.getMp());
        remove(helper, player);
        helper.succeed();
    }

    /** レベル10のステータスが、同梱の成長の定義どおりになる。 */
    @GameTest(template = EMPTY)
    public static void statsAtLevelTen(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        expectStat(helper, data, StatType.HP, 560);
        expectStat(helper, data, StatType.MP, 190);
        expectStat(helper, data, StatType.ATTACK, 28);
        expectStat(helper, data, StatType.MAGIC, 28);
        expectStat(helper, data, StatType.DEFENSE, 14);
        expectStat(helper, data, StatType.SPEED, 10);
        expectStat(helper, data, StatType.CRIT, 5);
        expectStat(helper, data, StatType.CRIT_DAMAGE, 50);
        int statPoints = LevelService.availableStatPoints(data, GrowthRules.current());
        int skillPoints = LevelService.availableSkillPoints(data, GrowthRules.current());
        helper.assertTrue(statPoints == 27, "ステータスポイントが27ではありません：" + statPoints);
        helper.assertTrue(skillPoints == 11, "スキルポイントが11ではありません：" + skillPoints);
        remove(helper, player);
        helper.succeed();
    }

    /** HPに振り分けると、最大HPと今のHPが同じだけ増える。 */
    @GameTest(template = EMPTY)
    public static void allocationRaisesHp(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        LevelService.allocate(player, data, StatType.HP, 1);
        expectStat(helper, data, StatType.HP, 572);
        helper.assertTrue(Math.abs(data.getHp() - 572) < EPSILON, "今のHPが572ではありません：" + data.getHp());

        data.setHp(300);
        LevelService.allocate(player, data, StatType.HP, 1);
        expectStat(helper, data, StatType.HP, 584);
        helper.assertTrue(Math.abs(data.getHp() - 312) < EPSILON, "今のHPが312ではありません：" + data.getHp());
        remove(helper, player);
        helper.succeed();
    }

    /** バニラの体力が減った・増えた割合が、独自のHPに反映される。 */
    @GameTest(template = EMPTY)
    public static void vanillaHealthIsConvertedByRatio(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        helper.assertTrue(Math.abs(player.getHealth() - player.getMaxHealth()) < 1.0E-3F,
                "バニラの体力が満タンではありません：" + player.getHealth());

        // バニラの体力が2割減る（落下などと同じ）
        player.setHealth(player.getMaxHealth() * 0.8F);
        VitalsService.tick(player, data);
        helper.assertTrue(Math.abs(data.getHp() - 448) < 0.5, "2割減った後のHPが448ではありません：" + data.getHp());

        // バニラの体力が1割戻る（回復薬などと同じ）
        player.setHealth(player.getMaxHealth() * 0.9F);
        VitalsService.tick(player, data);
        helper.assertTrue(Math.abs(data.getHp() - 504) < 0.5, "1割戻った後のHPが504ではありません：" + data.getHp());
        remove(helper, player);
        helper.succeed();
    }

    /** 経験値でレベルが上がり、最大レベルでは経験値がたまらない。 */
    @GameTest(template = EMPTY)
    public static void expRaisesLevel(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        int gained = LevelService.addExp(player, data, 1000, LevelService.ExpSource.ADMIN);
        helper.assertTrue(gained == 3, "上がったレベルが3ではありません：" + gained);
        helper.assertTrue(data.getLevel() == 13 && data.getExp() == 68,
                "レベル13・経験値68になっていません：レベル" + data.getLevel() + "・経験値" + data.getExp());

        LevelService.setLevel(player, data, 50);
        LevelService.addExp(player, data, 5000, LevelService.ExpSource.ADMIN);
        helper.assertTrue(data.getLevel() == 50 && data.getExp() == 0,
                "最大レベルで経験値がたまっています：経験値" + data.getExp());
        remove(helper, player);
        helper.succeed();
    }

    // ---------------------------------------------------------------- 共通

    private static PlayerData data(GameTestHelper helper, ServerPlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> {
            helper.fail("プレイヤーデータが付いていません");
            return new IllegalStateException("no player data");
        });
    }

    private static void expectStat(GameTestHelper helper, PlayerData data, StatType type, double expected) {
        double actual = data.getStats().get(type);
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-6,
                type.id() + " が " + expected + " ではありません：" + actual);
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }
}
