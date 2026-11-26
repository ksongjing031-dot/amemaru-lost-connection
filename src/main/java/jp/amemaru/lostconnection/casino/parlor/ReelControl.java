package jp.amemaru.lostconnection.casino.parlor;

/**
 * リール制御（11月27日【補完】。本物のパチスロと同じく、止めボタンを押した所から、最大4コマすべって止まる）。
 *
 * <p>リールの位置は、中段に見えている図柄の番号。図柄は上から下へ流れるので、止めボタンを押した位置 p から k コマすべると、
 * 止まる位置は p − k（k = 0〜4）。上段は位置 − 1、下段は位置 + 1 の図柄。</p>
 *
 * <p>役の形は「組」で数える（同じ図柄の並びの役は同じ組。チェリーは役ごと）。そのゲームでそろってよい組（{@code allowed}。ビットの
 * 集まり）だけを許し、ねらう組（{@code goal}）がそろう止め方があれば、それを選ぶ。2つ目のリールを止めるときは、残りのリールの
 * どこを押されても「そろってはいけない形」をよけられる止め方だけを選ぶ（先読み）。</p>
 */
public final class ReelControl {
    /** すべる最大のコマ数。 */
    public static final int MAX_SLIP = 4;
    private static final double FORBIDDEN = -1.0E6;

    private ReelControl() {
    }

    public static int mod(int v, int n) {
        int m = v % n;
        return m < 0 ? m + n : m;
    }

    /** リール r の位置 pos で、段 row（0 上・1 中・2 下）に見える図柄。 */
    public static char symbolAt(SlotSpec spec, int r, int pos, int row) {
        String reel = spec.reel(r);
        return reel.charAt(mod(pos + row - 1, reel.length()));
    }

    /**
     * 止まっているリール（{@code pos[r] >= 0}）から、もう決まった「そろった形」の組（ビット）。ライン役は3つとも止まってから、
     * チェリーは左のリールが止まった時から決まる。
     */
    public static int completed(SlotSpec spec, int[] pos) {
        int mask = 0;
        int groups = spec.groups();
        if (pos[0] >= 0) {
            for (int g = 0; g < groups; g++) {
                if (!spec.groupCherry(g)) {
                    continue;
                }
                char c = spec.groupPattern(g)[0];
                for (int row : spec.groupRows(g)) {
                    if (symbolAt(spec, 0, pos[0], row) == c) {
                        mask |= 1 << g;
                    }
                }
            }
        }
        if (pos[0] >= 0 && pos[1] >= 0 && pos[2] >= 0) {
            for (int[] line : spec.lines()) {
                char a = symbolAt(spec, 0, pos[0], line[0]);
                char b = symbolAt(spec, 1, pos[1], line[1]);
                char c = symbolAt(spec, 2, pos[2], line[2]);
                for (int g = 0; g < groups; g++) {
                    if (spec.groupCherry(g)) {
                        continue;
                    }
                    char[] p = spec.groupPattern(g);
                    if (p[0] == a && p[1] == b && p[2] == c) {
                        mask |= 1 << g;
                    }
                }
            }
        }
        return mask;
    }

    /**
     * リール r を、押した位置 press から止める。goal はねらう組（なければ −1）、allowed はそろってよい組のビット。
     * 戻り値は止まる位置（pos[r] にも入れる）。
     */
    public static int stop(SlotSpec spec, int[] pos, int r, int press, int goal, int allowed) {
        int n = spec.reelLength();
        int stopped = 0;
        for (int i = 0; i < 3; i++) {
            if (i != r && pos[i] >= 0) {
                stopped++;
            }
        }
        int best = mod(press, n);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int k = 0; k <= MAX_SLIP; k++) {
            int q = mod(press - k, n);
            pos[r] = q;
            double score = switch (stopped) {
                case 2 -> finalScore(spec, pos, goal, allowed);
                case 1 -> secondScore(spec, pos, goal, allowed);
                default -> firstScore(spec, pos, r, goal, allowed);
            };
            score -= k * 0.01;
            if (score > bestScore) {
                bestScore = score;
                best = q;
            }
        }
        pos[r] = best;
        return best;
    }

    /** 3つ目：そろってはいけない形がそろえば負け、ねらう形がそろえば勝ち。 */
    static double finalScore(SlotSpec spec, int[] pos, int goal, int allowed) {
        int done = completed(spec, pos);
        if ((done & ~allowed) != 0) {
            return FORBIDDEN;
        }
        return goal >= 0 && (done & (1 << goal)) != 0 ? 1000.0 : 0.0;
    }

    /** 2つ目：残りのリールの、押す所それぞれで、よけられるか・ねらう形がそろうかを数える（先読み）。 */
    static double secondScore(SlotSpec spec, int[] pos, int goal, int allowed) {
        int early = completed(spec, pos);
        if ((early & ~allowed) != 0) {
            return FORBIDDEN;
        }
        int last = pos[0] < 0 ? 0 : pos[1] < 0 ? 1 : 2;
        int n = spec.reelLength();
        // 3つ目のリールの止まる所それぞれで、良し悪しを先に数える（押す所ごとに、そこから4コマの中で選ぶ）
        double[] at = new double[n];
        for (int q = 0; q < n; q++) {
            pos[last] = q;
            at[q] = finalScore(spec, pos, goal, allowed);
        }
        pos[last] = -1;
        int safe = 0;
        int hit = 0;
        for (int press = 0; press < n; press++) {
            boolean anySafe = false;
            boolean anyHit = false;
            for (int k = 0; k <= MAX_SLIP; k++) {
                double s = at[mod(press - k, n)];
                if (s > FORBIDDEN) {
                    anySafe = true;
                }
                if (s >= 1000.0) {
                    anyHit = true;
                }
            }
            if (anySafe) {
                safe++;
            }
            if (anyHit) {
                hit++;
            }
        }
        // よけられない押し所が1つでもある止め方は、ほぼ選ばない
        return (safe - n) * 1000.0 + hit * 10.0;
    }

    /** 1つ目：チェリーがもう決まる（左のリール）ならその良し悪し。ねらう形の図柄が有効ラインに来る所を少しだけ好む。 */
    static double firstScore(SlotSpec spec, int[] pos, int r, int goal, int allowed) {
        int early = completed(spec, pos);
        if ((early & ~allowed) != 0) {
            return FORBIDDEN;
        }
        double score = 0;
        if (goal >= 0 && spec.groupCherry(goal)) {
            score += (early & (1 << goal)) != 0 ? 500.0 : 0.0;
        }
        if (goal >= 0 && !spec.groupCherry(goal)) {
            // ねらう図柄を、まず中段に（中段はどのリールでも届くように並べてある）。届かなければ、ほかのラインの段に
            char want = spec.groupPattern(goal)[r];
            if (symbolAt(spec, r, pos[r], 1) == want) {
                score += 50.0;
            }
            for (int[] line : spec.lines()) {
                if (symbolAt(spec, r, pos[r], line[r]) == want) {
                    score += 20.0;
                }
            }
        }
        // ねらっていない形の図柄が、多くのラインにかかる止め方を少しきらう（あとでよけにくくなる）
        for (int[] line : spec.lines()) {
            char c = symbolAt(spec, r, pos[r], line[r]);
            for (int g = 0; g < spec.groups(); g++) {
                if (spec.groupCherry(g) || (allowed & (1 << g)) != 0) {
                    continue;
                }
                if (spec.groupPattern(g)[r] == c) {
                    score -= 1.0;
                }
            }
        }
        return score;
    }
}
