package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.fx.StatusVisuals;
import jp.amemaru.lostconnection.client.screen.JobSelectScreen;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.OpenJobScreenS2C;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.SkillResultS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import jp.amemaru.lostconnection.network.s2c.SyncPlayerDataS2C;
import net.minecraft.client.Minecraft;

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

    public static void handleDefinitions(SyncDefinitionsS2C message) {
        ClientDefinitions.get().apply(message);
    }

    public static void handleSkillFx(SkillFxS2C message) {
        SkillFx.handle(message);
    }

    public static void handleCombatText(CombatTextS2C message) {
        CombatTextRenderer.handle(message);
        SkillFx.onCombatText(message);
    }

    public static void handleStatusFx(StatusFxS2C message) {
        StatusVisuals.handle(message);
    }

    public static void handleSkillResult(SkillResultS2C message) {
        SkillInput.handleResult(message);
    }

    public static void handleOpenJobScreen(OpenJobScreenS2C message) {
        Minecraft.getInstance().setScreen(new JobSelectScreen(message));
    }
}
