package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.network.s2c.SyncPlayerDataS2C;

/**
 * サーバーから届いたパケットの、クライアント側の処理。
 *
 * <p>このクラスはクライアントにだけ読み込まれる（パケットの側からは {@code DistExecutor} 経由で呼ぶ）。</p>
 */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handleSync(SyncPlayerDataS2C message) {
        ClientPlayerState.get().apply(message);
    }
}
