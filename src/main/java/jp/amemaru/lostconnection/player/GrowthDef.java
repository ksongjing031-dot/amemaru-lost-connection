package jp.amemaru.lostconnection.player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * 成長の定義（{@code data/amemaru/amemaru/growth/default.json}）。
 *
 * <p>レベルの上限、次のレベルまでの経験値の式、レベルごとのポイント、ステータスの基礎値・レベルごとの伸び・
 * 1ポイントあたりの伸びを決める。Phase 2で職業を足すと、職業の定義が基礎値と伸びを上書きする。</p>
 *
 * <p>次のレベルまでの経験値は「{@code exp.base} × {@code exp.growth} の（今のレベル）乗」（Phase 0-A 19章）。</p>
 */
public final class GrowthDef {
    /** 使う定義のID。 */
    public static final ResourceLocation DEFAULT_ID = AmemaruMod.id("default");

    /** 定義が見つからないときに使う値（同梱の default.json と同じ数値）。 */
    public static final GrowthDef FALLBACK = createFallback();

    private final int maxLevel;
    private final double expBase;
    private final double expGrowth;
    private final int statPointsPerLevel;
    private final int skillPointsPerLevel;
    private final int skillBonusEvery;
    private final int skillBonus;
    private final double[] base;
    private final double[] perLevel;
    private final double[] perPoint;

    private GrowthDef(int maxLevel, double expBase, double expGrowth,
                      int statPointsPerLevel, int skillPointsPerLevel, int skillBonusEvery, int skillBonus,
                      double[] base, double[] perLevel, double[] perPoint) {
        this.maxLevel = maxLevel;
        this.expBase = expBase;
        this.expGrowth = expGrowth;
        this.statPointsPerLevel = statPointsPerLevel;
        this.skillPointsPerLevel = skillPointsPerLevel;
        this.skillBonusEvery = skillBonusEvery;
        this.skillBonus = skillBonus;
        this.base = base;
        this.perLevel = perLevel;
        this.perPoint = perPoint;
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** 今のレベルから次のレベルまでに要る経験値。最大レベルでは 0。 */
    public int expToNext(int level) {
        if (level >= maxLevel) {
            return 0;
        }
        double need = expBase * Math.pow(expGrowth, level);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, Math.round(need)));
    }

    /** そのレベルまでにもらえるステータスポイントの合計。 */
    public int totalStatPoints(int level) {
        return statPointsPerLevel * Math.max(0, level - 1);
    }

    /** そのレベルまでにもらえるスキルポイントの合計（{@code skill_bonus_every} の倍数のレベルでは追加あり）。 */
    public int totalSkillPoints(int level) {
        int points = skillPointsPerLevel * Math.max(0, level - 1);
        if (skillBonusEvery > 0) {
            points += skillBonus * (level / skillBonusEvery);
        }
        return points;
    }

    /** レベル1のときの値。 */
    public double base(StatType type) {
        return base[type.ordinal()];
    }

    /** レベルが1上がるごとの伸び。 */
    public double perLevel(StatType type) {
        return perLevel[type.ordinal()];
    }

    /** ステータスポイント1つあたりの伸び。 */
    public double perPoint(StatType type) {
        return perPoint[type.ordinal()];
    }

    // ---------------------------------------------------------------- 読み込み

    /** JSONから読む。問題があれば {@link DefinitionException}。 */
    public static GrowthDef parse(JsonObject json) {
        int maxLevel = Json.intInRange(json, "max_level", 1, 200);

        JsonObject exp = Json.object(json, "exp");
        double expBase = Json.doubleInRange(exp, "base", 1.0, 1.0e6);
        double expGrowth = Json.doubleInRange(exp, "growth", 1.0, 2.0);

        JsonObject points = Json.object(json, "points");
        int statPerLevel = Json.intInRange(points, "stat_per_level", 0, 100);
        int skillPerLevel = Json.intInRange(points, "skill_per_level", 0, 100);
        int bonusEvery = Json.intInRange(points, "skill_bonus_every", 0, 1000);
        int bonus = Json.intInRange(points, "skill_bonus", 0, 100);

        double[] base = readStats(Json.object(json, "base"), "base", false);
        double[] perLevel = readStats(Json.optionalObject(json, "per_level"), "per_level", false);
        double[] perPoint = readStats(Json.optionalObject(json, "per_point"), "per_point", true);

        if (base[StatType.HP.ordinal()] < 1) {
            throw new DefinitionException("「base」の「hp」は1以上にしてください");
        }
        return new GrowthDef(maxLevel, expBase, expGrowth, statPerLevel, skillPerLevel, bonusEvery, bonus,
                base, perLevel, perPoint);
    }

    private static double[] readStats(@Nullable JsonObject object, String where, boolean allocatableOnly) {
        double[] values = new double[StatType.COUNT];
        if (object == null) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            StatType type = StatType.byId(key).orElseThrow(() ->
                    new DefinitionException("「" + where + "」に、ない種類のステータス「" + key + "」があります"));
            if (allocatableOnly && !type.isAllocatable()) {
                throw new DefinitionException("「" + where + "」の「" + key + "」は振り分けられないステータスです");
            }
            double value = Json.asNumber(entry.getValue(), where + "." + key);
            if (value < 0 || value > 1.0e6) {
                throw new DefinitionException("「" + where + "." + key + "」は 0〜1000000 の範囲にしてください");
            }
            values[type.ordinal()] = value;
        }
        return values;
    }

    private static GrowthDef createFallback() {
        double[] base = new double[StatType.COUNT];
        base[StatType.HP.ordinal()] = 200;
        base[StatType.MP.ordinal()] = 100;
        base[StatType.ATTACK.ordinal()] = 10;
        base[StatType.MAGIC.ordinal()] = 10;
        base[StatType.DEFENSE.ordinal()] = 5;
        base[StatType.SPEED.ordinal()] = 10;
        base[StatType.CRIT.ordinal()] = 5;
        base[StatType.CRIT_DAMAGE.ordinal()] = 50;

        double[] perLevel = new double[StatType.COUNT];
        perLevel[StatType.HP.ordinal()] = 40;
        perLevel[StatType.MP.ordinal()] = 10;
        perLevel[StatType.ATTACK.ordinal()] = 2;
        perLevel[StatType.MAGIC.ordinal()] = 2;
        perLevel[StatType.DEFENSE.ordinal()] = 1;

        double[] perPoint = new double[StatType.COUNT];
        perPoint[StatType.HP.ordinal()] = 12;
        perPoint[StatType.MP.ordinal()] = 5;
        perPoint[StatType.ATTACK.ordinal()] = 1;
        perPoint[StatType.MAGIC.ordinal()] = 1;
        perPoint[StatType.DEFENSE.ordinal()] = 1;
        perPoint[StatType.SPEED.ordinal()] = 1;
        perPoint[StatType.CRIT.ordinal()] = 0.3;

        return new GrowthDef(50, 120, 1.09, 3, 1, 5, 1, base, perLevel, perPoint);
    }
}
