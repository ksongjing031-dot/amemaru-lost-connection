package jp.amemaru.lostconnection.tower.lake;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.network.s2c.StoryTextS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 8階「沈黙の湖」の鐘（11月25日。Phase 0-A 9章の8階「湖のまわりの鐘を正しい順番で鳴らすと音が戻り、祠への道が開く」）【補完】。
 *
 * <ul>
 *   <li>歌の碑に触れると、今日の歌（その組の鐘を鳴らす順番。進行日ごとに変わる）が浮かぶ。パーティの仲間にも同じ歌が届く</li>
 *   <li>鐘を右クリックで鳴らす。順番どおりなら鐘が灯る。違う鐘を鳴らすと、灯った鐘はすべて消え、はじめからやり直し
 *       （今日の歌は変わらない）。最初の鐘から {@code seconds} 秒をすぎても、はじめから</li>
 *   <li>組の鐘がすべて灯ると解け、{@code opens} が開き、{@code raises} が浮かぶ（入江の鐘：沈んだ石橋。湖の鐘：祠の氷のふた）。
 *       {@code sound} の組が解けると、階に音が戻る（その日のうち。{@code environment.silent}）</li>
 *   <li>解けた組と灯った鐘はみんなで同じ。進行日が変わると、鐘はまた沈黙し、橋は沈み、ふたは閉じる（中に人がいる間は閉じない）</li>
 *   <li>湖心の鳴り石（組 {@code arena}）は、ボスの「静寂の帳」を破るための鐘（{@link jp.amemaru.lostconnection.enemy.boss.Mune}）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class BellService {
    /** 湖心の鳴り石の組の名前（ボスが使う。歌の碑はない）。 */
    public static final String ARENA = "arena";

    private BellService() {
    }

    // ================================================================ 保存（みんなの分。進行日が変わるまで）

    public static final class Data extends SavedData {
        private static final String NAME = "amemaru_lake_bells";
        private final Set<String> solved = new LinkedHashSet<>();
        private final Map<String, Integer> progress = new LinkedHashMap<>();
        private final Map<String, Long> started = new LinkedHashMap<>();

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            for (Tag t : tag.getList("solved", Tag.TAG_STRING)) {
                data.solved.add(t.getAsString());
            }
            CompoundTag p = tag.getCompound("progress");
            for (String key : p.getAllKeys()) {
                data.progress.put(key, p.getInt(key));
            }
            CompoundTag s = tag.getCompound("started");
            for (String key : s.getAllKeys()) {
                data.started.put(key, s.getLong(key));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            solved.forEach(k -> list.add(StringTag.valueOf(k)));
            tag.put("solved", list);
            CompoundTag p = new CompoundTag();
            progress.forEach(p::putInt);
            tag.put("progress", p);
            CompoundTag s = new CompoundTag();
            started.forEach(s::putLong);
            tag.put("started", s);
            return tag;
        }

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        public boolean solved(int floor, String group) {
            return solved.contains(floor + ":" + group);
        }

        public int progress(int floor, String group) {
            return progress.getOrDefault(floor + ":" + group, 0);
        }

        long started(int floor, String group) {
            return started.getOrDefault(floor + ":" + group, 0L);
        }

        void setProgress(int floor, String group, int value, long now) {
            String key = floor + ":" + group;
            if (value <= 0) {
                progress.remove(key);
                started.remove(key);
            } else {
                progress.put(key, value);
                if (value == 1) {
                    started.put(key, now);
                }
            }
            setDirty();
        }

        boolean solve(int floor, String group) {
            boolean added = solved.add(floor + ":" + group);
            progress.remove(floor + ":" + group);
            started.remove(floor + ":" + group);
            setDirty();
            return added;
        }

        public void clear() {
            if (!solved.isEmpty() || !progress.isEmpty() || !started.isEmpty()) {
                solved.clear();
                progress.clear();
                started.clear();
                setDirty();
            }
        }
    }

    /** 組の鐘を鳴らしきるまでの秒。11月26日：ひとり確認モード（{@link jp.amemaru.lostconnection.tower.SoloTest}）では延ばす。 */
    public static int limitSeconds(FloorFeatures.BellGroup group) {
        return jp.amemaru.lostconnection.tower.SoloTest.seconds(group.seconds(),
                group.seconds() * jp.amemaru.lostconnection.tower.SoloTest.BELL_FACTOR);
    }

    public static boolean solved(MinecraftServer server, int floor, String group) {
        return Data.get(server).solved(floor, group);
    }

    /** 解けた鐘の組の数と、ぜんぶの数。 */
    public static int[] count(MinecraftServer server, FloorDef def) {
        Data data = Data.get(server);
        int done = 0;
        for (FloorFeatures.BellGroup group : def.features().bells()) {
            if (data.solved(def.number(), group.id())) {
                done++;
            }
        }
        return new int[]{done, def.features().bells().size()};
    }

    public static boolean allSolved(MinecraftServer server, FloorDef def) {
        int[] c = count(server, def);
        return c[1] > 0 && c[0] == c[1];
    }

    /** 解けた組の印（組 i が解けたら i 番目のビット）。 */
    public static int groupBits(MinecraftServer server, FloorDef def) {
        Data data = Data.get(server);
        int bits = 0;
        List<FloorFeatures.BellGroup> groups = def.features().bells();
        for (int i = 0; i < groups.size(); i++) {
            if (data.solved(def.number(), groups.get(i).id())) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    /** その階に音が戻っているか（音を戻す組がないか、その組が解けている）。 */
    public static boolean soundRestored(MinecraftServer server, FloorDef def) {
        for (FloorFeatures.BellGroup group : def.features().bells()) {
            if (group.sound() && !solved(server, def.number(), group.id())) {
                return false;
            }
        }
        return true;
    }

    // ================================================================ 今日の歌

    /** 今日の歌：鐘を鳴らす順番（鐘の番号の並び）。進行日・階・組から決まる（同じ日なら、だれが見ても同じ）。 */
    public static List<Integer> song(MinecraftServer server, FloorDef def, FloorFeatures.BellGroup group) {
        int day = ProgressionData.currentDay(server);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < group.bells().size(); i++) {
            order.add(i);
        }
        long seed = day * 1_000_003L + def.number() * 7919L + group.id().hashCode();
        Collections.shuffle(order, new Random(seed));
        return order;
    }

    /** 鐘の印の言葉（「月の鐘」なら「月」）。 */
    public static String word(FloorFeatures.Bell bell) {
        String name = bell.name();
        return name.endsWith("の鐘") ? name.substring(0, name.length() - 2) : name;
    }

    /** 今日の歌を、矢印でつないだ文（「月 → 星 → 鶴」）。 */
    public static String songText(MinecraftServer server, FloorDef def, FloorFeatures.BellGroup group) {
        StringBuilder text = new StringBuilder();
        for (int index : song(server, def, group)) {
            if (!text.isEmpty()) {
                text.append(" → ");
            }
            text.append(word(group.bells().get(index)));
        }
        return text.toString();
    }

    /** 歌の碑に触れた：今日の歌を見せる（触れた人には大きく、同じ階のパーティの仲間にはチャットで）。 */
    public static void showSong(ServerPlayer player, int floor, String groupId) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.BellGroup group = def == null ? null : def.features().bellGroup(groupId);
        if (group == null) {
            return;
        }
        MinecraftServer server = player.server;
        String song = songText(server, def, group);
        StringBuilder where = new StringBuilder();
        for (FloorFeatures.Bell bell : group.bells()) {
            if (!where.isEmpty()) {
                where.append("・");
            }
            where.append(bell.name()).append(bell.where().isEmpty() ? "" : "（" + bell.where() + "）");
        }
        boolean done = solved(server, floor, groupId);
        String body = Component.translatable(done ? "message.amemaru.bells.song_done" : "message.amemaru.bells.song", song, limitSeconds(group),
                where.toString()).getString();
        ModNetwork.sendTo(player, new StoryTextS2C(StoryTextS2C.Kind.ANCIENT,
                Component.translatable("message.amemaru.bells.song_title", group.name()).getString(), body, 200));
        ServerLevel level = player.serverLevel();
        BlockPos stele = def.world(group.stele());
        level.sendParticles(ParticleTypes.ENCHANT, stele.getX() + 0.5, stele.getY() + 1.6, stele.getZ() + 0.5, 40, 0.6, 0.6, 0.6, 0.6);
        for (ServerPlayer mate : jp.amemaru.lostconnection.party.PartyService.onlineMembers(player)) {
            FloorDef at = mate.level() == player.level() ? FloorDefs.floorAt(level, mate.position()) : null;
            if (at != null && at.number() == floor) {
                mate.sendSystemMessage(Component.translatable("message.amemaru.bells.song_chat", group.name(), song)
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        if (!jp.amemaru.lostconnection.party.PartyService.onlineMembers(player).contains(player)) {
            player.sendSystemMessage(Component.translatable("message.amemaru.bells.song_chat", group.name(), song).withStyle(ChatFormatting.AQUA));
        }
    }

    // ================================================================ 鐘を鳴らす

    /** 鐘を右クリックした。 */
    public static void ring(ServerPlayer player, int floor, String groupId, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        ripple(level, pos, false);
        if (ARENA.equals(groupId)) {
            jp.amemaru.lostconnection.enemy.boss.Mune.ringArenaBell(player, pos);
            return;
        }
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.BellGroup group = def == null ? null : def.features().bellGroup(groupId);
        if (group == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < group.bells().size(); i++) {
            if (def.world(group.bells().get(i).pos()).equals(pos)) {
                index = i;
            }
        }
        if (index < 0) {
            return;
        }
        MinecraftServer server = player.server;
        Data data = Data.get(server);
        if (data.solved(floor, groupId)) {
            player.displayClientMessage(Component.translatable("message.amemaru.bells.already", group.name()).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        List<Integer> song = song(server, def, group);
        int progress = data.progress(floor, groupId);
        FloorFeatures.Bell bell = group.bells().get(index);
        if (song.get(progress) != index) {
            boolean had = progress > 0;
            data.setProgress(floor, groupId, 0, 0L);
            reconcileGroup(level, def, group, data);
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
            for (ServerPlayer p : playersOn(level, def)) {
                p.displayClientMessage(Component.translatable(had ? "message.amemaru.bells.wrong_reset" : "message.amemaru.bells.wrong",
                        bell.name(), group.name()).withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        progress++;
        long now = level.getGameTime();
        data.setProgress(floor, groupId, progress, now);
        setLit(level, pos, true);
        ripple(level, pos, true);
        if (progress >= group.bells().size()) {
            solve(server, def, group, player);
            return;
        }
        long left = Math.max(0L, limitSeconds(group) - (now - data.started(floor, groupId)) / 20L);
        for (ServerPlayer p : playersOn(level, def)) {
            p.displayClientMessage(Component.translatable("message.amemaru.bells.rang", bell.name(), progress, group.bells().size(), left)
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** 鐘の組が解けた（管理者・自動テストからも）。 */
    public static void solve(MinecraftServer server, FloorDef def, FloorFeatures.BellGroup group, @Nullable ServerPlayer by) {
        Data data = Data.get(server);
        if (!data.solve(def.number(), group.id())) {
            return;
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        int[] c = count(server, def);
        if (tower != null) {
            reconcileGroup(tower, def, group, data);
            for (ServerPlayer p : playersOn(tower, def)) {
                p.sendSystemMessage(Component.translatable(group.sound() ? "message.amemaru.bells.solved_sound" : "message.amemaru.bells.solved",
                        group.name(), c[0], c[1]).withStyle(ChatFormatting.AQUA));
                if (group.sound()) {
                    ModNetwork.sendTo(p, new AnnouncementS2C(AnnouncementS2C.Kind.STORY,
                            Component.translatable("message.amemaru.bells.sound_returns", def.name()).getString()));
                }
            }
            for (FloorFeatures.Bell bell : group.bells()) {
                BlockPos at = def.world(bell.pos());
                tower.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 30, 0.4, 1.5, 0.4, 0.05);
            }
        }
        AmemaruMod.LOGGER.info("[amemaru] Bells {} on floor {} solved by {} ({}/{})", group.id(), def.number(),
                by == null ? "admin" : by.getGameProfile().getName(), c[0], c[1]);
        if (group.sound()) {
            // 音が戻った：霧と音の決まりを送り直し、戻った音で大きな鐘を鳴らす
            jp.amemaru.lostconnection.tower.FloorEnvService.resend(server);
            if (tower != null) {
                server.execute(() -> {
                    for (ServerPlayer p : playersOn(tower, def)) {
                        p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 0.6F);
                        p.playNotifySound(SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 0.8F);
                        jp.amemaru.lostconnection.story.StoryEventManager.play(p, AmemaruMod.id("floor8/sound_returns"), false);
                    }
                });
            }
        }
        resyncStory(server);
    }

    /** 管理者・自動テスト：組の進み具合を消す（解けていなければ）。 */
    public static void resetProgress(MinecraftServer server, FloorDef def, FloorFeatures.BellGroup group) {
        Data data = Data.get(server);
        data.setProgress(def.number(), group.id(), 0, 0L);
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower != null) {
            reconcileGroup(tower, def, group, data);
        }
    }

    /** 進行日が変わった：鐘はまた沈黙し、橋は沈み、ふたは閉じる（中に人がいれば、出るまで待つ）。階の音も消える。 */
    public static void onDayChanged(MinecraftServer server) {
        Data.get(server).clear();
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower != null) {
            for (FloorDef def : FloorDefs.all()) {
                if (!def.features().bells().isEmpty()) {
                    reconcile(tower, def);
                }
            }
        }
        jp.amemaru.lostconnection.tower.FloorEnvService.resend(server);
        resyncStory(server);
    }

    // ================================================================ 見えているもの

    /** 鐘の灯り・開く所・浮かぶ所を、保存の状態に合わせる（読み込まれている所だけ）。 */
    public static void reconcile(ServerLevel level, FloorDef def) {
        Data data = Data.get(level.getServer());
        for (FloorFeatures.BellGroup group : def.features().bells()) {
            reconcileGroup(level, def, group, data);
        }
    }

    private static void reconcileGroup(ServerLevel level, FloorDef def, FloorFeatures.BellGroup group, Data data) {
        boolean solved = data.solved(def.number(), group.id());
        int progress = data.progress(def.number(), group.id());
        List<Integer> song = song(level.getServer(), def, group);
        for (int i = 0; i < group.bells().size(); i++) {
            boolean lit = solved || song.subList(0, Math.min(progress, song.size())).contains(i);
            setLit(level, def.world(group.bells().get(i).pos()), lit);
        }
        boolean hold = !solved && group.hold() != null && anyoneIn(level, def, group.hold());
        for (FloorFeatures.Door door : group.opens()) {
            if (!solved && hold) {
                continue;
            }
            fill(level, def, door, solved ? Blocks.AIR.defaultBlockState() : stateOf(door, Blocks.BLUE_ICE));
        }
        for (FloorFeatures.Door raise : group.raises()) {
            if (!solved && anyoneIn(level, def, boxOf(def, raise).inflate(2.0, 3.0, 2.0), true)) {
                continue;
            }
            fill(level, def, raise, solved ? stateOf(raise, Blocks.STONE_BRICKS) : Blocks.AIR.defaultBlockState());
        }
    }

    private static BlockState stateOf(FloorFeatures.Door door, Block fallback) {
        Block block = ForgeRegistries.BLOCKS.getValue(door.block());
        return (block == null || block == Blocks.AIR ? fallback : block).defaultBlockState();
    }

    private static AABB boxOf(FloorDef def, FloorFeatures.Door door) {
        BlockPos min = def.world(door.min());
        BlockPos max = def.world(door.max());
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    private static boolean anyoneIn(ServerLevel level, FloorDef def, AABB localBox) {
        Vec3 o = def.originVec();
        return anyoneIn(level, def, localBox.move(o.x, o.y, o.z), true);
    }

    private static boolean anyoneIn(ServerLevel level, FloorDef def, AABB worldBox, boolean world) {
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && worldBox.contains(p.position())) {
                return true;
            }
        }
        return false;
    }

    /** 箱の中を、そのブロックにする（変わる所だけ。壁は、まわりにつながるように置く）。 */
    private static void fill(ServerLevel level, FloorDef def, FloorFeatures.Door door, BlockState want) {
        BlockPos min = def.world(door.min());
        BlockPos max = def.world(door.max());
        boolean changed = false;
        for (BlockPos at : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(at)) {
                continue;
            }
            BlockState now = level.getBlockState(at);
            if (now.getBlock() == want.getBlock()) {
                continue;
            }
            BlockState place = want.isAir() ? want : Block.updateFromNeighbourShapes(want, level, at);
            level.setBlock(at.immutable(), place, 3);
            changed = true;
        }
        if (changed && level.isLoaded(min)) {
            Vec3 c = new Vec3((min.getX() + max.getX()) / 2.0 + 0.5, min.getY() + 1.0, (min.getZ() + max.getZ()) / 2.0 + 0.5);
            level.sendParticles(want.isAir() ? ParticleTypes.SNOWFLAKE : ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 60,
                    Math.max(1.0, (max.getX() - min.getX()) / 2.0), 1.0, Math.max(1.0, (max.getZ() - min.getZ()) / 2.0), 0.05);
            level.playSound(null, c.x, c.y, c.z, want.isAir() ? SoundEvents.GLASS_BREAK : SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.6F, 0.6F);
        }
    }

    private static void setLit(ServerLevel level, BlockPos pos, boolean lit) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof LakeBlocks.Bell && state.getValue(LakeBlocks.LIT) != lit) {
            level.setBlock(pos, state.setValue(LakeBlocks.LIT, lit), 3);
        }
    }

    /** 鐘のまわりに広がる光の輪（音のない湖でも、鳴ったことが目で分かる）。 */
    public static void ripple(ServerLevel level, BlockPos pos, boolean bright) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.3;
        double cz = pos.getZ() + 0.5;
        int n = bright ? 40 : 24;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0 * i / n;
            level.sendParticles(bright ? ParticleTypes.END_ROD : ParticleTypes.SNOWFLAKE, cx + Math.cos(a) * 1.2, cy, cz + Math.sin(a) * 1.2, 0,
                    Math.cos(a), 0.0, Math.sin(a), bright ? 0.25 : 0.15);
        }
        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.4F, bright ? 1.0F : 0.7F);
    }

    // ================================================================ 毎 tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 10 != 3) {
            return;
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower == null) {
            return;
        }
        Data data = Data.get(server);
        long now = tower.getGameTime();
        for (FloorDef def : FloorDefs.all()) {
            if (def.features().bells().isEmpty()) {
                continue;
            }
            List<ServerPlayer> players = playersOn(tower, def);
            if (players.isEmpty()) {
                continue;
            }
            for (FloorFeatures.BellGroup group : def.features().bells()) {
                int progress = data.progress(def.number(), group.id());
                if (progress > 0 && now - data.started(def.number(), group.id()) > limitSeconds(group) * 20L) {
                    data.setProgress(def.number(), group.id(), 0, 0L);
                    reconcileGroup(tower, def, group, data);
                    for (ServerPlayer p : players) {
                        p.sendSystemMessage(Component.translatable("message.amemaru.bells.timeout", group.name()).withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            if (tick % 40 == 3) {
                reconcile(tower, def);
            }
        }
    }

    static List<ServerPlayer> playersOn(ServerLevel level, FloorDef def) {
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
