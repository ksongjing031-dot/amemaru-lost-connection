package jp.amemaru.lostconnection.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * フリーマーケットの露店の区画と、市場監督の小屋の位置（{@code data/<名前空間>/amemaru/market_stalls/*.json}。Phase 6）。
 * 位置はどれも、街の真ん中（{@code /amemaru admin city build} の origin）からのずれ。{@code tools/datagen/build_stalls.py} が、
 * フリーマーケットの組み立て（{@code tools/mapgen/build_freemarket.py}）の区画から書き出す。
 */
public final class MarketStalls {
    /** 範囲（両端を含む） */
    public record Box(BlockPos from, BlockPos to) {
        public boolean contains(BlockPos rel) {
            return rel.getX() >= from.getX() && rel.getX() <= to.getX() && rel.getY() >= from.getY() && rel.getY() <= to.getY()
                    && rel.getZ() >= from.getZ() && rel.getZ() <= to.getZ();
        }

        public Vec3 center() {
            return new Vec3((from.getX() + to.getX() + 1) / 2.0, (from.getY() + to.getY() + 1) / 2.0, (from.getZ() + to.getZ() + 1) / 2.0);
        }

        /** 範囲までの距離の2乗（中なら0）。rel は街の真ん中からのずれ。 */
        public double distanceSqr(Vec3 rel) {
            double dx = Math.max(0, Math.max(from.getX() - rel.x, rel.x - (to.getX() + 1)));
            double dy = Math.max(0, Math.max(from.getY() - rel.y, rel.y - (to.getY() + 1)));
            double dz = Math.max(0, Math.max(from.getZ() - rel.z, rel.z - (to.getZ() + 1)));
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /** 露店の区画。stage は base（はじめの12）か more（寄付で開く8） */
    public record Stall(int num, boolean more, String title, Box box, Vec3 name, List<Vec3> displays) {
    }

    /** 1つのファイル */
    public record Layout(@Nullable Box booth, List<BlockPos> boothExcept, List<Stall> stalls) {
    }

    private static final DefinitionLoader<Layout> LOADER = new DefinitionLoader<>("market_stalls", MarketStalls::parse);

    private MarketStalls() {
    }

    public static DefinitionLoader<Layout> loader() {
        return LOADER;
    }

    static Layout parse(ResourceLocation id, JsonObject json) {
        Box booth = null;
        List<BlockPos> except = new ArrayList<>();
        JsonObject boothJson = Json.optionalObject(json, "booth");
        if (boothJson != null) {
            booth = box(Json.object(boothJson, "box"));
            for (JsonElement element : Json.optionalArray(boothJson, "except")) {
                except.add(pos(element.getAsJsonArray(), "except"));
            }
        }
        List<Stall> stalls = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(json, "stalls")) {
            JsonObject entry = Json.asObject(element, "stalls");
            int num = Json.intInRange(entry, "num", 1, 99);
            boolean more = "more".equals(Json.optionalString(entry, "stage", "base"));
            String title = Json.optionalString(entry, "title", "");
            Box box = box(Json.object(entry, "box"));
            Vec3 name = vec(Json.array(entry, "name"), "name");
            List<Vec3> displays = new ArrayList<>();
            for (JsonElement d : Json.optionalArray(entry, "displays")) {
                displays.add(vec(d.getAsJsonArray(), "displays"));
            }
            stalls.add(new Stall(num, more, title, box, name, List.copyOf(displays)));
        }
        return new Layout(booth, List.copyOf(except), List.copyOf(stalls));
    }

    private static Box box(JsonObject json) {
        BlockPos a = pos(Json.array(json, "from"), "from");
        BlockPos b = pos(Json.array(json, "to"), "to");
        return new Box(new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
    }

    private static BlockPos pos(JsonArray array, String name) {
        if (array.size() != 3) {
            throw new DefinitionException("「" + name + "」は [x, y, z] にしてください");
        }
        return new BlockPos((int) Math.floor(Json.asNumber(array.get(0), name)), (int) Math.floor(Json.asNumber(array.get(1), name)),
                (int) Math.floor(Json.asNumber(array.get(2), name)));
    }

    private static Vec3 vec(JsonArray array, String name) {
        if (array.size() != 3) {
            throw new DefinitionException("「" + name + "」は [x, y, z] にしてください");
        }
        return new Vec3(Json.asNumber(array.get(0), name), Json.asNumber(array.get(1), name), Json.asNumber(array.get(2), name));
    }

    /** すべての区画（番号の順。同じ番号はあとのファイルが上書き）。 */
    public static List<Stall> all() {
        Map<Integer, Stall> map = new TreeMap<>();
        for (Layout layout : LOADER.all().values()) {
            for (Stall stall : layout.stalls()) {
                map.put(stall.num(), stall);
            }
        }
        return List.copyOf(map.values());
    }

    @Nullable
    public static Stall get(int num) {
        for (Stall stall : all()) {
            if (stall.num() == num) {
                return stall;
            }
        }
        return null;
    }

    /** 市場監督の小屋（なければ null）。 */
    @Nullable
    public static Box booth() {
        Box found = null;
        for (Layout layout : LOADER.all().values()) {
            if (layout.booth() != null) {
                found = layout.booth();
            }
        }
        return found;
    }

    /** 小屋の範囲のうち、ふつうの動きのままにするマス（書見台・呼び鈴）か。 */
    public static boolean boothExcept(BlockPos rel) {
        for (Layout layout : LOADER.all().values()) {
            if (layout.boothExcept().contains(rel)) {
                return true;
            }
        }
        return false;
    }

    /** その場所（街の真ん中からのずれ）を含む区画（なければ null）。 */
    @Nullable
    public static Stall stallAt(BlockPos rel) {
        for (Stall stall : all()) {
            if (stall.box().contains(rel)) {
                return stall;
            }
        }
        return null;
    }
}
