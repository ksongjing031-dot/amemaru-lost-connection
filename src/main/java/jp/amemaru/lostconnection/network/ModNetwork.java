package jp.amemaru.lostconnection.network;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.c2s.AllocateStatC2S;
import jp.amemaru.lostconnection.network.c2s.JobActionC2S;
import jp.amemaru.lostconnection.network.c2s.LearnSkillC2S;
import jp.amemaru.lostconnection.network.c2s.SetLoadoutC2S;
import jp.amemaru.lostconnection.network.c2s.UseSkillC2S;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.OpenJobScreenS2C;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.SkillResultS2C;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
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
    public static final String PROTOCOL_VERSION = "2";

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

        // ---- Phase 2：職業とスキル
        CHANNEL.messageBuilder(SyncDefinitionsS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncDefinitionsS2C::encode)
                .decoder(SyncDefinitionsS2C::decode)
                .consumerMainThread(SyncDefinitionsS2C::handle)
                .add();
        CHANNEL.messageBuilder(SkillFxS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillFxS2C::encode)
                .decoder(SkillFxS2C::decode)
                .consumerMainThread(SkillFxS2C::handle)
                .add();
        CHANNEL.messageBuilder(CombatTextS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CombatTextS2C::encode)
                .decoder(CombatTextS2C::decode)
                .consumerMainThread(CombatTextS2C::handle)
                .add();
        CHANNEL.messageBuilder(SkillResultS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillResultS2C::encode)
                .decoder(SkillResultS2C::decode)
                .consumerMainThread(SkillResultS2C::handle)
                .add();
        CHANNEL.messageBuilder(OpenJobScreenS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenJobScreenS2C::encode)
                .decoder(OpenJobScreenS2C::decode)
                .consumerMainThread(OpenJobScreenS2C::handle)
                .add();
        CHANNEL.messageBuilder(UseSkillC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UseSkillC2S::encode)
                .decoder(UseSkillC2S::decode)
                .consumerMainThread(UseSkillC2S::handle)
                .add();
        CHANNEL.messageBuilder(LearnSkillC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LearnSkillC2S::encode)
                .decoder(LearnSkillC2S::decode)
                .consumerMainThread(LearnSkillC2S::handle)
                .add();
        CHANNEL.messageBuilder(SetLoadoutC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetLoadoutC2S::encode)
                .decoder(SetLoadoutC2S::decode)
                .consumerMainThread(SetLoadoutC2S::handle)
                .add();
        CHANNEL.messageBuilder(JobActionC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(JobActionC2S::encode)
                .decoder(JobActionC2S::decode)
                .consumerMainThread(JobActionC2S::handle)
                .add();
    }

    /** 1人のプレイヤーに送る。 */
    public static void sendTo(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** つながっている全員に送る。 */
    public static void sendToAll(Object message) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), message);
    }

    /** サーバーに送る（クライアントから呼ぶ）。 */
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
