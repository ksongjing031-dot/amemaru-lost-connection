package jp.amemaru.lostconnection.tower;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 8 で足した、階の仕組み（11月23日の決定「ダンジョンは一層上がるたびに広く、複雑に、難しく」と「後半はかなりの時間が
 * かかってもよい」から）。どれも無くてよい（1階は使わない）。座標は {@link FloorDef} と同じく、階の (0, 0, 0) から。
 *
 * <ul>
 *   <li>{@code environment}：霧の近さと遠さ・色（{@code fog}：[始まり, 終わり]、{@code fog_color}："#RRGGBB"）</li>
 *   <li>{@code lost_woods}：迷いの森（2階のギミック。Phase 0-A 9章）。区画ごとに、出口・鐘・結び目の灯台・戻される所</li>
 *   <li>{@code waypoints}：中継の灯（広い階の途中から入れる所）と、入口の道しるべの石（{@code travel_stone}）</li>
 *   <li>{@code seal_requires}：ボスの部屋の封印を通れる条件（{@code pillars}：石柱をすべて起動した人。1階のまま／
 *       {@code lost_woods}：迷いの森の区画がすべてつながったら、みんな）</li>
 * </ul>
 */
public record FloorFeatures(@Nullable Environment environment, List<LostWoods> lostWoods, List<Waypoint> waypoints,
                            @Nullable BlockPos travelStone, String sealRequires) {
    public static final FloorFeatures NONE = new FloorFeatures(null, List.of(), List.of(), null, "pillars");
    public static final String SEAL_PILLARS = "pillars";
    public static final String SEAL_LOST_WOODS = "lost_woods";

    /** 霧（クライアントの見え方）。色は 0xRRGGBB（−1 は空の色のまま）。 */
    public record Environment(float fogStart, float fogEnd, int fogColor) {
    }

    /** 迷いの森の出口。{@code box} に出たとき、正しい出口でなく、または区画がまだつながっていなければ、{@code entry} へ戻される。 */
    public record Exit(String id, AABB box, BlockPos post, boolean correct) {
    }

    /**
     * 迷いの森の区画。{@code box} の中から出口へ出ると戻される（正しい出口の灯台に結び目の灯を置くまで）。鐘を鳴らすと、
     * {@code echoDelay} 秒あとに正しい出口からこだまが返る（鐘から {@code listenDistance} 以上離れた人に方角が分かる）。
     * {@code decoys} は、まぎらわしい低いこだまの数（奥の区画ほど多い）。
     */
    public record LostWoods(String id, String name, AABB box, FloorDef.Spot entry, BlockPos bell, double listenDistance,
                            int echoDelay, int decoys, List<Exit> exits) {
        @Nullable
        public Exit correctExit() {
            for (Exit exit : exits) {
                if (exit.correct()) {
                    return exit;
                }
            }
            return null;
        }
    }

    /** 中継の灯。{@code arrive} は、道しるべの石から来たときに立つ所。 */
    public record Waypoint(int index, String id, String name, BlockPos pos, FloorDef.Spot arrive) {
    }

    @Nullable
    public LostWoods lostWoods(String id) {
        for (LostWoods woods : lostWoods) {
            if (woods.id().equals(id)) {
                return woods;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 読む

    public static FloorFeatures parse(JsonObject json) {
        Environment env = null;
        JsonObject e = Json.optionalObject(json, "environment");
        if (e != null) {
            JsonArray fog = Json.array(e, "fog");
            if (fog.size() != 2) {
                throw new DefinitionException("「environment.fog」は [始まり, 終わり] の2つの数にしてください");
            }
            int color = -1;
            String text = Json.optionalString(e, "fog_color", "");
            if (!text.isEmpty()) {
                try {
                    color = Integer.parseInt(text.startsWith("#") ? text.substring(1) : text, 16);
                } catch (NumberFormatException ex) {
                    throw new DefinitionException("「environment.fog_color」は #RRGGBB で書いてください");
                }
            }
            env = new Environment((float) Json.asNumber(fog.get(0), "fog"), (float) Json.asNumber(fog.get(1), "fog"), color);
        }
        List<LostWoods> woods = new ArrayList<>();
        JsonArray woodsArray = Json.optionalArray(json, "lost_woods");
        for (int i = 0; i < woodsArray.size(); i++) {
            JsonObject w = Json.asObject(woodsArray.get(i), "lost_woods");
            List<Exit> exits = new ArrayList<>();
            JsonArray exitArray = Json.array(w, "exits");
            int correct = 0;
            for (int k = 0; k < exitArray.size(); k++) {
                JsonObject x = Json.asObject(exitArray.get(k), "lost_woods.exits");
                Exit exit = new Exit(Json.string(x, "id"), box(Json.array(x, "box"), "exits.box"),
                        FloorDef.blockPos(Json.array(x, "post"), "exits.post"), x.has("correct") && x.get("correct").getAsBoolean());
                correct += exit.correct() ? 1 : 0;
                exits.add(exit);
            }
            if (correct != 1) {
                throw new DefinitionException("迷いの森「" + Json.string(w, "id") + "」の正しい出口は1つにしてください（" + correct + "）");
            }
            JsonObject entry = Json.object(w, "entry");
            woods.add(new LostWoods(Json.string(w, "id"), Json.string(w, "name"), box(Json.array(w, "box"), "lost_woods.box"),
                    new FloorDef.Spot(FloorDef.vec(Json.array(entry, "pos"), "entry.pos"),
                            (float) Json.optionalDouble(entry, "yaw", 0.0, -360.0, 360.0)),
                    FloorDef.blockPos(Json.array(w, "bell"), "lost_woods.bell"), Json.optionalDouble(w, "listen_distance", 12.0, 2.0, 128.0),
                    Json.optionalInt(w, "echo_delay", 3, 0, 30), Json.optionalInt(w, "decoys", 0, 0, 8), List.copyOf(exits)));
        }
        List<Waypoint> waypoints = new ArrayList<>();
        JsonArray wpArray = Json.optionalArray(json, "waypoints");
        for (int i = 0; i < wpArray.size(); i++) {
            JsonObject p = Json.asObject(wpArray.get(i), "waypoints");
            JsonObject arrive = Json.object(p, "arrive");
            waypoints.add(new Waypoint(i, Json.string(p, "id"), Json.string(p, "name"), FloorDef.blockPos(Json.array(p, "pos"), "waypoints.pos"),
                    new FloorDef.Spot(FloorDef.vec(Json.array(arrive, "pos"), "arrive.pos"),
                            (float) Json.optionalDouble(arrive, "yaw", 0.0, -360.0, 360.0))));
        }
        JsonArray stone = Json.optionalArray(json, "travel_stone");
        BlockPos travel = stone.size() == 3 ? FloorDef.blockPos(stone, "travel_stone") : null;
        String seal = Json.optionalString(json, "seal_requires", SEAL_PILLARS);
        if (!seal.equals(SEAL_PILLARS) && !seal.equals(SEAL_LOST_WOODS)) {
            throw new DefinitionException("「seal_requires」は pillars か lost_woods にしてください");
        }
        if (seal.equals(SEAL_LOST_WOODS) && woods.isEmpty()) {
            throw new DefinitionException("「seal_requires」が lost_woods なのに、「lost_woods」がありません");
        }
        return new FloorFeatures(env, List.copyOf(woods), List.copyOf(waypoints), travel, seal);
    }

    private static AABB box(JsonArray array, String name) {
        if (array.size() != 6) {
            throw new DefinitionException("「" + name + "」は [x0, y0, z0, x1, y1, z1] の6つの数にしてください");
        }
        double[] v = new double[6];
        for (int i = 0; i < 6; i++) {
            v[i] = Json.asNumber(array.get(i), name);
        }
        return new AABB(Math.min(v[0], v[3]), Math.min(v[1], v[4]), Math.min(v[2], v[5]),
                Math.max(v[0], v[3]) + 1, Math.max(v[1], v[4]) + 1, Math.max(v[2], v[5]) + 1);
    }

    /** 階の (0, 0, 0) から見た箱に、世界の位置が入っているか。 */
    public static boolean inside(AABB box, Vec3 world, Vec3 origin) {
        return box.contains(world.x - origin.x, world.y - origin.y, world.z - origin.z);
    }
}
