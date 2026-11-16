package jp.amemaru.lostconnection.core;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.data.DataReport;
import jp.amemaru.lostconnection.core.world.GameRuleSetup;
import jp.amemaru.lostconnection.player.GrowthRules;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * サーバー全体に関わるイベント：データパックの読み込み・起動時の準備・{@code /reload} の後の処理。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {
    }

    /** データパックの読み込み処理を登録する（起動時と /reload のたび）。 */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(GrowthRules.loader());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (ServerConfig.APPLY_GAME_RULES.get()) {
            GameRuleSetup.apply(event.getServer());
        }
    }

    /**
     * データパックをプレイヤーに配るとき（ログイン時は1人、/reload の後は全員）。
     * /reload の後は全員のステータスを計算し直し、問題があれば管理者に知らせる。
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer joining = event.getPlayer();
        if (joining != null) {
            DataReport.notifyAdmins(List.of(joining));
            return;
        }
        List<ServerPlayer> players = event.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            PlayerDataCapability.get(player).ifPresent(data -> LevelService.recompute(player, data));
        }
        DataReport.notifyAdmins(players);
    }
}
