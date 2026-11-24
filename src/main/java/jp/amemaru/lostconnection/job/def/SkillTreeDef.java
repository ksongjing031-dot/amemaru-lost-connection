package jp.amemaru.lostconnection.job.def;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 職業のスキルツリー（11月24日のご指示「ステータスもスキルツリーで一緒に振る形にして、すべてのスキルが取れるではなく
 * ビルドによって変化するように」。{@code data/amemaru/amemaru/skill_trees/<職業>.json}。tools/datagen/build_skill_trees.py が書き出す）。
 *
 * <p>節は4種類：{@code core}（職業の核。最初から取れている）・{@code skill}（スキル。段階はスキルの定義のとおり）・{@code stat}
 * （ステータスの小さな節。1ポイント）・{@code notable}（名前のある大きな節。%で上がるものもある。1ポイント）。節を取れるのは、
 * つながった（{@code links}）となりの節が取れているとき。</p>
 */
public final class SkillTreeDef {
    /** 節の種類。 */
    public enum Kind {
        CORE("core"),
        SKILL("skill"),
        STAT("stat"),
        NOTABLE("notable");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            throw new DefinitionException("節の「kind」は core・skill・stat・notable のどれかにしてください（今は " + id + "）");
        }
    }

    /**
     * 節。
     *
     * @param flat    上がるステータス（数）
     * @param percent 上がるステータス（%）
     * @param cost    取るのに使うポイント（ステータスの節・大きな節。スキルの節はスキルの段階の定義）
     */
    public record Node(String id, Kind kind, float x, float y, @Nullable ResourceLocation skill, boolean start, String name,
                       Map<StatType, Double> flat, Map<StatType, Double> percent, int cost) {
        /** ステータスの節か（小さな節・大きな節）。 */
        public boolean isStatNode() {
            return kind == Kind.STAT || kind == Kind.NOTABLE;
        }
    }

    /** ステータスの節・大きな節を取るのに使うポイント。 */
    public static final int STAT_NODE_COST = 1;

    private final ResourceLocation job;
    private final List<Node> nodes;
    private final Map<String, Node> byId;
    private final Map<String, List<String>> neighbors;
    private final Map<ResourceLocation, Node> bySkill;
    private final List<String[]> links;

    private SkillTreeDef(ResourceLocation job, List<Node> nodes, List<String[]> links) {
        this.job = job;
        this.nodes = List.copyOf(nodes);
        Map<String, Node> ids = new LinkedHashMap<>();
        Map<ResourceLocation, Node> skills = new HashMap<>();
        for (Node node : nodes) {
            ids.put(node.id(), node);
            if (node.skill() != null) {
                skills.put(node.skill(), node);
            }
        }
        this.byId = Collections.unmodifiableMap(ids);
        this.bySkill = Collections.unmodifiableMap(skills);
        Map<String, List<String>> adj = new HashMap<>();
        for (String[] link : links) {
            adj.computeIfAbsent(link[0], k -> new ArrayList<>()).add(link[1]);
            adj.computeIfAbsent(link[1], k -> new ArrayList<>()).add(link[0]);
        }
        adj.replaceAll((k, v) -> List.copyOf(v));
        this.neighbors = Collections.unmodifiableMap(adj);
        this.links = List.copyOf(links);
    }

    /** JSON から読む（{@code job} がなければ、ファイルの名前を職業にする）。 */
    public static SkillTreeDef parse(ResourceLocation fileId, JsonObject json) {
        ResourceLocation job = json.has("job") ? Json.id(json, "job") : fileId;
        List<Node> nodes = new ArrayList<>();
        Map<String, Node> ids = new HashMap<>();
        JsonArray array = Json.array(json, "nodes");
        boolean core = false;
        for (int i = 0; i < array.size(); i++) {
            JsonObject o = array.get(i).getAsJsonObject();
            String where = "nodes[" + i + "]：";
            String id = Json.string(o, "id");
            Kind kind = Kind.byId(Json.string(o, "kind"));
            float x = (float) Json.doubleInRange(o, "x", -5000, 5000);
            float y = (float) Json.doubleInRange(o, "y", -5000, 5000);
            ResourceLocation skill = null;
            if (kind == Kind.SKILL) {
                skill = Json.id(o, "skill");
            }
            Map<StatType, Double> flat = stats(o, "stats", where);
            Map<StatType, Double> percent = stats(o, "percent", where);
            if (kind == Kind.STAT && flat.isEmpty()) {
                throw new DefinitionException(where + "ステータスの節（stat）に「stats」がありません");
            }
            if (kind == Kind.NOTABLE && flat.isEmpty() && percent.isEmpty()) {
                throw new DefinitionException(where + "大きな節（notable）に「stats」も「percent」もありません");
            }
            core |= kind == Kind.CORE;
            Node node = new Node(id, kind, x, y, skill, Json.optionalBoolean(o, "start", false), Json.optionalString(o, "name", ""),
                    flat, percent, kind == Kind.STAT || kind == Kind.NOTABLE ? STAT_NODE_COST : 0);
            if (ids.put(id, node) != null) {
                throw new DefinitionException(where + "節の名前「" + id + "」が2つあります");
            }
            nodes.add(node);
        }
        if (!core) {
            throw new DefinitionException("職業の核（kind が core の節）がありません");
        }
        List<String[]> links = new ArrayList<>();
        JsonArray linkArray = Json.array(json, "links");
        for (int i = 0; i < linkArray.size(); i++) {
            JsonArray pair = linkArray.get(i).getAsJsonArray();
            if (pair.size() != 2) {
                throw new DefinitionException("links[" + i + "]：つながりは2つの節の名前にしてください");
            }
            String a = pair.get(0).getAsString();
            String b = pair.get(1).getAsString();
            if (!ids.containsKey(a) || !ids.containsKey(b)) {
                throw new DefinitionException("links[" + i + "]：ない節「" + (ids.containsKey(a) ? b : a) + "」につないでいます");
            }
            links.add(new String[]{a, b});
        }
        return new SkillTreeDef(job, nodes, links);
    }

    private static Map<StatType, Double> stats(JsonObject o, String key, String where) {
        if (!o.has(key)) {
            return Map.of();
        }
        Map<StatType, Double> out = new EnumMap<>(StatType.class);
        for (Map.Entry<String, JsonElement> entry : o.getAsJsonObject(key).entrySet()) {
            StatType type = StatType.byId(entry.getKey()).orElse(null);
            if (type == null) {
                throw new DefinitionException(where + "「" + key + "」に、ないステータス「" + entry.getKey() + "」があります");
            }
            double value = entry.getValue().getAsDouble();
            if (value <= 0 || value > 1000) {
                throw new DefinitionException(where + "「" + key + "." + entry.getKey() + "」は0より大きく1000以下にしてください");
            }
            out.put(type, value);
        }
        return Collections.unmodifiableMap(out);
    }

    public ResourceLocation job() {
        return job;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<String[]> links() {
        return links;
    }

    @Nullable
    public Node node(String id) {
        return byId.get(id);
    }

    /** そのスキルの節（ツリーにないスキルなら null）。 */
    @Nullable
    public Node nodeOf(ResourceLocation skill) {
        return bySkill.get(skill);
    }

    /** となりの節の名前。 */
    public List<String> neighbors(String id) {
        return neighbors.getOrDefault(id, List.of());
    }

    /** 職業の核。 */
    public Node core() {
        for (Node node : nodes) {
            if (node.kind() == Kind.CORE) {
                return node;
            }
        }
        throw new IllegalStateException("core");
    }
}
