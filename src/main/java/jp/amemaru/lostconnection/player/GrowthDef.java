package jp.amemaru.lostconnection.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 成長の定義（{@code data/amemaru/amemaru/growth/default.json}）。
 *
 * <p>レベルの上限、次のレベルまでの経験値の式、レベルごとのポイント、ステータスの基礎値・レベルごとの伸び・
 * 1ポイントあたりの伸びを決める。Phase 2で職業を足すと、職業の定義が基礎値と伸びを上書きする。</p>
 *
 * <p>次のレベルまでの経験値は「{@code exp.base} × {@code exp.growth} の（今のレベル）乗」（Phase 0-A 19章）。</p>
 *
 * <p>Phase 3で、章ごとのレベルの上限（{@code chapters}：第1章1〜5日はLv15、第2章6〜10日はLv28、第3章11〜15日はLv40、
 * 第4章16〜20日はLv50）、上限に届いている間に経験値の代わりにたまる「結晶の欠片」（{@code overflow}）、
 * 追いつき補正（{@code catch_up}：サーバーのレベルの中央値より5以上低い人は+50%、10以上低い人は+100%）を足した。</p>
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
    private final List<Chapter> chapters;
    private final int expPerShard;
    @Nullable
    private final ResourceLocation overflowItem;
    private final List<CatchUp> catchUp;

    /** 章（{@code fromDay} の進行日から始まり、その間のレベルの上限は {@code levelCap}）。 */
    public record Chapter(int fromDay, int levelCap) {
    }

    /** 追いつき補正の一段（中央値より {@code below} 以上低いと、経験値が {@code bonus}% 増える）。 */
    public record CatchUp(int below, double bonus) {
    }

    private GrowthDef(int maxLevel, double expBase, double expGrowth,
                      int statPointsPerLevel, int skillPointsPerLevel, int skillBonusEvery, int skillBonus,
                      double[] base, double[] perLevel, double[] perPoint, List<Chapter> chapters, int expPerShard,
                      @Nullable ResourceLocation overflowItem, List<CatchUp> catchUp) {
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
        this.chapters = chapters;
        this.expPerShard = expPerShard;
        this.overflowItem = overflowItem;
        this.catchUp = catchUp;
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** 章の一覧（始まる日の順）。 */
    public List<Chapter> chapters() {
        return chapters;
    }

    /** その進行日が何章か（1から。章の定義がなければ1）。 */
    public int chapterForDay(int day) {
        int chapter = 1;
        for (int i = 0; i < chapters.size(); i++) {
            if (day >= chapters.get(i).fromDay()) {
                chapter = i + 1;
            }
        }
        return chapter;
    }

    /** その章のレベルの上限（最大レベルを超えない。章の定義がなければ最大レベル）。 */
    public int levelCap(int chapter) {
        if (chapters.isEmpty()) {
            return maxLevel;
        }
        int index = Math.max(0, Math.min(chapters.size() - 1, chapter - 1));
        return Math.min(maxLevel, chapters.get(index).levelCap());
    }

    /** 上限に届いている間、結晶の欠片1つに変わる経験値（0なら変えない）。 */
    public int expPerShard() {
        return expPerShard;
    }

    /** 上限に届いている間にたまるアイテム（結晶の欠片）。 */
    @Nullable
    public ResourceLocation overflowItem() {
        return overflowItem;
    }

    /** 追いつき補正（%）。{@code belowMedian} はサーバーのレベルの中央値より何レベル低いか。 */
    public double catchUpBonus(int belowMedian) {
        double bonus = 0.0;
        for (CatchUp step : catchUp) {
            if (belowMedian >= step.below()) {
                bonus = Math.max(bonus, step.bonus());
            }
        }
        return bonus;
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

        List<Chapter> chapters = new ArrayList<>();
        JsonArray chapterArray = Json.optionalArray(json, "chapters");
        int lastDay = 0;
        int lastCap = 0;
        for (int i = 0; i < chapterArray.size(); i++) {
            JsonObject entry = Json.asObject(chapterArray.get(i), "chapters[" + i + "]");
            int fromDay = Json.intInRange(entry, "from_day", 1, 1000);
            int cap = Json.intInRange(entry, "level_cap", 1, maxLevel);
            if (fromDay <= lastDay) {
                throw new DefinitionException("「chapters」の「from_day」は、前の章より後の日にしてください（" + (i + 1) + "番目）");
            }
            if (cap < lastCap) {
                throw new DefinitionException("「chapters」の「level_cap」は、前の章より下げないでください（" + (i + 1) + "番目）");
            }
            lastDay = fromDay;
            lastCap = cap;
            chapters.add(new Chapter(fromDay, cap));
        }
        if (!chapters.isEmpty() && chapters.get(0).fromDay() != 1) {
            throw new DefinitionException("「chapters」の最初の章は「from_day」を1にしてください");
        }

        int expPerShard = 0;
        ResourceLocation overflowItem = null;
        JsonObject overflow = Json.optionalObject(json, "overflow");
        if (overflow != null) {
            expPerShard = Json.intInRange(overflow, "exp_per_item", 0, 1_000_000);
            overflowItem = Json.optionalId(overflow, "item");
        }

        List<CatchUp> catchUp = new ArrayList<>();
        JsonArray catchUpArray = Json.optionalArray(json, "catch_up");
        for (int i = 0; i < catchUpArray.size(); i++) {
            JsonObject entry = Json.asObject(catchUpArray.get(i), "catch_up[" + i + "]");
            catchUp.add(new CatchUp(Json.intInRange(entry, "below_median", 1, 1000),
                    Json.doubleInRange(entry, "bonus", 0.0, 1000.0)));
        }
        return new GrowthDef(maxLevel, expBase, expGrowth, statPerLevel, skillPerLevel, bonusEvery, bonus,
                base, perLevel, perPoint, List.copyOf(chapters), expPerShard, overflowItem, List.copyOf(catchUp));
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

        List<Chapter> chapters = List.of(new Chapter(1, 15), new Chapter(6, 28), new Chapter(11, 40), new Chapter(16, 50));
        List<CatchUp> catchUp = List.of(new CatchUp(5, 50.0), new CatchUp(10, 100.0));
        return new GrowthDef(50, 120, 1.09, 3, 1, 5, 1, base, perLevel, perPoint, chapters, 200,
                AmemaruMod.id("crystal_shard"), catchUp);
    }
}
