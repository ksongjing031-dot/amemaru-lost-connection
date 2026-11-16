package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.SyncPlayerDataS2C;
import net.minecraft.server.level.ServerPlayer;

/**
 * 本人の画面へのデータの同期。同じ tick に起きた変更は、tick の終わりにまとめて1回だけ送る。
 */
public final class PlayerSync {
    private PlayerSync() {
    }

    /** 変わったまとまりがあれば送る。 */
    public static void flush(ServerPlayer player, PlayerData data) {
        int sections = data.consumeDirty();
        if (sections != 0 && data.isStatsReady()) {
            ModNetwork.sendTo(player, SyncPlayerDataS2C.of(data, GrowthRules.current(), sections));
        } else if (sections != 0) {
            // ステータスがまだ計算されていなければ、計算されるまで印を残す
            data.markDirty(sections);
        }
    }

    /** すべてのまとまりを送る（ログイン・リスポーン・ディメンションの移動）。 */
    public static void syncAll(ServerPlayer player, PlayerData data) {
        data.markDirty(PlayerData.SYNC_ALL);
        flush(player, data);
    }
}
