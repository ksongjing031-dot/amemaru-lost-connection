package jp.amemaru.lostconnection.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * 灯りの小瓶（11月23日の直し。ろうそくと灯りの店）。右クリックで、その場とまわりに90秒の灯りをともす（{@link LightService}）。
 * 暗い所に目印を残す・みんなの足もとを照らすために使う。1回で1つ減る。
 */
public class LanternVialItem extends Item {
    public LanternVialItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (LightService.placeVial(serverPlayer) <= 0) {
            serverPlayer.displayClientMessage(Component.translatable("message.amemaru.lantern_vial.no_room").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        serverPlayer.getCooldowns().addCooldown(this, 20);
        serverPlayer.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        serverPlayer.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 24, 1.5, 0.6, 1.5,
                0.02);
        serverPlayer.displayClientMessage(Component.translatable("message.amemaru.lantern_vial.lit", LightService.VIAL_SECONDS)
                .withStyle(ChatFormatting.YELLOW), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.lantern_vial", LightService.VIAL_SECONDS).withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
