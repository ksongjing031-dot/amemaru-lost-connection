package jp.amemaru.lostconnection.admin;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 街の構造物を、ゲームの中で直して書き出す（11月21日。Phase 0-B 12章「クリエイティブで直した部分を保存し直せる」）。
 *
 * <p>ワールドの {@code generated/amemaru/city/} に、次の2つを置く。</p>
 * <ul>
 *   <li>{@code edits/<構造物>.json}：書き出した直し。Modに入っている形との違い（マスごとのブロックと、マスごとの「もの」）。
 *       {@code city build} は、Modに入っている形にこの直しを重ねて建てる（Modが新しくなっても、直しはそのまま重なる）。
 *       設計データへは {@code tools/mapgen/import_edits.py} で取り込む（取り込むと、作り直しても直しが残る）。</li>
 *   <li>{@code built/<構造物>.nbt}：最後に建てた形（書き出した直しも入れる）。直した所は、この形と今のワールドを比べて見つける。
 *       Modが新しくなっても、建てたときの形と比べられる（Modの形と比べると、Modで変えた所まで直しに見えるため）。</li>
 * </ul>
 * <ul>
 *   <li>マスは、そのマスを書く構造物のうち、置く順番で最後のものに入れる（ギルドと個人倉庫、はじめの形と育ったあとに重ねる形）。</li>
 *   <li>「もの」は額縁・光る額縁・絵・防具立て・表示の「もの」だけを見る（落ちたアイテム・動物などは見ない）。「もの」の変わった
 *       マスは、そのマスを書く構造物に入れ、ほかの構造物にあった同じマスの「もの」は消す（建て直しても重ならない）。</li>
 *   <li>額縁と防具立てには、飾りの守り（壊れない・額縁は固定・防具立ては出し入れを止める）を付け直して書き出す。</li>
 *   <li>草が土になる・扉が開く・灯りが点く・氷が溶ける・水が流れる・サトウキビや竹やつるが伸びる・銅が錆びる・柵や階段の形が
 *       まわりに合わせて変わる、などの自然に変わる所は、直しとして数えない。</li>
 *   <li>建物の外（道・広場・野原）は、どの構造物にも入らないので書き出さない（変わった所の数だけ知らせる）。</li>
 * </ul>
 */
final class CityEdits {
    /** 書き出した直しのファイルの形（tools/mapgen/import_edits.py と同じ） */
    static final String FORMAT = "amemaru-city-edits";
    static final int FORMAT_VERSION = 1;
    /** 送ってもらうフォルダ（ワールドのフォルダから） */
    static final String EDITS_FOLDER = "generated/amemaru/city/edits";
    private static final String FOLDER = "map/conelia/";
    /** 比べるときと書き出すときに外す、ブロックの中身の値（使っているうちに変わるもの） */
    private static final Set<String> VOLATILE_BLOCK_KEYS = Set.of("TickCount", "RecordStartTick", "IsPlaying", "TransferCooldown",
            "Bees", "FlowerPos", "CookingTimes", "CookingTotalTimes", "BurnTime", "CookTime", "CookTimeTotal", "RecipesUsed",
            "Fuel", "BrewTime", "LastOutput", "SuccessCount", "Page", "last_interacted_slot", "Delay", "LootTableSeed", "Levels", "Target");
    /** 比べるときと書き出すときに外す、「もの」の値（置き直すと変わるもの・体の状態） */
    private static final Set<String> VOLATILE_ENTITY_KEYS = Set.of("UUID", "Pos", "Motion", "FallDistance", "Fire", "Air", "OnGround",
            "PortalCooldown", "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount", "FallFlying", "Brain", "Attributes",
            "Health", "ForgeCaps", "ForgeData", "CanUpdate", "TileX", "TileY", "TileZ");
    /**
     * 自然に変わる（直しとして数えない）ブロックの性質。水につかる（waterlogged）も入れる：後に置く構造物の半ブロックなどは、前の
     * 構造物の水の所に置くと、ゲームが水につける（建て直しても同じになる）
     */
    private static final Set<String> VOLATILE_PROPERTIES = Set.of("lit", "distance", "persistent", "snowy", "power", "powered",
            "level", "age", "moisture", "open", "occupied", "bites", "triggered", "attached", "disarmed", "extended", "has_record",
            "honey_level", "stage", "leaves", "hatch", "dusted", "bloom", "sculk_sensor_phase", "can_summon", "shrieking", "berries",
            "enabled", "bottom", "waterlogged");
    /** 防具立てのすべての場所の出し入れを止める値（tools/mapgen/mapgen/world.py と同じ） */
    private static final int ALL_SLOTS_DISABLED = 4144959;

    private CityEdits() {
    }

    /** 書き出した直しのファイルが読めない */
    static final class EditsException extends Exception {
        EditsException(String message) {
            super(message);
        }
    }

    // ================================================================ 構造物の形

    /** 構造物の中の「もの」。cell はいるマス（掛かっている・立っているマス） */
    record EntityInfo(Vec3 pos, BlockPos cell, CompoundTag nbt) {
    }

