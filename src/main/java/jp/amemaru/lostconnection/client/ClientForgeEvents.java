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

    /** 新しいワールドを作る画面（開いたもの）。ワールドの種類を1度だけ選んでおく。 */
    private static final Set<net.minecraft.client.gui.screens.Screen> WORLD_TYPE_SET =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /**
     * 11月23日【補完】：新しいワールドを作るとき、ワールドの種類を、はじめから「コネリアの街」（このModの平地。開くと街が
     * 建っている）にしておく。画面を開いたときに1度だけ選ぶので、ほかの種類にも変えられる。
     */
    @SubscribeEvent
    public static void onScreenInit(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen screen)
                || !WORLD_TYPE_SET.add(screen)) {
            return;
        }
        net.minecraft.client.gui.screens.worldselection.WorldCreationUiState state = screen.getUiState();
        for (net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry entry : state.getNormalPresetList()) {
            if (entry.preset() != null && entry.preset().is(AmemaruMod.id("conelia_check"))) {
                state.setWorldType(entry);
                return;
            }
        }
    }

    /** 11月23日に決定：一人称で、籠手を両手にはめ、二刀の短剣を両手に持つ（{@code TwinWeapons}）。 */
    @SubscribeEvent
    public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
        jp.amemaru.lostconnection.client.render.TwinWeapons.onRenderHand(event);
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
     * Phase 6（11月22日に決定）：しゃがんでほかのプレイヤーを右クリックすると、ノットを渡す画面を、その人を選んだ形で開く
     * 【補完】。渡すかどうかは画面で確かめる。
     */
    @SubscribeEvent
    public static void onEntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide() || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player || !minecraft.player.isShiftKeyDown()
                || minecraft.screen != null) {
            return;
        }
        if (event.getTarget() instanceof net.minecraft.world.entity.player.Player other && other != minecraft.player) {
            minecraft.setScreen(new jp.amemaru.lostconnection.client.screen.TransferScreen(other.getUUID(),
                    other.getGameProfile().getName()));
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
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
        ClientConnection.clear();
        ClientFishing.clear();
    }
}
