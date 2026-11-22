package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.amemaru.lostconnection.item.AmemaruWeaponItem;
import jp.amemaru.lostconnection.item.WeaponKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;

/**
 * 両手で使う武器の見た目（11月23日に決定）：籠手は両手にはめ、二刀の短剣は両手に1本ずつ持つ。
 *
 * <p>アイテムは利き手の1つだけ（もう片方の手は空いている）。もう片方の手が空いているときに、同じ武器をもう片方の手にも描く。
 * 三人称（自分の後ろ・正面、ほかのプレイヤー）は {@link TwinHandLayer}、一人称は {@link #onRenderHand}。</p>
 * <ul>
 *   <li>籠手：腕の先（手首から拳）を包むように描く。三人称は、利き手は持ち方（{@code thirdperson_righthand}）で腕に合わせ、
 *       もう片方の手は {@link TwinHandLayer} が同じ持ち方で描く。一人称は、素手のときの腕を両方描いて、その腕にはめる。</li>
 *   <li>二刀の短剣：利き手はバニラのまま。もう片方の手（一人称・三人称）に、同じ短剣を左右を返して描く。</li>
 * </ul>
 * <p>籠手のモデル（{@code models/item/<名前>_3d.json}）は、腕の付け根を (9, 0, 8) に置いた「腕の座標」で作る
 * （tools/artgen/make_gear_phase6.py）。腕は x 6〜10・y −2〜10・z 6〜10 で、y は腕の先（手）へ向かう。</p>
 */
public final class TwinWeapons {
    /** 一人称で、利き手の「持ち上げぐあい」（攻撃のあと下がって戻る）。もう片方の手もそろえる。 */
    private static float mainEquip;

    private TwinWeapons() {
    }

    /** 両手で使う武器（二刀の短剣・籠手）か。 */
    public static boolean isTwin(ItemStack stack) {
        return stack.getItem() instanceof AmemaruWeaponItem weapon
                && (weapon.kind() == WeaponKind.DAGGERS || weapon.kind() == WeaponKind.FIST);
    }

    /** 籠手か。 */
    public static boolean isFist(ItemStack stack) {
        return stack.getItem() instanceof AmemaruWeaponItem weapon && weapon.kind() == WeaponKind.FIST;
    }

    /** もう片方の手にも描くか（利き手に両手の武器・もう片方の手が空いている）。 */
    public static boolean drawsOffHand(LivingEntity entity) {
        return isTwin(entity.getMainHandItem()) && entity.getOffhandItem().isEmpty();
    }

