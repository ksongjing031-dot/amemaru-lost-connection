package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.tower.FloorThingBlockEntity;
import jp.amemaru.lostconnection.tower.castle.CastleBlocks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * 9階の王家の肖像画を、名札の台座の上の壁に描く（11月28日）。絵は3×4ブロックで、顔は空白（ロストの侵食「名前と顔の空白」）。
 * 名前の札がはまると（{@code restored}）、顔の描かれた絵になり、台座に名前が出る。女王の肖像は、ずっと顔が空白で、名札もない。
 * 絵は {@code textures/block/castle/portrait_<名前>.png}（空白）と {@code portrait_<名前>_named.png}（戻った）。
 */
public class PortraitRenderer implements BlockEntityRenderer<FloorThingBlockEntity> {
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private final Font font;

    public PortraitRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    private static ResourceLocation texture(String id, boolean named) {
        String key = id + (named ? "_named" : "");
        return TEXTURES.computeIfAbsent(key, k -> AmemaruMod.id("textures/block/castle/portrait_" + k + ".png"));
    }

    @Override
    public void render(FloorThingBlockEntity thing, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       int packedOverlay) {
        BlockState state = thing.getBlockState();
        if (!(state.getBlock() instanceof CastleBlocks.Portrait)) {
            return;
        }
        String group = thing.group();
        String id;
        if (group.startsWith("portrait:")) {
            id = group.substring("portrait:".length());
        } else if (group.equals("queen")) {
            id = "queen";
        } else {
            return;
        }
        boolean queen = id.equals("queen");
        boolean restored = state.getValue(CastleBlocks.RESTORED) && !queen;
        Direction facing = state.getValue(CastleBlocks.FACING);
        BlockPos front = thing.getBlockPos().relative(facing);
        int light = thing.getLevel() == null ? packedLight : LevelRenderer.getLightColor(thing.getLevel(), front.above(2));
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // 絵（壁の面のすぐ前。横3・縦4）
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture(id, restored)));
        PoseStack.Pose pose = poseStack.last();
        float z = -0.5F + 0.015F;
        quad(consumer, pose.pose(), pose.normal(), -1.5F, 1.0F, 1.5F, 5.0F, z, light);
        // 名札（台座の前）：戻ると名前、空白の間は「？？？」。女王は何も書かれていない
        if (!queen) {
            poseStack.pushPose();
            poseStack.translate(0.0, 0.62, -0.5 + 0.25 + 0.01);
            poseStack.scale(0.0125F, -0.0125F, 0.0125F);
            Component text = restored ? Component.translatable("lineage.amemaru.portrait." + id + ".name")
                    : Component.literal("？？？");
            float width = font.width(text);
            int color = restored ? 0xFFF2D27A : 0xFF8A93A6;
            font.drawInBatch(text, -width / 2.0F, 0.0F, color, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0,
                    LevelRenderer.getLightColor(thing.getLevel(), front));
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x0, float y0, float x1, float y1, float z,
                             int light) {
        // 見る人から見て、左下 → 右下 → 右上 → 左上（左が x0）
        vertex(consumer, matrix, normal, x0, y0, z, 0.0F, 1.0F, light);
        vertex(consumer, matrix, normal, x1, y0, z, 1.0F, 1.0F, light);
        vertex(consumer, matrix, normal, x1, y1, z, 1.0F, 0.0F, light);
        vertex(consumer, matrix, normal, x0, y1, z, 0.0F, 0.0F, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float y, float z, float u, float v,
                               int light) {
        consumer.vertex(matrix, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(FloorThingBlockEntity thing) {
        return thing.getBlockState().getBlock() instanceof CastleBlocks.Portrait;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
