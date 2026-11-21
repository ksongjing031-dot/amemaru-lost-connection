package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.amemaru.lostconnection.client.ClientStory;
import jp.amemaru.lostconnection.client.fx.FxRenderTypes;
import jp.amemaru.lostconnection.client.fx.FxTextures;
import jp.amemaru.lostconnection.tower.TowerPillarBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * 石柱の核の光（見ている人が起動したかで変わる）：起動していない石柱は、核の上に淡い結晶がゆっくり浮かぶだけ。
 * 起動した石柱は、翠の結晶が回って光り、空へ細い光の柱が立つ（遠くからでも、どの石柱を起動したかが分かる）。
 */
public class TowerPillarRenderer implements BlockEntityRenderer<TowerPillarBlockEntity> {
    public TowerPillarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TowerPillarBlockEntity pillar, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       int packedOverlay) {
        if (pillar.getLevel() == null) {
            return;
        }
        boolean lit = ClientStory.pillarActivated(pillar.floor(), pillar.index());
        float time = pillar.getLevel().getGameTime() + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.5, 1.35 + Mth.sin(time * 0.06F) * 0.08, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * (lit ? 2.2F : 0.6F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(35.0F));
        int crystal = lit ? 0xE07FFFA8 : 0x705A7A6A;
        TowerFxDraw.cube(poseStack, buffer.getBuffer(FxRenderTypes.glow(FxTextures.GLOW)), lit ? 0.42F : 0.32F, crystal);
        poseStack.popPose();
        if (lit) {
            poseStack.pushPose();
            poseStack.translate(0.5, 1.0, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.8F));
            float pulse = 0.75F + 0.25F * Mth.sin(time * 0.1F);
            int alpha = Math.round(120 * pulse);
            TowerFxDraw.beam(poseStack, buffer.getBuffer(FxRenderTypes.glow(FxTextures.BEAM)), 0.22F, 18.0F, (alpha << 24) | 0x9CFFC0,
                    -time * 0.02F);
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TowerPillarBlockEntity pillar) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
