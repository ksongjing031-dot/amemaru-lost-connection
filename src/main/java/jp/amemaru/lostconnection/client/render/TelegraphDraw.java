package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 地面の予兆（当たる所）を描く。敵の体のまわりの予兆（{@link AmemaruMobRenderer}）と、地面の印（{@link GroundMarkerRenderer}）で使う。
 *
 * <p>形はどれも同じ見せ方：うすい赤で当たる所を塗り、ふちを濃くし、打つときに向かって明るい線が進む（いつ来るかが分かる）。
 * 座標は描く所の真ん中（足もと）から。前は +z。</p>
 */
public final class TelegraphDraw {
    private static final ResourceLocation TEXTURE = AmemaruMod.id("textures/fx/telegraph.png");
    private static final int COLOR = 0xFF4A3A;
    private static final int SEGMENTS = 32;

    private TelegraphDraw() {
    }

    public static VertexConsumer consumer(MultiBufferSource buffer) {
        return buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
    }

    /** 濃さ（0〜255）。打つときが近いほど濃く、少し脈打つ。 */
    public static int alpha(float progress, float age) {
        float pulse = 0.5F + 0.5F * Mth.sin(age * 0.6F);
        return Math.round((0.25F + 0.55F * progress + 0.15F * pulse) * 255.0F);
    }

    /** まっすぐな帯（幅 {@code width}・長さ {@code length}。前へ）。明るい線が手前から奥へ進む。 */
    public static void band(VertexConsumer consumer, PoseStack poseStack, float width, float length, float progress, int alpha) {
        float half = width / 2.0F;
        quad(consumer, poseStack, -half, 0.0F, half, length, alpha);
        float sweep = length * progress;
        quad(consumer, poseStack, -half, Math.max(0.0F, sweep - 0.4F), half, sweep, Math.min(255, alpha + 60));
        // ふち
        quad(consumer, poseStack, -half, 0.0F, -half + 0.12F, length, Math.min(255, alpha + 40));
        quad(consumer, poseStack, half - 0.12F, 0.0F, half, length, Math.min(255, alpha + 40));
    }

    /** 円（半径 {@code radius}）。明るい輪が真ん中から広がる。 */
    public static void circle(VertexConsumer consumer, PoseStack poseStack, float radius, float progress, int alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / SEGMENTS);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
            float inner = radius * progress;
            wedge(consumer, poseStack, a0, a1, Math.max(0.0F, inner - 0.3F), inner, Math.min(255, alpha + 60));
            wedge(consumer, poseStack, a0, a1, radius - 0.2F, radius, alpha);
            wedge(consumer, poseStack, a0, a1, 0.0F, radius - 0.2F, alpha / 3);
        }
    }

    /** 前の扇形（開き {@code angleDegrees}・半径 {@code radius}）。明るい弧が手前から広がる。 */
    public static void cone(VertexConsumer consumer, PoseStack poseStack, float angleDegrees, float radius, float progress, int alpha) {
        float half = (float) Math.toRadians(Mth.clamp(angleDegrees, 5.0F, 360.0F) / 2.0F);
        // 前（+z）は wedge の角度で 90度
        float start = (float) (Math.PI / 2.0) - half;
        int segments = Math.max(4, Math.round(SEGMENTS * (half * 2.0F) / (float) (Math.PI * 2.0)) + 2);
        float step = half * 2.0F / segments;
        float inner = radius * progress;
        for (int i = 0; i < segments; i++) {
            float a0 = start + step * i;
            float a1 = a0 + step;
            wedge(consumer, poseStack, a0, a1, Math.max(0.0F, inner - 0.3F), inner, Math.min(255, alpha + 60));
            wedge(consumer, poseStack, a0, a1, radius - 0.2F, radius, alpha);
            wedge(consumer, poseStack, a0, a1, 0.0F, radius - 0.2F, alpha / 3);
        }
        // 両がわのふち（細い扇で線にする）
        float edge = 0.12F / Math.max(1.0F, radius);
        wedge(consumer, poseStack, start, start + edge, 0.0F, radius, Math.min(255, alpha + 40));
        wedge(consumer, poseStack, start + half * 2.0F - edge, start + half * 2.0F, 0.0F, radius, Math.min(255, alpha + 40));
    }

    /** 地面に平らな四角（x0〜x1、奥行き z0〜z1）。 */
    public static void quad(VertexConsumer consumer, PoseStack poseStack, float x0, float z0, float x1, float z1, int alpha) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        vertex(consumer, matrix, normal, x0, z0, 0.0F, 0.0F, alpha);
        vertex(consumer, matrix, normal, x0, z1, 0.0F, 1.0F, alpha);
        vertex(consumer, matrix, normal, x1, z1, 1.0F, 1.0F, alpha);
        vertex(consumer, matrix, normal, x1, z0, 1.0F, 0.0F, alpha);
    }

    /** 地面に扇形の帯（半径 r0〜r1、角度 a0〜a1。角度 0 が +x、90度が +z）。 */
    public static void wedge(VertexConsumer consumer, PoseStack poseStack, float a0, float a1, float r0, float r1, int alpha) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        vertex(consumer, matrix, normal, Mth.cos(a0) * r0, Mth.sin(a0) * r0, 0.0F, 0.0F, alpha);
        vertex(consumer, matrix, normal, Mth.cos(a0) * r1, Mth.sin(a0) * r1, 0.0F, 1.0F, alpha);
        vertex(consumer, matrix, normal, Mth.cos(a1) * r1, Mth.sin(a1) * r1, 1.0F, 1.0F, alpha);
        vertex(consumer, matrix, normal, Mth.cos(a1) * r0, Mth.sin(a1) * r0, 1.0F, 0.0F, alpha);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float z, float u, float v, int alpha) {
        consumer.vertex(matrix, x, 0.0F, z)
                .color((COLOR >> 16) & 0xFF, (COLOR >> 8) & 0xFF, COLOR & 0xFF, Mth.clamp(alpha, 0, 255))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
