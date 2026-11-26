package jp.amemaru.lostconnection.casino.parlor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 台のデータ（データカウンター。11月27日【補完】）：その日の回転数・大当たり・ボーナスの回数と、大当たりの履歴・差枚の流れ、
 * 前の3日のまとめ。だれでも見られる（台を選ぶ手がかり。設定そのものは見せない）。
 */
public final class MachineRecord {
    /** 前の日のまとめ（スロット：ゲーム数・BIG・REG（AT は AT 回数）・差枚。パチンコ：回転数・大当たり・初当たり・最高連チャン）。 */
    public record Day(String day, int games, int a, int b, int c) {
    }

    /** 大当たり・ボーナスの履歴の1つ（何回転・何ゲーム目で、何に当たったか・連チャンの何回目か・出玉）。 */
    public record Hit(int at, String kind, int chain, int balls) {
    }

    private static final int HISTORY_DAYS = 3;
    private static final int HITS_KEPT = 40;
    private static final int SLUMP_POINTS = 240;
    /** 差枚の流れは、このゲーム数ごとに1点。 */
    public static final int SLUMP_STEP = 25;

    public String day = "";
    /** スロット：ゲーム数（ボーナス中は数えない）・BIG・REG・AT・CZ・差枚・最大の差枚・今のゲーム数（前の当たりから） */
    public int games;
    public int big;
    public int reg;
    public int at;
    public int cz;
    public long diff;
    public long maxDiff;
    public int since;
    /** パチンコ：回転数・大当たり・初当たり・最高連チャン・出玉（アタッカーの払い出し） */
    public int spins;
    public int hits;
    public int firstHits;
    public int maxChain;
    public long out;
    public final List<Integer> slump = new ArrayList<>();
    public final List<Hit> hitList = new ArrayList<>();
    public final List<Day> history = new ArrayList<>();

    /** 日が変わっていたら、今日の分を前の日のまとめに送って、数え直す。slot はスロットの台か。 */
    public boolean roll(String today, boolean slot) {
        if (today.equals(day)) {
            return false;
        }
        if (!day.isEmpty()) {
            history.add(0, slot ? new Day(day, games, big, reg, (int) diff) : new Day(day, spins, hits, firstHits, maxChain));
            while (history.size() > HISTORY_DAYS) {
                history.remove(history.size() - 1);
            }
        }
        day = today;
        games = 0;
        big = 0;
        reg = 0;
        at = 0;
        cz = 0;
        diff = 0;
        maxDiff = 0;
        spins = 0;
        hits = 0;
        firstHits = 0;
        maxChain = 0;
        out = 0;
        slump.clear();
        hitList.clear();
        // 今のゲーム数（ハマり）は日をまたいでも続く（本物の台と同じ）
        return true;
    }

    /** スロットの1ゲーム（bonusGame はボーナス中のゲーム）。 */
    public void slotGame(int bet, int payout, boolean bonusGame) {
        if (!bonusGame) {
            games++;
            since++;
        }
        diff += payout - bet;
        maxDiff = Math.max(maxDiff, diff);
        if (!bonusGame && games % SLUMP_STEP == 0 && slump.size() < SLUMP_POINTS) {
            slump.add((int) diff);
        }
    }

    public void slotHit(String kind) {
        switch (kind) {
            case "big" -> big++;
            case "reg" -> reg++;
            case "at" -> at++;
            case "cz" -> {
                cz++;
                return;
            }
            default -> {
            }
        }
        addHit(new Hit(since, kind, 0, 0));
        since = 0;
    }

    /** パチンコの1回転。 */
    public void spin() {
        spins++;
        since++;
    }

    /** パチンコの大当たり（chain は連チャンの何回目か。1なら初当たり）。 */
    public void pachinkoHit(String kind, int chain) {
        hits++;
        if (chain <= 1) {
            firstHits++;
        }
        maxChain = Math.max(maxChain, chain);
        addHit(new Hit(since, kind, chain, 0));
        since = 0;
    }

    /** 大当たりの出玉を、いちばん新しい履歴に書く。 */
    public void pachinkoPaid(int balls) {
        out += balls;
        if (!hitList.isEmpty()) {
            Hit h = hitList.get(0);
            hitList.set(0, new Hit(h.at(), h.kind(), h.chain(), h.balls() + balls));
        }
    }

    private void addHit(Hit h) {
        hitList.add(0, h);
        while (hitList.size() > HITS_KEPT) {
            hitList.remove(hitList.size() - 1);
        }
    }

