package jp.amemaru.lostconnection.tower;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
 *   <li>{@code story_zones}：物語のきっかけになる所（{@code id}・{@code box}）。人が入ると、ストーリーイベントの
 *       きっかけ {@code floor_zone}（{@code zone} が同じもの）が起きる。2階の「霧の奥でノアが初めて目撃される」など</li>
 *   <li>{@code murals}：壁画パズル（4階のギミック。Phase 0-A 9章「回転するパネルを正しく並べると、第一時代から第二時代までの
 *       歴史が一枚の絵になる」）。{@code songs}：音の順番の仕掛け（同じ表の「音の順番」）。{@code relics}：物語の品を拾う所
 *       （4階の結晶騎士団の遺品）。{@code boss_marks}：ボスが使う場所の組（4階の音の床・5階の結び台など。名前 → 場所の並び）。
 *       {@code abyss_y}：奈落（5階。階の中のこの高さより下へ落ちた人は、中継の灯か入口へ戻される）</li>
 * </ul>
 */
public record FloorFeatures(@Nullable Environment environment, List<LostWoods> lostWoods, List<Waypoint> waypoints,
                            @Nullable BlockPos travelStone, String sealRequires, List<StoryZone> storyZones,
                            List<Floodgate> floodgates, List<WaterSection> waterSections, List<Mural> murals, List<Song> songs,
                            List<Relic> relics, java.util.Map<String, List<BlockPos>> bossMarks, int abyssY) {
    /** 奈落のない階の {@link #abyssY}。 */
    public static final int NO_ABYSS = Integer.MIN_VALUE;
    public static final FloorFeatures NONE = new FloorFeatures(null, List.of(), List.of(), null, "pillars", List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), java.util.Map.<String, List<BlockPos>>of(), NO_ABYSS);
    /** 11月24日（4階）：その階の壁画をすべて元に戻した（みんなで同じ。その日のうち）。 */
    public static final String SEAL_MURALS = "murals";

    /**
     * 壁画（4階）。{@code panels} の向き（0〜3）がすべて {@code answer} にそろうと、元の絵に戻る。そろうと {@code door} の箱を
     * 開け（ブロックを空気に）、{@code text} の歴史の一文を、近くの人に出す。{@code display} は、すべての壁画がそろうと灯る
     * 「一枚の絵」（向きは変わらない。どの壁画の組にも入れない）。
     */
    public record Mural(String id, String name, List<Panel> panels, String text, @Nullable Door door, boolean display) {
    }

    /** 壁画のパネル。{@code piece} は絵のどの部分か（ブロックの絵の番号）、{@code answer} は正しい向き。 */
    public record Panel(BlockPos pos, int piece, int answer) {
    }

    /**
     * 音の順番の仕掛け（4階）。{@code chime} の石を鳴らすと、{@code length} 個の音の順番を鳴らす。{@code plates} の音の床
     * （番号が音）を、同じ順番で踏むと、{@code door} が開く（みんなの分。その日のうち）。
     */
    public record Song(String id, String name, BlockPos chime, List<BlockPos> plates, int length, @Nullable Door door) {
    }

    /**
     * 物語の品を拾う所（4階の結晶騎士団の遺品）。右クリックで {@code items} を受け取り、{@code flag} が立つ（人ごとに1回）。
     * {@code event} はそのとき流すストーリーイベント（なければ ""）。
     */
    /**
     * 物語の品の置き場所（11月24日：4階の結晶騎士団の遺品）。右クリックで {@code items} をもらい、{@code flag} が立ち、{@code event} の
     * ストーリーイベントが流れ、{@code quest} のクエストが始まる（なければ始めない）。
     */
    public record Relic(String id, BlockPos pos, List<net.minecraft.resources.ResourceLocation> items, net.minecraft.resources.ResourceLocation flag,
                        String event, @Nullable net.minecraft.resources.ResourceLocation quest) {
    }

    @Nullable
    public Mural mural(String id) {
        for (Mural mural : murals) {
            if (mural.id().equals(id)) {
                return mural;
            }
        }
        return null;
    }

    @Nullable
    public Song song(String id) {
        for (Song song : songs) {
            if (song.id().equals(id)) {
                return song;
            }
        }
        return null;
    }

    @Nullable
    public Relic relic(String id) {
        for (Relic relic : relics) {
            if (relic.id().equals(id)) {
                return relic;
            }
        }
        return null;
    }

    /** ボスが使う場所の組（なければ空）。 */
    public List<BlockPos> bossMarks(String name) {
        return bossMarks.getOrDefault(name, List.of());
    }
    public static final String SEAL_PILLARS = "pillars";
    public static final String SEAL_LOST_WOODS = "lost_woods";
    /**
     * 11月24日のご指示「時間が掛からない」【補完】：その階の名のある魔物（宝箱の守り手）をすべて倒した印（人ごと。日が変わっても
     * 消えない）。{@code lost_woods+guardians} のように、{@code +} でつないで、いくつも求められる。
     */
    public static final String SEAL_GUARDIANS = "guardians";
    /** Phase 8 その2（3階）：その階の水門（ボスの部屋の排水 {@link #DRAIN} をのぞく）がすべて開いた（みんなで同じ。その日のうち）。 */
    public static final String SEAL_FLOODGATES = "floodgates";
    /** ボスの部屋の排水のハンドル（ボスが使う。開いたままにならない）。 */
    public static final String DRAIN = "drain";

    /**
     * 水門の組（Phase 8 その2：3階のギミック。Phase 0-A 9章「離れた部屋の水門を、複数人が声をかけ合って同時に操作し、水の流れを
     * 変える」）。{@code valves} の2つのハンドルを、決まった秒数のうちに両方回すと開き、{@code drains} の水の区画の水が引く。
     * {@code after} は、先に開いていないと回せない水門（水の底のハンドルを、泳いで回せないように。なければ ""）【補完】。
     * {@code doors} は、開くと上がる扉（水門の扉。水を引いたあとで消え、閉じるとき水を戻す前に戻る）。
     */
    public record Floodgate(String id, String name, List<BlockPos> valves, List<String> drains, String after, List<Door> doors) {
    }

    /** 水門の扉（{@code min}〜{@code max} の箱を、{@code block} のブロックでふさぐ。階の座標）。 */
    public record Door(BlockPos min, BlockPos max, net.minecraft.resources.ResourceLocation block) {
    }

    /** 水の区画。{@code min}〜{@code max}（階の座標）の中の、高さ {@code low} より上・{@code high} までの水を、引く・戻す。 */
    public record WaterSection(String id, BlockPos min, BlockPos max, int low, int high) {
    }

    @Nullable
    public Floodgate floodgate(String id) {
        for (Floodgate gate : floodgates) {
            if (gate.id().equals(id)) {
                return gate;
            }
        }
        return null;
    }

    @Nullable
    public WaterSection waterSection(String id) {
        for (WaterSection section : waterSections) {
            if (section.id().equals(id)) {
                return section;
            }
        }
        return null;
    }

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

    /** 物語のきっかけになる所。 */
    public record StoryZone(String id, AABB box) {
    }

    /** 中継の灯。{@code arrive} は、道しるべの石から来たときに立つ所。 */
    public record Waypoint(int index, String id, String name, BlockPos pos, FloorDef.Spot arrive) {
    }

    /** 封印を通るのに、その条件（{@link #SEAL_PILLARS} など）が要るか。 */
    public boolean requires(String token) {
        return requires(sealRequires, token);
    }

    private static boolean requires(String seal, String token) {
        for (String t : seal.split("\\+")) {
            if (t.equals(token)) {
                return true;
            }
        }
        return false;
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
        for (String token : seal.split("\\+")) {
            if (!token.equals(SEAL_PILLARS) && !token.equals(SEAL_LOST_WOODS) && !token.equals(SEAL_GUARDIANS)
                    && !token.equals(SEAL_FLOODGATES) && !token.equals(SEAL_MURALS)) {
                throw new DefinitionException("「seal_requires」は pillars・lost_woods・guardians・floodgates・murals を + でつないだものにしてください（今は "
                        + seal + "）");
            }
        }
        List<WaterSection> sections = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "water_sections")) {
            JsonObject w = Json.asObject(element, "water_sections");
            JsonArray b = Json.array(w, "box");
            if (b.size() != 6) {
                throw new DefinitionException("「water_sections.box」は [x0, y0, z0, x1, y1, z1] の6つの数にしてください");
            }
            int[] v = new int[6];
            for (int i = 0; i < 6; i++) {
                v[i] = (int) Math.floor(Json.asNumber(b.get(i), "water_sections.box"));
            }
            sections.add(new WaterSection(Json.string(w, "id"), new BlockPos(Math.min(v[0], v[3]), Math.min(v[1], v[4]), Math.min(v[2], v[5])),
                    new BlockPos(Math.max(v[0], v[3]), Math.max(v[1], v[4]), Math.max(v[2], v[5])),
                    Json.intInRange(w, "low", -64, 512), Json.intInRange(w, "high", -64, 512)));
        }
        List<Floodgate> gates = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "floodgates")) {
            JsonObject g = Json.asObject(element, "floodgates");
            JsonArray valveArray = Json.array(g, "valves");
            if (valveArray.size() != 2) {
                throw new DefinitionException("「floodgates.valves」は、ハンドル2つの位置にしてください");
            }
            List<BlockPos> valves = new ArrayList<>();
            for (JsonElement v : valveArray) {
                valves.add(FloorDef.blockPos(v.getAsJsonArray(), "floodgates.valves"));
            }
            List<String> drains = new ArrayList<>();
            for (JsonElement d : Json.optionalArray(g, "drains")) {
                String id = d.getAsString();
                boolean known = false;
                for (WaterSection section : sections) {
                    known |= section.id().equals(id);
                }
                if (!known) {
                    throw new DefinitionException("「floodgates.drains」の " + id + " が「water_sections」にありません");
                }
                drains.add(id);
            }
            List<Door> doors = new ArrayList<>();
            for (JsonElement d : Json.optionalArray(g, "doors")) {
                JsonObject door = Json.asObject(d, "floodgates.doors");
                JsonArray b = Json.array(door, "box");
                if (b.size() != 6) {
                    throw new DefinitionException("「floodgates.doors.box」は [x0, y0, z0, x1, y1, z1] の6つの数にしてください");
                }
                int[] v = new int[6];
                for (int i = 0; i < 6; i++) {
                    v[i] = (int) Math.floor(Json.asNumber(b.get(i), "floodgates.doors.box"));
                }
                doors.add(new Door(new BlockPos(Math.min(v[0], v[3]), Math.min(v[1], v[4]), Math.min(v[2], v[5])),
                        new BlockPos(Math.max(v[0], v[3]), Math.max(v[1], v[4]), Math.max(v[2], v[5])), Json.id(door, "block")));
            }
            gates.add(new Floodgate(Json.string(g, "id"), Json.optionalString(g, "name", ""), List.copyOf(valves), List.copyOf(drains),
                    Json.optionalString(g, "after", ""), List.copyOf(doors)));
        }
        for (Floodgate gate : gates) {
            if (!gate.after().isEmpty() && gates.stream().noneMatch(other -> other.id().equals(gate.after()))) {
                throw new DefinitionException("「floodgates.after」の " + gate.after() + " が「floodgates」にありません");
            }
        }
        if (requires(seal, SEAL_FLOODGATES) && gates.isEmpty()) {
            throw new DefinitionException("「seal_requires」が floodgates なのに、「floodgates」がありません");
        }
        if (requires(seal, SEAL_LOST_WOODS) && woods.isEmpty()) {
            throw new DefinitionException("「seal_requires」が lost_woods なのに、「lost_woods」がありません");
        }
        List<StoryZone> zones = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "story_zones")) {
            JsonObject z = Json.asObject(element, "story_zones");
            zones.add(new StoryZone(Json.string(z, "id"), box(Json.array(z, "box"), "story_zones.box")));
        }
        // 11月24日（4階）：壁画・音の順番・物語の品・ボスの場所
        List<Mural> murals = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "murals")) {
            JsonObject m = Json.asObject(element, "murals");
            List<Panel> panels = new ArrayList<>();
            for (JsonElement pe : Json.array(m, "panels")) {
                JsonObject p = Json.asObject(pe, "murals.panels");
                panels.add(new Panel(FloorDef.blockPos(Json.array(p, "pos"), "murals.panels.pos"), Json.intInRange(p, "piece", 0, 63),
                        Json.optionalInt(p, "answer", 0, 0, 3)));
            }
            if (panels.isEmpty()) {
                throw new DefinitionException("壁画「" + Json.string(m, "id") + "」にパネルがありません");
            }
            murals.add(new Mural(Json.string(m, "id"), Json.optionalString(m, "name", ""), List.copyOf(panels),
                    Json.optionalString(m, "text", ""), door(Json.optionalObject(m, "door"), "murals.door"),
                    Json.optionalBoolean(m, "display", false)));
        }
        List<Song> songs = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "songs")) {
            JsonObject g = Json.asObject(element, "songs");
            List<BlockPos> plates = new ArrayList<>();
            for (JsonElement pe : Json.array(g, "plates")) {
                plates.add(FloorDef.blockPos(pe.getAsJsonArray(), "songs.plates"));
            }
            if (plates.size() < 2 || plates.size() > 8) {
                throw new DefinitionException("音の順番「" + Json.string(g, "id") + "」の音の床は2〜8にしてください");
            }
            songs.add(new Song(Json.string(g, "id"), Json.optionalString(g, "name", ""), FloorDef.blockPos(Json.array(g, "chime"), "songs.chime"),
                    List.copyOf(plates), Json.optionalInt(g, "length", 3, 2, 8), door(Json.optionalObject(g, "door"), "songs.door")));
        }
        List<Relic> relics = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "relics")) {
            JsonObject r = Json.asObject(element, "relics");
            relics.add(new Relic(Json.string(r, "id"), FloorDef.blockPos(Json.array(r, "pos"), "relics.pos"), Json.idList(r, "items"),
                    Json.id(r, "flag"), Json.optionalString(r, "event", ""), Json.optionalId(r, "quest")));
        }
        java.util.Map<String, List<BlockPos>> marks = new java.util.LinkedHashMap<>();
        JsonObject markObject = Json.optionalObject(json, "boss_marks");
        if (markObject != null) {
            for (String key : markObject.keySet()) {
                List<BlockPos> list = new ArrayList<>();
                for (JsonElement pe : Json.array(markObject, key)) {
                    list.add(FloorDef.blockPos(pe.getAsJsonArray(), "boss_marks." + key));
                }
                marks.put(key, List.copyOf(list));
            }
        }
        if (requires(seal, SEAL_MURALS) && murals.stream().noneMatch(m -> !m.display())) {
            throw new DefinitionException("「seal_requires」が murals なのに、「murals」がありません");
        }
        // 11月24日（5階）：奈落。階の中のこの高さ（y）より下へ落ちた人は、ともした中継の灯（なければ入口）へ戻される
        int abyss = json.has("abyss_y") ? Json.optionalInt(json, "abyss_y", 0, -64, 384) : NO_ABYSS;
        return new FloorFeatures(env, List.copyOf(woods), List.copyOf(waypoints), travel, seal, List.copyOf(zones), List.copyOf(gates),
                List.copyOf(sections), List.copyOf(murals), List.copyOf(songs), List.copyOf(relics), java.util.Map.copyOf(marks), abyss);
    }

    @Nullable
    private static Door door(@Nullable JsonObject door, String name) {
        if (door == null) {
            return null;
        }
        JsonArray b = Json.array(door, "box");
        if (b.size() != 6) {
            throw new DefinitionException("「" + name + ".box」は [x0, y0, z0, x1, y1, z1] の6つの数にしてください");
        }
        int[] v = new int[6];
        for (int i = 0; i < 6; i++) {
            v[i] = (int) Math.floor(Json.asNumber(b.get(i), name + ".box"));
        }
        return new Door(new BlockPos(Math.min(v[0], v[3]), Math.min(v[1], v[4]), Math.min(v[2], v[5])),
                new BlockPos(Math.max(v[0], v[3]), Math.max(v[1], v[4]), Math.max(v[2], v[5])), Json.id(door, "block"));
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
