package jp.amemaru.lostconnection.economy;

import jp.amemaru.lostconnection.block.ShopCounterBlockEntity;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.OpenShopS2C;
import jp.amemaru.lostconnection.network.s2c.ShopResultS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * 商店街の店（Phase 0-A 7章「商店街：消耗品の購入、素材の売却。ほかの12軒の店も、暮らしの品を売り、その店に合う品を買い取る
 * （11月20日に決定。塔の素材の買い取りは雑貨屋だけ）」。Phase 6）。
 *
 * <ul>
 *   <li>店のカウンターの呼び鈴（{@code amemaru:shop_counter}）を右クリックすると、店の画面が開く（11月22日に決定：GUIで使う）</li>
 *   <li>買う：所持金と持ち物の空きを確かめてから、払って渡す（足りなければ何もしない）</li>
 *   <li>売る：持っている数を確かめてから、取って払う</li>
 *   <li>値段はサーバーの店の定義で決める（画面が送ってきた値段は使わない）。出し入れは {@link Wallet} を通る</li>
 * </ul>
 */
public final class ShopService {
    private ShopService() {
    }

    /** 1回に買える・売れる数の上限 */
    public static final int MAX_COUNT = 64;

    public enum Result { OK, NO_SHOP, TOO_FAR, INVALID, NOT_ENOUGH_MONEY, NO_ROOM, NOT_HAVE }

    /** 店の画面を開く。 */
    public static void open(ServerPlayer player, BlockPos pos, ResourceLocation shopId) {
        ShopDef def = ShopDefs.get(shopId);
        if (def == null) {
            player.sendSystemMessage(Component.translatable("message.amemaru.shop.closed").withStyle(ChatFormatting.GRAY));
            return;
        }
        ModNetwork.sendTo(player, new OpenShopS2C(pos, def));
    }

