package jp.amemaru.lostconnection.progression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 進行日の予定（データパックの {@code progression/days.json}）。管理者が進行日を進めると、その日に決めてあること（階層の解放・
 * 全員への知らせ）をまとめて行う（Phase 0-B 9章「管理者が進行日を進める」、Phase 0-A 11章の日ごとの進行表）。
 *
 * <pre>
 * { "days": { "1": { "unlock_floors": [1], "announce": "…" }, "2": { … } } }
 * </pre>
 *
 * <p>街の変化・共同事業の受付・NPCの会話の切り替えは、物語フラグと進行日の条件（{@code day_min}）で出し分けるので、ここには書かない。</p>
 */
public final class DaySchedule {
    /** その日にすること。 */
    public record Day(int day, List<Integer> unlockFloors, String announce) {
    }

    private static final DefinitionLoader<Map<Integer, Day>> LOADER = new DefinitionLoader<>("progression", DaySchedule::parse);
    private static final ResourceLocation DEFAULT = AmemaruMod.id("days");

    private DaySchedule() {
    }

    public static DefinitionLoader<Map<Integer, Day>> loader() {
        return LOADER;
    }

    /** その日にすること（決めていなければ、何もしない日）。 */
    public static Day day(int day) {
        Map<Integer, Day> days = LOADER.get(DEFAULT).orElse(Map.of());
        return days.getOrDefault(day, new Day(day, List.of(), ""));
    }

    /** その日までに開く階（1日目からその日まで）。 */
    public static List<Integer> floorsUntil(int day) {
        List<Integer> list = new ArrayList<>();
        Map<Integer, Day> days = LOADER.get(DEFAULT).orElse(Map.of());
        for (Day d : days.values()) {
            if (d.day() <= day) {
                for (int floor : d.unlockFloors()) {
                    if (!list.contains(floor)) {
                        list.add(floor);
                    }
                }
            }
        }
        if (days.isEmpty() && !list.contains(1)) {
            // 予定のファイルがないときも、1階だけは開く
            list.add(1);
        }
        return list;
    }

    private static Map<Integer, Day> parse(JsonObject json) {
        JsonObject days = Json.object(json, "days");
        Map<Integer, Day> map = new TreeMap<>();
        for (String key : days.keySet()) {
            int day;
            try {
                day = Integer.parseInt(key.trim());
            } catch (NumberFormatException e) {
                throw new DefinitionException("「days」の「" + key + "」は日の数にしてください");
            }
            if (day < 1 || day > ProgressionData.MAX_DAY) {
                throw new DefinitionException("「days」の日は1〜" + ProgressionData.MAX_DAY + "にしてください（今は " + key + "）");
            }
            JsonObject one = Json.asObject(days.get(key), "days." + key);
            List<Integer> floors = new ArrayList<>();
            JsonArray array = Json.optionalArray(one, "unlock_floors");
            for (int i = 0; i < array.size(); i++) {
                floors.add((int) Json.asNumber(array.get(i), "unlock_floors"));
            }
            map.put(day, new Day(day, List.copyOf(floors), Json.optionalString(one, "announce", "")));
        }
        return Map.copyOf(map);
    }
}
