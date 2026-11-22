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
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * スキルの弾の見た目【仮素材】。
 *
 * <ul>
 *   <li>灯火・気弾・彗星：いつもこちらを向く光の玉（{@code textures/entity/lamp_bolt.png}、16×16）。気弾は青く、彗星は大きく金色に染める。</li>
 *   <li>矢・針（Phase 6 その2）：飛ぶ向きにのびた光の筋（{@code arrow_bolt.png}・{@code needle_bolt.png}、32×8。右が先）。
 *       バニラの矢と同じく、筋を軸のまわりに4面描く。</li>
 * </ul>
 */
public class LampBoltRenderer extends EntityRenderer<LampBolt> {
    private static final ResourceLocation TEXTURE = AmemaruMod.id("textures/entity/lamp_bolt.png");
    private static final ResourceLocation ARROW_TEXTURE = AmemaruMod.id("textures/entity/arrow_bolt.png");
    private static final ResourceLocation NEEDLE_TEXTURE = AmemaruMod.id("textures/entity/needle_bolt.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE);
    private static final RenderType ARROW_TYPE = RenderType.entityTranslucentEmissive(ARROW_TEXTURE);
    private static final RenderType NEEDLE_TYPE = RenderType.entityTranslucentEmissive(NEEDLE_TEXTURE);

    public LampBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(LampBolt entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(LampBolt entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        LampBolt.Kind kind = entity.kind();
        switch (kind) {
            case ARROW -> renderShaft(entity, partialTicks, poseStack, buffer.getBuffer(ARROW_TYPE), packedLight, 1.25F, 0.3F);
            case NEEDLE -> renderShaft(entity, partialTicks, poseStack, buffer.getBuffer(NEEDLE_TYPE), packedLight, 0.8F, 0.18F);
            case KI -> renderOrb(poseStack, buffer, packedLight, 0.85F, 150, 220, 255);
            case COMET -> renderOrb(poseStack, buffer, packedLight, 1.5F, 255, 222, 150);
            default -> renderOrb(poseStack, buffer, packedLight, 0.7F, 255, 255, 255);
        }
        super.render(entity, yaw, partialTicks, poseStack, buffer, packedLight);
    }

    /** こちらを向く光の玉。 */
    private void renderOrb(PoseStack poseStack, MultiBufferSource buffer, int packedLight, float size, int r, int g, int b) {
        poseStack.pushPose();
        poseStack.scale(size, size, size);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);
        vertex(consumer, matrix, normal, packedLight, -0.5F, -0.5F, 0.0F, 0, 1, r, g, b);
        vertex(consumer, matrix, normal, packedLight, 0.5F, -0.5F, 0.0F, 1, 1, r, g, b);
        vertex(consumer, matrix, normal, packedLight, 0.5F, 0.5F, 0.0F, 1, 0, r, g, b);
        vertex(consumer, matrix, normal, packedLight, -0.5F, 0.5F, 0.0F, 0, 0, r, g, b);
        poseStack.popPose();
    }

    /** 飛ぶ向きにのびた筋（長さ length・幅 width ブロック）。 */
    private static void renderShaft(LampBolt entity, float partialTicks, PoseStack poseStack, VertexConsumer consumer, int packedLight,
                                    float length, float width) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
        float half = width / 2.0F;
        float back = -length * 0.75F;
        float front = length * 0.25F;
        for (int i = 0; i < 4; i++) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix = pose.pose();
            Matrix3f normal = pose.normal();
            vertex(consumer, matrix, normal, packedLight, back, -half, 0.0F, 0, 1, 255, 255, 255);
            vertex(consumer, matrix, normal, packedLight, front, -half, 0.0F, 1, 1, 255, 255, 255);
            vertex(consumer, matrix, normal, packedLight, front, half, 0.0F, 1, 0, 255, 255, 255);
            vertex(consumer, matrix, normal, packedLight, back, half, 0.0F, 0, 0, 255, 255, 255);
        }
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int light, float x, float y, float z, int u, int v,
                               int r, int g, int b) {
        consumer.vertex(matrix, x, y, z)
                .color(r, g, b, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(LampBolt entity) {
        return switch (entity.kind()) {
            case ARROW -> ARROW_TEXTURE;
            case NEEDLE -> NEEDLE_TEXTURE;
            default -> TEXTURE;
        };
    }
}
