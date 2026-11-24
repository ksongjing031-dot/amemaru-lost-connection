package jp.amemaru.lostconnection.tower.temple;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.network.s2c.StoryTextS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.story.StoryEventManager;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

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
 * 4階「崩壊した神殿」のギミック（11月24日。Phase 0-A 9章の4階：「壁画パズル。回転するパネルを正しく並べると、第一時代から
 * 第二時代までの歴史が一枚の絵になる（表：壁画の復元・音の順番）」）。
 *
 * <ul>
 *   <li>壁画：パネルを右クリックで90度ずつ回す。組のパネルがすべて正しい向きになると、元の絵に戻り、歴史の一文が読める。
 *       扉があれば開く。みんなの分で、進行日が変わるまで（朝にはまた崩れている）【補完】</li>
 *   <li>3つの壁画がすべて戻ると、歌の大広間の前の「一枚の絵」が灯る（封印の条件 {@link FloorFeatures#SEAL_MURALS}）</li>
 *   <li>音の順番：鈴の石を鳴らすと、音の床が順に光って鳴る。同じ順番で床を踏むと扉が開く（ボスの「歌の順番どおりに4つの音の床を
 *       踏むと無防備になる」の練習）。違う床を踏むと、はじめから</li>
 *   <li>物語の品（結晶騎士団の遺品）：右クリックで受け取る（人ごとに1回）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class TempleService {
    /** 音の順番の1音の間（tick）。 */
    public static final int NOTE_TICKS = 14;
    /** 鳴らし終えてから、踏み終えるまでの時間（tick）。 */
    public static final int ANSWER_TICKS = 20 * 20;
    /**
     * 床に乗ってから、踏んだと数えるまで（tick）。11月25日のご指示「音の通りに床を踏んでも失敗になる」：祈りの間の床は一列に
     * 並んでいて、離れた床へ歩くと間の床を通り、そこで「違う床」になっていた。歩いて通り過ぎる間（1マスでおよそ5 tick）は
     * 数えず、乗って止まったら数える（音が鳴ったら数えた合図）。
     */
    public static final int DWELL_TICKS = 6;

    /** 鳴らしている・答えを待っている順番（「階:組」）。 */
    private static final Map<String, Performance> SONGS = new HashMap<>();
    /** 最後に踏んだ床（同じ床を踏み続けても、1回と数える）。 */
    /** 人ごとに、今乗っている音の床（降りると消える。乗っている間は数えない）。人は、抜けた・生き返った（別の人の入れ物）で消す。 */
    private static final Map<UUID, Standing> LAST_STEP = new HashMap<>();

    /** 乗っている人（{@code counted}：もう踏んだと数えた）。 */
    private record Standing(ServerPlayer player, BlockPos plate, int note, long since, boolean counted) {
        Standing countedNow() {
            return new Standing(player, plate, note, since, true);
        }
    }

    private static final class Performance {
        final int[] notes;
        long startedAt;
        int answered;
        long deadline;

        Performance(int[] notes, long now) {
            this.notes = notes;
            this.startedAt = now;
        }
    }

    private TempleService() {
    }

    // ================================================================ 保存（みんなの分。進行日が変わるまで）

    public static final class Data extends SavedData {
        private static final String NAME = "amemaru_temple";
        private final Set<String> murals = new LinkedHashSet<>();
        private final Set<String> songs = new LinkedHashSet<>();

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            for (Tag t : tag.getList("murals", Tag.TAG_STRING)) {
                data.murals.add(t.getAsString());
            }
            for (Tag t : tag.getList("songs", Tag.TAG_STRING)) {
                data.songs.add(t.getAsString());
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag m = new ListTag();
            murals.forEach(k -> m.add(StringTag.valueOf(k)));
            tag.put("murals", m);
            ListTag s = new ListTag();
            songs.forEach(k -> s.add(StringTag.valueOf(k)));
            tag.put("songs", s);
            return tag;
        }

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        public boolean solved(int floor, String mural) {
            return murals.contains(floor + ":" + mural);
        }

        public boolean solve(int floor, String mural) {
            boolean added = murals.add(floor + ":" + mural);
            if (added) {
                setDirty();
            }
            return added;
        }

        public boolean sung(int floor, String song) {
            return songs.contains(floor + ":" + song);
        }

        public boolean sing(int floor, String song) {
            boolean added = songs.add(floor + ":" + song);
            if (added) {
                setDirty();
            }
            return added;
        }

        public void clear() {
            if (!murals.isEmpty() || !songs.isEmpty()) {
                murals.clear();
                songs.clear();
                setDirty();
            }
        }
    }

    public static boolean solved(MinecraftServer server, int floor, String mural) {
        return Data.get(server).solved(floor, mural);
    }

    /** その階の壁画（一枚の絵をのぞく）がすべて戻ったか（封印の条件 murals）。 */
    public static boolean allSolved(MinecraftServer server, FloorDef def) {
        int[] c = count(server, def);
        return c[1] > 0 && c[0] == c[1];
    }

    /** 戻った壁画の数と、ぜんぶの数（一枚の絵をのぞく）。 */
    public static int[] count(MinecraftServer server, FloorDef def) {
        Data data = Data.get(server);
        int done = 0;
        int total = 0;
        for (FloorFeatures.Mural mural : def.features().murals()) {
            if (mural.display()) {
                continue;
            }
            total++;
            if (data.solved(def.number(), mural.id())) {
                done++;
            }
        }
        return new int[]{done, total};
    }

    /** 戻った壁画の印（封印の判定をクライアントでも同じにするため。壁画 i が戻ったら i 番目のビット）。 */
    public static int muralBits(MinecraftServer server, FloorDef def) {
        Data data = Data.get(server);
        int bits = 0;
        int i = 0;
        for (FloorFeatures.Mural mural : def.features().murals()) {
            if (mural.display()) {
                continue;
            }
            if (data.solved(def.number(), mural.id())) {
                bits |= 1 << i;
            }
            i++;
        }
        return bits;
    }

    // ================================================================ 壁画

    /** パネルを90度回す。組がそろったら、壁画が戻る。 */
    public static void turnPanel(ServerPlayer player, int floor, String muralId, int index, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.Mural mural = def == null ? null : def.features().mural(muralId);
        ServerLevel level = player.serverLevel();
        if (mural == null || mural.display()) {
            player.displayClientMessage(Component.translatable("message.amemaru.mural.display").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (solved(player.server, floor, mural.id())) {
            player.displayClientMessage(Component.translatable("message.amemaru.mural.already", mural.name()).withStyle(ChatFormatting.AQUA),
                    true);
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(TempleBlocks.TURN)) {
            return;
        }
        level.setBlock(pos, state.setValue(TempleBlocks.TURN, (state.getValue(TempleBlocks.TURN) + 1) % 4), 3);
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6F, 1.4F);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.8F, 0.7F);
        level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.01);
        if (correct(level, def, mural)) {
            solve(player.server, def, mural, player);
        } else {
            int right = rightPanels(level, def, mural);
            player.displayClientMessage(Component.translatable("message.amemaru.mural.progress", mural.name(), right, mural.panels().size())
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** パネルがすべて正しい向きか（読み込まれていないパネルがあれば false）。 */
    public static boolean correct(ServerLevel level, FloorDef def, FloorFeatures.Mural mural) {
        return rightPanels(level, def, mural) == mural.panels().size();
    }

    private static int rightPanels(ServerLevel level, FloorDef def, FloorFeatures.Mural mural) {
        int right = 0;
        for (FloorFeatures.Panel panel : mural.panels()) {
            BlockPos at = def.world(panel.pos());
            if (!level.isLoaded(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            if (state.hasProperty(TempleBlocks.TURN) && state.getValue(TempleBlocks.TURN) == panel.answer()) {
                right++;
            }
        }
        return right;
    }

    /** 壁画が戻った（管理者・自動テストからも）。 */
    public static void solve(MinecraftServer server, FloorDef def, FloorFeatures.Mural mural, @Nullable ServerPlayer by) {
        if (!Data.get(server).solve(def.number(), mural.id())) {
            return;
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        int[] c = count(server, def);
        if (tower != null) {
            BlockPos center = def.world(mural.panels().get(mural.panels().size() / 2).pos());
            tower.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.4F, 1.4F);
            tower.playSound(null, center, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 0.8F);
            for (FloorFeatures.Panel panel : mural.panels()) {
                BlockPos at = def.world(panel.pos());
                tower.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 10, 0.4, 0.4, 0.4, 0.04);
            }
            if (mural.door() != null) {
                door(tower, def, mural.door(), true);
            }
            // 11月24日：管理者のコマンドで戻したときも、パネルを正しい向きにそろえる（前は崩れた絵のまま「戻った」になった）
            straighten(tower, def, mural);
            for (ServerPlayer p : playersOn(tower, def)) {
                p.sendSystemMessage(Component.translatable("message.amemaru.mural.solved", mural.name(), c[0], c[1])
                        .withStyle(ChatFormatting.AQUA));
                if (!mural.text().isEmpty() && p.blockPosition().distSqr(center) < 28 * 28) {
                    ModNetwork.sendTo(p, new StoryTextS2C(StoryTextS2C.Kind.ANCIENT, mural.name(), mural.text(), 180));
                }
                if (c[0] == c[1]) {
                    ModNetwork.sendTo(p, new AnnouncementS2C(AnnouncementS2C.Kind.STORY,
                            Component.translatable("message.amemaru.mural.all", def.name()).getString()));
                }
            }
            if (c[0] == c[1]) {
                lightDisplays(tower, def, true);
            }
        }
        AmemaruMod.LOGGER.info("[amemaru] Mural {} on floor {} restored by {} ({}/{})", mural.id(), def.number(),
                by == null ? "admin" : by.getGameProfile().getName(), c[0], c[1]);
        resyncStory(server);
    }

    /** 「一枚の絵」を灯す・消す（読み込まれている所だけ。残りは人が来たとき {@link #reconcile}）。 */
    private static void lightDisplays(ServerLevel level, FloorDef def, boolean lit) {
        for (FloorFeatures.Mural mural : def.features().murals()) {
            if (!mural.display()) {
                continue;
            }
            for (FloorFeatures.Panel panel : mural.panels()) {
                BlockPos at = def.world(panel.pos());
                if (!level.isLoaded(at)) {
                    continue;
                }
                BlockState state = level.getBlockState(at);
                if (state.hasProperty(TempleBlocks.LIT) && state.getValue(TempleBlocks.LIT) != lit) {
                    level.setBlock(at, state.setValue(TempleBlocks.LIT, lit), 3);
                    if (lit) {
                        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.03);
                    }
                }
            }
        }
    }

    /** 扉を開ける（空気に）・閉じる（扉のブロックに）。読み込まれている所だけ。 */
    private static void door(ServerLevel level, FloorDef def, FloorFeatures.Door door, boolean open) {
        net.minecraft.world.level.block.Block block = ForgeRegistries.BLOCKS.getValue(door.block());
        BlockState closed = block == null ? Blocks.STONE_BRICKS.defaultBlockState() : block.defaultBlockState();
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
            level.playSound(null, min, open ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.6F, 0.5F);
            level.sendParticles(ParticleTypes.CLOUD, (min.getX() + max.getX()) / 2.0 + 0.5, min.getY() + 1.0,
                    (min.getZ() + max.getZ()) / 2.0 + 0.5, 20, 0.8, 0.8, 0.8, 0.02);
        }
    }

    // ================================================================ 音の順番

    private static final float[] PITCH = {0.71F, 0.89F, 1.12F, 1.41F};

    /** 音の高さ（音の床の番号 0〜3）。ボスの歌にも使う。 */
    public static float pitch(int note) {
        return PITCH[Math.floorMod(note, PITCH.length)];
    }

    /** 1つの音を鳴らす（音の床を光らせ、鈴の音と音符）。 */
    public static void playNote(ServerLevel level, BlockPos plate, int note) {
        level.playSound(null, plate, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 2.0F, pitch(note));
        level.playSound(null, plate, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4F, pitch(note));
        level.sendParticles(ParticleTypes.NOTE, plate.getX() + 0.5, plate.getY() + 1.2, plate.getZ() + 0.5, 4, 0.3, 0.2, 0.3, note / 4.0);
        level.sendParticles(ParticleTypes.END_ROD, plate.getX() + 0.5, plate.getY() + 0.5, plate.getZ() + 0.5, 8, 0.35, 0.3, 0.35, 0.03);
        setLit(level, plate, true);
    }

    /** 音の床の光（踏み終えたら、少しあとで消す）。 */
    public static void setLit(ServerLevel level, BlockPos plate, boolean lit) {
        if (!level.isLoaded(plate)) {
            return;
        }
        BlockState state = level.getBlockState(plate);
        if (state.hasProperty(TempleBlocks.LIT) && state.getValue(TempleBlocks.LIT) != lit && state.getBlock() instanceof TempleBlocks.SoundPlate) {
            level.setBlock(plate, state.setValue(TempleBlocks.LIT, lit), 3);
        }
    }

    /** 鈴の石を鳴らす：音の順番を決めて、順に鳴らす（踏む前に、もう一度鳴らすと、同じ順番をもう一度）。 */
    public static void ringChime(ServerPlayer player, int floor, String songId, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.Song song = def == null ? null : def.features().song(songId);
        ServerLevel level = player.serverLevel();
        if (song == null) {
            return;
        }
        if (Data.get(player.server).sung(floor, song.id())) {
            player.displayClientMessage(Component.translatable("message.amemaru.song.already", song.name()).withStyle(ChatFormatting.AQUA), true);
            return;
        }
        String key = floor + ":" + song.id();
        long now = level.getGameTime();
        Performance perf = SONGS.get(key);
        if (perf == null || perf.answered > 0 || now > perf.deadline) {
            int[] notes = new int[song.length()];
            for (int i = 0; i < notes.length; i++) {
                notes[i] = level.random.nextInt(song.plates().size());
                if (i > 0 && notes[i] == notes[i - 1]) {
                    notes[i] = (notes[i] + 1 + level.random.nextInt(song.plates().size() - 1)) % song.plates().size();
                }
            }
            perf = new Performance(notes, now);
            SONGS.put(key, perf);
        } else {
            perf.startedAt = now;
        }
        perf.answered = 0;
        perf.deadline = now + (long) song.length() * NOTE_TICKS + ANSWER_TICKS;
        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2F, 1.6F);
        player.displayClientMessage(Component.translatable("message.amemaru.song.listen", song.length()).withStyle(ChatFormatting.AQUA), true);
    }

    /** 自動テスト用：床に乗った回数（同じ床に乗り続けている間は数えない）。 */
    private static int countedSteps;
    /** 自動テスト用：踏んだと数えた回数（乗って {@link #DWELL_TICKS} たった）。 */
    private static int confirmedSteps;

    public static int countedSteps() {
        return countedSteps;
    }

    public static int confirmedSteps() {
        return confirmedSteps;
    }

    /** その人が乗って、踏んだと数えた床（乗っていない・まだ数えていなければ null）。ボスの歌の答えで使う。 */
    @Nullable
    public static BlockPos countedPlate(ServerPlayer player) {
        Standing standing = LAST_STEP.get(player.getUUID());
        return standing != null && standing.counted() && standing.player() == player && standingOn(player, standing.plate())
                ? standing.plate() : null;
    }

    /** 答えを受け付けはじめる時（最後の音が鳴り始めた時。11月25日：前は鳴り終えて少したってからで、すぐ踏むと数えなかった）。 */
    private static long answerFrom(Performance perf) {
        return perf.startedAt + (long) Math.max(0, perf.notes.length - 1) * NOTE_TICKS;
    }

    /** 自動テスト用：今の答えの進み（いくつ正しく踏んだか。鳴らしていなければ -1）。 */
    public static int songAnswered(int floor, String songId) {
        Performance perf = SONGS.get(floor + ":" + songId);
        return perf == null ? -1 : perf.answered;
    }

    /** 自動テスト用：鳴らしている（答えを待っている）音の順番（床の番号。鳴らしていなければ空）。 */
    public static int[] songNotes(int floor, String songId) {
        Performance perf = SONGS.get(floor + ":" + songId);
        return perf == null ? new int[0] : perf.notes.clone();
    }

    /** その人が、その音の床の上に立っているか（足の真ん中のマスがその床で、足が床の上から半ブロックまで。観戦中の人は数えない）。 */
    public static boolean standingOn(ServerPlayer player, BlockPos plate) {
        return !player.isSpectator() && player.blockPosition().equals(plate) && player.getY() <= plate.getY() + 0.5;
    }

    /** 自動テスト用：その人が今乗っている音の床（なければ null）。 */
    @Nullable
    public static BlockPos lastStep(ServerPlayer player) {
        Standing standing = LAST_STEP.get(player.getUUID());
        return standing == null ? null : standing.plate();
    }

    /**
     * 床に乗った（{@link TempleBlocks.SoundPlate}。体が床のマスに入っている間、動くたびに呼ばれる）。乗ったことを覚えるだけで、
     * 踏んだと数えるのは、乗ったまま {@link #DWELL_TICKS} たってから（{@link #onServerTick}。歩いて通り過ぎた床は数えない）。
     * 同じ床に乗っている間は1回だけ数え、いちど降りて（{@link #onServerTick} が、乗っていた床から離れたのを見て消す）また乗れば、
     * 同じ床でももう1回数える（「紅 → 白 → 紅」のような歌のため）。
     */
    public static void step(ServerPlayer player, BlockPos pos, int note) {
        ServerLevel level = player.serverLevel();
        Standing last = LAST_STEP.get(player.getUUID());
        if (last != null && last.player() == player && pos.equals(last.plate())) {
            return;
        }
        LAST_STEP.put(player.getUUID(), new Standing(player, pos.immutable(), note, level.getGameTime(), false));
        countedSteps++;
    }

    /** 乗ってから {@link #DWELL_TICKS} たった床を、踏んだと数える（音が鳴る）。 */
    private static void confirm(ServerPlayer player, BlockPos pos, int note) {
        ServerLevel level = player.serverLevel();
        confirmedSteps++;
        // ボスの音の床（4階のボス。近くにいれば、ボスが受け取る）
        for (jp.amemaru.lostconnection.enemy.boss.Cantare boss : level.getEntitiesOfClass(jp.amemaru.lostconnection.enemy.boss.Cantare.class,
                new AABB(pos).inflate(40.0))) {
            if (boss.onPlateStepped(level, player, pos, note)) {
                return;
            }
        }
        FloorDef def = FloorDefs.floorAt(level, player.position());
        if (def == null) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8F, pitch(note));
            return;
        }
        for (FloorFeatures.Song song : def.features().songs()) {
            int index = -1;
            for (int i = 0; i < song.plates().size(); i++) {
                if (def.world(song.plates().get(i)).equals(pos)) {
                    index = i;
                }
            }
            if (index < 0) {
                continue;
            }
            answer(player, level, def, song, index, false);
            return;
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8F, pitch(note));
    }

    /**
     * 踏んだ床で答える。{@code lenient} は、鳴り終える前から乗っていた人を、鳴り終えたときに数えるとき
     * （次の音の床なら数え、違えば外れにしない）。
     */
    private static void answer(ServerPlayer player, ServerLevel level, FloorDef def, FloorFeatures.Song song, int index, boolean lenient) {
        BlockPos plate = def.world(song.plates().get(index));
        playNote(level, plate, index);
        if (Data.get(player.server).sung(def.number(), song.id())) {
            return;
        }
        String key = def.number() + ":" + song.id();
        Performance perf = SONGS.get(key);
        long now = level.getGameTime();
        if (perf == null || now > perf.deadline || now < answerFrom(perf)) {
            return; // まだ鳴らしていない・鳴らしている途中・時間切れ：ただ鳴るだけ
        }
        if (perf.notes[perf.answered] == index) {
            perf.answered++;
            if (perf.answered >= perf.notes.length) {
                SONGS.remove(key);
                Data.get(player.server).sing(def.number(), song.id());
                if (song.door() != null) {
                    door(level, def, song.door(), true);
                }
                level.playSound(null, plate, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.2F);
                for (ServerPlayer p : playersOn(level, def)) {
                    if (p.blockPosition().distSqr(plate) < 40 * 40) {
                        p.displayClientMessage(Component.translatable("message.amemaru.song.done", song.name())
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                    }
                }
            } else {
                player.displayClientMessage(Component.translatable("message.amemaru.song.progress", perf.answered, perf.notes.length)
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return;
        }
        if (lenient) {
            return; // 鳴り終える前から乗っていた床が、次の音でなかった：数えないだけ（外れにしない）
        }
        // 外れ：はじめから（もう一度鈴を鳴らす）
        perf.answered = 0;
        perf.deadline = now;
        level.playSound(null, plate, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.BLOCKS, 1.6F, 0.6F);
        level.sendParticles(ParticleTypes.SMOKE, plate.getX() + 0.5, plate.getY() + 0.5, plate.getZ() + 0.5, 12, 0.4, 0.2, 0.4, 0.02);
        for (ServerPlayer p : playersOn(level, def)) {
            if (p.blockPosition().distSqr(plate) < 40 * 40) {
                p.displayClientMessage(Component.translatable("message.amemaru.song.wrong").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    // ================================================================ 物語の品

    /** 結晶騎士団の遺品を受け取る（人ごとに1回）。 */
    public static void takeRelic(ServerPlayer player, int floor, String relicId, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.Relic relic = def == null ? null : def.features().relic(relicId);
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (relic == null || data == null) {
            return;
        }
        if (data.getStory().has(relic.flag())) {
            player.displayClientMessage(Component.translatable("message.amemaru.relic.already").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        data.getStory().add(relic.flag());
        for (ResourceLocation id : relic.items()) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        ServerLevel level = player.serverLevel();
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 16, 0.3, 0.4, 0.3, 0.03);
        if (!relic.event().isEmpty()) {
            StoryEventManager.play(player, ResourceLocation.tryParse(relic.event()), false);
        }
        if (relic.quest() != null) {
            // 遺品をガルドに見せるクエスト（ストーリーイベントからはクエストを始められないので、ここで始める）
            jp.amemaru.lostconnection.guild.QuestService.accept(player, relic.quest(), null);
        }
        AmemaruMod.LOGGER.info("[amemaru] {} took the relic {} on floor {}", player.getGameProfile().getName(), relic.id(), floor);
    }

    // ================================================================ 毎 tick・朝

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower == null) {
            return;
        }
        long now = tower.getGameTime();
        tickSongs(tower, now);
        if (now % 40 == 0) {
            for (FloorDef def : FloorDefs.all()) {
                if (!def.features().murals().isEmpty() || !def.features().songs().isEmpty()) {
                    if (!playersOn(tower, def).isEmpty()) {
                        reconcile(tower, def);
                    }
                }
            }
        }
        // 乗っていた音の床から降りた人の印を消す（次に同じ床へ乗ったとき、また数える）。乗って DWELL_TICKS たった人は、数える
        List<Standing> confirm = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, Standing>> it = LAST_STEP.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Standing> entry = it.next();
            Standing standing = entry.getValue();
            if (standing.player().isRemoved() || standing.player().hasDisconnected() || !standingOn(standing.player(), standing.plate())) {
                it.remove();
            } else if (!standing.counted() && standing.player().serverLevel().getGameTime() - standing.since() >= DWELL_TICKS) {
                entry.setValue(standing.countedNow());
                confirm.add(standing);
            }
        }
        for (Standing standing : confirm) {
            confirm(standing.player(), standing.plate(), standing.note());
        }
    }

    /** 鳴らしている順番：1音ずつ床を光らせて鳴らす。 */
    private static void tickSongs(ServerLevel level, long now) {
        SONGS.entrySet().removeIf(entry -> {
            String[] parts = entry.getKey().split(":");
            FloorDef def = FloorDefs.byNumber(Integer.parseInt(parts[0]));
            FloorFeatures.Song song = def == null ? null : def.features().song(parts[1]);
            Performance perf = entry.getValue();
            if (song == null || now > perf.deadline + 200) {
                return true;
            }
            long t = now - perf.startedAt;
            if (t >= 0 && t % NOTE_TICKS == 0 && t / NOTE_TICKS < perf.notes.length) {
                int note = perf.notes[(int) (t / NOTE_TICKS)];
                playNote(level, def.world(song.plates().get(note)), note);
            }
            if (t == (long) perf.notes.length * NOTE_TICKS && perf.answered == 0) {
                // 鳴り終えた：鳴っている間から床に乗って待っていた人を、乗っている床で数える（11月25日）
                for (Standing standing : new ArrayList<>(LAST_STEP.values())) {
                    if (!standing.counted() || standing.player().level() != level || !standingOn(standing.player(), standing.plate())) {
                        continue;
                    }
                    for (int i = 0; i < song.plates().size(); i++) {
                        if (def.world(song.plates().get(i)).equals(standing.plate())) {
                            answer(standing.player(), level, def, song, i, true);
                        }
                    }
                }
            }
            if (t >= 0 && t % NOTE_TICKS == NOTE_TICKS - 2) {
                for (BlockPos p : song.plates()) {
                    setLit(level, def.world(p), false);
                }
            }
            return false;
        });
    }

    /**
     * 人のいる階：保存の状態に、見えるものを合わせる（扉・一枚の絵・朝に崩れた壁画のパネル）。読み込まれている所だけ。
     */
    public static void reconcile(ServerLevel level, FloorDef def) {
        Data data = Data.get(level.getServer());
        int[] c = count(level.getServer(), def);
        for (FloorFeatures.Mural mural : def.features().murals()) {
            if (mural.display()) {
                continue;
            }
            boolean solved = data.solved(def.number(), mural.id());
            if (mural.door() != null) {
                door(level, def, mural.door(), solved);
            }
            if (!solved && correct(level, def, mural)) {
                scramble(level, def, mural);
            } else if (solved && !correct(level, def, mural)) {
                straighten(level, def, mural);
            }
        }
        lightDisplays(level, def, c[1] > 0 && c[0] == c[1]);
        for (FloorFeatures.Song song : def.features().songs()) {
            if (song.door() != null) {
                door(level, def, song.door(), data.sung(def.number(), song.id()));
            }
        }
    }

    /** 壁画のパネルを、すべて正しい向きにそろえる（読み込まれている所だけ）。 */
    private static void straighten(ServerLevel level, FloorDef def, FloorFeatures.Mural mural) {
        for (FloorFeatures.Panel panel : mural.panels()) {
            BlockPos at = def.world(panel.pos());
            if (!level.isLoaded(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            if (state.hasProperty(TempleBlocks.TURN) && state.getValue(TempleBlocks.TURN) != panel.answer()) {
                level.setBlock(at, state.setValue(TempleBlocks.TURN, panel.answer()), 3);
            }
        }
    }

    /** 壁画を崩す（朝。少なくとも1枚は違う向きに）。 */
    private static void scramble(ServerLevel level, FloorDef def, FloorFeatures.Mural mural) {
        boolean wrong = false;
        List<FloorFeatures.Panel> panels = mural.panels();
        for (int i = 0; i < panels.size(); i++) {
            FloorFeatures.Panel panel = panels.get(i);
            BlockPos at = def.world(panel.pos());
            BlockState state = level.getBlockState(at);
            if (!state.hasProperty(TempleBlocks.TURN)) {
                continue;
            }
            int turn = level.random.nextInt(4);
            if (i == panels.size() - 1 && !wrong && turn == panel.answer()) {
                turn = (turn + 1 + level.random.nextInt(3)) % 4;
            }
            wrong |= turn != panel.answer();
            level.setBlock(at, state.setValue(TempleBlocks.TURN, turn), 3);
        }
    }

    /** 進行日が変わった：壁画は崩れ、扉は閉じる（人のいる所はすぐ、ほかは人が来たとき）。 */
    public static void onDayChanged(MinecraftServer server) {
        Data.get(server).clear();
        SONGS.clear();
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower != null) {
            for (FloorDef def : FloorDefs.all()) {
                if (!def.features().murals().isEmpty() || !def.features().songs().isEmpty()) {
                    reconcile(tower, def);
                }
            }
        }
        resyncStory(server);
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
