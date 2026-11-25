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
 *   <li>{@code hearths}・{@code memory_statues}：6階「凍結した村」の熱の炉（熱の結晶をもらう所）と記憶の氷像（熱の結晶で溶かすと、
 *       その日の村の短い場面 {@code event} が流れる。Phase 0-A 9章の6階「記憶の解凍」。11月25日）。封印の条件 {@code statues}：
 *       記憶の氷像がすべて溶けた（みんなで同じ。その日のうち）。{@code environment} の {@code sky}（{@code aurora}：オーロラの空）・
 *       {@code weather}（{@code snow}：雪が降る）</li>
 *   <li>{@code light_mazes}：7階「氷の森林」の光の迷路（Phase 0-A 9章の7階「氷の鏡を動かして光を導く迷路」。11月25日）。光の泉
 *       {@code source} から {@code direction} へ伸びる光が、氷の鏡（右クリックで「／」と「＼」が入れ替わる）で折れ、受け石
 *       {@code target} に届くと、{@code door} の箱が開く（みんなで同じ。その日のうち）。封印の条件 {@code lights}：光の迷路を
 *       すべて解いた</li>
 *   <li>{@code passphrase}：合言葉の扉（7階。同じ表の「最奥の合言葉の扉は、パーティ全員が同じ合言葉をチャットに打つと開く」）。
 *       {@code area} の中にいるパーティの全員が、{@code seconds} 秒のうちに同じ言葉をチャットに打つと、その人たちは封印を通れる
 *       （封印の条件 {@code passphrase}。人ごと）</li>
 *   <li>{@code bells}：8階「沈黙の湖」の鐘（Phase 0-A 9章の8階「湖のまわりの鐘を正しい順番で鳴らすと音が戻り、祠への道が開く」。
 *       11月25日）。組ごとに、歌の碑 {@code stele}（触れると今日の順番が出る）・鐘 {@code bells}・制限時間 {@code seconds}・
 *       鳴らせたら開く箱 {@code opens}（ブロック → 空気）と浮かぶ箱 {@code raises}（空気 → ブロック）・開いたままにする所
 *       {@code hold}（人がいる間は閉じない）・音が戻るか {@code sound}。封印の条件 {@code bells}：すべての組を鳴らした（みんな。
 *       その日のうち）。{@code environment} の {@code silent}：音が戻る組を鳴らすまで、階じゅうの音が消えている</li>
 *   <li>{@code lake}：8階の湖（{@code ice_y}：氷の面の高さ。{@code rescue_seconds}：氷の下の水に落ちた人を、何秒で引き上げるか）。
 *       薄氷（2人以上が近くに乗ると割れる）と、敵やボスが割った氷の穴は {@code tower.lake.ThinIceService}</li>
 * </ul>
 */
