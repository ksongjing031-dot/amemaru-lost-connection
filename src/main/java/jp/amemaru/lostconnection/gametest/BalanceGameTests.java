package jp.amemaru.lostconnection.gametest;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.enemy.boss.SharedBlow;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 11月24日のご指示（バランスの調節）の自動テスト。
 *
 * <ul>
 *   <li>雑魚・名のある魔物・ボスのHPと攻撃が、決めた下限より上（「プレイヤーが一方的」にならない）。葉影ギツネは遅くした</li>
 *   <li>ボスのHPは、ひとりでも2人ぶん（hp_min_players）。みんなで受ける一撃は、ひとりで受けると倒れる強さ</li>
 *   <li>防具と盾の防御とHPを下げた（「装備が強くて全然削れない」）</li>
 * </ul>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BalanceGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-9;

    private BalanceGameTests() {
    }

    /** 雑魚と名のある魔物の強さの下限（2人で1体に十数秒・名のある魔物は1〜2分）と、葉影ギツネの速さ。 */
    @GameTest(template = EMPTY)
    public static void enemiesAreNotOneSided(GameTestHelper helper) {
        for (String id : List.of("tsunomofu", "crystal_bee", "ishikure_soldier", "kakekiba_wolf")) {
            EnemyDef def = enemy(id);
            check(helper, def.hp() >= 400 && def.attack() >= 25, "1階の雑魚 " + id + " が弱すぎます：HP" + def.hp() + "・攻撃" + def.attack());
        }
        for (String id : List.of("mayoi_bi", "juhihei", "pokodake", "hakage_kitsune")) {
            EnemyDef def = enemy(id);
            check(helper, def.hp() >= 700 && def.attack() >= 30, "2階の雑魚 " + id + " が弱すぎます：HP" + def.hp() + "・攻撃" + def.attack());
        }
        for (String id : List.of("tsunomofu_alpha", "juhihei_captain", "hakage_kitsune_chief")) {
            EnemyDef def = enemy(id);
            check(helper, def.hp() >= 7000, "名のある魔物 " + id + " のHPが少なすぎます：" + def.hp());
        }
        check(helper, enemy("hakage_kitsune").moveSpeed() <= 0.30 + EPSILON && enemy("hakage_kitsune_chief").moveSpeed() <= 0.33 + EPSILON,
                "葉影ギツネが遅くなっていません");
        check(helper, enemy("hakage_kitsune").behavior("flank_speed", 9.0) <= 1.3 + EPSILON, "葉影ギツネの回り込みが遅くなっていません");
        helper.succeed();
    }

    /** ボス：ひとりでも2人ぶんのHP・みんなで受ける一撃（ひとりで受けると倒れる）・怒るまでの時間。 */
    @GameTest(template = EMPTY)
    public static void bossesNeedAParty(GameTestHelper helper) {
        for (int floor = 1; floor <= 2; floor++) {
            FloorDef def = FloorDefs.byNumber(floor);
            check(helper, def != null && def.bossRoom() != null, floor + "階のボスの部屋がありません");
            FloorDef.BossRoomDef room = def.bossRoom();
            FloorDef.Scaling scaling = room.scaling();
            double solo = scaling.hpMultiplier(room.hpPerExtraPlayer(), 1);
            double two = scaling.hpMultiplier(room.hpPerExtraPlayer(), 2);
            double four = scaling.hpMultiplier(room.hpPerExtraPlayer(), 4);
            check(helper, Math.abs(solo - two) < EPSILON && solo > 1.0, floor + "階のボスが、ひとりのとき2人ぶんのHPになっていません：" + solo);
            check(helper, four > two, floor + "階のボスのHPが、人数で増えません");
            check(helper, scaling.enrageSeconds() >= 900, floor + "階のボスが、すぐに怒ります：" + scaling.enrageSeconds() + "秒");
            EnemyDef boss = EnemyDefs.get(room.boss());
            check(helper, boss != null, floor + "階のボスの定義がありません");
            EnemyDef.Skill share = boss.skills().get("share");
            check(helper, share != null, floor + "階のボスに、みんなで受ける一撃がありません");
            check(helper, SharedBlow.split(share.power(), 1) >= 1.0, floor + "階のみんなで受ける一撃が、ひとりで受けても倒れない強さです");
            check(helper, SharedBlow.split(share.power(), 2) < 1.0, floor + "階のみんなで受ける一撃が、2人で分けても倒れる強さです");
            check(helper, share.windupTicks() >= 60, floor + "階のみんなで受ける一撃の予兆が短すぎます（集まる間がない）");
            check(helper, boss.hp() * two >= (floor == 1 ? 40000 : 85000), floor + "階のボスのHP（2人）が少なすぎます：" + boss.hp() * two);
        }
        check(helper, Math.abs(SharedBlow.split(160, 4) - 0.4) < EPSILON, "4人で分けると、ひとり最大HPの40%ではありません");
        check(helper, SharedBlow.split(160, 0) == 1.6, "円に誰もいないとき、狙われた人がひとりで受けていません");
        helper.succeed();
    }

    /** 防具と盾：防御とHPを下げた（古代石の重鎧と盾の合計）。 */
    @GameTest(template = EMPTY)
    public static void armorIsToneDown(GameTestHelper helper) {
        double defense = 0.0;
        double hp = 0.0;
        for (String id : List.of("ancient_stone_helm", "ancient_stone_cuirass", "ancient_stone_greaves", "ancient_stone_sabatons",
                "ancient_stone_shield")) {
            EquipmentDef def = EquipmentDefs.server().get(AmemaruMod.id(id));
            check(helper, def != null, id + " の定義がありません");
            defense += def.stats().getOrDefault(StatType.DEFENSE, 0.0);
            hp += def.stats().getOrDefault(StatType.HP, 0.0);
        }
        check(helper, defense <= 20.0 && hp <= 90.0, "古代石の重鎧と盾が強すぎます：防御" + defense + "・HP" + hp);
        helper.succeed();
    }

    private static EnemyDef enemy(String id) {
        EnemyDef def = EnemyDefs.get(AmemaruMod.id(id));
        if (def == null) {
            throw new GameTestAssertException("敵の定義がありません：" + id);
        }
        return def;
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}
