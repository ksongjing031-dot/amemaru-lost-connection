package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.fishing.FishDef;
import jp.amemaru.lostconnection.fishing.FishDefs;
import jp.amemaru.lostconnection.fishing.FishItem;
import jp.amemaru.lostconnection.fishing.FishingData;
import jp.amemaru.lostconnection.fishing.FishingRules;
import jp.amemaru.lostconnection.fishing.FishingService;
import jp.amemaru.lostconnection.fishing.FishingTournament;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Locale;

/**
 * 管理者のコマンド：釣り（Phase 7 その2）。
 *
 * <ul>
 *   <li>{@code fishing give <人> <魚> [cm]}：魚を渡す（図鑑には書かない。大きさを書くと、大物の印をつける）</li>
 *   <li>{@code fishing catch <人> <魚> [cm]}：釣ったことにする（図鑑・記録・称号・大会。CPは数えない）</li>
 *   <li>{@code fishing hook <人> <魚>}：魚をかけて、糸の張りのミニゲームを始める（確かめ用）</li>
 *   <li>{@code fishing dex <人> [clear|fill]}：魚図鑑を見る・空にする・すべて埋める</li>
 *   <li>{@code fishing open <人> [タブ]}：ポッカ爺の釣り具屋の画面を開く（dex・sell・trade・tournament）</li>
 *   <li>{@code fishing tournament start [分] [始まるまでの秒]}・{@code stop}（順位と賞品を出して終える）・{@code cancel}（何も渡さず
 *       取りやめる）・{@code status}：釣り大会（Phase 0-A 15章「夜の催し」。管理者が好きなときに始める）</li>
 *   <li>{@code fishing lord [reset]}：湖の主を最初に釣った人を見る・消す</li>
 *   <li>{@code fishing lake <true|false>}：湖畔の釣り場を開く・閉じる（共同事業①の印。寄付と共同事業はその4）</li>
 * </ul>
 */
public final class FishingCommands {
    private static final SuggestionProvider<CommandSourceStack> FISH = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(FishDefs.all().keySet(), builder);

