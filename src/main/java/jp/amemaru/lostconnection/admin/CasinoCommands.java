package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.casino.CasinoData;
import jp.amemaru.lostconnection.casino.CasinoRules;
import jp.amemaru.lostconnection.casino.CasinoService;
import jp.amemaru.lostconnection.casino.CasinoTable;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/**
 * 管理者のコマンド：カジノ（Phase 7 その3）。
 *
 * <ul>
 *   <li>{@code casino chips <人> <数>}・{@code casino give <人> <数>}：チップを決める・足す</li>
 *   <li>{@code casino open <人> [roulette|highlow|cashier|prizes]}：カジノの画面を開く（開場の日と距離を見ない）</li>
 *   <li>{@code casino table <卓>}：卓の様子。{@code casino next <卓> <結果>}：次の結果を決めておく（ルーレット 0〜36・ハイ＆ロー
 *       1〜13）。{@code casino card <卓> <1〜13>}：ハイ＆ローの今のカードを決める（だれも賭けていないとき）。
 *       {@code casino spin <卓>}：賭けを締めて、すぐ回す。{@code casino reset <卓>}：卓を初めに戻す（賭けは返す）</li>
 *   <li>{@code casino unlock <highlow|new_machines> <true|false>}：ハイ＆ローの印（共同事業②）・2階の新台の間の印（共同事業⑥）</li>
 *   <li>11月27日：地下遊技場。{@code casino machine <台番号> open <人>}（その台の画面。開場の日と距離を見ない）・{@code info}（設定・釘・
 *       中の状態）・{@code setting <1〜6>}・{@code nail <回転数>}（今日だけ）・{@code force [big|reg|at|大当たりのID]}（次の当たり）・
 *       {@code reset}。{@code casino balls <人> <数>}・{@code casino medals <人> <数>}：持ち玉・持ちメダル。{@code casino parlor today}：
 *       今日の設定の分かれ方とイベントの日</li>
 *   <li>{@code casino stats}：館の勘定（賭けられたチップと払い戻したチップ）・カジノナイト</li>
 * </ul>
 * カジノナイトは {@code event start casino_night [分]}・{@code event stop casino_night}（Phase 0-B 10章）。
 */
public final class CasinoCommands {
    private static final SuggestionProvider<CommandSourceStack> TABLES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(List.of("roulette", "highlow_a", "highlow_b"), builder);
    private static final SuggestionProvider<CommandSourceStack> TABS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(CasinoService.TABS, builder);

