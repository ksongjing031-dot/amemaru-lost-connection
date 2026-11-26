package jp.amemaru.lostconnection.casino;

import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Set;

/**
 * カジノの遊びの決まり（Phase 0-A 15章「最初はルーレット（0が一つの形式）。寄付で『ハイ＆ロー』『魚レース』を追加し、将来も
 * ゲームを足せる作りにする」）。卓の流れ（賭ける → 回る・めくる → 結果）は {@link CasinoTable}、ここは賭け方と配当だけ。
 * 11月27日のご指示「ルーレットとハイ＆ロー以外を廃止」で、魚レースはやめた（パチンコとスロットは casino/parlor）。
 *
 * <p>配当は「賭けたチップ × 倍率」が戻る（当たらなければ0）。胴元の取り分（Phase 0-A 20章「カジノ（胴元の取り分約3%）」）は、
 * ルーレットは0の目（37分の1。2.7%）、ハイ＆ローは {@code edge}（0.97 → 3%）で出す。</p>
 */
public final class CasinoGames {
    private CasinoGames() {
    }

    /** 遊びの種類。 */
    public enum Game {
        ROULETTE("roulette"),
        HIGHLOW("highlow");

        private final String id;

        Game(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    /** 1つの賭け（multiplier：当たったときに戻る倍率。賭けた時に決まる）。 */
    public record Bet(String kind, int value, long chips, double multiplier) {
    }

    // ================================================================ ルーレット（0が一つ。37の目）

    /** 赤の目（ふつうのヨーロッパ式の輪の色）。 */
    public static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    /** 輪の並び（0から時計回り。ヨーロッパ式）。画面の輪の絵もこの順。 */
    public static final List<Integer> WHEEL = List.of(0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16,
            33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26);
    /** ルーレットの賭け方：一点（35倍＋賭け）・赤・黒・奇数・偶数・前半・後半（2倍）・ダズン・列（3倍）。 */
    public static final List<String> ROULETTE_KINDS = List.of("number", "red", "black", "odd", "even", "low", "high", "dozen", "column");

    /** ルーレットの賭けの倍率（賭け方がまちがっていれば 0）。 */
    public static double rouletteMultiplier(String kind, int value) {
        return switch (kind) {
            case "number" -> value >= 0 && value <= 36 ? 36 : 0;
            case "red", "black", "odd", "even", "low", "high" -> 2;
            case "dozen", "column" -> value >= 1 && value <= 3 ? 3 : 0;
            default -> 0;
        };
    }

    /** ルーレットの賭けが当たったか（0 はどの外の賭けにも入らない）。 */
    public static boolean rouletteWins(String kind, int value, int number) {
        if ("number".equals(kind)) {
            return number == value;
        }
        if (number == 0) {
            return false;
        }
        return switch (kind) {
            case "red" -> RED.contains(number);
            case "black" -> !RED.contains(number);
            case "odd" -> number % 2 == 1;
            case "even" -> number % 2 == 0;
            case "low" -> number <= 18;
            case "high" -> number >= 19;
            case "dozen" -> (number - 1) / 12 + 1 == value;
            case "column" -> (number - 1) % 3 + 1 == value;
            default -> false;
        };
    }

    public static int spinRoulette(RandomSource random) {
        return random.nextInt(37);
    }

    // ================================================================ ハイ＆ロー（共同事業②）

    /** カード（1〜13。A・2〜10・J・Q・K）を引く（毎回まぜなおす）。 */
    public static int drawCard(RandomSource random) {
        return 1 + random.nextInt(13);
    }

    /** ハイ・ローの倍率（当たる確率から胴元の取り分を引いて、100分の1で切り捨て。当たりがないなら 0）。同じ数は外れ。 */
    public static double highLowMultiplier(String kind, int card, double edge) {
        int wins = switch (kind) {
            case "high" -> 13 - card;
            case "low" -> card - 1;
            default -> 0;
        };
        if (wins <= 0) {
            return 0;
        }
        return Math.floor(edge * 13.0 / wins * 100.0) / 100.0;
    }

    public static boolean highLowWins(String kind, int card, int next) {
        return "high".equals(kind) ? next > card : "low".equals(kind) && next < card;
    }
}
