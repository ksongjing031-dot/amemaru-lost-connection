package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.ConnectionsScreen;
import jp.amemaru.lostconnection.client.screen.GlowScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * 自動の確認の写真（Phase 7：コネクション量と大結晶の輝き）の段取り：大結晶の台の西の「記憶の石」を右クリックする・
 * 輝きの画面のタブを変える・縁帳を開く・縁帳から結び札を贈る。
 */
final class CiPhase7 {
    /** 記憶の石（中央広場の真ん中から。tools/mapgen/build_city.py）。 */
    private static final int STONE_X = -8;
    private static final int STONE_Y = 1;
    private static final int STONE_Z = 0;

    private CiPhase7() {
    }

    static boolean action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("p7_stone", "p7_glow", "p7_glow_tab", "p7_book", "p7_thanks").contains(kind)) {
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
            default -> {
            }
        }
        return true;
    }
}
