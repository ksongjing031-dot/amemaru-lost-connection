package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * このModの敵の見た目（GeckoLib）と、攻撃の予兆・名前とHPの帯。
 *
 * <ul>
 *   <li>モデル・絵・アニメーション：{@code geo/entity/<名前>.geo.json} など（{@link AssetGeoModel}）。光る部分は
 *       {@code textures/entity/<名前>_glowmask.png}</li>
 *   <li>予兆：突進の線（地面に、向きと長さを持つ帯）・周りへの攻撃の円・仲間を呼ぶ「！」。予兆の終わりに近づくほど濃くなる</li>
 *   <li>頭の上：名前とレベル。傷ついていればHPの帯</li>
 * </ul>
 */
public class AmemaruMobRenderer<T extends AmemaruMob> extends GeoEntityRenderer<T> {
    private static final ResourceLocation TELEGRAPH = AmemaruMod.id("textures/fx/telegraph.png");
    private static final int TELEGRAPH_COLOR = 0xFF4A3A;

    public AmemaruMobRenderer(EntityRendererProvider.Context context, String name, float shadow) {
        super(context, new AssetGeoModel<>("entity", name));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = shadow;
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        // 倒れる動きは死ぬアニメーションで見せる（バニラの横倒しはしない）
        return 0.0F;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
        renderTelegraph(entity, partialTick, poseStack, buffer);
        if (!entity.isDeadOrDying() && entity.distanceToSqr(entityRenderDispatcher.camera.getPosition()) < 24 * 24) {
            renderLabel(entity, poseStack, buffer, packedLight);
        }
    }

    // ---------------------------------------------------------------- 予兆

    private void renderTelegraph(T entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        int kind = entity.telegraph();
        if (kind == AmemaruMob.TELEGRAPH_NONE || kind == AmemaruMob.TELEGRAPH_WINDUP) {
            return;
        }
        float total = Math.max(1, entity.telegraphTicks());
        float progress = Mth.clamp(entity.telegraphAge(partialTick) / total, 0.0F, 1.0F);
        if (kind == AmemaruMob.TELEGRAPH_ALERT) {
            renderAlert(entity, poseStack, buffer, progress);
            return;
        }
        float pulse = 0.5F + 0.5F * Mth.sin(entity.telegraphAge(partialTick) * 0.6F);
        int alpha = Math.round((0.25F + 0.55F * progress + 0.15F * pulse) * 255.0F);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(TELEGRAPH));
        poseStack.pushPose();
        poseStack.translate(0.0, 0.06, 0.0);
        if (kind == AmemaruMob.TELEGRAPH_LINE) {
            // 体の向きではなく、予兆を出したときの向き（固定）で地面に帯を描く
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.telegraphYaw()));
            float length = entity.telegraphSize();
            float half = 0.75F;
            quad(consumer, poseStack, -half, 0.0F, half, length, alpha);
            // 終わり近くは、帯の中を進む明るい線で「いつ来るか」を見せる
            float sweep = length * progress;
            quad(consumer, poseStack, -half, Math.max(0.0F, sweep - 0.4F), half, sweep, Math.min(255, alpha + 60));
        } else if (kind == AmemaruMob.TELEGRAPH_CIRCLE) {
            float radius = entity.telegraphSize() + 0.5F;
            int segments = 32;
            for (int i = 0; i < segments; i++) {
                float a0 = (float) (i * Math.PI * 2.0 / segments);
                float a1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
                float inner = radius * progress;
                wedge(consumer, poseStack, a0, a1, Math.max(0.0F, inner - 0.3F), inner, Math.min(255, alpha + 60));
                wedge(consumer, poseStack, a0, a1, radius - 0.2F, radius, alpha);
                wedge(consumer, poseStack, a0, a1, 0.0F, radius - 0.2F, alpha / 3);
            }
        }
        poseStack.popPose();
    }

    private void renderAlert(T entity, PoseStack poseStack, MultiBufferSource buffer, float progress) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + 0.9 + 0.15 * Mth.sin(progress * 6.0F), 0.0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.05F, -0.05F, 0.05F);
        Component mark = Component.literal("!");
        float x = -font.width(mark) / 2.0F;
        font.drawInBatch(mark, x, 0.0F, 0xFFFF5040, false, poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH,
                0x40000000, 0xF000F0);
        poseStack.popPose();
    }

    /** 地面に平らな四角（x0〜x1、奥行き z0〜z1）。 */
    private static void quad(VertexConsumer consumer, PoseStack poseStack, float x0, float z0, float x1, float z1, int alpha) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        vertex(consumer, matrix, normal, x0, z0, 0.0F, 0.0F, alpha);
        vertex(consumer, matrix, normal, x0, z1, 0.0F, 1.0F, alpha);
        vertex(consumer, matrix, normal, x1, z1, 1.0F, 1.0F, alpha);
        vertex(consumer, matrix, normal, x1, z0, 1.0F, 0.0F, alpha);
    }

    /** 地面に扇形の帯（半径 r0〜r1、角度 a0〜a1）。 */
    private static void wedge(VertexConsumer consumer, PoseStack poseStack, float a0, float a1, float r0, float r1, int alpha) {
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
                .color((TELEGRAPH_COLOR >> 16) & 0xFF, (TELEGRAPH_COLOR >> 8) & 0xFF, TELEGRAPH_COLOR & 0xFF, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    // ---------------------------------------------------------------- 名前とHP

    private void renderLabel(T entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        EnemyDef.Info info = id == null ? null : EnemyDefs.clientInfo().get(id);
        Component name = info == null ? entity.getDisplayName()
                : Component.translatable("enemy.amemaru.label", info.name(), info.level());
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + 0.45, 0.0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(name) / 2.0F;
        int nameColor = entity.isReturning() ? 0xFF9AA6BF : (info != null && info.lost() ? 0xFFE8F4FF : 0xFFFFFFFF);
        font.drawInBatch(name, x, 0.0F, nameColor, false, matrix, buffer, Font.DisplayMode.NORMAL, 0x40000000, packedLight);
        double ratio = entity.getEnemyHp() / entity.getEnemyMaxHp();
        if (ratio < 0.999) {
            VertexConsumer bar = buffer.getBuffer(RenderType.textBackgroundSeeThrough());
            int width = 40;
            float left = -width / 2.0F;
            fill(bar, matrix, left - 1, 10.0F, left + width + 1, 14.0F, 0xC0000000);
            fill(bar, matrix, left, 11.0F, left + (float) (width * Mth.clamp(ratio, 0.0, 1.0)), 13.0F,
                    ratio > 0.5 ? 0xFF52DC6E : ratio > 0.25 ? 0xFFE0B04A : 0xFFE05252);
        }
        poseStack.popPose();
    }

    private static void fill(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1, int color) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        consumer.vertex(matrix, x0, y0, 0.01F).color(r, g, b, a).uv2(0xF000F0).endVertex();
        consumer.vertex(matrix, x0, y1, 0.01F).color(r, g, b, a).uv2(0xF000F0).endVertex();
        consumer.vertex(matrix, x1, y1, 0.01F).color(r, g, b, a).uv2(0xF000F0).endVertex();
        consumer.vertex(matrix, x1, y0, 0.01F).color(r, g, b, a).uv2(0xF000F0).endVertex();
    }
}
