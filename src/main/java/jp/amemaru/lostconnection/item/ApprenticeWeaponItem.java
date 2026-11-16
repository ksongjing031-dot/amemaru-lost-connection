package jp.amemaru.lostconnection.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 見習いの武器（職業を選ぶともらえる。Phase 0-A 20章「見習い装備」）。
 *
 * <p>手に持っている間、{@link GearStats} の分だけステータスが上がる。両手持ちの武器を持っていると、
 * もう片方の手の盾は効かない。近接攻撃のダメージは、バニラの値ではなく攻撃のステータスで決まる（{@code CombatService}）。</p>
 */
public class ApprenticeWeaponItem extends Item {
    public enum Kind {
        /** 片手剣（ナイト）。 */
        SWORD(false, -2.4, "tooltip.amemaru.weapon.sword"),
        /** 両手剣（ナイト）。 */
        GREATSWORD(true, -3.0, "tooltip.amemaru.weapon.greatsword"),
        /** 杖（ウィザード）。 */
        STAFF(false, -2.8, "tooltip.amemaru.weapon.staff");

        private final boolean twoHanded;
        private final double attackSpeed;
        private final String tooltip;

        Kind(boolean twoHanded, double attackSpeed, String tooltip) {
            this.twoHanded = twoHanded;
            this.attackSpeed = attackSpeed;
            this.tooltip = tooltip;
        }
    }

    private final Kind kind;
    private final GearStats stats;
    private final Multimap<Attribute, AttributeModifier> modifiers;

    public ApprenticeWeaponItem(Kind kind, GearStats stats, Properties properties) {
        super(properties);
        this.kind = kind;
        this.stats = stats;
        // バニラの攻撃力は「攻撃が当たるか」だけに効く小さな値にし、ダメージは攻撃のステータスで決める
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 1.0,
                AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", kind.attackSpeed,
                AttributeModifier.Operation.ADDITION));
        this.modifiers = builder.build();
    }

    public Kind kind() {
        return kind;
    }

    public GearStats stats() {
        return stats;
    }

    /** 両手持ちか（持っている間、盾が効かない）。 */
    public boolean isTwoHanded() {
        return kind.twoHanded;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(kind.tooltip).withStyle(ChatFormatting.GRAY));
        stats.appendTooltip(lines);
        if (kind.twoHanded) {
            lines.add(Component.translatable("tooltip.amemaru.weapon.two_handed").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (GearStats.isBound(stack)) {
            lines.add(Component.translatable("tooltip.amemaru.bound").withStyle(ChatFormatting.DARK_RED));
        }
    }
}
