package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.VitalsService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * 消耗品を使う（右クリックで、すぐにHPかMPが戻る。Phase 6 の {@link ConsumableItem} から分けた）。
 *
 * <p>11月23日の直し（「色々な店があるがどこで使うかわからないものが多いので実用的なものを置く」）：このModでは満腹度が
 * いつも満ちていて、バニラの食べ物は食べられなかった。食べ物（パン・焼いた肉・はちみつの瓶など）にも数値を付け
 * （{@code data/minecraft/amemaru/consumables/}）、右クリックで、すぐに少しHPかMPが戻るようにした。食べ物は group が
 * {@code food}（回復薬とは別のクールダウン）【補完】。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class ConsumableService {
    private ConsumableService() {
    }

    /** そのアイテムの数値（なければ null）。 */
    @Nullable
    public static ConsumableDef defOf(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : ConsumableDefs.get(id);
    }

    /**
     * 使う（サーバー）。戻せたら1つ減らしてクールダウンをかけ、true。ダウン中・満タン・数値がないときは false
     * （理由は画面の下に出す）。
     */
    public static boolean consume(ServerPlayer player, ItemStack stack, ConsumableDef def) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || !data.isStatsReady()) {
            return false;
        }
        if (data.down().isDown() || player.isDeadOrDying()) {
            player.displayClientMessage(Component.translatable("message.amemaru.consumable.down").withStyle(ChatFormatting.RED), true);
            return false;
        }
        boolean hp = def.restore() == ConsumableDef.Restore.HP;
        double before = hp ? data.getHp() : data.getMp();
        double max = hp ? data.getStats().maxHp() : data.getStats().maxMp();
        if (before >= max - 1.0E-6) {
            player.displayClientMessage(Component.translatable(hp ? "message.amemaru.consumable.full_hp"
                    : "message.amemaru.consumable.full_mp").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        double amount = def.amount(max);
        if (hp) {
            data.setHp(before + amount);
            VitalsService.pushVanillaHealth(player, data);
        } else {
            data.setMp(before + amount);
        }
        double restored = (hp ? data.getHp() : data.getMp()) - before;
        if (restored > 0) {
            CombatTextS2C.send(player, restored, hp ? CombatTextS2C.Kind.HEAL : CombatTextS2C.Kind.MP);
        }
        ItemStack shown = stack.copy();
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        int ticks = def.cooldownTicks();
        if (ticks > 0) {
            for (ConsumableDef other : ConsumableDefs.group(def.group())) {
                Item item = ForgeRegistries.ITEMS.getValue(other.id());
                if (item != null) {
                    player.getCooldowns().addCooldown(item, ticks);
                }
            }
            player.getCooldowns().addCooldown(shown.getItem(), ticks);
        }
        ServerLevel level = player.serverLevel();
        if (def.eat()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8F,
                    0.9F + level.random.nextFloat() * 0.2F);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shown), player.getX(), player.getEyeY() - 0.2, player.getZ(),
                    8, 0.2, 0.1, 0.2, 0.05);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8F,
                    hp ? 1.1F : 1.3F);
        }
        level.sendParticles(hp ? ParticleTypes.HEART : ParticleTypes.GLOW, player.getX(), player.getY() + 1.2, player.getZ(),
                hp ? (def.eat() ? 2 : 4) : (def.eat() ? 5 : 10), 0.35, 0.4, 0.35, 0.02);
        return true;
    }

    /**
     * バニラの食べ物など、{@link ConsumableItem} ではない品に数値があるとき：右クリックで、すぐに戻す（食べる間の動きは待たない。
     * 回復薬と同じ）。クライアントでも止めて、食べる動きを始めないようにする（使う知らせはサーバーへ届く）。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() instanceof ConsumableItem) {
            return;
        }
        ConsumableDef def = defOf(stack);
        if (def == null) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setCancellationResult(consume(player, stack, def) ? InteractionResult.CONSUME : InteractionResult.FAIL);
        } else {
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
