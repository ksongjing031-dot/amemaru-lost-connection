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
    /** p8_bench で置いた作業台。 */
    private static BlockPos bench;

    private CiPhase8() {
    }

    /** その種類の行なら true（first のときだけ動かす）。 */
    static boolean action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("p8_bell", "p8_listen", "p8_knot", "p8_hold", "p8_valve", "p8_bench", "p8_bench_open", "p8_charm", "p8_lock")
                .contains(kind)) {
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
            // 11月24日：ロックオンのキー（B）を1回押したことにする（見ている敵をロック。照準の印と名前・距離・HPを撮る）
            case "p8_lock" -> net.minecraft.client.KeyMapping.click(KeyBindings.LOCK_ON.getKey());
            // 11月24日：お守りの枠に付ける（「-」で外す）。5階のユニーク「第七の門鍵」の、体に表示するモデルを撮る
            case "p8_charm" -> CiPhase3.onServer(minecraft, player -> CiPhase3.setCurio(player,
                    jp.amemaru.lostconnection.accessory.AccessorySlot.CHARM,
                    "-".equals(shot[3]) ? net.minecraft.world.item.ItemStack.EMPTY : CiPhase3.accessory(shot[3], 1, 1.0)));
            // Phase 8 その2：3階の水門のハンドルの前（2マス）に立って、回す（「組:0か1」）
            case "p8_valve" -> CiPhase3.onServer(minecraft, player -> turnValve(player, shot[3]));
            // Phase 8 その2：目の前（北へ1ブロック）に工房の作業台（armor・weapon・accessory）を置き、次の行で開く
            case "p8_bench" -> {
                bench = minecraft.player == null ? null : minecraft.player.blockPosition().north(1);
                if (bench != null) {
                    CiScreenshots.runCommand(minecraft, "setblock " + bench.getX() + " " + bench.getY() + " " + bench.getZ()
                            + " amemaru:" + shot[3] + "_workbench[facing=south]");
                }
            }
            case "p8_bench_open" -> {
                if (bench != null) {
                    CiPhase3.useWorkbench(minecraft, bench);
                }
            }
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

    private static void turnValve(ServerPlayer player, String spec) {
        String[] parts = spec.split(":");
        FloorDef def = FloorDefs.byNumber(3);
        ServerLevel level = FloorDefs.towerLevel(player.server);
        FloorFeatures.Floodgate gate = def == null ? null : def.features().floodgate(parts[0]);
        if (gate == null || level == null || parts.length < 2) {
            return;
        }
        int side = Integer.parseInt(parts[1]);
        BlockPos valve = def.world(gate.valves().get(side));
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(valve);
        net.minecraft.core.Direction facing = state.hasProperty(jp.amemaru.lostconnection.tower.waterway.WaterwayBlocks.FACING)
                ? state.getValue(jp.amemaru.lostconnection.tower.waterway.WaterwayBlocks.FACING) : net.minecraft.core.Direction.SOUTH;
        double x = valve.getX() + 0.5 + facing.getStepX() * 2.0;
        double z = valve.getZ() + 0.5 + facing.getStepZ() * 2.0;
        player.teleportTo(level, x, valve.getY(), z, facing.getOpposite().toYRot(), 20.0F);
        jp.amemaru.lostconnection.tower.waterway.FloodgateService.turn(player, def.number(), gate.id(), side, valve);
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
