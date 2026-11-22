package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.connection.ConnectionActionDef;
import jp.amemaru.lostconnection.connection.ConnectionActionDefs;
import jp.amemaru.lostconnection.connection.ConnectionCategory;
import jp.amemaru.lostconnection.connection.ConnectionData;
import jp.amemaru.lostconnection.connection.ConnectionRecord;
import jp.amemaru.lostconnection.connection.ConnectionRules;
import jp.amemaru.lostconnection.connection.ConnectionService;
import jp.amemaru.lostconnection.connection.ConnectionSocial;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.guild.GuildData;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/**
 * 管理者のコマンド：コネクション量と大結晶の輝き段階（Phase 7 その1）。
 *
 * <ul>
 *   <li>{@code connection}：サーバー全体（段階・星・累計・次の段階まで・人数補正・効いている効果・分野別）</li>
 *   <li>{@code connection add <CP>}・{@code set <CP>}：サーバー全体の累計に足す・決める（段階は下げない）</li>
 *   <li>{@code connection stage <1〜10>}：段階を決める（上げると順に知らせる。下げると累計もしきい値にそろえる）</li>
 *   <li>{@code connection reset}：段階・星・累計・貢献を初めに戻す</li>
 *   <li>{@code connection player <人>}：その人の今日の分野別CPと上限・これまでの合計・来た日</li>
 *   <li>{@code connection award <人> <行動> [数]}：行動をしたことにする（1日の上限も確かめる。確かめ用）</li>
 *   <li>{@code connection sample <人> <名前>}：縁帳に見本の人（パーティを1回組んだ、いない人）を足す（結び札の確かめ用）</li>
 *   <li>{@code connection partner <人> [相手]}：今日の相棒を見る・ふたりお互いに決める</li>
 * </ul>
 */
public final class ConnectionCommands {
    private static final SuggestionProvider<CommandSourceStack> ACTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(ConnectionActionDefs.all().keySet(), builder);

