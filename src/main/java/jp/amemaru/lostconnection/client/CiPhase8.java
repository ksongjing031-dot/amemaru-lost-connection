package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.forest.LostWoodsService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 自動の確認の写真（Phase 8 その1：2階「忘れられた森」）の段取り：迷いの森の鐘を鳴らす・出口の方でこだまを聞く・
 * 正しい灯台に結び目の灯を置く・品を手に持つ。
 */
final class CiPhase8 {
    private CiPhase8() {
    }

    /** その種類の行なら true（first のときだけ動かす）。 */
    static boolean action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("p8_bell", "p8_listen", "p8_knot", "p8_hold").contains(kind)) {
            return false;
        }
        if (!first) {
            return true;
        }
        switch (kind) {
            // 鐘の南2マスに立って鐘を鳴らす（結び目の灯ももらえる）
            case "p8_bell" -> CiPhase3.onServer(minecraft, player -> withWoods(player, shot[3], (def, woods, level) -> {
                BlockPos bell = def.world(woods.bell());
                player.teleportTo(level, bell.getX() + 0.5, bell.getY(), bell.getZ() + 2.5, 180.0F, 10.0F);
                LostWoodsService.ringBell(player, def.number(), woods.id(), bell);
            }));
            // 間違いの出口の灯台のそば（鐘から十分はなれた所）に立って、こだまの方角を聞く
            case "p8_listen" -> CiPhase3.onServer(minecraft, player -> withWoods(player, shot[3], (def, woods, level) -> {
                int correct = woods.exits().indexOf(woods.correctExit());
                FloorFeatures.Exit other = woods.exits().get((correct + woods.exits().size() - 1) % woods.exits().size());
                BlockPos post = def.world(other.post());
                player.teleportTo(level, post.getX() - 1.5, post.getY(), post.getZ() + 0.5, 180.0F, 0.0F);
            }));
            // 正しい灯台の南に立って、結び目の灯を置く（区画がつながり、灯台が灯る）
            case "p8_knot" -> CiPhase3.onServer(minecraft, player -> withWoods(player, shot[3], (def, woods, level) -> {
                FloorFeatures.Exit exit = woods.correctExit();
                if (exit == null) {
                    return;
                }
                BlockPos post = def.world(exit.post());
                player.teleportTo(level, post.getX() + 0.5, post.getY(), post.getZ() + 3.5, 180.0F, 18.0F);
                LostWoodsService.placeKnot(player, def.number(), woods.id(), woods.exits().indexOf(exit), post);
            }));
            case "p8_hold" -> CiPhase3.onServer(minecraft, player -> CiGear.hold(player, "-".equals(shot[3]) ? null : shot[3], null));
            default -> {
            }
        }
        return true;
    }

    /** 地面の印（{@link jp.amemaru.lostconnection.entity.GroundMarker}）が見えている。 */
    static boolean anyGroundMarker(Minecraft minecraft) {
        if (minecraft.level == null) {
            return false;
        }
        for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof jp.amemaru.lostconnection.entity.GroundMarker) {
                return true;
            }
        }
        return false;
    }

    private interface WoodsAction {
        void run(FloorDef def, FloorFeatures.LostWoods woods, ServerLevel level);
    }

    private static void withWoods(ServerPlayer player, String id, WoodsAction action) {
        FloorDef def = FloorDefs.byNumber(2);
        ServerLevel level = FloorDefs.towerLevel(player.server);
        FloorFeatures.LostWoods woods = def == null ? null : def.features().lostWoods(id);
        if (woods != null && level != null) {
            action.run(def, woods, level);
        }
    }
}
