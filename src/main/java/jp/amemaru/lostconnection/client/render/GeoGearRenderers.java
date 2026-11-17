package jp.amemaru.lostconnection.client.render;

import jp.amemaru.lostconnection.item.AmemaruArmorItem;
import jp.amemaru.lostconnection.item.GeoWeaponItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLibで描く装備の見た目を作る（クライアント専用。アイテムの {@code initializeClient} から呼ぶ）。
 */
public final class GeoGearRenderers {
    private GeoGearRenderers() {
    }

    /** 動く・光る武器（{@code geo/item/<名前>.geo.json}）。光る部分は {@code textures/item/<名前>_glowmask.png}。 */
    public static BlockEntityWithoutLevelRenderer weapon(GeoWeaponItem item) {
        GeoItemRenderer<GeoWeaponItem> renderer = new GeoItemRenderer<>(new AssetGeoModel<>("item", item.name()));
        renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
        return renderer;
    }

    /** 防具（{@code geo/armor/<シリーズ>.geo.json}）。 */
    public static HumanoidModel<?> armor(AmemaruArmorItem item) {
        GeoArmorRenderer<AmemaruArmorItem> renderer = new GeoArmorRenderer<>(new AssetGeoModel<>("armor", item.series()));
        renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
        return renderer;
    }

    /** 防具を描く前に、今描く体と場所を渡す。 */
    public static void prepareArmor(HumanoidModel<?> renderer, LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                                    HumanoidModel<?> original) {
        if (renderer instanceof GeoArmorRenderer<?> armor) {
            armor.prepForRender(entity, stack, slot, original);
        }
    }
}
