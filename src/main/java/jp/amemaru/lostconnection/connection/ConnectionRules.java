package jp.amemaru.lostconnection.connection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 輝き段階の決まり（{@code data/amemaru/amemaru/connection_rules/default.json}。Phase 0-A 13章「輝き段階と効果」）。
 *
 * <ul>
 *   <li>{@code stages}：段階1〜10になる累計CP（25人基準。人数補正の倍率を掛けて使う）</li>
 *   <li>{@code star_step}・{@code max_stars}：段階10の後、このCPごとに「加護の星」が一つ灯る（最大5つ）</li>
 *   <li>{@code effects}：段階ごとの効果（全員・累積。同じ種類は、届いたいちばん上の段階の値）。
 *       {@code lost_resist}（ロスト耐性%）・{@code exp}（経験値%）・{@code barrier}（襲撃時の結界の耐久%）・
 *       {@code down_seconds}（ダウンの秒数）・{@code blessing}（最終決戦の加護。1で最大）・{@code barrier_plaza}（結界が塔前広場まで）</li>
 *   <li>{@code voices}：段階が上がったときの、大結晶（リンク・コア）の一言。段階が上がるほど、はっきりする（6章）</li>
 * </ul>
 */
public final class ConnectionRules {
    public static final ResourceLocation DEFAULT_ID = AmemaruMod.id("default");
    private static final DefinitionLoader<ConnectionRules> LOADER = new DefinitionLoader<>("connection_rules", ConnectionRules::parse);
    /** 読めなかったときの決まり（Phase 0-A 13章の値）。 */
    public static final ConnectionRules FALLBACK = new ConnectionRules(
            new long[]{0, 5_000, 15_000, 27_500, 42_500, 62_500, 85_000, 110_000, 137_500, 165_000}, 5_000, 5, Map.of(), Map.of(), "");

    private final long[] thresholds;
    private final long starStep;
    private final int maxStars;
    private final Map<Integer, Map<String, Double>> effects;
    private final Map<Integer, String> voices;
    private final String starVoice;

    private ConnectionRules(long[] thresholds, long starStep, int maxStars, Map<Integer, Map<String, Double>> effects,
                            Map<Integer, String> voices, String starVoice) {
        this.thresholds = thresholds;
        this.starStep = starStep;
        this.maxStars = maxStars;
        this.effects = effects;
        this.voices = voices;
        this.starVoice = starVoice;
    }

    public static DefinitionLoader<ConnectionRules> loader() {
        return LOADER;
    }

    public static ConnectionRules current() {
        return LOADER.get(DEFAULT_ID).orElse(FALLBACK);
    }

    static ConnectionRules parse(JsonObject json) {
        JsonArray stages = Json.array(json, "stages");
        if (stages.size() < 2) {
            throw new DefinitionException("「stages」には、段階1から順に、2つ以上の累計CPを書いてください");
        }
        long[] thresholds = new long[stages.size()];
        for (int i = 0; i < stages.size(); i++) {
            thresholds[i] = (long) Json.asNumber(stages.get(i), "stages[" + i + "]");
            if (i == 0 && thresholds[i] != 0) {
                throw new DefinitionException("「stages」の1つ目（段階1）は 0 にしてください");
            }
            if (i > 0 && thresholds[i] <= thresholds[i - 1]) {
                throw new DefinitionException("「stages」は、だんだん大きくなるように書いてください（" + (i + 1) + "つ目）");
            }
        }
        Map<Integer, Map<String, Double>> effects = new HashMap<>();
        JsonObject effectJson = Json.optionalObject(json, "effects");
        if (effectJson != null) {
            for (Map.Entry<String, JsonElement> entry : effectJson.entrySet()) {
                int stage = stageKey(entry.getKey(), thresholds.length);
                Map<String, Double> values = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : Json.asObject(entry.getValue(), "effects." + entry.getKey()).entrySet()) {
                    values.put(e.getKey(), Json.asNumber(e.getValue(), "effects." + entry.getKey() + "." + e.getKey()));
                }
                effects.put(stage, Collections.unmodifiableMap(values));
            }
        }
        Map<Integer, String> voices = new HashMap<>();
        JsonObject voiceJson = Json.optionalObject(json, "voices");
        if (voiceJson != null) {
            for (Map.Entry<String, JsonElement> entry : voiceJson.entrySet()) {
                voices.put(stageKey(entry.getKey(), thresholds.length), Json.asString(entry.getValue(), "voices." + entry.getKey()));
            }
        }
        return new ConnectionRules(thresholds,
                (long) Json.optionalDouble(json, "star_step", 5_000, 1, 1.0E9),
                Json.optionalInt(json, "max_stars", 5, 0, 100),
                Collections.unmodifiableMap(effects), Collections.unmodifiableMap(voices),
                Json.optionalString(json, "star_voice", ""));
    }

    private static int stageKey(String key, int max) {
        try {
            int stage = Integer.parseInt(key);
            if (stage >= 1 && stage <= max) {
                return stage;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new DefinitionException("段階は 1〜" + max + " の数で書いてください（今は " + key + "）");
    }

    /** 段階の数（10）。 */
    public int maxStage() {
        return thresholds.length;
    }

    /** その段階になる累計CP（25人基準）。段階1は0。 */
    public long threshold(int stage) {
        return thresholds[Math.max(0, Math.min(thresholds.length - 1, stage - 1))];
    }

    /** 累計CPと人数補正の倍率から、段階（1〜10）。 */
    public int stageFor(long total, double scale) {
        int stage = 1;
        for (int i = 1; i < thresholds.length; i++) {
            if (total >= Math.round(thresholds[i] * scale)) {
                stage = i + 1;
            }
        }
        return stage;
    }

    /** 段階10の後の「加護の星」の数。 */
    public int starsFor(long total, double scale) {
        long top = Math.round(thresholds[thresholds.length - 1] * scale);
        long step = Math.max(1, Math.round(starStep * scale));
        if (total < top + step) {
            return 0;
        }
        return (int) Math.min(maxStars, (total - top) / step);
    }

    public long starStep() {
        return starStep;
    }

    public int maxStars() {
        return maxStars;
    }

    /** その種類の効果（届いたいちばん上の段階の値。なければ0）。 */
    public double effect(String key, int stage) {
        double value = 0.0;
        for (int s = 1; s <= Math.min(stage, thresholds.length); s++) {
            Map<String, Double> values = effects.get(s);
            if (values != null && values.containsKey(key)) {
                value = values.get(key);
            }
        }
        return value;
    }

    /** その段階で、新しく効く効果（段階が上がったときの知らせ）。 */
    public Map<String, Double> effectsAt(int stage) {
        return effects.getOrDefault(stage, Map.of());
    }

    public String voice(int stage) {
        return voices.getOrDefault(stage, "");
    }

    public String starVoice() {
        return starVoice;
    }
}
