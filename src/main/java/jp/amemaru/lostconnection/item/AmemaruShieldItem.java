package jp.amemaru.lostconnection.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import jp.amemaru.lostconnection.equipment.GearEffects;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.equipment.GearItems;
import jp.amemaru.lostconnection.equipment.GearSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;

/**
 * このModの盾（ナイト）。もう片方の手に持つと定義の分だけステータスが上がり、パッシブ「鉄壁」が効く。
 * 右クリックで構えられる（バニラの盾と同じ）。耐久はこのModの耐久（NBT）で、バニラの耐久は使わない。
 */
public class AmemaruShieldItem extends ShieldItem implements GearItem {
    public AmemaruShieldItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public GearSlot gearSlot() {
        return GearSlot.OFFHAND;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        // バニラの盾は旗の模様で名前が変わるが、このModの盾はいつも同じ名前
        return getDescriptionId();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.OFFHAND) {
            return super.getAttributeModifiers(slot, stack);
        }
        return GearEffects.withMoveSpeed(ImmutableMultimap.of(), stack, slot);
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
        // 修理は工房で行う（金床では直さない）
        return false;
    }
}
