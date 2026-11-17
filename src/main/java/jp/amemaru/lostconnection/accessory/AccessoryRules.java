package jp.amemaru.lostconnection.accessory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.equipment.GearRules;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * アクセサリーの抽選の決まり（{@code data/amemaru/amemaru/accessory_rules/default.json}。Phase 0-A 21章）。
 *
 * <ul>
 *   <li>章ごとの倍率：数値は第3章の品を基準にし、第1章×0.4、第2章×0.7、第4章×1.3（%のステータスは×0.6／0.8／1.2）</li>
 *   <li>メイン：その章の上限値の70〜100%でランダム。上限値は【補完】（第3章で攻撃・魔力12、HP150、防御12、MP60、素早さ6、
 *       会心5%、回復量8%、ロスト耐性12%、会心ダメージ12%、クールダウン短縮4%）</li>
 *   <li>サブの数：1個30%、2個40%、3個25%、4個5%。装飾師の「大成功」でサブが一つ多くつく</li>
 *   <li>サブの抽選表（出やすさと1回分の数値の上限）。数値の幅は「下限＝上限の半分」</li>
 *   <li>品質：全ステータス（メインを含む）の割合の平均。★1は60%未満…★5は90%以上</li>
 *   <li>完璧なアクセサリー：サブ4つで、メインを含むすべての割合が95%以上</li>
 * </ul>
 */
public final class AccessoryRules {
    public static final ResourceLocation DEFAULT_ID = AmemaruMod.id("default");

    /** サブステータスの抽選表の1行。 */
    public record SubEntry(StatType type, int weight, double max) {
    }

    private final double[] flatFactor;
    private final double[] percentFactor;
    private final double mainMinRatio;
    private final Map<StatType, Double> mainCaps;
    private final int[] subCountWeights;
    private final double subMinRatio;
    private final Map<StatType, SubEntry> subs;
    private final int[] qualityThresholds;
    private final int perfectSubs;
    private final double perfectRatio;
    private final int maxEnhance;

    private AccessoryRules(double[] flatFactor, double[] percentFactor, double mainMinRatio, Map<StatType, Double> mainCaps,
                           int[] subCountWeights, double subMinRatio, Map<StatType, SubEntry> subs, int[] qualityThresholds,
                           int perfectSubs, double perfectRatio, int maxEnhance) {
        this.flatFactor = flatFactor;
        this.percentFactor = percentFactor;
        this.mainMinRatio = mainMinRatio;
        this.mainCaps = mainCaps;
        this.subCountWeights = subCountWeights;
        this.subMinRatio = subMinRatio;
        this.subs = subs;
        this.qualityThresholds = qualityThresholds;
        this.perfectSubs = perfectSubs;
        this.perfectRatio = perfectRatio;
        this.maxEnhance = maxEnhance;
    }

    /** その世代（章）の倍率。 */
    public double factor(StatType type, int generation) {
        double[] table = type.isPercent() ? percentFactor : flatFactor;
        int index = Math.max(0, Math.min(table.length - 1, generation - 1));
        return table[index];
    }

    /** メインの上限値（第3章の基準 × 章の倍率）。 */
    public double mainCap(StatType type, int generation) {
        Double base = mainCaps.get(type);
        return base == null ? 0.0 : base * factor(type, generation);
    }

    public double mainMinRatio() {
        return mainMinRatio;
    }

    /** サブの数の出やすさ（1個・2個・3個・4個）。 */
    public int[] subCountWeights() {
        return subCountWeights.clone();
    }

    public double subMinRatio() {
        return subMinRatio;
    }

    public Map<StatType, SubEntry> subs() {
        return subs;
    }

    /** サブの1回分の上限値（第3章の基準 × 章の倍率）。 */
    public double subCap(StatType type, int generation) {
        SubEntry entry = subs.get(type);
        return entry == null ? 0.0 : entry.max() * factor(type, generation);
    }

    /** 平均の割合（0〜1）から品質（★1〜5）。 */
    public int quality(double averageRatio) {
        double percent = averageRatio * 100.0 + 1.0E-6;
        int stars = 1;
        for (int threshold : qualityThresholds) {
            if (percent >= threshold) {
                stars++;
            }
        }
        return Math.min(5, stars);
    }

    public int perfectSubs() {
        return perfectSubs;
    }

    /** 完璧の条件の割合（0〜1）。 */
    public double perfectRatio() {
        return perfectRatio;
    }

    public int maxEnhance() {
        return maxEnhance;
    }

    // ---------------------------------------------------------------- 読み込み

    private static final DefinitionLoader<AccessoryRules> LOADER = new Loader();
    private static final AccessoryRules FALLBACK = fallback();

    public static DefinitionLoader<AccessoryRules> loader() {
        return LOADER;
    }

    public static AccessoryRules current() {
        return LOADER.get(DEFAULT_ID).orElse(FALLBACK);
    }

