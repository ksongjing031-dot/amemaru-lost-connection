package jp.amemaru.lostconnection.guild;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.story.Conditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * クエスト（データパックの {@code quests/}。Phase 6 その3）。
 *
 * <pre>
 * {
 *   "name": "ツノモフ退治", "kind": "daily", "giver": "amemaru:lilia", "board": true,
 *   "description": "…",
 *   "requires": { "day_min": 1, "level_min": 1, … },    // 会話の条件と同じ形（親密度は giver について見る）
 *   "objectives": [ { "type": "kill", "enemy": "amemaru:tsunomofu", "count": 12 } ],
 *   "report": "board",                                   // 報告する所：board（ギルドの掲示板）か NPC のID。省くと giver（いなければ board）
 *   "rewards": { "knots": 150, "exp": 60, "items": [ { "item": "…", "count": 1 } ], "affinity": { "amemaru:lilia": 10 },
 *                "flags": [ … ], "title": "…", "promote": false },
 *   "repeat": "daily", "pool": "floor1", "order": 10
 * }
 * </pre>
 *
 * <p>目標の種類：{@code kill}（その敵を倒す。パーティで近くにいた仲間にも数える）・{@code kill_any}（どの敵でも）・{@code boss}（その階のボス）・
 * {@code collect}（品を持っている。{@code consume} なら報告のときに渡す）・{@code talk}（そのNPCと話す）・{@code visit}（その場所へ行く。
 * {@code place} は {@code conelia}＝中央広場の真ん中からの位置）・{@code chest}（古代の宝箱を開ける）・{@code record}（古代記録を見つける）・
 * {@code pillar}（石柱を起動する）。</p>
 */