    public JsonObject save() {
        JsonObject o = new JsonObject();
        o.addProperty("day", day);
        o.addProperty("games", games);
        o.addProperty("big", big);
        o.addProperty("reg", reg);
        o.addProperty("at", at);
        o.addProperty("cz", cz);
        o.addProperty("diff", diff);
        o.addProperty("maxDiff", maxDiff);
        o.addProperty("since", since);
        o.addProperty("spins", spins);
        o.addProperty("hits", hits);
        o.addProperty("firstHits", firstHits);
        o.addProperty("maxChain", maxChain);
        o.addProperty("out", out);
        JsonArray s = new JsonArray();
        slump.forEach(s::add);
        o.add("slump", s);
        JsonArray h = new JsonArray();
        for (Hit hit : hitList) {
            JsonObject one = new JsonObject();
            one.addProperty("at", hit.at());
            one.addProperty("kind", hit.kind());
            one.addProperty("chain", hit.chain());
            one.addProperty("balls", hit.balls());
            h.add(one);
        }
        // 「hits」は大当たりの回数（数）。当たりの履歴は「hitList」（同じ名前にすると数が消える）
        o.add("hitList", h);
        JsonArray d = new JsonArray();
        for (Day day1 : history) {
            JsonObject one = new JsonObject();
            one.addProperty("day", day1.day());
            one.addProperty("games", day1.games());
            one.addProperty("a", day1.a());
            one.addProperty("b", day1.b());
            one.addProperty("c", day1.c());
            d.add(one);
        }
        o.add("history", d);
        return o;
    }

    public void load(JsonObject o) {
        day = ParlorJson.optString(o, "day", "");
        games = ParlorJson.optInt(o, "games", 0, 0, Integer.MAX_VALUE);
        big = ParlorJson.optInt(o, "big", 0, 0, Integer.MAX_VALUE);
        reg = ParlorJson.optInt(o, "reg", 0, 0, Integer.MAX_VALUE);
        at = ParlorJson.optInt(o, "at", 0, 0, Integer.MAX_VALUE);
        cz = ParlorJson.optInt(o, "cz", 0, 0, Integer.MAX_VALUE);
        diff = (long) ParlorJson.optNumber(o, "diff", 0, -1.0E12, 1.0E12);
        maxDiff = (long) ParlorJson.optNumber(o, "maxDiff", 0, -1.0E12, 1.0E12);
        since = ParlorJson.optInt(o, "since", 0, 0, Integer.MAX_VALUE);
        spins = ParlorJson.optInt(o, "spins", 0, 0, Integer.MAX_VALUE);
        hits = ParlorJson.optInt(o, "hits", 0, 0, Integer.MAX_VALUE);
        firstHits = ParlorJson.optInt(o, "firstHits", 0, 0, Integer.MAX_VALUE);
        maxChain = ParlorJson.optInt(o, "maxChain", 0, 0, Integer.MAX_VALUE);
        out = (long) ParlorJson.optNumber(o, "out", 0, 0, 1.0E15);
        slump.clear();
        JsonArray s = ParlorJson.optArray(o, "slump");
        if (s != null) {
            for (JsonElement e : s) {
                slump.add(e.getAsInt());
            }
        }
        hitList.clear();
        JsonArray h = ParlorJson.optArray(o, "hitList");
        if (h != null) {
            for (JsonElement e : h) {
                JsonObject one = e.getAsJsonObject();
                hitList.add(new Hit(ParlorJson.optInt(one, "at", 0, 0, Integer.MAX_VALUE), ParlorJson.optString(one, "kind", ""),
                        ParlorJson.optInt(one, "chain", 0, 0, 100000), ParlorJson.optInt(one, "balls", 0, 0, Integer.MAX_VALUE)));
            }
        }
        history.clear();
        JsonArray d = ParlorJson.optArray(o, "history");
        if (d != null) {
            for (JsonElement e : d) {
                JsonObject one = e.getAsJsonObject();
                history.add(new Day(ParlorJson.optString(one, "day", ""), ParlorJson.optInt(one, "games", 0, 0, Integer.MAX_VALUE),
                        ParlorJson.optInt(one, "a", 0, -Integer.MAX_VALUE, Integer.MAX_VALUE),
                        ParlorJson.optInt(one, "b", 0, -Integer.MAX_VALUE, Integer.MAX_VALUE),
                        ParlorJson.optInt(one, "c", 0, -Integer.MAX_VALUE, Integer.MAX_VALUE)));
            }
        }
    }
}
