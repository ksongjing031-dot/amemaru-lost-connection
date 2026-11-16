package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアントの登録（Modのイベントバス）：キーとHUD。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_STATUS);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        // ホットバーのすぐ後に描く（ハートや空気の泡より先に描き、高さの計算を渡すため）
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "status", AmemaruHud.STATUS);
    }
}
