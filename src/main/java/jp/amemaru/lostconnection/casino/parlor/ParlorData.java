package jp.amemaru.lostconnection.casino.parlor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 遊技場の保存（11月27日【補完】）：台ごとの中の状態（ボーナスの持ち越し・確変・AT・天井まで・保留）とデータカウンター、その日に
 * 決めた設定と釘、日ごとの当たり台を決めるひみつの種（サーバーごと）、管理者が決めた設定、館の勘定。
 */
public final class ParlorData extends SavedData {
    private static final String NAME = "amemaru_parlor";

    long salt;
    String day = "";
    final Map<Integer, String> states = new HashMap<>();
    final Map<Integer, String> records = new HashMap<>();
    /** その日の設定（スロット・設定付きのパチンコ）と釘（10倍の整数）。 */
    final Map<Integer, Integer> settings = new HashMap<>();
    final Map<Integer, Integer> nails = new HashMap<>();
    /** 管理者が決めた設定・釘（その日だけ。day が変わると消える）。 */
    final Map<Integer, Integer> forcedSettings = new HashMap<>();
    final Map<Integer, Integer> forcedNails = new HashMap<>();
    /** 館の勘定（貸した玉・メダルのチップと、交換で戻したチップ）。 */
    long lentChips;
    long returnedChips;

    public static ParlorData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ParlorData::load, ParlorData::create, NAME);
    }

    static ParlorData create() {
        ParlorData d = new ParlorData();
        d.salt = UUID.randomUUID().getMostSignificantBits();
        return d;
    }

    public long salt() {
        return salt;
    }

    public long lentChips() {
        return lentChips;
    }

    public long returnedChips() {
        return returnedChips;
    }

    JsonObject state(int no) {
        String s = states.get(no);
        return s == null ? null : JsonParser.parseString(s).getAsJsonObject();
    }

    JsonObject record(int no) {
        String s = records.get(no);
        return s == null ? null : JsonParser.parseString(s).getAsJsonObject();
    }

    void putState(int no, JsonObject state, JsonObject record) {
        states.put(no, state.toString());
        records.put(no, record.toString());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("salt", salt);
        tag.putString("day", day);
        tag.put("states", intMap(states));
        tag.put("records", intMap(records));
        tag.put("settings", intIntMap(settings));
        tag.put("nails", intIntMap(nails));
        tag.put("forcedSettings", intIntMap(forcedSettings));
        tag.put("forcedNails", intIntMap(forcedNails));
        tag.putLong("lentChips", lentChips);
        tag.putLong("returnedChips", returnedChips);
        return tag;
    }

    static ParlorData load(CompoundTag tag) {
        ParlorData d = new ParlorData();
        d.salt = tag.contains("salt") ? tag.getLong("salt") : UUID.randomUUID().getMostSignificantBits();
        d.day = tag.getString("day");
        readMap(tag.getCompound("states"), d.states);
        readMap(tag.getCompound("records"), d.records);
        readIntMap(tag.getCompound("settings"), d.settings);
        readIntMap(tag.getCompound("nails"), d.nails);
        readIntMap(tag.getCompound("forcedSettings"), d.forcedSettings);
        readIntMap(tag.getCompound("forcedNails"), d.forcedNails);
        d.lentChips = tag.getLong("lentChips");
        d.returnedChips = tag.getLong("returnedChips");
        return d;
    }

    private static CompoundTag intMap(Map<Integer, String> map) {
        CompoundTag t = new CompoundTag();
        map.forEach((k, v) -> t.putString(Integer.toString(k), v));
        return t;
    }

    private static CompoundTag intIntMap(Map<Integer, Integer> map) {
        CompoundTag t = new CompoundTag();
        map.forEach((k, v) -> t.putInt(Integer.toString(k), v));
        return t;
    }

    private static void readMap(CompoundTag t, Map<Integer, String> into) {
        for (String k : t.getAllKeys()) {
            try {
                into.put(Integer.parseInt(k), t.getString(k));
            } catch (NumberFormatException ignored) {
                // 番号でない鍵は読まない
            }
        }
    }

    private static void readIntMap(CompoundTag t, Map<Integer, Integer> into) {
        for (String k : t.getAllKeys()) {
            if (t.contains(k, Tag.TAG_INT)) {
                try {
                    into.put(Integer.parseInt(k), t.getInt(k));
                } catch (NumberFormatException ignored) {
                    // 番号でない鍵は読まない
                }
            }
        }
    }
}
