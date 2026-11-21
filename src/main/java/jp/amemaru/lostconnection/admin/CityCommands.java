package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 確かめ用のコマンド {@code /amemaru admin city …}（11月21日）：コネリアの街の11施設の構造物を、地図を組むときの位置と順番で
 * まとめて建てて、見て回る。
 *
 * <ul>
 *   <li>{@code city build [grown]}：中央広場の真ん中（大結晶の場所）を世界の (0, 64, 0) にして建てる。{@code grown} なら、
 *       育ったあとに重ねる形（料理屋・露店の8区画・記憶の相談所・湖畔の釣り場・第2章の氷・ハイ＆ロー・魚レース・記念碑・古代炉）も重ねる</li>
 *   <li>{@code city build at <x y z> [grown]}：真ん中の場所を決めて建てる（CIの写真では空に建てる）</li>
 *   <li>{@code city tp [場所] [人]}：見どころへ移る（場所を省くと一覧。人を省くと自分）。最後に建てた所が基準</li>
 *   <li>{@code city export}：クリエイティブで直した所を、構造物ごとにワールドの {@code generated/amemaru/city/edits/} へ書き出す
 *       （{@link CityEdits}）。このワールドの {@code city build} は、Modに入っている形に書き出した直しを重ねて建てる。書き出していない
 *       直しがあると {@code city build} は止まる（最後に {@code force} を付けると、書き出していない直しを捨てて建て直す）</li>
 *   <li>建てる・書き出すのは、街の範囲の区画と「もの」が読み込まれてから（{@link CityLoad}。遠くにいても使える）</li>
 * </ul>
 *
 * <p>置き場所・順番・道と広場の範囲は {@code tools/mapgen/check_city.py}（街全体の通しの確かめ）と同じ。座標は、道のブロックの高さが 0、
 * x が東、z が南。まだ作っていない道と広場（ゾーニングの図）は土の道、まだ作っていない城壁（半径112〜116）は砂利で、仮に地面に示す。
 * 建てる範囲（x −144〜135、z −184〜127）は、道の高さの13下から地面を作り直し、64上まで片づける。範囲の中の「もの」（額縁・防具立て・
 * 動物など。プレイヤーは除く）は消してから置き直す（建て直しても重ならない）。</p>
 */
public final class CityCommands {
    /** 何もしないで建てたときの、中央広場の真ん中（確かめ用の平らなワールドの地面の高さ64）。 */
    public static final BlockPos DEFAULT_ORIGIN = new BlockPos(0, 64, 0);
    /** 建て終えた回数（CIの写真が、建て終わるのを待つのに使う。同じJVMの中のサーバー） */
    public static volatile int builds;

    static final int MIN_X = -144;
    static final int MAX_X = 135;
    static final int MIN_Z = -184;
    static final int MAX_Z = 127;
    static final int GROUND_DEPTH = 13;
    static final int CLEAR_HEIGHT = 64;
    private static final double PLAZA_R = 22.5;
    private static final double WALLROAD_IN = 107.0;
    private static final double WALL_IN = 112.0;
    private static final double WALL_OUT = 116.0;

    /**
     * 構造物（{@code map/conelia/} の後ろ）と、その (0, 0, 0) を置く所。facility は施設（見どころの名前）。grown は育ったあとに
     * 重ねる形。並びは置く順番。
     */
    record Placement(String name, String facility, int x, int y, int z, boolean grown) {
    }

