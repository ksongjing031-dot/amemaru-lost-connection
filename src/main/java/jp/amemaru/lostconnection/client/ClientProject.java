package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.ProjectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * 寄付と共同事業（クライアント。Phase 7 その4）：寄付台の画面を開く・寄付のあとに書きかえる。
 */
public final class ClientProject {
    private ClientProject() {
    }

    public static void handle(String kind, CompoundTag data) {
        if (!"screen".equals(kind)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ProjectScreen screen) {
            screen.update(data);
        } else if (!data.getBoolean("refresh")) {
            minecraft.setScreen(new ProjectScreen(data));
        }
    }
}
