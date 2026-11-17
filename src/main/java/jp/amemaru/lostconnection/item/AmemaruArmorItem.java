package jp.amemaru.lostconnection.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.render.GeoGearRenderers;
import jp.amemaru.lostconnection.equipment.GearEffects;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.equipment.GearItems;
import jp.amemaru.lostconnection.equipment.GearSlot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * このModの防具（頭・胴・脚・足）。GeckoLibの防具のモデルで描く（Phase 0-B 12章「防具」）。
 *
 * <p>見た目はシリーズごとに1つのファイル：{@code geo/armor/<シリーズ>.geo.json}・{@code textures/armor/<シリーズ>.png}。
 * ボーンの名前は armorHead・armorBody・armorRightArm・armorLeftArm・armorRightLeg・armorLeftLeg・armorRightBoot・armorLeftBoot。
 * 防御はバニラの防具の値ではなく、このModの防御のステータスで決まる（バニラの防具の値は0）。</p>
 */
public class AmemaruArmorItem extends ArmorItem implements GearItem, GeoItem {
    private final String series;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public AmemaruArmorItem(String series, Type type, Properties properties) {
        super(new SeriesMaterial(series), type, properties.stacksTo(1));
        this.series = series;
    }

    /** 見た目のシリーズの名前（ファイルの名前）。 */
    public String series() {
        return series;
    }

    @Override
    public GearSlot gearSlot() {
        return switch (getType()) {
            case HELMET -> GearSlot.HEAD;
            case CHESTPLATE -> GearSlot.CHEST;
            case LEGGINGS -> GearSlot.LEGS;
            case BOOTS -> GearSlot.FEET;
        };
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private HumanoidModel<?> renderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                                                          HumanoidModel<?> original) {
                if (renderer == null) {
                    renderer = GeoGearRenderers.armor(AmemaruArmorItem.this);
                }
                GeoGearRenderers.prepareArmor(renderer, entity, stack, slot, original);
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 防具は今は動かさない（揺れるマントなどは、モデルを作る人がアニメーションを足したら、ここで流す）
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        // GeckoLibのモデルが自分の絵を使うため、バニラの防具の絵は使わない（念のため同じ絵を返す）
        return AmemaruMod.MOD_ID + ":textures/armor/" + series + ".png";
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != getEquipmentSlot()) {
            return super.getAttributeModifiers(slot, stack);
        }
        // バニラの防具の値（0）は付けず、移動の速さの特殊効果だけを付ける
        return GearEffects.withMoveSpeed(ImmutableMultimap.of(), stack, slot);
    }

    @Override
    public int getDefaultTooltipHideFlags(ItemStack stack) {
        return ItemStack.TooltipPart.MODIFIERS.getMask();
    }

    @Override
    public Component getName(ItemStack stack) {
        return GearItems.name(stack, super.getName(stack));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        GearItems.inventoryTick(stack, level, entity);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return GearItems.barVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return GearItems.barWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return GearItems.barColor(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return GearItems.foil(stack) || super.isFoil(stack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return false;
    }

    /** バニラの防具の値を持たない素材（耐久もこのModの耐久を使うので0）。 */
    private record SeriesMaterial(String series) implements ArmorMaterial {
        @Override
        public int getDurabilityForType(Type type) {
            return 0;
        }

        @Override
        public int getDefenseForType(Type type) {
            return 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_IRON;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return AmemaruMod.MOD_ID + ":" + series;
        }

        @Override
        public float getToughness() {
            return 0.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    }
}
