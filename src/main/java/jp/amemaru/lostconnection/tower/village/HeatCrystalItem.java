package jp.amemaru.lostconnection.tower.village;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 熱の結晶（6階「凍結した村」の熱の炉でもらう。11月25日）と、記憶の結晶（6階のボス、村長の残響オルドとの戦いの間に、広場のまわりの
 * 家に現れる）。どちらも重く、持っている間は足が遅くなる（{@link VillageService}）。熱の結晶は、しばらくすると冷えて消える。
 * 階の外へは持ち出せない【補完】。
 */
public class HeatCrystalItem extends Item {
    private final boolean memory;

    public HeatCrystalItem(boolean memory, Properties properties) {
        super(properties);
        this.memory = memory;
    }

    /** 記憶の結晶か（熱の結晶でなく）。 */
    public boolean isMemory() {
        return memory;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(memory ? "tooltip.amemaru.memory_crystal" : "tooltip.amemaru.heat_crystal")
                .withStyle(memory ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        if (!memory && level != null && stack.hasTag() && stack.getTag().contains(VillageService.MADE_TAG)) {
            long left = VillageService.CRYSTAL_SECONDS - (level.getGameTime() - stack.getTag().getLong(VillageService.MADE_TAG)) / 20;
            lines.add(Component.translatable("tooltip.amemaru.heat_crystal.left", Math.max(0, left)).withStyle(ChatFormatting.YELLOW));
        }
        lines.add(Component.translatable("tooltip.amemaru.carry_slow").withStyle(ChatFormatting.RED));
    }
}
