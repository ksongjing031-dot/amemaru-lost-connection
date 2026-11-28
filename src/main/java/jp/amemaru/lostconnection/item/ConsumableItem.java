package jp.amemaru.lostconnection.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * 消耗品（回復薬（小）・魔力薬（小）など。Phase 6）。右クリックで、すぐにHPかMPが戻る（飲む間の動きは待たない。戦いの最中に使うため）
 * 【補完】。使ったあとは、同じ group の品がしばらく使えない。ダウンの間は使えない。数値は {@link ConsumableDefs}。
 */
public class ConsumableItem extends Item {
    public ConsumableItem(Properties properties) {
        super(properties);
    }

    @Nullable
    private ConsumableDef def() {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(this);
        return id == null ? null : ConsumableDefs.get(id);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        ConsumableDef def = def();
        if (def == null || !ConsumableService.consume(serverPlayer, stack, def)) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        ConsumableDef def = def();
        if (def != null) {
            restoreLines(def, lines);
        }
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }

    /** 説明文の「HPが○%戻る」と「使ったあと○秒は使えない」の行（バニラの食べ物の説明にも使う。11月23日）。 */
    public static void restoreLines(ConsumableDef def, List<Component> lines) {
        // 11月29日：キャンディー（しばらくステータスが上がる）
        if (def.buff() != null) {
            lines.add(Component.translatable("tooltip.amemaru.candy.line", ItemBuffService.text(def.buff())).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("tooltip.amemaru.candy.one").withStyle(ChatFormatting.GRAY));
        }
        if (def.restore() != null) {
            restoreAmountLine(def, lines);
        }
        if (def.cooldown() > 0) {
            lines.add(Component.translatable(def.group().equals("food") ? "tooltip.amemaru.consumable.cooldown_food"
                    : def.buff() != null && def.restore() == null ? "tooltip.amemaru.candy.cooldown" : "tooltip.amemaru.consumable.cooldown",
                    number(def.cooldown())).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void restoreAmountLine(ConsumableDef def, List<Component> lines) {
        String what = def.restore() == ConsumableDef.Restore.HP ? "hp" : "mp";
        if (def.percent() > 0 && def.flat() > 0) {
            lines.add(Component.translatable("tooltip.amemaru.consumable." + what + "_both", number(def.percent()), number(def.flat()))
                    .withStyle(ChatFormatting.GREEN));
        } else if (def.percent() > 0) {
            lines.add(Component.translatable("tooltip.amemaru.consumable." + what + "_percent", number(def.percent()))
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lines.add(Component.translatable("tooltip.amemaru.consumable." + what + "_flat", number(def.flat()))
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    static String number(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
