package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.player.CollectStatsEvent;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * キャンディー（11月29日のご指示「一時的なステータスバフを得るアイテム『キャンディー』を追加しましょう。攻撃が上がる『剛力キャンディー』
 * など」）：使うと、しばらくステータスが上がる（{@link ConsumableDef.Buff}。数値は {@code data/amemaru/amemaru/consumables/candy_*.json}）。
 *
 * <ul>
 *   <li>上がるのは一度に1つ。新しく使うと入れかわる（同じキャンディーなら、時間がはじめからになる）【補完】</li>
 *   <li>料理屋の料理（{@code RestaurantService}。こちらも1つ）とは別に重なる【補完】</li>
 *   <li>残りの時間は、ステータスの種類ごとの状態（{@link ModEffects#candy}）で数える。HUD と持ち物の画面に、上がっている種類の
 *       印と残りの時間が出る。上がる量はプレイヤーの永続データ {@code AmemaruCandy} に書き、状態がある間だけ足す
 *       （時間切れ・牛乳・倒れて教会へ戻ると、状態ごと消える）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class ItemBuffService {
    private static final String TAG = "AmemaruCandy";

    private ItemBuffService() {
    }

    /** キャンディーを使った：前のキャンディーの効き目を消して、新しく上げる。 */
    public static void apply(ServerPlayer player, ItemStack stack, ConsumableDef.Buff buff) {
        for (StatType stat : StatType.values()) {
            MobEffect effect = ModEffects.candy(stat);
            if (player.hasEffect(effect)) {
                player.removeEffect(effect);
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("item", String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())));
        tag.putString("stat", buff.stat().id());
        tag.putBoolean("percent", buff.percent());
        tag.putDouble("amount", buff.amount());
        player.getPersistentData().put(TAG, tag);
        player.addEffect(new MobEffectInstance(ModEffects.candy(buff.stat()), buff.ticks(), 0, false, false, true));
        PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
        player.displayClientMessage(Component.translatable("message.amemaru.candy.ate", stack.getHoverName(), text(buff))
                .withStyle(ChatFormatting.GOLD), true);
    }

    /** 今効いているキャンディーの強化（なければ null）。状態が消えていれば、効いていない。 */
    @Nullable
    public static CompoundTag active(Player player) {
        CompoundTag tag = player.getPersistentData().getCompound(TAG);
        if (tag.isEmpty()) {
            return null;
        }
        StatType stat = StatType.byId(tag.getString("stat")).orElse(null);
        return stat != null && player.hasEffect(ModEffects.candy(stat)) ? tag : null;
    }

    @SubscribeEvent
    public static void onCollectStats(CollectStatsEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        CompoundTag tag = active(player);
        if (tag == null) {
            return;
        }
        StatType.byId(tag.getString("stat")).ifPresent(stat -> {
            if (tag.getBoolean("percent")) {
                event.addPercent(stat, tag.getDouble("amount"));
            } else {
                event.addFlat(stat, tag.getDouble("amount"));
            }
        });
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        refresh(event.getEntity(), event.getEffectInstance().getEffect(), false);
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        refresh(event.getEntity(), instance == null ? null : instance.getEffect(), true);
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        refresh(event.getEntity(), event.getEffect(), false);
    }

    /** キャンディーの状態が付いた・消えた：ステータスを計算し直す（その tick の終わりに）。時間切れなら知らせる。 */
    private static void refresh(LivingEntity entity, @Nullable MobEffect effect, boolean expired) {
        if (!(entity instanceof ServerPlayer player) || effect == null || !ModEffects.isCandy(effect)) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
        if (expired) {
            CompoundTag tag = player.getPersistentData().getCompound(TAG);
            player.displayClientMessage(Component.translatable("message.amemaru.candy.worn_off", itemName(tag.getString("item")))
                    .withStyle(ChatFormatting.GRAY), false);
            player.getPersistentData().remove(TAG);
        }
    }

    /** 自動テスト：キャンディーの効き目を消す。 */
    public static void clearForTest(ServerPlayer player) {
        for (StatType stat : StatType.values()) {
            player.removeEffect(ModEffects.candy(stat));
        }
        player.getPersistentData().remove(TAG);
        PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
    }

    // ---------------------------------------------------------------- 文

    /** 「攻撃 +8%（3分）」「会心 +4%（90秒）」。 */
    public static MutableComponent text(ConsumableDef.Buff buff) {
        double amount = buff.amount();
        String value = (amount == Math.floor(amount) ? String.valueOf((long) amount) : String.format(Locale.ROOT, "%.1f", amount))
                + (buff.percent() || buff.stat().isPercent() ? "%" : "");
        Component time = buff.seconds() % 60 == 0
                ? Component.translatable("tooltip.amemaru.candy.minutes", buff.seconds() / 60)
                : Component.translatable("tooltip.amemaru.candy.seconds", buff.seconds());
        return Component.translatable("tooltip.amemaru.candy.effect", Component.translatable(buff.stat().translationKey()), value, time);
    }

    private static Component itemName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return item == null ? Component.translatable("item.amemaru.candy") : new ItemStack(item).getHoverName();
    }
}