    static final List<Placement> PLACEMENTS = List.of(
            // Phase 5：街並み（道・中央広場と大結晶・城壁と門・結界の柱・家・倉庫・灯り・木。tools/mapgen/build_city.py が
            // 書き出す 48×48 の区画）。地面の後、11の施設より先に置く（施設が上に重なる。街並みは施設の書くマスを書かない）
            new Placement("town/t0_2", "town", -144, -7, -88, false),
            new Placement("town/t0_3", "town", -144, -7, -40, false),
            new Placement("town/t0_4", "town", -144, -7, 8, false),
            new Placement("town/t1_1", "town", -96, -7, -136, false),
            new Placement("town/t1_2", "town", -96, -7, -88, false),
            new Placement("town/t1_3", "town", -96, -7, -40, false),
            new Placement("town/t1_4", "town", -96, -7, 8, false),
            new Placement("town/t1_5", "town", -96, -7, 56, false),
            new Placement("town/t1_6", "town", -96, -7, 104, false),
            new Placement("town/t2_1", "town", -48, -7, -136, false),
            new Placement("town/t2_2", "town", -48, -7, -88, false),
            new Placement("town/t2_3", "town", -48, -7, -40, false),
            new Placement("town/t2_4", "town", -48, -7, 8, false),
            new Placement("town/t2_5", "town", -48, -7, 56, false),
            new Placement("town/t2_6", "town", -48, -7, 104, false),
            new Placement("town/t3_1", "town", 0, -7, -136, false),
            new Placement("town/t3_2", "town", 0, -7, -88, false),
            new Placement("town/t3_3", "town", 0, -7, -40, false),
            new Placement("town/t3_4", "town", 0, -7, 8, false),
            new Placement("town/t3_5", "town", 0, -7, 56, false),
            new Placement("town/t3_6", "town", 0, -7, 104, false),
            new Placement("town/t4_1", "town", 48, -7, -136, false),
            new Placement("town/t4_2", "town", 48, -7, -88, false),
            new Placement("town/t4_3", "town", 48, -7, -40, false),
            new Placement("town/t4_4", "town", 48, -7, 8, false),
            new Placement("town/t4_5", "town", 48, -7, 56, false),
            new Placement("town/t4_6", "town", 48, -7, 104, false),
            new Placement("town/t5_2", "town", 96, -7, -88, false),
            new Placement("town/t5_3", "town", 96, -7, -40, false),
            new Placement("town/t5_4", "town", 96, -7, 8, false),
            new Placement("town/t5_5", "town", 96, -7, 56, false),
            new Placement("guild_hall", "guild", 6, 0, -100, false),
            new Placement("personal_storage", "storage", 22, -7, -82, false),        // ギルドの後に置く
            new Placement("weapon_workshop", "weapon", 79, 0, -26, false),
            new Placement("armor_workshop", "armor", 60, 0, 4, false),
            new Placement("accessory_workshop", "accessory", 22, 0, -26, false),
            new Placement("market_north", "market", -18, 0, 22, false),
            new Placement("market_south", "market", -18, 0, 60, false),
            new Placement("free_market", "free_market", -48, 0, 60, false),
            new Placement("church", "church", -105, 0, -22, false),
            new Placement("church_garden", "church", -96, 0, 16, false),
            new Placement("fishing_pond", "fishing", -95, -9, 23, false),            // 記憶の庭の後に置く
            new Placement("water_gate", "lake", -131, -9, 48, false),
            new Placement("fishing_alley", "fishing", -66, -1, 66, false),
            new Placement("casino", "casino", 5, -3, 68, false),                     // 商店街の南の後に置く
            new Placement("tower_entrance", "tower", -22, 1, -177, false),
            new Placement("tower_plaza", "tower", -23, -1, -147, false),
            new Placement("weapon_forge_lit", "weapon", 92, 1, -23, true),
            new Placement("market_restaurant_open", "market", -18, 0, 22, true),
            new Placement("free_market_expanded", "free_market", -48, 0, 60, true),
            new Placement("church_counsel", "church", -105, 0, -22, true),
            new Placement("fishing_lakeside", "lake", -131, -9, 48, true),
            new Placement("fishing_pond_frozen", "fishing", -95, -9, 23, true),
            new Placement("casino_highlow", "casino", 5, -3, 68, true),
            new Placement("casino_fishrace", "casino", 5, -3, 68, true),
            new Placement("tower_plaza_monument", "tower", -23, -1, -147, true));

    /** ゾーニングの図の道と広場（x0, z0, x1, z1。両端を含む）。中央広場と城壁沿いの道は丸いので別に見る。 */
    private static final int[][] ROADS = {
            {-5, -116, 5, -22}, {-4, 22, 4, 115}, {22, -4, 115, 4}, {-59, -4, -22, 4},
            {-59, -59, -6, -53}, {6, -59, 59, -53}, {-59, 53, -5, 59}, {5, 53, 59, 59},
            {-59, -52, -53, -5}, {-59, 5, -53, 52}, {53, -52, 59, -5}, {53, 5, 59, 52},
            {-70, -12, -60, 12}, {6, -86, 13, -70}, {5, 71, 12, 97}, {5, 68, 46, 70},
            {-72, 23, -60, 25}, {-65, 66, -63, 77}, {-62, 75, -49, 77}, {-23, -147, 23, -116}};

