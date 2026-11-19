package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.SyncAdventureS2C;
import jp.amemaru.lostconnection.network.s2c.SyncPlayerDataS2C;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.server.level.ServerPlayer;

/**
 * 本人の画面へのデータの同期。同じ tick に起きた変更は、tick の終わりにまとめて1回だけ送る。
 *
 * <p>冒険の記録（{@link PlayerData#SYNC_ADVENTURE}。結晶果・生産職・図鑑など）は、別のパケット
 * （{@link SyncAdventureS2C}）でまとめて送る。</p>
 */
public final class PlayerSync {
    private PlayerSync() {
    }

    /** 変わったまとまりがあれば送る。 */
    public static void flush(ServerPlayer player, PlayerData data) {
        int sections = data.consumeDirty();
        if ((sections & PlayerData.SYNC_ADVENTURE) != 0) {
            ModNetwork.sendTo(player, SyncAdventureS2C.of(player, data));
            sections &= ~PlayerData.SYNC_ADVENTURE;
        }
        if (sections != 0 && data.isStatsReady()) {
            ModNetwork.sendTo(player, SyncPlayerDataS2C.of(data, GrowthRules.current(),
                    ProgressionData.currentLevelCap(player.server), sections, player.level().getGameTime()));
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
