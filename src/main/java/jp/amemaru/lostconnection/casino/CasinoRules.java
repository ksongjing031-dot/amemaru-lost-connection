package jp.amemaru.lostconnection.casino;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.casino.parlor.ParlorRules;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * カジノの決まり（データパックの {@code casino/default.json}。Phase 0-B 6章のフォルダの一覧「casino/：カジノの倍率と上限」。
 * Phase 7 その3）。数は【補完】。
 *
 * <ul>
 *   <li>{@code open_day}：開場の日（Phase 0-A 11章「3日目：カジノ開場（ルーレット）」）</li>
 *   <li>{@code knots_per_chip}：チップ1枚のノット。{@code daily_chip_limit}：1日に買えるチップ（15章「チップの購入には1日の上限」。
 *       日は毎日4時に切り替わる）</li>
 *   <li>{@code min_bet}・{@code max_bet}：1回の賭けのチップ。{@code max_round_bet}：1回の勝負で1人が賭けられるチップの合計</li>
 *   <li>{@code bet_seconds}・{@code roll_seconds}・{@code result_seconds}：卓の1回の勝負の流れ（賭ける → 回る・めくる・走る → 結果）</li>
 *   <li>{@code table_players}：「4人以上の卓」の人数（13章のCP・カジノナイトの配当のおまけ）。{@code night_bonus}：おまけの割合</li>
 *   <li>{@code watch_range}：画面を開いた所から、卓を離れたことになる距離</li>
 *   <li>{@code zones}：右クリックで画面が開く所（中央広場の真ん中からの位置。tools/mapgen/build_casino.py の卓と窓口）</li>
 *   <li>{@code highlow}：共同事業②で遊べるようになるハイ＆ロー（物語の印）と、配当の決め方。11月27日のご指示「ルーレットとハイ＆ロー
 *       以外を廃止」で、⑥の魚レースはやめた（⑥は「カジノの新台入替」）</li>
 *   <li>{@code prizes}：景品（15章「見た目の品、称号、留め糸など、強さに直結しない物」）</li>
 *   <li>{@code parlor}：地下遊技場（パチンコとスロット。11月27日のご指示）の決まりと台の並び（{@link ParlorRules}）</li>
 * </ul>
 */
public final class CasinoRules {
    public static final ResourceLocation DEFAULT_ID = AmemaruMod.id("default");
    private static final DefinitionLoader<CasinoRules> LOADER = new DefinitionLoader<>("casino", CasinoRules::parse);