    private ConnectionCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> connectionCommand() {
        return Commands.literal("connection")
                .executes(c -> status(c.getSource()))
                .then(Commands.literal("add")
                        .then(Commands.argument("cp", LongArgumentType.longArg(1, 100_000_000L))
                                .executes(c -> {
                                    ConnectionService.adminAdd(c.getSource().getServer(), LongArgumentType.getLong(c, "cp"));
                                    return status(c.getSource());
                                })))
                .then(Commands.literal("set")
                        .then(Commands.argument("cp", LongArgumentType.longArg(0, 100_000_000L))
                                .executes(c -> {
                                    ConnectionService.adminSet(c.getSource().getServer(), LongArgumentType.getLong(c, "cp"));
                                    return status(c.getSource());
                                })))
                .then(Commands.literal("stage")
                        .then(Commands.argument("stage", IntegerArgumentType.integer(1, 10))
                                .executes(c -> {
                                    ConnectionService.adminStage(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "stage"));
                                    return status(c.getSource());
                                })))
                .then(Commands.literal("reset")
                        .executes(c -> {
                            ConnectionService.adminReset(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> Component.translatable("command.amemaru.connection.reset"), true);
                            return status(c.getSource());
                        }))
                .then(Commands.literal("player")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> player(c.getSource(), EntityArgument.getPlayer(c, "target")))))
                .then(Commands.literal("sample")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(c -> sample(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("partner")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> partner(c.getSource(), EntityArgument.getPlayer(c, "target")))
                                .then(Commands.argument("other", EntityArgument.player())
                                        .executes(c -> {
                                            ServerPlayer a = EntityArgument.getPlayer(c, "target");
                                            ServerPlayer b = EntityArgument.getPlayer(c, "other");
                                            if (a == b) {
                                                c.getSource().sendFailure(Component.translatable("command.amemaru.connection.partner_self"));
                                                return 0;
                                            }
                                            ConnectionSocial.adminSetPartners(c.getSource().getServer(), a.getUUID(), a.getGameProfile().getName(),
                                                    b.getUUID(), b.getGameProfile().getName());
                                            return partner(c.getSource(), a);
                                        }))))
                .then(Commands.literal("award")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("action", ResourceLocationArgument.id())
                                        .suggests(ACTIONS)
                                        .executes(c -> award(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                ResourceLocationArgument.getId(c, "action"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100_000))
                                                .executes(c -> award(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                        ResourceLocationArgument.getId(c, "action"),
                                                        IntegerArgumentType.getInteger(c, "count")))))));
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ConnectionData data = ConnectionData.get(server);
        ConnectionRules rules = ConnectionRules.current();
        double scale = ConnectionService.scale(server);
        int stage = data.stageReached();
        long next = stage < rules.maxStage() ? Math.round(rules.threshold(stage + 1) * scale) : 0L;
        String nextText = next > 0 ? String.valueOf(Math.max(0L, next - data.total())) : "-";
        source.sendSuccess(() -> Component.translatable("command.amemaru.connection.status", stage, data.starsReached(), data.total(),
                nextText, String.format(Locale.ROOT, "%.2f", scale), String.format(Locale.ROOT, "%.1f", ConnectionService.averageActive(server)),
                ServerConfig.CONNECTION_BASE_PLAYERS.get()), false);
        MutableComponent effects = Component.empty();
        boolean any = false;
        for (String key : new String[]{"barrier_plaza", "lost_resist", "exp", "barrier", "down_seconds", "blessing"}) {
            double value = rules.effect(key, stage);
            if (value > 0) {
                if (any) {
                    effects.append("・");
                }
                effects.append(ConnectionService.effectText(key, value));
                any = true;
            }
        }
        Component effectLine = any ? effects : Component.translatable("command.amemaru.connection.no_effects");
        source.sendSuccess(() -> Component.translatable("command.amemaru.connection.effects", effectLine).withStyle(ChatFormatting.GRAY), false);
        MutableComponent cats = Component.empty();
        boolean first = true;
        for (ConnectionCategory category : ConnectionCategory.values()) {
            if (!first) {
                cats.append("・");
            }
            cats.append(Component.translatable(category.translationKey())).append(" " + data.category(category));
            first = false;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.connection.categories", cats).withStyle(ChatFormatting.GRAY), false);
        return stage;
    }

    private static int player(CommandSourceStack source, ServerPlayer target) {
        PlayerData data = PlayerDataCapability.get(target).orElse(null);
        if (data == null) {
            return 0;
        }
        ConnectionRecord record = data.getConnection();
        record.rollover(ConnectionService.today());
        MutableComponent cats = Component.empty();
        boolean first = true;
        for (ConnectionCategory category : ConnectionCategory.values()) {
            if (!first) {
                cats.append("・");
            }
            cats.append(Component.translatable(category.translationKey())).append(" " + record.today(category));
            first = false;
        }
        boolean newcomer = ConnectionService.isNewcomer(target);
        source.sendSuccess(() -> Component.translatable("command.amemaru.connection.player", target.getGameProfile().getName(),
                record.todayTotal(), ServerConfig.CONNECTION_DAILY_TOTAL.get(), cats, record.lifetimeTotal(), record.joinDay(),
                newcomer ? Component.translatable("command.amemaru.connection.newcomer") : Component.empty()), false);
        return record.todayTotal();
    }

    private static int partner(CommandSourceStack source, ServerPlayer target) {
        net.minecraft.nbt.CompoundTag tag = ConnectionSocial.partnerTag(target);
        if (!tag.contains("name")) {
            source.sendSuccess(() -> Component.translatable("command.amemaru.connection.partner_none", target.getGameProfile().getName()), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.connection.partner", target.getGameProfile().getName(),
                tag.getString("name"), tag.getInt("progress"), tag.getInt("goal"), String.valueOf(tag.getBoolean("done"))), false);
        return 1;
    }

    private static int sample(CommandSourceStack source, Collection<ServerPlayer> targets, String name) {
        UUID id = UUID.nameUUIDFromBytes(("amemaru-sample:" + name).getBytes(StandardCharsets.UTF_8));
        int day = ProgressionData.get(source.getServer()).day();
        int count = 0;
        for (ServerPlayer player : targets) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data != null) {
                data.getGuild().addConnection(id, name, GuildData.ConnectionKind.PARTY, day);
                data.markDirty(PlayerData.SYNC_GUILD);
                count++;
            }
        }
        int done = count;
        source.sendSuccess(() -> Component.translatable("command.amemaru.connection.sample", name, done), true);
        return done;
    }

    private static int award(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation action, int count) {
        ConnectionActionDef def = ConnectionActionDefs.get(action);
        if (def == null) {
            source.sendFailure(Component.translatable("command.amemaru.connection.unknown_action", action.toString()));
            return 0;
        }
        int sum = 0;
        for (ServerPlayer player : targets) {
            int granted = ConnectionService.award(player, action, null, count);
            sum += granted;
            source.sendSuccess(() -> Component.translatable("command.amemaru.connection.awarded", player.getGameProfile().getName(), def.name(),
                    count, granted), true);
        }
        return sum;
    }
}
