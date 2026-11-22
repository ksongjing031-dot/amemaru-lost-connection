package jp.amemaru.lostconnection.economy;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.item.ConsumableItem;
import jp.amemaru.lostconnection.item.CrystalFruitItem;
import jp.amemaru.lostconnection.item.MaterialItem;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.MarketPageS2C;
import jp.amemaru.lostconnection.network.s2c.MarketPriceS2C;
import jp.amemaru.lostconnection.network.s2c.MarketResultS2C;
import jp.amemaru.lostconnection.network.s2c.MarketStallsS2C;
import jp.amemaru.lostconnection.network.s2c.OpenMarketS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * フリーマーケット（Phase 0-A 20章・Phase 0-B 9章「フリマで買う」。Phase 6）。
 *
 * <ul>
 *   <li>出品はすべて1つの台帳（{@link MarketData}）。街のどこからでも「市場の手帳」で開く取引所で、さがす・買う・出品・取り消し・売上の受け取り</li>
 *   <li>値段は1個あたり、相場の50〜200%（config）。手数料は出品のとき2%（2日目の露店開きは無料）、売れたとき5%（売上から引く）</li>
 *   <li>出品は1人20件（露店を借りている人は30件）。出品は3日で下がり、品は倉庫の受け取り箱へ戻る【補完】</li>
 *   <li>買う：まだ出品中か・値段が変わっていないか・所持金・受け取り箱の空きを確かめてから、払う・品を受け取り箱へ・売り手の売上に入れる、
 *       を一度に行う（サーバーで1件ずつ順に処理するので、同時に買っても一人だけが買える【確定】）。自分の出品は買えない【確定】</li>
 *   <li>露店：市場監督の小屋で区画を借り、店名を付ける。借りている人の出品が、露店の台に並んで見える。露店で直接買うと、売れたときの手数料が半分</li>
 *   <li>相場の変動：日が変わるとき、その日の取引が5件以上の品だけ、取引の中央値に向けて1日±10%まで動かす（オン・オフはconfig【確定】）</li>
 * </ul>
 */
public final class MarketService {
    private MarketService() {
    }

    /** 1ページの行の数（取引所の画面に収まる数） */
    public static final int PAGE_SIZE = 7;
    /** 検索で送れる品のIDの数の上限 */
    public static final int MAX_SEARCH_IDS = 1024;

    public enum Mode { NOTEBOOK, BOOTH, STALL }

    public enum Category { ALL, WEAPON, ARMOR, ACCESSORY, MATERIAL, CONSUMABLE, OTHER }

    public enum Sort { CHEAP, EXPENSIVE, NEWEST }

    public enum Result {
        OK, CLOSED, NO_ACCESS, TOO_FAR, NOT_FOUND, OWN, PRICE_CHANGED, NOT_ENOUGH, NO_ROOM, UNTRADEABLE, NO_PRICE, OUT_OF_RANGE,
        LIMIT, INVALID, NOT_YOURS, STALL_TAKEN, HAS_STALL, NO_STALL, NOT_YET, STALL_CLOSED, NOTHING
    }

    /** その人がいま使えるもの（手帳を持っているか、小屋の近くか、どの露店の近くか）。 */
    public record Access(boolean exchange, boolean booth, int stall) {
        public static final Access NONE = new Access(false, false, 0);

        public static Access of(ServerPlayer player) {
            boolean notebook = hasNotebook(player);
            Optional<BlockPos> origin = origin(player.server);
            if (origin.isEmpty() || player.level().dimension() != Level.OVERWORLD) {
                return new Access(notebook, false, 0);
            }
            Vec3 rel = player.position().subtract(Vec3.atLowerCornerOf(origin.get()));
            double reach = ServerConfig.MARKET_REACH.get();
            MarketStalls.Box booth = MarketStalls.booth();
            boolean atBooth = booth != null && booth.distanceSqr(rel) <= reach * reach;
            int stall = 0;
            double best = reach * reach;
            for (MarketStalls.Stall s : MarketStalls.all()) {
                double d = s.box().distanceSqr(rel);
                if (d <= best) {
                    best = d;
                    stall = s.num();
                }
            }
            return new Access(notebook || atBooth || stall > 0, atBooth, stall);
        }
    }

    // ---------------------------------------------------------------- 基本

    public static Optional<BlockPos> origin(MinecraftServer server) {
        return CityCommands.builtOrigin(server.overworld());
    }

