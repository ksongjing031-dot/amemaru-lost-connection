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
        poseStack.translate(0.0, 0.07, 0.0);
        TelegraphDraw.circle(TelegraphDraw.consumer(buffer), poseStack, marker.radius(), progress, TelegraphDraw.alpha(progress, age));
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(GroundMarker marker) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