    private static final class Piece {
        final CityCommands.Placement placement;
        final int index;
        final BlockPos at;
        final int sx;
        final int sy;
        final int sz;
        final BlockState[] states;
        final CompoundTag[] nbts;
        final List<EntityInfo> entities = new ArrayList<>();
        // 比べた結果（構造物の中の位置）
        final List<BlockPos> changedCells = new ArrayList<>();
        final Map<BlockPos, List<Entity>> entityChanges = new LinkedHashMap<>();
        final Set<BlockPos> entityDrops = new LinkedHashSet<>();

        Piece(CityCommands.Placement placement, int index, BlockPos at, int sx, int sy, int sz) {
            this.placement = placement;
            this.index = index;
            this.at = at;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.states = new BlockState[sx * sy * sz];
            this.nbts = new CompoundTag[sx * sy * sz];
        }

        int idx(int x, int y, int z) {
            return (x * sy + y) * sz + z;
        }

        boolean inside(BlockPos c) {
            return c.getX() >= 0 && c.getY() >= 0 && c.getZ() >= 0 && c.getX() < sx && c.getY() < sy && c.getZ() < sz;
        }

        boolean changed() {
            return !changedCells.isEmpty() || !entityChanges.isEmpty() || !entityDrops.isEmpty();
        }
    }

