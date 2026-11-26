package jp.amemaru.lostconnection.casino.parlor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;

/**
 * 遊技機の定義（{@code casino_machines/*.json}）を読む小さな道具。Gson だけで書く（Minecraft なしで、台の数を確かめる外の道具
 * {@code tools/balance/ParlorSim.java} からも同じ読み方をする）。問題があれば {@link DefinitionException}（日本語で、どこをどう直すか）。
 */
public final class ParlorJson {
    private ParlorJson() {
    }

    public static JsonObject object(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        if (e == null || !e.isJsonObject()) {
            throw new DefinitionException("「" + key + "」を { } で書いてください");
        }
        return e.getAsJsonObject();
    }

    public static JsonObject optObject(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    public static JsonArray array(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        if (e == null || !e.isJsonArray()) {
            throw new DefinitionException("「" + key + "」を [ ] で書いてください");
        }
        return e.getAsJsonArray();
    }

    public static JsonArray optArray(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    public static String string(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            throw new DefinitionException("「" + key + "」（文字）がありません");
        }
        return e.getAsString();
    }

    public static String optString(JsonObject parent, String key, String fallback) {
        JsonElement e = parent.get(key);
        return e == null || !e.isJsonPrimitive() ? fallback : e.getAsString();
    }

    public static double number(JsonObject parent, String key, double min, double max) {
        JsonElement e = parent.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new DefinitionException("「" + key + "」（数）がありません");
        }
        return check(key, e.getAsDouble(), min, max);
    }

    public static double optNumber(JsonObject parent, String key, double fallback, double min, double max) {
        JsonElement e = parent.get(key);
        if (e == null) {
            return fallback;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new DefinitionException("「" + key + "」は数で書いてください");
        }
        return check(key, e.getAsDouble(), min, max);
    }

    public static int optInt(JsonObject parent, String key, int fallback, int min, int max) {
        return (int) Math.round(optNumber(parent, key, fallback, min, max));
    }

    public static boolean optBool(JsonObject parent, String key, boolean fallback) {
        JsonElement e = parent.get(key);
        return e == null || !e.isJsonPrimitive() ? fallback : e.getAsBoolean();
    }

    private static double check(String key, double v, double min, double max) {
        if (Double.isNaN(v) || v < min || v > max) {
            throw new DefinitionException("「" + key + "」は " + fmt(min) + "〜" + fmt(max) + " で書いてください（今は " + fmt(v) + "）");
        }
        return v;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    /**
     * 設定ごとの値（1〜6）。数1つなら全部の設定で同じ、6つの並びなら設定1〜6。
     */
    public static double[] perSetting(JsonObject parent, String key, double min, double max) {
        JsonElement e = parent.get(key);
        if (e == null) {
            throw new DefinitionException("「" + key + "」がありません（数1つか、設定1〜6の6つの並び）");
        }
        return perSetting(e, key, min, max);
    }

    public static double[] optPerSetting(JsonObject parent, String key, double fallback, double min, double max) {
        JsonElement e = parent.get(key);
        if (e == null) {
            double[] out = new double[6];
            java.util.Arrays.fill(out, fallback);
            return out;
        }
        return perSetting(e, key, min, max);
    }

    public static double[] perSetting(JsonElement e, String key, double min, double max) {
        double[] out = new double[6];
        if (e.isJsonPrimitive()) {
            java.util.Arrays.fill(out, check(key, e.getAsDouble(), min, max));
            return out;
        }
        if (!e.isJsonArray() || e.getAsJsonArray().size() != 6) {
            throw new DefinitionException("「" + key + "」は、数1つか、設定1〜6の6つの並びで書いてください");
        }
        for (int i = 0; i < 6; i++) {
            out[i] = check(key + "[" + i + "]", e.getAsJsonArray().get(i).getAsDouble(), min, max);
        }
        return out;
    }
}
