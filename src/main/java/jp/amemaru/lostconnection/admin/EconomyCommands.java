package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import jp.amemaru.lostconnection.economy.EconomyLog;
import jp.amemaru.lostconnection.economy.KnotTransfer;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 経済の確認（Phase 6。Phase 0-A 20章「通貨の総量と回収量を毎日記録し、管理者が確認できるようにする」）。
 *
 * <ul>
 *   <li>{@code /amemaru admin economy}：世界にあるノットの合計と、今日（進行日）の発行・回収・移動</li>
 *   <li>{@code /amemaru admin economy days}：記録のある日ごとの発行・回収・移動</li>
 *   <li>{@code /amemaru admin economy recent [件数]}：直近の出し入れ（初期値10件、50件まで）</li>
 *   <li>{@code /amemaru admin shop list}・{@code shop set <場所> <店>}：店の一覧と、呼び鈴への店の割り当て</li>
 *   <li>{@code /amemaru admin market …}：フリーマーケットの台帳・出品・露店・相場の変動（{@link #marketCommand()}）</li>
 * </ul>
 */
public final class EconomyCommands {
    private EconomyCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> economyCommand() {
        return Commands.literal("economy")
                .executes(context -> summary(context.getSource()))
                .then(Commands.literal("days").executes(context -> days(context.getSource())))
                .then(Commands.literal("recent")
                        .executes(context -> recent(context.getSource(), 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(context -> recent(context.getSource(), IntegerArgumentType.getInteger(context, "count")))));
    }

    /**
     * 個人倉庫の確認と容量の変更（Phase 0-B 10章「倉庫の確認・復旧・容量の変更」）。オフラインの人も、名前で探す
     * （倉庫を一度でも開いた人）。
     */
    public static LiteralArgumentBuilder<CommandSourceStack> storageCommand() {
        return Commands.literal("storage")
                .then(Commands.literal("list")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(context -> storageList(context.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("pages")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("pages", IntegerArgumentType.integer(0, 20))
                                        .executes(context -> storagePages(context.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
                                                IntegerArgumentType.getInteger(context, "pages"))))));
    }

    /**
     * 店（Phase 6）：{@code shop list} で読み込んだ店の一覧、{@code shop set <場所> <店>} で、その場所の呼び鈴に店を割り当てる
     * （ゲームの中で呼び鈴を置き直したときに使う。書き出しの直しにも、割り当てが入る）。
     */
    public static LiteralArgumentBuilder<CommandSourceStack> shopCommand() {
        return Commands.literal("shop")
                .then(Commands.literal("list").executes(context -> shopList(context.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .then(Commands.argument("shop", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(
                                                jp.amemaru.lostconnection.economy.ShopDefs.all().keySet(), builder))
                                        .executes(context -> shopSet(context.getSource(),
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "shop"))))));
    }

    private static int shopList(CommandSourceStack source) {
        java.util.Map<net.minecraft.resources.ResourceLocation, jp.amemaru.lostconnection.economy.ShopDef> shops =
                jp.amemaru.lostconnection.economy.ShopDefs.all();
        if (shops.isEmpty()) {
            source.sendFailure(Component.translatable("commands.amemaru.shop.none"));
            return 0;
        }
        for (jp.amemaru.lostconnection.economy.ShopDef def : shops.values()) {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.shop.entry", def.id().toString(), def.name(),
                    def.sells().size(), def.buys().size()), false);
        }
        return shops.size();
    }

    private static int shopSet(CommandSourceStack source, net.minecraft.core.BlockPos pos, net.minecraft.resources.ResourceLocation shop) {
        if (jp.amemaru.lostconnection.economy.ShopDefs.get(shop) == null) {
            source.sendFailure(Component.translatable("commands.amemaru.shop.unknown", shop.toString()));
            return 0;
        }
        if (!(source.getLevel().getBlockEntity(pos) instanceof jp.amemaru.lostconnection.block.ShopCounterBlockEntity counter)) {
            source.sendFailure(Component.translatable("commands.amemaru.shop.not_counter", pos.toShortString()));
            return 0;
        }
        counter.setShop(shop);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.shop.set", pos.toShortString(), shop.toString()), true);
        return 1;
    }

    /**
     * フリーマーケット（Phase 6）：{@code market} 台帳のまとめ、{@code market listings [名前]} 出品の一覧、{@code market remove <番号>}
     * 出品を下げる（品は出品者の受け取り箱へ）、{@code market stall list|free <区画>|hide <区画>|show <区画>}、
     * {@code market expand on|off}（寄付で露店の区画が20に増えたか。寄付の仕組みができるまでは手で）、{@code market factors [reset]}（相場の変動）。
     */
    public static LiteralArgumentBuilder<CommandSourceStack> marketCommand() {
        return Commands.literal("market")
                .executes(context -> marketSummary(context.getSource()))
                .then(Commands.literal("listings")
                        .executes(context -> marketListings(context.getSource(), null))
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(context -> marketListings(context.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                                .executes(context -> marketRemove(context.getSource(),
                                        com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "id")))))
                .then(Commands.literal("stall")
                        .then(Commands.literal("list").executes(context -> stallList(context.getSource())))
                        .then(Commands.literal("free").then(Commands.argument("num", IntegerArgumentType.integer(1, 99))
                                .executes(context -> stallFree(context.getSource(), IntegerArgumentType.getInteger(context, "num")))))
                        .then(Commands.literal("hide").then(Commands.argument("num", IntegerArgumentType.integer(1, 99))
                                .executes(context -> stallHide(context.getSource(), IntegerArgumentType.getInteger(context, "num"), true))))
                        .then(Commands.literal("show").then(Commands.argument("num", IntegerArgumentType.integer(1, 99))
                                .executes(context -> stallHide(context.getSource(), IntegerArgumentType.getInteger(context, "num"), false)))))
                .then(Commands.literal("expand")
                        .then(Commands.literal("on").executes(context -> marketExpand(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> marketExpand(context.getSource(), false))))
                .then(Commands.literal("factors")
                        .executes(context -> marketFactors(context.getSource(), false))
                        .then(Commands.literal("reset").executes(context -> marketFactors(context.getSource(), true))));
    }

    private static int marketSummary(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        jp.amemaru.lostconnection.economy.MarketData data = jp.amemaru.lostconnection.economy.MarketData.get(server);
        int rented = data.stalls().size();
        source.sendSuccess(() -> Component.translatable("commands.amemaru.market.summary",
                jp.amemaru.lostconnection.economy.MarketService.isOpen(server)
                        ? Component.translatable("commands.amemaru.market.open") : Component.translatable("commands.amemaru.market.not_open"),
                data.listings().size(), KnotTransfer.format(data.pendingProceeds()), rented,
                data.expanded() ? 20 : 12, jp.amemaru.lostconnection.core.config.ServerConfig.MARKET_PRICE_DRIFT.get()
                        ? Component.translatable("commands.amemaru.market.drift_on") : Component.translatable("commands.amemaru.market.drift_off")),
                false);
        return data.listings().size();
    }

    private static int marketListings(CommandSourceStack source, @javax.annotation.Nullable String name) {
        jp.amemaru.lostconnection.economy.MarketData data = jp.amemaru.lostconnection.economy.MarketData.get(source.getServer());
        int shown = 0;
        for (jp.amemaru.lostconnection.economy.MarketData.Listing listing : data.listings()) {
            if (name != null && !listing.sellerName.equalsIgnoreCase(name)) {
                continue;
            }
            shown++;
            if (shown > 50) {
                continue;
            }
            source.sendSuccess(() -> Component.translatable("commands.amemaru.market.listing", listing.id, listing.sellerName,
                    listing.item.getHoverName(), listing.item.getCount(), KnotTransfer.format(listing.unitPrice),
                    listing.expiresDay), false);
        }
        int total = shown;
        if (total == 0) {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.market.no_listings"), false);
        } else if (total > 50) {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.market.more", total - 50), false);
        }
        return total;
    }

    private static int marketRemove(CommandSourceStack source, long id) {
        MinecraftServer server = source.getServer();
        jp.amemaru.lostconnection.economy.MarketData data = jp.amemaru.lostconnection.economy.MarketData.get(server);
        jp.amemaru.lostconnection.economy.MarketData.Listing listing = data.listing(id);
        if (listing == null) {
            source.sendFailure(Component.translatable("commands.amemaru.market.no_listing", id));
            return 0;
        }
        if (!jp.amemaru.lostconnection.economy.StorageService.deliver(server, listing.seller, listing.sellerName, listing.item.copy())) {
            source.sendFailure(Component.translatable("commands.amemaru.market.box_full", listing.sellerName));
            return 0;
        }
        data.remove(id);
        jp.amemaru.lostconnection.economy.MarketService.broadcastStalls(server);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.market.removed", id, listing.sellerName), true);
        return 1;
    }

    private static int stallList(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        jp.amemaru.lostconnection.economy.MarketData data = jp.amemaru.lostconnection.economy.MarketData.get(server);
        for (jp.amemaru.lostconnection.economy.MarketStalls.Stall stall : jp.amemaru.lostconnection.economy.MarketStalls.all()) {
            jp.amemaru.lostconnection.economy.MarketData.StallRent rent = data.stall(stall.num());
            boolean active = jp.amemaru.lostconnection.economy.MarketService.stallActive(server, stall);
            source.sendSuccess(() -> rent == null
                    ? Component.translatable(active ? "commands.amemaru.market.stall_vacant" : "commands.amemaru.market.stall_closed",
                    stall.num(), stall.title())
                    : Component.translatable("commands.amemaru.market.stall_rented", stall.num(), rent.name, rent.ownerName, rent.lastDay,
                    rent.visitors, rent.hidden ? Component.translatable("commands.amemaru.market.hidden") : Component.empty()), false);
        }
        return data.stalls().size();
    }

    private static int stallFree(CommandSourceStack source, int num) {
        jp.amemaru.lostconnection.economy.MarketData data = jp.amemaru.lostconnection.economy.MarketData.get(source.getServer());
        if (data.stall(num) == null) {
            source.sendFailure(Component.translatable("commands.amemaru.market.stall_none", num));
            return 0;
        }
        data.setStall(num, null);
        jp.amemaru.lostconnection.economy.MarketService.broadcastStalls(source.getServer());
        source.sendSuccess(() -> Component.translatable("commands.amemaru.market.stall_freed", num), true);
        return 1;
    }

    private static int stallHide(CommandSourceStack source, int num, boolean hide) {
        jp.amemaru.lostconnection.economy.MarketData data = jp.amemaru.lostconnection.economy.MarketData.get(source.getServer());
        jp.amemaru.lostconnection.economy.MarketData.StallRent rent = data.stall(num);
        if (rent == null) {
            source.sendFailure(Component.translatable("commands.amemaru.market.stall_none", num));
            return 0;
        }
        rent.hidden = hide;
        data.setDirty();
        jp.amemaru.lostconnection.economy.MarketService.broadcastStalls(source.getServer());
        source.sendSuccess(() -> Component.translatable(hide ? "commands.amemaru.market.stall_hidden" : "commands.amemaru.market.stall_shown", num),
                true);
        return 1;
    }

    private static int marketExpand(CommandSourceStack source, boolean on) {
        jp.amemaru.lostconnection.economy.MarketData data = jp.amemaru.lostconnection.economy.MarketData.get(source.getServer());
        data.setExpanded(on);
        jp.amemaru.lostconnection.economy.MarketService.broadcastStalls(source.getServer());
        source.sendSuccess(() -> Component.translatable(on ? "commands.amemaru.market.expanded" : "commands.amemaru.market.unexpanded"), true);
        return 1;
    }

    private static int marketFactors(CommandSourceStack source, boolean reset) {
        jp.amemaru.lostconnection.economy.MarketData data = jp.amemaru.lostconnection.economy.MarketData.get(source.getServer());
        java.util.Map<String, Double> factors = new java.util.TreeMap<>(data.factors());
        if (reset) {
            for (String key : factors.keySet()) {
                data.setFactor(key, 1.0);
            }
            source.sendSuccess(() -> Component.translatable("commands.amemaru.market.factors_reset", factors.size()), true);
            return factors.size();
        }
        if (factors.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.market.factors_none"), false);
        }
        for (java.util.Map.Entry<String, Double> entry : factors.entrySet()) {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.market.factor", entry.getKey(),
                    String.format(Locale.ROOT, "%.0f", entry.getValue() * 100.0)), false);
        }
        return factors.size();
    }

    @javax.annotation.Nullable
    private static java.util.UUID ownerByName(CommandSourceStack source, String name) {
        net.minecraft.server.level.ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        return jp.amemaru.lostconnection.economy.StorageData.get(source.getServer()).findByName(name);
    }

    private static int storageList(CommandSourceStack source, String name) {
        java.util.UUID owner = ownerByName(source, name);
        jp.amemaru.lostconnection.economy.StorageData.Box box = owner == null ? null
                : jp.amemaru.lostconnection.economy.StorageData.get(source.getServer()).peek(owner);
        if (box == null) {
            source.sendFailure(Component.translatable("commands.amemaru.storage.none", name));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.amemaru.storage.summary", name, box.pages(), box.usedSlots(),
                box.capacity(), box.receivedCount()), false);
        java.util.List<net.minecraft.world.item.ItemStack> items = box.items();
        for (int i = 0; i < items.size(); i++) {
            net.minecraft.world.item.ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                int slot = i;
                source.sendSuccess(() -> Component.translatable("commands.amemaru.storage.item", slot / 27 + 1, slot % 27 + 1,
                        stack.getHoverName(), stack.getCount()), false);
            }
        }
        for (net.minecraft.world.item.ItemStack stack : box.received()) {
            if (!stack.isEmpty()) {
                source.sendSuccess(() -> Component.translatable("commands.amemaru.storage.received", stack.getHoverName(), stack.getCount()), false);
            }
        }
        return box.usedSlots();
    }

    private static int storagePages(CommandSourceStack source, String name, int pages) {
        java.util.UUID owner = ownerByName(source, name);
        if (owner == null) {
            source.sendFailure(Component.translatable("commands.amemaru.storage.none", name));
            return 0;
        }
        jp.amemaru.lostconnection.economy.StorageData storage = jp.amemaru.lostconnection.economy.StorageData.get(source.getServer());
        jp.amemaru.lostconnection.economy.StorageData.Box box = storage.box(owner);
        if (box.name().isEmpty()) {
            box.setName(name);
        }
        box.setPages(pages);
        storage.setDirty();
        source.sendSuccess(() -> Component.translatable("commands.amemaru.storage.pages", name, box.pages(), box.capacity()), true);
        return box.pages();
    }

    private static int summary(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        EconomyLog log = EconomyLog.get(server);
        int day = ProgressionData.currentDay(server);
        EconomyLog.DayTotal total = log.day(day);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.economy.summary", KnotTransfer.format(log.supply()), day,
                KnotTransfer.format(total.issued()), KnotTransfer.format(total.collected()), KnotTransfer.format(total.moved()),
                total.count()), false);
        return 1;
    }

    private static int days(CommandSourceStack source) {
        EconomyLog log = EconomyLog.get(source.getServer());
        List<Integer> days = log.recordedDays();
        if (days.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.economy.none"), false);
            return 0;
        }
        for (int day : days) {
            EconomyLog.DayTotal total = log.day(day);
            source.sendSuccess(() -> Component.translatable("commands.amemaru.economy.day", day, KnotTransfer.format(total.issued()),
                    KnotTransfer.format(total.collected()), KnotTransfer.format(total.moved()), total.count()), false);
        }
        return days.size();
    }

    private static int recent(CommandSourceStack source, int count) {
        List<EconomyLog.Entry> entries = EconomyLog.get(source.getServer()).recent(count);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.economy.none"), false);
            return 0;
        }
        SimpleDateFormat time = new SimpleDateFormat("MM/dd HH:mm", Locale.ROOT);
        for (EconomyLog.Entry entry : entries) {
            String who = switch (entry.flow()) {
                case ISSUE -> "→ " + entry.to();
                case COLLECT -> entry.from() + " →";
                case MOVE -> entry.from() + " → " + entry.to();
            };
            Component flow = Component.translatable("commands.amemaru.economy.flow." + entry.flow().name().toLowerCase(Locale.ROOT));
            source.sendSuccess(() -> Component.translatable("commands.amemaru.economy.entry", time.format(new Date(entry.millis())),
                    flow, KnotTransfer.format(entry.amount()), who, entry.reason()), false);
        }
        return entries.size();
    }
}
