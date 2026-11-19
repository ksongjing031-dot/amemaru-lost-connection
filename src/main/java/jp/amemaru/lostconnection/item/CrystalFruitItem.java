package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * ステータスアップアイテム「結晶果」（7種類。Phase 0-A 20章）。紅＝攻撃、蒼＝魔力、翠＝HP、琥珀＝防御、白＝素早さ、
 * 金＝会心、紫＝MP。
 *
 * <p>右クリックで食べると、そのステータスに「ステータスポイントを1つ振ったのと同じだけ」ずっと上がる【補完】
 * （「1つで+1」を、ポイント1つ分と読んだ。HPなら+12、会心なら+0.3%）。ステータスごとに上限は15個
 * （config の {@code fruitCap}）。取引はできない【確定】（雑魚から低確率、ボスから中確率で落ちる）。</p>
 */
public class CrystalFruitItem extends Item {
    private final StatType stat;

    public CrystalFruitItem(StatType stat, Properties properties) {
        super(properties);
        this.stat = stat;
    }

    public StatType stat() {
        return stat;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Rarity.RARE.paint(super.getName(stack).copy());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        PlayerData data = PlayerDataCapability.get(serverPlayer).orElse(null);
        if (data == null) {
            return InteractionResultHolder.fail(stack);
        }
        int cap = ServerConfig.FRUIT_CAP.get();
        int eaten = data.getAdventure().fruits(stat);
        if (eaten >= cap) {
            serverPlayer.displayClientMessage(Component.translatable("message.amemaru.fruit.capped",
                    Component.translatable(stat.translationKey()), cap).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        Component name = stack.getHoverName();
        data.getAdventure().setFruits(stat, eaten + 1);
        LevelService.recompute(serverPlayer, data);
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        serverPlayer.displayClientMessage(Component.translatable("message.amemaru.fruit.eaten", name,
                Component.translatable(stat.translationKey()), eaten + 1, cap).withStyle(ChatFormatting.GOLD), false);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(),
                    16, 0.4, 0.6, 0.4, 0.05);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.fruit", Component.translatable(stat.translationKey()))
                .withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.amemaru.untradeable").withStyle(ChatFormatting.DARK_RED));
    }
}
