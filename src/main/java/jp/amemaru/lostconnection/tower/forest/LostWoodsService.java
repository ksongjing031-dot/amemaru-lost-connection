package jp.amemaru.lostconnection.tower.forest;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModItems;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 迷いの森（Phase 8 その1。2階「忘れられた森」のギミック。Phase 0-A 9章「迷いの森。正しい道に『結び目の灯』を置いていかないと
 * 同じ場所に戻される。鐘を鳴らす人と、こだまの方向を聞く人に分かれて道を探す」）。
 *
 * <ul>
 *   <li>区画の中から出口へ出ると、霧が濃くなって区画の入口へ戻される。正しい出口の「結び目の灯台」に結び目の灯を置くと、
 *       その区画の道がつながり、正しい出口から先へ行ける（みんな。進行日が変わるまで）【補完】</li>
 *   <li>区画の鐘を鳴らすと、{@code echo_delay} 秒あとに正しい出口からこだまが返る。鐘から {@code listen_distance} 以上離れた
 *       人にだけ方角が分かる（鐘の近くでは鐘の音が大きくて聞き取れない）。奥の区画では、低い「まぎらわしいこだま」も返る
 *       （正しいこだまは、鐘と同じ高い音）【補完】</li>
 *   <li>鐘を鳴らすと、結び目の灯を1つもらえる（持っていなければ。30秒に1回）。違う出口の灯台に置くと、灯は消えて入口へ戻される</li>
 *   <li>すべての区画がつながると、ボスの部屋の封印がひらく（{@code seal_requires: lost_woods}）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class LostWoodsService {
    public static final int LANTERN_COOLDOWN_TICKS = 30 * 20;
    private static final Map<UUID, String> INSIDE = new HashMap<>();
    private static final Map<String, Long> LANTERN_GIVEN = new HashMap<>();
    private static final List<Echo> ECHOES = new ArrayList<>();

    private record Echo(int floor, String woods, long at, boolean decoy, BlockPos post, BlockPos bell) {
    }

    private LostWoodsService() {
    }

    // ================================================================ つながった区画（進行日が変わるまで）

    /** つながった区画（{@code amemaru_lost_woods.dat}。「階:区画」）。進行日が変わると、ほどける。 */
    public static final class Data extends SavedData {
        private static final String NAME = "amemaru_lost_woods";
        private final Set<String> tied = new LinkedHashSet<>();

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            for (Tag t : tag.getList("tied", Tag.TAG_STRING)) {
                data.tied.add(t.getAsString());
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (String key : tied) {
                list.add(StringTag.valueOf(key));
            }
            tag.put("tied", list);
            return tag;
        }

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        public boolean isTied(int floor, String woods) {
            return tied.contains(floor + ":" + woods);
        }

        public boolean tie(int floor, String woods) {
            boolean added = tied.add(floor + ":" + woods);
            if (added) {
                setDirty();
            }
            return added;
        }

        public void clear() {
            if (!tied.isEmpty()) {
                tied.clear();
                setDirty();
            }
        }
    }

    public static boolean isTied(MinecraftServer server, int floor, String woods) {
        return Data.get(server).isTied(floor, woods);
    }

    /** その階の区画が、すべてつながったか（ボスの部屋の封印）。 */
    public static boolean allTied(MinecraftServer server, FloorDef def) {
        Data data = Data.get(server);
        for (FloorFeatures.LostWoods woods : def.features().lostWoods()) {
            if (!data.isTied(def.number(), woods.id())) {
                return false;
            }
        }
        return true;
    }

    /** つながった区画の印（石柱と同じく、区画 i がつながったら i 番目のビット。クライアントの封印の判定に送る）。 */
    public static int tieBits(MinecraftServer server, FloorDef def) {
        Data data = Data.get(server);
        int bits = 0;
        List<FloorFeatures.LostWoods> list = def.features().lostWoods();
        for (int i = 0; i < list.size(); i++) {
            if (data.isTied(def.number(), list.get(i).id())) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    /** 進行日が変わった：結び目の灯は消え、迷いの森はまた迷う。 */
    public static void onDayChanged(MinecraftServer server) {
        Data.get(server).clear();
        for (FloorDef def : FloorDefs.all()) {
            for (FloorFeatures.LostWoods woods : def.features().lostWoods()) {
                for (FloorFeatures.Exit exit : woods.exits()) {
                    setPost(server, def, exit.post(), false);
                }
            }
        }
        resyncStory(server);
    }

    /** 管理者用・自動テスト用：区画をつなぐ（灯台も灯す）。 */
    public static boolean adminTie(MinecraftServer server, int floor, String woodsId) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.LostWoods woods = def == null ? null : def.features().lostWoods(woodsId);
        if (woods == null || woods.correctExit() == null) {
            return false;
        }
        tieSection(server, def, woods, null);
        return true;
    }

    // ================================================================ 鐘

    /** 鐘を鳴らす。 */
    public static void ringBell(ServerPlayer player, int floor, String woodsId, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.LostWoods woods = def == null ? null : def.features().lostWoods(woodsId);
        ServerLevel level = player.serverLevel();
        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.6F, 1.5F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4F, 1.6F);
        level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 6, 0.4, 0.2, 0.4, 1.0);
        if (woods == null) {
            return;
        }
        if (isTied(player.server, floor, woodsId)) {
            player.displayClientMessage(Component.translatable("message.amemaru.lost_woods.already", woods.name()).withStyle(ChatFormatting.AQUA),
                    true);
            return;
        }
        long now = level.getGameTime();
        // こだま（正しい出口と、まぎらわしい出口）。11月26日：ひとり確認モードでは、鐘から離れる間があるように、返るのを遅らせる。
        // 11月27日：その階にほかの人がいない（ひとりで挑んでいる）ときも、同じように遅らせる
        long delay = jp.amemaru.lostconnection.tower.SoloTest.seconds(woods.echoDelay(), jp.amemaru.lostconnection.tower.SoloTest.ECHO_SECONDS,
                jp.amemaru.lostconnection.tower.SoloTest.aloneOnFloor(player.server, floor)) * 20L;
        FloorFeatures.Exit correct = woods.correctExit();
        if (correct != null) {
            ECHOES.add(new Echo(floor, woodsId, now + delay, false, correct.post(), pos));
        }
        List<FloorFeatures.Exit> wrong = new ArrayList<>();
        for (FloorFeatures.Exit exit : woods.exits()) {
            if (!exit.correct()) {
                wrong.add(exit);
            }
        }
        for (int i = 0; i < woods.decoys() && !wrong.isEmpty(); i++) {
            FloorFeatures.Exit decoy = wrong.remove(level.random.nextInt(wrong.size()));
            ECHOES.add(new Echo(floor, woodsId, now + delay + 10 + i * 8L, true, decoy.post(), pos));
        }
        // 結び目の灯（持っていなければ。30秒に1回）
        Item lantern = ModItems.MANIFEST.get("knot_lantern").get();
        String key = player.getUUID() + ":" + floor + ":" + woodsId;
        Long last = LANTERN_GIVEN.get(key);
        if (count(player, lantern) == 0 && (last == null || now - last >= LANTERN_COOLDOWN_TICKS)) {
            LANTERN_GIVEN.put(key, now);
            ItemStack stack = new ItemStack(lantern);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            player.displayClientMessage(Component.translatable("message.amemaru.lost_woods.lantern").withStyle(ChatFormatting.GOLD), false);
        }
        player.displayClientMessage(Component.translatable("message.amemaru.lost_woods.rang", woods.name(), delay / 20L)
                .withStyle(ChatFormatting.AQUA), true);
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n + (player.getOffhandItem().is(item) ? player.getOffhandItem().getCount() : 0);
    }

    private static void echo(MinecraftServer server, Echo echo) {
        FloorDef def = FloorDefs.byNumber(echo.floor());
        FloorFeatures.LostWoods woods = def == null ? null : def.features().lostWoods(echo.woods());
        ServerLevel tower = server.getLevel(FloorDefs.TOWER);
        if (woods == null || tower == null) {
            return;
        }
        Vec3 origin = def.originVec();
        BlockPos post = echo.post().offset(def.origin());
        tower.playSound(null, post, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3.0F, echo.decoy() ? 0.7F : 1.6F);
        tower.playSound(null, post, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.2F, echo.decoy() ? 0.6F : 1.5F);
        tower.sendParticles(echo.decoy() ? ParticleTypes.SMOKE : ParticleTypes.NOTE, post.getX() + 0.5, post.getY() + 1.6,
                post.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.5);
        Vec3 bell = Vec3.atCenterOf(echo.bell());   // 鐘はワールドの位置（灯台は階の中の位置）
        for (ServerPlayer player : tower.players()) {
            if (!FloorFeatures.inside(woods.box(), player.position(), origin)) {
                continue;
            }
            if (player.position().distanceTo(bell) < woods.listenDistance()) {
                if (!echo.decoy()) {
                    player.displayClientMessage(Component.translatable("message.amemaru.lost_woods.too_close")
                            .withStyle(ChatFormatting.GRAY), true);
                }
                continue;
            }
            Component dir = direction(player.position(), Vec3.atCenterOf(post));
            player.displayClientMessage(Component.translatable(echo.decoy() ? "message.amemaru.lost_woods.echo_low"
                    : "message.amemaru.lost_woods.echo_high", dir).withStyle(echo.decoy() ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA),
                    false);
        }
    }

    /** 方角（北・北東・東……）。 */
    public static Component direction(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double angle = Math.toDegrees(Math.atan2(dx, -dz));     // 北が0、東が90
        int index = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        String[] names = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
        return Component.translatable("direction.amemaru." + names[index]);
    }

    // ================================================================ 結び目の灯台

    /** 灯台に結び目の灯を置く。 */
    public static void placeKnot(ServerPlayer player, int floor, String woodsId, int exitIndex, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.LostWoods woods = def == null ? null : def.features().lostWoods(woodsId);
        if (woods == null || exitIndex < 0 || exitIndex >= woods.exits().size()) {
            return;
        }
        if (isTied(player.server, floor, woodsId)) {
            player.displayClientMessage(Component.translatable("message.amemaru.lost_woods.already", woods.name()).withStyle(ChatFormatting.AQUA),
                    true);
            return;
        }
        Item lantern = ModItems.MANIFEST.get("knot_lantern").get();
        if (count(player, lantern) == 0) {
            player.displayClientMessage(Component.translatable("message.amemaru.lost_woods.no_lantern").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        take(player, lantern);
        FloorFeatures.Exit exit = woods.exits().get(exitIndex);
        if (exit.correct()) {
            tieSection(player.server, def, woods, player);
        } else {
            ServerLevel level = player.serverLevel();
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.3, 0.4, 0.3, 0.02);
            player.displayClientMessage(Component.translatable("message.amemaru.lost_woods.wrong").withStyle(ChatFormatting.GRAY), false);
            loop(player, def, woods);
        }
    }

    private static void take(ServerPlayer player, Item item) {
        if (player.getAbilities().instabuild) {
            return;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                stack.shrink(1);
                return;
            }
        }
        if (player.getOffhandItem().is(item)) {
            player.getOffhandItem().shrink(1);
        }
    }

    private static void tieSection(MinecraftServer server, FloorDef def, FloorFeatures.LostWoods woods, @Nullable ServerPlayer by) {
        if (!Data.get(server).tie(def.number(), woods.id())) {
            return;
        }
        FloorFeatures.Exit correct = woods.correctExit();
        if (correct != null) {
            setPost(server, def, correct.post(), true);
            ServerLevel tower = server.getLevel(FloorDefs.TOWER);
            if (tower != null) {
                BlockPos post = correct.post().offset(def.origin());
                tower.playSound(null, post, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 1.2F);
                tower.sendParticles(ParticleTypes.END_ROD, post.getX() + 0.5, post.getY() + 1.0, post.getZ() + 0.5, 30, 0.6, 0.8, 0.6, 0.05);
            }
        }
        // その階にいる人に知らせる（つないだ人の名前と、残りの区画）
        int left = 0;
        for (FloorFeatures.LostWoods other : def.features().lostWoods()) {
            left += isTied(server, def.number(), other.id()) ? 0 : 1;
        }
        Component text = by == null ? Component.translatable("message.amemaru.lost_woods.tied_admin", woods.name(), left)
                : Component.translatable("message.amemaru.lost_woods.tied", by.getDisplayName(), woods.name(), left);
        ServerLevel tower = server.getLevel(FloorDefs.TOWER);
        if (tower != null) {
            for (ServerPlayer p : tower.players()) {
                FloorDef here = FloorDefs.floorAt(tower, p.position());
                if (here != null && here.number() == def.number()) {
                    p.sendSystemMessage(text.copy().withStyle(ChatFormatting.GOLD));
                    if (left == 0) {
                        ModNetwork.sendTo(p, new AnnouncementS2C(AnnouncementS2C.Kind.STORY,
                                Component.translatable("message.amemaru.lost_woods.all_tied", def.name()).getString()));
                    }
                }
            }
        }
        resyncStory(server);
    }

    /**
     * 結び目の灯台の灯りを、今の結び目に合わせる（正しい出口の灯台で、その森が結ばれていれば灯る）。灯台が読み込まれたときに
     * 呼ぶ（{@link jp.amemaru.lostconnection.tower.FloorThingBlockEntity#onLoad}）。結んだとき・日が変わったときは、
     * 読み込まれている灯台だけを変えるので、読み込まれていなかった灯台は、ここで直る。
     */
    public static void syncPost(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof jp.amemaru.lostconnection.tower.FloorThingBlockEntity thing)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(ForestBlocks.LIT)) {
            return;
        }
        FloorDef def = FloorDefs.byNumber(thing.floor());
        FloorFeatures.LostWoods woods = def == null ? null : def.features().lostWoods(thing.group());
        boolean lit = woods != null && thing.index() >= 0 && thing.index() < woods.exits().size()
                && woods.exits().get(thing.index()).correct() && isTied(level.getServer(), thing.floor(), woods.id());
        if (state.getValue(ForestBlocks.LIT) != lit) {
            level.setBlock(pos, state.setValue(ForestBlocks.LIT, lit), 3);
        }
    }

    private static void setPost(MinecraftServer server, FloorDef def, BlockPos rel, boolean lit) {
        ServerLevel tower = server.getLevel(FloorDefs.TOWER);
        if (tower == null) {
            return;
        }
        BlockPos pos = rel.offset(def.origin());
        if (!tower.isLoaded(pos)) {
            return;
        }
        BlockState state = tower.getBlockState(pos);
        if (state.hasProperty(ForestBlocks.LIT) && state.getValue(ForestBlocks.LIT) != lit) {
            tower.setBlock(pos, state.setValue(ForestBlocks.LIT, lit), 3);
        }
    }

    private static void resyncStory(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerDataCapability.get(p).ifPresent(data -> data.markDirty(PlayerData.SYNC_STORY));
        }
    }

    // ================================================================ 戻される

    /** 区画の入口へ戻す（霧の演出つき）。 */
    public static void loop(ServerPlayer player, FloorDef def, FloorFeatures.LostWoods woods) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.8, 0.8, 0.8, 0.02);
        Vec3 at = def.originVec().add(woods.entry().pos());
        player.teleportTo(level, at.x, at.y, at.z, woods.entry().yaw(), player.getXRot());
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1.0, at.z, 30, 0.8, 0.8, 0.8, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.6F, 1.4F);
        player.displayClientMessage(Component.translatable("message.amemaru.lost_woods.looped", woods.name()).withStyle(ChatFormatting.GRAY),
                false);
        INSIDE.put(player.getUUID(), woods.id());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel tower = server.getLevel(FloorDefs.TOWER);
        if (tower == null) {
            return;
        }
        long now = tower.getGameTime();
        if (!ECHOES.isEmpty()) {
            Iterator<Echo> it = ECHOES.iterator();
            List<Echo> due = new ArrayList<>();
            while (it.hasNext()) {
                Echo echo = it.next();
                if (now >= echo.at()) {
                    due.add(echo);
                    it.remove();
                }
            }
            for (Echo echo : due) {
                echo(server, echo);
            }
        }
        if (server.getTickCount() % 4 != 0) {
            return;
        }
        for (ServerPlayer player : tower.players()) {
            if (player.isSpectator()) {
                continue;
            }
            FloorDef def = FloorDefs.floorAt(tower, player.position());
            if (def == null || def.features().lostWoods().isEmpty()) {
                INSIDE.remove(player.getUUID());
                continue;
            }
            check(server, player, def);
        }
    }

    private static void check(MinecraftServer server, ServerPlayer player, FloorDef def) {
        Vec3 origin = def.originVec();
        Vec3 pos = player.position();
        for (FloorFeatures.LostWoods woods : def.features().lostWoods()) {
            if (FloorFeatures.inside(woods.box(), pos, origin)) {
                INSIDE.put(player.getUUID(), woods.id());
                return;
            }
        }
        String was = INSIDE.get(player.getUUID());
        if (was == null) {
            return;
        }
        FloorFeatures.LostWoods woods = def.features().lostWoods(was);
        if (woods == null) {
            INSIDE.remove(player.getUUID());
            return;
        }
        for (FloorFeatures.Exit exit : woods.exits()) {
            if (FloorFeatures.inside(exit.box(), pos, origin)) {
                if (exit.correct() && isTied(server, def.number(), woods.id())) {
                    INSIDE.remove(player.getUUID());   // 正しい出口から、先へ
                } else {
                    loop(player, def, woods);
                }
                return;
            }
        }
        INSIDE.remove(player.getUUID());
    }

    /** 自動テスト用：その人が今いる（いた）区画。 */
    @Nullable
    public static String insideOf(ServerPlayer player) {
        return INSIDE.get(player.getUUID());
    }
}
