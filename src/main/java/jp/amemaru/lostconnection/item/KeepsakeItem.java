package jp.amemaru.lostconnection.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * NPCゆかりの小物（Phase 0-A 14章「親密度4 親友：NPCゆかりの小物（見た目だけ）」。Phase 6 その3）。
 * 強さには何も影響しない、思い出の品。説明の文は {@code item.amemaru.<名前>.desc}。
 */
public class KeepsakeItem extends Item {
    private final String key;

    public KeepsakeItem(String name, Properties properties) {
        super(properties);
        this.key = "item.amemaru." + name + ".desc";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("tooltip.amemaru.keepsake").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
