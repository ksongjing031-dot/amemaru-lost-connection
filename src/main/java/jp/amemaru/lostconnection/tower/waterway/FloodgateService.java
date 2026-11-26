package jp.amemaru.lostconnection.tower.waterway;

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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 水門（Phase 8 その2：3階「古代の水路」のギミック。Phase 0-A 9章「離れた部屋の水門を、複数人が声をかけ合って同時に操作し、
 * 水の流れを変える」）。
 *
 * <ul>
 *   <li>水門の組ごとに、離れた2つの部屋にハンドルが1つずつ。片方を回すと {@link #WINDOW_TICKS} の間だけ回ったまま。その間に
 *       もう片方も回すと、水門が開き、決めた水の区画の水が引く（みんなの分。進行日が変わるまで）【補完】</li>
 *   <li>片方だけでは、ハンドルは重く戻る（ひとりでは開けられない）</li>
 *   <li>ボスの部屋の排水のハンドル（{@link FloorFeatures#DRAIN}）は、開いたままにならない。2つを同時に回すと、ボスに知らせる</li>
 *   <li>水を引く・戻すのは、少しずつ（{@link #CHANGES_PER_TICK} ずつ）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class FloodgateService {
    /** 2つのハンドルを「同時に」とみなす間（3秒）。 */
    public static final int WINDOW_TICKS = 60;

    /**
     * 片方を回してから、もう片方を回すまでに待つ tick。11月26日：ひとり確認モード（{@link jp.amemaru.lostconnection.tower.SoloTest}）
     * では、ひとりで離れた部屋へ走れるように延ばす（水門 {@code FLOODGATE_SECONDS}・ボスの部屋の排水 {@code DRAIN_SECONDS}）。
     */
    public static int windowTicks(String gateId) {
        return windowTicks(gateId, false);
    }

    /**
     * 11月27日：ひとりで挑んでいるとき（水門はその階にほかの人がいない、排水はボス戦の参加者が1人）も、モードと同じように延ばす
     * （「推奨レベルよりかなり高いときは、ソロでも攻略できるように」）。
     */
    public static int windowTicks(net.minecraft.server.MinecraftServer server, int floor, String gateId) {
        boolean alone;
        if (FloorFeatures.DRAIN.equals(gateId)) {
            jp.amemaru.lostconnection.tower.FloorRuntime runtime = jp.amemaru.lostconnection.tower.TowerService.runtime(floor);
            alone = runtime == null || runtime.bossRoom() == null
                    || jp.amemaru.lostconnection.tower.SoloTest.aloneInFight(runtime.bossRoom().participants());
        } else {
            alone = jp.amemaru.lostconnection.tower.SoloTest.aloneOnFloor(server, floor);
        }
        return windowTicks(gateId, alone);
    }

    private static int windowTicks(String gateId, boolean alone) {
        int solo = FloorFeatures.DRAIN.equals(gateId) ? jp.amemaru.lostconnection.tower.SoloTest.DRAIN_SECONDS
                : jp.amemaru.lostconnection.tower.SoloTest.FLOODGATE_SECONDS;
        return jp.amemaru.lostconnection.tower.SoloTest.seconds(WINDOW_TICKS / 20, solo, alone) * 20;
    }
    /** 1 tick に変えるブロックの数。 */
    private static final int CHANGES_PER_TICK = 1500;

    /** ハンドルを回した時刻（階:組:どちら → ゲームの時刻）。 */
    private static final Map<String, Long> TURNED = new HashMap<>();
    /** 水を引く・戻すブロックの、残りの作業。 */
    private static final Deque<Change> CHANGES = new ArrayDeque<>();

    /** 1つのブロックの作業。{@code door} があれば扉を戻す（null で water が false なら、扉を上げる）。 */
    private record Change(ServerLevel level, int floor, BlockPos pos, boolean water, @Nullable BlockState door, boolean isDoor) {
    }

    private FloodgateService() {
    }

    // ================================================================ 保存（開いた水門。みんなの分）

    public static final class Data extends SavedData {
        private static final String NAME = "amemaru_floodgates";
        private static final String PENDING = "pending";
        private final Map<Integer, Set<String>> opened = new HashMap<>();
        /** 水を引く・戻すのがやりかけで止まった階（次にその階に人が来たとき、保存の状態に合わせ直す）。 */
        private final Set<Integer> pending = new HashSet<>();

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            for (int floor : tag.getIntArray(PENDING)) {
                data.pending.add(floor);
            }
            for (String key : tag.getAllKeys()) {
                if (PENDING.equals(key)) {
                    continue;
                }
                try {
                    int floor = Integer.parseInt(key);
                    Set<String> set = new LinkedHashSet<>();
                    for (Tag t : tag.getList(key, Tag.TAG_STRING)) {
                        set.add(t.getAsString());
                    }
                    data.opened.put(floor, set);
                } catch (NumberFormatException ignored) {
                    // 古い形式は読まない
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            for (Map.Entry<Integer, Set<String>> entry : opened.entrySet()) {
                ListTag list = new ListTag();
                for (String id : entry.getValue()) {
                    list.add(StringTag.valueOf(id));
                }
                tag.put(Integer.toString(entry.getKey()), list);
            }
            tag.putIntArray(PENDING, pending.stream().mapToInt(Integer::intValue).toArray());
            return tag;
        }

        public void markPending(int floor) {
            if (pending.add(floor)) {
                setDirty();
            }
        }

        /** やりかけの印を消す。印があったなら true。 */
        public boolean takePending(int floor) {
            boolean had = pending.remove(floor);
            if (had) {
                setDirty();
            }
            return had;
        }

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        public boolean isOpen(int floor, String id) {
            return opened.getOrDefault(floor, Set.of()).contains(id);
        }

        public boolean open(int floor, String id) {
            boolean added = opened.computeIfAbsent(floor, f -> new LinkedHashSet<>()).add(id);
            if (added) {
                setDirty();
            }
            return added;
        }

        public Set<String> opened(int floor) {
            return Set.copyOf(opened.getOrDefault(floor, Set.of()));
        }

        public void clear() {
            opened.clear();
            setDirty();
        }
    }

    // ================================================================ 調べる

    public static boolean isOpen(MinecraftServer server, int floor, String id) {
        return Data.get(server).isOpen(floor, id);
    }

    /** その階の水門（排水のハンドルをのぞく）が、すべて開いたか（封印の条件 floodgates）。 */
    public static boolean allOpen(MinecraftServer server, FloorDef def) {
        for (FloorFeatures.Floodgate gate : def.features().floodgates()) {
            if (!FloorFeatures.DRAIN.equals(gate.id()) && !isOpen(server, def.number(), gate.id())) {
                return false;
            }
        }
        return true;
    }

    /** 開いた水門の数と、ぜんぶの数（排水のハンドルをのぞく）。 */
    public static int[] count(MinecraftServer server, FloorDef def) {
        int open = 0;
        int total = 0;
        for (FloorFeatures.Floodgate gate : def.features().floodgates()) {
            if (FloorFeatures.DRAIN.equals(gate.id())) {
                continue;
            }
            total++;
            if (isOpen(server, def.number(), gate.id())) {
                open++;
            }
        }
        return new int[]{open, total};
    }

    // ================================================================ ハンドルを回す

    /**
     * ハンドルを回す（{@code side}：組の中の 0 か 1）。
     *
     * @return 水門が開いた（または排水が始まった）とき true
     */
    public static boolean turn(ServerPlayer player, int floor, String gateId, int side, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.Floodgate gate = def == null ? null : def.features().floodgate(gateId);
        ServerLevel level = player.serverLevel();
        if (gate == null || side < 0 || side > 1) {
            return false;
        }
        String name = gate.name().isEmpty() ? gate.id() : gate.name();
        boolean drain = FloorFeatures.DRAIN.equals(gate.id());
        if (!drain && isOpen(player.server, floor, gate.id())) {
            player.displayClientMessage(Component.translatable("message.amemaru.floodgate.already", name).withStyle(ChatFormatting.AQUA), true);
            return false;
        }
        FloorFeatures.Floodgate before = gate.after().isEmpty() ? null : def.features().floodgate(gate.after());
        if (before != null && !isOpen(player.server, floor, before.id())) {
            // 水の底のハンドル：先の水門で水を引くまでは、水の重さで回らない【補完】
            player.displayClientMessage(Component.translatable("message.amemaru.floodgate.underwater",
                    before.name().isEmpty() ? before.id() : before.name()).withStyle(ChatFormatting.GRAY), true);
            level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 1.0F, 0.5F);
            return false;
        }
        long now = level.getGameTime();
        TURNED.put(key(floor, gate.id(), side), now);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.2F, 0.5F);
        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.4, 0.3, 0.4, 0.1);
        Long other = TURNED.get(key(floor, gate.id(), 1 - side));
        int window = windowTicks(player.server, floor, gate.id());
        if (other != null && now - other <= window) {
            TURNED.remove(key(floor, gate.id(), 0));
            TURNED.remove(key(floor, gate.id(), 1));
            if (drain) {
                boolean ok = Drains.both(level, def);
                if (!ok) {
                    // ボスがいない・戦っていない・水が戻ったばかり
                    player.displayClientMessage(Component.translatable("message.amemaru.floodgate.drain_busy").withStyle(ChatFormatting.GRAY),
                            true);
                    level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 1.0F, 0.5F);
                }
                return ok;
            }
            open(player.server, def, gate, player);
            return true;
        }
        int seconds = window / 20;
        player.displayClientMessage(Component.translatable("message.amemaru.floodgate.waiting", name, seconds)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
        // その階のほかの人にも知らせる（もう片方へ声をかけるきっかけ）
        for (ServerPlayer p : level.players()) {
            FloorDef here = FloorDefs.floorAt(level, p.position());
            if (p != player && here != null && here.number() == floor) {
                p.displayClientMessage(Component.translatable("message.amemaru.floodgate.other_waiting", player.getDisplayName(), name, seconds)
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
        return false;
    }

    private static String key(int floor, String gate, int side) {
        return floor + ":" + gate + ":" + side;
    }

    /** 片方だけ回したまま、時間が過ぎた：ハンドルが戻る（知らせる）。 */
    private static void tickSpringBack(MinecraftServer server) {
        if (TURNED.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        TURNED.entrySet().removeIf(entry -> {
            String[] parts = entry.getKey().split(":");
            if (now - entry.getValue() <= windowTicks(server, Integer.parseInt(parts[0]), parts[1])) {
                return false;
            }
            FloorDef def = FloorDefs.byNumber(Integer.parseInt(parts[0]));
            FloorFeatures.Floodgate gate = def == null ? null : def.features().floodgate(parts[1]);
            ServerLevel tower = FloorDefs.towerLevel(server);
            if (gate != null && tower != null) {
                BlockPos valve = def.world(gate.valves().get(Integer.parseInt(parts[2])));
                tower.playSound(null, valve, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.5F);
                for (ServerPlayer p : tower.players()) {
                    if (p.blockPosition().distSqr(valve) < 16 * 16) {
                        p.displayClientMessage(Component.translatable("message.amemaru.floodgate.sprang").withStyle(ChatFormatting.GRAY), true);
                    }
                }
            }
            return true;
        });
    }

    // ================================================================ 開く・戻す

    /** 水門を開く（水を引く・ハンドルを開いた形に）。管理者・自動テストからも。 */
    public static void open(MinecraftServer server, FloorDef def, FloorFeatures.Floodgate gate, @Nullable ServerPlayer by) {
        if (FloorFeatures.DRAIN.equals(gate.id()) || !Data.get(server).open(def.number(), gate.id())) {
            return;
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower != null) {
            for (String drain : gate.drains()) {
                FloorFeatures.WaterSection section = def.features().waterSection(drain);
                if (section != null) {
                    queue(tower, def, section, false);
                }
            }
            // 水を引いたあとで、扉を上げる
            doors(tower, def, gate, true);
            for (BlockPos valve : gate.valves()) {
                WaterwayBlocks.setValveOpen(tower, def.world(valve), true);
                tower.playSound(null, def.world(valve), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.6F, 0.6F);
            }
        }
        int[] c = count(server, def);
        Component text = by == null ? Component.translatable("message.amemaru.floodgate.opened_admin", gate.name(), c[1] - c[0])
                : Component.translatable("message.amemaru.floodgate.opened", gate.name(), c[1] - c[0]);
        if (tower != null) {
            for (ServerPlayer p : tower.players()) {
                FloorDef here = FloorDefs.floorAt(tower, p.position());
                if (here != null && here.number() == def.number()) {
                    p.sendSystemMessage(text.copy().withStyle(ChatFormatting.AQUA));
                    if (c[0] == c[1]) {
                        ModNetwork.sendTo(p, new AnnouncementS2C(AnnouncementS2C.Kind.STORY,
                                Component.translatable("message.amemaru.floodgate.all_opened", def.name()).getString()));
                    }
                }
            }
        }
        resyncStory(server);
    }

    /** 進行日が変わった：水門を閉じ、水を戻す（先に扉をすべて戻してから、水を戻す）。 */
    public static void onDayChanged(MinecraftServer server) {
        Data data = Data.get(server);
        ServerLevel tower = FloorDefs.towerLevel(server);
        for (FloorDef def : FloorDefs.all()) {
            if (tower != null) {
                for (String id : data.opened(def.number())) {
                    FloorFeatures.Floodgate gate = def.features().floodgate(id);
                    if (gate != null) {
                        doors(tower, def, gate, false);
                    }
                }
                for (String id : data.opened(def.number())) {
                    FloorFeatures.Floodgate gate = def.features().floodgate(id);
                    if (gate == null) {
                        continue;
                    }
                    for (String drain : gate.drains()) {
                        FloorFeatures.WaterSection section = def.features().waterSection(drain);
                        if (section != null) {
                            queue(tower, def, section, true);
                        }
                    }
                    for (BlockPos valve : gate.valves()) {
                        WaterwayBlocks.setValveOpen(tower, def.world(valve), false);
                    }
                }
            }
        }
        data.clear();
        resyncStory(server);
    }

    /** 水の区画の水を、引く（{@code water} false）・戻す（true）。 */
    public static void queue(ServerLevel level, FloorDef def, FloorFeatures.WaterSection section, boolean water) {
        BlockPos min = def.world(section.min());
        BlockPos max = def.world(section.max());
        int low = def.origin().getY() + section.low();
        int high = def.origin().getY() + section.high();
        for (int y = water ? low + 1 : high; water ? y <= high : y > low; y += water ? 1 : -1) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    CHANGES.add(new Change(level, def.number(), new BlockPos(x, y, z), water, null, false));
                }
            }
        }
    }

    /** 水門の扉を、上げる（{@code open}）・戻す。 */
    private static void doors(ServerLevel level, FloorDef def, FloorFeatures.Floodgate gate, boolean open) {
        for (FloorFeatures.Door door : gate.doors()) {
            net.minecraft.world.level.block.Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(door.block());
            BlockState state = block == null ? Blocks.STONE.defaultBlockState() : block.defaultBlockState();
            BlockPos min = def.world(door.min());
            BlockPos max = def.world(door.max());
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        CHANGES.add(new Change(level, def.number(), new BlockPos(x, y, z), false, open ? null : state, true));
                    }
                }
            }
            if (level.isLoaded(min)) {
                level.playSound(null, min, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2.0F, 0.5F);
            }
        }
    }

    /** 残りの作業があるか（自動テスト）。 */
    public static boolean busy() {
        return !CHANGES.isEmpty();
    }

    /** ブロックを変える決まり：見た目だけ送り、まわりの形の計算はしない（となりの水に「流れ出せ」と知らせない）。 */
    private static final int FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS
            | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;

    private static void tickChanges() {
        int done = 0;
        while (!CHANGES.isEmpty() && done < CHANGES_PER_TICK) {
            Change change = CHANGES.poll();
            done++;
            ServerLevel level = change.level();
            // 読み込まれていない所は読み込んでから変える（あとで人が来たとき、水と水門の状態が合っているように）
            level.getChunkAt(change.pos());
            BlockState state = level.getBlockState(change.pos());
            if (change.isDoor()) {
                // 扉：上げるときは扉のブロックを消し、戻すときは空気か水を扉にする
                if (change.door() == null) {
                    if (!state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity()) {
                        level.setBlock(change.pos(), Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                } else if (state.isAir() || state.is(Blocks.WATER)) {
                    level.setBlock(change.pos(), change.door(), FLAGS);
                }
                continue;
            }
            if (change.water() && (state.isAir() || (state.is(Blocks.WATER) && !state.getFluidState().isSource()))) {
                // 空気と、流れてきた水を、水の源にする
                level.setBlock(change.pos(), Blocks.WATER.defaultBlockState(), FLAGS);
            } else if (!change.water() && state.is(Blocks.WATER)) {
                level.setBlock(change.pos(), Blocks.AIR.defaultBlockState(), FLAGS);
            } else if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED) != change.water()) {
                // 水に浸かった階段など：水を含ませる・抜く
                level.setBlock(change.pos(), state.setValue(BlockStateProperties.WATERLOGGED, change.water()), FLAGS);
            }
        }
    }

    /**
     * その階の水を、保存の状態に合わせる（水を引く・戻すのがやりかけでサーバーが止まった・階を建て直した、その次にその階に人が
     * 来たとき）。
     */
    public static void sync(MinecraftServer server, FloorDef def) {
        if (def.features().floodgates().isEmpty() || !Data.get(server).takePending(def.number())) {
            return;
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower == null) {
            return;
        }
        for (FloorFeatures.Floodgate gate : def.features().floodgates()) {
            if (!FloorFeatures.DRAIN.equals(gate.id()) && !isOpen(server, def.number(), gate.id())) {
                doors(tower, def, gate, false);
            }
        }
        for (FloorFeatures.Floodgate gate : def.features().floodgates()) {
            if (FloorFeatures.DRAIN.equals(gate.id())) {
                continue;
            }
            boolean open = isOpen(server, def.number(), gate.id());
            for (String drain : gate.drains()) {
                FloorFeatures.WaterSection section = def.features().waterSection(drain);
                if (section != null) {
                    queue(tower, def, section, !open);
                }
            }
            if (open) {
                doors(tower, def, gate, true);
            }
            for (BlockPos valve : gate.valves()) {
                WaterwayBlocks.setValveOpen(tower, def.world(valve), open);
            }
        }
    }

    /** 階を建て直した（建てた形は、水門がみな閉じた形）：次に人が来たとき、水を保存の状態に合わせ直す。 */
    public static void forgetSync(MinecraftServer server, int floor) {
        FloorDef def = FloorDefs.byNumber(floor);
        if (def != null && !def.features().floodgates().isEmpty()) {
            Data.get(server).markPending(floor);
        }
    }

    private static void resyncStory(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data != null) {
                data.markDirty(PlayerData.SYNC_STORY);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tickChanges();
        tickSpringBack(event.getServer());
    }

    /** 止まる前：やりかけの作業があれば、その階に印をつける。 */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        if (CHANGES.isEmpty()) {
            return;
        }
        Data data = Data.get(event.getServer());
        for (Change change : CHANGES) {
            data.markPending(change.floor());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TURNED.clear();
        CHANGES.clear();
    }
}
