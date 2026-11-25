package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.amemaru.lostconnection.enemy.iceforest.Mimic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 写し身（7階）を、写した人の姿（スキン）で描く（11月25日）。写した人がいない・見つからないときは、ID から決まるふつうのスキン。
 * 少し透けた、氷に映ったような見た目。名前は「？？？」（{@link jp.amemaru.lostconnection.client.LostClientEvents}）。
 * 本物の仲間との見分け方（Phase 0-A 9章の7階「名前の表示とHPバーの動き」）：名前の色が氷の色で、叩くと頭の上にHPバーが出て減る
 * （プレイヤーには頭の上のHPバーがない。仲間のHPは、パーティの欄で動く）。
 */
public class MimicRenderer extends LivingEntityRenderer<Mimic, PlayerModel<Mimic>> {
    private final PlayerModel<Mimic> wide;
    private final PlayerModel<Mimic> slim;

    public MimicRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = getModel();
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Nullable
    private static PlayerInfo info(Mimic entity) {
        UUID copied = entity.copied();
        if (copied == null || Minecraft.getInstance().getConnection() == null) {
            return null;
        }
        return Minecraft.getInstance().getConnection().getPlayerInfo(copied);
    }

    @Override
    public ResourceLocation getTextureLocation(Mimic entity) {
        PlayerInfo info = info(entity);
        if (info != null) {
            return info.getSkinLocation();
        }
        UUID copied = entity.copied();
        return DefaultPlayerSkin.getDefaultSkin(copied != null ? copied : entity.getUUID());
    }

    @Override
    public void render(Mimic entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        PlayerInfo info = info(entity);
        UUID copied = entity.copied();
        String model = info != null ? info.getModelName() : DefaultPlayerSkin.getSkinModelName(copied != null ? copied : entity.getUUID());
        this.model = "slim".equals(model) ? slim : wide;
        this.model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        this.model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
        renderBar(entity, poseStack, buffers);
    }

    /** 叩かれて減ったHPのバー（名前の下。敵のHPバーと同じ色の決まり）。 */
    private void renderBar(Mimic entity, PoseStack poseStack, MultiBufferSource buffers) {
        double ratio = entity.getEnemyHp() / Math.max(1.0, entity.getEnemyMaxHp());
        if (ratio >= 0.999 || !entity.isAlive() || !shouldShowName(entity)) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + 0.25, 0.0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer bar = buffers.getBuffer(RenderType.textBackgroundSeeThrough());
        int width = 40;
        float left = -width / 2.0F;
        fill(bar, matrix, left - 1, 10.0F, left + width + 1, 14.0F, 0xC0000000);
        fill(bar, matrix, left, 11.0F, left + (float) (width * Mth.clamp(ratio, 0.0, 1.0)), 13.0F,
                ratio > 0.5 ? 0xFF52DC6E : ratio > 0.25 ? 0xFFE0B04A : 0xFFE05252);
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

    @Override
    protected void scale(Mimic entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Nullable
    @Override
    protected RenderType getRenderType(Mimic entity, boolean visible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(entity));
    }

    @Override
    protected boolean shouldShowName(Mimic entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && entity.distanceToSqr(minecraft.player) < 32.0 * 32.0 && !entity.isInvisible();
    }
}
