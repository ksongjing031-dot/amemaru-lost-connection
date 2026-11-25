package jp.amemaru.lostconnection.town;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ペトラ・ポムの料理（Phase 7 その4。共同事業③「料理による一時強化」）。食べると、しばらく強くなる（強化は1つだけ）。
 * 強化の中身は、作ったときにアイテムの {@code AmemaruDish} に書く（説明もそこから出す）。
 */
public class DishItem extends Item {
    public static final String TAG = "AmemaruDish";

    public DishItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            RestaurantService.eat(player, stack);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag != null) {
            tooltip.add(RestaurantService.effectText(tag.getString("stat"), tag.getBoolean("percent"), tag.getDouble("amount"),
                    tag.getInt("minutes")).withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("item.amemaru.dish.desc").withStyle(ChatFormatting.GRAY));
    }
}
