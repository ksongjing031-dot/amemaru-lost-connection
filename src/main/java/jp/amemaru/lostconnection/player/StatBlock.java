package jp.amemaru.lostconnection.player;

import java.util.Arrays;

/**
 * 計算済みのステータスの値の組。サーバーで計算し、本人の画面に送る。
 */
public final class StatBlock {
    private final double[] values = new double[StatType.COUNT];

    public double get(StatType type) {
        return values[type.ordinal()];
    }

    public void set(StatType type, double value) {
        values[type.ordinal()] = value;
    }

    /** 最大HP。 */
    public double maxHp() {
        return get(StatType.HP);
    }

    /** 最大MP。 */
    public double maxMp() {
        return get(StatType.MP);
    }

    public void copyFrom(StatBlock other) {
        System.arraycopy(other.values, 0, values, 0, values.length);
    }

    public boolean sameAs(StatBlock other) {
        return Arrays.equals(values, other.values);
    }

    /** 通信用に、値の配列の写しを返す。 */
    public double[] toArray() {
        return values.clone();
    }
}