public record FloorFeatures(@Nullable Environment environment, List<LostWoods> lostWoods, List<Waypoint> waypoints,
                            @Nullable BlockPos travelStone, String sealRequires, List<StoryZone> storyZones,
                            List<Floodgate> floodgates, List<WaterSection> waterSections, List<Mural> murals, List<Song> songs,
                            List<Relic> relics, java.util.Map<String, List<BlockPos>> bossMarks, int abyssY,
                            List<Hearth> hearths, List<MemoryStatue> memoryStatues, List<LightMaze> lightMazes,
                            @Nullable Passphrase passphrase, List<BellGroup> bells, @Nullable Lake lake) {
    /** 奈落のない階の {@link #abyssY}。 */
    public static final int NO_ABYSS = Integer.MIN_VALUE;
    public static final FloorFeatures NONE = new FloorFeatures(null, List.of(), List.of(), null, "pillars", List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), java.util.Map.<String, List<BlockPos>>of(), NO_ABYSS, List.of(), List.of(),
            List.of(), null, List.of(), null);
    /** 11月25日（6階）：記憶の氷像がすべて溶けた（みんなで同じ。その日のうち）。 */
    public static final String SEAL_STATUES = "statues";
    /** 11月25日（7階）：光の迷路をすべて解いた（みんなで同じ。その日のうち）。 */
    public static final String SEAL_LIGHTS = "lights";
    /** 11月25日（7階）：合言葉の扉で、パーティの全員が同じ合言葉を言った（人ごと）。 */
    public static final String SEAL_PASSPHRASE = "passphrase";
    /** 11月25日（8階）：鐘の組をすべて正しい順番で鳴らした（みんなで同じ。その日のうち）。 */
    public static final String SEAL_BELLS = "bells";

    /** 鐘（8階）。{@code mark}：歌の碑に出る印（月・鶴など）、{@code name}：鐘の名前、{@code where}：どこにあるか。 */
    public record Bell(BlockPos pos, String mark, String name, String where) {
    }

    /**
     * 鐘の組（8階）。歌の碑 {@code stele} に触れると、今日の順番（毎日かわる）が出る。最初の鐘から {@code seconds} 秒のうちに、
     * 順番どおりに鳴らすと解け、{@code opens} が開き（空気に）、{@code raises} が浮かぶ（ブロックを置く）。{@code sound}：解けると、
     * 階に音が戻る（environment.silent）。{@code hold}：人がいる間は、日が変わっても閉じない所（なければ null）。
     */
    public record BellGroup(String id, String name, BlockPos stele, int seconds, boolean sound, List<Bell> bells, List<Door> opens,
                            List<Door> raises, @Nullable AABB hold) {
    }

    /** 8階の湖。{@code iceY}：氷の面の高さ（階の座標）。{@code rescueSeconds}：水に落ちた人を引き上げるまでの秒。 */
    public record Lake(int iceY, int rescueSeconds) {
    }

    @Nullable
    public BellGroup bellGroup(String id) {
        for (BellGroup group : bells) {
            if (group.id().equals(id)) {
                return group;
            }
        }
        return null;
    }

    /** 氷の鏡（7階）。{@code slash}：はじめ（朝）の向きが「／」か（でなければ「＼」）。 */
    public record Mirror(BlockPos pos, boolean slash) {
    }

    /** 光の迷路（7階）。 */
    public record LightMaze(String id, String name, BlockPos source, net.minecraft.core.Direction direction, BlockPos target,
                            List<Mirror> mirrors, @Nullable Door door) {
    }

    /** 合言葉の扉（7階）。{@code area}：扉の前の、合言葉を言う所。 */
    public record Passphrase(AABB area, int seconds) {
    }

    @Nullable
    public LightMaze lightMaze(String id) {
        for (LightMaze maze : lightMazes) {
            if (maze.id().equals(id)) {
                return maze;
            }
        }
        return null;
    }

    /** 熱の炉（6階。右クリックで熱の結晶をもらう）。 */
    public record Hearth(String id, String name, BlockPos pos) {
    }

    /** 記憶の氷像（6階。熱の結晶で溶かすと、{@code event} のストーリーイベントが流れる）。 */
    public record MemoryStatue(String id, String name, BlockPos pos, net.minecraft.resources.ResourceLocation event) {
    }

    @Nullable
    public MemoryStatue memoryStatue(String id) {
        for (MemoryStatue statue : memoryStatues) {
            if (statue.id().equals(id)) {
                return statue;
            }
        }
        return null;
    }
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

    /** 霧（クライアントの見え方）。色は 0xRRGGBB（−1 は空の色のまま）。{@code sky}・{@code weather} は 6階から（なければ ""）。 */
    public record Environment(float fogStart, float fogEnd, int fogColor, String sky, String weather, boolean silent) {
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
            String sky = Json.optionalString(e, "sky", "");
            String weather = Json.optionalString(e, "weather", "");
            if (!sky.isEmpty() && !sky.equals("aurora")) {
                throw new DefinitionException("「environment.sky」は aurora にしてください（今は " + sky + "）");
            }
            if (!weather.isEmpty() && !weather.equals("snow")) {
                throw new DefinitionException("「environment.weather」は snow にしてください（今は " + weather + "）");
            }
            env = new Environment((float) Json.asNumber(fog.get(0), "fog"), (float) Json.asNumber(fog.get(1), "fog"), color, sky, weather,
                    Json.optionalBoolean(e, "silent", false));
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
                    && !token.equals(SEAL_FLOODGATES) && !token.equals(SEAL_MURALS) && !token.equals(SEAL_STATUES)
                    && !token.equals(SEAL_LIGHTS) && !token.equals(SEAL_PASSPHRASE) && !token.equals(SEAL_BELLS)) {
                throw new DefinitionException("「seal_requires」は pillars・lost_woods・guardians・floodgates・murals・statues・lights・passphrase・bells を"
                        + " + でつないだものにしてください（今は "
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
        // 11月25日（6階）：熱の炉と記憶の氷像
        List<Hearth> hearths = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "hearths")) {
            JsonObject h = Json.asObject(element, "hearths");
            hearths.add(new Hearth(Json.string(h, "id"), Json.optionalString(h, "name", ""), FloorDef.blockPos(Json.array(h, "pos"), "hearths.pos")));
        }
        List<MemoryStatue> statues = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "memory_statues")) {
            JsonObject m = Json.asObject(element, "memory_statues");
            statues.add(new MemoryStatue(Json.string(m, "id"), Json.optionalString(m, "name", ""),
                    FloorDef.blockPos(Json.array(m, "pos"), "memory_statues.pos"), Json.id(m, "event")));
        }
        if (requires(seal, SEAL_STATUES) && statues.isEmpty()) {
            throw new DefinitionException("「seal_requires」が statues なのに、「memory_statues」がありません");
        }
        if (statues.size() > 16) {
            throw new DefinitionException("「memory_statues」は16までにしてください");
        }
        List<LightMaze> mazes = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "light_mazes")) {
            JsonObject m = Json.asObject(element, "light_mazes");
            String dirText = Json.string(m, "direction");
            net.minecraft.core.Direction dir = net.minecraft.core.Direction.byName(dirText);
            if (dir == null || dir.getAxis().isVertical()) {
                throw new DefinitionException("「light_mazes.direction」は north・south・east・west のどれかにしてください（今は " + dirText + "）");
            }
            List<Mirror> mirrors = new ArrayList<>();
            for (JsonElement me : Json.array(m, "mirrors")) {
                JsonObject mo = Json.asObject(me, "light_mazes.mirrors");
                mirrors.add(new Mirror(FloorDef.blockPos(Json.array(mo, "pos"), "mirrors.pos"), Json.optionalBoolean(mo, "slash", true)));
            }
            if (mirrors.isEmpty() || mirrors.size() > 32) {
                throw new DefinitionException("「light_mazes.mirrors」は1〜32にしてください");
            }
            mazes.add(new LightMaze(Json.string(m, "id"), Json.optionalString(m, "name", ""), FloorDef.blockPos(Json.array(m, "source"),
                    "light_mazes.source"), dir, FloorDef.blockPos(Json.array(m, "target"), "light_mazes.target"), List.copyOf(mirrors),
                    door(Json.optionalObject(m, "door"), "light_mazes.door")));
        }
        if (requires(seal, SEAL_LIGHTS) && mazes.isEmpty()) {
            throw new DefinitionException("「seal_requires」が lights なのに、「light_mazes」がありません");
        }
        Passphrase passphrase = null;
        JsonObject pp = Json.optionalObject(json, "passphrase");
        if (pp != null) {
            passphrase = new Passphrase(box(Json.array(pp, "area"), "passphrase.area"), Json.optionalInt(pp, "seconds", 20, 5, 120));
        }
        if (requires(seal, SEAL_PASSPHRASE) && passphrase == null) {
            throw new DefinitionException("「seal_requires」が passphrase なのに、「passphrase」がありません");
        }
        List<BellGroup> bellGroups = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "bells")) {
            JsonObject g = Json.asObject(element, "bells");
            List<Bell> list = new ArrayList<>();
            for (JsonElement be : Json.array(g, "bells")) {
                JsonObject b = Json.asObject(be, "bells.bells");
                list.add(new Bell(FloorDef.blockPos(Json.array(b, "pos"), "bells.pos"), Json.string(b, "mark"), Json.string(b, "name"),
                        Json.optionalString(b, "where", "")));
            }
            if (list.size() < 2 || list.size() > 8) {
                throw new DefinitionException("「bells.bells」は2〜8にしてください（" + Json.string(g, "id") + "）");
            }
            List<Door> opens = new ArrayList<>();
            for (JsonElement oe : Json.optionalArray(g, "opens")) {
                opens.add(door(Json.asObject(oe, "bells.opens"), "bells.opens"));
            }
            List<Door> raises = new ArrayList<>();
            for (JsonElement re : Json.optionalArray(g, "raises")) {
                raises.add(door(Json.asObject(re, "bells.raises"), "bells.raises"));
            }
            JsonArray holdArray = Json.optionalArray(g, "hold");
            bellGroups.add(new BellGroup(Json.string(g, "id"), Json.optionalString(g, "name", ""),
                    FloorDef.blockPos(Json.array(g, "stele"), "bells.stele"), Json.optionalInt(g, "seconds", 120, 10, 900),
                    Json.optionalBoolean(g, "sound", false), List.copyOf(list), List.copyOf(opens), List.copyOf(raises),
                    holdArray.size() == 6 ? box(holdArray, "bells.hold") : null));
        }
        if (requires(seal, SEAL_BELLS) && bellGroups.isEmpty()) {
            throw new DefinitionException("「seal_requires」が bells なのに、「bells」がありません");
        }
        Lake lake = null;
        JsonObject lk = Json.optionalObject(json, "lake");
        if (lk != null) {
            lake = new Lake(Json.optionalInt(lk, "ice_y", 24, 1, 300), Json.optionalInt(lk, "rescue_seconds", 4, 1, 60));
        }
        return new FloorFeatures(env, List.copyOf(woods), List.copyOf(waypoints), travel, seal, List.copyOf(zones), List.copyOf(gates),
                List.copyOf(sections), List.copyOf(murals), List.copyOf(songs), List.copyOf(relics), java.util.Map.copyOf(marks), abyss,
                List.copyOf(hearths), List.copyOf(statues), List.copyOf(mazes), passphrase, List.copyOf(bellGroups), lake);
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
