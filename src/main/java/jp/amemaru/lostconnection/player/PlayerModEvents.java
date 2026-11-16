package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Capabilityの登録（Modのイベントバス）。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PlayerModEvents {
    private PlayerModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerData.class);
    }
}
