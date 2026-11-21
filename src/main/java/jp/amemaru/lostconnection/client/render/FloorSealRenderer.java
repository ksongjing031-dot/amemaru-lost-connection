package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.amemaru.lostconnection.client.fx.FxRenderTypes;
import jp.amemaru.lostconnection.client.fx.FxTextures;
import jp.amemaru.lostconnection.tower.FloorSealBlock;
import jp.amemaru.lostconnection.tower.FloorSealBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ボスの部屋の封印（光の幕）の見た目。見ている人が通れるかで色が変わる：
 * 通れる（石柱をすべて起動した）＝薄い金、通れない＝白く濃い幕、戦いの間（閉じている）＝赤い幕。
 */
public class FloorSealRenderer implements BlockEntityRenderer<FloorSealBlockEntity> {
    public FloorSealRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FloorSealBlockEntity seal, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       int packedOverlay) {
        Level level = seal.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = seal.getBlockPos();
        BlockState state = seal.getBlockState();
        boolean sealed = state.hasProperty(FloorSealBlock.SEALED) && state.getValue(FloorSealBlock.SEALED);
        boolean pass = !sealed && Minecraft.getInstance().player != null && FloorSealBlock.canPass(Minecraft.getInstance().player, seal.floor());
        float time = level.getGameTime() + partialTick;
        float pulse = 0.8F + 0.2F * Mth.sin(time * 0.08F + (pos.getX() + pos.getY() * 3 + pos.getZ()) * 0.4F);
        int argb;
        if (sealed) {
            argb = (Math.round(170 * pulse) << 24) | 0xFF5A48;
        } else if (pass) {
            argb = (Math.round(55 * pulse) << 24) | 0xFFE6A0;
        } else {
            argb = (Math.round(150 * pulse) << 24) | 0xCFE8FF;
        }
        // 幕の向き：となりの封印が東西に並んでいれば、南北を向く面
        boolean alongX = level.getBlockState(pos.east()).getBlock() instanceof FloorSealBlock
                || level.getBlockState(pos.west()).getBlock() instanceof FloorSealBlock;
        VertexConsumer consumer = buffer.getBuffer(FxRenderTypes.glow(FxTextures.WALL));
        float scroll = time * 0.01F;
        float v0 = scroll + Math.floorMod(pos.getY(), 4) * 0.25F;
        poseStack.pushPose();
        if (alongX) {
            TowerFxDraw.quad(poseStack, consumer, new float[]{0.0F, 0.0F, 0.5F}, new float[]{1.0F, 0.0F, 0.5F}, new float[]{1.0F, 1.0F, 0.5F},
                    new float[]{0.0F, 1.0F, 0.5F}, argb, Math.floorMod(pos.getX(), 4) * 0.25F, v0, Math.floorMod(pos.getX(), 4) * 0.25F + 0.25F, v0 + 0.25F);
        } else {
            TowerFxDraw.quad(poseStack, consumer, new float[]{0.5F, 0.0F, 0.0F}, new float[]{0.5F, 0.0F, 1.0F}, new float[]{0.5F, 1.0F, 1.0F},
                    new float[]{0.5F, 1.0F, 0.0F}, argb, Math.floorMod(pos.getZ(), 4) * 0.25F, v0, Math.floorMod(pos.getZ(), 4) * 0.25F + 0.25F, v0 + 0.25F);
        }
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
