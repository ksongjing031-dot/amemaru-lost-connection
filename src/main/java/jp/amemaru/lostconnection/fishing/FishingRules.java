package jp.amemaru.lostconnection.fishing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 釣りの決まり（データパックの {@code fishing_rules/default.json}。Phase 7 その2）。数は【補完】。
 *
 * <ul>
 *   <li>{@code rarity_weights}：★1〜3の出やすさ。{@code current_chapter_weight}：今の章の魚の出やすさの倍率</li>
 *   <li>{@code memory_chance}・{@code lord_chance}：記憶魚・湖の主の確率（%。釣り堀と湖だけ／湖だけ）</li>
 *   <li>{@code size_skew}：大きさのかたより（1より大きいほど、小さいのが多い）。{@code big_ratio}：「大物」とみなす割合</li>
 *   <li>{@code big_price_factor}：大物（大きさの割合が big_ratio 以上。持ち物でほかと重ならない）の買い取りの倍率。ふつうの魚は
 *       大きさを持たず、同じ魚どうしで重なる（持ち物があふれないように【補完】）</li>
 *   <li>{@code points_per_thread}：留め糸1つに要る点。{@code near_radius}・{@code near_seconds}：「ほかの人の近くで釣る」の距離と時間</li>
 *   <li>{@code spots}：釣り堀と湖の範囲（中央広場の真ん中からの位置）。{@code lake_flag}：湖で釣れるようになる物語の印</li>
 *   <li>{@code minigame}：糸の張りのゲージ。{@code tournament}：釣り大会の長さ・始まるまでの秒・★ごとの点の倍率・賞品</li>
 * </ul>
 */
public final class FishingRules {
    public static final ResourceLocation DEFAULT_ID = AmemaruMod.id("default");
    private static final DefinitionLoader<FishingRules> LOADER = new DefinitionLoader<>("fishing_rules", FishingRules::parse);

    /** 釣り場の範囲（中央広場の真ん中からの位置。両端を含む）。 */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    /** ミニゲーム（糸の張り）の決まり。 */
    public record Minigame(double zoneCenter, double zoneHalf, double zoneShrink, double reelRate, double slackRate, double pull,
                           double burst, double decay, int baseTicks, int extraTicks, int escapeTicks, int limitSeconds) {
    }

    /** 大会の賞品（順位ごと）。 */
    public record Prize(long knots, int threads) {
    }

    private final Map<Integer, Double> rarityWeights;
    private final double currentChapterWeight;
    private final double memoryChance;
    private final double lordChance;
    private final double sizeSkew;
    private final double bigRatio;
    private final double bigPriceFactor;
    private final int pointsPerThread;
    private final long rodRentKnots;
    private final int nearRadius;
    private final int nearSeconds;
    private final Map<String, Box> spots;
    private final ResourceLocation lakeFlag;
    private final Minigame minigame;
    private final int tournamentMinutes;
    private final int tournamentCountdown;
    private final Map<Integer, Double> tournamentMultiplier;
    private final List<Prize> prizes;

    public static final FishingRules FALLBACK = new FishingRules(Map.of(1, 60.0, 2, 30.0, 3, 10.0), 1.5, 2.0, 0.2, 1.6, 0.9, 1.3, 10, 0,
            16, 30, Map.of("pond", new Box(-96, -10, 22, -59, 6, 68), "lake", new Box(-132, -12, 54, -95, 6, 92)),
            AmemaruMod.id("project/lakeside"),
            new Minigame(0.5, 0.19, 0.35, 0.024, 0.022, 0.016, 0.065, 0.25, 50, 50, 40, 20), 10, 30,
            Map.of(1, 1.0, 2, 2.0, 3, 4.0, 4, 6.0, 5, 20.0),
            List.of(new Prize(3000, 3), new Prize(2000, 2), new Prize(1000, 1)));

