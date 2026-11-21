package jp.amemaru.lostconnection.economy;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.player.PlayerData;
import net.minecraft.server.level.ServerPlayer;

/**
 * 所持金（ノット）の出し入れ（Phase 0-B 8章「お金の出し入れはすべて Wallet を通す」）。
 *
 * <p>Phase 4ではデスペナルティ（呼び戻しの儀の代価）と管理者のコマンドだけが使う。店・フリマ・倉庫・取引の記録
 * （{@code EconomyLog}）はPhase 6で足す。それまでは、出し入れをサーバーのログに残す。</p>
 */
public final class Wallet {
    private Wallet() {
    }

    public static long balance(PlayerData data) {
        return data.getKnots();
    }

    /** 渡す（0以下なら何もしない）。 */
    public static void give(ServerPlayer player, PlayerData data, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        long before = data.getKnots();
        data.setKnots(saturatedAdd(before, amount));
        log(player, reason, data.getKnots() - before, data.getKnots());
    }

    /** 取る（持っている分まで）。取れた量を返す。 */
    public static long take(ServerPlayer player, PlayerData data, long amount, String reason) {
        if (amount <= 0) {
            return 0;
        }
        long before = data.getKnots();
        long taken = Math.min(before, amount);
        data.setKnots(before - taken);
        log(player, reason, -taken, data.getKnots());
        return taken;
    }

    /** 決める（管理者のコマンド）。 */
    public static void set(ServerPlayer player, PlayerData data, long amount, String reason) {
        long before = data.getKnots();
        data.setKnots(amount);
        log(player, reason, data.getKnots() - before, data.getKnots());
    }

    /** 所持金の何%か（切り捨て）。デスペナルティで失う量。 */
    public static long percentOf(long balance, double percent) {
        if (balance <= 0 || percent <= 0) {
            return 0;
        }
        return (long) Math.floor(balance * Math.min(100.0, percent) / 100.0);
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    private static void log(ServerPlayer player, String reason, long change, long after) {
        if (change != 0) {
            AmemaruMod.LOGGER.info("[amemaru][economy] {} {}{} knots ({}) -> {}", player.getGameProfile().getName(),
                    change > 0 ? "+" : "", change, reason, after);
        }
    }
}
