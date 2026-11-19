package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.loot.LootItemEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 敵のドロップの見た目。持ち主にだけ見える（ほかの人の画面では描かない）。レアドロップには、レアリティの色の光の柱が立つ。
 */
public class LootItemRenderer extends ItemEntityRenderer {
    public LootItemRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(ItemEntity entity, Frustum frustum, double x, double y, double z) {
        if (entity instanceof LootItemEntity loot && Minecraft.getInstance().player != null
                && !loot.visibleTo(Minecraft.getInstance().player)) {
            return false;
        }
        return super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public void render(ItemEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity instanceof LootItemEntity loot && loot.rareTier() >= 0 && Minecraft.getInstance().level != null) {
            int color = Rarity.byIndex(loot.rareTier()).colorAt(System.currentTimeMillis());
            float[] colors = {((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F};
            poseStack.pushPose();
            poseStack.translate(-0.5, 0.0, -0.5);
            BeaconRenderer.renderBeaconBeam(poseStack, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F,
                    Minecraft.getInstance().level.getGameTime(), 0, 7, colors, 0.08F, 0.14F);
            poseStack.popPose();
        }
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
