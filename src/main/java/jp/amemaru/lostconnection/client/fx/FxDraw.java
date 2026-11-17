package jp.amemaru.lostconnection.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 演出の形を描く道具（クライアント専用。{@link FxWorld} が1フレームに1つ作る）。
 *
 * <p>座標はワールドの座標のまま渡す（カメラの位置は、頂点を書くときに倍精度で引く。遠い場所でも形が崩れないため）。
 * 色は 0xRRGGBB と不透明度（0〜1）。
 * テクスチャは {@link FxTextures} のもの。</p>
 */
public final class FxDraw {
    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final Camera camera;
    private final float partialTick;
    private final Vec3 cameraPos;
    private final Vector3f left;
    private final Vector3f up;

    FxDraw(PoseStack pose, MultiBufferSource buffers, Camera camera, float partialTick) {
        this.pose = pose;
        this.buffers = buffers;
        this.camera = camera;
        this.partialTick = partialTick;
        this.cameraPos = camera.getPosition();
        this.left = new Vector3f(camera.getLeftVector());
        this.up = new Vector3f(camera.getUpVector());
    }

    public float partialTick() {
        return partialTick;
    }

    public Vec3 cameraPos() {
        return cameraPos;
    }

    public Camera camera() {
        return camera;
    }

    /** 光を足し合わせる描き方の、書き込み先。 */
    public VertexConsumer glow(ResourceLocation texture) {
        return buffers.getBuffer(FxRenderTypes.glow(texture));
    }

    /** 半透明の描き方の、書き込み先。 */
    public VertexConsumer translucent(ResourceLocation texture) {
        return buffers.getBuffer(FxRenderTypes.translucent(texture));
    }

    // ---------------------------------------------------------------- 頂点

