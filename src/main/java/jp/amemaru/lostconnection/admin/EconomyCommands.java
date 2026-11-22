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
