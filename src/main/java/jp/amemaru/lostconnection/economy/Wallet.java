package jp.amemaru.lostconnection.economy;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 所持金（ノット）の出し入れ（Phase 0-B 8章「お金の出し入れはすべて Wallet を通し、EconomyLog に記録します」）。
 *
 * <p>Phase 4ではデスペナルティと管理者のコマンドだけが使っていた。Phase 6で、出し入れを {@link EconomyLog} に
 * 記録し（渡す＝発行、取る＝回収、人から人へ＝移動）、プレイヤー同士の受け渡し（{@link #transfer}）を足した。
 * サーバーのログにも1件ずつ残す。</p>
 */
public final class Wallet {
    private Wallet() {
    }

    public static long balance(PlayerData data) {
        return data.getKnots();
    }

    /** 渡す（0以下なら何もしない）。世界にノットが増える（発行）。 */
    public static void give(ServerPlayer player, PlayerData data, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        long before = data.getKnots();
        data.setKnots(saturatedAdd(before, amount));
        long change = data.getKnots() - before;
        log(player, reason, change, data.getKnots());
        record(player.server, EconomyLog.Flow.ISSUE, reason, "", name(player), change);
        jp.amemaru.lostconnection.guild.GuildHooks.onKnotsChanged(player, data, before, data.getKnots());
    }

    /** 取る（持っている分まで）。取れた量を返す。世界からノットが消える（回収）。 */
    public static long take(ServerPlayer player, PlayerData data, long amount, String reason) {
        if (amount <= 0) {
            return 0;
        }
        long before = data.getKnots();
        long taken = Math.min(before, amount);
        data.setKnots(before - taken);
        log(player, reason, -taken, data.getKnots());
        record(player.server, EconomyLog.Flow.COLLECT, reason, name(player), "", taken);
        jp.amemaru.lostconnection.guild.GuildHooks.onKnotsChanged(player, data, before, data.getKnots());
        return taken;
    }

    /** 持っていれば、ちょうどその額を取る（足りなければ何も取らずに false）。店・手数料・契約など。 */
    public static boolean pay(ServerPlayer player, PlayerData data, long amount, String reason) {
        if (amount < 0 || data.getKnots() < amount) {
            return false;
        }
        if (amount > 0) {
            take(player, data, amount, reason);
        }
        return true;
    }

    /** 決める（管理者のコマンド）。増えた分は発行、減った分は回収として記録する。 */
    public static void set(ServerPlayer player, PlayerData data, long amount, String reason) {
        long before = data.getKnots();
        data.setKnots(amount);
        long change = data.getKnots() - before;
        log(player, reason, change, data.getKnots());
        if (change > 0) {
            record(player.server, EconomyLog.Flow.ISSUE, reason, "", name(player), change);
        } else if (change < 0) {
            record(player.server, EconomyLog.Flow.COLLECT, reason, name(player), "", -change);
        }
        jp.amemaru.lostconnection.guild.GuildHooks.onKnotsChanged(player, data, before, data.getKnots());
    }

    /**
     * 人から人へ渡す（11月22日：ノットの受け渡し。フリマの売上も）。ちょうどその額を渡せたら true。
     * 足りないとき・同じ人・0以下のときは、何もしないで false（一部だけ渡すことはしない）。
     */
    public static boolean transfer(ServerPlayer from, PlayerData fromData, ServerPlayer to, PlayerData toData, long amount,
                                   String reason) {
        if (amount <= 0 || from.getUUID().equals(to.getUUID()) || fromData == toData || fromData.getKnots() < amount) {
            return false;
        }
        long fromBefore = fromData.getKnots();
        long toBefore = toData.getKnots();
        fromData.setKnots(fromData.getKnots() - amount);
        toData.setKnots(saturatedAdd(toData.getKnots(), amount));
        log(from, reason + " -> " + name(to), -amount, fromData.getKnots());
        log(to, reason + " <- " + name(from), amount, toData.getKnots());
        record(from.server, EconomyLog.Flow.MOVE, reason, name(from), name(to), amount);
        jp.amemaru.lostconnection.guild.GuildHooks.onKnotsChanged(from, fromData, fromBefore, fromData.getKnots());
        jp.amemaru.lostconnection.guild.GuildHooks.onKnotsChanged(to, toData, toBefore, toData.getKnots());
        return true;
    }

    /**
     * フリーマーケットで買う（Phase 6）：買い手からちょうど total を取り、手数料 fee は回収、残り（売り手の売上）は売り手への移動として
     * 記録する（売上は台帳に預かり、売り手が受け取るときは {@link #receive} で記録しない）。足りなければ何もしないで false。
     */
    public static boolean payToSeller(ServerPlayer buyer, PlayerData data, long total, long fee, String sellerName, String reason) {
        if (total <= 0 || fee < 0 || fee > total || data.getKnots() < total) {
            return false;
        }
        long before = data.getKnots();
        data.setKnots(data.getKnots() - total);
        log(buyer, reason + " -> " + sellerName, -total, data.getKnots());
        jp.amemaru.lostconnection.guild.GuildHooks.onKnotsChanged(buyer, data, before, data.getKnots());
        record(buyer.server, EconomyLog.Flow.COLLECT, reason + " fee", name(buyer), "", fee);
        record(buyer.server, EconomyLog.Flow.MOVE, reason, name(buyer), sellerName, total - fee);
        return true;
    }

    /** 預かっていた売上を渡す（Phase 6。売れたときに「移動」として記録済みなので、ここでは記録しない）。 */
    public static void receive(ServerPlayer player, PlayerData data, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        long before = data.getKnots();
        data.setKnots(saturatedAdd(before, amount));
        log(player, reason, data.getKnots() - before, data.getKnots());
        jp.amemaru.lostconnection.guild.GuildHooks.onKnotsChanged(player, data, before, data.getKnots());
    }

    /** 所持金の何%か（切り捨て）。デスペナルティで失う量。 */
    public static long percentOf(long balance, double percent) {
        if (balance <= 0 || percent <= 0) {
            return 0;
        }
        return (long) Math.floor(balance * Math.min(100.0, percent) / 100.0);
    }

    /** 額の何%か（切り上げ。手数料など。0%なら0、それ以外は1以上）。 */
    public static long feeOf(long amount, double percent) {
        if (amount <= 0 || percent <= 0) {
            return 0;
        }
        return Math.max(1L, (long) Math.ceil(amount * Math.min(100.0, percent) / 100.0));
    }

    static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    /** かけ算（あふれたら Long.MAX_VALUE。0以上どうし）。 */
    public static long saturatedMultiply(long a, long b) {
        if (a <= 0 || b <= 0) {
            return 0;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    private static void record(MinecraftServer server, EconomyLog.Flow flow, String reason, String from, String to, long amount) {
        if (server == null || amount <= 0) {
            return;
        }
        EconomyLog.get(server).record(ProgressionData.currentDay(server), flow, reason, from, to, amount);
    }

    private static void log(ServerPlayer player, String reason, long change, long after) {
        if (change != 0) {
            AmemaruMod.LOGGER.info("[amemaru][economy] {} {}{} knots ({}) -> {}", player.getGameProfile().getName(),
                    change > 0 ? "+" : "", change, reason, after);
        }
    }
}
