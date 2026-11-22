package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.ConnectionsScreen;
import jp.amemaru.lostconnection.client.screen.GlowScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * 自動の確認の写真（Phase 7：コネクション量と大結晶の輝き・釣り・カジノ）の段取り：大結晶の台の西の「記憶の石」を右クリックする・
 * 輝きの画面のタブを変える・縁帳を開く・縁帳から結び札を贈る・釣り具屋の画面のタブと図鑑の魚を選ぶ・糸の張りを自動で巻く・
 * カジノのルーレットの卓を右クリックする・カジノの画面のタブとチップの額を選ぶ・賭ける・魚レースに魚を出す・寄付の台座を右クリック
 * する・寄付台の画面で共同事業を選ぶ・寄付する。
 */
final class CiPhase7 {
    /** 記憶の石（中央広場の真ん中から。tools/mapgen/build_city.py）。 */
    private static final int STONE_X = -8;
    private static final int STONE_Y = 1;
    private static final int STONE_Z = 0;
    /** 寄付の台座（中央広場の真ん中から。tools/mapgen/build_city.py の「寄付の台座」の真ん中）。 */
    private static final int STAND_X = 15;
    private static final int STAND_Y = 1;
    private static final int STAND_Z = 15;
    /** カジノのルーレットの卓の目（中央広場の真ん中から。tools/mapgen/build_casino.py の POCKETS の (13, 12)）。 */
    private static final int ROULETTE_X = 26;
    private static final int ROULETTE_Y = 4;
    private static final int ROULETTE_Z = 84;

    private CiPhase7() {
    }

    static boolean action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("p7_stone", "p7_glow", "p7_glow_tab", "p7_book", "p7_thanks", "p7f_tab", "p7f_select", "p7f_auto",
                "p7c_use", "p7c_tab", "p7c_chip", "p7c_bet", "p7c_enter", "p7p_use", "p7p_select", "p7p_donate").contains(kind)) {
            return false;
        }
        if (!first) {
            return true;
        }
        switch (kind) {
            case "p7_stone" -> {
                BlockPos origin = CiScreenshots.cityOrigin();
                if (origin != null) {
                    CiPhase3.useWorkbench(minecraft, origin.offset(STONE_X, STONE_Y, STONE_Z));
                }
            }
            case "p7_glow" -> ClientConnection.send("open", new CompoundTag());
            case "p7_glow_tab" -> {
                if (minecraft.screen instanceof GlowScreen glow) {
                    glow.ciEveryone("everyone".equals(shot[3]));
                }
            }
            case "p7_book" -> ClientGuild.send("connections_open");
            case "p7_thanks" -> {
                if (minecraft.screen instanceof ConnectionsScreen book) {
                    book.ciThanks(Integer.parseInt(shot[3]));
                }
            }
            case "p7f_tab" -> {
                if (minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.FishingScreen screen) {
                    screen.ciTab(shot[3]);
                }
            }
            case "p7f_select" -> {
                if (minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.FishingScreen screen) {
                    screen.ciSelect(Integer.parseInt(shot[3]));
                }
            }
            case "p7f_auto" -> ClientFishing.setAuto("on".equals(shot[3]));
            case "p7c_use" -> {
                BlockPos origin = CiScreenshots.cityOrigin();
                if (origin != null) {
                    CiPhase3.useWorkbench(minecraft, origin.offset(ROULETTE_X, ROULETTE_Y, ROULETTE_Z));
                }
            }
            case "p7c_tab" -> {
                if (minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.CasinoScreen screen) {
                    screen.ciTab(shot[3]);
                }
            }
            case "p7c_chip" -> {
                if (minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.CasinoScreen screen) {
                    screen.ciChip(Long.parseLong(shot[3]));
                }
            }
            case "p7c_bet" -> {
                if (minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.CasinoScreen screen) {
                    String[] parts = shot[3].split(":");
                    screen.ciBet(parts[0], Integer.parseInt(parts[1]));
                }
            }
            case "p7p_use" -> {
                BlockPos origin = CiScreenshots.cityOrigin();
                if (origin != null) {
                    CiPhase3.useWorkbench(minecraft, origin.offset(STAND_X, STAND_Y, STAND_Z));
                }
            }
            case "p7p_select" -> {
                if (minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.ProjectScreen screen) {
                    screen.ciSelect(Integer.parseInt(shot[3]));
                }
            }
            case "p7p_donate" -> {
                if (minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.ProjectScreen screen) {
                    screen.ciDonate(Long.parseLong(shot[3]));
                }
            }
            case "p7c_enter" -> {
                if (minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.CasinoScreen screen) {
                    screen.ciEnter();
                }
            }
            default -> {
            }
        }
        return true;
    }
}
