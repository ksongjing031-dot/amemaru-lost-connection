package jp.amemaru.lostconnection.tower;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 塔の階の定義（データパックの {@code floors/}。Phase 0-B 6章「floors/：階の番号、章、名前、範囲と入口、敵の配置、ギミック、ボス、
 * レイドの設定（最大人数・人数補正）、古代記録、推奨レベル」）。
 *
 * <p>階は独自のディメンション {@code amemaru:tower} の、離れた座標に建てる（Phase 0-B 2章。N階は x = N × 4096 のあたり）。
 * 座標はすべて、階の構造物の (0, 0, 0)（{@link #origin}）からの位置で書く。階の形（構造物・石柱・封印・ボスの部屋・湧く所）は
 * マップの設計データの道具（{@code tools/mapgen/build_floor1.py}）が、構造物と一緒にこのファイルへ書き出す。</p>
 */
public record FloorDef(
        ResourceLocation id,
        int number,
        int chapter,
        String name,
        String chapterTitle,
        int recommendedMin,
        int recommendedMax,
        BlockPos origin,
        int sizeX,
        int sizeY,
        int sizeZ,
        String structurePrefix,
        int pieceSize,
        Spot entrance,
        Circle returnCircle,
        @Nullable BossRoomDef bossRoom,
        List<PillarDef> pillars,
        String pillarsDoneMessage,
        List<SpawnZone> spawns,
        @Nullable Tablet tablet,
        Extras extras) {

    /** 足もとの位置と向き（yaw 0 が南、90 が西、180 が北、−90 が東）。 */
    public record Spot(Vec3 pos, float yaw) {
    }

    /** 床に描いた円（立つと移る所）。y は足もとの高さ。 */
    public record Circle(Vec3 center, double radius) {
        public boolean contains(Vec3 world, Vec3 origin) {
            double dx = world.x - (origin.x + center.x);
            double dz = world.z - (origin.z + center.z);
            double dy = world.y - (origin.y + center.y);
            return dx * dx + dz * dz <= radius * radius && dy > -1.0 && dy < 2.5;
        }
    }

    /**
     * 起動する石柱。{@code line} は触れたときに出す古代文字の一行。{@code requires} は起動する前の課題（なければ、触れるとすぐ
     * 起動する。11月22日【補完】）。
     */
    public record PillarDef(int index, BlockPos pos, String line, @Nullable Requirement requires) {
    }

    /**
     * 石柱を起動する前の課題（11月22日【補完】。一日一層には中身が薄いというご指示から）。
     * <ul>
     *   <li>{@code guardian}：守り手（{@link Guardian} の {@code id}）を倒した人とそのパーティだけが起動できる</li>
     *   <li>{@code items}：{@code item} を {@code count} 個持って触れると、それを使って起動できる</li>
     * </ul>
     * 門の奥にある石柱（仕掛け石で門をひらく）は、課題なし（門そのものが課題）。{@code hint} は、まだ起動できないときに出す一言。
     */
    public record Requirement(String type, String guardian, @Nullable ResourceLocation item, int count, String hint) {
        public static final String GUARDIAN = "guardian";
        public static final String ITEMS = "items";
    }

    /** 石柱の守り手：決まった所に1体だけ出る強い敵（{@code variant} は同じ見た目の別の定義）。倒されると {@code respawnSeconds} 後にまた出る。 */
    public record Guardian(String id, ResourceLocation enemy, @Nullable ResourceLocation variant, Vec3 pos, int respawnSeconds,
                           double activation, int pillar) {
    }

    /**
     * 仕掛け石の組：{@code stones} をすべて {@code windowSeconds} 秒のうちに押すと、門（{@code gateMin}〜{@code gateMax} の格子）が
     * {@code openSeconds} 秒ひらく。
     */
    public record SwitchGroup(String id, List<BlockPos> stones, int windowSeconds, int openSeconds, BlockPos gateMin, BlockPos gateMax) {
    }

    /** 古代の宝箱：人それぞれ1度だけ開けられる。中身は {@code lootTable} と、{@code knotsMin}〜{@code knotsMax} のノット。 */
    public record ChestDef(int index, BlockPos pos, @Nullable ResourceLocation lootTable, int knotsMin, int knotsMax) {
    }

    /**
     * 階の中だけのドロップ（光のかけら）。{@code enemy} を倒した人に {@code chance}% で落とす。{@code pillar} の石柱を
     * 起動するまで、持っている数が {@code keep} 個になるまで。
     */
    public record ExtraDrop(ResourceLocation enemy, ResourceLocation item, double chance, int pillar, int keep) {
    }

    /** 11月22日に足した、階の仕掛け（守り手・仕掛け石と門・古代の宝箱・階の中だけのドロップ）。 */
    public record Extras(List<Guardian> guardians, List<SwitchGroup> switches, List<ChestDef> chests, List<ExtraDrop> drops) {
        public static final Extras NONE = new Extras(List.of(), List.of(), List.of(), List.of());

        @Nullable
        public Guardian guardian(String id) {
            for (Guardian guardian : guardians) {
                if (guardian.id().equals(id)) {
                    return guardian;
                }
            }
            return null;
        }

        @Nullable
        public SwitchGroup switchGroup(String id) {
            for (SwitchGroup group : switches) {
                if (group.id().equals(id)) {
                    return group;
                }
            }
            return null;
        }
    }

    /**
     * 敵の湧く所。{@code route} があれば、その道を巡回する（石くれ兵）。{@code activation} の中に人がいる間だけ湧く
     * （人のいない所には敵を出さない。Phase 0-B 11章）。
     */
    public record SpawnZone(String id, ResourceLocation enemy, Vec3 center, double radius, int max, int respawnSeconds,
                            int groupMin, int groupMax, double activation, List<BlockPos> route) {
    }

    /**
     * ボスの人数補正（HPのほかの分）と怒り（11月23日）。参加者が1人増えるごとに、攻撃が {@code attackPerExtraPlayer}、
     * 技の間が {@code pacePerExtraPlayer} の割合で変わる（技の間は 0.6 倍まで）。{@code enrageSeconds} 秒たつと怒る（0 なら怒らない）。
     */
    public record Scaling(double attackPerExtraPlayer, double pacePerExtraPlayer, int enrageSeconds) {
        public static final Scaling NONE = new Scaling(0.0, 0.0, 0);

        public double attackMultiplier(int players) {
            return 1.0 + attackPerExtraPlayer * Math.max(0, players - 1);
        }

        public double pace(int players) {
            return Math.max(0.6, 1.0 - pacePerExtraPlayer * Math.max(0, players - 1));
        }
    }

    /** ボスの部屋。{@code seal} は入口の封印のブロックの範囲（両端を含む）。 */
    public record BossRoomDef(ResourceLocation boss, Spot spawn, Vec3 arenaCenter, double arenaRadius, int arenaYMin,
                              int arenaYMax, BlockPos sealMin, BlockPos sealMax, double startRadius, int respawnSeconds,
                              int maxPlayers, double hpPerExtraPlayer, Circle exit, String name, Scaling scaling) {
        /** 戦いの間だけ、部屋の円のまわりに足すはば（門の通路のぶん）。 */
        public static final double FIGHT_MARGIN = 4.0;

        /** 部屋の中か（世界の座標）。 */
        public boolean inside(Vec3 world, Vec3 origin) {
            double dx = world.x - (origin.x + arenaCenter.x);
            double dz = world.z - (origin.z + arenaCenter.z);
            double y = world.y - origin.y;
            return dx * dx + dz * dz <= arenaRadius * arenaRadius && y >= arenaYMin && y <= arenaYMax;
        }

        /**
         * 戦いの間の「部屋の中」か：門は光の幕で閉じているので、部屋の円のまわり {@link #FIGHT_MARGIN} ブロック（門の通路）にいる人も
         * 中に数える。突進・後ろ蹴り・大跳躍で門の通路まで押し出された人が「部屋を出た」ことになり、戦いがはじめからにならないように
         * 【補完】（11月22日）。高さは、突き上げで浮いたぶんも入れる。
         */
        public boolean insideFight(Vec3 world, Vec3 origin) {
            double dx = world.x - (origin.x + arenaCenter.x);
            double dz = world.z - (origin.z + arenaCenter.z);
            double y = world.y - origin.y;
            double r = arenaRadius + FIGHT_MARGIN;
            return dx * dx + dz * dz <= r * r && y >= arenaYMin - 2 && y <= arenaYMax + 4;
        }
    }

    /** 古代記録の石板（Phase 6 で記録帳に入る。いまは触れると読める）。 */
    public record Tablet(BlockPos pos, String title, String text) {
    }

    // ---------------------------------------------------------------- 世界の座標

    public Vec3 originVec() {
        return new Vec3(origin.getX(), origin.getY(), origin.getZ());
    }

    public Vec3 world(Vec3 local) {
        return local.add(origin.getX(), origin.getY(), origin.getZ());
    }

    public BlockPos world(BlockPos local) {
        return local.offset(origin);
    }

    /** 階の範囲（世界の座標）。 */
    public AABB bounds() {
        return new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + sizeX, origin.getY() + sizeY,
                origin.getZ() + sizeZ);
    }

    /** 階の範囲の中か（上下は少し広く見る）。 */
    public boolean contains(Vec3 world) {
        return world.x >= origin.getX() && world.x < origin.getX() + sizeX && world.z >= origin.getZ()
                && world.z < origin.getZ() + sizeZ && world.y >= origin.getY() - 16 && world.y < origin.getY() + sizeY + 32;
    }

    /** 表示する名前（「1階　始まりの草原」）。 */
    public String title() {
        return number + "階　" + name;
    }

    // ---------------------------------------------------------------- 読む

    public static FloorDef parse(ResourceLocation id, JsonObject json) {
        int number = Json.intInRange(json, "floor", 1, 99);
        int chapter = Json.intInRange(json, "chapter", 1, 9);
        String name = Json.string(json, "name");
        String chapterTitle = Json.optionalString(json, "chapter_title", "");
        JsonArray level = Json.array(json, "recommended_level");
        if (level.size() != 2) {
            throw new DefinitionException("「recommended_level」は [下, 上] の2つの数にしてください");
        }
        int min = (int) Json.asNumber(level.get(0), "recommended_level");
        int max = (int) Json.asNumber(level.get(1), "recommended_level");
        BlockPos origin = blockPos(Json.array(json, "origin"), "origin");
        JsonArray size = Json.array(json, "size");
        if (size.size() != 3) {
            throw new DefinitionException("「size」は [東西, 高さ, 南北] の3つの数にしてください");
        }
        int sx = (int) Json.asNumber(size.get(0), "size");
        int sy = (int) Json.asNumber(size.get(1), "size");
        int sz = (int) Json.asNumber(size.get(2), "size");
        String prefix = Json.string(json, "structure");
        int piece = Json.optionalInt(json, "piece_size", 48, 1, 48);
        Spot entrance = spot(Json.object(json, "entrance"), "entrance");
        Circle back = circle(Json.object(json, "return_circle"), "return_circle");
        JsonObject bossJson = Json.optionalObject(json, "boss_room");
        BossRoomDef boss = bossJson == null ? null : bossRoom(bossJson);
        List<PillarDef> pillars = new ArrayList<>();
        JsonArray pillarArray = Json.optionalArray(json, "pillars");
        for (int i = 0; i < pillarArray.size(); i++) {
            JsonObject p = Json.asObject(pillarArray.get(i), "pillars");
            JsonObject req = Json.optionalObject(p, "requires");
            pillars.add(new PillarDef(i, blockPos(Json.array(p, "pos"), "pillars.pos"), Json.string(p, "line"),
                    req == null ? null : requirement(req)));
        }
        String done = Json.optionalString(json, "pillars_done", "");
        List<SpawnZone> spawns = new ArrayList<>();
        JsonArray spawnArray = Json.optionalArray(json, "spawns");
        for (int i = 0; i < spawnArray.size(); i++) {
            JsonObject s = Json.asObject(spawnArray.get(i), "spawns");
            List<BlockPos> route = new ArrayList<>();
            JsonArray r = Json.optionalArray(s, "route");
            for (int k = 0; k < r.size(); k++) {
                route.add(blockPos(r.get(k).getAsJsonArray(), "spawns.route"));
            }
            JsonArray group = Json.optionalArray(s, "group");
            int gMin = group.size() == 2 ? (int) Json.asNumber(group.get(0), "group") : 1;
            int gMax = group.size() == 2 ? (int) Json.asNumber(group.get(1), "group") : gMin;
            spawns.add(new SpawnZone(Json.optionalString(s, "id", "zone" + i), Json.id(s, "enemy"),
                    vec(Json.array(s, "center"), "spawns.center"), Json.optionalDouble(s, "radius", 12.0, 1.0, 256.0),
                    Json.optionalInt(s, "max", 4, 0, 64), Json.optionalInt(s, "respawn_seconds", 30, 1, 3600),
                    Math.max(1, gMin), Math.max(Math.max(1, gMin), gMax),
                    Json.optionalDouble(s, "activation", 64.0, 8.0, 512.0), List.copyOf(route)));
        }
        JsonObject tabletJson = Json.optionalObject(json, "tablet");
        Tablet tablet = tabletJson == null ? null : new Tablet(blockPos(Json.array(tabletJson, "pos"), "tablet.pos"),
                Json.string(tabletJson, "title"), Json.string(tabletJson, "text"));
        Extras extras = new Extras(guardians(json), switchGroups(json), chests(json), extraDrops(json));
        for (PillarDef pillar : pillars) {
            if (pillar.requires() != null && Requirement.GUARDIAN.equals(pillar.requires().type())
                    && extras.guardian(pillar.requires().guardian()) == null) {
                throw new DefinitionException("石柱 " + (pillar.index() + 1) + " の守り手「" + pillar.requires().guardian() + "」が「guardians」にありません");
            }
        }
        for (Guardian guardian : extras.guardians()) {
            if (guardian.pillar() >= pillars.size()) {
                throw new DefinitionException("守り手「" + guardian.id() + "」の石柱の番号が大きすぎます");
            }
        }
        return new FloorDef(id, number, chapter, name, chapterTitle, min, max, origin, sx, sy, sz, prefix, piece, entrance,
                back, boss, List.copyOf(pillars), done, List.copyOf(spawns), tablet, extras);
    }

    private static Requirement requirement(JsonObject json) {
        String type = Json.string(json, "type");
        if (!Requirement.GUARDIAN.equals(type) && !Requirement.ITEMS.equals(type)) {
            throw new DefinitionException("石柱の「requires.type」は guardian・items のどちらかにしてください");
        }
        ResourceLocation item = Requirement.ITEMS.equals(type) ? Json.id(json, "item") : null;
        return new Requirement(type, Json.optionalString(json, "guardian", ""), item, Json.optionalInt(json, "count", 1, 1, 64),
                Json.optionalString(json, "hint", ""));
    }

    private static List<Guardian> guardians(JsonObject json) {
        List<Guardian> list = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "guardians")) {
            JsonObject g = Json.asObject(element, "guardians");
            list.add(new Guardian(Json.string(g, "id"), Json.id(g, "enemy"), Json.optionalId(g, "variant"),
                    vec(Json.array(g, "pos"), "guardians.pos"), Json.optionalInt(g, "respawn_seconds", 180, 5, 86400),
                    Json.optionalDouble(g, "activation", 48.0, 8.0, 512.0), Json.optionalInt(g, "pillar", 0, 0, 31)));
        }
        return List.copyOf(list);
    }

    private static List<SwitchGroup> switchGroups(JsonObject json) {
        List<SwitchGroup> list = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "switches")) {
            JsonObject g = Json.asObject(element, "switches");
            List<BlockPos> stones = new ArrayList<>();
            for (JsonElement stone : Json.array(g, "stones")) {
                stones.add(blockPos(stone.getAsJsonArray(), "switches.stones"));
            }
            if (stones.isEmpty()) {
                throw new DefinitionException("仕掛け石の組「" + Json.string(g, "id") + "」に石がありません");
            }
            JsonArray gate = Json.array(g, "gate");
            if (gate.size() != 2) {
                throw new DefinitionException("「switches.gate」は [[x, y, z], [x, y, z]] の2つの角にしてください");
            }
            BlockPos a = blockPos(gate.get(0).getAsJsonArray(), "switches.gate");
            BlockPos b = blockPos(gate.get(1).getAsJsonArray(), "switches.gate");
            list.add(new SwitchGroup(Json.string(g, "id"), List.copyOf(stones), Json.optionalInt(g, "window_seconds", 10, 1, 600),
                    Json.optionalInt(g, "open_seconds", 60, 1, 3600),
                    new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                    new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))));
        }
        return List.copyOf(list);
    }

    private static List<ChestDef> chests(JsonObject json) {
        List<ChestDef> list = new ArrayList<>();
        JsonArray array = Json.optionalArray(json, "chests");
        if (array.size() > 64) {
            throw new DefinitionException("古代の宝箱は1つの階に64個までです");
        }
        for (int i = 0; i < array.size(); i++) {
            JsonObject c = Json.asObject(array.get(i), "chests");
            int min = Json.optionalInt(c, "knots_min", 0, 0, 1_000_000);
            int max = Json.optionalInt(c, "knots_max", min, min, 1_000_000);
            list.add(new ChestDef(i, blockPos(Json.array(c, "pos"), "chests.pos"), Json.optionalId(c, "loot"), min, max));
        }
        return List.copyOf(list);
    }

    private static List<ExtraDrop> extraDrops(JsonObject json) {
        List<ExtraDrop> list = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "extra_drops")) {
            JsonObject d = Json.asObject(element, "extra_drops");
            list.add(new ExtraDrop(Json.id(d, "enemy"), Json.id(d, "item"), Json.doubleInRange(d, "chance", 0.0, 100.0),
                    Json.optionalInt(d, "pillar", -1, -1, 31), Json.optionalInt(d, "keep", 64, 1, 64)));
        }
        return List.copyOf(list);
    }

    private static BossRoomDef bossRoom(JsonObject json) {
        JsonObject arena = Json.object(json, "arena");
        JsonArray seal = Json.array(json, "seal");
        if (seal.size() != 2) {
            throw new DefinitionException("「seal」は [[x, y, z], [x, y, z]] の2つの角にしてください");
        }
        BlockPos a = blockPos(seal.get(0).getAsJsonArray(), "seal");
        BlockPos b = blockPos(seal.get(1).getAsJsonArray(), "seal");
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        return new BossRoomDef(Json.id(json, "boss"), spot(Json.object(json, "spawn"), "boss_room.spawn"),
                vec(Json.array(arena, "center"), "arena.center"), Json.doubleInRange(arena, "radius", 4.0, 128.0),
                Json.intInRange(arena, "y_min", -64, 512), Json.intInRange(arena, "y_max", -64, 512), min, max,
                Json.optionalDouble(json, "start_radius", 16.0, 1.0, 128.0), Json.optionalInt(json, "respawn_seconds", 60, 0, 86400),
                Json.optionalInt(json, "max_players", 6, 1, 64), Json.optionalDouble(json, "hp_per_extra_player", 0.6, 0.0, 10.0),
                circle(Json.object(json, "exit"), "boss_room.exit"), Json.optionalString(json, "name", ""),
                new Scaling(Json.optionalDouble(json, "attack_per_extra_player", 0.0, 0.0, 5.0),
                        Json.optionalDouble(json, "pace_per_extra_player", 0.0, 0.0, 0.5),
                        Json.optionalInt(json, "enrage_seconds", 0, 0, 7200)));
    }

    private static Spot spot(JsonObject json, String name) {
        return new Spot(vec(Json.array(json, "pos"), name), (float) Json.optionalDouble(json, "yaw", 0.0, -360.0, 360.0));
    }

    private static Circle circle(JsonObject json, String name) {
        return new Circle(vec(Json.array(json, "center"), name), Json.optionalDouble(json, "radius", 2.5, 0.5, 32.0));
    }

    static Vec3 vec(JsonArray array, String name) {
        if (array.size() != 3) {
            throw new DefinitionException("「" + name + "」は [x, y, z] の3つの数にしてください");
        }
        return new Vec3(Json.asNumber(array.get(0), name), Json.asNumber(array.get(1), name), Json.asNumber(array.get(2), name));
    }

    static BlockPos blockPos(JsonArray array, String name) {
        Vec3 v = vec(array, name);
        return BlockPos.containing(v.x, v.y, v.z);
    }

    @SuppressWarnings("unused")
    private static JsonArray asArray(JsonElement element, String name) {
        if (!element.isJsonArray()) {
            throw new DefinitionException("「" + name + "」は [ ] で囲んだ形にしてください");
        }
        return element.getAsJsonArray();
    }
}
