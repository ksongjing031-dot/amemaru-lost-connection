package jp.amemaru.lostconnection.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 見習いの盾（ナイト）。もう片方の手に持つと防御が上がり、パッシブ「鉄壁」が効く。右クリックで構えられる（バニラの盾と同じ）。
 */
public class ApprenticeShieldItem extends ShieldItem {
    private final GearStats stats;

    public ApprenticeShieldItem(GearStats stats, Properties properties) {
        super(properties);
        this.stats = stats;
    }

    public GearStats stats() {
        return stats;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        // バニラの盾は旗の模様で名前が変わるが、見習いの盾はいつも同じ名前
        return getDescriptionId();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.weapon.shield").withStyle(ChatFormatting.GRAY));
        stats.appendTooltip(lines);
        if (GearStats.isBound(stack)) {
            lines.add(Component.translatable("tooltip.amemaru.bound").withStyle(ChatFormatting.DARK_RED));
        }
    }
}
