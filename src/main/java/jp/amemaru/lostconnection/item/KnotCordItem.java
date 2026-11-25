package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.combat.LostService;
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
 * 消耗品「結び紐」（Phase 0-A 13章：ロスト状態は、結び直し・ヒーラーの技・消耗品「結び紐」で解ける。11月25日、7階から）。
 * 右クリックで、自分とまわり {@link #RANGE} ブロックの人のロスト状態を解く。だれもロスト状態でなければ、減らない【補完】。
 */
public class KnotCordItem extends Item {
    public static final double RANGE = 5.0;
    public static final int COOLDOWN_TICKS = 200;

    public KnotCordItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        int cleared = 0;
        int people = 0;
        for (ServerPlayer other : serverPlayer.serverLevel().players()) {
            if (other.distanceToSqr(serverPlayer) > RANGE * RANGE || !other.isAlive()) {
                continue;
            }
            int n = LostService.clear(other);
            if (n > 0) {
                cleared += n;
                people++;
                serverPlayer.serverLevel().sendParticles(ParticleTypes.END_ROD, other.getX(), other.getY() + 1.0, other.getZ(), 12, 0.4,
                        0.6, 0.4, 0.03);
                if (other != serverPlayer) {
                    other.displayClientMessage(Component.translatable("message.amemaru.knot_cord.freed_by", serverPlayer.getDisplayName())
                            .withStyle(ChatFormatting.GOLD), true);
                }
            }
        }
        if (cleared == 0) {
            serverPlayer.displayClientMessage(Component.translatable("message.amemaru.knot_cord.nothing").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        serverPlayer.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.2F, 1.4F);
        serverPlayer.displayClientMessage(Component.translatable("message.amemaru.knot_cord.used", people).withStyle(ChatFormatting.GOLD), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.knot_cord", (int) RANGE).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
