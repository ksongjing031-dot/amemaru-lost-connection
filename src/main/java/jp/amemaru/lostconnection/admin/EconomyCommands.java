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
