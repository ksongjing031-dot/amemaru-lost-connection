package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.CasinoScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;

/**
 * カジノ（クライアント。Phase 7 その3）：サーバーからの知らせで、カジノの画面を開く・書きかえる。
 */
public final class ClientCasino {
    private ClientCasino() {
    }

    public static void handle(String kind, CompoundTag data) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (kind) {
            case "screen" -> {
                if (minecraft.screen instanceof CasinoScreen screen) {
                    // 開いている画面を書きかえる（閉じて開き直すと、卓を離れたことになるため）
                    if (data.getBoolean("refresh")) {
                        screen.update(data);
                    } else {
                        screen.reopen(data);
                    }
                } else if (!data.getBoolean("refresh")) {
                    minecraft.setScreen(new CasinoScreen(data));
                }
            }
            case "table" -> {
                if (minecraft.screen instanceof CasinoScreen screen) {
                    boolean rollingNow = "rolling".equals(data.getString("phase")) && !screen.isRolling();
                    screen.setState(data);
                    if (rollingNow && minecraft.player != null) {
                        minecraft.player.playSound(SoundEvents.UI_LOOM_TAKE_RESULT, 0.5F, 1.4F);
                    }
                }
            }
            default -> {
            }
        }
    }
}
