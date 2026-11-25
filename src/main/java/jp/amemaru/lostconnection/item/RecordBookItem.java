package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.guild.RecordService;
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
 * 記録帳（Phase 0-A 14章。Phase 6 その3）。エルネストから受け取る。右クリックで、見つけた古代記録と、階ごとの収集率を見る。
 * 中身は本人の記録（持ち物の本そのものには何も書かない）なので、なくしても、エルネストに頼めばもう一冊もらえる。
 */
public class RecordBookItem extends Item {
    public RecordBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            RecordService.openBook(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.amemaru.record_book.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.amemaru.record_book.use").withStyle(ChatFormatting.GREEN));
    }
}
