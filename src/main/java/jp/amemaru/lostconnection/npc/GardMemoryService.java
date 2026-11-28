package jp.amemaru.lostconnection.npc;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.progression.ProgressionService;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 9日目：ガルドが、結晶騎士団の団員の名前を思い出せなくなる（11月28日。Phase 0-A 9章の9階の物語「ガルドが団員の名前を思い出せなく
 * なる」・12章のガルド「9日目 団員の名前を思い出せなくなる → 10日目 戻る」）。
 *
 * <p>こちらで決めたこと【補完】：忘れるのは、9階に誰かが初めて入ったとき（物語フラグ {@link #FORGOT}。リリアの 7階と同じ決め方）。
 * 忘れている間の会話は、データの {@code dialogues/gard/forgot*.json}・{@code captain.json}。10日目の撃破
 * （{@link NpcMemoryService#QUEEN_DOWN}）で戻る。ここでは、忘れたときに全員へ一度だけ知らせる。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class GardMemoryService {
    public static final ResourceLocation FORGOT = AmemaruMod.id("ch2/gard_forgot");
    /** 「ガルドが忘れた」知らせを出したか（サーバー全体。一度だけ） */
    private static final ResourceLocation ANNOUNCED = AmemaruMod.id("ch2/gard_forgot_announced");

    private GardMemoryService() {
    }

    /** いま、ガルドが団員の名前を忘れているか。 */
    public static boolean forgets(MinecraftServer server) {
        StoryState state = StoryState.get(server);
        return state.has(FORGOT) && !state.has(NpcMemoryService.QUEEN_DOWN);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 40 != 29) {
            return;
        }
        MinecraftServer server = event.getServer();
        StoryState state = StoryState.get(server);
        if (!forgets(server) || state.has(ANNOUNCED)) {
            return;
        }
        state.add(ANNOUNCED);
        ProgressionService.broadcast(server, Component.translatable("message.amemaru.gard.forgot"), AnnouncementS2C.Kind.STORY);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.translatable("message.amemaru.gard.forgot_detail").withStyle(ChatFormatting.GRAY));
        }
        AmemaruMod.LOGGER.info("[amemaru] Gard forgot the knights' names (gard_forgot)");
    }
}
