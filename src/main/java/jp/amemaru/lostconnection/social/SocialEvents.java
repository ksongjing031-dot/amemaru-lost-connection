package jp.amemaru.lostconnection.social;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 依頼台の出入り（Phase 6 その4）：入ったときに、いない間に戻ってきた品・ノット・手数料を受け取る。日が変わったら、
 * 誰も受けないまま日がたった依頼を取り下げる。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class SocialEvents {
    private static int lastDay = -1;

    private SocialEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            CommissionBoard.claimPayout(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CommissionBoard.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 != 17) {
            return;
        }
        int day = ProgressionData.currentDay(server);
        if (lastDay < 0) {
            lastDay = day;
            CommissionBoard.onDay(server, day);
        } else if (day != lastDay) {
            lastDay = day;
            CommissionBoard.onDay(server, day);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastDay = -1;
        CommissionBoard.clearAll();
    }
}