    public static int today(MinecraftServer server) {
        return ProgressionData.currentDay(server);
    }

    public static boolean isOpen(MinecraftServer server) {
        return today(server) >= ServerConfig.MARKET_OPEN_DAY.get();
    }

    public static boolean hasNotebook(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModItems.MARKET_NOTEBOOK.get())) {
                return true;
            }
        }
        return false;
    }

    /** 相場をまとめる鍵：品のID（アクセサリーは品質の★も。★ごとに相場が違うため）。 */
    public static String priceKey(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String key = id.toString();
        if (stack.getItem() instanceof AccessoryItem) {
            int stars = AccessoryData.storedQuality(stack);
            if (stars > 0) {
                key += "#q" + stars;
            }
        }
        return key;
    }

    /** 今の相場（1個あたり。相場表の基準価格×変動の倍率。0なら値段を決められない）。 */
    public static long reference(MinecraftServer server, ItemStack stack) {
        long base = MarketPrices.base(stack);
        if (base <= 0) {
            return 0;
        }
        double factor = MarketData.get(server).factor(priceKey(stack));
        return Math.max(1L, Math.round(base * factor));
    }

    /** 1個あたりの値段の下限と上限。 */
    public static long[] range(long reference) {
        long min = Math.max(1L, (long) Math.ceil(reference * ServerConfig.MARKET_PRICE_MIN_PERCENT.get() / 100.0));
        long max = Math.max(min, (long) Math.floor(reference * ServerConfig.MARKET_PRICE_MAX_PERCENT.get() / 100.0));
        return new long[]{min, max};
    }

    /**
     * 取引できない理由（言語の鍵。取引できるなら null）。Phase 0-A 20章「取引できる物・できない物」：素材・消耗品・作った装備と
     * アクセサリーは取引できる。結晶果・見習い装備（取引不可の印）・装備したボスのレア装備（本人専用）・クエストの品はできない。
     * 相場表にない品と、このModの品でないもの（相場表にあるものを除く）も出せない。
     */
    @Nullable
    public static String untradeable(ItemStack stack) {
        if (stack.isEmpty()) {
            return "message.amemaru.market.untradeable";
        }
        if (stack.getItem() instanceof CrystalFruitItem) {
            return "message.amemaru.market.untradeable_fruit";
        }
        if (GearData.isBound(stack)) {
            return "message.amemaru.market.untradeable_bound";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (MarketPrices.untradeable(id)) {
            return "message.amemaru.market.untradeable_quest";
        }
        boolean known = MarketPrices.base(id) > 0 || stack.getItem() instanceof jp.amemaru.lostconnection.equipment.GearItem
                || stack.getItem() instanceof AccessoryItem || stack.getItem() instanceof MaterialItem
                || stack.getItem() instanceof ConsumableItem;
        if (!known || MarketPrices.base(stack) <= 0) {
            return "message.amemaru.market.no_price";
        }
        return null;
    }

    public static Category category(ItemStack stack) {
        if (stack.getItem() instanceof AccessoryItem) {
            return Category.ACCESSORY;
        }
        if (stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ShieldItem) {
            return Category.ARMOR;
        }
        if (stack.getItem() instanceof jp.amemaru.lostconnection.equipment.GearItem) {
            return Category.WEAPON;
        }
        if (stack.getItem() instanceof MaterialItem) {
            return Category.MATERIAL;
        }
        if (stack.getItem() instanceof ConsumableItem || stack.isEdible()) {
            return Category.CONSUMABLE;
        }
        return Category.OTHER;
    }

    public static int limit(MinecraftServer server, UUID player) {
        return activeStallOf(server, player) > 0 ? ServerConfig.MARKET_STALL_LISTING_LIMIT.get() : ServerConfig.MARKET_LISTING_LIMIT.get();
    }

    public static boolean freeListingDay(MinecraftServer server) {
        return ServerConfig.MARKET_FREE_LISTING_DAYS.get().contains(today(server));
    }

    /** 露店の区画が使えるか（はじめの12、寄付で増えたあとは20）。 */
    public static boolean stallActive(MinecraftServer server, MarketStalls.Stall stall) {
        return !stall.more() || MarketData.get(server).expanded();
    }

    /** その人が借りている、使える区画（なければ0）。 */
    public static int activeStallOf(MinecraftServer server, UUID player) {
        MarketData data = MarketData.get(server);
        int num = data.stallOf(player);
        if (num <= 0) {
            return 0;
        }
        MarketStalls.Stall stall = MarketStalls.get(num);
        return stall != null && stallActive(server, stall) ? num : 0;
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    // ---------------------------------------------------------------- 画面を開く

    public static void open(ServerPlayer player, Mode mode, int stall) {
        MinecraftServer server = player.server;
        ModNetwork.sendTo(player, MarketStallsS2C.current(server));
        ModNetwork.sendTo(player, new OpenMarketS2C(mode, stall, isOpen(server), ServerConfig.MARKET_OPEN_DAY.get(), today(server)));
        if (mode == Mode.STALL && stall > 0) {
            MarketData data = MarketData.get(server);
            MarketData.StallRent rent = data.stall(stall);
            if (rent != null && !rent.owner.equals(player.getUUID())) {
                rent.visitors++;
                data.setDirty();
            }
        }
    }

    // ---------------------------------------------------------------- 出品

    public static Result list(ServerPlayer player, Access access, int slot, int count, long unitPrice, String expectedItem) {
        MinecraftServer server = player.server;
        if (!isOpen(server)) {
            return tell(player, Result.CLOSED);
        }
        if (!access.exchange()) {
            return tell(player, Result.NO_ACCESS);
        }
        Inventory inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.items.size()) {
            return tell(player, Result.INVALID);
        }
        ItemStack stack = inventory.items.get(slot);
        if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(expectedItem)
                || count < 1 || count > stack.getCount()) {
            return tell(player, Result.INVALID);
        }
        String problem = untradeable(stack);
        if (problem != null) {
            player.sendSystemMessage(Component.translatable(problem).withStyle(ChatFormatting.RED));
            ModNetwork.sendTo(player, new MarketResultS2C(Component.translatable(problem), false));
            return Result.UNTRADEABLE;
        }
        long reference = reference(server, stack);
        if (reference <= 0) {
            return tell(player, Result.NO_PRICE);
        }
        long[] range = range(reference);
        if (unitPrice < range[0] || unitPrice > range[1]) {
            return tell(player, Result.OUT_OF_RANGE, KnotTransfer.format(range[0]), KnotTransfer.format(range[1]));
        }
        MarketData data = MarketData.get(server);
        int limit = limit(server, player.getUUID());
        if (data.countOf(player.getUUID()) >= limit) {
            return tell(player, Result.LIMIT, limit);
        }
        long total = Wallet.saturatedMultiply(unitPrice, count);
        long fee = freeListingDay(server) ? 0 : Wallet.feeOf(total, ServerConfig.MARKET_LISTING_FEE_PERCENT.get());
        PlayerData playerData = PlayerDataCapability.get(player).orElse(null);
        if (playerData == null || !Wallet.pay(player, playerData, fee, "market listing fee")) {
            return tell(player, Result.NOT_ENOUGH, KnotTransfer.format(fee));
        }
        ItemStack listed = stack.split(count);
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
        int day = today(server);
        int days = ServerConfig.MARKET_LISTING_DAYS.get();
        MarketData.Listing listing = data.add(player.getUUID(), name(player), listed, unitPrice, day, days > 0 ? day + days : 0,
                server.overworld().getGameTime());
        data.addHistory(player.getUUID(), new MarketData.History(day, MarketData.Kind.LISTED, listed.copy(), total, fee, ""));
        AmemaruMod.LOGGER.info("[amemaru][market] {} listed #{} {}x{} at {} (fee {})", name(player), listing.id,
                BuiltInRegistries.ITEM.getKey(listed.getItem()), listed.getCount(), unitPrice, fee);
        stallsChanged(server, player.getUUID());
        send(player, Component.translatable(fee > 0 ? "message.amemaru.market.listed" : "message.amemaru.market.listed_free",
                listed.getHoverName(), listed.getCount(), KnotTransfer.format(unitPrice), KnotTransfer.format(fee))
                .withStyle(ChatFormatting.GOLD), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
        return Result.OK;
    }

    // ---------------------------------------------------------------- 買う

    /** 買う。viaStall は露店で直接買うときの区画の番号（0なら取引所）。seenUnitPrice は画面で見ていた1個の値段。 */
    public static Result buy(ServerPlayer player, Access access, long id, long seenUnitPrice, int viaStall) {
        MinecraftServer server = player.server;
        if (!isOpen(server)) {
            return tell(player, Result.CLOSED);
        }
        if (viaStall > 0 ? access.stall() != viaStall : !access.exchange()) {
            return tell(player, viaStall > 0 ? Result.TOO_FAR : Result.NO_ACCESS);
        }
        MarketData data = MarketData.get(server);
        MarketData.Listing listing = data.listing(id);
        if (listing == null) {
            return tell(player, Result.NOT_FOUND);
        }
        if (listing.seller.equals(player.getUUID())) {
            return tell(player, Result.OWN);
        }
        if (listing.unitPrice != seenUnitPrice) {
            return tell(player, Result.PRICE_CHANGED);
        }
        if (viaStall > 0 && activeStallOf(server, listing.seller) != viaStall) {
            return tell(player, Result.INVALID);
        }
        if (StorageService.receivedRoom(server, player.getUUID()) < 1) {
            return tell(player, Result.NO_ROOM);
        }
        long total = listing.total();
        double percent = ServerConfig.MARKET_SALE_FEE_PERCENT.get() * (viaStall > 0 ? ServerConfig.MARKET_STALL_FEE_FACTOR.get() : 1.0);
        long fee = Math.min(total, Wallet.feeOf(total, percent));
        PlayerData buyerData = PlayerDataCapability.get(player).orElse(null);
        if (buyerData == null || !Wallet.payToSeller(player, buyerData, total, fee, listing.sellerName,
                viaStall > 0 ? "market stall buy" : "market buy")) {
            return tell(player, Result.NOT_ENOUGH, KnotTransfer.format(total));
        }
        data.remove(id);
        ItemStack item = listing.item.copy();
        boolean delivered = StorageService.deliver(server, player.getUUID(), name(player), item);
        if (!delivered) {
            // 確かめたあとなので届くはず。届かなければ持ち物へ（それもだめなら足もとへ）
            ItemStack rest = item.copy();
            if (!player.getInventory().add(rest) && !rest.isEmpty()) {
                player.drop(rest, false);
            }
        }
        data.addProceeds(listing.seller, total - fee);
        data.markFirstTrade(listing.seller, player.getUUID());
        int day = today(server);
        long base = MarketPrices.base(listing.item);
        data.recordTrade(priceKey(listing.item), new MarketData.Trade(day, listing.unitPrice, listing.item.getCount(), base));
        data.addHistory(player.getUUID(), new MarketData.History(day, MarketData.Kind.BOUGHT, listing.item.copy(), total, 0,
                listing.sellerName));
        data.addHistory(listing.seller, new MarketData.History(day, MarketData.Kind.SOLD, listing.item.copy(), total - fee, fee,
                name(player)));
        AmemaruMod.LOGGER.info("[amemaru][market] {} bought #{} from {} for {} (fee {}{})", name(player), id, listing.sellerName, total, fee,
                viaStall > 0 ? ", stall " + viaStall : "");
        ServerPlayer seller = server.getPlayerList().getPlayer(listing.seller);
        if (seller != null) {
            seller.sendSystemMessage(Component.translatable("message.amemaru.market.sold", listing.item.getHoverName(),
                    listing.item.getCount(), KnotTransfer.format(total - fee)).withStyle(ChatFormatting.GOLD));
            seller.level().playSound(null, seller.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
        stallsChanged(server, listing.seller);
        send(player, Component.translatable("message.amemaru.market.bought", listing.item.getHoverName(), listing.item.getCount(),
                KnotTransfer.format(total)).withStyle(ChatFormatting.GOLD), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.4F, 1.3F);
        return Result.OK;
    }

    // ---------------------------------------------------------------- 取り消し・売上

    /** 取り消す：品は持ち物へ（入らない分は受け取り箱へ）。出品の手数料は戻らない。 */
    public static Result cancel(ServerPlayer player, Access access, long id) {
        MinecraftServer server = player.server;
        if (!access.exchange()) {
            return tell(player, Result.NO_ACCESS);
        }
        MarketData data = MarketData.get(server);
        MarketData.Listing listing = data.listing(id);
        if (listing == null) {
            return tell(player, Result.NOT_FOUND);
        }
        if (!listing.seller.equals(player.getUUID())) {
            return tell(player, Result.NOT_YOURS);
        }
        boolean fits = ShopService.room(player.getInventory(), listing.item) >= listing.item.getCount();
        if (!fits && StorageService.receivedRoom(server, player.getUUID()) < 1) {
            return tell(player, Result.NO_ROOM);
        }
        data.remove(id);
        ItemStack back = listing.item.copy();
        if (!player.getInventory().add(back) && !back.isEmpty()) {
            StorageService.deliver(server, player.getUUID(), name(player), back);
        }
        player.containerMenu.broadcastChanges();
        data.addHistory(player.getUUID(), new MarketData.History(today(server), MarketData.Kind.CANCELLED, listing.item.copy(), 0, 0, ""));
        stallsChanged(server, player.getUUID());
        send(player, Component.translatable("message.amemaru.market.cancelled", listing.item.getHoverName(), listing.item.getCount())
                .withStyle(ChatFormatting.GOLD), true);
        return Result.OK;
    }

    /** 売上を受け取る。 */
    public static Result collect(ServerPlayer player, Access access) {
        MinecraftServer server = player.server;
        if (!access.exchange()) {
            return tell(player, Result.NO_ACCESS);
        }
        MarketData data = MarketData.get(server);
        PlayerData playerData = PlayerDataCapability.get(player).orElse(null);
        if (playerData == null) {
            return tell(player, Result.INVALID);
        }
        long amount = data.takeProceeds(player.getUUID());
        if (amount <= 0) {
            return tell(player, Result.NOTHING);
        }
        Wallet.receive(player, playerData, amount, "market proceeds");
        data.addHistory(player.getUUID(), new MarketData.History(today(server), MarketData.Kind.COLLECTED, ItemStack.EMPTY, amount, 0, ""));
        send(player, Component.translatable("message.amemaru.market.collected", KnotTransfer.format(amount)).withStyle(ChatFormatting.GOLD),
                true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.1F);
        return Result.OK;
    }

    // ---------------------------------------------------------------- 露店

    public static Result rent(ServerPlayer player, Access access, int num, String rawName) {
        MinecraftServer server = player.server;
        if (!isOpen(server)) {
            return tell(player, Result.CLOSED);
        }
        if (!access.booth()) {
            return tell(player, Result.TOO_FAR);
        }
        MarketStalls.Stall stall = MarketStalls.get(num);
        if (stall == null) {
            return tell(player, Result.INVALID);
        }
        if (!stallActive(server, stall)) {
            return tell(player, Result.STALL_CLOSED);
        }
        MarketData data = MarketData.get(server);
        MarketData.StallRent current = data.stall(num);
        if (current != null) {
            return tell(player, current.owner.equals(player.getUUID()) ? Result.HAS_STALL : Result.STALL_TAKEN);
        }
        if (data.stallOf(player.getUUID()) > 0) {
            return tell(player, Result.HAS_STALL);
        }
        long price = ServerConfig.MARKET_STALL_RENT.get();
        PlayerData playerData = PlayerDataCapability.get(player).orElse(null);
        if (playerData == null || !Wallet.pay(player, playerData, price, "market stall rent")) {
            return tell(player, Result.NOT_ENOUGH, KnotTransfer.format(price));
        }
        int day = today(server);
        String shown = cleanName(rawName, name(player));
        data.setStall(num, new MarketData.StallRent(player.getUUID(), name(player), shown, day + ServerConfig.MARKET_STALL_DAYS.get() - 1));
        data.addHistory(player.getUUID(), new MarketData.History(day, MarketData.Kind.RENTED, ItemStack.EMPTY, price, 0,
                Integer.toString(num)));
        broadcastStalls(server);
        send(player, Component.translatable("message.amemaru.market.rented", num, shown,
                day + ServerConfig.MARKET_STALL_DAYS.get() - 1, KnotTransfer.format(price)).withStyle(ChatFormatting.GOLD), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.5F, 1.1F);
        return Result.OK;
    }

    /** 借りている日を延ばす（残りが1回分より少ないとき）。 */
    public static Result extend(ServerPlayer player, Access access) {
        MinecraftServer server = player.server;
        if (!access.booth()) {
            return tell(player, Result.TOO_FAR);
        }
        MarketData data = MarketData.get(server);
        int num = data.stallOf(player.getUUID());
        MarketData.StallRent rent = num > 0 ? data.stall(num) : null;
        if (rent == null) {
            return tell(player, Result.NO_STALL);
        }
        int days = ServerConfig.MARKET_STALL_DAYS.get();
        int day = today(server);
        if (rent.lastDay - day + 1 >= days) {
            return tell(player, Result.NOT_YET, rent.lastDay);
        }
        long price = ServerConfig.MARKET_STALL_RENT.get();
        PlayerData playerData = PlayerDataCapability.get(player).orElse(null);
        if (playerData == null || !Wallet.pay(player, playerData, price, "market stall extend")) {
            return tell(player, Result.NOT_ENOUGH, KnotTransfer.format(price));
        }
        rent.lastDay = Math.max(rent.lastDay, day - 1) + days;
        data.setDirty();
        data.addHistory(player.getUUID(), new MarketData.History(day, MarketData.Kind.RENTED, ItemStack.EMPTY, price, 0,
                Integer.toString(num)));
        broadcastStalls(server);
        send(player, Component.translatable("message.amemaru.market.extended", num, rent.lastDay, KnotTransfer.format(price))
                .withStyle(ChatFormatting.GOLD), true);
        return Result.OK;
    }

    /** 返す（借りた代金は戻らない）。 */
    public static Result release(ServerPlayer player, Access access) {
        MinecraftServer server = player.server;
        if (!access.booth()) {
            return tell(player, Result.TOO_FAR);
        }
        MarketData data = MarketData.get(server);
        int num = data.stallOf(player.getUUID());
        if (num <= 0) {
            return tell(player, Result.NO_STALL);
        }
        data.setStall(num, null);
        broadcastStalls(server);
        send(player, Component.translatable("message.amemaru.market.released", num).withStyle(ChatFormatting.GOLD), true);
        return Result.OK;
    }

    public static Result rename(ServerPlayer player, Access access, String rawName) {
        MinecraftServer server = player.server;
        if (!access.booth() && access.stall() <= 0) {
            return tell(player, Result.TOO_FAR);
        }
        MarketData data = MarketData.get(server);
        int num = data.stallOf(player.getUUID());
        MarketData.StallRent rent = num > 0 ? data.stall(num) : null;
        if (rent == null) {
            return tell(player, Result.NO_STALL);
        }
        rent.name = cleanName(rawName, name(player));
        data.setDirty();
        broadcastStalls(server);
        send(player, Component.translatable("message.amemaru.market.renamed", num, rent.name).withStyle(ChatFormatting.GOLD), true);
        return Result.OK;
    }

    /** 店名を整える：飾りの記号を消し、長さを切り、NGワードを＊で伏せる。空なら「〜の露店」。 */
    public static String cleanName(String raw, String owner) {
        String text = raw == null ? "" : raw.replace("§", "").replaceAll("[\\p{Cntrl}]", "").strip();
        int max = ServerConfig.MARKET_STALL_NAME_MAX.get();
        if (text.codePointCount(0, text.length()) > max) {
            text = text.substring(0, text.offsetByCodePoints(0, max));
        }
        for (String word : ServerConfig.MARKET_NG_WORDS.get()) {
            if (word != null && !word.isBlank()) {
                text = text.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), "＊".repeat(word.codePointCount(0, word.length())));
            }
        }
        return text.isBlank() ? owner + "の露店" : text;
    }

    // ---------------------------------------------------------------- 日が変わる

    /** 進行日が変わったとき：相場の変動（終わった日の取引から）・出品と露店の期限。 */
    public static void onDayChanged(MinecraftServer server, int before, int now) {
        MarketData data = MarketData.get(server);
        if (now > before) {
            for (int day = before; day < now; day++) {
                drift(server, data, day);
            }
        }
        expire(server, data, now);
        broadcastStalls(server);
    }

    /** 相場の変動：その日の取引が決まった数以上の品だけ、値段÷基準価格の中央値に向けて、倍率を1日の幅まで動かす。 */
    public static void drift(MinecraftServer server, MarketData data, int day) {
        if (!ServerConfig.MARKET_PRICE_DRIFT.get()) {
            return;
        }
        double maxMove = ServerConfig.MARKET_DRIFT_MAX_PERCENT.get() / 100.0;
        int minTrades = ServerConfig.MARKET_DRIFT_MIN_TRADES.get();
        for (var entry : new ArrayList<>(data.allTrades().entrySet())) {
            List<Double> ratios = new ArrayList<>();
            for (MarketData.Trade trade : entry.getValue()) {
                if (trade.day() == day && trade.base() > 0) {
                    ratios.add((double) trade.unitPrice() / trade.base());
                }
            }
            if (ratios.size() < minTrades) {
                continue;
            }
            ratios.sort(Double::compare);
            int n = ratios.size();
            double median = n % 2 == 1 ? ratios.get(n / 2) : (ratios.get(n / 2 - 1) + ratios.get(n / 2)) / 2.0;
            double factor = data.factor(entry.getKey());
            double next = Math.max(factor * (1.0 - maxMove), Math.min(factor * (1.0 + maxMove), median));
            if (Math.abs(next - factor) < 1.0E-9) {
                continue;
            }
            data.setFactor(entry.getKey(), next);
            boolean sharp = maxMove > 0 && Math.abs(next - factor) >= factor * maxMove - 1.0E-9;
            AmemaruMod.LOGGER.info("[amemaru][market] price drift {} day {}: x{} -> x{} (median x{}, {} trades)", entry.getKey(), day,
                    String.format(Locale.ROOT, "%.3f", factor), String.format(Locale.ROOT, "%.3f", next),
                    String.format(Locale.ROOT, "%.3f", median), n);
            if (sharp) {
                notifyAdmins(server, Component.translatable("message.amemaru.market.sharp", entry.getKey(),
                        String.format(Locale.ROOT, "%+.0f", (next / factor - 1.0) * 100.0), n));
            }
        }
    }

    /** 期限：下がる日の来た出品は、品を出品者の受け取り箱へ戻す（いっぱいなら翌日に回す）。借りる日の過ぎた露店は空ける。 */
    public static void expire(MinecraftServer server, MarketData data, int now) {
        for (MarketData.Listing listing : data.listings()) {
            if (listing.expiresDay > 0 && listing.expiresDay <= now) {
                if (StorageService.deliver(server, listing.seller, listing.sellerName, listing.item.copy())) {
                    data.remove(listing.id);
                    data.addHistory(listing.seller, new MarketData.History(now, MarketData.Kind.EXPIRED, listing.item.copy(), 0, 0, ""));
                    ServerPlayer seller = server.getPlayerList().getPlayer(listing.seller);
                    if (seller != null) {
                        seller.sendSystemMessage(Component.translatable("message.amemaru.market.expired", listing.item.getHoverName(),
                                listing.item.getCount()).withStyle(ChatFormatting.YELLOW));
                    }
                } else {
                    AmemaruMod.LOGGER.warn("[amemaru][market] could not return expired #{} to {} (received box full)", listing.id,
                            listing.sellerName);
                }
            }
        }
        for (var entry : new ArrayList<>(data.stalls().entrySet())) {
            if (entry.getValue().lastDay < now) {
                data.setStall(entry.getKey(), null);
                ServerPlayer owner = server.getPlayerList().getPlayer(entry.getValue().owner);
                if (owner != null) {
                    owner.sendSystemMessage(Component.translatable("message.amemaru.market.stall_ended", entry.getKey())
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    private static void notifyAdmins(MinecraftServer server, Component message) {
        AmemaruMod.LOGGER.warn("[amemaru][market] {}", message.getString());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(message.copy().withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    // ---------------------------------------------------------------- 一覧

    /** 取引所の一覧の1ページ。ids は名前の検索に合う品（null なら名前で絞らない）。stall は露店の画面（0なら全部）。 */
    public static MarketPageS2C page(MinecraftServer server, UUID viewer, Category category, Sort sort, int page,
                                     @Nullable Set<ResourceLocation> ids, int stall) {
        MarketData data = MarketData.get(server);
        UUID stallOwner = null;
        if (stall > 0) {
            MarketData.StallRent rent = data.stall(stall);
            MarketStalls.Stall def = MarketStalls.get(stall);
            if (rent == null || def == null || !stallActive(server, def)) {
                return new MarketPageS2C(stall, 0, 1, 0, List.of());
            }
            stallOwner = rent.owner;
        }
        List<MarketData.Listing> list = new ArrayList<>();
        for (MarketData.Listing listing : data.listings()) {
            if (stallOwner != null && !listing.seller.equals(stallOwner)) {
                continue;
            }
            if (category != Category.ALL && category(listing.item) != category) {
                continue;
            }
            if (ids != null && !ids.contains(BuiltInRegistries.ITEM.getKey(listing.item.getItem()))) {
                continue;
            }
            list.add(listing);
        }
        Comparator<MarketData.Listing> order = switch (sort) {
            case CHEAP -> Comparator.<MarketData.Listing>comparingLong(l -> l.unitPrice).thenComparingLong(l -> l.id);
            case EXPENSIVE -> Comparator.<MarketData.Listing>comparingLong(l -> -l.unitPrice).thenComparingLong(l -> l.id);
            case NEWEST -> Comparator.<MarketData.Listing>comparingLong(l -> -l.id);
        };
        list.sort(order);
        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.max(0, Math.min(page, pages - 1));
        List<MarketPageS2C.Row> rows = new ArrayList<>();
        for (int i = p * PAGE_SIZE; i < Math.min(list.size(), (p + 1) * PAGE_SIZE); i++) {
            rows.add(row(server, data, list.get(i), viewer));
        }
        return new MarketPageS2C(stall, p, pages, list.size(), rows);
    }

    static MarketPageS2C.Row row(MinecraftServer server, MarketData data, MarketData.Listing listing, UUID viewer) {
        return new MarketPageS2C.Row(listing.id, listing.item.copy(), listing.unitPrice, listing.sellerName,
                activeStallOf(server, listing.seller), listing.seller.equals(viewer), reference(server, listing.item),
                recent(data, priceKey(listing.item), 3), listing.expiresDay);
    }

    /** 自分の出品（新しい順）・まだ受け取っていない売上・出品の上限・最近の履歴。 */
    public static jp.amemaru.lostconnection.network.s2c.MarketMineS2C mine(MinecraftServer server, UUID player) {
        MarketData data = MarketData.get(server);
        List<MarketPageS2C.Row> rows = new ArrayList<>();
        List<MarketData.Listing> own = data.listingsOf(player);
        for (int i = own.size() - 1; i >= 0; i--) {
            rows.add(row(server, data, own.get(i), player));
        }
        List<jp.amemaru.lostconnection.network.s2c.MarketMineS2C.Line> lines = new ArrayList<>();
        for (MarketData.History h : data.history(player)) {
            lines.add(new jp.amemaru.lostconnection.network.s2c.MarketMineS2C.Line(h.day(), h.kind().name(), h.item().copy(), h.amount(),
                    h.fee(), h.other()));
            if (lines.size() >= 30) {
                break;
            }
        }
        return new jp.amemaru.lostconnection.network.s2c.MarketMineS2C(rows, data.proceeds(player), limit(server, player), lines);
    }

    /** 最近の取引の1個の値段（新しい順に max 件）。 */
    public static long[] recent(MarketData data, String key, int max) {
        List<MarketData.Trade> trades = data.trades(key);
        long[] out = new long[Math.min(max, trades.size())];
        for (int i = 0; i < out.length; i++) {
            out[i] = trades.get(i).unitPrice();
        }
        return out;
    }

    /** 出品の画面：持ち物のその枠の品の、相場・値段の幅・手数料。 */
    public static MarketPriceS2C priceInfo(ServerPlayer player, int slot) {
        MinecraftServer server = player.server;
        Inventory inventory = player.getInventory();
        ItemStack stack = slot >= 0 && slot < inventory.items.size() ? inventory.items.get(slot) : ItemStack.EMPTY;
        String problem = untradeable(stack);
        long reference = problem == null ? reference(server, stack) : 0;
        long[] range = reference > 0 ? range(reference) : new long[]{0, 0};
        MarketData data = MarketData.get(server);
        boolean free = freeListingDay(server);
        return new MarketPriceS2C(slot, problem == null ? "" : problem, reference, range[0], range[1],
                free ? 0.0 : ServerConfig.MARKET_LISTING_FEE_PERCENT.get(), ServerConfig.MARKET_SALE_FEE_PERCENT.get(), free,
                stack.isEmpty() ? new long[0] : recent(data, priceKey(stack), 5), data.countOf(player.getUUID()),
                limit(server, player.getUUID()));
    }

    // ---------------------------------------------------------------- 露店の見た目（全員へ）

    /** 露店の借り手の出品が変わったら、全員に送り直す（台に並ぶ品）。 */
    static void stallsChanged(MinecraftServer server, UUID seller) {
        if (MarketData.get(server).stallOf(seller) > 0) {
            broadcastStalls(server);
        }
    }

    public static void broadcastStalls(MinecraftServer server) {
        if (server.getPlayerList() != null) {
            ModNetwork.sendToAll(MarketStallsS2C.current(server));
        }
    }

    // ---------------------------------------------------------------- 知らせ

    private static void send(ServerPlayer player, Component message, boolean ok) {
        player.sendSystemMessage(message);
        ModNetwork.sendTo(player, new MarketResultS2C(message, ok));
    }

    private static Result tell(ServerPlayer player, Result result, Object... args) {
        Component message = Component.translatable("message.amemaru.market." + result.name().toLowerCase(Locale.ROOT), args)
                .withStyle(result == Result.NOTHING ? ChatFormatting.GRAY : ChatFormatting.RED);
        send(player, message, false);
        return result;
    }
}
