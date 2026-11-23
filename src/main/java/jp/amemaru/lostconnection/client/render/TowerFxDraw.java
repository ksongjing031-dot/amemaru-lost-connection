package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** 塔の仕掛け（石柱・封印）の光を描く小さな道具（クライアント専用）。 */
final class TowerFxDraw {
    private TowerFxDraw() {
    }

    /** 四角い面（p0..p3 の順。どの面も両側から見える描き方で使う）。 */
    static void quad(PoseStack poseStack, VertexConsumer consumer, float[] p0, float[] p1, float[] p2, float[] p3, int argb, float u0, float v0,
                     float u1, float v1) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int light = LightTexture.FULL_BRIGHT;
        consumer.vertex(pose, p0[0], p0[1], p0[2]).color(r, g, b, a).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(pose, p1[0], p1[1], p1[2]).color(r, g, b, a).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(pose, p2[0], p2[1], p2[2]).color(r, g, b, a).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(pose, p3[0], p3[1], p3[2]).color(r, g, b, a).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** 真ん中が原点の、辺の長さ {@code s} の立方体。 */
    static void cube(PoseStack poseStack, VertexConsumer consumer, float s, int argb) {
        float h = s / 2.0F;
        float[][] v = {{-h, -h, -h}, {h, -h, -h}, {h, h, -h}, {-h, h, -h}, {-h, -h, h}, {h, -h, h}, {h, h, h}, {-h, h, h}};
        int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
        for (int[] f : faces) {
            quad(poseStack, consumer, v[f[0]], v[f[1]], v[f[2]], v[f[3]], argb, 0.0F, 0.0F, 1.0F, 1.0F);
        }
    }

    /** 縦の光の柱（4枚の面を十字に組む）。 */
    static void beam(PoseStack poseStack, VertexConsumer consumer, float radius, float height, int argb, float scroll) {
        for (int i = 0; i < 2; i++) {
            float ax = i == 0 ? radius : 0.0F;
            float az = i == 0 ? 0.0F : radius;
            quad(poseStack, consumer, new float[]{-ax, 0.0F, -az}, new float[]{ax, 0.0F, az}, new float[]{ax, height, az},
                    new float[]{-ax, height, -az}, argb, 0.0F, scroll, 1.0F, scroll + height / 4.0F);
        }
    }
}
