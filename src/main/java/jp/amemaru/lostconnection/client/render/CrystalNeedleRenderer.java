package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.entity.CrystalNeedle;
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
 * 結晶蜂の針の見た目：進む向きに伸びた、光る細い結晶（十字に重ねた2枚の板。{@code textures/entity/crystal_needle.png}、16×16）【仮素材】。
 */
public class CrystalNeedleRenderer extends EntityRenderer<CrystalNeedle> {
    private static final ResourceLocation TEXTURE = AmemaruMod.id("textures/entity/crystal_needle.png");
    /** 11月28日：9階の書架霊の頁（{@code textures/entity/page_shard.png}、16×16）。 */
    private static final ResourceLocation PAGE = AmemaruMod.id("textures/entity/page_shard.png");

    public CrystalNeedleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(CrystalNeedle entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(CrystalNeedle entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.style() == CrystalNeedle.STYLE_PAGE) {
            renderPage(entity, partialTicks, poseStack, buffer);
            super.render(entity, yaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        for (int i = 0; i < 2; i++) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix = pose.pose();
            Matrix3f normal = pose.normal();
            vertex(consumer, matrix, normal, -0.5F, -0.08F, 0.0F, 0.0F);
            vertex(consumer, matrix, normal, 0.5F, -0.08F, 1.0F, 0.0F);
            vertex(consumer, matrix, normal, 0.5F, 0.08F, 1.0F, 1.0F);
            vertex(consumer, matrix, normal, -0.5F, 0.08F, 0.0F, 1.0F);
            vertex(consumer, matrix, normal, -0.5F, 0.08F, 0.0F, 1.0F);
            vertex(consumer, matrix, normal, 0.5F, 0.08F, 1.0F, 1.0F);
            vertex(consumer, matrix, normal, 0.5F, -0.08F, 1.0F, 0.0F);
            vertex(consumer, matrix, normal, -0.5F, -0.08F, 0.0F, 0.0F);
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, packedLight);
    }

    /** 頁：くるくる回りながら飛ぶ、光る紙片（表と裏）。 */
    private void renderPage(CrystalNeedle entity, float partialTicks, PoseStack poseStack, MultiBufferSource buffer) {
        poseStack.pushPose();
        float age = entity.tickCount + partialTicks;
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 31.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.6F) * 25.0F));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(PAGE));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        float h = 0.28F;
        vertex(consumer, matrix, normal, -h, -h, 0.0F, 1.0F);
        vertex(consumer, matrix, normal, h, -h, 1.0F, 1.0F);
        vertex(consumer, matrix, normal, h, h, 1.0F, 0.0F);
        vertex(consumer, matrix, normal, -h, h, 0.0F, 0.0F);
        vertex(consumer, matrix, normal, -h, h, 0.0F, 0.0F);
        vertex(consumer, matrix, normal, h, h, 1.0F, 0.0F);
        vertex(consumer, matrix, normal, h, -h, 1.0F, 1.0F);
        vertex(consumer, matrix, normal, -h, -h, 0.0F, 1.0F);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float y, float u, float v) {
        consumer.vertex(matrix, x, y, 0.0F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(CrystalNeedle entity) {
        return entity.style() == CrystalNeedle.STYLE_PAGE ? PAGE : TEXTURE;
    }
}
