package jp.amemaru.lostconnection.casino.parlor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地下遊技場の決まりと台の並び（{@code casino/default.json} の {@code "parlor"}。tools/datagen/build_parlor.py が書く。11月27日【補完】）。
 * 位置はカジノの右クリックの所と同じく、中央広場の真ん中からの位置（y は道の高さから）。
 *
 * <ul>
 *   <li>{@code balls_per_chip}・{@code medals_per_chip}：チップ1枚で借りられる玉・メダル（交換も同じ数：等価）</li>
 *   <li>{@code lend}：台の画面の「玉を借りる」のチップの数</li>
 *   <li>{@code keep_seconds}・{@code keep_feature_seconds}：台を離れても、その人の台にしておく時間（ボーナス・確変・AT の最中は長く）</li>
 *   <li>{@code reach}：台から離れてよい距離（これより離れると、台を離れたことになる）</li>
 *   <li>{@code new_flag}：2階の新台の間の島（{@code floor: new}）が遊べるようになる物語の印（共同事業⑥）</li>
 * </ul>
 */
public record ParlorRules(int ballsPerChip, int medalsPerChip, List<Integer> lend, int keepSeconds, int keepFeatureSeconds,
                          double reach, ResourceLocation newFlag, int[] counterMin, int[] counterMax, List<Island> islands,
                          List<Machine> machines, Map<Long, Machine> byPos, Map<Integer, Machine> byNo, Map<Long, Island> boards) {

    public record Island(String id, String name, String model, boolean newFloor, List<BlockPos> boards) {
    }

    /** 台（pos は台のブロック。その下は受け皿）。 */
    public record Machine(int no, String model, String island, BlockPos pos, Direction facing, boolean newFloor) {
    }

    public static final ParlorRules EMPTY = new ParlorRules(25, 5, List.of(1, 10, 40), 120, 600, 6.0,
            AmemaruMod.id("project/new_machines"), new int[3], new int[3], List.of(), List.of(), Map.of(), Map.of(), Map.of());

    @Nullable
    public Machine machine(int no) {
        return byNo.get(no);
    }

    /** 中央広場の真ん中からの位置 rel にある台（台のブロックか、その下の受け皿）。 */
    @Nullable
    public Machine machineAt(BlockPos rel) {
        Machine m = byPos.get(rel.asLong());
        return m != null ? m : byPos.get(rel.above().asLong());
    }

    @Nullable
    public Island boardAt(BlockPos rel) {
        return boards.get(rel.asLong());
    }

    @Nullable
    public Island island(String id) {
        for (Island i : islands) {
            if (i.id().equals(id)) {
                return i;
            }
        }
        return null;
    }

    public boolean inCounter(BlockPos rel) {
        return rel.getX() >= counterMin[0] && rel.getX() <= counterMax[0] && rel.getY() >= counterMin[1] && rel.getY() <= counterMax[1]
                && rel.getZ() >= counterMin[2] && rel.getZ() <= counterMax[2];
    }

    public List<Machine> machinesOf(String island) {
        List<Machine> out = new ArrayList<>();
        for (Machine m : machines) {
            if (m.island().equals(island)) {
                out.add(m);
            }
        }
        return out;
    }

    public static ParlorRules parse(@Nullable JsonObject json) {
        if (json == null) {
            return EMPTY;
        }
        int balls = ParlorJson.optInt(json, "balls_per_chip", 25, 1, 1000);
        int medals = ParlorJson.optInt(json, "medals_per_chip", 5, 1, 1000);
        List<Integer> lend = new ArrayList<>();
        JsonArray lendJson = ParlorJson.optArray(json, "lend");
        if (lendJson != null) {
            for (JsonElement e : lendJson) {
                lend.add(Math.max(1, Math.min(1000, e.getAsInt())));
            }
        }
        if (lend.isEmpty()) {
            lend.add(10);
        }
        ResourceLocation flag = ResourceLocation.tryParse(ParlorJson.optString(json, "new_flag", "amemaru:project/new_machines"));
        if (flag == null) {
            throw new DefinitionException("「parlor.new_flag」が読めません");
        }
        int[] cmin = new int[3];
        int[] cmax = new int[3];
        JsonObject counter = ParlorJson.optObject(json, "counter");
        if (counter != null) {
            cmin = triple(ParlorJson.array(counter, "min"));
            cmax = triple(ParlorJson.array(counter, "max"));
        }
        List<Island> islands = new ArrayList<>();
        Map<Long, Island> boards = new HashMap<>();
        JsonArray islandJson = ParlorJson.optArray(json, "islands");
        if (islandJson != null) {
            for (JsonElement e : islandJson) {
                JsonObject o = e.getAsJsonObject();
                List<BlockPos> bs = new ArrayList<>();
                JsonArray b = ParlorJson.optArray(o, "boards");
                if (b != null) {
                    for (JsonElement p : b) {
                        int[] t = triple(p.getAsJsonArray());
                        bs.add(new BlockPos(t[0], t[1], t[2]));
                    }
                }
                Island island = new Island(ParlorJson.string(o, "id"), ParlorJson.string(o, "name"), ParlorJson.string(o, "model"),
                        "new".equals(ParlorJson.optString(o, "floor", "base")), List.copyOf(bs));
                islands.add(island);
                for (BlockPos p : bs) {
                    boards.put(p.asLong(), island);
                }
            }
        }
        List<Machine> machines = new ArrayList<>();
        Map<Long, Machine> byPos = new HashMap<>();
        Map<Integer, Machine> byNo = new HashMap<>();
        JsonArray machineJson = ParlorJson.optArray(json, "machines");
        if (machineJson != null) {
            for (JsonElement e : machineJson) {
                JsonObject o = e.getAsJsonObject();
                int no = ParlorJson.optInt(o, "no", 0, 1, 9999);
                String islandId = ParlorJson.string(o, "island");
                Island island = null;
                for (Island i : islands) {
                    if (i.id().equals(islandId)) {
                        island = i;
                    }
                }
                if (island == null) {
                    throw new DefinitionException("台 " + no + " の島「" + islandId + "」が「islands」にありません");
                }
                int[] t = triple(ParlorJson.array(o, "pos"));
                Direction facing = Direction.byName(ParlorJson.optString(o, "facing", "north"));
                if (facing == null || facing.getAxis().isVertical()) {
                    throw new DefinitionException("台 " + no + " の「facing」は north・south・east・west です");
                }
                Machine m = new Machine(no, ParlorJson.string(o, "model"), islandId, new BlockPos(t[0], t[1], t[2]), facing,
                        island.newFloor());
                if (byNo.put(no, m) != null) {
                    throw new DefinitionException("台番号 " + no + " が2つあります");
                }
                machines.add(m);
                byPos.put(m.pos().asLong(), m);
            }
        }
        return new ParlorRules(balls, medals, List.copyOf(lend), ParlorJson.optInt(json, "keep_seconds", 120, 0, 36000),
                ParlorJson.optInt(json, "keep_feature_seconds", 600, 0, 36000), ParlorJson.optNumber(json, "reach", 6, 1, 64),
                flag, cmin, cmax, Collections.unmodifiableList(islands), Collections.unmodifiableList(machines),
                Collections.unmodifiableMap(byPos), Collections.unmodifiableMap(byNo), Collections.unmodifiableMap(boards));
    }

    private static int[] triple(JsonArray a) {
        if (a.size() != 3) {
            throw new DefinitionException("位置は [x, y, z] の3つの数です");
        }
        return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
    }
}
