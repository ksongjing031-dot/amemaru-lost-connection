package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.parlor.PachinkoScreen;
import jp.amemaru.lostconnection.client.parlor.SlotScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 自動の確認の写真（11月27日：カジノの地下遊技場）の段取り：台・島の掲示板を右クリックする・パチンコのハンドル・スロットのレバーと
 * 止めボタン（目押し）。台の位置は tools/mapgen/parlor_layout.py（中央広場の真ん中から）。
 */
final class CiParlor {
    /** 1番台（パチンコ「輝きの塔」。島 b1 の西の列の北の端）・101番台（スロット「ノットランプ」。島 b3 の西の列の北の端）・島 b1 の掲示板。 */
    private static final int[] MACHINE_1 = {17, -8, 81};
    private static final int[] MACHINE_101 = {30, -8, 81};
    private static final int[] BOARD_B1 = {17, -8, 80};

    private CiParlor() {
    }

    static boolean action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("p8c_use", "p8c_board", "p8c_shoot", "p8c_lever", "p8c_aim").contains(kind)) {
            return false;
        }
        if (!first) {
            return true;
        }
        switch (kind) {
            case "p8c_use" -> {
                BlockPos origin = CiScreenshots.cityOrigin();
                int[] at = "101".equals(shot[3]) ? MACHINE_101 : MACHINE_1;
                if (origin != null) {
                    CiPhase3.useWorkbench(minecraft, origin.offset(at[0], at[1], at[2]));
                }
            }
            case "p8c_board" -> {
                BlockPos origin = CiScreenshots.cityOrigin();
                if (origin != null) {
                    CiPhase3.useWorkbench(minecraft, origin.offset(BOARD_B1[0], BOARD_B1[1], BOARD_B1[2]));
                }
            }
            case "p8c_shoot" -> {
                if (minecraft.screen instanceof PachinkoScreen screen) {
                    String[] p = shot[3].split(":");
                    screen.ciHandle("on".equals(p[0]), "right".equals(p[1]), Integer.parseInt(p[2]));
                }
            }
            case "p8c_lever" -> {
                if (minecraft.screen instanceof SlotScreen screen) {
                    screen.ciLever();
                }
            }
            case "p8c_aim" -> {
                if (minecraft.screen instanceof SlotScreen screen) {
                    String[] p = shot[3].split(":");
                    screen.ciAim(Integer.parseInt(p[0]), p[1].charAt(0));
                }
            }
            default -> {
            }
        }
        return true;
    }
}