    private FishingRules(Map<Integer, Double> rarityWeights, double currentChapterWeight, double memoryChance, double lordChance,
                         double sizeSkew, double bigRatio, double bigPriceFactor, int pointsPerThread, long rodRentKnots,
                         int nearRadius, int nearSeconds, Map<String, Box> spots, ResourceLocation lakeFlag, Minigame minigame,
                         int tournamentMinutes, int tournamentCountdown, Map<Integer, Double> tournamentMultiplier, List<Prize> prizes) {
        this.rarityWeights = rarityWeights;
        this.currentChapterWeight = currentChapterWeight;
        this.memoryChance = memoryChance;
        this.lordChance = lordChance;
        this.sizeSkew = sizeSkew;
        this.bigRatio = bigRatio;
        this.bigPriceFactor = bigPriceFactor;
        this.pointsPerThread = pointsPerThread;
        this.rodRentKnots = rodRentKnots;
        this.nearRadius = nearRadius;
        this.nearSeconds = nearSeconds;
        this.spots = spots;
        this.lakeFlag = lakeFlag;
        this.minigame = minigame;
        this.tournamentMinutes = tournamentMinutes;
        this.tournamentCountdown = tournamentCountdown;
        this.tournamentMultiplier = tournamentMultiplier;
        this.prizes = prizes;
    }

    public static DefinitionLoader<FishingRules> loader() {
        return LOADER;
    }

    public static FishingRules current() {
        return LOADER.get(DEFAULT_ID).orElse(FALLBACK);
    }

    static FishingRules parse(JsonObject json) {
        Map<Integer, Double> weights = rarityMap(Json.object(json, "rarity_weights"), "rarity_weights");
        Map<String, Box> spots = new HashMap<>();
        JsonObject spotJson = Json.object(json, "spots");
        for (Map.Entry<String, JsonElement> entry : spotJson.entrySet()) {
            if (!FishDef.SPOTS.contains(entry.getKey()) || "river".equals(entry.getKey())) {
                throw new DefinitionException("「spots」に書けるのは pond と lake です（今は " + entry.getKey() + "）");
            }
            JsonObject box = Json.asObject(entry.getValue(), "spots." + entry.getKey());
            int[] min = triple(Json.array(box, "min"), "spots." + entry.getKey() + ".min");
            int[] max = triple(Json.array(box, "max"), "spots." + entry.getKey() + ".max");
            spots.put(entry.getKey(), new Box(Math.min(min[0], max[0]), Math.min(min[1], max[1]), Math.min(min[2], max[2]),
                    Math.max(min[0], max[0]), Math.max(min[1], max[1]), Math.max(min[2], max[2])));
        }
        JsonObject mg = Json.object(json, "minigame");
        Minigame minigame = new Minigame(
                Json.doubleInRange(mg, "zone_center", 0.1, 0.9),
                Json.doubleInRange(mg, "zone_half", 0.02, 0.45),
                Json.optionalDouble(mg, "zone_shrink", 0.35, 0.0, 0.9),
                Json.doubleInRange(mg, "reel_rate", 0.001, 0.5),
                Json.doubleInRange(mg, "slack_rate", 0.001, 0.5),
                Json.doubleInRange(mg, "pull", 0.0, 0.5),
                Json.doubleInRange(mg, "burst", 0.0, 0.5),
                Json.optionalDouble(mg, "decay", 0.5, 0.0, 5.0),
                Json.intInRange(mg, "base_ticks", 10, 2000),
                Json.intInRange(mg, "extra_ticks", 0, 2000),
                Json.intInRange(mg, "escape_ticks", 5, 2000),
                Json.intInRange(mg, "limit_seconds", 3, 300));
        JsonObject tournament = Json.object(json, "tournament");
        List<Prize> prizes = new ArrayList<>();
        JsonArray prizeJson = Json.optionalArray(tournament, "prizes");
        if (prizeJson != null) {
            for (int i = 0; i < prizeJson.size(); i++) {
                JsonObject one = Json.asObject(prizeJson.get(i), "tournament.prizes[" + i + "]");
                prizes.add(new Prize((long) Json.optionalDouble(one, "knots", 0, 0, 1.0E9), Json.optionalInt(one, "threads", 0, 0, 1000)));
            }
        }
        return new FishingRules(weights,
                Json.optionalDouble(json, "current_chapter_weight", 1.5, 0.0, 100.0),
                Json.optionalDouble(json, "memory_chance", 2.0, 0.0, 100.0),
                Json.optionalDouble(json, "lord_chance", 0.2, 0.0, 100.0),
                Json.optionalDouble(json, "size_skew", 1.6, 0.1, 10.0),
                Json.optionalDouble(json, "big_ratio", 0.9, 0.0, 1.0),
                Json.optionalDouble(json, "big_price_factor", 1.3, 0.0, 100.0),
                Json.intInRange(json, "points_per_thread", 1, 100_000),
                (long) Json.optionalDouble(json, "rod_rent_knots", 0, 0, 1.0E9),
                Json.optionalInt(json, "near_radius", 16, 1, 256),
                Json.optionalInt(json, "near_seconds", 30, 1, 3600),
                Collections.unmodifiableMap(spots),
                Json.parseId(Json.string(json, "lake_flag"), "lake_flag"),
                minigame,
                Json.optionalInt(tournament, "minutes", 10, 1, 240),
                Json.optionalInt(tournament, "countdown_seconds", 30, 0, 3600),
                rarityMap(Json.object(tournament, "rarity_multiplier"), "tournament.rarity_multiplier"),
                List.copyOf(prizes));
    }