    public static AccessoryRules parse(JsonObject json) {
        JsonArray factors = Json.array(json, "chapter_factor");
        if (factors.size() < 1) {
            throw new DefinitionException("「chapter_factor」に章ごとの倍率を書いてください");
        }
        double[] flat = new double[factors.size()];
        double[] percent = new double[factors.size()];
        for (int i = 0; i < factors.size(); i++) {
            JsonObject entry = Json.asObject(factors.get(i), "chapter_factor[" + i + "]");
            flat[i] = Json.doubleInRange(entry, "flat", 0.01, 100.0);
            percent[i] = Json.doubleInRange(entry, "percent", 0.01, 100.0);
        }

        JsonObject main = Json.object(json, "main");
        double mainMin = Json.doubleInRange(main, "min_ratio", 1.0, 100.0) / 100.0;
        Map<StatType, Double> caps = new EnumMap<>(StatType.class);
        for (Map.Entry<String, JsonElement> entry : Json.object(main, "caps").entrySet()) {
            StatType type = StatType.byId(entry.getKey()).orElseThrow(() ->
                    new DefinitionException("「main.caps」に、ない種類のステータス「" + entry.getKey() + "」があります"));
            caps.put(type, Json.asNumber(entry.getValue(), "main.caps." + entry.getKey()));
        }
        for (AccessorySlot slot : AccessorySlot.all()) {
            for (StatType type : slot.mainStats()) {
                if (!caps.containsKey(type)) {
                    throw new DefinitionException("「main.caps」に「" + type.id() + "」（" + slot.id() + "のメイン）がありません");
                }
            }
        }

        JsonObject subJson = Json.object(json, "subs");
        JsonArray countArray = Json.array(subJson, "count_weights");
        if (countArray.size() != 4) {
            throw new DefinitionException("「subs.count_weights」は4つ（1個・2個・3個・4個の出やすさ）にしてください");
        }
        int[] counts = new int[4];
        for (int i = 0; i < 4; i++) {
            counts[i] = (int) Json.asNumber(countArray.get(i), "subs.count_weights");
        }
        double subMin = Json.doubleInRange(subJson, "min_ratio", 1.0, 100.0) / 100.0;
        Map<StatType, SubEntry> subs = new EnumMap<>(StatType.class);
        for (Map.Entry<String, JsonElement> entry : Json.object(subJson, "table").entrySet()) {
            StatType type = StatType.byId(entry.getKey()).orElseThrow(() ->
                    new DefinitionException("「subs.table」に、ない種類のステータス「" + entry.getKey() + "」があります"));
            JsonObject one = Json.asObject(entry.getValue(), "subs.table." + entry.getKey());
            subs.put(type, new SubEntry(type, Json.intInRange(one, "weight", 0, 10000), Json.doubleInRange(one, "max", 0.0, 100000.0)));
        }
        if (subs.size() < 5) {
            throw new DefinitionException("「subs.table」には5種類以上のステータスを書いてください（メインを除いても4つ選べるように）");
        }

        int[] quality = GearRules.readThresholds(Json.array(json, "quality"));
        JsonObject perfect = Json.object(json, "perfect");
        int perfectSubs = Json.intInRange(perfect, "subs", 1, 4);
        double perfectRatio = Json.doubleInRange(perfect, "min_ratio", 1.0, 100.0) / 100.0;
        int maxEnhance = Json.optionalInt(json, "max_enhance", 12, 0, 100);
        return new AccessoryRules(flat, percent, mainMin, caps, counts, subMin, subs, quality, perfectSubs, perfectRatio,
                maxEnhance);
    }

    private static AccessoryRules fallback() {
        Map<StatType, Double> caps = new EnumMap<>(StatType.class);
        caps.put(StatType.ATTACK, 12.0);
        caps.put(StatType.MAGIC, 12.0);
        caps.put(StatType.HP, 150.0);
        caps.put(StatType.DEFENSE, 12.0);
        caps.put(StatType.MP, 60.0);
        caps.put(StatType.SPEED, 6.0);
        caps.put(StatType.CRIT, 5.0);
        caps.put(StatType.HEAL_POWER, 8.0);
        caps.put(StatType.LOST_RESIST, 12.0);
        caps.put(StatType.CRIT_DAMAGE, 12.0);
        caps.put(StatType.COOLDOWN_REDUCTION, 4.0);
        Map<StatType, SubEntry> subs = new EnumMap<>(StatType.class);
        subs.put(StatType.HP, new SubEntry(StatType.HP, 15, 80));
        subs.put(StatType.DEFENSE, new SubEntry(StatType.DEFENSE, 12, 8));
        subs.put(StatType.MP, new SubEntry(StatType.MP, 10, 30));
        subs.put(StatType.ATTACK, new SubEntry(StatType.ATTACK, 10, 8));
        subs.put(StatType.MAGIC, new SubEntry(StatType.MAGIC, 10, 8));
        subs.put(StatType.LOST_RESIST, new SubEntry(StatType.LOST_RESIST, 10, 6));
        subs.put(StatType.SPEED, new SubEntry(StatType.SPEED, 8, 4));
        subs.put(StatType.CRIT, new SubEntry(StatType.CRIT, 8, 3));
        subs.put(StatType.CRIT_DAMAGE, new SubEntry(StatType.CRIT_DAMAGE, 6, 6));
        subs.put(StatType.HEAL_POWER, new SubEntry(StatType.HEAL_POWER, 6, 4));
        subs.put(StatType.COOLDOWN_REDUCTION, new SubEntry(StatType.COOLDOWN_REDUCTION, 5, 2));
        return new AccessoryRules(new double[]{0.4, 0.7, 1.0, 1.3}, new double[]{0.6, 0.8, 1.0, 1.2}, 0.7, caps,
                new int[]{30, 40, 25, 5}, 0.5, subs, new int[]{60, 70, 80, 90}, 4, 0.95, 12);
    }

    @Override
    public String toString() {
        return "AccessoryRules" + Arrays.toString(flatFactor);
    }

    private static final class Loader extends DefinitionLoader<AccessoryRules> {
        Loader() {
            super("accessory_rules", AccessoryRules::parse);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
            super.apply(jsons, resourceManager, profiler);
            if (get(DEFAULT_ID).isEmpty()) {
                addError("「" + DEFAULT_ID + "」が見つからないため、Modに入っている初期値を使います");
            }
        }
    }
}
