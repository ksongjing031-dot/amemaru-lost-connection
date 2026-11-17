package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.job.ResetService;
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
 * やり直しのアイテム（Phase 0-A 19章）。「忘れ水」はステータスの振り分けを、「白紙の誓約書」はスキルの習得を、
 * すべて戻す。間違えて使わないよう、3秒以内に2回使うと実行する。それぞれ最初の1つは、職業を選んだときに無料で配る。
 */
public class ResetItem extends Item {
    public enum Kind {
        /** 忘れ水：ステータスの振り分けを戻す。 */
        STATS("tooltip.amemaru.forget_water"),
        /** 白紙の誓約書：スキルの習得を戻す。 */
        SKILLS("tooltip.amemaru.blank_oath");

        private final String tooltip;

        Kind(String tooltip) {
            this.tooltip = tooltip;
        }
    }

    private final Kind kind;

    public ResetItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            boolean used = ResetService.useItem(serverPlayer, kind);
            if (used && !serverPlayer.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(kind.tooltip).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.amemaru.reset.how").withStyle(ChatFormatting.DARK_GRAY));
        if (GearStats.isBound(stack)) {
            lines.add(Component.translatable("tooltip.amemaru.bound").withStyle(ChatFormatting.DARK_RED));
        }
    }
}
