package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.party.Party;
import jp.amemaru.lostconnection.party.PartyManager;
import jp.amemaru.lostconnection.player.ChurchPoint;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Optional;

/**
 * Phase 4の管理者コマンド（確かめと運営）。
 *
 * <ul>
 *   <li>{@code down <人>}：ダウンにする。{@code down <人> revive}：すぐ起こす。{@code down <人> church}：すぐ教会へ戻す
 *       （代価と衰弱もかかる）</li>
 *   <li>{@code church}：教会での復活の場所を見る。{@code church set [位置] [向き]}：決める（省くと自分の場所と向き）。
 *       {@code church clear}：決めた場所を消す（確かめ用の街の教会か、スポーン地点に戻る）。{@code church tp [人]}：そこへ移る</li>
 *   <li>{@code money <人>}：所持金を見る。{@code money <人> set|give|take <額>}：決める・渡す・取る</li>
 *   <li>{@code party list}：すべてのパーティ</li>
 * </ul>
 */
final class DownCommands {
    private DownCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> downCommand() {
        return Commands.literal("down")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> down(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), Mode.DOWN))
                        .then(Commands.literal("revive")
                                .executes(ctx -> down(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), Mode.REVIVE)))
                        .then(Commands.literal("church")
                                .executes(ctx -> down(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), Mode.CHURCH))));
    }

    private enum Mode { DOWN, REVIVE, CHURCH }

    private static int down(CommandSourceStack source, Collection<ServerPlayer> targets, Mode mode) {
        int done = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> found = PlayerDataCapability.get(player);
            if (found.isEmpty()) {
                continue;
            }
            PlayerData data = found.get();
            switch (mode) {
                case DOWN -> {
                    if (!data.down().isDown()) {
                        player.setHealth(Math.min(player.getHealth(), 0.01F));
                        DownHandler.goDown(player, data);
                        done++;
                    }
                }
                case REVIVE -> {
                    if (data.down().isDown()) {
                        DownHandler.revive(player, data, null);
                        done++;
                    }
                }
                case CHURCH -> {
                    DownHandler.returnToChurch(player, data);
                    done++;
                }
            }
        }
        int count = done;
        AdminCommands.log(source, "down " + mode.name().toLowerCase(java.util.Locale.ROOT) + " " + count + " players");
        source.sendSuccess(() -> Component.translatable("commands.amemaru.down." + mode.name().toLowerCase(java.util.Locale.ROOT), count), true);
        return count;
    }

    // ---------------------------------------------------------------- 教会

    static LiteralArgumentBuilder<CommandSourceStack> churchCommand() {
        return Commands.literal("church")
                .executes(ctx -> showChurch(ctx.getSource()))
                .then(Commands.literal("set")
                        .executes(ctx -> setChurch(ctx.getSource(), ctx.getSource().getPosition(), ctx.getSource().getRotation().y))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> setChurch(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"), ctx.getSource().getRotation().y))
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg(-180.0F, 360.0F))
                                        .executes(ctx -> setChurch(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"),
                                                FloatArgumentType.getFloat(ctx, "yaw"))))))
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            ChurchPoint.get(ctx.getSource().getServer()).clear();
                            AdminCommands.log(ctx.getSource(), "church clear");
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.church.cleared"), true);
                            return showChurch(ctx.getSource());
                        }))
                .then(Commands.literal("tp")
                        .executes(ctx -> tpChurch(ctx.getSource(), java.util.List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> tpChurch(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))));
    }

    private static int showChurch(CommandSourceStack source) {
        ChurchPoint.Spot spot = ChurchPoint.resolve(source.getServer());
        Vec3 p = spot.pos();
        source.sendSuccess(() -> Component.translatable("commands.amemaru.church.show",
                Component.translatable("commands.amemaru.church.source." + spot.source().name().toLowerCase(java.util.Locale.ROOT)),
                spot.level().dimension().location().toString(), String.format(java.util.Locale.ROOT, "%.1f", p.x),
                String.format(java.util.Locale.ROOT, "%.1f", p.y), String.format(java.util.Locale.ROOT, "%.1f", p.z),
                Math.round(spot.yaw())), false);
        return 1;
    }

    private static int setChurch(CommandSourceStack source, Vec3 pos, float yaw) {
        ChurchPoint.get(source.getServer()).set(source.getLevel(), pos, yaw);
        AdminCommands.log(source, "church set " + source.getLevel().dimension().location() + " " + pos + " yaw " + yaw);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.church.set"), true);
        return showChurch(source);
    }

    private static int tpChurch(CommandSourceStack source, Collection<ServerPlayer> targets) {
        ChurchPoint.Spot spot = ChurchPoint.resolve(source.getServer());
        for (ServerPlayer player : targets) {
            player.teleportTo(spot.level(), spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), 0.0F);
        }
        source.sendSuccess(() -> Component.translatable("commands.amemaru.church.tp", targets.size()), false);
        return targets.size();
    }

    // ---------------------------------------------------------------- 所持金

    static LiteralArgumentBuilder<CommandSourceStack> moneyCommand() {
        return Commands.literal("money")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> showMoney(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> money(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), "set",
                                                LongArgumentType.getLong(ctx, "amount")))))
                        .then(Commands.literal("give")
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> money(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), "give",
                                                LongArgumentType.getLong(ctx, "amount")))))
                        .then(Commands.literal("take")
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> money(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), "take",
                                                LongArgumentType.getLong(ctx, "amount"))))));
    }

    private static int showMoney(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            PlayerDataCapability.get(player).ifPresent(data -> source.sendSuccess(() -> Component.translatable(
                    "commands.amemaru.money.show", player.getDisplayName(), data.getKnots()), false));
        }
        return targets.size();
    }

    private static int money(CommandSourceStack source, Collection<ServerPlayer> targets, String action, long amount) {
        for (ServerPlayer player : targets) {
            PlayerDataCapability.get(player).ifPresent(data -> {
                switch (action) {
                    case "set" -> Wallet.set(player, data, amount, "admin set");
                    case "give" -> Wallet.give(player, data, amount, "admin give");
                    default -> Wallet.take(player, data, amount, "admin take");
                }
            });
        }
        AdminCommands.log(source, "money " + action + " " + amount + " x" + targets.size());
        return showMoney(source, targets);
    }

    // ---------------------------------------------------------------- パーティ

    static LiteralArgumentBuilder<CommandSourceStack> partyCommand() {
        return Commands.literal("party")
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            PartyManager manager = PartyManager.get(ctx.getSource().getServer());
                            int n = 0;
                            for (Party party : manager.parties()) {
                                String text = party.toString();
                                ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                                n++;
                            }
                            int count = n;
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.party.count", count), false);
                            return count;
                        }));
    }
}
