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
 * 手提げランタン（11月23日の直し。ろうそくと灯りの店）。手（どちらでも）に持っている間、まわりが明るくなる
 * （{@link LightService}）。暗い階層や、夜の街で使う。使っても減らない。
 */
public class HandLanternItem extends Item {
    public HandLanternItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.hand_lantern").withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