    public void vertex(VertexConsumer vc, double x, double y, double z, float u, float v, int rgb, float alpha) {
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float a = Mth.clamp(alpha, 0.0F, 1.0F);
        vc.vertex(matrix, (float) (x - cameraPos.x), (float) (y - cameraPos.y), (float) (z - cameraPos.z))
                .color(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** 4つの角で四角を描く（a→b→c→d の順に、まわりを回る）。 */
    public void quad(VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1, int rgb, float alpha) {
        vertex(vc, a.x, a.y, a.z, u0, v0, rgb, alpha);
        vertex(vc, b.x, b.y, b.z, u0, v1, rgb, alpha);
        vertex(vc, c.x, c.y, c.z, u1, v1, rgb, alpha);
        vertex(vc, d.x, d.y, d.z, u1, v0, rgb, alpha);
    }

    // ---------------------------------------------------------------- 形

    /** カメラの方を向いた四角（光の玉・星・頭の上の印）。{@code rotation} はラジアン。 */
    public void billboard(VertexConsumer vc, Vec3 center, float size, float rotation, int rgb, float alpha) {
        billboard(vc, center, size, rotation, 0.0F, 0.0F, 1.0F, 1.0F, rgb, alpha);
    }

    /** テクスチャの一部（u0〜u1, v0〜v1）を使う、カメラの方を向いた四角。 */
    public void billboard(VertexConsumer vc, Vec3 center, float size, float rotation, float u0, float v0, float u1, float v1, int rgb,
                          float alpha) {
        float cos = Mth.cos(rotation);
        float sin = Mth.sin(rotation);
        float h = size / 2.0F;
        Vector3f l = new Vector3f(left).mul(cos).add(new Vector3f(up).mul(sin)).mul(h);
        Vector3f u = new Vector3f(up).mul(cos).sub(new Vector3f(left).mul(sin)).mul(h);
        Vec3 a = center.add(l.x + u.x, l.y + u.y, l.z + u.z);
        Vec3 b = center.add(l.x - u.x, l.y - u.y, l.z - u.z);
        Vec3 c = center.add(-l.x - u.x, -l.y - u.y, -l.z - u.z);
        Vec3 d = center.add(-l.x + u.x, -l.y + u.y, -l.z + u.z);
        quad(vc, a, b, c, d, u0, v0, u1, v1, rgb, alpha);
    }

    /** 地面に寝かせた四角（陣・波紋）。{@code rotation} はラジアン。 */
    public void ground(VertexConsumer vc, Vec3 center, float radius, float rotation, int rgb, float alpha) {
        float cos = Mth.cos(rotation) * radius;
        float sin = Mth.sin(rotation) * radius;
        double y = center.y;
        Vec3 a = new Vec3(center.x + (-cos + sin), y, center.z + (-sin - cos));
        Vec3 b = new Vec3(center.x + (-cos - sin), y, center.z + (-sin + cos));
        Vec3 c = new Vec3(center.x + (cos - sin), y, center.z + (sin + cos));
        Vec3 d = new Vec3(center.x + (cos + sin), y, center.z + (sin - cos));
        quad(vc, a, b, c, d, 0.0F, 0.0F, 1.0F, 1.0F, rgb, alpha);
    }

    /**
     * 2点を結ぶ帯（いつもカメラの方を向く）。光の糸・鎖・稲妻の1本。
     *
     * @param u0 帯の始まりのテクスチャの位置（帯に沿って u が進む。鎖のくり返しに使う）
     */
    public void beam(VertexConsumer vc, Vec3 from, Vec3 to, float width, float u0, float u1, int rgb, float alpha) {
        beam(vc, from, to, width, u0, u1, rgb, alpha, alpha);
    }

    /** 始まりと終わりで不透明度の違う帯（尾を引く光）。 */
    public void beam(VertexConsumer vc, Vec3 from, Vec3 to, float width, float u0, float u1, int rgb, float alphaFrom, float alphaTo) {
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 view = from.add(to).scale(0.5).subtract(cameraPos);
        Vec3 side = direction.cross(view);
        if (side.lengthSqr() < 1.0E-8) {
            side = direction.cross(new Vec3(0.0, 1.0, 0.0));
            if (side.lengthSqr() < 1.0E-8) {
                side = new Vec3(1.0, 0.0, 0.0);
            }
        }
        side = side.normalize().scale(width / 2.0);
        vertex(vc, from.x + side.x, from.y + side.y, from.z + side.z, u0, 0.0F, rgb, alphaFrom);
        vertex(vc, from.x - side.x, from.y - side.y, from.z - side.z, u0, 1.0F, rgb, alphaFrom);
        vertex(vc, to.x - side.x, to.y - side.y, to.z - side.z, u1, 1.0F, rgb, alphaTo);
        vertex(vc, to.x + side.x, to.y + side.y, to.z + side.z, u1, 0.0F, rgb, alphaTo);
    }

    /**
     * 立てた輪（筒の側面）。波紋の壁・光の柱・円陣のふち。テクスチャの v は下（0）から上（1）。
     *
     * @param uRepeat まわりに何回テクスチャをくり返すか
     */
    public void ringWall(VertexConsumer vc, Vec3 center, float radius, float height, int segments, float rotation, float uRepeat,
                         int rgb, float alphaBottom, float alphaTop) {
        for (int i = 0; i < segments; i++) {
            float a0 = rotation + i / (float) segments * Mth.TWO_PI;
            float a1 = rotation + (i + 1) / (float) segments * Mth.TWO_PI;
            double x0 = center.x + Mth.cos(a0) * radius;
            double z0 = center.z + Mth.sin(a0) * radius;
            double x1 = center.x + Mth.cos(a1) * radius;
            double z1 = center.z + Mth.sin(a1) * radius;
            float u0 = i / (float) segments * uRepeat;
            float u1 = (i + 1) / (float) segments * uRepeat;
            vertex(vc, x0, center.y, z0, u0, 1.0F, rgb, alphaBottom);
            vertex(vc, x1, center.y, z1, u1, 1.0F, rgb, alphaBottom);
            vertex(vc, x1, center.y + height, z1, u1, 0.0F, rgb, alphaTop);
            vertex(vc, x0, center.y + height, z0, u0, 0.0F, rgb, alphaTop);
        }
    }

    /**
     * 弧の帯（斬撃）。中心のまわり、半径 radius の円の一部を、太さ width で描く。
     *
     * @param yaw     弧の向き（度。0で南、プレイヤーの向きと同じ決まり）
     * @param tilt    弧の面の傾き（度。0で水平、90で縦）
     * @param start   弧の始まり（度。yaw からの角度）
     * @param end     弧の終わり（度）
     */
    public void arc(VertexConsumer vc, Vec3 center, float radius, float width, float yaw, float tilt, float start, float end,
                    int segments, int rgb, float alphaInner, float alphaOuter) {
        float yawRad = -yaw * Mth.DEG_TO_RAD;
        float tiltRad = tilt * Mth.DEG_TO_RAD;
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            float a0 = (start + (end - start) * t0) * Mth.DEG_TO_RAD;
            float a1 = (start + (end - start) * t1) * Mth.DEG_TO_RAD;
            Vec3 in0 = arcPoint(center, radius - width, a0, yawRad, tiltRad);
            Vec3 out0 = arcPoint(center, radius, a0, yawRad, tiltRad);
            Vec3 in1 = arcPoint(center, radius - width, a1, yawRad, tiltRad);
            Vec3 out1 = arcPoint(center, radius, a1, yawRad, tiltRad);
            vertex(vc, in0.x, in0.y, in0.z, t0, 1.0F, rgb, alphaInner * fadeEnds(t0));
            vertex(vc, out0.x, out0.y, out0.z, t0, 0.0F, rgb, alphaOuter * fadeEnds(t0));
            vertex(vc, out1.x, out1.y, out1.z, t1, 0.0F, rgb, alphaOuter * fadeEnds(t1));
            vertex(vc, in1.x, in1.y, in1.z, t1, 1.0F, rgb, alphaInner * fadeEnds(t1));
        }
    }

    /** 弧の両端を少し薄くする。 */
    private static float fadeEnds(float t) {
        return Mth.clamp(Math.min(t, 1.0F - t) * 5.0F, 0.0F, 1.0F) * 0.8F + 0.2F;
    }

    private static Vec3 arcPoint(Vec3 center, float radius, float angle, float yawRad, float tiltRad) {
        // 傾ける前：前（+z）を0度として、xz の面で回る
        double lx = Mth.sin(angle) * radius;
        double lz = Mth.cos(angle) * radius;
        // 前後の軸のまわりに傾ける（tilt）
        double ly = lx * Mth.sin(tiltRad);
        lx = lx * Mth.cos(tiltRad);
        // 向き（yaw）で回す
        double rx = lx * Mth.cos(yawRad) + lz * Mth.sin(yawRad);
        double rz = -lx * Mth.sin(yawRad) + lz * Mth.cos(yawRad);
        return center.add(rx, ly, rz);
    }

    /**
     * 球（障壁・円陣のドーム）。{@code hemisphere} なら上半分だけ。
     *
     * @param uScroll テクスチャを横に流す量（模様が回って見える）
     */
    public void sphere(VertexConsumer vc, Vec3 center, float radius, int stacks, int slices, boolean hemisphere, float uScroll, int rgb,
                       float alpha) {
        float minPhi = hemisphere ? 0.0F : -Mth.HALF_PI;
        for (int i = 0; i < stacks; i++) {
            float p0 = minPhi + (Mth.HALF_PI - minPhi) * i / stacks;
            float p1 = minPhi + (Mth.HALF_PI - minPhi) * (i + 1) / stacks;
            for (int j = 0; j < slices; j++) {
                float t0 = j / (float) slices * Mth.TWO_PI;
                float t1 = (j + 1) / (float) slices * Mth.TWO_PI;
                float u0 = j / (float) slices * 4.0F + uScroll;
                float u1 = (j + 1) / (float) slices * 4.0F + uScroll;
                float v0 = 1.0F - i / (float) stacks;
                float v1 = 1.0F - (i + 1) / (float) stacks;
                // 下ほど濃く、てっぺんほど薄い（ふちが光って見える）
                float a0 = alpha * (0.45F + 0.55F * (1.0F - Math.abs(Mth.sin(p0))));
                float a1 = alpha * (0.45F + 0.55F * (1.0F - Math.abs(Mth.sin(p1))));
                Vec3 q00 = spherePoint(center, radius, p0, t0);
                Vec3 q01 = spherePoint(center, radius, p0, t1);
                Vec3 q11 = spherePoint(center, radius, p1, t1);
                Vec3 q10 = spherePoint(center, radius, p1, t0);
                vertex(vc, q00.x, q00.y, q00.z, u0, v0, rgb, a0);
                vertex(vc, q01.x, q01.y, q01.z, u1, v0, rgb, a0);
                vertex(vc, q11.x, q11.y, q11.z, u1, v1, rgb, a1);
                vertex(vc, q10.x, q10.y, q10.z, u0, v1, rgb, a1);
            }
        }
    }

    private static Vec3 spherePoint(Vec3 center, float radius, float phi, float theta) {
        double r = Mth.cos(phi) * radius;
        return center.add(Mth.cos(theta) * r, Mth.sin(phi) * radius, Mth.sin(theta) * r);
    }

    /** 箱（凍結した敵を包む氷）。6面とも描く。 */
    public void box(VertexConsumer vc, AABB box, int rgb, float alpha) {
        double x0 = box.minX;
        double y0 = box.minY;
        double z0 = box.minZ;
        double x1 = box.maxX;
        double y1 = box.maxY;
        double z1 = box.maxZ;
        // 下・上
        quad(vc, new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y0, z0), 0, 0, 1, 1, rgb, alpha);
        quad(vc, new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), 0, 0, 1, 1, rgb, alpha);
        // 北・南
        quad(vc, new Vec3(x0, y1, z0), new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), 0, 0, 1, 1, rgb, alpha);
        quad(vc, new Vec3(x1, y1, z1), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), 0, 0, 1, 1, rgb, alpha);
        // 西・東
        quad(vc, new Vec3(x0, y1, z1), new Vec3(x0, y0, z1), new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), 0, 0, 1, 1, rgb, alpha);
        quad(vc, new Vec3(x1, y1, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), 0, 0, 1, 1, rgb, alpha);
    }

    /** 4つの面のとがった柱（氷の棘・氷柱）。base から tip へ。 */
    public void spike(VertexConsumer vc, Vec3 base, Vec3 tip, float radius, float rotation, int rgb, float alpha) {
        Vec3 axis = tip.subtract(base);
        if (axis.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 dir = axis.normalize();
        Vec3 helper = Math.abs(dir.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 sideA = dir.cross(helper).normalize();
        Vec3 sideB = dir.cross(sideA).normalize();
        Vec3[] corners = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            float angle = rotation + i * Mth.HALF_PI;
            corners[i] = base.add(sideA.scale(Mth.cos(angle) * radius)).add(sideB.scale(Mth.sin(angle) * radius));
        }
        for (int i = 0; i < 4; i++) {
            Vec3 a = corners[i];
            Vec3 b = corners[(i + 1) % 4];
            // 三角を、頂点を2回使った四角で描く
            vertex(vc, a.x, a.y, a.z, 0.0F, 1.0F, rgb, alpha * 0.85F);
            vertex(vc, b.x, b.y, b.z, 1.0F, 1.0F, rgb, alpha * 0.85F);
            vertex(vc, tip.x, tip.y, tip.z, 0.5F, 0.0F, rgb, alpha);
            vertex(vc, tip.x, tip.y, tip.z, 0.5F, 0.0F, rgb, alpha);
        }
    }

    /** 2点の間の、ギザギザの稲妻の点（seed が同じなら同じ形）。 */
    public static Vec3[] lightningPoints(Vec3 from, Vec3 to, int segments, float jitter, long seed) {
        Vec3[] points = new Vec3[segments + 1];
        java.util.Random random = new java.util.Random(seed);
        for (int i = 0; i <= segments; i++) {
            Vec3 point = from.lerp(to, i / (double) segments);
            if (i > 0 && i < segments) {
                double falloff = Math.sin(Math.PI * i / segments);
                point = point.add((random.nextDouble() - 0.5) * jitter * falloff, (random.nextDouble() - 0.5) * jitter * falloff,
                        (random.nextDouble() - 0.5) * jitter * falloff);
            }
            points[i] = point;
        }
        return points;
    }

    /** 点をつないだ帯（稲妻・糸）。 */
    public void polyline(VertexConsumer vc, Vec3[] points, float width, int rgb, float alpha) {
        for (int i = 0; i + 1 < points.length; i++) {
            beam(vc, points[i], points[i + 1], width, 0.0F, 1.0F, rgb, alpha);
        }
    }
}
