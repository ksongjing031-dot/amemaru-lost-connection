package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.s2c.MarketStallsS2C;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * フリーマーケットの、クライアントの側（Phase 6）。
 *
 * <ul>
 *   <li>露店の区画と市場監督の小屋の右クリックで、ブロックのふつうの動き（看板・樽など）を手元でもしない（画面はサーバーが開く）</li>
 *   <li>借りられている露店の屋根の上に店名と区画、台の上に出品した品（最大3つ）を、ゆっくり回して浮かべる。世界のブロックは変えない
 *       （街の直しの書き出しに入らない）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class MarketClientEvents {
    private static final double DRAW_DISTANCE = 48.0;

    private MarketClientEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getLevel().dimension() != Level.OVERWORLD) {
            return;
        }
        BlockPos pos = event.getPos();
        MarketStallsS2C stalls = ClientMarket.stalls();
        if (stalls.inBooth(pos) || ClientMarket.stallAt(pos) != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMarket.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) {
            return;
        }
        MarketStallsS2C stalls = ClientMarket.stalls();
        if (stalls.stalls().isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        float time = minecraft.level.getGameTime() + event.getPartialTick();
        Font font = minecraft.font;
        boolean drew = false;
        for (MarketStallsS2C.View view : stalls.stalls()) {
            if (view.owner() == null || !view.active() || view.namePos().distanceToSqr(cam) > DRAW_DISTANCE * DRAW_DISTANCE) {
                continue;
            }
            drew = true;
            for (int i = 0; i < Math.min(view.displayPos().size(), view.displayItems().size()); i++) {
                Vec3 at = view.displayPos().get(i);
                ItemStack stack = view.displayItems().get(i);
                pose.pushPose();
                pose.translate(at.x - cam.x, at.y - cam.y + Math.sin((time + i * 20) / 20.0) * 0.04, at.z - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees((time * 1.5F + i * 120F) % 360F));
                pose.scale(1.1F, 1.1F, 1.1F);
                int light = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(at));
                minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                        minecraft.level, i);
                pose.popPose();
            }
            Vec3 at = view.namePos();
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(camera.rotation());
            pose.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f matrix = pose.last().pose();
            int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
            String name = view.name().isBlank() ? view.ownerName() : view.name();
            font.drawInBatch(name, -font.width(name) / 2.0F, 0, 0xFFFFE08A, false, matrix, buffers, Font.DisplayMode.NORMAL, background,
                    LightTexture.FULL_BRIGHT);
            String sub = Component.translatable("world.amemaru.market.stall_sub", view.num(), view.listings()).getString();
            pose.scale(0.7F, 0.7F, 0.7F);
            Matrix4f small = pose.last().pose();
            font.drawInBatch(sub, -font.width(sub) / 2.0F, 15, 0xFFD8E4F2, false, small, buffers, Font.DisplayMode.NORMAL, background,
                    LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        if (drew) {
            buffers.endBatch();
        }
    }
}
