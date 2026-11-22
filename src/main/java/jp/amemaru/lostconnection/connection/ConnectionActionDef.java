package jp.amemaru.lostconnection.connection;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

/**
 * コネクション量（CP）が増える行動（データパックの {@code connection_actions/}。Phase 0-A 13章「増える行動と量」・
 * Phase 0-B 6章「connection_actions/：行動、分野（7分野）、CP、1日の回数の上限」）。
 *
 * <pre>
 * {
 *   "name": "仲間の蘇生",
 *   "category": "social",
 *   "cp": 10,                 // 1回分のCP
 *   "per": 1,                 // この数ごとに1回分（「5体ごとに1」なら 5）
 *   "daily_limit": 0,         // 1日に増える回数の上限（0 なら上限なし。分野の上限は別にかかる）
 *   "same_target_daily": 3    // 同じ相手は1日に何回まで（0 なら数えない）
 * }
 * </pre>
 */
public record ConnectionActionDef(ResourceLocation id, String name, ConnectionCategory category, int cp, int per, int dailyLimit,
                                  int sameTargetDaily) {

    public static ConnectionActionDef parse(ResourceLocation id, JsonObject json) {
        return new ConnectionActionDef(id,
                Json.optionalString(json, "name", id.getPath()),
                ConnectionCategory.parse(Json.string(json, "category")),
                Json.intInRange(json, "cp", 0, 100_000),
                Json.optionalInt(json, "per", 1, 1, 100_000),
                Json.optionalInt(json, "daily_limit", 0, 0, 100_000),
                Json.optionalInt(json, "same_target_daily", 0, 0, 100_000));
    }
}
