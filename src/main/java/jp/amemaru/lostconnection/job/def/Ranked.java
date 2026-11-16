package jp.amemaru.lostconnection.job.def;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;

import java.util.Locale;

/**
 * スキルの段階で変わる数値。「1段目の値」と「段階が1つ上がるごとの増減」の組。
 *
 * <p>JSONでは {@code "cooldown": 12, "cooldown_per_rank": -1} のように、同じ名前に {@code _per_rank} を付けて書く
 * （{@code _per_rank} は省略できる。省略すると段階で変わらない）。</p>
 */
public record Ranked(double base, double perRank) {
    public static final Ranked ZERO = new Ranked(0.0, 0.0);

    /** その段階（1から）の値。 */
    public double at(int rank) {
        return base + perRank * Math.max(0, rank - 1);
    }

    /** その段階の値を整数に丸めたもの。 */
    public int intAt(int rank) {
        return (int) Math.round(at(rank));
    }

    public boolean isZero() {
        return base == 0.0 && perRank == 0.0;
    }

    public static Ranked of(double base) {
        return new Ranked(base, 0.0);
    }

    /** 必須の数値を読む。 */
    public static Ranked read(JsonObject json, String key, double min, double max) {
        if (!json.has(key)) {
            throw new DefinitionException("「" + key + "」がありません");
        }
        return readOptional(json, key, 0.0, min, max);
    }

    /** 省略できる数値を読む。ないときは {@code fallback}（段階で変わらない）。 */
    public static Ranked readOptional(JsonObject json, String key, double fallback, double min, double max) {
        double base = Json.optionalDouble(json, key, fallback, min, max);
        double perRank = Json.optionalDouble(json, key + "_per_rank", 0.0, -max - Math.abs(min), max + Math.abs(min));
        return new Ranked(base, perRank);
    }

    /** 最大の段階まで、値が範囲に収まるかを確かめる。 */
    public void checkRange(String name, int maxRank, double min, double max) {
        for (int rank = 1; rank <= maxRank; rank++) {
            double value = at(rank);
            if (value < min || value > max) {
                throw new DefinitionException(String.format(Locale.ROOT,
                        "「%s」が%d段目で %s になり、範囲（%s〜%s）の外です", name, rank, trim(value), trim(min), trim(max)));
            }
        }
    }

    /** 画面用：小数がなければ整数で、あれば小数1けたで表す。 */
    public static String trim(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
