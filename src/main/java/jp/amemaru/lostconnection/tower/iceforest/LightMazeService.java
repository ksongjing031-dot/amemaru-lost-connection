package jp.amemaru.lostconnection.tower.iceforest;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 7階「氷の森林」の光の迷路（11月25日。Phase 0-A 9章の7階「氷の鏡を動かして光を導く迷路」）【補完】。
 *
 * <ul>
 *   <li>光の泉から、決まった向きへ光が伸びる。氷の鏡（「／」か「＼」）に当たると折れ、不透明なブロックに当たると止まる</li>
 *   <li>氷の鏡を右クリックすると、向きが入れ替わる。光が受け石に届くと解け、受け石が灯り、氷の門（{@code door}）がほどける</li>
 *   <li>鏡の向きと解けた迷路は、みんなで同じ。解けたあとは、鏡は凍りついて動かない。進行日が変わると、朝の向きに戻り、門は閉じる</li>
 *   <li>光の道は、近くの人（泉から {@link #SHOW_RANGE} ブロック）に光の粒で見える</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class LightMazeService {
    public static final double SHOW_RANGE = 48.0;
    /** 光が進む長さの上限（ブロック）。 */
    public static final int MAX_STEPS = 160;

    private static final Map<String, Trace> CACHE = new HashMap<>();

    /** 光の道（通ったマス）と、受け石に届いたか。 */
    public record Trace(List<BlockPos> cells, boolean reached) {
    }

    private LightMazeService() {
    }

    // ================================================================ 保存（みんなの分。進行日が変わるまで）

    public static final class Data extends SavedData {
        private static final String NAME = "amemaru_light_mazes";
        private final Set<String> solved = new LinkedHashSet<>();
        private final Map<String, Boolean> mirrors = new LinkedHashMap<>();

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            for (Tag t : tag.getList("solved", Tag.TAG_STRING)) {
                data.solved.add(t.getAsString());
            }
            CompoundTag m = tag.getCompound("mirrors");
            for (String key : m.getAllKeys()) {
                data.mirrors.put(key, m.getBoolean(key));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            solved.forEach(k -> list.add(StringTag.valueOf(k)));
            tag.put("solved", list);
            CompoundTag m = new CompoundTag();
            mirrors.forEach(m::putBoolean);
            tag.put("mirrors", m);
            return tag;
        }

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        public boolean solved(int floor, String maze) {
            return solved.contains(floor + ":" + maze);
        }

        boolean solve(int floor, String maze) {
            boolean added = solved.add(floor + ":" + maze);
            if (added) {
                setDirty();
            }
            return added;
        }

        public boolean slash(int floor, FloorFeatures.LightMaze maze, int index) {
            return mirrors.getOrDefault(floor + ":" + maze.id() + ":" + index, maze.mirrors().get(index).slash());
        }

        /** 鏡の向きを決める（右クリックで回したとき。自動テストが解き方を探すときも）。 */
        public void setSlash(int floor, FloorFeatures.LightMaze maze, int index, boolean slash) {
            mirrors.put(floor + ":" + maze.id() + ":" + index, slash);
            setDirty();
        }

        public void clear() {
            if (!solved.isEmpty() || !mirrors.isEmpty()) {
                solved.clear();
                mirrors.clear();
                setDirty();
            }
        }
    }

    public static boolean solved(MinecraftServer server, int floor, String maze) {
        return Data.get(server).solved(floor, maze);
    }

    /** 解けた光の迷路の数と、ぜんぶの数。 */
    public static int[] count(MinecraftServer server, FloorDef def) {
        Data data = Data.get(server);
        int done = 0;
        for (FloorFeatures.LightMaze maze : def.features().lightMazes()) {
            if (data.solved(def.number(), maze.id())) {
                done++;
            }
        }
        return new int[]{done, def.features().lightMazes().size()};
    }

    public static boolean allSolved(MinecraftServer server, FloorDef def) {
        int[] c = count(server, def);
        return c[1] > 0 && c[0] == c[1];
    }

    /** 解けた迷路の印（迷路 i が解けたら i 番目のビット）。 */
    public static int mazeBits(MinecraftServer server, FloorDef def) {
        Data data = Data.get(server);
        int bits = 0;
        List<FloorFeatures.LightMaze> mazes = def.features().lightMazes();
        for (int i = 0; i < mazes.size(); i++) {
            if (data.solved(def.number(), mazes.get(i).id())) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    // ================================================================ 光の道

    /** 鏡に当たった光の、次の向き。「／」：東→北・北→東・西→南・南→西。「＼」：東→南・南→東・西→北・北→西。 */
    public static Direction reflect(Direction dir, boolean slash) {
        return switch (dir) {
            case EAST -> slash ? Direction.NORTH : Direction.SOUTH;
            case NORTH -> slash ? Direction.EAST : Direction.WEST;
            case WEST -> slash ? Direction.SOUTH : Direction.NORTH;
            case SOUTH -> slash ? Direction.WEST : Direction.EAST;
            default -> dir;
        };
    }

    /** 光をたどる（今の鏡の向きで）。 */
    public static Trace trace(ServerLevel level, FloorDef def, FloorFeatures.LightMaze maze) {
        Data data = Data.get(level.getServer());
        Map<BlockPos, Integer> mirrorAt = new HashMap<>();
        for (int i = 0; i < maze.mirrors().size(); i++) {
            mirrorAt.put(def.world(maze.mirrors().get(i).pos()), i);
        }
        BlockPos target = def.world(maze.target());
        BlockPos pos = def.world(maze.source());
        Direction dir = maze.direction();
        List<BlockPos> cells = new ArrayList<>();
        for (int step = 0; step < MAX_STEPS; step++) {
            pos = pos.relative(dir);
            if (pos.equals(target)) {
                cells.add(pos);
                return new Trace(cells, true);
            }
            Integer mirror = mirrorAt.get(pos);
            if (mirror != null) {
                cells.add(pos);
                dir = reflect(dir, data.slash(def.number(), maze, mirror));
                continue;
            }
            if (!level.isLoaded(pos)) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.canOcclude()) {
                break;
            }
            cells.add(pos);
        }
        return new Trace(cells, false);
    }

    private static Trace cached(ServerLevel level, FloorDef def, FloorFeatures.LightMaze maze) {
        return CACHE.computeIfAbsent(def.number() + ":" + maze.id(), k -> trace(level, def, maze));
    }

    private static void invalidate(int floor, String maze) {
        CACHE.remove(floor + ":" + maze);
    }

    // ================================================================ 鏡を回す・解ける

    /** 氷の鏡を右クリックした：向きを入れ替える。光が受け石に届けば解ける。 */
    public static void turn(ServerPlayer player, int floor, String mazeId, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.LightMaze maze = def == null ? null : def.features().lightMaze(mazeId);
        if (maze == null) {
            return;
        }
        MinecraftServer server = player.server;
        Data data = Data.get(server);
        if (data.solved(floor, mazeId)) {
            player.displayClientMessage(Component.translatable("message.amemaru.light.locked").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int index = -1;
        for (int i = 0; i < maze.mirrors().size(); i++) {
            if (def.world(maze.mirrors().get(i).pos()).equals(pos)) {
                index = i;
            }
        }
        if (index < 0) {
            return;
        }
        boolean slash = !data.slash(floor, maze, index);
        data.setSlash(floor, maze, index, slash);
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof IceForestBlocks.Mirror) {
            level.setBlock(pos, state.setValue(IceForestBlocks.SLASH, slash), 3);
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0F, slash ? 1.2F : 0.9F);
        invalidate(floor, mazeId);
        Trace trace = cached(level, def, maze);
        show(level, trace, true);
        if (trace.reached()) {
            solve(server, def, maze, player);
        }
    }

    /** 光の泉・受け石を右クリックした：光の道を、すぐに見せる。 */
    public static void showPath(ServerPlayer player, int floor, String mazeId) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.LightMaze maze = def == null ? null : def.features().lightMaze(mazeId);
        if (maze == null) {
            return;
        }
        Trace trace = cached(player.serverLevel(), def, maze);
        show(player.serverLevel(), trace, true);
        player.displayClientMessage(Component.translatable(trace.reached() ? "message.amemaru.light.reached" : "message.amemaru.light.path",
                maze.name(), trace.cells().size()).withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * 鏡を「解き方の向き」に回してから解く（管理者・自動の確認の写真から）。解き方は、鏡の向きの組み合わせを順に試して探す
     * （鏡は多くても 32。ふつうは 10 まで）。見つからないとき、塔が読み込まれていないときは、鏡はそのままで解く。見つかったら true。
     */
    public static boolean solveByMirrors(MinecraftServer server, FloorDef def, FloorFeatures.LightMaze maze, @Nullable ServerPlayer by) {
        ServerLevel tower = FloorDefs.towerLevel(server);
        Data data = Data.get(server);
        int count = maze.mirrors().size();
        int answer = -1;
        if (tower != null && count <= 16 && tower.isLoaded(def.world(maze.source()))) {
            boolean[] start = new boolean[count];
            for (int i = 0; i < count; i++) {
                start[i] = data.slash(def.number(), maze, i);
            }
            for (int bits = 0; bits < (1 << count) && answer < 0; bits++) {
                for (int i = 0; i < count; i++) {
                    data.setSlash(def.number(), maze, i, (bits >> i & 1) == 1);
                }
                if (trace(tower, def, maze).reached()) {
                    answer = bits;
                }
            }
            if (answer < 0) {
                for (int i = 0; i < count; i++) {
                    data.setSlash(def.number(), maze, i, start[i]);
                }
            }
            invalidate(def.number(), maze.id());
        }
        solve(server, def, maze, by);
        if (tower != null) {
            reconcile(tower, def);
        }
        return answer >= 0;
    }

    /** 迷路が解けた（管理者・自動テストからも）。 */
    public static void solve(MinecraftServer server, FloorDef def, FloorFeatures.LightMaze maze, @Nullable ServerPlayer by) {
        if (!Data.get(server).solve(def.number(), maze.id())) {
            return;
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        int[] c = count(server, def);
        if (tower != null) {
            BlockPos target = def.world(maze.target());
            setLit(tower, target, true);
            if (maze.door() != null) {
                door(tower, def, maze.door(), true);
            }
            tower.playSound(null, target, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.6F, 1.4F);
            tower.playSound(null, target, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 1.2F);
            tower.sendParticles(ParticleTypes.END_ROD, target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5, 40, 0.5, 1.0, 0.5, 0.06);
            for (ServerPlayer p : playersOn(tower, def)) {
                p.sendSystemMessage(Component.translatable("message.amemaru.light.solved", maze.name(), c[0], c[1]).withStyle(ChatFormatting.AQUA));
                if (c[0] == c[1]) {
                    ModNetwork.sendTo(p, new AnnouncementS2C(AnnouncementS2C.Kind.STORY,
                            Component.translatable("message.amemaru.light.all", def.name()).getString()));
                }
            }
        }
        AmemaruMod.LOGGER.info("[amemaru] Light maze {} on floor {} solved by {} ({}/{})", maze.id(), def.number(),
                by == null ? "admin" : by.getGameProfile().getName(), c[0], c[1]);
        resyncStory(server);
    }

    /** 見えているもの（鏡の向き・受け石・門）を、保存の状態に合わせる（読み込まれている所だけ）。 */
    public static void reconcile(ServerLevel level, FloorDef def) {
        Data data = Data.get(level.getServer());
        for (FloorFeatures.LightMaze maze : def.features().lightMazes()) {
            boolean solved = data.solved(def.number(), maze.id());
            for (int i = 0; i < maze.mirrors().size(); i++) {
                BlockPos at = def.world(maze.mirrors().get(i).pos());
                if (level.isLoaded(at)) {
                    BlockState state = level.getBlockState(at);
                    boolean want = data.slash(def.number(), maze, i);
                    if (state.getBlock() instanceof IceForestBlocks.Mirror && state.getValue(IceForestBlocks.SLASH) != want) {
                        level.setBlock(at, state.setValue(IceForestBlocks.SLASH, want), 3);
                    }
                }
            }
            setLit(level, def.world(maze.target()), solved);
            if (maze.door() != null) {
                door(level, def, maze.door(), solved);
            }
        }
    }

    /** 進行日が変わった：鏡は朝の向きに戻り、門は閉じる。 */
    public static void onDayChanged(MinecraftServer server) {
        Data.get(server).clear();
        CACHE.clear();
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower != null) {
            for (FloorDef def : FloorDefs.all()) {
                if (!def.features().lightMazes().isEmpty()) {
                    reconcile(tower, def);
                }
            }
        }
        resyncStory(server);
    }

    // ================================================================ 毎 tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 10 != 7) {
            return;
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower == null) {
            return;
        }
        if (tick % 200 == 7) {
            CACHE.clear();
        }
        for (FloorDef def : FloorDefs.all()) {
            if (def.features().lightMazes().isEmpty()) {
                continue;
            }
            List<ServerPlayer> players = playersOn(tower, def);
            if (players.isEmpty()) {
                continue;
            }
            if (tick % 40 == 7) {
                reconcile(tower, def);
            }
            for (FloorFeatures.LightMaze maze : def.features().lightMazes()) {
                Vec3 source = Vec3.atCenterOf(def.world(maze.source()));
                boolean near = false;
                for (ServerPlayer p : players) {
                    if (p.position().distanceToSqr(source) < SHOW_RANGE * SHOW_RANGE) {
                        near = true;
                        break;
                    }
                }
                if (near) {
                    show(tower, cached(tower, def, maze), false);
                }
            }
        }
    }

    private static void show(ServerLevel level, Trace trace, boolean bright) {
        List<BlockPos> cells = trace.cells();
        for (int i = 0; i < cells.size(); i++) {
            if (!bright && i % 2 == 1) {
                continue;
            }
            BlockPos at = cells.get(i);
            level.sendParticles(trace.reached() ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 0.55,
                    at.getZ() + 0.5, 1, 0.08, 0.04, 0.08, 0.0);
        }
        if (!cells.isEmpty() && !trace.reached()) {
            BlockPos end = cells.get(cells.size() - 1);
            level.sendParticles(ParticleTypes.WAX_OFF, end.getX() + 0.5, end.getY() + 0.6, end.getZ() + 0.5, bright ? 6 : 2, 0.2, 0.2, 0.2, 0.0);
        }
    }

    // ================================================================ 共通

    private static void setLit(ServerLevel level, BlockPos pos, boolean lit) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof IceForestBlocks.Target && state.getValue(IceForestBlocks.LIT) != lit) {
            level.setBlock(pos, state.setValue(IceForestBlocks.LIT, lit), 3);
        }
    }

    /** 門を開ける（空気に）・閉じる（門のブロックに）。読み込まれている所だけ。 */
    private static void door(ServerLevel level, FloorDef def, FloorFeatures.Door door, boolean open) {
        net.minecraft.world.level.block.Block block = ForgeRegistries.BLOCKS.getValue(door.block());
        BlockState closed = block == null ? Blocks.BLUE_ICE.defaultBlockState() : block.defaultBlockState();
        BlockPos min = def.world(door.min());
        BlockPos max = def.world(door.max());
        boolean changed = false;
        for (BlockPos at : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(at)) {
                continue;
            }
            BlockState now = level.getBlockState(at);
            BlockState want = open ? Blocks.AIR.defaultBlockState() : closed;
            if (now.getBlock() != want.getBlock()) {
                level.setBlock(at, want, 3);
                changed = true;
            }
        }
        if (changed && level.isLoaded(min)) {
            level.playSound(null, min, open ? SoundEvents.GLASS_BREAK : SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.6F, 0.6F);
            level.sendParticles(ParticleTypes.SNOWFLAKE, (min.getX() + max.getX()) / 2.0 + 0.5, min.getY() + 1.0,
                    (min.getZ() + max.getZ()) / 2.0 + 0.5, 40, 0.8, 1.0, 0.8, 0.03);
        }
    }

    private static List<ServerPlayer> playersOn(ServerLevel level, FloorDef def) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            FloorDef here = FloorDefs.floorAt(level, p.position());
            if (here != null && here.number() == def.number()) {
                list.add(p);
            }
        }
        return list;
    }

    private static void resyncStory(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data != null) {
                data.markDirty(PlayerData.SYNC_STORY);
            }
        }
    }
}
