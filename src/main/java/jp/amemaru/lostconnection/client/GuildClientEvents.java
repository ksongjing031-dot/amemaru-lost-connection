package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ギルドの、クライアントのイベント（Phase 6 その3）：頭の上の名前の前に、表示している称号を付ける
 * （限定はゆっくり光り、ユニークは虹色に変わり続けるので、描くたびに作る）。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class GuildClientEvents {
    private GuildClientEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ClientGuild.Shown shown = ClientGuild.shown(player.getUUID());
        if (shown == null) {
            return;
        }
        event.setContent(Component.empty()
                .append(TitleText.of(shown.name(), shown.color(), shown.kind(), shown.rainbow(), Util.getMillis(), true))
                .append(event.getContent()));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientGuild.clear();
    }
}
