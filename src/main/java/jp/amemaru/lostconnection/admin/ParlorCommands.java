package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import jp.amemaru.lostconnection.casino.parlor.ParlorService;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/**
 * 管理者のコマンド：カジノの地下遊技場（11月27日【補完】）。{@code casino machine <台番号> …}・{@code casino balls|medals <人> <数>}・
 * {@code casino parlor today}。
 */
public final class ParlorCommands {
    private ParlorCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> machine() {
        return Commands.literal("machine")
                .then(Commands.argument("no", IntegerArgumentType.integer(1, 9999))
                        .then(Commands.literal("open")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(c -> {
                                            ParlorService.open(EntityArgument.getPlayer(c, "target"), IntegerArgumentType.getInteger(c, "no"), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("info").executes(c -> {
                            List<String> lines = ParlorService.describe(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "no"));
                            if (lines.isEmpty()) {
                                c.getSource().sendFailure(Component.translatable("command.amemaru.parlor.no_machine",
                                        IntegerArgumentType.getInteger(c, "no")));
                                return 0;
                            }
                            for (String line : lines) {
                                c.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("setting")
                                .then(Commands.argument("setting", IntegerArgumentType.integer(1, 6))
                                        .executes(c -> setting(c.getSource(), IntegerArgumentType.getInteger(c, "no"),
                                                IntegerArgumentType.getInteger(c, "setting"), 0))))
                        .then(Commands.literal("nail")
                                .then(Commands.argument("nail", DoubleArgumentType.doubleArg(5, 60))
                                        .executes(c -> setting(c.getSource(), IntegerArgumentType.getInteger(c, "no"), 0,
                                                DoubleArgumentType.getDouble(c, "nail")))))
                        .then(Commands.literal("force")
                                .executes(c -> force(c.getSource(), IntegerArgumentType.getInteger(c, "no"), ""))
                                .then(Commands.argument("what", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("big", "reg", "at", "k10", "k4",
                                                "s10", "lt10"), b))
                                        .executes(c -> force(c.getSource(), IntegerArgumentType.getInteger(c, "no"),
                                                StringArgumentType.getString(c, "what")))))
                        .then(Commands.literal("reset").executes(c -> {
                            int no = IntegerArgumentType.getInteger(c, "no");
                            if (!ParlorService.reset(c.getSource().getServer(), no)) {
                                c.getSource().sendFailure(Component.translatable("command.amemaru.parlor.no_machine", no));
                                return 0;
                            }
                            c.getSource().sendSuccess(() -> Component.translatable("command.amemaru.parlor.reset", no), true);
                            return 1;
                        })));
    }

    static LiteralArgumentBuilder<CommandSourceStack> holdings(String what) {
        return Commands.literal(what)
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("count", LongArgumentType.longArg(0, 1_000_000_000L))
                                .executes(c -> holdings(c.getSource(), EntityArgument.getPlayers(c, "targets"), what,
                                        LongArgumentType.getLong(c, "count")))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> parlor() {
        return Commands.literal("parlor")
                .then(Commands.literal("today").executes(c -> {
                    String text = ParlorService.today(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal(text), false);
                    return 1;
                }));
    }

    private static int setting(CommandSourceStack source, int no, int setting, double nail) {
        if (!ParlorService.setSetting(source.getServer(), no, setting, nail)) {
            source.sendFailure(Component.translatable("command.amemaru.parlor.no_machine", no));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.parlor.setting", no, setting > 0 ? String.valueOf(setting) : "-",
                nail > 0 ? String.valueOf(nail) : "-"), true);
        return 1;
    }

    private static int force(CommandSourceStack source, int no, String what) {
        if (!ParlorService.force(source.getServer(), no, what)) {
            source.sendFailure(Component.translatable("command.amemaru.parlor.no_machine", no));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.parlor.force", no, what.isEmpty() ? "-" : what), true);
        return 1;
    }

    private static int holdings(CommandSourceStack source, Collection<ServerPlayer> targets, String what, long count) {
        for (ServerPlayer player : targets) {
            PlayerDataCapability.get(player).ifPresent(d -> {
                if ("balls".equals(what)) {
                    d.getCasino().setBalls(count);
                } else {
                    d.getCasino().setMedals(count);
                }
            });
            source.sendSuccess(() -> Component.translatable("command.amemaru.parlor." + what, player.getGameProfile().getName(), count), true);
        }
        return targets.size();
    }
}
