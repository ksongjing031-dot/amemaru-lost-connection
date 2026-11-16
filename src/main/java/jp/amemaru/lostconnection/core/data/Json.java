package jp.amemaru.lostconnection.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /** 省略できる範囲つきの小数を読む。ないときは {@code fallback}。 */
    public static double optionalDouble(JsonObject parent, String key, double fallback, double min, double max) {
        if (!parent.has(key) || parent.get(key).isJsonNull()) {
            return fallback;
        }
        return doubleInRange(parent, key, min, max);
    }

    /** 省略できる範囲つきの整数を読む。ないときは {@code fallback}。 */
    public static int optionalInt(JsonObject parent, String key, int fallback, int min, int max) {
        if (!parent.has(key) || parent.get(key).isJsonNull()) {
            return fallback;
        }
        return intInRange(parent, key, min, max);
    }

    /** 必須の文字列を読む（空はだめ）。 */
    public static String string(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull()) {
            throw new DefinitionException("「" + key + "」がありません");
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new DefinitionException("「" + key + "」は文字列（\"…\"）にしてください");
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new DefinitionException("「" + key + "」が空です");
        }
        return value;
    }

    /** 省略できる文字列を読む。ないときは {@code fallback}。 */
    public static String optionalString(JsonObject parent, String key, String fallback) {
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new DefinitionException("「" + key + "」は文字列（\"…\"）にしてください");
        }
        return element.getAsString();
    }

    /** 省略できる真偽値（true / false）を読む。 */
    public static boolean optionalBoolean(JsonObject parent, String key, boolean fallback) {
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new DefinitionException("「" + key + "」は true か false にしてください");
        }
        return element.getAsBoolean();
    }

    /** 省略できる配列（[ ... ]）を読む。ないときは空の配列。 */
    public static JsonArray optionalArray(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull()) {
            return new JsonArray();
        }
        if (!element.isJsonArray()) {
            throw new DefinitionException("「" + key + "」は [ ] で囲んだ形にしてください");
        }
        return element.getAsJsonArray();
    }

    /** 必須の配列（[ ... ]）を読む。 */
    public static JsonArray array(JsonObject parent, String key) {
        if (!parent.has(key)) {
            throw new DefinitionException("「" + key + "」がありません");
        }
        return optionalArray(parent, key);
    }

    /** 配列の要素をオブジェクトとして読む。 */
    public static JsonObject asObject(JsonElement element, String name) {
        if (element == null || !element.isJsonObject()) {
            throw new DefinitionException("「" + name + "」の中身は { } で囲んだ形にしてください");
        }
        return element.getAsJsonObject();
    }

    /** 配列の要素を文字列として読む。 */
    public static String asString(JsonElement element, String name) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new DefinitionException("「" + name + "」の中身は文字列（\"…\"）にしてください");
        }
        return element.getAsString();
    }

    /**
     * IDを読む。名前空間（「amemaru:」など）を省いたときは {@code amemaru} とみなす。
     */
    public static ResourceLocation id(JsonObject parent, String key) {
        return parseId(string(parent, key), key);
    }

    /** 省略できるIDを読む。ないときは null。 */
    @Nullable
    public static ResourceLocation optionalId(JsonObject parent, String key) {
        String text = optionalString(parent, key, "");
        return text.isBlank() ? null : parseId(text, key);
    }

    /** 文字列をIDに変える。名前空間を省いたときは {@code amemaru}。 */
    public static ResourceLocation parseId(String text, String name) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        ResourceLocation id = ResourceLocation.tryParse(value.contains(":") ? value : AmemaruMod.MOD_ID + ":" + value);
        if (id == null) {
            throw new DefinitionException("「" + name + "」の「" + text + "」はIDとして読めません（英小文字・数字・_・/・. だけにしてください）");
        }
        return id;
    }

    /** IDの配列を読む。 */
    public static List<ResourceLocation> idList(JsonObject parent, String key) {
        List<ResourceLocation> list = new ArrayList<>();
        JsonArray array = optionalArray(parent, key);
        for (int i = 0; i < array.size(); i++) {
            list.add(parseId(asString(array.get(i), key), key));
        }
        return list;
    }

    /** 色（"#RRGGBB"）を読む。ないときは {@code fallback}。 */
    public static int optionalColor(JsonObject parent, String key, int fallback) {
        String text = optionalString(parent, key, "");
        if (text.isBlank()) {
            return fallback;
        }
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6) {
            throw new DefinitionException("「" + key + "」は \"#RRGGBB\" の形にしてください（今は " + text + "）");
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new DefinitionException("「" + key + "」は \"#RRGGBB\" の形にしてください（今は " + text + "）");
        }
    }

    private static String format(double value) {
        if (value == Math.floor(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
