package jp.amemaru.lostconnection.tower;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** 塔の階を建てたか（{@code amemaru_tower.dat}）。建てていない階には、塔の門から行けない（「準備中」）。 */
public final class TowerData extends SavedData {
    private static final String NAME = "amemaru_tower";
    private final Set<Integer> built = new TreeSet<>();

    public static TowerData load(CompoundTag tag) {
        TowerData data = new TowerData();
        for (Tag t : tag.getList("built", Tag.TAG_INT)) {
            data.built.add(((IntTag) t).getAsInt());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (int floor : built) {
            list.add(IntTag.valueOf(floor));
        }
        tag.put("built", list);
        return tag;
    }

    public static TowerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TowerData::load, TowerData::new, NAME);
    }

    public boolean isBuilt(int floor) {
        return built.contains(floor);
    }

    public void markBuilt(int floor) {
        if (built.add(floor)) {
            setDirty();
        }
    }

    public Set<Integer> built() {
        return Collections.unmodifiableSet(built);
    }
}
