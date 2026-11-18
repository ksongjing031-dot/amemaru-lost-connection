package jp.amemaru.lostconnection.workshop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * 生産の熟練度の決まり（{@code data/amemaru/amemaru/production_rules/default.json}。Phase 0-A 19章）。
 *
 * <ul>
 *   <li>熟練度は1〜10。章ごとに上限が開く【補完】（第1章3・第2章5・第3章8・第4章10）。上限では経験がたまらない</li>
 *   <li>熟練度ごとに要る経験の合計【補完】（作るたびに、レシピの mastery_exp だけ増える）</li>
 *   <li>「大成功」の確率【補完】：5% ＋（熟練度 − レシピの熟練度）× 4%、最大40%。装備は品質が一段上がり、
 *       アクセサリーはサブが一つ多くつく</li>
 * </ul>
 */
public final class ProductionRules {
    public static final ResourceLocation DEFAULT_ID = AmemaruMod.id("default");

    private final int[] thresholds;
    private final int[] chapterCaps;
    private final double greatBase;
    private final double greatPerLevel;
    private final double greatMax;

    private ProductionRules(int[] thresholds, int[] chapterCaps, double greatBase, double greatPerLevel, double greatMax) {
        this.thresholds = thresholds;
        this.chapterCaps = chapterCaps;
        this.greatBase = greatBase;
        this.greatPerLevel = greatPerLevel;
        this.greatMax = greatMax;
    }

    /** 熟練度の最大（10）。 */
    public int maxMastery() {
        return thresholds.length;
    }

    /** その章の熟練度の上限。 */
    public int cap(int chapter) {
        int index = Math.max(0, Math.min(chapterCaps.length - 1, chapter - 1));
        return Math.min(maxMastery(), chapterCaps[index]);
    }

    /** 経験から熟練度（1〜）。 */
    public int level(int exp, int chapter) {
        int level = 1;
        for (int i = 1; i < thresholds.length; i++) {
            if (exp >= thresholds[i]) {
                level = i + 1;
            }
        }
        return Math.min(level, cap(chapter));
    }

    /** その熟練度になるのに要る経験の合計（1なら0）。 */
    public int threshold(int level) {
        int index = Math.max(0, Math.min(thresholds.length - 1, level - 1));
        return thresholds[index];
    }

    /** 経験を足した後の値（章の上限の次の段に届く手前で止める）。 */
    public int addExp(int exp, int amount, int chapter) {
        int next = exp + Math.max(0, amount);
        int cap = cap(chapter);
        if (cap < maxMastery()) {
            next = Math.min(next, threshold(cap + 1) - 1);
        }
        return Math.max(exp, next);
    }

    /** 「大成功」の確率（%）。 */
    public double greatChance(int mastery, int recipeMastery) {
        return Math.min(greatMax, greatBase + Math.max(0, mastery - recipeMastery) * greatPerLevel);
    }

    // ---------------------------------------------------------------- 読み込み・通信

    private static final DefinitionLoader<ProductionRules> LOADER = new Loader();
    private static final ProductionRules FALLBACK = new ProductionRules(new int[]{0, 20, 50, 100, 170, 260, 380, 530, 720, 950},
            new int[]{3, 5, 8, 10}, 5.0, 4.0, 40.0);
    private static volatile ProductionRules client = FALLBACK;

    public static DefinitionLoader<ProductionRules> loader() {
        return LOADER;
    }

    public static ProductionRules current() {
        return LOADER.get(DEFAULT_ID).orElse(FALLBACK);
    }

    /** クライアントの写し（工房の画面で使う）。 */
    public static ProductionRules client() {
        return client;
    }

    public static void setClient(ProductionRules rules) {
        client = rules;
    }

    public static ProductionRules parse(JsonObject json) {
        JsonArray expArray = Json.array(json, "mastery_exp");
        if (expArray.size() < 2) {
            throw new DefinitionException("「mastery_exp」には熟練度ごとの経験の合計を、2つ以上書いてください");
        }
        int[] thresholds = new int[expArray.size()];
        for (int i = 0; i < thresholds.length; i++) {
            thresholds[i] = (int) Json.asNumber(expArray.get(i), "mastery_exp");
            if (i == 0 && thresholds[i] != 0) {
                throw new DefinitionException("「mastery_exp」の最初（熟練度1）は0にしてください");
            }
            if (i > 0 && thresholds[i] <= thresholds[i - 1]) {
                throw new DefinitionException("「mastery_exp」は大きくなる順にしてください");
            }
        }
        JsonArray capArray = Json.array(json, "chapter_caps");
        int[] caps = new int[capArray.size()];
        for (int i = 0; i < caps.length; i++) {
            caps[i] = (int) Json.asNumber(capArray.get(i), "chapter_caps");
        }
        if (caps.length == 0) {
            throw new DefinitionException("「chapter_caps」に章ごとの熟練度の上限を書いてください");
        }
        JsonObject great = Json.object(json, "great_success");
        return new ProductionRules(thresholds, caps, Json.doubleInRange(great, "base", 0.0, 100.0),
                Json.doubleInRange(great, "per_level", 0.0, 100.0), Json.doubleInRange(great, "max", 0.0, 100.0));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarIntArray(thresholds);
        buf.writeVarIntArray(chapterCaps);
        buf.writeDouble(greatBase);
        buf.writeDouble(greatPerLevel);
        buf.writeDouble(greatMax);
    }

    public static ProductionRules read(FriendlyByteBuf buf) {
        int[] thresholds = buf.readVarIntArray(64);
        int[] caps = buf.readVarIntArray(16);
        return new ProductionRules(thresholds, caps, buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static final class Loader extends DefinitionLoader<ProductionRules> {
        Loader() {
            super("production_rules", ProductionRules::parse);
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
