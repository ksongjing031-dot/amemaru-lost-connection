package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.client.ClientAdventure;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * 体に表示するアクセサリーの見た目（首飾り・腕輪・お守り・留め具。Phase 0-A 21章）。
 *
 * <p>GeckoLib の防具の描き方を使い、アクセサリーのモデル（{@code geo/accessory/<名前>.geo.json}）を体の部分に合わせて描く。
 * パーツは防具と同じボーンの名前（armorBody＝胸・腰、armorRightArm／armorLeftArm＝手首）に入れる。
 * 枠ごとの表示のオン・オフ（自分の画面でもまわりの人の画面でも）に従う。</p>
 *
 * <p>胸の防具を着ているときは、防具に隠れないように、胴（首飾り・お守り）は横 {@value #BODY_WIDTH}・前後
 * {@value #BODY_DEPTH} 倍、腕（腕輪）は横と前後を {@value #ARM_SCALE} 倍に広げて描く（ボーンの中心で広げる。
 * 高さは変えない）。値は tools/artgen/make_gear_accessories.py の確認画像と同じ。</p>
 */
public class AccessoryCurioRenderer implements ICurioRenderer {
    static final float BODY_WIDTH = 1.3F;
    static final float BODY_DEPTH = 1.45F;
    static final float ARM_SCALE = 1.25F;

    private final Renderer renderer;

    public AccessoryCurioRenderer(AccessoryItem item) {
        renderer = new Renderer(new AssetGeoModel<>("accessory", item.assetName()));
        renderer.addRenderLayer(new OptionalGlowLayer<>(renderer));
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack poseStack,
                                                                          RenderLayerParent<T, M> renderLayerParent,
                                                                          MultiBufferSource buffer, int light, float limbSwing,
                                                                          float limbSwingAmount, float partialTicks, float ageInTicks,
                                                                          float netHeadYaw, float headPitch) {
        if (!(stack.getItem() instanceof AccessoryItem item) || !item.visibleOnBody()) {
            return;
        }
        LivingEntity entity = slotContext.entity();
        if (!ClientAdventure.accessoryVisible(entity.getId(), item.slot().ordinal())) {
            return;
        }
        if (!(renderLayerParent.getModel() instanceof HumanoidModel<?> parent)) {
            return;
        }
        renderer.armored = entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem;
        renderer.prepForRender(entity, stack, EquipmentSlot.CHEST, parent);
        renderer.renderToBuffer(poseStack, null, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 防具の上に見えるように、胸の防具を着ているときだけ、胴と腕のボーンを外へ広げる。 */
    private static final class Renderer extends GeoArmorRenderer<AccessoryItem> {
        private boolean armored;

        Renderer(GeoModel<AccessoryItem> model) {
            super(model);
        }

        @Override
        protected void applyBaseTransformations(HumanoidModel<?> baseModel) {
            super.applyBaseTransformations(baseModel);
            float width = armored ? BODY_WIDTH : 1.0F;
            float depth = armored ? BODY_DEPTH : 1.0F;
            float arm = armored ? ARM_SCALE : 1.0F;
            if (this.body != null) {
                this.body.setScaleX(width);
                this.body.setScaleY(1.0F);
                this.body.setScaleZ(depth);
            }
            for (var bone : new software.bernie.geckolib.cache.object.GeoBone[]{this.rightArm, this.leftArm}) {
                if (bone != null) {
                    bone.setScaleX(arm);
                    bone.setScaleY(1.0F);
                    bone.setScaleZ(arm);
                }
            }
        }
    }
}
