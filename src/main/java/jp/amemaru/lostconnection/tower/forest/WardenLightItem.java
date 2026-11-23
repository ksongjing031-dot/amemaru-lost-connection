package jp.amemaru.lostconnection.tower.forest;

import jp.amemaru.lostconnection.item.HandLanternItem;
import jp.amemaru.lostconnection.tower.FloorDefs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 森番の灯（2階のボス戦。HP30%で森が暗くなると、空き地に2つ落ちる）。手提げランタンと同じく、手に持つとまわりが明るくなり、
 * 持っている人の近く（6ブロック）の人は目がくらまない。塔の外へ出る・10分たつと消える【補完】。
 */
public class WardenLightItem extends HandLanternItem {
    public static final int LIFETIME_TICKS = 10 * 60 * 20;

    public WardenLightItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide()) {
            return;
        }
        int age = stack.getOrCreateTag().getInt("age") + 1;
        stack.getOrCreateTag().putInt("age", age);
        if (age >= LIFETIME_TICKS || !level.dimension().equals(FloorDefs.TOWER)) {
            stack.setCount(0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.warden_light").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