    private FishingCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> fishingCommand() {
        return Commands.literal("fishing")
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("fish", ResourceLocationArgument.id()).suggests(FISH)
                                        .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                ResourceLocationArgument.getId(c, "fish"), -1))
                                        .then(Commands.argument("size", DoubleArgumentType.doubleArg(0.1, 10_000))
                                                .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                        ResourceLocationArgument.getId(c, "fish"), DoubleArgumentType.getDouble(c, "size")))))))
                .then(Commands.literal("catch")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("fish", ResourceLocationArgument.id()).suggests(FISH)
                                        .executes(c -> caught(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                ResourceLocationArgument.getId(c, "fish"), -1))
                                        .then(Commands.argument("size", DoubleArgumentType.doubleArg(0.1, 10_000))
                                                .executes(c -> caught(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                        ResourceLocationArgument.getId(c, "fish"), DoubleArgumentType.getDouble(c, "size")))))))
                .then(Commands.literal("hook")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("fish", ResourceLocationArgument.id()).suggests(FISH)
                                        .executes(c -> hook(c.getSource(), EntityArgument.getPlayer(c, "target"),
                                                ResourceLocationArgument.getId(c, "fish"))))))
                .then(Commands.literal("dex")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> dex(c.getSource(), EntityArgument.getPlayer(c, "target"), ""))
                                .then(Commands.literal("clear")
                                        .executes(c -> dex(c.getSource(), EntityArgument.getPlayer(c, "target"), "clear")))
                                .then(Commands.literal("fill")
                                        .executes(c -> dex(c.getSource(), EntityArgument.getPlayer(c, "target"), "fill")))))
                .then(Commands.literal("open")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> open(c.getSource(), EntityArgument.getPlayer(c, "target"), "dex"))
                                .then(Commands.argument("tab", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"dex", "sell", "trade", "tournament"}, b))
                                        .executes(c -> open(c.getSource(), EntityArgument.getPlayer(c, "target"),
                                                StringArgumentType.getString(c, "tab"))))))
                .then(Commands.literal("tournament")
                        .then(Commands.literal("start")
                                .executes(c -> start(c.getSource(), FishingRules.current().tournamentMinutes(),
                                        FishingRules.current().tournamentCountdown()))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 240))
                                        .executes(c -> start(c.getSource(), IntegerArgumentType.getInteger(c, "minutes"),
                                                FishingRules.current().tournamentCountdown()))
                                        .then(Commands.argument("countdown", IntegerArgumentType.integer(0, 3600))
                                                .executes(c -> start(c.getSource(), IntegerArgumentType.getInteger(c, "minutes"),
                                                        IntegerArgumentType.getInteger(c, "countdown"))))))
                        .then(Commands.literal("stop").executes(c -> stop(c.getSource(), true)))
                        .then(Commands.literal("cancel").executes(c -> stop(c.getSource(), false)))
                        .then(Commands.literal("status").executes(c -> status(c.getSource()))))
                .then(Commands.literal("lord")
                        .executes(c -> lord(c.getSource(), false))
                        .then(Commands.literal("reset").executes(c -> lord(c.getSource(), true))))
                .then(Commands.literal("lake")
                        .then(Commands.argument("open", BoolArgumentType.bool())
                                .executes(c -> {
                                    boolean on = BoolArgumentType.getBool(c, "open");
                                    StoryState state = StoryState.get(c.getSource().getServer());
                                    if (on) {
                                        state.add(FishingRules.current().lakeFlag());
                                    } else {
                                        state.remove(FishingRules.current().lakeFlag());
                                    }
                                    c.getSource().sendSuccess(() -> Component.translatable("command.amemaru.fishing.lake", String.valueOf(on)), true);
                                    return on ? 1 : 0;
                                })));
    }

    private static FishDef fish(CommandSourceStack source, ResourceLocation id) {
        FishDef def = FishDefs.get(id);
        if (def == null) {
            source.sendFailure(Component.translatable("command.amemaru.fishing.unknown_fish", id.toString()));
        }
        return def;
    }

    private static double sizeOf(FishDef def, double size, ServerPlayer player) {
        return size > 0 ? size : FishingService.rollSize(player.getRandom(), def);
    }

    private static int give(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id, double size) {
        FishDef def = fish(source, id);
        Item item = def == null ? null : FishingService.itemOf(def);
        if (item == null) {
            return 0;
        }
        for (ServerPlayer player : targets) {
            ItemStack stack = new ItemStack(item);
            double cm = sizeOf(def, size, player);
            if (size > 0) {
                FishItem.markBig(stack, cm, player.getGameProfile().getName(), ProgressionData.currentDay(player.server));
            }
            Component name = stack.getHoverName();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.given", player.getGameProfile().getName(), name,
                    String.format(Locale.ROOT, "%.1f", cm)), true);
        }
        return targets.size();
    }

    private static int caught(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id, double size) {
        FishDef def = fish(source, id);
        if (def == null) {
            return 0;
        }
        for (ServerPlayer player : targets) {
            double cm = sizeOf(def, size, player);
            String spot = FishingService.spotAt(player.serverLevel(), player.blockPosition());
            FishingService.catchFish(player, def, cm, spot, false);
            source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.caught", player.getGameProfile().getName(), def.name(),
                    String.format(Locale.ROOT, "%.1f", cm)), true);
        }
        return targets.size();
    }

    private static int hook(CommandSourceStack source, ServerPlayer player, ResourceLocation id) {
        FishDef def = fish(source, id);
        if (def == null) {
            return 0;
        }
        FishingService.startSession(player, def, FishingService.rollSize(player.getRandom(), def),
                FishingService.spotAt(player.serverLevel(), player.blockPosition()));
        source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.minigame", player.getGameProfile().getName(), def.name()), true);
        return 1;
    }

    private static int dex(CommandSourceStack source, ServerPlayer player, String mode) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return 0;
        }
        String name = player.getGameProfile().getName();
        if ("clear".equals(mode)) {
            data.getFishing().clear();
            source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.dex_cleared", name), true);
        } else if ("fill".equals(mode)) {
            int day = ProgressionData.currentDay(player.server);
            for (FishDef def : FishDefs.ordered()) {
                if (!data.getFishing().has(def.id())) {
                    data.getFishing().record(def.id(), (def.sizeMin() + def.sizeMax()) / 2.0, day);
                }
            }
            jp.amemaru.lostconnection.guild.TitleService.check(player);
            source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.dex_filled", name), true);
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.dex", name, data.getFishing().species(), FishDefs.all().size(),
                FishingService.percent(data), data.getFishing().total(), data.getFishing().points()), false);
        return data.getFishing().species();
    }

    private static int open(CommandSourceStack source, ServerPlayer player, String tab) {
        FishingService.openShop(player, tab, true);
        return 1;
    }

    private static int start(CommandSourceStack source, int minutes, int countdown) {
        if (!FishingTournament.start(source.getServer(), minutes, countdown)) {
            source.sendFailure(Component.translatable("command.amemaru.fishing.tournament_running"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.tournament_started", minutes, countdown), true);
        return 1;
    }

    private static int stop(CommandSourceStack source, boolean award) {
        if (!FishingData.get(source.getServer()).tournamentOn()) {
            source.sendFailure(Component.translatable("command.amemaru.fishing.tournament_none"));
            return 0;
        }
        FishingTournament.finish(source.getServer(), award);
        source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.tournament_stopped"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        FishingData fd = FishingData.get(source.getServer());
        if (!fd.tournamentOn()) {
            source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.tournament_none"), false);
            return 0;
        }
        long left = Math.max(0, (fd.endMillis() - System.currentTimeMillis()) / 1000);
        Component venue = Component.translatable(fd.lakeVenue() ? "fishing.amemaru.tournament.venue_lake" : "fishing.amemaru.tournament.venue_pond");
        source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.tournament_status", fd.held(), venue, left, fd.ranking().size()),
                false);
        return fd.ranking().size();
    }

    private static int lord(CommandSourceStack source, boolean reset) {
        FishingData fd = FishingData.get(source.getServer());
        if (reset) {
            FishingService.resetLord(source.getServer());
            source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.lord_reset"), true);
            return 1;
        }
        FishingData.LordCatch lord = fd.lord();
        if (lord == null) {
            source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.lord_none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.fishing.lord", lord.name(), lord.day(),
                String.format(Locale.ROOT, "%.1f", lord.size())), false);
        return 1;
    }
}
