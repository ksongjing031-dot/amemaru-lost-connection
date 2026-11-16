package jp.amemaru.lostconnection.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * サーバー全体の進行（Phase 0-B 4.2 {@code ProgressionData}）。ワールドの data フォルダの {@code amemaru_progression.dat} に保存する。
 *
 * <p>Phase 2で持つのは「進行日」だけ（戦闘職の変更が、お試し期間かどうかの判断に使う）。進行日は、管理者が
 * {@code /amemaru admin day next} などで進める（日付では進まない。Phase 0-B 10章）。章・階層の解放・初撃破の記録・
 * 予告などは、Phase 5で同じクラスに足す。</p>
 */
public final class ProgressionData extends SavedData {
    private static final String NAME = "amemaru_progression";
    /** 進行日の上限（イベントは20日間だが、延長に備えて余裕を持たせる）。 */
    public static final int MAX_DAY = 365;

    private int day = 1;

    public ProgressionData() {
    }

    public static ProgressionData load(CompoundTag tag) {
        ProgressionData data = new ProgressionData();
        data.day = Math.max(1, Math.min(MAX_DAY, tag.contains("day") ? tag.getInt("day") : 1));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", 1);
        tag.putInt("day", day);
        return tag;
    }

    /** サーバーの進行のデータ（オーバーワールドに置く）。 */
    public static ProgressionData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(ProgressionData::load, ProgressionData::new, NAME);
    }

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

    /** サーバーの今の進行日。 */
    public static int currentDay(MinecraftServer server) {
        return get(server).day();
    }
}
