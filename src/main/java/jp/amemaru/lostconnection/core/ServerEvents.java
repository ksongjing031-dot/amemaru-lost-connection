package jp.amemaru.lostconnection.core;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.data.DataReport;
import jp.amemaru.lostconnection.core.world.GameRuleSetup;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import jp.amemaru.lostconnection.player.GrowthRules;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.PlayerSync;
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
        event.addListener(SkillRegistry.jobLoader());
        event.addListener(SkillRegistry.skillLoader());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (ServerConfig.APPLY_GAME_RULES.get()) {
            GameRuleSetup.apply(event.getServer());
        }
        // 職業とスキルの一覧を作っておく（参照切れなどの問題を、起動時のログに出すため）
        SkillRegistry.get();
    }

    /**
     * データパックをプレイヤーに配るとき（ログイン時は1人、/reload の後は全員）。
     * 職業とスキルの定義を配り、/reload の後は全員のステータスを計算し直して同期し直す。問題があれば管理者に知らせる。
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SyncDefinitionsS2C definitions = SyncDefinitionsS2C.of(SkillRegistry.get());
        ServerPlayer joining = event.getPlayer();
        if (joining != null) {
            ModNetwork.sendTo(joining, definitions);
            DataReport.notifyAdmins(List.of(joining));
            return;
        }
        List<ServerPlayer> players = event.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            ModNetwork.sendTo(player, definitions);
            PlayerDataCapability.get(player).ifPresent(data -> {
                LevelService.recompute(player, data);
                // 定義の番号が変わっているかもしれないので、すべて送り直す
                PlayerSync.syncAll(player, data);
            });
        }
        DataReport.notifyAdmins(players);
    }
}
