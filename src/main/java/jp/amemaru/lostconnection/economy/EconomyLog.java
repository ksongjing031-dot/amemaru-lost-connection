package jp.amemaru.lostconnection.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 通貨の記録（Phase 0-B 4.2 {@code EconomyLog}。Phase 6）。
 *
 * <ul>
 *   <li>進行日ごとの発行量・回収量・移動量（Phase 0-A 20章「通貨の総量と回収量を毎日記録し、管理者が確認できるようにする」）</li>
 *   <li>世界にあるノットの合計（記録を始めてからの、発行 − 回収）</li>
 *   <li>直近の出し入れ（{@link #RECENT_MAX} 件まで）</li>
 * </ul>
 *
 * <p>出し入れはすべて {@link Wallet} を通り、Wallet がここに書く。管理者は {@code /amemaru admin economy} で見る。</p>
 */
public final class EconomyLog extends SavedData {
    private static final String NAME = "amemaru_economy";
    /** 直近の出し入れを何件まで残すか */
    public static final int RECENT_MAX = 500;

    /** 出し入れの種類 */
    public enum Flow {
        /** 発行：世界にノットが増える（敵の報酬・宝箱・店への売却など） */
        ISSUE,
        /** 回収：世界からノットが消える（手数料・倉庫の契約・デスペナルティ・店での買い物など） */
        COLLECT,
        /** 移動：人から人へ（ノットの受け渡し・フリマの売上）。合計は変わらない */
        MOVE
    }

    /** 1件の出し入れ */
    public record Entry(long millis, int day, Flow flow, String reason, String from, String to, long amount) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("t", millis);
            tag.putInt("d", day);
            tag.putString("f", flow.name());
            tag.putString("r", reason);
            tag.putString("from", from);
            tag.putString("to", to);
            tag.putLong("a", amount);
            return tag;
        }

        static Entry load(CompoundTag tag) {
            Flow flow;
            try {
                flow = Flow.valueOf(tag.getString("f"));
            } catch (IllegalArgumentException e) {
                flow = Flow.MOVE;
            }
            return new Entry(tag.getLong("t"), tag.getInt("d"), flow, tag.getString("r"), tag.getString("from"),
                    tag.getString("to"), tag.getLong("a"));
        }
    }

    /** 1日の合計 */
    public static final class DayTotal {
        private long issued;
        private long collected;
        private long moved;
        private int count;

        public long issued() {
            return issued;
        }

        public long collected() {
            return collected;
        }

        public long moved() {
            return moved;
        }

        public int count() {
            return count;
        }
    }

    private final Map<Integer, DayTotal> days = new TreeMap<>();
    private final Deque<Entry> recent = new ArrayDeque<>();
    private long supply;

    public static EconomyLog get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(EconomyLog::load, EconomyLog::new, NAME);
    }

    /** 記録する（0以下は記録しない）。 */
    public void record(int day, Flow flow, String reason, String from, String to, long amount) {
        if (amount <= 0) {
            return;
        }
        DayTotal total = days.computeIfAbsent(day, d -> new DayTotal());
        switch (flow) {
            case ISSUE -> {
                total.issued = add(total.issued, amount);
                supply = add(supply, amount);
            }
            case COLLECT -> {
                total.collected = add(total.collected, amount);
                supply = Math.max(0L, supply - amount);
            }
            case MOVE -> total.moved = add(total.moved, amount);
        }
        total.count++;
        recent.addLast(new Entry(System.currentTimeMillis(), day, flow, reason, from, to, amount));
        while (recent.size() > RECENT_MAX) {
            recent.removeFirst();
        }
        setDirty();
    }

    /** 世界にあるノットの合計（記録を始めてからの発行 − 回収）。 */
    public long supply() {
        return supply;
    }

    /** その日の合計（なければ0の合計）。 */
    public DayTotal day(int day) {
        DayTotal total = days.get(day);
        return total == null ? new DayTotal() : total;
    }

    /** 記録のある日（古い順）。 */
    public List<Integer> recordedDays() {
        return new ArrayList<>(days.keySet());
    }

    /** 直近の出し入れ（新しい順、{@code limit} 件まで）。 */
    public List<Entry> recent(int limit) {
        List<Entry> list = new ArrayList<>(recent);
        Collections.reverse(list);
        return list.subList(0, Math.min(Math.max(0, limit), list.size()));
    }

    private static long add(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    // ---------------------------------------------------------------- 保存

    /** 保存の形から作る（自動テストで、保存して読み直すときに使う）。 */
    public static EconomyLog fromTag(CompoundTag tag) {
        return load(tag);
    }

    static EconomyLog load(CompoundTag tag) {
        EconomyLog log = new EconomyLog();
        log.supply = Math.max(0L, tag.getLong("supply"));
        ListTag dayList = tag.getList("days", Tag.TAG_COMPOUND);
        for (int i = 0; i < dayList.size(); i++) {
            CompoundTag d = dayList.getCompound(i);
            DayTotal total = new DayTotal();
            total.issued = d.getLong("issued");
            total.collected = d.getLong("collected");
            total.moved = d.getLong("moved");
            total.count = d.getInt("count");
            log.days.put(d.getInt("day"), total);
        }
        ListTag entries = tag.getList("recent", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            log.recent.addLast(Entry.load(entries.getCompound(i)));
        }
        while (log.recent.size() > RECENT_MAX) {
            log.recent.removeFirst();
        }
        return log;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("supply", supply);
        ListTag dayList = new ListTag();
        for (Map.Entry<Integer, DayTotal> e : days.entrySet()) {
            CompoundTag d = new CompoundTag();
            d.putInt("day", e.getKey());
            d.putLong("issued", e.getValue().issued);
            d.putLong("collected", e.getValue().collected);
            d.putLong("moved", e.getValue().moved);
            d.putInt("count", e.getValue().count);
            dayList.add(d);
        }
        tag.put("days", dayList);
        ListTag entries = new ListTag();
        for (Entry entry : recent) {
            entries.add(entry.save());
        }
        tag.put("recent", entries);
        return tag;
    }
}
