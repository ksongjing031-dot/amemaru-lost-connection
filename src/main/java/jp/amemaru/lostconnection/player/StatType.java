package jp.amemaru.lostconnection.player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * ステータスの種類（Phase 0-A 19章）。
 *
 * <p>HP〜会心の7つはステータスポイントで振り分けられる。会心ダメージ・回復量・クールダウン短縮・
 * ロスト耐性は、装備とアクセサリーでだけ上がる。順番を変えると通信と保存の互換が崩れるため、
 * 足すときは最後に足すこと。</p>
 */
public enum StatType {
    HP("hp", true, false),
    MP("mp", true, false),
    ATTACK("attack", true, false),
    MAGIC("magic", true, false),
    DEFENSE("defense", true, false),
    SPEED("speed", true, false),
    CRIT("crit", true, true),
    CRIT_DAMAGE("crit_damage", false, true),
    HEAL_POWER("heal_power", false, true),
    COOLDOWN_REDUCTION("cooldown_reduction", false, true),
    LOST_RESIST("lost_resist", false, true);

    private static final StatType[] VALUES = values();
    /** 種類の数。 */
    public static final int COUNT = VALUES.length;
    private static final List<StatType> ALLOCATABLE;
    /** 振り分けられる種類の数。 */
    public static final int ALLOCATABLE_COUNT;
    private static final Map<String, StatType> BY_ID = new HashMap<>();

    static {
        List<StatType> allocatable = new ArrayList<>();
        for (StatType type : VALUES) {
            BY_ID.put(type.id, type);
            if (type.allocatable) {
                type.allocIndex = allocatable.size();
                allocatable.add(type);
            }
        }
        ALLOCATABLE = Collections.unmodifiableList(allocatable);
        ALLOCATABLE_COUNT = allocatable.size();
    }

    private final String id;
    private final boolean allocatable;
    private final boolean percent;
    private int allocIndex = -1;

    StatType(String id, boolean allocatable, boolean percent) {
        this.id = id;
        this.allocatable = allocatable;
        this.percent = percent;
    }

    /** JSONと保存データで使う名前（例：{@code attack}）。 */
    public String id() {
        return id;
    }

    /** ステータスポイントで振り分けられるか。 */
    public boolean isAllocatable() {
        return allocatable;
    }

    /** %で表すステータスか。 */
    public boolean isPercent() {
        return percent;
    }

    /** 振り分けられる種類の中での番号。振り分けられない種類は -1。 */
    public int allocIndex() {
        return allocIndex;
    }

    /** 言語ファイルのキー。 */
    public String translationKey() {
        return "stat.amemaru." + id;
    }

    /** 画面に出す形の数値（%の種類は小数1けた、ほかは整数）。 */
    public String format(double value) {
        if (percent) {
            return String.format(Locale.ROOT, "%.1f%%", value);
        }
        return Long.toString((long) Math.floor(value));
    }

    /** すべての種類（定義の順）。 */
    public static StatType[] all() {
        return VALUES.clone();
    }

    /** 振り分けられる種類（定義の順）。 */
    public static List<StatType> allocatable() {
        return ALLOCATABLE;
    }

    /** 名前から探す。 */
    public static Optional<StatType> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** 番号（ordinal）から探す。範囲の外なら null。 */
    @Nullable
    public static StatType byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : null;
    }
}