    /** 右クリックで画面が開く所（中央広場の真ん中からの位置。両端を含む）。 */
    public record Zone(String id, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    /**
     * 景品。{@code kind}：item（品物。{@code cosmetic} なら強さのない見た目の品）・banner（ラッキーノットの旗）・title（称号）。
     */
    public record Prize(String id, String kind, @Nullable ResourceLocation item, int count, int color, boolean cosmetic,
                        @Nullable ResourceLocation title, long chips) {
    }

    private final int openDay;
    private final long knotsPerChip;
    private final long dailyChipLimit;
    private final long minBet;
    private final long maxBet;
    private final long maxRoundBet;
    private final int betSeconds;
    private final int rollSeconds;
    private final int resultSeconds;
    private final int tablePlayers;
    private final double nightBonus;
    private final int nightMinutes;
    private final int watchRange;
    private final Map<String, Zone> zones;
    private final ResourceLocation highLowFlag;
    private final double highLowEdge;
    private final List<Prize> prizes;
    private final ParlorRules parlor;

    public static final CasinoRules FALLBACK = new CasinoRules(3, 10, 500, 1, 100, 300, 25, 6, 5, 4, 0.1, 60, 24, Map.of(),
            AmemaruMod.id("project/highlow"), 0.97, List.of(), ParlorRules.EMPTY);

    private CasinoRules(int openDay, long knotsPerChip, long dailyChipLimit, long minBet, long maxBet, long maxRoundBet, int betSeconds,
                        int rollSeconds, int resultSeconds, int tablePlayers, double nightBonus, int nightMinutes, int watchRange,
                        Map<String, Zone> zones, ResourceLocation highLowFlag, double highLowEdge, List<Prize> prizes,
                        ParlorRules parlor) {
        this.openDay = openDay;
        this.knotsPerChip = knotsPerChip;
        this.dailyChipLimit = dailyChipLimit;
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.maxRoundBet = maxRoundBet;
        this.betSeconds = betSeconds;
        this.rollSeconds = rollSeconds;
        this.resultSeconds = resultSeconds;
        this.tablePlayers = tablePlayers;
        this.nightBonus = nightBonus;
        this.nightMinutes = nightMinutes;
        this.watchRange = watchRange;
        this.zones = zones;
        this.highLowFlag = highLowFlag;
        this.highLowEdge = highLowEdge;
        this.prizes = prizes;
        this.parlor = parlor;
    }

    public static DefinitionLoader<CasinoRules> loader() {
        return LOADER;
    }

    public static CasinoRules current() {
        return LOADER.get(DEFAULT_ID).orElse(FALLBACK);
    }

    static CasinoRules parse(JsonObject json) {
        long minBet = (long) Json.optionalDouble(json, "min_bet", 1, 1, 1.0E6);
        long maxBet = (long) Json.optionalDouble(json, "max_bet", 100, 1, 1.0E9);
        if (maxBet < minBet) {
            throw new DefinitionException("「max_bet」は「min_bet」より小さくできません");
        }
        Map<String, Zone> zones = new LinkedHashMap<>();
        JsonObject zoneJson = Json.optionalObject(json, "zones");
        if (zoneJson != null) {
            for (Map.Entry<String, JsonElement> e : zoneJson.entrySet()) {
                if (!CasinoService.ZONES.contains(e.getKey())) {
                    throw new DefinitionException("「zones」に書けるのは " + String.join("・", CasinoService.ZONES) + " です（今は " + e.getKey() + "）");
                }
                JsonObject box = Json.asObject(e.getValue(), "zones." + e.getKey());
                int[] min = triple(Json.array(box, "min"), "zones." + e.getKey() + ".min");
                int[] max = triple(Json.array(box, "max"), "zones." + e.getKey() + ".max");
                zones.put(e.getKey(), new Zone(e.getKey(), Math.min(min[0], max[0]), Math.min(min[1], max[1]), Math.min(min[2], max[2]),
                        Math.max(min[0], max[0]), Math.max(min[1], max[1]), Math.max(min[2], max[2])));
            }
        }
        JsonObject hl = Json.object(json, "highlow");
        List<Prize> prizes = new ArrayList<>();
        JsonArray prizeJson = Json.optionalArray(json, "prizes");
        if (prizeJson != null) {
            for (int i = 0; i < prizeJson.size(); i++) {
                JsonObject one = Json.asObject(prizeJson.get(i), "prizes[" + i + "]");
                String kind = Json.string(one, "kind");
                if (!List.of("item", "banner", "title").contains(kind)) {
                    throw new DefinitionException("「prizes[" + i + "].kind」は item・banner・title のどれかにしてください（今は " + kind + "）");
                }
                ResourceLocation item = "title".equals(kind) ? null : Json.id(one, "item");
                ResourceLocation title = "title".equals(kind) ? Json.id(one, "title") : null;
                String color = Json.optionalString(one, "color", "");
                int rgb = 0;
                if (!color.isEmpty()) {
                    try {
                        rgb = Integer.parseInt(color.replace("#", ""), 16);
                    } catch (NumberFormatException ex) {
                        throw new DefinitionException("「prizes[" + i + "].color」は #RRGGBB で書いてください（今は " + color + "）");
                    }
                }
                prizes.add(new Prize(Json.string(one, "id"), kind, item, Json.optionalInt(one, "count", 1, 1, 64), rgb,
                        Json.optionalBoolean(one, "cosmetic", false), title, (long) Json.doubleInRange(one, "chips", 1, 1.0E9)));
            }
        }
        return new CasinoRules(
                Json.optionalInt(json, "open_day", 3, 0, 1000),
                (long) Json.doubleInRange(json, "knots_per_chip", 1, 1.0E6),
                (long) Json.doubleInRange(json, "daily_chip_limit", 0, 1.0E9),
                minBet, maxBet,
                (long) Json.optionalDouble(json, "max_round_bet", 300, 1, 1.0E9),
                Json.optionalInt(json, "bet_seconds", 25, 3, 600),
                Json.optionalInt(json, "roll_seconds", 6, 1, 60),
                Json.optionalInt(json, "result_seconds", 5, 1, 60),
                Json.optionalInt(json, "table_players", 4, 1, 100),
                Json.optionalDouble(json, "night_bonus", 0.1, 0.0, 10.0),
                Json.optionalInt(json, "night_minutes", 60, 1, 24 * 60),
                Json.optionalInt(json, "watch_range", 24, 2, 128),
                Collections.unmodifiableMap(zones),
                Json.parseId(Json.string(hl, "flag"), "highlow.flag"),
                Json.optionalDouble(hl, "edge", 0.97, 0.5, 1.0),
                List.copyOf(prizes),
                ParlorRules.parse(Json.optionalObject(json, "parlor")));
    }

    private static int[] triple(JsonArray array, String name) {
        if (array.size() != 3) {
            throw new DefinitionException("「" + name + "」は [x, y, z] で書いてください");
        }
        return new int[]{(int) Json.asNumber(array.get(0), name), (int) Json.asNumber(array.get(1), name), (int) Json.asNumber(array.get(2), name)};
    }

    public int openDay() {
        return openDay;
    }

    public long knotsPerChip() {
        return knotsPerChip;
    }

    public long dailyChipLimit() {
        return dailyChipLimit;
    }

    public long minBet() {
        return minBet;
    }

    public long maxBet() {
        return maxBet;
    }

    public long maxRoundBet() {
        return maxRoundBet;
    }

    public int betTicks() {
        return betSeconds * 20;
    }

    public int rollTicks() {
        return rollSeconds * 20;
    }

    public int resultTicks() {
        return resultSeconds * 20;
    }

    public int tablePlayers() {
        return tablePlayers;
    }

    public double nightBonus() {
        return nightBonus;
    }

    public int nightMinutes() {
        return nightMinutes;
    }

    public int watchRange() {
        return watchRange;
    }

    public Map<String, Zone> zones() {
        return zones;
    }

    public ResourceLocation highLowFlag() {
        return highLowFlag;
    }

    public double highLowEdge() {
        return highLowEdge;
    }

    public List<Prize> prizes() {
        return prizes;
    }

    /** 地下遊技場の決まりと台の並び。 */
    public ParlorRules parlor() {
        return parlor;
    }

    @Nullable
    public Prize prize(String id) {
        for (Prize p : prizes) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }
}
