package jp.amemaru.lostconnection.social;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import jp.amemaru.lostconnection.economy.KnotTransfer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 決闘と依頼台のコマンド（Phase 6 その4）。
 *
 * <ul>
 *   <li>だれでも：{@code /amemaru duel}（決闘の画面）・{@code duel challenge <人>}・{@code accept [人]}・{@code decline [人]}・
 *       {@code watch <番号>}・{@code leave}（観戦をやめる・降参する）、{@code /amemaru commission stop <番号>}（依頼を止める）。
 *       チャットの［受ける］［観戦する］［ここで止める］のボタンが使う</li>
 *   <li>管理者：{@code /amemaru admin duel list|stop <番号>}・{@code /amemaru admin commission list|cancel <番号>}</li>
 * </ul>
 */
public final class SocialCommands {
    private SocialCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> duelCommand() {
        return Commands.literal("duel")
                .executes(ctx -> {
                    DuelService.sendState(ctx.getSource().getPlayerOrException(), true);
                    return 1;
                })
                .then(Commands.literal("challenge")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> DuelService.challenge(ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player")) ? 1 : 0)))
                .then(Commands.literal("accept")
                        .executes(ctx -> DuelService.accept(ctx.getSource().getPlayerOrException(), null) ? 1 : 0)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> DuelService.accept(ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player").getUUID()) ? 1 : 0)))
                .then(Commands.literal("decline")
                        .executes(ctx -> DuelService.decline(ctx.getSource().getPlayerOrException(), null) ? 1 : 0)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> DuelService.decline(ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player").getUUID()) ? 1 : 0)))
                .then(Commands.literal("watch")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> DuelService.watch(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)))
                .then(Commands.literal("leave")
                        .executes(ctx -> DuelService.leave(ctx.getSource().getPlayerOrException()) ? 1 : 0));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> commissionCommand() {
        return Commands.literal("commission")
                .then(Commands.literal("stop")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> CommissionBoard.stop(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> adminDuelCommand() {
        return Commands.literal("duel")
                // 決闘場を建てて（建っていなければ）、観客席か上から見る（確かめ用）
                .then(Commands.literal("arena")
                        .executes(ctx -> arena(ctx.getSource(), 0, false))
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, 15))
                                .executes(ctx -> arena(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index"), false))
                                .then(Commands.literal("above")
                                        .executes(ctx -> arena(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index"), true)))))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            List<Component> lines = DuelService.describe();
                            if (lines.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.duel.none"), false);
                            }
                            for (Component line : lines) {
                                ctx.getSource().sendSuccess(() -> line, false);
                            }
                            return lines.size();
                        }))
                .then(Commands.literal("stop")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                    boolean done = DuelService.adminStop(ctx.getSource().getServer(), id);
                                    ctx.getSource().sendSuccess(() -> Component.translatable(done ? "commands.amemaru.duel.stopped"
                                            : "commands.amemaru.duel.none", id), true);
                                    return done ? 1 : 0;
                                })));
    }

    private static int arena(CommandSourceStack source, int index, boolean above) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
        DuelArena.ensureBuilt(source.getServer(), index);
        net.minecraft.core.BlockPos center = DuelArena.center(index);
        if (above) {
            player.teleportTo(DuelArena.level(source.getServer()), center.getX() + 0.5, center.getY() + 15.0, center.getZ() - 21.5, 0.0F, 38.0F);
        } else {
            net.minecraft.world.phys.Vec3 spot = DuelArena.spectatorSpot(index, 0);
            player.teleportTo(DuelArena.level(source.getServer()), spot.x, spot.y, spot.z, DuelArena.spectatorYaw(0), 12.0F);
        }
        source.sendSuccess(() -> Component.translatable("commands.amemaru.duel.arena", index), false);
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> adminCommissionCommand() {
        return Commands.literal("commission")
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            List<Component> lines = CommissionBoard.describe(ctx.getSource().getServer());
                            long held = CommissionData.get(ctx.getSource().getServer()).heldKnots();
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.commission.held",
                                    lines.size(), KnotTransfer.format(held)), false);
                            for (Component line : lines) {
                                ctx.getSource().sendSuccess(() -> line, false);
                            }
                            return lines.size();
                        }))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                    boolean done = CommissionBoard.adminCancel(ctx.getSource().getServer(), id);
                                    ctx.getSource().sendSuccess(() -> Component.translatable(done ? "commands.amemaru.commission.cancelled"
                                            : "commands.amemaru.commission.none", id), true);
                                    return done ? 1 : 0;
                                })));
    }
}
