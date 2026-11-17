package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.entity.LampBolt;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 灯火弾の見た目：いつもこちらを向く光の玉（{@code textures/entity/lamp_bolt.png}、16×16）【仮素材】。
 */
public class LampBoltRenderer extends EntityRenderer<LampBolt> {
    private static final ResourceLocation TEXTURE = AmemaruMod.id("textures/entity/lamp_bolt.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    public LampBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(LampBolt entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(LampBolt entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(0.7F, 0.7F, 0.7F);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);
        vertex(consumer, matrix, normal, packedLight, 0.0F, 0, 0, 1);
        vertex(consumer, matrix, normal, packedLight, 1.0F, 0, 1, 1);
        vertex(consumer, matrix, normal, packedLight, 1.0F, 1, 1, 0);
        vertex(consumer, matrix, normal, packedLight, 0.0F, 1, 0, 0);
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int light, float x, int y, int u, int v) {
        consumer.vertex(matrix, x - 0.5F, y - 0.5F, 0.0F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(LampBolt entity) {
        return TEXTURE;
    }
}
