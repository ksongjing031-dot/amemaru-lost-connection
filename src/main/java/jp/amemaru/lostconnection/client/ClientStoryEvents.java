package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.SceneStateC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.player.Input;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 物語の演出のクライアントの処理（Forgeのイベントバス）。
 *
 * <ul>
 *   <li>オープニングの準備：サーバーが「準備を待っている」と言ったら、ワールドを読み込み、まわりの区画が届いて描けるようになってから
 *       「準備ができた」を返す（ラグで入るのが遅れても、見えないうちに演出が進まない）。15秒たったら、そのまま返す</li>
 *   <li>演出の間：視点を固定し、動けないようにし、HUDを隠す</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class ClientStoryEvents {
    private static final ResourceLocation STORY_OVERLAY = AmemaruMod.id("story");
    /** まわりの区画が届いてから、描けるようになるまで待つ時間。 */
    private static final int SETTLE_TICKS = 30;
    private static final int READY_TIMEOUT_TICKS = 20 * 15;

    private static boolean readySent;
    private static int pendingTicks;
    private static int settledTicks;

    private ClientStoryEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ScenePlayer.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientStory.openingPending()) {
            readySent = false;
            pendingTicks = 0;
            settledTicks = 0;
            return;
        }
        if (readySent || minecraft.player == null || minecraft.level == null) {
            return;
        }
        pendingTicks++;
        boolean loading = minecraft.screen instanceof ReceivingLevelScreen;
        boolean chunks = !loading && minecraft.player.tickCount > 20 && chunksAround(minecraft);
        settledTicks = chunks ? settledTicks + 1 : 0;
        if (settledTicks >= SETTLE_TICKS || pendingTicks >= READY_TIMEOUT_TICKS) {
            readySent = true;
            ModNetwork.sendToServer(new SceneStateC2S(SceneStateC2S.State.READY, 0));
        }
    }

    /** まわり（半径2区画）の区画が届いているか。 */
    private static boolean chunksAround(Minecraft minecraft) {
        int cx = minecraft.player.getBlockX() >> 4;
        int cz = minecraft.player.getBlockZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!minecraft.level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ScenePlayer.isActive()) {
            return;
        }
        Vec3 eye = event.getCamera().getPosition();
        float[] angles = ScenePlayer.cameraAngles(eye, (float) event.getPartialTick(), event.getYaw(), event.getPitch());
        if (angles != null) {
            event.setYaw(angles[0]);
            event.setPitch(angles[1]);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ScenePlayer.isActive() && !ClientStory.openingPending()) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!ScenePlayer.hideHud() && !ClientStory.openingPending()) {
            return;
        }
        if (!STORY_OVERLAY.equals(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ScenePlayer.cleanup();
        ClientStory.clear();
        readySent = false;
        pendingTicks = 0;
        settledTicks = 0;
    }
}