public record QuestDef(ResourceLocation id, String name, Kind kind, @Nullable ResourceLocation giver, boolean board, String description,
                       Conditions requires, int levelMin, List<Objective> objectives, @Nullable ResourceLocation reportTo,
                       Rewards rewards, boolean daily, String pool, int order) {
    /** 目標の数の上限。 */
    public static final int OBJECTIVES_MAX = 12;
    public static final Set<String> OBJECTIVE_TYPES = Set.of("kill", "kill_any", "boss", "collect", "talk", "visit", "chest", "record",
            "pillar");

    /** クエストの種類（掲示板のタブ・並べ方）。 */
    public enum Kind {
        /** 街めぐり（1日目。全施設を回る。Phase 0-A 11章）。 */
        TOWN_TOUR,
        /** デイリー（1日3回。Phase 0-A 13章）。 */
        DAILY,
        /** ギルドの依頼（いつでも受けられる）。 */
        GUILD,
        /** 上位クエスト（5日目から。Phase 0-A 7章）。 */
        UPPER,
        /** 昇格クエスト（11日目・Lv60から（11月24日の2回目に30から変えた）。上位職になる。Phase 0-A 19章）。 */
        PROMOTION,
        /** NPCの個人的な依頼（親密度2・3で。Phase 0-A 14章）。 */
        PERSONAL,
        /** 物語のクエスト（Phase 8）。 */
        STORY;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind parse(String text) {
            for (Kind kind : values()) {
                if (kind.key().equals(text)) {
                    return kind;
                }
            }
            throw new DefinitionException("「kind」は town_tour・daily・guild・upper・promotion・personal・story のどれかにしてください（今は "
                    + text + "）");
        }
    }

    /** 目標。 */
    public record Objective(String type, @Nullable ResourceLocation target, int count, int floor, String label, String place,
                            @Nullable Vec3 pos, double radius, boolean consume) {
        static Objective parse(JsonObject json) {
            String type = Json.string(json, "type");
            if (!OBJECTIVE_TYPES.contains(type)) {
                throw new DefinitionException("目標の「type」は " + String.join("・", OBJECTIVE_TYPES) + " のどれかにしてください（今は " + type + "）");
            }
            ResourceLocation target = switch (type) {
                case "kill" -> Json.id(json, "enemy");
                case "collect" -> Json.id(json, "item");
                case "talk" -> Json.id(json, "npc");
                default -> null;
            };
            Vec3 pos = null;
            if ("visit".equals(type)) {
                JsonArray p = Json.array(json, "pos");
                if (p.size() != 3) {
                    throw new DefinitionException("目標の「pos」は [x, y, z] の3つの数にしてください");
                }
                pos = new Vec3(Json.asNumber(p.get(0), "pos"), Json.asNumber(p.get(1), "pos"), Json.asNumber(p.get(2), "pos"));
            }
            int defaultCount = "talk".equals(type) || "visit".equals(type) || "boss".equals(type) ? 1 : 1;
            return new Objective(type, target, Json.optionalInt(json, "count", defaultCount, 1, 100000),
                    Json.optionalInt(json, "floor", 0, 0, 20), Json.optionalString(json, "label", ""),
                    Json.optionalString(json, "place", "conelia"), pos, Json.optionalDouble(json, "radius", 8.0, 1.0, 64.0),
                    Json.optionalBoolean(json, "consume", false));
        }
    }

    /** 報酬。 */
    public record Rewards(long knots, long exp, List<ItemReward> items, Map<ResourceLocation, Integer> affinity, List<ResourceLocation> flags,
                          @Nullable ResourceLocation title, boolean promote) {
        static final Rewards NONE = new Rewards(0, 0, List.of(), Map.of(), List.of(), null, false);

        static Rewards parse(@Nullable JsonObject json) {
            if (json == null) {
                return NONE;
            }
            List<ItemReward> items = new ArrayList<>();
            JsonArray array = Json.optionalArray(json, "items");
            for (int i = 0; i < array.size(); i++) {
                JsonObject one = Json.asObject(array.get(i), "rewards.items");
                items.add(new ItemReward(Json.id(one, "item"), Json.optionalInt(one, "count", 1, 1, 64)));
            }
            Map<ResourceLocation, Integer> affinity = new LinkedHashMap<>();
            JsonObject a = Json.optionalObject(json, "affinity");
            if (a != null) {
                for (String key : a.keySet()) {
                    affinity.put(Json.parseId(key, "rewards.affinity"), (int) Json.asNumber(a.get(key), "rewards.affinity." + key));
                }
            }
            return new Rewards((long) Json.optionalDouble(json, "knots", 0, 0, 1.0e9), (long) Json.optionalDouble(json, "exp", 0, 0, 1.0e9),
                    List.copyOf(items), Map.copyOf(affinity), Json.idList(json, "flags"), Json.optionalId(json, "title"),
                    Json.optionalBoolean(json, "promote", false));
        }
    }

    public record ItemReward(ResourceLocation item, int count) {
    }

    public static QuestDef parse(ResourceLocation id, JsonObject json) {
        String name = Json.string(json, "name");
        Kind kind = Kind.parse(Json.optionalString(json, "kind", "guild"));
        ResourceLocation giver = Json.optionalId(json, "giver");
        List<Objective> objectives = new ArrayList<>();
        JsonArray array = Json.array(json, "objectives");
        for (int i = 0; i < array.size(); i++) {
            objectives.add(Objective.parse(Json.asObject(array.get(i), "objectives")));
        }
        if (objectives.isEmpty() || objectives.size() > OBJECTIVES_MAX) {
            throw new DefinitionException("「objectives」は1〜" + OBJECTIVES_MAX + "個にしてください");
        }
        String report = Json.optionalString(json, "report", "");
        ResourceLocation reportTo = report.isEmpty() ? giver : "board".equals(report) ? null : Json.parseId(report, "report");
        JsonObject requires = Json.optionalObject(json, "requires");
        String repeat = Json.optionalString(json, "repeat", kind == Kind.DAILY ? "daily" : "none");
        if (!repeat.equals("none") && !repeat.equals("daily")) {
            throw new DefinitionException("「repeat」は none か daily にしてください（今は " + repeat + "）");
        }
        return new QuestDef(id, name, kind, giver, Json.optionalBoolean(json, "board", giver == null || kind != Kind.PERSONAL),
                Json.optionalString(json, "description", ""), Conditions.parse(requires),
                requires == null ? 0 : Json.optionalInt(requires, "level_min", 0, 0, 200), List.copyOf(objectives), reportTo,
                Rewards.parse(Json.optionalObject(json, "rewards")), "daily".equals(repeat), Json.optionalString(json, "pool", ""),
                Json.optionalInt(json, "order", 1000, 0, 100000));
    }
}
