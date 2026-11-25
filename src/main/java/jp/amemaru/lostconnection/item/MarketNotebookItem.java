package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.economy.MarketService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 市場の手帳（Phase 0-A 20章「街のどこからでも開ける『取引所』の画面（ギルドで配る『市場の手帳』）」。Phase 6）。
 * 右クリックで取引所の画面（さがす・出品・出品中・売上と履歴・露店）。
 */
public class MarketNotebookItem extends Item {
    public MarketNotebookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            MarketService.open(serverPlayer, MarketService.Mode.NOTEBOOK, 0);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.amemaru.market_notebook.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.amemaru.market_notebook.use").withStyle(ChatFormatting.GREEN));
    }
}
