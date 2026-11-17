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
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * 体に表示するアクセサリーの見た目（首飾り・腕輪・お守り・留め具。Phase 0-A 21章）。
 *
 * <p>GeckoLib の防具の描き方を使い、アクセサリーのモデル（{@code geo/accessory/<名前>.geo.json}）を体の部分に合わせて描く。
 * パーツは防具と同じボーンの名前（armorBody＝胸・腰、armorRightArm／armorLeftArm＝手首）に入れる。
 * 枠ごとの表示のオン・オフ（自分の画面でもまわりの人の画面でも）に従う。</p>
 */
public class AccessoryCurioRenderer implements ICurioRenderer {
    private final GeoArmorRenderer<AccessoryItem> renderer;

    public AccessoryCurioRenderer(AccessoryItem item) {
        renderer = new GeoArmorRenderer<>(new AssetGeoModel<>("accessory", item.assetName()));
        renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
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
        renderer.prepForRender(entity, stack, EquipmentSlot.CHEST, parent);
        renderer.renderToBuffer(poseStack, null, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
