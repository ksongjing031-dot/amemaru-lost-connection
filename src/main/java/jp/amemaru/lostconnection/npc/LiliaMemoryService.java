package jp.amemaru.lostconnection.npc;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.progression.ProgressionService;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 7日目：リリアがプレイヤーを忘れる（11月25日。Phase 0-A 13章「NPCの記憶喪失」の表）。
 *
 * <p>確定：プレイヤーの名前と顔を忘れる（「はじめまして……ですよね？」）。仕事はできる。「ただいま」と声をかけた人数
 * （サーバー全体、1人1日1回）がアクティブ人数の25%で名前をうっすら、50%で顔、75%でさらに思い出す。10日目の撃破で完全に戻る。</p>
 * <p>こちらで決めたこと【補完】：忘れるのは、7階に誰かが初めて入ったとき（物語フラグ {@link #FORGOT}。0-A の「lilia_forgot：7階への
 * 初到達」）。「アクティブ人数」は、忘れてからログインした人の数。声は累計（同じ人も、日が変われば数える）。思い出した段階は、
 * サーバー全体の物語フラグ（{@link #NAME}・{@link #FACE}・{@link #MORE}）で、会話の出し分けに使う。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class LiliaMemoryService {
    public static final ResourceLocation FORGOT = AmemaruMod.id("ch2/lilia_forgot");
    public static final ResourceLocation NAME = AmemaruMod.id("ch2/lilia_name");
    public static final ResourceLocation FACE = AmemaruMod.id("ch2/lilia_face");
    public static final ResourceLocation MORE = AmemaruMod.id("ch2/lilia_more");
    /** 「リリアが忘れた」知らせを出したか（サーバー全体。一度だけ） */
    private static final ResourceLocation ANNOUNCED = AmemaruMod.id("ch2/lilia_forgot_announced");

    private LiliaMemoryService() {
    }

    public static final class Data extends SavedData {
        private static final String NAME_KEY = "amemaru_lilia_memory";
        private final Set<String> greeted = new LinkedHashSet<>();
        private final Set<String> seen = new LinkedHashSet<>();
        private int total;

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            for (Tag t : tag.getList("greeted", Tag.TAG_STRING)) {
                data.greeted.add(t.getAsString());
            }
            for (Tag t : tag.getList("seen", Tag.TAG_STRING)) {
                data.seen.add(t.getAsString());
            }
            data.total = tag.getInt("total");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag g = new ListTag();
            greeted.forEach(k -> g.add(StringTag.valueOf(k)));
            tag.put("greeted", g);
            ListTag s = new ListTag();
            seen.forEach(k -> s.add(StringTag.valueOf(k)));
            tag.put("seen", s);
            tag.putInt("total", total);
            return tag;
        }

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME_KEY);
        }

        public int total() {
            return total;
        }

        public int active() {
            return Math.max(1, seen.size());
        }

        /** 忘れてからログインした人（アクティブ人数）に数える（自動テストからも）。 */
        public void see(String id) {
            if (seen.add(id)) {
                setDirty();
            }
        }

        boolean greet(String key) {
            if (!greeted.add(key)) {
                return false;
            }
            total++;
            setDirty();
            return true;
        }

        /** 自動テスト・管理者：はじめから。 */
        public void clear() {
            greeted.clear();
            seen.clear();
            total = 0;
            setDirty();
        }
    }

    /** リリアがプレイヤーを忘れている間か（7階に人が来てから、10階の初撃破まで）。 */
    public static boolean forgets(MinecraftServer server) {
        StoryState state = StoryState.get(server);
        return state.has(FORGOT) && !state.has(NpcMemoryService.QUEEN_DOWN);
    }

    /** 「ただいま」の声の累計が、アクティブ人数の何%か。 */
    public static int percent(MinecraftServer server) {
        Data data = Data.get(server);
        return data.total() * 100 / data.active();
    }

    /** 思い出した段階（0：忘れている・1：名前をうっすら・2：顔・3：さらに）。 */
    public static int stage(MinecraftServer server) {
        StoryState state = StoryState.get(server);
        return state.has(MORE) ? 3 : state.has(FACE) ? 2 : state.has(NAME) ? 1 : 0;
    }

    /** リリアに「ただいま」と声をかけた（会話の選択肢から）。数えたら true（1人1日1回）。 */
    public static boolean greet(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!forgets(server)) {
            return false;
        }
        Data data = Data.get(server);
        data.see(player.getUUID().toString());
        int day = ProgressionData.currentDay(server);
        if (!data.greet(player.getUUID() + ":" + day)) {
            player.sendSystemMessage(Component.translatable("message.amemaru.lilia.greeted_today").withStyle(ChatFormatting.GRAY));
            return false;
        }
        int before = stage(server);
        int percent = percent(server);
        StoryState state = StoryState.get(server);
        if (percent >= 25) {
            state.add(NAME);
        }
        if (percent >= 50) {
            state.add(FACE);
        }
        if (percent >= 75) {
            state.add(MORE);
        }
        int after = stage(server);
        player.sendSystemMessage(Component.translatable("message.amemaru.lilia.greeted", Math.min(100, percent)).withStyle(ChatFormatting.AQUA));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL,
                1.0F, 1.1F);
        if (after > before) {
            ProgressionService.broadcast(server, Component.translatable("message.amemaru.lilia.stage" + after), AnnouncementS2C.Kind.STORY);
        }
        AmemaruMod.LOGGER.info("[amemaru] {} said tadaima to Lilia (total {}, active {}, {}%, stage {})", player.getGameProfile().getName(),
                data.total(), data.active(), percent, after);
        return true;
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && forgets(player.server)) {
            Data.get(player.server).see(player.getUUID().toString());
        }
    }

    /** 忘れたとき（7階に誰かが初めて入ったとき）、全員に一度だけ知らせ、いまログインしている人を「アクティブ」に数える。 */
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || event.getServer().getTickCount() % 40 != 23) {
            return;
        }
        MinecraftServer server = event.getServer();
        StoryState state = StoryState.get(server);
        if (!forgets(server) || state.has(ANNOUNCED)) {
            return;
        }
        state.add(ANNOUNCED);
        Data data = Data.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            data.see(player.getUUID().toString());
        }
        ProgressionService.broadcast(server, Component.translatable("message.amemaru.lilia.forgot"), AnnouncementS2C.Kind.STORY);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.translatable("message.amemaru.lilia.forgot_detail").withStyle(ChatFormatting.GRAY));
        }
        AmemaruMod.LOGGER.info("[amemaru] Lilia forgot the players (lilia_forgot)");
    }
}
