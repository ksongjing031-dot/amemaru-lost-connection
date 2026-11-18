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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * このModの武器（片手剣・両手剣・杖）。種類（IDと見た目）は {@code amemaru/item_types.json} で起動時に登録し、
 * 数値は装備の定義（データパックの {@code equipment/}）から読む。
 *
 * <p>手に持っている間、定義と品のデータ（レアリティ・抽選・強化）の分だけステータスが上がる（{@code GearService}）。
 * 両手持ちの武器を持っていると、もう片方の手の盾は効かない。近接攻撃のダメージはバニラの値ではなく、
 * 攻撃のステータスで決まる（{@code CombatService}）。耐久はこのModの耐久（NBT）で、0になっても消えずに「壊れた」状態になる。</p>
 */
public class AmemaruWeaponItem extends Item implements GearItem {
    private final WeaponKind kind;
    private final Multimap<Attribute, AttributeModifier> modifiers;

    public AmemaruWeaponItem(WeaponKind kind, Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
        // バニラの攻撃力は「攻撃が当たるか」だけに効く小さな値にし、ダメージは攻撃のステータスで決める
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 1.0,
                AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", kind.attackSpeed(),
                AttributeModifier.Operation.ADDITION));
        this.modifiers = builder.build();
    }

    public WeaponKind kind() {
        return kind;
    }

    @Override
    public GearSlot gearSlot() {
        return GearSlot.WEAPON;
    }

    @Override
    public boolean isTwoHanded() {
        return kind.twoHanded();
    }

    @SuppressWarnings("deprecation")
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) {
            return super.getAttributeModifiers(slot, stack);
        }
        return GearEffects.withMoveSpeed(modifiers, stack, slot);
    }

    @Override
    public int getDefaultTooltipHideFlags(ItemStack stack) {
        // バニラの「攻撃力」「攻撃速度」の行は出さない（このModのステータスで説明する）
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
        // 強さはこのModの強化で上げる（バニラのエンチャントは使わない）
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // 耐久や抽選の書きかえで、手に持った武器が上下に揺れないようにする
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }
}