    private static HolderLookup<Block> blocks(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.BLOCK);
    }

    private static Piece parse(HolderLookup<Block> blocks, CityCommands.Placement placement, int index, BlockPos at, CompoundTag tag) {
        ListTag size = tag.getList("size", Tag.TAG_INT);
        Piece p = new Piece(placement, index, at, size.getInt(0), size.getInt(1), size.getInt(2));
        ListTag palette = tag.contains("palettes", Tag.TAG_LIST)
                ? tag.getList("palettes", Tag.TAG_LIST).getList(0)
                : tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> pal = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            pal.add(NbtUtils.readBlockState(blocks, palette.getCompound(i)));
        }
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            BlockPos c = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
            int state = b.getInt("state");
            if (!p.inside(c) || state < 0 || state >= pal.size()) {
                continue;
            }
            int k = p.idx(c.getX(), c.getY(), c.getZ());
            p.states[k] = pal.get(state);
            p.nbts[k] = b.contains("nbt", Tag.TAG_COMPOUND) ? b.getCompound("nbt") : null;
        }
        ListTag ents = tag.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < ents.size(); i++) {
            CompoundTag e = ents.getCompound(i);
            ListTag pos = e.getList("pos", Tag.TAG_DOUBLE);
            Vec3 v = new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
            // いるマスは、ゲームが置くときと同じく位置から決める（額縁は掛かっているマス、防具立ては立っているマス）
            p.entities.add(new EntityInfo(v, BlockPos.containing(v), e.getCompound("nbt")));
        }
        return p;
    }

    private static CompoundTag toTag(Piece p) {
        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        ListTag blocks = new ListTag();
        for (int x = 0; x < p.sx; x++) {
            for (int y = 0; y < p.sy; y++) {
                for (int z = 0; z < p.sz; z++) {
                    int k = p.idx(x, y, z);
                    BlockState state = p.states[k];
                    if (state == null) {
                        continue;
                    }
                    CompoundTag entry = new CompoundTag();
                    entry.put("pos", ints(x, y, z));
                    entry.putInt("state", palette.computeIfAbsent(state, s -> palette.size()));
                    if (p.nbts[k] != null) {
                        entry.put("nbt", p.nbts[k].copy());
                    }
                    blocks.add(entry);
                }
            }
        }
        ListTag pal = new ListTag();
        for (BlockState state : palette.keySet()) {
            pal.add(NbtUtils.writeBlockState(state));
        }
        ListTag entities = new ListTag();
        for (EntityInfo info : p.entities) {
            CompoundTag e = new CompoundTag();
            e.put("pos", doubles(info.pos()));
            e.put("blockPos", ints(info.cell().getX(), info.cell().getY(), info.cell().getZ()));
            e.put("nbt", info.nbt().copy());
            entities.add(e);
        }
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.put("size", ints(p.sx, p.sy, p.sz));
        root.put("palette", pal);
        root.put("blocks", blocks);
        root.put("entities", entities);
        return root;
    }

    // ================================================================ 置き場所と、建てる形

    private static Path cityDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.GENERATED_DIR).resolve(AmemaruMod.MOD_ID).resolve("city");
    }

    private static Path editsFile(MinecraftServer server, String name) {
        return cityDir(server).resolve("edits").resolve(name + ".json");
    }

    private static Path builtFile(MinecraftServer server, String name) {
        return cityDir(server).resolve("built").resolve(name + ".nbt");
    }

    /** Modに入っている形（資源から直接読む）。ないときは null */
    @Nullable
    private static CompoundTag shippedTag(ServerLevel level, CityCommands.Placement placement) {
        Optional<Resource> resource = level.getServer().getResourceManager()
                .getResource(AmemaruMod.id("structures/" + FOLDER + placement.name() + ".nbt"));
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open()) {
            return NbtIo.readCompressed(in);
        } catch (IOException e) {
            AmemaruMod.LOGGER.error("[amemaru] city: cannot read {}", placement.name(), e);
            return null;
        }
    }

    /** この構造物に、書き出した直しがあるか */
    static boolean hasEdits(ServerLevel level, CityCommands.Placement placement) {
        return Files.isRegularFile(editsFile(level.getServer(), placement.name()));
    }

    /** 建てる形：Modに入っている形に、書き出した直しを重ねたもの。Modにない構造物なら null */
    @Nullable
    static CompoundTag buildTag(ServerLevel level, CityCommands.Placement placement) throws EditsException {
        CompoundTag shipped = shippedTag(level, placement);
        if (shipped == null) {
            return null;
        }
        Edits edits = readEdits(level, placement);
        if (edits.isEmpty()) {
            return shipped;
        }
        Piece p = parse(blocks(level), placement, 0, BlockPos.ZERO, shipped);
        apply(p, edits);
        return toTag(p);
    }

    /** 建てた形を控える（tags は PLACEMENTS と同じ並び。建てなかった構造物は null で、控えを消す） */
    static void saveBuilt(ServerLevel level, List<CompoundTag> tags) {
        for (int i = 0; i < CityCommands.PLACEMENTS.size(); i++) {
            Path file = builtFile(level.getServer(), CityCommands.PLACEMENTS.get(i).name());
            try {
                if (tags.get(i) == null) {
                    Files.deleteIfExists(file);
                } else {
                    Files.createDirectories(file.getParent());
                    NbtIo.writeCompressed(tags.get(i), file.toFile());
                }
            } catch (IOException e) {
                // 控えがなくても、書き出しは「Modの形＋書き出した直し」と比べて動く
                AmemaruMod.LOGGER.error("[amemaru] city: cannot save {}", file, e);
            }
        }
    }

    /** 最後に建てた形（控え。なければ「Modの形＋書き出した直し」） */
    @Nullable
    private static CompoundTag knownTag(ServerLevel level, CityCommands.Placement placement) throws EditsException {
        Path file = builtFile(level.getServer(), placement.name());
        if (Files.isRegularFile(file)) {
            try {
                return NbtIo.readCompressed(file.toFile());
            } catch (IOException e) {
                AmemaruMod.LOGGER.error("[amemaru] city: cannot read {}", file, e);
            }
        }
        return buildTag(level, placement);
    }

    // ================================================================ 書き出した直し（JSON）

    /** マスの直し：ブロックと、その中身（看板の文字など。ないときは null） */
    private record CellEdit(BlockState state, @Nullable CompoundTag nbt) {
    }

    private static final class Edits {
        final Map<BlockPos, CellEdit> cells = new HashMap<>();
        /** マス → そのマスにいる「もの」（空なら、そのマスの「もの」を消す） */
        final Map<BlockPos, List<EntityInfo>> entityCells = new HashMap<>();

        boolean isEmpty() {
            return cells.isEmpty() && entityCells.isEmpty();
        }

        void putAll(Edits other) {
            cells.putAll(other.cells);
            entityCells.putAll(other.entityCells);
        }
    }

    private static Edits readEdits(ServerLevel level, CityCommands.Placement placement) throws EditsException {
        Path file = editsFile(level.getServer(), placement.name());
        Edits edits = new Edits();
        if (!Files.isRegularFile(file)) {
            return edits;
        }
        String where = EDITS_FOLDER + "/" + file.getFileName();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!FORMAT.equals(root.get("format").getAsString()) || root.get("version").getAsInt() != FORMAT_VERSION
                    || !placement.name().equals(root.get("structure").getAsString())) {
                throw new EditsException(where + "（形が違います）");
            }
            HolderLookup<Block> lookup = blocks(level);
            for (JsonElement element : root.getAsJsonArray("cells")) {
                JsonObject cell = element.getAsJsonObject();
                BlockState state = BlockStateParser.parseForBlock(lookup, cell.get("state").getAsString(), false).blockState();
                CompoundTag nbt = cell.has("nbt") ? TagParser.parseTag(cell.get("nbt").getAsString()) : null;
                edits.cells.put(pos(cell.getAsJsonArray("pos")), new CellEdit(state, nbt));
            }
            for (JsonElement element : root.getAsJsonArray("entityCells")) {
                JsonObject cell = element.getAsJsonObject();
                BlockPos c = pos(cell.getAsJsonArray("cell"));
                List<EntityInfo> list = new ArrayList<>();
                for (JsonElement e : cell.getAsJsonArray("entities")) {
                    JsonObject one = e.getAsJsonObject();
                    JsonArray p = one.getAsJsonArray("pos");
                    CompoundTag nbt = TagParser.parseTag(one.get("nbt").getAsString());
                    if (!nbt.contains("id", Tag.TAG_STRING)) {
                        throw new EditsException(where + "（「もの」の種類がありません）");
                    }
                    list.add(new EntityInfo(new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()), c, nbt));
                }
                edits.entityCells.put(c, list);
            }
        } catch (IOException | CommandSyntaxException | RuntimeException e) {
            throw new EditsException(where + "（" + e.getMessage() + "）");
        }
        return edits;
    }

    private static void writeEdits(ServerLevel level, CityCommands.Placement placement, Edits edits) throws IOException {
        Path file = editsFile(level.getServer(), placement.name());
        if (edits.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("structure", placement.name());
        root.addProperty("facility", placement.facility());
        root.addProperty("dataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        // BlockPos には位置を詰めた long を読む static の getY(long) もあるので、メソッド参照ではなくラムダで書く
        Comparator<BlockPos> order = Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getZ())
                .thenComparingInt(p -> p.getX());
        JsonArray cells = new JsonArray();
        edits.cells.entrySet().stream().sorted(Map.Entry.<BlockPos, CellEdit>comparingByKey(order)).forEach(e -> {
            JsonObject cell = new JsonObject();
            cell.add("pos", json(e.getKey()));
            cell.addProperty("state", BlockStateParser.serialize(e.getValue().state()));
            if (e.getValue().nbt() != null) {
                cell.addProperty("nbt", e.getValue().nbt().toString());
            }
            cells.add(cell);
        });
        root.add("cells", cells);
        JsonArray entityCells = new JsonArray();
        edits.entityCells.entrySet().stream().sorted(Map.Entry.<BlockPos, List<EntityInfo>>comparingByKey(order)).forEach(e -> {
            JsonObject cell = new JsonObject();
            cell.add("cell", json(e.getKey()));
            JsonArray list = new JsonArray();
            for (EntityInfo info : e.getValue()) {
                JsonObject one = new JsonObject();
                JsonArray p = new JsonArray();
                p.add(info.pos().x);
                p.add(info.pos().y);
                p.add(info.pos().z);
                one.add("pos", p);
                one.addProperty("nbt", info.nbt().toString());
                list.add(one);
            }
            cell.add("entities", list);
            entityCells.add(cell);
        });
        root.add("entityCells", entityCells);
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n",
                StandardCharsets.UTF_8);
    }

    /** 直しを重ねる（マスのブロックと中身、マスの「もの」） */
    private static void apply(Piece p, Edits edits) {
        for (Map.Entry<BlockPos, CellEdit> e : edits.cells.entrySet()) {
            BlockPos c = e.getKey();
            if (!p.inside(c)) {
                continue;
            }
            int k = p.idx(c.getX(), c.getY(), c.getZ());
            p.states[k] = e.getValue().state();
            p.nbts[k] = e.getValue().nbt() == null ? null : e.getValue().nbt().copy();
        }
        if (!edits.entityCells.isEmpty()) {
            p.entities.removeIf(info -> edits.entityCells.containsKey(info.cell()));
            for (List<EntityInfo> list : edits.entityCells.values()) {
                for (EntityInfo info : list) {
                    p.entities.add(new EntityInfo(info.pos(), info.cell(), info.nbt().copy()));
                }
            }
        }
    }

    // ================================================================ 比べる

    private record Expected(int piece, CompoundTag signature) {
    }

    private static final class Analysis {
        final List<Piece> pieces = new ArrayList<>();
        /** マス（世界の位置）→ そのマスを書く構造物のうち、置く順番で最後のもの */
        final Long2IntOpenHashMap owner = new Long2IntOpenHashMap();
        int outsideCells;
        final List<BlockPos> outsideExamples = new ArrayList<>();
        int outsideEntities;
        @Nullable
        String missing;

        Analysis() {
            owner.defaultReturnValue(-1);
        }
    }

    /** 最後に建てた形と、今のワールドを比べる。scanOutside なら、建物の外（道・広場・野原）も見る */
    private static Analysis analyze(ServerLevel level, BlockPos origin, boolean grown, boolean scanOutside) throws EditsException {
        Analysis a = new Analysis();
        HolderLookup<Block> blocks = blocks(level);
        for (CityCommands.Placement placement : CityCommands.PLACEMENTS) {
            if (placement.grown() && !grown) {
                continue;
            }
            CompoundTag tag = knownTag(level, placement);
            if (tag == null) {
                a.missing = placement.name();
                return a;
            }
            a.pieces.add(parse(blocks, placement, a.pieces.size(), origin.offset(placement.x(), placement.y(), placement.z()), tag));
        }
        for (Piece p : a.pieces) {
            for (int x = 0; x < p.sx; x++) {
                for (int y = 0; y < p.sy; y++) {
                    for (int z = 0; z < p.sz; z++) {
                        if (p.states[p.idx(x, y, z)] != null) {
                            a.owner.put(BlockPos.asLong(p.at.getX() + x, p.at.getY() + y, p.at.getZ() + z), p.index);
                        }
                    }
                }
            }
        }
        // ブロック
        BlockPos.MutableBlockPos w = new BlockPos.MutableBlockPos();
        for (Piece p : a.pieces) {
            for (int x = 0; x < p.sx; x++) {
                for (int y = 0; y < p.sy; y++) {
                    for (int z = 0; z < p.sz; z++) {
                        int k = p.idx(x, y, z);
                        if (p.states[k] == null) {
                            continue;
                        }
                        w.set(p.at.getX() + x, p.at.getY() + y, p.at.getZ() + z);
                        if (a.owner.get(w.asLong()) == p.index && !sameBlock(level, w.immutable(), p.states[k], p.nbts[k])) {
                            p.changedCells.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
        // 「もの」：どの構造物の分も合わせて、マスごとに比べる
        Map<Long, List<Expected>> expected = new HashMap<>();
        for (Piece p : a.pieces) {
            for (EntityInfo info : p.entities) {
                Entity e = create(level, p.at, info);
                if (e != null && decoration(e)) {
                    expected.computeIfAbsent(cellOf(e).asLong(), c -> new ArrayList<>()).add(new Expected(p.index, signature(e)));
                }
            }
        }
        Map<Long, List<Entity>> actual = new HashMap<>();
        for (Entity e : level.getEntities((Entity) null, cityBox(origin), CityEdits::decoration)) {
            actual.computeIfAbsent(cellOf(e).asLong(), c -> new ArrayList<>()).add(e);
        }
        Set<Long> cells = new HashSet<>(expected.keySet());
        cells.addAll(actual.keySet());
        for (long cell : cells) {
            List<Expected> want = expected.getOrDefault(cell, List.of());
            List<Entity> have = actual.getOrDefault(cell, List.of());
            List<CompoundTag> wantSig = new ArrayList<>();
            for (Expected ex : want) {
                wantSig.add(ex.signature());
            }
            List<CompoundTag> haveSig = new ArrayList<>();
            for (Entity e : have) {
                haveSig.add(signature(e));
            }
            if (sameMultiset(wantSig, haveSig)) {
                continue;
            }
            int o = a.owner.get(cell);
            if (o < 0) {
                a.outsideEntities++;
                continue;
            }
            BlockPos world = BlockPos.of(cell);
            Piece op = a.pieces.get(o);
            op.entityChanges.put(world.subtract(op.at), have);
            for (Expected ex : want) {
                if (ex.piece() != o) {
                    Piece other = a.pieces.get(ex.piece());
                    other.entityDrops.add(world.subtract(other.at));
                }
            }
        }
        // 建物の外（道・広場・野原）：どの構造物も書かないマス
        if (scanOutside) {
            BlockState stone = Blocks.STONE.defaultBlockState();
            BlockState dirt = Blocks.DIRT.defaultBlockState();
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int x = CityCommands.MIN_X; x <= CityCommands.MAX_X; x++) {
                for (int z = CityCommands.MIN_Z; z <= CityCommands.MAX_Z; z++) {
                    BlockState surface = CityCommands.surface(x, z);
                    for (int dy = -CityCommands.GROUND_DEPTH; dy <= CityCommands.CLEAR_HEIGHT; dy++) {
                        w.set(origin.getX() + x, origin.getY() + dy, origin.getZ() + z);
                        if (a.owner.get(w.asLong()) >= 0) {
                            continue;
                        }
                        BlockState expect = dy < -3 ? stone : dy < 0 ? dirt : dy == 0 ? surface : air;
                        BlockState now = level.getBlockState(w);
                        if (now != expect && !naturalChange(expect, now)) {
                            a.outsideCells++;
                            if (a.outsideExamples.size() < 3) {
                                a.outsideExamples.add(new BlockPos(x, dy, z));
                            }
                        }
                    }
                }
            }
        }
        return a;
    }

    private static AABB cityBox(BlockPos origin) {
        return new AABB(origin.getX() + CityCommands.MIN_X, origin.getY() - CityCommands.GROUND_DEPTH, origin.getZ() + CityCommands.MIN_Z,
                origin.getX() + CityCommands.MAX_X + 1, origin.getY() + CityCommands.CLEAR_HEIGHT + 1,
                origin.getZ() + CityCommands.MAX_Z + 1);
    }

    /** ワールドのブロックが、置いた形と同じか（自然に変わる所と、まわりに合わせて変わる形は同じとみなす） */
    private static boolean sameBlock(ServerLevel level, BlockPos pos, BlockState want, @Nullable CompoundTag wantNbt) {
        BlockState now = level.getBlockState(pos);
        boolean same = now == want || naturalChange(want, now);
        if (!same) {
            BlockState shaped = shaped(level, pos, want);
            same = now == shaped || naturalChange(shaped, now);
        }
        if (!same) {
            return false;
        }
        if (now.getBlock() == want.getBlock() && now.hasBlockEntity()) {
            CompoundTag a = normalizedBlockEntity(level, pos, now, wantNbt);
            BlockEntity be = level.getBlockEntity(pos);
            return a == null || be == null || a.equals(stripBlock(be.saveWithoutMetadata()));
        }
        return true;
    }

    /** まわりに合わせた形（柵・塀・板ガラスのつながり、階段の角、チェストの組など。置いたときにゲームが計算し直す） */
    private static BlockState shaped(ServerLevel level, BlockPos pos, BlockState state) {
        try {
            return Block.updateFromNeighbourShapes(state, level, pos);
        } catch (RuntimeException e) {
            return state;
        }
    }

    /** 自然に変わる所か（直しとして数えない） */
    static boolean naturalChange(BlockState want, BlockState now) {
        Block a = want.getBlock();
        Block b = now.getBlock();
        if (a == b) {
            if (a == Blocks.VINE) {
                return true;  // つるは伸びて、つく面が増える
            }
            for (Property<?> property : want.getProperties()) {
                if (!want.getValue(property).equals(now.getValue(property)) && !VOLATILE_PROPERTIES.contains(property.getName())) {
                    return false;
                }
            }
            return true;
        }
        if (pair(a, b, Blocks.GRASS_BLOCK, Blocks.DIRT) || pair(a, b, Blocks.DIRT_PATH, Blocks.DIRT)
                || pair(a, b, Blocks.FARMLAND, Blocks.DIRT) || pair(a, b, Blocks.ICE, Blocks.WATER)
                || pair(a, b, Blocks.FROSTED_ICE, Blocks.WATER) || pair(a, b, Blocks.SNOW, Blocks.AIR)) {
            return true;
        }
        // 水と溶岩が広がる・引く
        if (want.isAir() && b instanceof LiquidBlock || now.isAir() && a instanceof LiquidBlock && !want.getFluidState().isSource()) {
            return true;
        }
        // 火
        if (want.isAir() && b instanceof BaseFireBlock || now.isAir() && a instanceof BaseFireBlock) {
            return true;
        }
        // 伸びる草木（サトウキビ・竹・つる・昆布）
        if (want.isAir() && (b == Blocks.SUGAR_CANE || b == Blocks.BAMBOO || b == Blocks.BAMBOO_SAPLING || b == Blocks.VINE)) {
            return true;
        }
        if ((a == Blocks.WATER || a == Blocks.KELP || a == Blocks.KELP_PLANT) && (b == Blocks.KELP || b == Blocks.KELP_PLANT)
                || a == Blocks.BAMBOO_SAPLING && b == Blocks.BAMBOO) {
            return true;
        }
        // 銅は時間とともに錆びる（ろうを塗っていないもの）
        Block next = a;
        for (int i = 0; i < 3; i++) {
            Optional<Block> step = WeatheringCopper.getNext(next);
            if (step.isEmpty()) {
                break;
            }
            next = step.get();
            if (next == b) {
                return naturalChange(next.withPropertiesOf(want), now);
            }
        }
        return false;
    }

    private static boolean pair(Block a, Block b, Block x, Block y) {
        return a == x && b == y || a == y && b == x;
    }

    /** ブロックの中身を、ブロックに読ませてから書き出した形（書き方の違いをそろえる）。中身のないブロックなら null */
    @Nullable
    private static CompoundTag normalizedBlockEntity(ServerLevel level, BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }
        try {
            BlockEntity fresh = entityBlock.newBlockEntity(pos, state);
            if (fresh == null) {
                return null;
            }
            fresh.setLevel(level);
            if (nbt != null) {
                fresh.load(nbt.copy());
            }
            return stripBlock(fresh.saveWithoutMetadata());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static CompoundTag stripBlock(CompoundTag tag) {
        for (String key : VOLATILE_BLOCK_KEYS) {
            tag.remove(key);
        }
        return tag;
    }

    /** 見る「もの」：額縁・光る額縁・絵・防具立て・表示の「もの」（落ちたアイテム・動物・乗り物・リードの結び目などは見ない） */
    private static boolean decoration(Entity e) {
        return e instanceof HangingEntity && !(e instanceof LeashFenceKnotEntity) || e instanceof ArmorStand || e instanceof Display;
    }

    private static BlockPos cellOf(Entity e) {
        return e instanceof HangingEntity hanging ? hanging.getPos() : e.blockPosition();
    }

    /** 構造物の「もの」を、置いたときと同じ所に作る（ワールドには入れない） */
    @Nullable
    private static Entity create(ServerLevel level, BlockPos at, EntityInfo info) {
        CompoundTag nbt = info.nbt().copy();
        Vec3 w = Vec3.atLowerCornerOf(at).add(info.pos());
        nbt.put("Pos", doubles(w));
        nbt.remove("UUID");
        if (nbt.contains("TileX")) {
            BlockPos cell = at.offset(info.cell());
            nbt.putInt("TileX", cell.getX());
            nbt.putInt("TileY", cell.getY());
            nbt.putInt("TileZ", cell.getZ());
        }
        try {
            Optional<Entity> made = EntityType.create(nbt, level);
            if (made.isEmpty()) {
                return null;
            }
            Entity e = made.get();
            e.setPos(w.x, w.y, w.z);
            return e;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 「もの」を比べる形：置き直すと変わる値を外し、飾りの守りをそろえ、世界の中の位置（1/16 にそろえる）を足す */
    private static CompoundTag signature(Entity e) {
        CompoundTag tag = entityNbt(e);
        Vec3 p = e.position();
        ListTag at = new ListTag();
        at.add(DoubleTag.valueOf(Math.round(p.x * 16.0) / 16.0));
        at.add(DoubleTag.valueOf(Math.round(p.y * 16.0) / 16.0));
        at.add(DoubleTag.valueOf(Math.round(p.z * 16.0) / 16.0));
        tag.put("at", at);
        return tag;
    }

    /** 書き出す「もの」の中身（置き直すと変わる値を外す。額縁・絵と防具立てには飾りの守りを付け直す） */
    private static CompoundTag entityNbt(Entity e) {
        CompoundTag tag = new CompoundTag();
        if (!e.save(tag)) {
            tag.putString("id", String.valueOf(EntityType.getKey(e.getType())));
        }
        for (String key : VOLATILE_ENTITY_KEYS) {
            tag.remove(key);
        }
        if (e instanceof HangingEntity) {
            tag.putBoolean("Invulnerable", true);
        }
        if (e instanceof ItemFrame) {
            tag.putBoolean("Fixed", true);
            tag.putFloat("ItemDropChance", 0.0F);
        } else if (e instanceof ArmorStand) {
            tag.putBoolean("Invulnerable", true);
            tag.putBoolean("NoGravity", true);
            tag.putInt("DisabledSlots", ALL_SLOTS_DISABLED);
        }
        return tag;
    }

    /** ワールドの「もの」を、構造物の中の「もの」として書く（at は構造物の (0, 0, 0)、cell は構造物の中のマス） */
    private static EntityInfo exportInfo(Entity e, BlockPos at, BlockPos cell) {
        CompoundTag nbt = entityNbt(e);
        Vec3 pos;
        if (e instanceof HangingEntity hanging) {
            // 掛かっているマスの真ん中（置くとき、ゲームはこのマスから額縁・絵の位置を決め直す。大きな絵もずれない）
            pos = Vec3.atCenterOf(hanging.getPos()).subtract(Vec3.atLowerCornerOf(at));
            nbt.putInt("TileX", cell.getX());
            nbt.putInt("TileY", cell.getY());
            nbt.putInt("TileZ", cell.getZ());
        } else {
            pos = e.position().subtract(Vec3.atLowerCornerOf(at));
        }
        return new EntityInfo(pos, cell, nbt);
    }

    private static boolean sameMultiset(List<CompoundTag> a, List<CompoundTag> b) {
        if (a.size() != b.size()) {
            return false;
        }
        List<CompoundTag> rest = new ArrayList<>(b);
        for (CompoundTag tag : a) {
            if (!rest.remove(tag)) {
                return false;
            }
        }
        return true;
    }

    // ================================================================ 建て直す前の確かめ

    /** 最後に建てた街の、書き出していない直しがある施設（見どころの名前。並びは置く順番） */
    static List<String> unsavedFacilities(ServerLevel level, BlockPos origin, boolean grown) throws EditsException {
        Analysis a = analyze(level, origin, grown, false);
        Set<String> out = new LinkedHashSet<>();
        for (Piece p : a.pieces) {
            if (p.changed()) {
                out.add(p.placement.facility());
            }
        }
        return new ArrayList<>(out);
    }

    // ================================================================ 書き出す

    static int export(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CityCommands.CityData data = CityCommands.CityData.get(level);
        if (!data.built) {
            source.sendFailure(Component.translatable("commands.amemaru.city.not_built"));
            return 0;
        }
        BlockPos origin = data.origin;
        boolean grown = data.grown;
        CityLoad.whenLoaded(source, level, List.of(origin), () -> exportLoaded(source, level, origin, grown));
        return 1;
    }

    private static void exportLoaded(CommandSourceStack source, ServerLevel level, BlockPos origin, boolean grown) {
        long started = System.currentTimeMillis();
        Analysis a;
        try {
            a = analyze(level, origin, grown, true);
        } catch (EditsException e) {
            source.sendFailure(Component.translatable("commands.amemaru.city.edits_bad", e.getMessage()));
            return;
        }
        if (a.missing != null) {
            source.sendFailure(Component.translatable("commands.amemaru.city.missing", a.missing));
            return;
        }
        List<Piece> changed = new ArrayList<>();
        Map<Piece, Edits> saved = new LinkedHashMap<>();
        try {
            for (Piece p : a.pieces) {
                if (!p.changed()) {
                    continue;
                }
                Edits diff = collect(level, p);
                Edits edits = readEdits(level, p.placement);
                edits.putAll(diff);
                prune(level, p, edits);
                writeEdits(level, p.placement, edits);
                // 最後に建てた形の控えも、今の形にする（書き出した直しは、もう「書き出していない直し」ではない）
                apply(p, diff);
                Path file = builtFile(level.getServer(), p.placement.name());
                Files.createDirectories(file.getParent());
                NbtIo.writeCompressed(toTag(p), file.toFile());
                changed.add(p);
                saved.put(p, edits);
            }
        } catch (IOException e) {
            AmemaruMod.LOGGER.error("[amemaru] city export failed", e);
            source.sendFailure(Component.translatable("commands.amemaru.city.export.failed", String.valueOf(e.getMessage())));
            return;
        } catch (EditsException e) {
            source.sendFailure(Component.translatable("commands.amemaru.city.edits_bad", e.getMessage()));
            return;
        }
        int files = countEditFiles(level);
        AdminCommands.log(source, "city export " + changed.size() + " structures, " + files + " edit files, outside " + a.outsideCells
                + " cells / " + a.outsideEntities + " entity cells (" + (System.currentTimeMillis() - started) + " ms)");
        if (changed.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.city.export.none"), true);
        } else {
            int count = changed.size();
            source.sendSuccess(() -> Component.translatable("commands.amemaru.city.export.done", count), true);
            for (Piece p : changed) {
                Edits edits = saved.get(p);
                int cellsNow = p.changedCells.size();
                Set<BlockPos> entityNow = new HashSet<>(p.entityChanges.keySet());
                entityNow.addAll(p.entityDrops);
                int entitiesNow = entityNow.size();
                source.sendSuccess(() -> Component.translatable("commands.amemaru.city.export.piece",
                        CityCommands.placeName(p.placement.facility()), p.placement.name(), cellsNow, entitiesNow,
                        edits.cells.size(), edits.entityCells.size()), true);
            }
        }
        if (files > 0) {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.city.export.files", files, EDITS_FOLDER), true);
        }
        if (a.outsideCells > 0) {
            StringBuilder examples = new StringBuilder();
            for (BlockPos e : a.outsideExamples) {
                if (!examples.isEmpty()) {
                    examples.append(" / ");
                }
                examples.append(e.getX()).append(", ").append(e.getY()).append(", ").append(e.getZ());
            }
            int count = a.outsideCells;
            source.sendSuccess(() -> Component.translatable("commands.amemaru.city.export.outside", count, examples.toString()), true);
        }
        if (a.outsideEntities > 0) {
            int count = a.outsideEntities;
            source.sendSuccess(() -> Component.translatable("commands.amemaru.city.export.outside_entities", count), true);
        }
    }

    /** 今回の直し（最後に建てた形と違う所の、今のワールドの形） */
    private static Edits collect(ServerLevel level, Piece p) {
        Edits diff = new Edits();
        for (BlockPos c : p.changedCells) {
            BlockPos w = p.at.offset(c);
            BlockEntity be = level.getBlockEntity(w);
            diff.cells.put(c, new CellEdit(level.getBlockState(w), be == null ? null : stripBlock(be.saveWithId())));
        }
        for (Map.Entry<BlockPos, List<Entity>> e : p.entityChanges.entrySet()) {
            List<EntityInfo> list = new ArrayList<>();
            for (Entity entity : e.getValue()) {
                list.add(exportInfo(entity, p.at, e.getKey()));
            }
            diff.entityCells.put(e.getKey(), list);
        }
        for (BlockPos c : p.entityDrops) {
            diff.entityCells.putIfAbsent(c, List.of());
        }
        return diff;
    }

    /** Modに入っている形と同じになった直しを外す（直して元に戻した所・取り込み済みの所） */
    private static void prune(ServerLevel level, Piece p, Edits edits) {
        CompoundTag shippedTag = shippedTag(level, p.placement);
        if (shippedTag == null) {
            return;
        }
        Piece s = parse(blocks(level), p.placement, 0, p.at, shippedTag);
        edits.cells.entrySet().removeIf(e -> {
            BlockPos c = e.getKey();
            if (!s.inside(c)) {
                return false;
            }
            int k = s.idx(c.getX(), c.getY(), c.getZ());
            BlockState state = s.states[k];
            BlockState edit = e.getValue().state();
            BlockPos w = p.at.offset(c);
            if (state == null || state != edit && shaped(level, w, state) != edit) {
                return false;
            }
            CompoundTag a = normalizedBlockEntity(level, w, edit, s.nbts[k]);
            CompoundTag b = normalizedBlockEntity(level, w, edit, e.getValue().nbt());
            return a == null ? b == null : a.equals(b);
        });
        edits.entityCells.entrySet().removeIf(e -> {
            List<CompoundTag> shipped = new ArrayList<>();
            for (EntityInfo info : s.entities) {
                if (info.cell().equals(e.getKey())) {
                    Entity entity = create(level, s.at, info);
                    if (entity != null && decoration(entity)) {
                        shipped.add(signature(entity));
                    }
                }
            }
            List<CompoundTag> edit = new ArrayList<>();
            for (EntityInfo info : e.getValue()) {
                Entity entity = create(level, p.at, info);
                if (entity != null) {
                    edit.add(signature(entity));
                }
            }
            return sameMultiset(shipped, edit);
        });
    }

    private static int countEditFiles(ServerLevel level) {
        Path dir = cityDir(level.getServer()).resolve("edits");
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int n = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path ignored : files) {
                n++;
            }
        } catch (IOException e) {
            AmemaruMod.LOGGER.error("[amemaru] city: cannot list {}", dir, e);
        }
        return n;
    }

    // ================================================================ 小物

    private static BlockPos pos(JsonArray a) {
        return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }

    private static JsonArray json(BlockPos p) {
        JsonArray a = new JsonArray();
        a.add(p.getX());
        a.add(p.getY());
        a.add(p.getZ());
        return a;
    }

    private static ListTag ints(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x));
        list.add(IntTag.valueOf(y));
        list.add(IntTag.valueOf(z));
        return list;
    }

    private static ListTag doubles(Vec3 v) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(v.x));
        list.add(DoubleTag.valueOf(v.y));
        list.add(DoubleTag.valueOf(v.z));
        return list;
    }
}