    /**
     * 一人称の手（Forgeのイベントバス。{@code ClientForgeEvents} から呼ぶ）。
     * 籠手は両手とも、腕を描いてはめる。二刀の短剣は、空いているもう片方の手に短剣を描く。
     */
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!isTwin(weapon)) {
            return;
        }
        boolean fist = isFist(weapon);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            mainEquip = event.getEquipProgress();
            if (fist) {
                event.setCanceled(true);
                renderFistArm(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), player,
                        player.getMainArm(), weapon, event.getEquipProgress(), event.getSwingProgress());
            }
            return;
        }
        if (!player.getOffhandItem().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        HumanoidArm arm = player.getMainArm().getOpposite();
        // もう片方の手は、利き手と同じだけ下がる（攻撃のあと、両手がそろって戻る）
        float equip = Math.max(event.getEquipProgress(), mainEquip);
        if (fist) {
            renderFistArm(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), player, arm, weapon,
                    equip, event.getSwingProgress());
        } else {
            renderHeldItem(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), player, arm, weapon,
                    equip, event.getSwingProgress());
        }
    }

    /** 一人称：素手のときと同じ置き方で腕を描き、その腕に籠手をはめる（ItemInHandRenderer#renderPlayerArm と同じ動き）。 */
    private static void renderFistArm(PoseStack poseStack, MultiBufferSource buffer, int light, LocalPlayer player,
                                      HumanoidArm arm, ItemStack weapon, float equip, float swing) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderer<? super LocalPlayer> entityRenderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
        if (!(entityRenderer instanceof PlayerRenderer renderer) || player.isScoping()) {
            return;
        }
        boolean right = arm == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        float root = Mth.sqrt(swing);
        float dx = -0.3F * Mth.sin(root * (float) Math.PI);
        float dy = 0.4F * Mth.sin(root * ((float) Math.PI * 2.0F));
        float dz = -0.4F * Mth.sin(swing * (float) Math.PI);
        poseStack.pushPose();
        poseStack.translate(side * (dx + 0.64000005F), dy - 0.6F + equip * -0.6F, dz - 0.71999997F);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
        float a = Mth.sin(swing * swing * (float) Math.PI);
        float b = Mth.sin(root * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * b * 70.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * a * -20.0F));
        poseStack.translate(side * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -135.0F));
        poseStack.translate(side * 5.6F, 0.0F, 0.0F);
        if (!player.isInvisible()) {
            if (right) {
                renderer.renderRightHand(poseStack, buffer, light, player);
            } else {
                renderer.renderLeftHand(poseStack, buffer, light, player);
            }
        }
        // 描いた腕（renderRightHand が置いた形のまま）の先に、籠手をはめる
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        poseStack.pushPose();
        model.translateToHand(arm, poseStack);
        renderWorn(poseStack, buffer, light, player, weapon, arm);
        poseStack.popPose();
        poseStack.popPose();
    }

    /**
     * 籠手を、腕の座標（{@code translateToHand} のあと）で描く。三人称の持ち方（{@code thirdperson_righthand}：
     * 回転 [−90, 180, 0]・移動 [0, −2, 2]・大きさ 1）と同じ所に来る。
     */
    public static void renderWorn(PoseStack poseStack, MultiBufferSource buffer, int light, LivingEntity entity, ItemStack weapon,
                                  HumanoidArm arm) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getItemRenderer().getModel(weapon, entity.level(), entity, entity.getId());
        poseStack.pushPose();
        // モデルの (9, 0, 8)（右腕）・(7, 0, 8)（左腕）が、腕の付け根に来る
        poseStack.translate((arm == HumanoidArm.RIGHT ? -1.0F : 1.0F) / 16.0F, 0.5F, 0.0F);
        minecraft.getItemRenderer().render(weapon, ItemDisplayContext.NONE, arm == HumanoidArm.LEFT, poseStack, buffer, light,
                OverlayTexture.NO_OVERLAY, model);
        poseStack.popPose();
    }

    /** 一人称：もう片方の手に、武器を持たせる（ItemInHandRenderer#renderArmWithItem のふつうの物と同じ動き）。 */
    private static void renderHeldItem(PoseStack poseStack, MultiBufferSource buffer, int light, LocalPlayer player,
                                       HumanoidArm arm, ItemStack weapon, float equip, float swing) {
        if (player.isScoping()) {
            return;
        }
        boolean right = arm == HumanoidArm.RIGHT;
        int side = right ? 1 : -1;
        poseStack.pushPose();
        float root = Mth.sqrt(swing);
        float dx = -0.4F * Mth.sin(root * (float) Math.PI);
        float dy = 0.2F * Mth.sin(root * ((float) Math.PI * 2.0F));
        float dz = -0.2F * Mth.sin(swing * (float) Math.PI);
        poseStack.translate(side * dx, dy, dz);
        // applyItemArmTransform
        poseStack.translate(side * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        // applyItemArmAttackTransform
        float a = Mth.sin(swing * swing * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0F + a * -20.0F)));
        float b = Mth.sin(root * (float) Math.PI);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * b * -20.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(b * -80.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0F));
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, weapon,
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !right,
                poseStack, buffer, light);
        poseStack.popPose();
    }
}
