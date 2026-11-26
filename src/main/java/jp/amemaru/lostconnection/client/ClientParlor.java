package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.parlor.MachineScreen;
import jp.amemaru.lostconnection.client.parlor.PachinkoScreen;
import jp.amemaru.lostconnection.client.parlor.ParlorDataScreen;
import jp.amemaru.lostconnection.client.parlor.SlotScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/** 地下遊技場の知らせを、画面へ渡す（11月27日【補完】）。 */
public final class ClientParlor {
    private ClientParlor() {
    }

    public static void handle(String kind, CompoundTag data) {
        Minecraft minecraft = Minecraft.getInstance();
        int no = data.getInt("no");
        MachineScreen open = minecraft.screen instanceof MachineScreen m && m.no() == no ? m : null;
        switch (kind) {
            case "open" -> minecraft.setScreen("slot".equals(data.getString("kind")) ? new SlotScreen(data) : new PachinkoScreen(data));
            case "state" -> {
                if (open != null) {
                    open.onState(data);
                }
            }
            case "spin" -> {
                if (open instanceof PachinkoScreen p) {
                    p.onSpin(data.getString("script"));
                }
            }
            case "slot" -> {
                if (open instanceof SlotScreen s) {
                    s.onSlot(data);
                }
            }
            case "data" -> {
                if (minecraft.screen instanceof ParlorDataScreen screen) {
                    screen.update(data);
                } else {
                    minecraft.setScreen(new ParlorDataScreen(data));
                }
            }
            case "close" -> {
                if (minecraft.screen instanceof MachineScreen m) {
                    m.closeFromServer();
                }
            }
            default -> {
            }
        }
    }
}
