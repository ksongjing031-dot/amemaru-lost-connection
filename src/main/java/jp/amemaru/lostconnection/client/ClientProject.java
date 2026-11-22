package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.ProjectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * 寄付と共同事業（クライアント。Phase 7 その4）：寄付台の画面・ペトラ・ポムの料理屋の画面を開く・書きかえる。
 */
public final class ClientProject {
    private ClientProject() {
    }

    public static void handle(String kind, CompoundTag data) {
        Minecraft minecraft = Minecraft.getInstance();
        if ("restaurant".equals(kind)) {
            if (minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.RestaurantScreen screen) {
                screen.update(data);
            } else if (!data.getBoolean("refresh")) {
                minecraft.setScreen(new jp.amemaru.lostconnection.client.screen.RestaurantScreen(data));
            }
            return;
        }
        if (!"screen".equals(kind)) {
            return;
        }
        if (minecraft.screen instanceof ProjectScreen screen) {
            screen.update(data);
        } else if (!data.getBoolean("refresh")) {
            minecraft.setScreen(new ProjectScreen(data));
        }
    }
}
