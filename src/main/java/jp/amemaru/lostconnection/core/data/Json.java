package jp.amemaru.lostconnection.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.annotation.Nullable;

/**
 * 定義を読むときの小さな道具。値が足りない・型が違う・範囲の外のときは、
 * 管理者が直せるよう、日本語のメッセージで {@link DefinitionException} を投げる。
 */
public final class Json {
    private Json() {
    }

    /** 必須のオブジェクト（{ ... }）を読む。 */
    public static JsonObject object(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element == null) {
            throw new DefinitionException("「" + key + "」がありません");
        }
        if (!element.isJsonObject()) {
            throw new DefinitionException("「" + key + "」は { } で囲んだ形にしてください");
        }
        return element.getAsJsonObject();
    }

    /** 省略できるオブジェクトを読む。ないときは null。 */
    @Nullable
    public static JsonObject optionalObject(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            throw new DefinitionException("「" + key + "」は { } で囲んだ形にしてください");
        }
        return element.getAsJsonObject();
    }

    /** 必須の数を読む。 */
    public static double number(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element == null) {
            throw new DefinitionException("「" + key + "」がありません");
        }
        return asNumber(element, key);
    }

    /** 数として読む。 */
    public static double asNumber(JsonElement element, String name) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new DefinitionException("「" + name + "」は数にしてください");
        }
        double value = element.getAsDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new DefinitionException("「" + name + "」は数にしてください");
        }
        return value;
    }

    /** 範囲つきの小数を読む。 */
    public static double doubleInRange(JsonObject parent, String key, double min, double max) {
        double value = number(parent, key);
        if (value < min || value > max) {
            throw new DefinitionException("「" + key + "」は " + format(min) + "〜" + format(max) + " の範囲にしてください（今は " + format(value) + "）");
        }
        return value;
    }

    /** 範囲つきの整数を読む。 */
    public static int intInRange(JsonObject parent, String key, int min, int max) {
        double value = number(parent, key);
        if (value != Math.floor(value)) {
            throw new DefinitionException("「" + key + "」は整数にしてください（今は " + format(value) + "）");
        }
        if (value < min || value > max) {
            throw new DefinitionException("「" + key + "」は " + min + "〜" + max + " の範囲にしてください（今は " + format(value) + "）");
        }
        return (int) value;
    }

    private static String format(double value) {
        if (value == Math.floor(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
