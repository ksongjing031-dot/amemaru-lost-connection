package jp.amemaru.lostconnection.progression;

import jp.amemaru.lostconnection.player.GrowthRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * </ul>
 *
 * <p>階層の解放・初撃破の記録・予告などは、Phase 5で同じクラスに足す。</p>
 */
public final class ProgressionData extends SavedData {
    private static final String NAME = "amemaru_progression";
    /** 進行日の上限（イベントは20日間だが、延長に備えて余裕を持たせる）。 */
    public static final int MAX_DAY = 365;

    private int day = 1;
    private final Map<UUID, Integer> levels = new HashMap<>();
    /** 中央値の計算結果（レベルが変わるまで使い回す）。負の値は「まだ計算していない」。 */
    private int cachedMedian = -1;

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
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", 2);
        tag.putInt("day", day);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> entry : levels.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("id", entry.getKey());
            one.putInt("level", entry.getValue());
            list.add(one);
        }
        tag.put("levels", list);
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