    private static Map<Integer, Double> rarityMap(JsonObject json, String name) {
        Map<Integer, Double> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            int rarity;
            try {
                rarity = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException e) {
                throw new DefinitionException("「" + name + "」のキーは ★の数（1〜5）にしてください（今は " + entry.getKey() + "）");
            }
            if (rarity < 1 || rarity > 5) {
                throw new DefinitionException("「" + name + "」のキーは 1〜5 にしてください（今は " + rarity + "）");
            }
            map.put(rarity, Json.asNumber(entry.getValue(), name + "." + entry.getKey()));
        }
        return Collections.unmodifiableMap(map);
    }

    private static int[] triple(JsonArray array, String name) {
        if (array.size() != 3) {
            throw new DefinitionException("「" + name + "」は [x, y, z] で書いてください");
        }
        return new int[]{(int) Json.asNumber(array.get(0), name), (int) Json.asNumber(array.get(1), name), (int) Json.asNumber(array.get(2), name)};
    }

    public double rarityWeight(int rarity) {
        return rarityWeights.getOrDefault(rarity, 0.0);
    }

    public double currentChapterWeight() {
        return currentChapterWeight;
    }

    public double memoryChance() {
        return memoryChance;
    }

    public double lordChance() {
        return lordChance;
    }

    public double sizeSkew() {
        return sizeSkew;
    }

    public double bigRatio() {
        return bigRatio;
    }

    public double bigPriceFactor() {
        return bigPriceFactor;
    }

    public int pointsPerThread() {
        return pointsPerThread;
    }

    public long rodRentKnots() {
        return rodRentKnots;
    }

    public int nearRadius() {
        return nearRadius;
    }

    public int nearSeconds() {
        return nearSeconds;
    }

    public Map<String, Box> spots() {
        return spots;
    }

    public ResourceLocation lakeFlag() {
        return lakeFlag;
    }

    public Minigame minigame() {
        return minigame;
    }

    public int tournamentMinutes() {
        return tournamentMinutes;
    }

    public int tournamentCountdown() {
        return tournamentCountdown;
    }

    public double tournamentMultiplier(int rarity) {
        return tournamentMultiplier.getOrDefault(rarity, 1.0);
    }

    public List<Prize> prizes() {
        return prizes;
    }
}
