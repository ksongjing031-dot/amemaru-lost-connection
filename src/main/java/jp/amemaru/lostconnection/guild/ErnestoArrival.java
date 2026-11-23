package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionService;
import jp.amemaru.lostconnection.story.StoryState;
import jp.amemaru.lostconnection.tower.FloorDefs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * エルネストの登場（Phase 0-A 11章の3日目「3階の記録を持ち帰るとエルネストが登場し、記録帳がもらえる」。Phase 8 その2）。
 *
 * <ul>
 *   <li>3階の古代記録「水路管理局の掲示」（{@link #RECORD}）を見つけた人が、塔から街へ戻ると、サーバー全体の物語フラグ
 *       {@link #ARRIVED} が立つ。エルネストの {@code appears} はこのフラグ（{@code npcs/ernesto.json}）。最初の会話で記録帳を渡す</li>
 *   <li>3階ができる前の世界（3日目からギルドの資料室にいた）で、もうエルネストに会っていた人がいれば、そのまま居てもらう</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class ErnestoArrival {
    /** エルネストが街に来た（サーバー全体）。 */
    public static final ResourceLocation ARRIVED = AmemaruMod.id("story/ernesto_arrived");
    /** 3階の古代記録。 */
    public static final ResourceLocation RECORD = AmemaruMod.id("f03_waterworks");
    private static final ResourceLocation MET = AmemaruMod.id("story/ernesto_met");

    private ErnestoArrival() {
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getFrom().equals(FloorDefs.TOWER)
                && event.getTo().equals(Level.OVERWORLD)) {
            check(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data != null && data.getStory().has(MET)) {
            // 前の決まり（3日目から資料室にいた）で、もう会っていた
            arrive(player.server, null);
            return;
        }
        if (player.level().dimension().equals(Level.OVERWORLD)) {
            check(player);
        }
    }

    /** 3階の記録を持って街にいるか確かめ、そうならエルネストを呼ぶ。呼んだら true。 */
    public static boolean check(ServerPlayer player) {
        if (StoryState.get(player.server).has(ARRIVED)) {
            return false;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || data.getGuild().record(RECORD) == null) {
            return false;
        }
        return arrive(player.server, player);
    }

    /** エルネストが街に来る（もう来ていれば false）。{@code by} は記録を持ち帰った人。 */
    public static boolean arrive(MinecraftServer server, @Nullable ServerPlayer by) {
        if (!StoryState.get(server).add(ARRIVED)) {
            return false;
        }
        GuildEvents.updateAppearances(server);
        if (by != null) {
            ProgressionService.broadcast(server, Component.translatable("message.amemaru.ernesto.arrived_by", by.getDisplayName()),
                    AnnouncementS2C.Kind.STORY);
            by.sendSystemMessage(Component.translatable("message.amemaru.ernesto.bring").withStyle(ChatFormatting.AQUA));
        }
        AmemaruMod.LOGGER.info("[amemaru] Ernesto arrived in Conelia{}", by == null ? " (already met)" : " (record brought by "
                + by.getGameProfile().getName() + ")");
        return true;
    }
}
