package jp.amemaru.lostconnection.progression;

import jp.amemaru.lostconnection.player.GrowthRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * サーバー全体の進行（Phase 0-B 4.2 {@code ProgressionData}）。ワールドの data フォルダの {@code amemaru_progression.dat} に保存する。
 *
 * <p>持つもの：</p>
 * <ul>
 *   <li>進行日（Phase 2）。管理者が {@code /amemaru admin day next} などで進める（日付では進まない。Phase 0-B 10章）。</li>
 *   <li>章（Phase 3）。進行日から決まる（第1章1〜5日、第2章6〜10日、第3章11〜15日、第4章16〜20日。Phase 0-A 11章）。
 *       章ごとにレベルの上限と生産の熟練度の上限が開く。日と章の対応は {@code growth/default.json} の {@code chapters}。</li>
 *   <li>プレイヤーごとの今のレベル（Phase 3）。経験値の追いつき補正（サーバーのレベルの中央値より低い人は経験値が増える。
 *       Phase 0-A 19章）の中央値を出すために使う。ログアウト中の人も数に入る。</li>
 *   <li>Phase 5：解放済みの階層、予告中の解放とイベント（予告とカウントダウン）、各階の初撃破（日時とメンバー）、
 *       「結晶の加護」の段（ボスへの与ダメージ +5% ずつ、最大 +20%。Phase 0-A 11章）。</li>
 * </ul>
 */
public final class ProgressionData extends SavedData {
    private static final String NAME = "amemaru_progression";
    /** 進行日の上限（イベントは20日間だが、延長に備えて余裕を持たせる）。 */
    public static final int MAX_DAY = 365;
    /** 結晶の加護の段の上限（+5% × 4 = +20%）。 */
    public static final int MAX_BLESSING = 4;

    private int day = 1;
    private final Map<UUID, Integer> levels = new HashMap<>();
    /** 中央値の計算結果（レベルが変わるまで使い回す）。負の値は「まだ計算していない」。 */
    private int cachedMedian = -1;

    // ---- Phase 5
    /** 解放の初期化（進行日の予定に合わせて、今の日までの階を開く）を済ませたか。 */
    private boolean floorsInitialized;
    private final TreeSet<Integer> unlockedFloors = new TreeSet<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final TreeMap<Integer, FirstKill> firstKills = new TreeMap<>();
    private final TreeMap<Integer, Integer> blessings = new TreeMap<>();
    private int nextScheduleId = 1;

    /** 予告をつけた操作の種類。 */
    public enum ScheduleKind {
        /** 階を開く（value が階）。 */
        UNLOCK_FLOOR,
        /** 進行日を進める（value が次の日）。 */
        DAY,
        /** 知らせだけ（カウントダウンの後に、全員に知らせる）。 */
        ANNOUNCE
    }

    /** 予告をつけた操作。{@code atMillis} は実際の時刻（サーバーが止まっても数え続ける）。 */
    public record Scheduled(int id, ScheduleKind kind, int value, String text, long atMillis) {
    }

    /** 階の初撃破（日時・その時の進行日・パーティのメンバーの名前）。 */
    public record FirstKill(long millis, int day, List<String> members) {
    }

    public ProgressionData() {
    }

