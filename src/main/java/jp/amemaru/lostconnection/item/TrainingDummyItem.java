package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 訓練人形を置くアイテム。ブロックの上を右クリックすると、こちらを向いた人形が立つ。
 * 人形は倒れず、スキルのダメージの数字や連携の確かめに使う（ギルドの訓練場に置く想定）。
 */
public class TrainingDummyItem extends Item {
    public TrainingDummyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        TrainingDummy dummy = ModEntities.TRAINING_DUMMY.get().create(level);
        if (dummy == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        float yaw = player == null ? 0.0F : Mth.wrapDegrees(player.getYRot() + 180.0F);
        dummy.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        dummy.setYHeadRot(yaw);
        dummy.setYBodyRot(yaw);
        if (!level.noCollision(dummy, dummy.getBoundingBox())) {
            return InteractionResult.FAIL;
        }
        ((ServerLevel) level).addFreshEntity(dummy);
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.training_dummy").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.amemaru.training_dummy.remove").withStyle(ChatFormatting.DARK_GRAY));
    }
}