    /** 見どころ（足もとの所。中央広場の真ん中からの位置。yaw 0 が南、90 が西、180 が北、−90 が東。pitch は下向きが正）。 */
    public record View(String id, double x, double y, double z, float yaw, float pitch) {
    }

    public static final List<View> VIEWS = List.of(
            new View("plaza", 0.5, 1, 12.5, 180, -5),
            new View("guild", -1.5, 1, -78.5, -90, -25),
            new View("storage", 49.5, 1, -45.5, 180, -20),
            new View("weapon", 90.5, 1, 6.5, 180, -20),
            new View("armor", 71.5, 1, -6.5, 0, -20),
            new View("accessory", 31.5, 1, 6.5, 180, -20),
            new View("market", 0.5, 1, 17.5, 0, 0),
            new View("free_market", -33.5, 1, 51.5, 0, -5),
            new View("church", -50.5, 1, 0.5, 90, -25),
            new View("fishing", -49.5, 1, 56.5, 90, 5),
            new View("lake", -99.5, 1, 88.5, 140, 15),
            new View("casino", -1.5, 1, 84.5, -90, -25),
            new View("tower", 0.5, 1, -109.5, 180, -20),
            // Phase 5：街並み（北西の環状路の家並み・南の門・北東の倉庫地区・北東の角の塔と城壁）
            new View("houses", -30.5, 1, -56.5, 90, -5),
            new View("gate_south", 0.5, 1, 100.5, 0, -10),
            new View("warehouses", 56.5, 1, -33.5, -90, -10),
            new View("wall", 72.5, 1, -72.5, -135, -30),
            // 上から：描く距離12チャンクでも霧に隠れないよう、北と南の半分ずつ（高さ110）
            new View("top_n", -13.5, 110, -100.5, 180, 90),
            new View("top_s", -13.5, 110, 26.5, 180, 90),
            new View("aerial_se", 110.5, 70, 110.5, 135, 25),
            new View("aerial_nw", -125.5, 70, -150.5, -45, 25),
            // 育ったあとに重ねる形の見どころ
            new View("restaurant", 2.5, 1, 28.5, 90, -5),
            new View("stalls", -33.5, 30, 76.5, 180, 90),
            new View("frozen_pond", -79.5, 30, 46.5, 180, 90),
            new View("highlow", 22.5, 3, 92.5, 0, 20),
            new View("fishrace", 25.5, 10, 80.5, 0, 35),
            new View("monument", -5.5, 2, -127.5, 108, -5),
            new View("forge", 93.5, 1, -14.5, 180, -10));

    private static final SuggestionProvider<CommandSourceStack> PLACES = (context, builder) -> {
        for (View view : VIEWS) {
            builder.suggest(view.id());
        }
        return builder.buildFuture();
    };

