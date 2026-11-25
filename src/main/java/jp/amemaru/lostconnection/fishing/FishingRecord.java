package jp.amemaru.lostconnection.fishing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * プレイヤーごとの釣りの記録（Phase 7 その2）：魚図鑑（魚ごとの釣った数・いちばん大きいの・初めて釣った進行日）・釣った数の
 * 合計・留め糸と交換したときの点の端数。
 */
public final class FishingRecord {
    /** 図鑑の一行。 */
    public static final class Entry {
        int count;
        double best;
        int firstDay;

        public int count() {
            return count;
        }

        /** いちばん大きいの（cm）。 */
        public double best() {
            return best;
        }

        public int firstDay() {
            return firstDay;
        }
    }

    private final Runnable onChange;
    private final Map<String, Entry> dex = new LinkedHashMap<>();
    private int total;
    private int points;

    public FishingRecord(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * 釣った魚を図鑑に書く。初めての魚なら true。
     */
    public boolean record(ResourceLocation fish, double size, int day) {
        Entry entry = dex.get(fish.toString());
        boolean first = entry == null;
        if (first) {
            entry = new Entry();
            entry.firstDay = day;
            dex.put(fish.toString(), entry);
        }
        entry.count++;
        entry.best = Math.max(entry.best, size);
        total++;
        onChange.run();
        return first;
    }

    @Nullable
    public Entry entry(ResourceLocation fish) {
        return dex.get(fish.toString());
    }

    public boolean has(ResourceLocation fish) {
        return dex.containsKey(fish.toString());
    }

    public Map<String, Entry> dex() {
        return Collections.unmodifiableMap(dex);
    }

    public int species() {
        return dex.size();
    }

    public int total() {
        return total;
    }

    /** 留め糸と交換したときの点の端数。 */
    public int points() {
        return points;
    }

    public void setPoints(int value) {
        points = Math.max(0, value);
        onChange.run();
    }

    /** 管理者：図鑑を空にする。 */
    public void clear() {
        dex.clear();
        total = 0;
        points = 0;
        onChange.run();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag list = new CompoundTag();
        dex.forEach((id, e) -> {
            CompoundTag one = new CompoundTag();
            one.putInt("count", e.count);
            one.putDouble("best", e.best);
            one.putInt("first", e.firstDay);
            list.put(id, one);
        });
        tag.put("dex", list);
        tag.putInt("total", total);
        tag.putInt("points", points);
        return tag;
    }

    public void load(CompoundTag tag) {
        dex.clear();
        CompoundTag list = tag.getCompound("dex");
        for (String id : list.getAllKeys()) {
            CompoundTag one = list.getCompound(id);
            Entry e = new Entry();
            e.count = one.getInt("count");
            e.best = one.getDouble("best");
            e.firstDay = one.getInt("first");
            dex.put(id, e);
        }
        total = tag.getInt("total");
        points = tag.getInt("points");
    }

    public void copyFrom(FishingRecord other) {
        load(other.save());
    }
}
