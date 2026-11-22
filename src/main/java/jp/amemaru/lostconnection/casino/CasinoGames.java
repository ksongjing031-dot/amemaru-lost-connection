package jp.amemaru.lostconnection.casino;

import jp.amemaru.lostconnection.fishing.FishDef;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * カジノの遊びの決まり（Phase 0-A 15章「最初はルーレット（0が一つの形式）。寄付で『ハイ＆ロー』『魚レース』を追加し、将来も
 * ゲームを足せる作りにする」）。卓の流れ（賭ける → 回る・めくる・走る → 結果）は {@link CasinoTable}、ここは賭け方と配当だけ。
 *
 * <p>配当は「賭けたチップ × 倍率」が戻る（当たらなければ0）。胴元の取り分（Phase 0-A 20章「カジノ（胴元の取り分約3%）」）は、
 * ルーレットは0の目（37分の1。2.7%）、ハイ＆ローと魚レースは {@code edge}（0.97 → 3%）で出す。</p>
 */
public final class CasinoGames {
    private CasinoGames() {
    }

    /** 遊びの種類。 */
    public enum Game {
        ROULETTE("roulette"),
        HIGHLOW("highlow"),
        FISHRACE("fishrace");

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

    // ================================================================ 魚レース（共同事業⑥）

    /** 走る魚（owner が null なら館の魚）。ratio：大きさの割合（0〜1）。 */
    public record Runner(String fish, String name, @Nullable java.util.UUID owner, String ownerName, int rarity, double ratio) {
        /** 速さのもと：★と大きさで少しだけ速い（★1の小さい魚 1.00 〜 湖の主 1.22）。その日の調子で大きく変わる。 */
        public double base() {
            return 1.0 + 0.04 * (rarity - 1) + 0.06 * ratio;
        }

        public static Runner of(FishDef def, double ratio, @Nullable java.util.UUID owner, String ownerName) {
            return new Runner(def.id().toString(), def.name(), owner, ownerName, def.rarity(), Math.max(0, Math.min(1, ratio)));
        }
    }

    /** 1回のレースの歩み（1歩ごとの位置。ゴールは 1.0）。 */
    public static final int RACE_STEPS = 40;

    /** レースを走らせる。frames[歩][走路]＝位置（0〜1。ゴールしたら1）。最後の歩でいちばん前の走路が勝ち。 */
    public static float[][] runRace(List<Runner> runners, RandomSource random) {
        int n = runners.size();
        double[] condition = new double[n];
        for (int i = 0; i < n; i++) {
            condition[i] = runners.get(i).base() * (0.82 + 0.36 * random.nextDouble());
        }
        double[] pos = new double[n];
        java.util.List<float[]> frames = new java.util.ArrayList<>();
        frames.add(new float[n]);
        for (int step = 0; step < RACE_STEPS * 3; step++) {
            boolean done = false;
            for (int i = 0; i < n; i++) {
                pos[i] += condition[i] * (0.6 + 0.8 * random.nextDouble()) / RACE_STEPS;
                if (pos[i] >= 1.0) {
                    done = true;
                }
            }
            float[] frame = new float[n];
            for (int i = 0; i < n; i++) {
                frame[i] = (float) Math.min(1.0, pos[i]);
            }
            frames.add(frame);
            if (done) {
                break;
            }
        }
        // 勝ち：いちばん先へ出た魚（同じなら走路の若い方）。最後の絵で、勝った魚だけがゴールの線にいるようにする
        int winner = winner(pos);
        float[] last = frames.get(frames.size() - 1);
        for (int i = 0; i < n; i++) {
            last[i] = i == winner ? 1.0F : (float) Math.min(0.985, pos[i]);
        }
        return frames.toArray(new float[0][]);
    }

    static int winner(double[] pos) {
        int best = 0;
        for (int i = 1; i < pos.length; i++) {
            if (pos[i] > pos[best]) {
                best = i;
            }
        }
        return best;
    }

    /** 走路ごとの最後の位置から勝った走路を出す。 */
    public static int raceWinner(float[][] frames) {
        float[] last = frames[frames.length - 1];
        double[] pos = new double[last.length];
        for (int i = 0; i < last.length; i++) {
            pos[i] = last[i];
        }
        return winner(pos);
    }

    /** 走路ごとの勝つ見込み（何度も走らせて数える。seed が同じなら同じ）。 */
    public static double[] raceChances(List<Runner> runners, long seed, int trials) {
        RandomSource random = RandomSource.create(seed);
        double[] wins = new double[runners.size()];
        for (int t = 0; t < trials; t++) {
            float[][] frames = runRace(runners, random);
            wins[raceWinner(frames)]++;
        }
        for (int i = 0; i < wins.length; i++) {
            wins[i] /= trials;
        }
        return wins;
    }

    /**
     * 魚レースの倍率（見込みから胴元の取り分を引いて、10分の1で切り捨て。max まで）。min より小さくなる（勝つ見込みがとても高い）
     * ときは、胴元の取り分を残すため min まで上げず、100分の1で切り捨てる（1倍より下にはしない）。
     */
    public static double raceMultiplier(double chance, double edge, double min, double max) {
        if (chance <= 0) {
            return max;
        }
        double m = Math.floor(edge / chance * 10.0) / 10.0;
        if (m < min) {
            m = Math.floor(edge / chance * 100.0) / 100.0;
        }
        return Math.max(1.0, Math.min(max, m));
    }
}