    private CityCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> cityCommand() {
        return Commands.literal("city")
                .then(Commands.literal("build")
                        .executes(ctx -> build(ctx.getSource(), DEFAULT_ORIGIN, false, false))
                        .then(Commands.literal("force")
                                .executes(ctx -> build(ctx.getSource(), DEFAULT_ORIGIN, false, true)))
                        .then(Commands.literal("grown")
                                .executes(ctx -> build(ctx.getSource(), DEFAULT_ORIGIN, true, false))
                                .then(Commands.literal("force")
                                        .executes(ctx -> build(ctx.getSource(), DEFAULT_ORIGIN, true, true))))
                        .then(Commands.literal("at")
                                .then(Commands.argument("origin", BlockPosArgument.blockPos())
                                        .executes(ctx -> build(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "origin"), false, false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> build(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "origin"), false, true)))
                                        .then(Commands.literal("grown")
                                                .executes(ctx -> build(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "origin"), true, false))
                                                .then(Commands.literal("force")
                                                        .executes(ctx -> build(ctx.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "origin"), true, true)))))))
                .then(Commands.literal("export")
                        .executes(ctx -> CityEdits.export(ctx.getSource())))
                .then(Commands.literal("tp")
                        .executes(ctx -> listPlaces(ctx.getSource()))
                        .then(Commands.argument("place", StringArgumentType.word()).suggests(PLACES)
                                .executes(ctx -> tp(ctx.getSource(), StringArgumentType.getString(ctx, "place"),
                                        List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> tp(ctx.getSource(), StringArgumentType.getString(ctx, "place"),
                                                EntityArgument.getPlayers(ctx, "targets"))))));
    }

    // ---------------------------------------------------------------- 建てる

    private static int build(CommandSourceStack source, BlockPos origin, boolean grown, boolean force) {
        ServerLevel level = source.getLevel();
        if (origin.getY() - GROUND_DEPTH < level.getMinBuildHeight() || origin.getY() + CLEAR_HEIGHT >= level.getMaxBuildHeight()) {
            source.sendFailure(Component.translatable("commands.amemaru.city.height",
                    level.getMinBuildHeight() + GROUND_DEPTH, level.getMaxBuildHeight() - CLEAR_HEIGHT - 1));
            return 0;
        }
        CityData before = CityData.get(level);
        // 前に建てた街に、書き出していない直しがあれば建て直さない（直した所が消えるため）。force なら確かめない
        boolean check = before.built && !force;
        BlockPos at = origin.immutable();
        List<BlockPos> areas = new ArrayList<>();
        areas.add(at);
        if (check && !before.origin.equals(at)) {
            areas.add(before.origin);
        }
        // 区画と「もの」が読み込まれてから建てる（古い額縁・防具立てを残さず消すため）
        CityLoad.whenLoaded(source, level, areas, () -> buildLoaded(source, level, at, grown, check));
        return 1;
    }

    private static void buildLoaded(CommandSourceStack source, ServerLevel level, BlockPos origin, boolean grown, boolean check) {
        if (check) {
            CityData before = CityData.get(level);
            List<String> unsaved;
            try {
                unsaved = CityEdits.unsavedFacilities(level, before.origin, before.grown);
            } catch (CityEdits.EditsException e) {
                source.sendFailure(Component.translatable("commands.amemaru.city.edits_bad", e.getMessage()));
                return;
            }
            if (!unsaved.isEmpty()) {
                source.sendFailure(Component.translatable("commands.amemaru.city.unsaved", facilityNames(unsaved)));
                return;
            }
        }
        // 建てる形：Modに入っている形に、このワールドで書き出した直しを重ねたもの（CityEdits）
        StructureTemplateManager manager = level.getStructureManager();
        List<CompoundTag> tags = new ArrayList<>();
        List<StructureTemplate> templates = new ArrayList<>();
        List<String> edited = new ArrayList<>();
        for (Placement placement : PLACEMENTS) {
            if (placement.grown() && !grown) {
                tags.add(null);
                templates.add(null);
                continue;
            }
            CompoundTag tag;
            try {
                tag = CityEdits.buildTag(level, placement);
            } catch (CityEdits.EditsException e) {
                source.sendFailure(Component.translatable("commands.amemaru.city.edits_bad", e.getMessage()));
                return;
            }
            if (tag == null) {
                source.sendFailure(Component.translatable("commands.amemaru.city.missing", placement.name()));
                return;
            }
            if (CityEdits.hasEdits(level, placement) && !edited.contains(placement.facility())) {
                edited.add(placement.facility());
            }
            tags.add(tag);
            // 置くための写し（額縁の掛かっているマスを世界の位置に、向きを −180〜180 に。控えは tag のまま）
            templates.add(manager.readStructure(CityEdits.placementTag(tag, origin.offset(placement.x(), placement.y(), placement.z()))));
        }
        long started = System.currentTimeMillis();
        int x0 = origin.getX() + MIN_X;
        int x1 = origin.getX() + MAX_X;
        int z0 = origin.getZ() + MIN_Z;
        int z1 = origin.getZ() + MAX_Z;
        // 範囲の区画（読み込み済み）。光の計算が終わるまで、しばらく読み込んだままにする
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        CityLoad.hold(level, origin);
        // 範囲の中の「もの」を消す（構造物の額縁・防具立てが、建て直すたびに重ならないように）
        AABB box = new AABB(x0, origin.getY() - GROUND_DEPTH, z0, x1 + 1, origin.getY() + CLEAR_HEIGHT + 1, z1 + 1);
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            entity.discard();
        }
        // 地面（13下から）と、上の片づけ（64上まで）。同じブロックならそのまま（平らなワールドでは書かない）
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                BlockState surface = surface(x, z);
                for (int dy = -GROUND_DEPTH; dy <= CLEAR_HEIGHT; dy++) {
                    BlockState want = dy < -3 ? stone : dy < 0 ? dirt : dy == 0 ? surface : air;
                    pos.set(origin.getX() + x, origin.getY() + dy, origin.getZ() + z);
                    BlockState now = level.getBlockState(pos);
                    if (now != want) {
                        if (now.hasBlockEntity()) {
                            // 建て直すとき、樽・書見台などの中身が落ちないように、先に空にする（/fill と同じ）
                            Clearable.tryClear(level.getBlockEntity(pos));
                        }
                        level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        changed++;
                    }
                }
            }
        }
        // 構造物（置く順番どおり）
        StructurePlaceSettings settings = new StructurePlaceSettings();
        for (int i = 0; i < PLACEMENTS.size(); i++) {
            Placement placement = PLACEMENTS.get(i);
            if (templates.get(i) == null) {
                continue;
            }
            BlockPos at = origin.offset(placement.x(), placement.y(), placement.z());
            templates.get(i).placeInWorld(level, at, at, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        }
        // 建てた形を控える（あとで、直した所を見分けるもと）
        CityEdits.saveBuilt(level, tags);
        CityData data = CityData.get(level);
        data.origin = origin.immutable();
        data.grown = grown;
        data.built = true;
        data.setDirty();
        // Phase 5：街のNPCを立つ所に置き、塔の入口の「塔の灯り」と札を、今の解放に合わせる
        int npcs = jp.amemaru.lostconnection.npc.NpcService.spawnAll(level, origin);
        jp.amemaru.lostconnection.tower.TowerHall.update(level.getServer());
        builds++;
        long millis = System.currentTimeMillis() - started;
        AdminCommands.log(source, "city build " + origin.toShortString() + (grown ? " grown" : "") + " (" + changed + " ground blocks, "
                + millis + " ms, " + npcs + " npcs" + (edited.isEmpty() ? "" : ", edits: " + String.join(",", edited)) + ")");
        Component form = Component.translatable(grown ? "commands.amemaru.city.form.grown" : "commands.amemaru.city.form.start");
        source.sendSuccess(() -> Component.translatable("commands.amemaru.city.built", form,
                origin.getX(), origin.getY(), origin.getZ()), true);
        if (!edited.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.city.built_edits", facilityNames(edited)), true);
        }
    }

    /** 最後に建てた街で、書き出していない直しがある施設（自動テスト用。街を建てていなければ空） */
    public static List<String> unsavedFacilities(ServerLevel level) {
        CityData data = CityData.get(level);
        if (!data.built) {
            return List.of();
        }
        try {
            return CityEdits.unsavedFacilities(level, data.origin, data.grown);
        } catch (CityEdits.EditsException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** このワールドに街を建てていれば、中央広場の真ん中（大結晶の場所。道のブロックの高さ）。Phase 5の塔の門・NPC・目覚める所の基準。 */
    public static java.util.Optional<BlockPos> builtOrigin(ServerLevel level) {
        CityData data = CityData.get(level);
        return data.built ? java.util.Optional.of(data.origin) : java.util.Optional.empty();
    }

    /** その構造物（map/conelia/ の後ろの名前）に、書き出した直しのファイルがあるか（自動テスト用） */
    public static boolean hasEditFile(ServerLevel level, String structure) {
        return CityEdits.hasEdits(level, structure);
    }

    /** 施設（見どころの名前）を「、」でつなぐ */
    static MutableComponent facilityNames(List<String> facilities) {
        MutableComponent names = Component.empty();
        for (int i = 0; i < facilities.size(); i++) {
            if (i > 0) {
                names.append(Component.translatable("commands.amemaru.separator"));
            }
            names.append(placeName(facilities.get(i)));
        }
        return names;
    }

    /** 教会の呼び戻しの円の真ん中の足もと（中央広場の真ん中から。tools/mapgen/build_church.py の CIRCLE） */
    private static final double CHURCH_CIRCLE_X = -92 + 0.5;
    private static final double CHURCH_CIRCLE_Y = 3;
    private static final double CHURCH_CIRCLE_Z = 0 + 0.5;

    /**
     * このワールドに確かめ用の街を建てていれば、教会の呼び戻しの円の真ん中（足もと）。Phase 4の「教会での復活」の場所の
     * 初期値（{@code /amemaru admin church set} で決めた場所があれば、そちらが先）。
     */
    public static java.util.Optional<net.minecraft.world.phys.Vec3> churchCircle(ServerLevel level) {
        CityData data = CityData.get(level);
        if (!data.built) {
            return java.util.Optional.empty();
        }
        BlockPos o = data.origin;
        return java.util.Optional.of(new net.minecraft.world.phys.Vec3(o.getX() + CHURCH_CIRCLE_X, o.getY() + CHURCH_CIRCLE_Y,
                o.getZ() + CHURCH_CIRCLE_Z));
    }

    /**
     * 道の高さのブロック：ゾーニングの図の道と広場は土の道、城壁の所は砂利、ほかは草。Phase 5 からは街並みの区画が、城壁の内の
     * 地面（道・広場・空き地）と城壁と門の前の道を上に書くので、見えるのは城壁の外の草地だけ。
     */
    static BlockState surface(int x, int z) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        if (r <= PLAZA_R) {
            return Blocks.DIRT_PATH.defaultBlockState();
        }
        for (int[] road : ROADS) {
            if (road[0] <= x && x <= road[2] && road[1] <= z && z <= road[3]) {
                return Blocks.DIRT_PATH.defaultBlockState();
            }
        }
        if (WALLROAD_IN <= r && r < WALL_IN) {
            return Blocks.DIRT_PATH.defaultBlockState();
        }
        if (WALL_IN <= r && r < WALL_OUT) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    // ---------------------------------------------------------------- 見て回る

    private static int tp(CommandSourceStack source, String id, Collection<ServerPlayer> players) {
        View view = null;
        for (View v : VIEWS) {
            if (v.id().equals(id)) {
                view = v;
            }
        }
        if (view == null) {
            source.sendFailure(Component.translatable("commands.amemaru.city.unknown_place", id));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos o = CityData.get(level).origin;
        for (ServerPlayer player : players) {
            player.teleportTo(level, o.getX() + view.x(), o.getY() + view.y(), o.getZ() + view.z(), view.yaw(), view.pitch());
        }
        View chosen = view;
        source.sendSuccess(() -> Component.translatable("commands.amemaru.city.tp", placeName(chosen.id())), false);
        return players.size();
    }

    private static int listPlaces(CommandSourceStack source) {
        MutableComponent list = Component.empty();
        for (int i = 0; i < VIEWS.size(); i++) {
            if (i > 0) {
                list.append(Component.translatable("commands.amemaru.separator"));
            }
            String id = VIEWS.get(i).id();
            list.append(Component.literal(id).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("（")).append(placeName(id)).append(Component.literal("）"));
        }
        source.sendSuccess(() -> Component.translatable("commands.amemaru.city.places", list), false);
        return VIEWS.size();
    }

    static Component placeName(String id) {
        return Component.translatable("commands.amemaru.city.place." + id);
    }

    // ---------------------------------------------------------------- 最後に建てた所（ワールドの data の amemaru_city_check.dat）

    static final class CityData extends SavedData {
        private static final String NAME = "amemaru_city_check";
        BlockPos origin = DEFAULT_ORIGIN;
        boolean grown;
        /** このワールドに街を建てたか */
        boolean built;

        static CityData load(CompoundTag tag) {
            CityData data = new CityData();
            if (tag.contains("x")) {
                data.origin = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                data.built = !tag.contains("built") || tag.getBoolean("built");
            }
            data.grown = tag.getBoolean("grown");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("x", origin.getX());
            tag.putInt("y", origin.getY());
            tag.putInt("z", origin.getZ());
            tag.putBoolean("grown", grown);
            tag.putBoolean("built", built);
            return tag;
        }

        static CityData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(CityData::load, CityData::new, NAME);
        }
    }
}
