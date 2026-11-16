package jp.amemaru.lostconnection.network;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.c2s.AllocateStatC2S;
import jp.amemaru.lostconnection.network.s2c.SyncPlayerDataS2C;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 通信のチャンネル {@code amemaru:main}（Phase 0-B 5章）。
 *
 * <p>プロトコル番号が違うクライアントは接続できない（Modの版ずれを防ぐ）。パケットの形を変えたら
 * {@link #PROTOCOL_VERSION} を上げる。クライアントが送るのは「何をしたいか」だけで、数値の計算はサーバーで行う。</p>
 */
public final class ModNetwork {
    public static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            AmemaruMod.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static boolean registered;

    private ModNetwork() {
    }

    /** パケットを登録する。番号の順番を変えないこと（足すときは最後に足す）。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;

        // サーバー → クライアント
        CHANNEL.messageBuilder(SyncPlayerDataS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPlayerDataS2C::encode)
                .decoder(SyncPlayerDataS2C::decode)
                .consumerMainThread(SyncPlayerDataS2C::handle)
                .add();

        // クライアント → サーバー
        CHANNEL.messageBuilder(AllocateStatC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AllocateStatC2S::encode)
                .decoder(AllocateStatC2S::decode)
                .consumerMainThread(AllocateStatC2S::handle)
                .add();
    }

    /** 1人のプレイヤーに送る。 */
    public static void sendTo(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** サーバーに送る（クライアントから呼ぶ）。 */
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
