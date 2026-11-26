package jp.amemaru.lostconnection.casino.parlor;

import java.util.SplittableRandom;

/**
 * 日ごとの当たり台と外れ台（11月27日のご指示「日にちごとに代わるあたり台と外れ台を用意する」【補完】）。
 *
 * <p>本物の店と同じく、毎朝（日は CP と同じく4時に切り替わる）台ごとに、スロットとパチンコの設定（1〜6）と、パチンコの釘（1,000円＝
 * 250個あたりの回転数）を決めなおす。7のつく日（7日・17日・27日）は「ラッキーノットの日」（イベントの日）で、高い設定と広い釘が
 * 多く、さらにその日の「当たりの末尾」（台番号の下1けた）の台は、設定が2つ上がり、釘も広い。決め方はサーバーごとのひみつの種
 * （{@code salt}）と日と台番号から決まるので、同じ日のうちは何度見ても同じ。</p>
 */
public final class DailySettings {
    /** ふつうの日・イベントの日の設定の割合（%。設定1〜6）。 */
    public static final double[] NORMAL = {40, 25, 15, 12, 6, 2};
    public static final double[] EVENT = {18, 18, 16, 20, 16, 12};

    private DailySettings() {
    }

    /** 日（"2026-11-27" の形）が、7のつく日か。 */
    public static boolean isEvent(String day) {
        if (day == null || day.length() < 10) {
            return false;
        }
        char last = day.charAt(day.length() - 1);
        return last == '7';
    }

    /** イベントの日の「当たりの末尾」（0〜9）。イベントの日でなければ −1。 */
    public static int luckyDigit(long salt, String day) {
        if (!isEvent(day)) {
            return -1;
        }
        return new SplittableRandom(mix(salt, day, 0, 7)).nextInt(10);
    }

    /** その日の、その台の設定（1〜6）。 */
    public static int setting(long salt, String day, int machineNo) {
        boolean event = isEvent(day);
        SplittableRandom r = new SplittableRandom(mix(salt, day, machineNo, 1));
        int s = pick(r.nextDouble(), event ? EVENT : NORMAL) + 1;
        if (event && machineNo % 10 == luckyDigit(salt, day)) {
            s = Math.min(6, s + 2);
        }
        return s;
    }

    /** その日の、その台の釘（1,000円あたりの回転数。機種の幅の中で、ふつうの日は狭い方に寄る）。 */
    public static double nail(long salt, String day, int machineNo, PachinkoSpec spec) {
        boolean event = isEvent(day);
        double[] range = spec.nails(event);
        SplittableRandom r = new SplittableRandom(mix(salt, day, machineNo, 2));
        double u = Math.pow(r.nextDouble(), event ? 0.8 : 1.5);
        double v = range[0] + (range[1] - range[0]) * u;
        if (event && machineNo % 10 == luckyDigit(salt, day)) {
            v = Math.min(range[1] + 1.5, v + 2.5);
        }
        return Math.round(v * 10.0) / 10.0;
    }

    static int pick(double x, double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i] / total;
            if (x < acc) {
                return i;
            }
        }
        return weights.length - 1;
    }

    static long mix(long salt, String day, int machineNo, int kind) {
        long h = salt * 0x9E3779B97F4A7C15L;
        for (int i = 0; i < day.length(); i++) {
            h = (h ^ day.charAt(i)) * 0x100000001B3L;
        }
        h ^= (long) machineNo * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) kind * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        return h;
    }
}
