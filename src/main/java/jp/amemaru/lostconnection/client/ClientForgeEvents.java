package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.screen.StatusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * クライアントの毎回の処理（Forgeのイベントバス）：キーの入力、バニラのHUDを隠す、ログアウト時の後片付け。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    /** このModのHUDに置きかえるバニラの表示。 */
    private static final Set<ResourceLocation> HIDDEN_VANILLA = Set.of(
            VanillaGuiOverlay.PLAYER_HEALTH.id(),
            VanillaGuiOverlay.ARMOR_LEVEL.id(),
            VanillaGuiOverlay.FOOD_LEVEL.id(),
            VanillaGuiOverlay.EXPERIENCE_BAR.id());

    private ClientForgeEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (KeyBindings.OPEN_STATUS.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new StatusScreen());
            }
        }
    }

    /**
     * このModのサーバーにつながっている間は、バニラのハート・防御・満腹度・経験値・ホットバーを隠す
     * （AmemaruHud が同じ場所に描く）。観戦モードのホットバーは、バニラのものをそのまま使う。
     */
    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!ClientPlayerState.get().hasData()) {
            return;
        }
        ResourceLocation id = event.getOverlay().id();
        if (HIDDEN_VANILLA.contains(id)) {
            event.setCanceled(true);
        } else if (id.equals(VanillaGuiOverlay.HOTBAR.id())) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && !minecraft.player.isSpectator()) {
                event.setCanceled(true);
            }
        }
    }

    /** チャットを、HUD（結晶の冠）に重ならない高さまで上げる。 */
    @SubscribeEvent
    public static void onCustomizeChat(CustomizeGuiOverlayEvent.Chat event) {
        if (!ClientPlayerState.get().hasData()) {
            return;
        }
        int reserved = Math.max(AmemaruHud.reservedHeight(), SkillHud.reservedHeight());
        int limit = event.getWindow().getGuiScaledHeight() - reserved;
        if (event.getPosY() > limit) {
            event.setPosY(limit);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPlayerState.get().reset();
        ClientAdventure.reset();
        ClientDefinitions.get().reset();
        AmemaruHud.resetAnimations();
        SkillHud.resetAnimations();
        SkillFx.reset();
        SkillInput.reset();
        CombatTextRenderer.reset();
        ClientDown.reset();
        ClientParty.reset();
    }
}