    public static ProgressionData load(CompoundTag tag) {
        ProgressionData data = new ProgressionData();
        data.day = Math.max(1, Math.min(MAX_DAY, tag.contains("day") ? tag.getInt("day") : 1));
        ListTag list = tag.getList("levels", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("id")) {
                data.levels.put(entry.getUUID("id"), Math.max(1, entry.getInt("level")));
            }
        }
        // 版2 → 版3：階層の解放・予告・初撃破・加護が増えた。版2のデータには無いので、起動したときに今の日までの階を開く
        data.floorsInitialized = tag.getBoolean("floorsInitialized");
        for (Tag t : tag.getList("floors", Tag.TAG_INT)) {
            data.unlockedFloors.add(((IntTag) t).getAsInt());
        }
        ListTag sched = tag.getList("scheduled", Tag.TAG_COMPOUND);
        for (int i = 0; i < sched.size(); i++) {
            CompoundTag one = sched.getCompound(i);
            ScheduleKind kind;
            try {
                kind = ScheduleKind.valueOf(one.getString("kind"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            data.scheduled.add(new Scheduled(one.getInt("id"), kind, one.getInt("value"), one.getString("text"), one.getLong("at")));
        }
        data.nextScheduleId = Math.max(1, tag.getInt("nextScheduleId"));
        CompoundTag kills = tag.getCompound("firstKills");
        for (String key : kills.getAllKeys()) {
            try {
                CompoundTag one = kills.getCompound(key);
                List<String> members = new ArrayList<>();
                for (Tag t : one.getList("members", Tag.TAG_STRING)) {
                    members.add(t.getAsString());
                }
                data.firstKills.put(Integer.parseInt(key), new FirstKill(one.getLong("millis"), one.getInt("day"), List.copyOf(members)));
            } catch (NumberFormatException ignored) {
                // 読めない記録は飛ばす
            }
        }
        CompoundTag bless = tag.getCompound("blessings");
        for (String key : bless.getAllKeys()) {
            try {
                data.blessings.put(Integer.parseInt(key), Math.max(0, Math.min(MAX_BLESSING, bless.getInt(key))));
            } catch (NumberFormatException ignored) {
                // 読めない記録は飛ばす
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", 3);
        tag.putInt("day", day);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> entry : levels.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("id", entry.getKey());
            one.putInt("level", entry.getValue());
            list.add(one);
        }
        tag.put("levels", list);
        tag.putBoolean("floorsInitialized", floorsInitialized);
        ListTag floors = new ListTag();
        for (int floor : unlockedFloors) {
            floors.add(IntTag.valueOf(floor));
        }
        tag.put("floors", floors);
        ListTag sched = new ListTag();
        for (Scheduled s : scheduled) {
            CompoundTag one = new CompoundTag();
            one.putInt("id", s.id());
            one.putString("kind", s.kind().name());
            one.putInt("value", s.value());
            one.putString("text", s.text());
            one.putLong("at", s.atMillis());
            sched.add(one);
        }
        tag.put("scheduled", sched);
        tag.putInt("nextScheduleId", nextScheduleId);
        CompoundTag kills = new CompoundTag();
        for (Map.Entry<Integer, FirstKill> entry : firstKills.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putLong("millis", entry.getValue().millis());
            one.putInt("day", entry.getValue().day());
            ListTag members = new ListTag();
            for (String name : entry.getValue().members()) {
                members.add(StringTag.valueOf(name));
            }
            one.put("members", members);
            kills.put(Integer.toString(entry.getKey()), one);
        }
        tag.put("firstKills", kills);
        CompoundTag bless = new CompoundTag();
        for (Map.Entry<Integer, Integer> entry : blessings.entrySet()) {
            bless.putInt(Integer.toString(entry.getKey()), entry.getValue());
        }
        tag.put("blessings", bless);
        return tag;
    }

    /** サーバーの進行のデータ（オーバーワールドに置く）。 */
    public static ProgressionData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(ProgressionData::load, ProgressionData::new, NAME);
    }

    // ---------------------------------------------------------------- 進行日と章

    /** 今の進行日（1から）。 */
    public int day() {
        return day;
    }

    public void setDay(int value) {
        int next = Math.max(1, Math.min(MAX_DAY, value));
        if (next != day) {
            day = next;
            setDirty();
        }
    }

    /** 今の章（1から。進行日から決まる）。 */
    public int chapter() {
        return GrowthRules.current().chapterForDay(day);
    }

    /** サーバーの今の進行日。 */
    public static int currentDay(MinecraftServer server) {
        return get(server).day();
    }

    /** サーバーの今の章。 */
    public static int currentChapter(MinecraftServer server) {
        return get(server).chapter();
    }

    /** サーバーの今のレベルの上限（章の上限）。 */
    public static int currentLevelCap(MinecraftServer server) {
        return GrowthRules.current().levelCap(currentChapter(server));
    }

    // ---------------------------------------------------------------- 階層の解放（Phase 5）

    public boolean floorsInitialized() {
        return floorsInitialized;
    }

    public void markFloorsInitialized() {
        if (!floorsInitialized) {
            floorsInitialized = true;
            setDirty();
        }
    }

    public boolean isUnlocked(int floor) {
        return unlockedFloors.contains(floor);
    }

    /** 開く。もう開いていれば false。 */
    public boolean unlock(int floor) {
        boolean added = unlockedFloors.add(floor);
        if (added) {
            setDirty();
        }
        return added;
    }

    /** 閉じる。開いていなければ false。 */
    public boolean lock(int floor) {
        boolean removed = unlockedFloors.remove(floor);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public Set<Integer> unlockedFloors() {
        return Collections.unmodifiableSet(unlockedFloors);
    }

    // ---------------------------------------------------------------- 予告（Phase 5）

    public Scheduled schedule(ScheduleKind kind, int value, String text, long atMillis) {
        Scheduled s = new Scheduled(nextScheduleId++, kind, value, text, atMillis);
        scheduled.add(s);
        scheduled.sort((a, b) -> Long.compare(a.atMillis(), b.atMillis()));
        setDirty();
        return s;
    }

    public List<Scheduled> scheduled() {
        return Collections.unmodifiableList(scheduled);
    }

    /** 予告を取り消す（なければ false）。 */
    public boolean cancel(int id) {
        boolean removed = scheduled.removeIf(s -> s.id() == id);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** 時刻になった予告を取り出す（取り出した分は消える）。 */
    public List<Scheduled> takeDue(long nowMillis) {
        List<Scheduled> due = new ArrayList<>();
        scheduled.removeIf(s -> {
            if (s.atMillis() <= nowMillis) {
                due.add(s);
                return true;
            }
            return false;
        });
        if (!due.isEmpty()) {
            setDirty();
        }
        return due;
    }

    /** 予告中の、いちばん早い階の解放（なければ null）。 */
    @Nullable
    public Scheduled nextFloorUnlock() {
        for (Scheduled s : scheduled) {
            if (s.kind() == ScheduleKind.UNLOCK_FLOOR) {
                return s;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 初撃破（Phase 5）

    @Nullable
    public FirstKill firstKill(int floor) {
        return firstKills.get(floor);
    }

    /** 初撃破を記録する（もう記録があれば false）。 */
    public boolean recordFirstKill(int floor, long millis, List<String> members) {
        if (firstKills.containsKey(floor)) {
            return false;
        }
        firstKills.put(floor, new FirstKill(millis, day, List.copyOf(members)));
        setDirty();
        return true;
    }

    /** 初撃破の記録を消す（管理者が確かめ直すとき）。 */
    public boolean clearFirstKill(int floor) {
        boolean removed = firstKills.remove(floor) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // ---------------------------------------------------------------- 結晶の加護（Phase 5）

    public int blessing(int floor) {
        return blessings.getOrDefault(floor, 0);
    }

    public void setBlessing(int floor, int steps) {
        int value = Math.max(0, Math.min(MAX_BLESSING, steps));
        Integer old = value == 0 ? blessings.remove(floor) : blessings.put(floor, value);
        if (old == null || old != value) {
            setDirty();
        }
    }

    // ---------------------------------------------------------------- レベルの中央値

    /** プレイヤーの今のレベルを記録する（ログイン時とレベルが変わったとき）。 */
    public void recordLevel(UUID player, int level) {
        Integer old = levels.put(player, Math.max(1, level));
        if (old == null || old != level) {
            cachedMedian = -1;
            setDirty();
        }
    }

    /** 記録したプレイヤーのレベルの中央値（記録がなければ1）。人数が偶数のときは、真ん中の2人の低い方。 */
    public int medianLevel() {
        if (cachedMedian >= 0) {
            return cachedMedian;
        }
        if (levels.isEmpty()) {
            return 1;
        }
        List<Integer> sorted = new ArrayList<>(levels.values());
        Collections.sort(sorted);
        cachedMedian = sorted.get((sorted.size() - 1) / 2);
        return cachedMedian;
    }

    /** 記録しているプレイヤーの数。 */
    public int recordedPlayers() {
        return levels.size();
    }
}
