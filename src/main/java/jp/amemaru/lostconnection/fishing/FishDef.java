package jp.amemaru.lostconnection.fishing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 魚（データパックの {@code fishing/<名前>.json}。Phase 7 その2。Phase 0-A 15章「魚図鑑：全40種【提案】」）。アイテムは
 * {@code amemaru:fish_<名前>}（Modに同梱した {@code item_types.json} で起動時に登録する）。
 *
 * <pre>
 * {
 *   "name": "琥珀ゴイ",
 *   "chapter": 1,              // この章から釣れる（第1章は川魚、第2章は氷下の魚、第3章は湧き水の魚、第4章は空魚）
 *   "rarity": 3,               // ★1〜5（4は記憶魚、5は湖の主）
 *   "spots": ["pond", "lake"], // 釣り場（pond＝釣り堀、lake＝湖畔の釣り場、river＝そのほかの水）
 *   "size": [40, 80],          // 大きさ（cm）
 *   "price": 180,              // 買い取りの値（大物は big_price_factor 倍）
 *   "points": 5,               // 留め糸と交換するときの点
 *   "difficulty": 0.55,        // 手ごたえ（0〜1。ミニゲームの糸の張りの暴れ方と、かかっている長さ）
 *   "comment": "…",            // 図鑑のポッカ爺のひと言
 *   "memory": "amemaru:fish_memory_dawn",  // 記憶魚：記録帳に入る記録（なければ書かない）
 *   "legendary": true          // 湖の主（最初に釣った人を覚える）
 * }
 * </pre>
 */
public record FishDef(ResourceLocation id, String name, int chapter, int rarity, Set<String> spots, double sizeMin, double sizeMax,
                      long price, int points, double difficulty, String comment, int order, @Nullable ResourceLocation memory,
                      boolean legendary) {

    public static final Set<String> SPOTS = Set.of("pond", "lake", "river");

    public static FishDef parse(ResourceLocation id, JsonObject json) {
        Set<String> spots = new LinkedHashSet<>();
        for (JsonElement element : Json.array(json, "spots")) {
            String spot = Json.asString(element, "spots");
            if (!SPOTS.contains(spot)) {
                throw new DefinitionException("「spots」は pond・lake・river のどれかにしてください（今は " + spot + "）");
            }
            spots.add(spot);
        }
        if (spots.isEmpty()) {
            throw new DefinitionException("「spots」に、釣れる所を1つ以上書いてください");
        }
        var size = Json.array(json, "size");
        if (size.size() != 2) {
            throw new DefinitionException("「size」は [最小, 最大]（cm）で書いてください");
        }
        double lo = Json.asNumber(size.get(0), "size[0]");
        double hi = Json.asNumber(size.get(1), "size[1]");
        if (!(lo > 0 && hi > lo)) {
            throw new DefinitionException("「size」は 0 より大きく、最小より最大を大きくしてください");
        }
        return new FishDef(id,
                Json.string(json, "name"),
                Json.intInRange(json, "chapter", 1, 9),
                Json.intInRange(json, "rarity", 1, 5),
                Set.copyOf(spots), lo, hi,
                (long) Json.doubleInRange(json, "price", 0, 1.0E9),
                Json.optionalInt(json, "points", 1, 0, 100_000),
                Json.optionalDouble(json, "difficulty", 0.3, 0.0, 1.0),
                Json.optionalString(json, "comment", ""),
                Json.optionalInt(json, "order", 0, -100_000, 100_000),
                Json.optionalId(json, "memory"),
                Json.optionalBoolean(json, "legendary", false));
    }

    /** アイテムのID（{@code amemaru:fish_<名前>}）。 */
    public ResourceLocation itemId() {
        return new ResourceLocation(id.getNamespace(), "fish_" + id.getPath());
    }

    /** 記憶魚か。 */
    public boolean isMemory() {
        return memory != null;
    }

    /** 大きさの割合（0〜1。最小が0、最大が1）。 */
    public double sizeRatio(double size) {
        return Math.max(0.0, Math.min(1.0, (size - sizeMin) / (sizeMax - sizeMin)));
    }
}
