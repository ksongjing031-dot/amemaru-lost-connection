package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.AmemaruMod;
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
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 確かめ用のコマンド {@code /amemaru admin city …}（11月21日）：コネリアの街の11施設の構造物を、地図を組むときの位置と順番で
 * まとめて建てて、見て回る。
 *
 * <ul>
 *   <li>{@code city build [grown]}：中央広場の真ん中（大結晶の場所）を世界の (0, 64, 0) にして建てる。{@code grown} なら、
 *       育ったあとに重ねる形（料理屋・露店の8区画・記憶の相談所・湖畔の釣り場・第2章の氷・ハイ＆ロー・魚レース・記念碑・古代炉）も重ねる</li>
 *   <li>{@code city build at <x y z> [grown]}：真ん中の場所を決めて建てる（CIの写真では空に建てる）</li>
 *   <li>{@code city tp [場所] [人]}：見どころへ移る（場所を省くと一覧。人を省くと自分）。最後に建てた所が基準</li>
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

    private static final int MIN_X = -144;
    private static final int MAX_X = 135;
    private static final int MIN_Z = -184;
    private static final int MAX_Z = 127;
    private static final int GROUND_DEPTH = 13;
    private static final int CLEAR_HEIGHT = 64;
    private static final double PLAZA_R = 22.5;
    private static final double WALLROAD_IN = 107.0;
    private static final double WALL_IN = 112.0;
    private static final double WALL_OUT = 116.0;

    /** 構造物（{@code map/conelia/} の後ろ）と、その (0, 0, 0) を置く所。grown は育ったあとに重ねる形。並びは置く順番。 */
    private record Placement(String name, int x, int y, int z, boolean grown) {
    }

    private static final List<Placement> PLACEMENTS = List.of(
            new Placement("guild_hall", 6, 0, -100, false),
            new Placement("personal_storage", 22, -7, -82, false),        // ギルドの後に置く
            new Placement("weapon_workshop", 79, 0, -26, false),
            new Placement("armor_workshop", 60, 0, 4, false),
            new Placement("accessory_workshop", 22, 0, -26, false),
            new Placement("market_north", -18, 0, 22, false),
            new Placement("market_south", -18, 0, 60, false),
            new Placement("free_market", -48, 0, 60, false),
            new Placement("church", -105, 0, -22, false),
            new Placement("church_garden", -96, 0, 16, false),
            new Placement("fishing_pond", -95, -9, 23, false),            // 記憶の庭の後に置く
            new Placement("water_gate", -131, -9, 48, false),
            new Placement("fishing_alley", -66, -1, 66, false),
            new Placement("casino", 5, -3, 68, false),                     // 商店街の南の後に置く
            new Placement("tower_entrance", -22, 1, -177, false),
            new Placement("tower_plaza", -23, -1, -147, false),
            new Placement("weapon_forge_lit", 92, 1, -23, true),
            new Placement("market_restaurant_open", -18, 0, 22, true),
            new Placement("free_market_expanded", -48, 0, 60, true),
            new Placement("church_counsel", -105, 0, -22, true),
            new Placement("fishing_lakeside", -131, -9, 48, true),
            new Placement("fishing_pond_frozen", -95, -9, 23, true),
            new Placement("casino_highlow", 5, -3, 68, true),
            new Placement("casino_fishrace", 5, -3, 68, true),
            new Placement("tower_plaza_monument", -23, -1, -147, true));

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
            // 上から：描く距離12チャンクでも霧に隠れないよう、北と南の半分ずつ（高さ110）
            new View("top_n", -13.5, 110, -100.5, 180, 90),
            new View("top_s", -13.5, 110, 26.5, 180, 90),
            new View("aerial_se", 110.5, 70, 110.5, 135, 25),
            new View("aerial_nw", -125.5, 70, -150.5, -45, 25),
            // 育ったあとに重ねる形の見どころ
            new View("restaurant", 2.5, 1, 28.5, 90, -5),
            new View("stalls", -33.5, 30, 76.5, 180, 90),
            new View("frozen_pond", -67.5, 2, 40.5, 90, 30),
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

    /** 建てた範囲の区画を、光の計算が終わるまで読み込んでおく（30秒） */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("amemaru_city", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);

    private CityCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> cityCommand() {
        return Commands.literal("city")
                .then(Commands.literal("build")
                        .executes(ctx -> build(ctx.getSource(), DEFAULT_ORIGIN, false))
                        .then(Commands.literal("grown")
                                .executes(ctx -> build(ctx.getSource(), DEFAULT_ORIGIN, true)))
                        .then(Commands.literal("at")
                                .then(Commands.argument("origin", BlockPosArgument.blockPos())
                                        .executes(ctx -> build(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "origin"), false))
                                        .then(Commands.literal("grown")
                                                .executes(ctx -> build(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "origin"), true))))))
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

    private static int build(CommandSourceStack source, BlockPos origin, boolean grown) {
        ServerLevel level = source.getLevel();
        if (origin.getY() - GROUND_DEPTH < level.getMinBuildHeight() || origin.getY() + CLEAR_HEIGHT >= level.getMaxBuildHeight()) {
            source.sendFailure(Component.translatable("commands.amemaru.city.height",
                    level.getMinBuildHeight() + GROUND_DEPTH, level.getMaxBuildHeight() - CLEAR_HEIGHT - 1));
            return 0;
        }
        StructureTemplateManager manager = level.getStructureManager();
        List<StructureTemplate> templates = new ArrayList<>();
        for (Placement placement : PLACEMENTS) {
            Optional<StructureTemplate> template = manager.get(AmemaruMod.id("map/conelia/" + placement.name()));
            if (template.isEmpty()) {
                source.sendFailure(Component.translatable("commands.amemaru.city.missing", placement.name()));
                return 0;
            }
            templates.add(template.get());
        }
        long started = System.currentTimeMillis();
        int x0 = origin.getX() + MIN_X;
        int x1 = origin.getX() + MAX_X;
        int z0 = origin.getZ() + MIN_Z;
        int z1 = origin.getZ() + MAX_Z;
        // 範囲の区画を読み込む（まだなければ作る）。光の計算が終わるまで、しばらく読み込んだままにする
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        ChunkPos middle = new ChunkPos(new BlockPos((x0 + x1) / 2, origin.getY(), (z0 + z1) / 2));
        level.getChunkSource().addRegionTicket(TICKET, middle, Math.max((x1 - x0) / 32, (z1 - z0) / 32) + 2, middle);
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
            if (placement.grown() && !grown) {
                continue;
            }
            BlockPos at = origin.offset(placement.x(), placement.y(), placement.z());
            templates.get(i).placeInWorld(level, at, at, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        }
        CityData data = CityData.get(level);
        data.origin = origin.immutable();
        data.grown = grown;
        data.setDirty();
        builds++;
        long millis = System.currentTimeMillis() - started;
        AdminCommands.log(source, "city build " + origin.toShortString() + (grown ? " grown" : "") + " (" + changed + " ground blocks, "
                + millis + " ms)");
        Component form = Component.translatable(grown ? "commands.amemaru.city.form.grown" : "commands.amemaru.city.form.start");
        source.sendSuccess(() -> Component.translatable("commands.amemaru.city.built", form,
                origin.getX(), origin.getY(), origin.getZ()), true);
        return 1;
    }

    /** 道の高さのブロック：ゾーニングの図の道と広場は土の道、まだ作っていない城壁は砂利、ほかは草 */
    private static BlockState surface(int x, int z) {
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

    private static Component placeName(String id) {
        return Component.translatable("commands.amemaru.city.place." + id);
    }

    // ---------------------------------------------------------------- 最後に建てた所（ワールドの data の amemaru_city_check.dat）

    private static final class CityData extends SavedData {
        private static final String NAME = "amemaru_city_check";
        private BlockPos origin = DEFAULT_ORIGIN;
        private boolean grown;

        static CityData load(CompoundTag tag) {
            CityData data = new CityData();
            if (tag.contains("x")) {
                data.origin = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
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
            return tag;
        }

        static CityData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(CityData::load, CityData::new, NAME);
        }
    }
}
