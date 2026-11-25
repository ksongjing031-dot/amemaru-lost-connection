package jp.amemaru.lostconnection.fishing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * サーバー全体の釣りの記録（Phase 7 その2）。ワールド（オーバーワールド）に保存する。
 *
 * <ul>
 *   <li>湖の主「記憶の主」を最初に釣った人（Phase 0-A 23章：ポッカの五十年竿を譲る相手。ユニーク装備は Phase 8）</li>
 *   <li>釣り大会：何回目か・始まる時刻と終わる時刻（実時間）・会場・人ごとのいちばんの一匹</li>
 *   <li>大会の賞品のうち、終わったときにいなかった人の分（次に入ったときに渡す）</li>
 * </ul>
 */
public final class FishingData extends SavedData {
    private static final String NAME = "amemaru_fishing";

    /** 湖の主を最初に釣った人。 */
    public record LordCatch(UUID who, String name, int day, long millis, double size) {
    }

    /** 大会で、その人のいちばんの一匹（at：その一匹を釣った時刻。同じ点なら先に釣った人が上）。 */
    public record Best(UUID who, String name, String fish, double size, long score, int catches, long at) {
    }

    @Nullable
    private LordCatch lord;
    private int held;
    /** 大会：0 ＝ していない。始まる前は startMillis が先。 */
    private long startMillis;
    private long endMillis;
    private boolean lakeVenue;
    private final Map<UUID, Best> scores = new LinkedHashMap<>();
    /** いない人の賞品（[ノット, 留め糸の数]）。 */
    private final Map<UUID, List<long[]>> prizes = new LinkedHashMap<>();

    public static FishingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FishingData::load, FishingData::new, NAME);
    }

    // ---------------------------------------------------------------- 湖の主

    @Nullable
    public LordCatch lord() {
        return lord;
    }

    void setLord(@Nullable LordCatch value) {
        lord = value;
        setDirty();
    }

    // ---------------------------------------------------------------- 大会

    public int held() {
        return held;
    }

    /** 大会の予定・最中か。 */
    public boolean tournamentOn() {
        return endMillis > 0;
    }

    public long startMillis() {
        return startMillis;
    }

    public long endMillis() {
        return endMillis;
    }

    public boolean lakeVenue() {
        return lakeVenue;
    }

    void startTournament(long start, long end, boolean lake) {
        held++;
        startMillis = start;
        endMillis = end;
        lakeVenue = lake;
        scores.clear();
        setDirty();
    }

    void endTournament() {
        startMillis = 0;
        endMillis = 0;
        setDirty();
    }

    /** 一匹を数える（その人のいちばんより点が高ければ入れ替える）。釣った数は毎回1つ増える。 */
    void score(UUID who, String name, String fish, double size, long score) {
        Best old = scores.get(who);
        int catches = old == null ? 1 : old.catches() + 1;
        if (old == null || score > old.score()) {
            scores.put(who, new Best(who, name, fish, size, score, catches, System.currentTimeMillis()));
        } else {
            scores.put(who, new Best(who, old.name(), old.fish(), old.size(), old.score(), catches, old.at()));
        }
        setDirty();
    }

    /** 順位（点の高い順。同じなら先に釣った人）。 */
    public List<Best> ranking() {
        List<Best> list = new ArrayList<>(scores.values());
        list.sort(Comparator.comparingLong(Best::score).reversed().thenComparingLong(Best::at));
        return Collections.unmodifiableList(list);
    }

    @Nullable
    public Best best(UUID who) {
        return scores.get(who);
    }

    // ---------------------------------------------------------------- いない人の賞品

    void addPrize(UUID who, long knots, int threads) {
        prizes.computeIfAbsent(who, k -> new ArrayList<>()).add(new long[]{knots, threads});
        setDirty();
    }

    List<long[]> takePrizes(UUID who) {
        List<long[]> list = prizes.remove(who);
        if (list != null) {
            setDirty();
        }
        return list == null ? List.of() : list;
    }

    // ---------------------------------------------------------------- 保存

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (lord != null) {
            CompoundTag one = new CompoundTag();
            one.putUUID("who", lord.who());
            one.putString("name", lord.name());
            one.putInt("day", lord.day());
            one.putLong("millis", lord.millis());
            one.putDouble("size", lord.size());
            tag.put("lord", one);
        }
        tag.putInt("held", held);
        tag.putLong("start", startMillis);
        tag.putLong("end", endMillis);
        tag.putBoolean("lake", lakeVenue);
        ListTag list = new ListTag();
        for (Best b : scores.values()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("who", b.who());
            one.putString("name", b.name());
            one.putString("fish", b.fish());
            one.putDouble("size", b.size());
            one.putLong("score", b.score());
            one.putInt("catches", b.catches());
            one.putLong("at", b.at());
            list.add(one);
        }
        tag.put("scores", list);
        ListTag waiting = new ListTag();
        prizes.forEach((who, entries) -> {
            for (long[] p : entries) {
                CompoundTag one = new CompoundTag();
                one.putUUID("who", who);
                one.putLong("knots", p[0]);
                one.putLong("threads", p[1]);
                waiting.add(one);
            }
        });
        tag.put("prizes", waiting);
        return tag;
    }

    static FishingData load(CompoundTag tag) {
        FishingData data = new FishingData();
        if (tag.contains("lord", Tag.TAG_COMPOUND)) {
            CompoundTag one = tag.getCompound("lord");
            if (one.hasUUID("who")) {
                data.lord = new LordCatch(one.getUUID("who"), one.getString("name"), one.getInt("day"), one.getLong("millis"),
                        one.getDouble("size"));
            }
        }
        data.held = tag.getInt("held");
        data.startMillis = tag.getLong("start");
        data.endMillis = tag.getLong("end");
        data.lakeVenue = tag.getBoolean("lake");
        for (Tag t : tag.getList("scores", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            if (one.hasUUID("who")) {
                data.scores.put(one.getUUID("who"), new Best(one.getUUID("who"), one.getString("name"), one.getString("fish"),
                        one.getDouble("size"), one.getLong("score"), one.getInt("catches"), one.getLong("at")));
            }
        }
        for (Tag t : tag.getList("prizes", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            if (one.hasUUID("who")) {
                data.prizes.computeIfAbsent(one.getUUID("who"), k -> new ArrayList<>())
                        .add(new long[]{one.getLong("knots"), one.getLong("threads")});
            }
        }
        return data;
    }
}
