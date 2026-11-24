package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;


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

    /** 半透明で描くか（4階の祈り手の残響：声だけが残った、淡い姿。11月24日）。 */
    private final boolean translucent;

    public AmemaruMobRenderer(EntityRendererProvider.Context context, String name, float shadow) {
        this(context, name, shadow, false);
    }

    public AmemaruMobRenderer(EntityRendererProvider.Context context, String name, float shadow, boolean translucent) {
        super(context, new AssetGeoModel<>("entity", name));
        addRenderLayer(new OptionalGlowLayer<>(this));
        shadowRadius = shadow;
        this.translucent = translucent;
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, @javax.annotation.Nullable MultiBufferSource bufferSource,
                                    float partialTick) {
        return translucent ? RenderType.entityTranslucent(texture) : super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        // 倒れる動きは死ぬアニメーションで見せる（バニラの横倒しはしない）
        return 0.0F;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float scale = entity.modelScale();
        if (Math.abs(scale - 1.0F) > 1.0E-3F) {
            // 大きな個体（群れの長など。定義の behavior の scale。11月22日）：体だけ大きく描く（名前とHPのバーはそのまま）
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
            super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
            poseStack.popPose();
        } else {
            super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
        }
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
        float age = entity.telegraphAge(partialTick);
        float progress = Mth.clamp(age / total, 0.0F, 1.0F);
        if (kind == AmemaruMob.TELEGRAPH_ALERT) {
            renderAlert(entity, poseStack, buffer, progress);
            return;
        }
        int alpha = TelegraphDraw.alpha(progress, age);
        VertexConsumer consumer = TelegraphDraw.consumer(buffer);
        poseStack.pushPose();
        // 浅い水の中では、水面に描く（Phase 8 その2）
        poseStack.translate(0.0, 0.06 + TelegraphDraw.waterLift(entity), 0.0);
        if (kind == AmemaruMob.TELEGRAPH_LINE) {
            // 体の向きではなく、予兆を出したときの向き（固定）で地面に帯を描く。幅は arg（0 なら 1.5）
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.telegraphYaw()));
            float width = entity.telegraphArg() > 0.0F ? entity.telegraphArg() : 1.5F;
            TelegraphDraw.band(consumer, poseStack, width, entity.telegraphSize(), progress, alpha);
        } else if (kind == AmemaruMob.TELEGRAPH_CIRCLE) {
            TelegraphDraw.circle(consumer, poseStack, entity.telegraphSize() + 0.5F, progress, alpha);
        } else if (kind == AmemaruMob.TELEGRAPH_CONE) {
            // 扇形（薙ぎ払い・後ろ蹴り）：予兆を出したときの向き（固定）。開きは arg（度）
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.telegraphYaw()));
            float angle = entity.telegraphArg() > 0.0F ? entity.telegraphArg() : 120.0F;
            TelegraphDraw.cone(consumer, poseStack, angle, entity.telegraphSize() + 0.4F, progress, alpha);
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

    // ---------------------------------------------------------------- 名前とHP

    private void renderLabel(T entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ResourceLocation id = entity.variantId() != null ? entity.variantId() : ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
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
