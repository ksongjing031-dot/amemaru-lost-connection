package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.equipment.Rarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 素材（古代石・苔鉄・結晶の欠片・敵の素材・厳選の素材など）。ふだんは追加の情報（NBT）を持たない（Phase 0-B 4.3）。
 *
 * <p>説明文は言語ファイルの {@code item.amemaru.<名前>.desc}（世界観と主な入手先）。</p>
 */
public class MaterialItem extends Item {
    private final Rarity rarity;

    public MaterialItem(Rarity rarity, Properties properties) {
        super(properties);
        this.rarity = rarity;
    }

    public Rarity amemaruRarity() {
        return rarity;
    }

    @Override
    public Component getName(ItemStack stack) {
        return rarity == Rarity.COMMON ? super.getName(stack) : rarity.paint(super.getName(stack).copy());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.material").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
