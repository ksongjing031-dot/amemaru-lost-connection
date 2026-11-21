package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.network.s2c.PartyStateS2C;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.UUID;

/** パーティの様子（クライアント。Phase 4）。サーバーから {@link PartyStateS2C} で受け取る（変わったときと1秒ごと）。 */
public final class ClientParty {
    private static final PartyStateS2C EMPTY = new PartyStateS2C(false, List.of(), List.of());
    private static PartyStateS2C state = EMPTY;
    private static long receivedAt;
    /** 受け取るたびに増える（画面がボタンを作り直すのに使う） */
    private static int version;

    private ClientParty() {
    }

    public static void apply(PartyStateS2C message) {
        state = message;
        receivedAt = ClientDown.now();
        version++;
    }

    public static PartyStateS2C state() {
        return state;
    }

    public static int version() {
        return version;
    }

    /** 受け取ってからの tick（誘いの残り時間を進めるのに使う）。 */
    public static long age() {
        return ClientDown.now() - receivedAt;
    }

    public static boolean inParty() {
        return state.inParty();
    }

    /** 自分がリーダーか（パーティでなければ false）。 */
    public static boolean selfIsLeader() {
        UUID self = selfId();
        for (PartyStateS2C.Member member : state.members()) {
            if (member.id().equals(self)) {
                return member.leader();
            }
        }
        return false;
    }

    public static boolean isMember(UUID id) {
        for (PartyStateS2C.Member member : state.members()) {
            if (member.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static UUID selfId() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? new UUID(0L, 0L) : minecraft.player.getUUID();
    }

    public static void reset() {
        state = EMPTY;
        version++;
    }
}