    /** 買う。 */
    public static Result buy(ServerPlayer player, BlockPos pos, ResourceLocation shopId, int index, int count) {
        Result where = check(player, pos, shopId);
        if (where != Result.OK) {
            return tell(player, where, null, 0, 0);
        }
        ShopDef def = ShopDefs.get(shopId);
        if (def == null || index < 0 || index >= def.sells().size() || count < 1 || count > MAX_COUNT) {
            return tell(player, Result.INVALID, null, 0, 0);
        }
        ShopDef.Entry entry = def.sells().get(index);
        Item item = ForgeRegistries.ITEMS.getValue(entry.item());
        long price = entry.resolvedPrice();
        if (item == null || price <= 0) {
            return tell(player, Result.INVALID, null, 0, 0);
        }
        ItemStack sample = new ItemStack(item);
        long total = price * count;
        if (total / count != price) {
            return tell(player, Result.INVALID, null, 0, 0);
        }
        if (room(player.getInventory(), sample) < count) {
            return tell(player, Result.NO_ROOM, sample, count, total);
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || !Wallet.pay(player, data, total, "shop buy " + shopId.getPath())) {
            return tell(player, Result.NOT_ENOUGH_MONEY, sample, count, total);
        }
        int left = count;
        while (left > 0) {
            int part = Math.min(left, sample.getMaxStackSize());
            ItemStack stack = new ItemStack(item, part);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
            left -= part;
        }
        player.containerMenu.broadcastChanges();
        player.level().playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 0.35F, 1.3F);
        return tell(player, Result.OK, sample, count, total);
    }

    /** 売る（店が買い取る）。 */
    public static Result sell(ServerPlayer player, BlockPos pos, ResourceLocation shopId, int index, int count) {
        Result where = check(player, pos, shopId);
        if (where != Result.OK) {
            return tellSell(player, where, null, 0, 0);
        }
        ShopDef def = ShopDefs.get(shopId);
        if (def == null || index < 0 || index >= def.buys().size() || count < 1 || count > MAX_COUNT * 36) {
            return tellSell(player, Result.INVALID, null, 0, 0);
        }
        ShopDef.Entry entry = def.buys().get(index);
        Item item = ForgeRegistries.ITEMS.getValue(entry.item());
        long price = entry.resolvedPrice();
        if (item == null || price <= 0) {
            return tellSell(player, Result.INVALID, null, 0, 0);
        }
        ItemStack sample = new ItemStack(item);
        if (count(player.getInventory(), item) < count) {
            return tellSell(player, Result.NOT_HAVE, sample, count, 0);
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return tellSell(player, Result.INVALID, null, 0, 0);
        }
        int left = count;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size() && left > 0; slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (sellable(stack, item)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        long total = price * count;
        Wallet.give(player, data, total, "shop sell " + shopId.getPath());
        player.containerMenu.broadcastChanges();
        player.level().playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.4F, 1.2F);
        return tellSell(player, Result.OK, sample, count, total);
    }

    /** 店のカウンターの近くか、そのカウンターの店か。 */
    private static Result check(ServerPlayer player, BlockPos pos, ResourceLocation shopId) {
        double reach = ServerConfig.SHOP_REACH.get();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > reach * reach) {
            return Result.TOO_FAR;
        }
        if (!player.level().isLoaded(pos)) {
            return Result.TOO_FAR;
        }
        BlockEntity entity = player.level().getBlockEntity(pos);
        if (!(entity instanceof ShopCounterBlockEntity counter) || !shopId.equals(counter.shop())) {
            return Result.NO_SHOP;
        }
        return ShopDefs.get(shopId) == null ? Result.NO_SHOP : Result.OK;
    }

    /** 持ち物に、その品があと何個入るか。 */
    public static int room(Inventory inventory, ItemStack sample) {
        int room = 0;
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (stack.isEmpty()) {
                room += sample.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(stack, sample)) {
                room += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
        }
        return room;
    }

    /** 持ち物（身に着けている物は除く）に、売れる形のその品が何個あるか（名前を付けた品は売らない）。 */
    public static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (sellable(stack, item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean sellable(ItemStack stack, Item item) {
        return !stack.isEmpty() && stack.is(item) && !stack.hasCustomHoverName();
    }

    private static Result tell(ServerPlayer player, Result result, @Nullable ItemStack sample, int count, long total) {
        Component message = switch (result) {
            case OK -> Component.translatable("message.amemaru.shop.bought", sample == null ? "" : sample.getHoverName(), count,
                    KnotTransfer.format(total)).withStyle(ChatFormatting.GOLD);
            case NOT_ENOUGH_MONEY -> Component.translatable("message.amemaru.shop.not_enough", KnotTransfer.format(total))
                    .withStyle(ChatFormatting.RED);
            case NO_ROOM -> Component.translatable("message.amemaru.shop.no_room").withStyle(ChatFormatting.RED);
            case TOO_FAR -> Component.translatable("message.amemaru.shop.too_far").withStyle(ChatFormatting.RED);
            case NO_SHOP -> Component.translatable("message.amemaru.shop.closed").withStyle(ChatFormatting.GRAY);
            default -> Component.translatable("message.amemaru.shop.invalid").withStyle(ChatFormatting.RED);
        };
        send(player, message, result == Result.OK);
        return result;
    }

    /** チャットと、開いている店の画面の下の行に出す。 */
    private static void send(ServerPlayer player, Component message, boolean ok) {
        player.sendSystemMessage(message);
        ModNetwork.sendTo(player, new ShopResultS2C(message, ok));
    }

    private static Result tellSell(ServerPlayer player, Result result, @Nullable ItemStack sample, int count, long total) {
        if (result == Result.OK) {
            send(player, Component.translatable("message.amemaru.shop.sold", sample == null ? "" : sample.getHoverName(),
                    count, KnotTransfer.format(total)).withStyle(ChatFormatting.GOLD), true);
            return result;
        }
        if (result == Result.NOT_HAVE) {
            send(player, Component.translatable("message.amemaru.shop.not_have").withStyle(ChatFormatting.RED), false);
            return result;
        }
        return tell(player, result, sample, count, total);
    }
}