    private CasinoCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> casinoCommand() {
        return Commands.literal("casino")
                .then(Commands.literal("chips")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("count", LongArgumentType.longArg(0, 1_000_000_000L))
                                        .executes(c -> chips(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                LongArgumentType.getLong(c, "count"), false)))))
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("count", LongArgumentType.longArg(1, 1_000_000_000L))
                                        .executes(c -> chips(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                LongArgumentType.getLong(c, "count"), true)))))
                .then(Commands.literal("open")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> open(c.getSource(), EntityArgument.getPlayer(c, "target"), "roulette"))
                                .then(Commands.argument("tab", StringArgumentType.word()).suggests(TABS)
                                        .executes(c -> open(c.getSource(), EntityArgument.getPlayer(c, "target"),
                                                StringArgumentType.getString(c, "tab"))))))
                .then(Commands.literal("table")
                        .then(Commands.argument("table", StringArgumentType.word()).suggests(TABLES)
                                .executes(c -> table(c.getSource(), StringArgumentType.getString(c, "table")))))
                .then(Commands.literal("next")
                        .then(Commands.argument("table", StringArgumentType.word()).suggests(TABLES)
                                .then(Commands.argument("result", IntegerArgumentType.integer(0, 36))
                                        .executes(c -> next(c.getSource(), StringArgumentType.getString(c, "table"),
                                                IntegerArgumentType.getInteger(c, "result"))))))
                .then(Commands.literal("card")
                        .then(Commands.argument("table", StringArgumentType.word()).suggests(TABLES)
                                .then(Commands.argument("card", IntegerArgumentType.integer(1, 13))
                                        .executes(c -> {
                                            String id = StringArgumentType.getString(c, "table");
                                            int card = IntegerArgumentType.getInteger(c, "card");
                                            if (!CasinoService.setCard(c.getSource().getServer(), id, card)) {
                                                c.getSource().sendFailure(Component.translatable("command.amemaru.casino.no_card", id));
                                                return 0;
                                            }
                                            c.getSource().sendSuccess(() -> Component.translatable("command.amemaru.casino.card", id, card), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("spin")
                        .then(Commands.argument("table", StringArgumentType.word()).suggests(TABLES)
                                .executes(c -> spin(c.getSource(), StringArgumentType.getString(c, "table")))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("table", StringArgumentType.word()).suggests(TABLES)
                                .executes(c -> {
                                    CasinoService.resetTable(c.getSource().getServer(), StringArgumentType.getString(c, "table"));
                                    c.getSource().sendSuccess(() -> Component.translatable("command.amemaru.casino.reset",
                                            StringArgumentType.getString(c, "table")), true);
                                    return 1;
                                })))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("highlow", "new_machines"), b))
                                .then(Commands.argument("open", BoolArgumentType.bool())
                                        .executes(c -> unlock(c.getSource(), StringArgumentType.getString(c, "game"),
                                                BoolArgumentType.getBool(c, "open"))))))
                .then(Commands.literal("stats").executes(c -> stats(c.getSource())))
                .then(ParlorCommands.machine())
                .then(ParlorCommands.holdings("balls"))
                .then(ParlorCommands.holdings("medals"))
                .then(ParlorCommands.parlor());
    }

    private static int chips(CommandSourceStack source, Collection<ServerPlayer> targets, long count, boolean add) {
        for (ServerPlayer player : targets) {
            PlayerDataCapability.get(player).ifPresent(d -> {
                if (add) {
                    d.getCasino().addChips(count);
                } else {
                    d.getCasino().setChips(count);
                }
                source.sendSuccess(() -> Component.translatable("command.amemaru.casino.chips", player.getGameProfile().getName(),
                        d.getCasino().chips()), true);
            });
        }
        return targets.size();
    }

    private static int open(CommandSourceStack source, ServerPlayer player, String tab) {
        CasinoService.open(player, tab, true);
        return 1;
    }

    private static int table(CommandSourceStack source, String id) {
        CasinoTable table = CasinoService.table(id);
        if (table == null) {
            source.sendFailure(Component.translatable("command.amemaru.casino.unknown_table", id));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.casino.table", id, table.phase().name(), table.round(),
                table.watchers().size(), table.outcome()), false);
        return table.round();
    }

    private static int next(CommandSourceStack source, String id, int result) {
        CasinoTable table = CasinoService.table(id);
        if (table == null) {
            source.sendFailure(Component.translatable("command.amemaru.casino.unknown_table", id));
            return 0;
        }
        int value = result;
        CasinoService.forceNext(id, value);
        source.sendSuccess(() -> Component.translatable("command.amemaru.casino.next", id, result), true);
        return 1;
    }

    private static int spin(CommandSourceStack source, String id) {
        if (!CasinoService.spinNow(source.getServer(), id)) {
            source.sendFailure(Component.translatable("command.amemaru.casino.no_spin", id));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.casino.spun", id), true);
        return 1;
    }

    private static int unlock(CommandSourceStack source, String game, boolean on) {
        CasinoRules rules = CasinoRules.current();
        ResourceLocation flag = switch (game) {
            case "highlow" -> rules.highLowFlag();
            case "new_machines" -> rules.parlor().newFlag();
            default -> null;
        };
        if (flag == null) {
            source.sendFailure(Component.translatable("command.amemaru.casino.unknown_game", game));
            return 0;
        }
        StoryState state = StoryState.get(source.getServer());
        if (on) {
            state.add(flag);
        } else {
            state.remove(flag);
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.casino.unlock", game, String.valueOf(on)), true);
        return 1;
    }

    private static int stats(CommandSourceStack source) {
        CasinoData cd = CasinoData.get(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.amemaru.casino.stats", cd.wagered(), cd.paid(),
                cd.wagered() - cd.paid(), cd.night() ? Component.translatable("command.amemaru.event.state_on")
                        : Component.translatable("command.amemaru.event.state_off"), cd.nightHeld()), false);
        return (int) Math.min(Integer.MAX_VALUE, cd.wagered());
    }
}
