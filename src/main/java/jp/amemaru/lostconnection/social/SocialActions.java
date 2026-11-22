package jp.amemaru.lostconnection.social;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 交流の操作（Phase 6 その4：依頼台と決闘）。{@code SocialActionC2S} の「何をしたいか」を受けて、できるかどうかを確かめて行う。
 */
public final class SocialActions {
    private SocialActions() {
    }

    public static void onAction(ServerPlayer player, String action, CompoundTag args) {
        switch (action) {
            // ---- 依頼台
            case "commission_open" -> CommissionBoard.open(player, BlockPos.of(args.getLong("pos")));
            case "commission_close" -> CommissionBoard.close(player);
            case "commission_quote" -> CommissionBoard.quote(player, args);
            case "commission_create" -> CommissionBoard.create(player, args);
            case "commission_stop" -> CommissionBoard.stop(player, args.getInt("id"));
            case "commission_accept" -> CommissionBoard.accept(player, args.getInt("id"));
            case "commission_release" -> CommissionBoard.release(player, args.getInt("id"));
            case "commission_attempt" -> CommissionBoard.attempt(player, args.getInt("id"), Math.max(1, args.getInt("times")));
            // ---- 決闘
            case "duel_open" -> DuelService.sendState(player, true);
            case "duel_close" -> DuelService.close(player);
            case "duel_challenge" -> {
                ServerPlayer target = player.server.getPlayerList().getPlayerByName(args.getString("name"));
                if (target != null) {
                    DuelService.challenge(player, target);
                }
            }
            case "duel_accept" -> {
                ServerPlayer from = player.server.getPlayerList().getPlayerByName(args.getString("name"));
                DuelService.accept(player, from == null ? null : from.getUUID());
            }
            case "duel_decline" -> {
                ServerPlayer from = player.server.getPlayerList().getPlayerByName(args.getString("name"));
                DuelService.decline(player, from == null ? null : from.getUUID());
            }
            case "duel_watch" -> DuelService.watch(player, args.getInt("id"));
            case "duel_leave" -> DuelService.leave(player);
            default -> {
            }
        }
        if (action.startsWith("duel_") && !action.equals("duel_close") && !action.equals("duel_open")) {
            DuelService.sendState(player, false);
        }
    }
}
