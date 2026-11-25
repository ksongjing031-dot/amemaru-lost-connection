package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.VitalsService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        PlayerData data = PlayerDataCapability.get(serverPlayer).orElse(null);
        if (def == null || data == null || !data.isStatsReady()) {
            return InteractionResultHolder.fail(stack);
        }
        if (data.down().isDown() || serverPlayer.isDeadOrDying()) {
            serverPlayer.displayClientMessage(Component.translatable("message.amemaru.consumable.down").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        boolean hp = def.restore() == ConsumableDef.Restore.HP;
        double before = hp ? data.getHp() : data.getMp();
        double max = hp ? data.getStats().maxHp() : data.getStats().maxMp();
        if (before >= max - 1.0E-6) {
            serverPlayer.displayClientMessage(Component.translatable(hp ? "message.amemaru.consumable.full_hp"
                    : "message.amemaru.consumable.full_mp").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        double amount = def.amount(max);
        if (hp) {
            data.setHp(before + amount);
            VitalsService.pushVanillaHealth(serverPlayer, data);
        } else {
            data.setMp(before + amount);
        }
        double restored = (hp ? data.getHp() : data.getMp()) - before;
        if (restored > 0) {
            CombatTextS2C.send(serverPlayer, restored, hp ? CombatTextS2C.Kind.HEAL : CombatTextS2C.Kind.MP);
        }
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        int ticks = def.cooldownTicks();
        if (ticks > 0) {
            for (ConsumableDef other : ConsumableDefs.group(def.group())) {
                Item item = ForgeRegistries.ITEMS.getValue(other.id());
                if (item != null) {
                    serverPlayer.getCooldowns().addCooldown(item, ticks);
                }
            }
            serverPlayer.getCooldowns().addCooldown(this, ticks);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8F,
                hp ? 1.1F : 1.3F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(hp ? ParticleTypes.HEART : ParticleTypes.GLOW, player.getX(), player.getY() + 1.2, player.getZ(),
                    hp ? 4 : 10, 0.35, 0.4, 0.35, 0.02);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        ConsumableDef def = def();
        if (def != null) {
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
            if (def.cooldown() > 0) {
                lines.add(Component.translatable("tooltip.amemaru.consumable.cooldown", number(def.cooldown())).withStyle(ChatFormatting.GRAY));
            }
        }
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
