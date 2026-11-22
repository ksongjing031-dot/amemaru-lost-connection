package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 三人称：両手で使う武器（籠手・二刀の短剣）を、空いているもう片方の手にも描く層（11月23日に決定）。
 * 自分の三人称の視点と、ほかのプレイヤーの見た目の両方に効く。利き手はバニラの手に持つ物の層が描く。
 * 置き方はバニラ（ItemInHandLayer#renderArmWithItem）と同じなので、モデルの持ち方（thirdperson_lefthand）で決まる。
 */
public class TwinHandLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public TwinHandLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                         ItemInHandRenderer itemInHandRenderer) {
        super(parent);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!TwinWeapons.drawsOffHand(player)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        HumanoidArm arm = player.getMainArm().getOpposite();
        boolean left = arm == HumanoidArm.LEFT;
        poseStack.pushPose();
        getParentModel().translateToHand(arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        itemInHandRenderer.renderItem(player, weapon,
                left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left,
                poseStack, buffer, light);
        poseStack.popPose();
    }
}
