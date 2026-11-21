package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 塔の階の定義の置き場と、塔のディメンション {@code amemaru:tower}（Phase 0-B 2章「ワールドの構成」：空だけの世界。
 * 各階を離れた座標に建てる）。
 */
public final class FloorDefs {
    /** 塔のディメンション。 */
    public static final ResourceKey<Level> TOWER = ResourceKey.create(Registries.DIMENSION, AmemaruMod.id("tower"));
    /** 階の数（Phase 0-A 9章：全20階）。 */
    public static final int FLOOR_COUNT = 20;

    private static final DefinitionLoader<FloorDef> LOADER = new DefinitionLoader<>("floors", FloorDef::parse);

    private FloorDefs() {
    }

    public static DefinitionLoader<FloorDef> loader() {
        return LOADER;
    }

    /** その番号の階（定義がなければ null）。 */
    @Nullable
    public static FloorDef byNumber(int number) {
        for (FloorDef def : LOADER.all().values()) {
            if (def.number() == number) {
                return def;
            }
        }
        return null;
    }

    /** 定義のある階（番号の順）。 */
    public static List<FloorDef> all() {
        List<FloorDef> list = new ArrayList<>(LOADER.all().values());
        list.sort(Comparator.comparingInt(FloorDef::number));
        return list;
    }

    /** 塔のディメンション（データパックで作る。なければ null）。 */
    @Nullable
    public static ServerLevel towerLevel(MinecraftServer server) {
        return server.getLevel(TOWER);
    }

    /** その場所がどの階か（塔の外なら null）。 */
    @Nullable
    public static FloorDef floorAt(Level level, Vec3 pos) {
        if (level.dimension() != TOWER) {
            return null;
        }
        for (FloorDef def : LOADER.all().values()) {
            if (def.contains(pos)) {
                return def;
            }
        }
        return null;
    }
}
