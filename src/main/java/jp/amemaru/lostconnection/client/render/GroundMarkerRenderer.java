package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.amemaru.lostconnection.entity.GroundMarker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** 地面の印（{@link GroundMarker}）を、敵の予兆と同じ見せ方の円で描く。 */
public class GroundMarkerRenderer extends EntityRenderer<GroundMarker> {
    public GroundMarkerRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(GroundMarker marker, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float age = marker.tickCount + partialTick;
        float progress = marker.progress(partialTick);
        poseStack.pushPose();
        poseStack.translate(0.0, 0.07 + TelegraphDraw.waterLift(marker), 0.0);
        if (marker.kind() == GroundMarker.KIND_SHARE) {
            // 青の印：みんなで集まって受ける所。内向きの矢じりで「集まれ」を見せる（11月24日）
            int alpha = Math.min(255, TelegraphDraw.alpha(progress, age) + 30);
            TelegraphDraw.withColor(TelegraphDraw.SHARE_COLOR, () -> {
                TelegraphDraw.circle(TelegraphDraw.consumer(buffer), poseStack, marker.radius(), progress, alpha);
                TelegraphDraw.gatherArrows(TelegraphDraw.consumer(buffer), poseStack, marker.radius(), age, alpha);
            });
        } else {
            TelegraphDraw.circle(TelegraphDraw.consumer(buffer), poseStack, marker.radius(), progress, TelegraphDraw.alpha(progress, age));
        }
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(GroundMarker marker) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
