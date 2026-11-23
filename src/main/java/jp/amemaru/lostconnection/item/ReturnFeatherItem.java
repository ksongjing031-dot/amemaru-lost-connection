package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.tower.BossRoom;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorRuntime;
import jp.amemaru.lostconnection.tower.TowerService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 帰還の羽根（11月23日の直し。雑貨屋・道具屋）。塔の中で右クリックを押し続けると、3秒で街の塔の門へ帰る
 * （{@link TowerService#returnToGate}。入口の帰りの円と同じ所）。階が広くなる Phase 8 で、来た道を戻らずに帰るための品。
 * ボスと戦っている間（ボスの部屋の戦いの中）と、ダウンの間は使えない【補完】。1回で1つ減る。
 */
public class ReturnFeatherItem extends Item {
    public static final int CHANNEL_TICKS = 60;

    public ReturnFeatherItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return CHANNEL_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    /** 今使えないわけ（使えるなら null）。 */
    @Nullable
    public static String blocked(ServerPlayer player) {
        if (!player.level().dimension().equals(FloorDefs.TOWER)) {
            return "message.amemaru.return_feather.not_tower";
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data != null && data.down().isDown()) {
            return "message.amemaru.consumable.down";
        }
        FloorDef def = FloorDefs.floorAt(player.level(), player.position());
        if (def != null) {
            FloorRuntime runtime = TowerService.runtime(def.number());
            BossRoom room = runtime == null ? null : runtime.bossRoom();
            if (room != null && room.fighting() && room.insideFight(player.position())) {
                return "message.amemaru.return_feather.boss";
            }
        }
        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            String why = blocked(serverPlayer);
            if (why != null) {
                serverPlayer.displayClientMessage(Component.translatable(why).withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
        } else if (!player.level().dimension().equals(FloorDefs.TOWER)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof ServerPlayer player && remaining % 5 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(), 6, 0.6, 0.1, 0.6,
                    0.02);
            player.displayClientMessage(Component.translatable("message.amemaru.return_feather.channel", (remaining + 19) / 20)
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            String why = blocked(player);
            if (why != null) {
                player.displayClientMessage(Component.translatable(why).withStyle(ChatFormatting.GRAY), true);
                return stack;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(this, 100);
            TowerService.returnToGate(player);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 1.0F, 1.2F);
            player.displayClientMessage(Component.translatable("message.amemaru.return_feather.done").withStyle(ChatFormatting.AQUA), true);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.return_feather", CHANNEL_TICKS / 20).withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
