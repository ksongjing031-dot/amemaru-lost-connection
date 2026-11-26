package jp.amemaru.lostconnection.casino.parlor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * パチンコの台の動き（11月27日【補完】。本物の「デジパチ」と同じ流れ）。
 *
 * <ul>
 *   <li>打つ：1分に100個（12tick に1個）。通常はハンドルを左へ（左打ち）、ヘソ（スタートチャッカー）に入ると3個戻り、保留（4つまで）
 *       が1つ増える。入る見込みは、その日の釘（1,000円あたりの回転数）と、ハンドルの強さ（ストローク）で決まる</li>
 *   <li>保留に入ったときに抽選する（先読み：保留の色に出る）。図柄が回り、止まると当たり・はずれ</li>
 *   <li>大当たり：右打ちで大入賞口（アタッカー）へ。1ラウンド10個（カウント）×15個。あとは確変（次の当たりまで高確率と電サポ）・ST
 *       （決まった回数だけ）・時短（通常の確率で電サポ）・通常</li>
 *   <li>電サポ：右打ちで電チューへ。玉がほとんど減らず、保留は電チューの方（特図2）を先に回す</li>
 * </ul>
 */
public final class PachinkoEngine {
    /** 1個を打つ間（12tick＝1分に100個）。 */
    public static final int SHOT_TICKS = 12;
    /** 大当たりのファンファーレ・ラウンドの間・終わりの時間と、1ラウンドの最長（tick）。 */
    public static final int OPENING_TICKS = 70;
    public static final int INTERVAL_TICKS = 40;
    public static final int ENDING_TICKS = 90;
    public static final int ROUND_MAX_TICKS = 600;
    public static final int MAX_HOLDS = 4;

    private PachinkoEngine() {
    }

    public enum Mode { NORMAL, KAKUHEN, ST, JITAN }

    /** 保留（入ったときに抽選した結果と、保留の色）。色：0 白・1 青・2 緑・3 赤・4 金・5 虹。 */
    public record Hold(boolean hit, String jackpot, int color, boolean denchu) {
    }

    /** 図柄が回る1回の見せ方（サーバーと画面で同じ時間で動く）。 */
    public record Script(int ticks, int[] digits, String reach, int pseudo, int step, int cutin, boolean group, boolean hot,
                         boolean hit, String jackpot, boolean promote, int holdColor, int leftAt, int rightAt, int centerAt,
                         boolean support) {
        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("ticks", ticks);
            JsonArray d = new JsonArray();
            for (int v : digits) {
                d.add(v);
            }
            o.add("digits", d);
            o.addProperty("reach", reach);
            o.addProperty("pseudo", pseudo);
            o.addProperty("step", step);
            o.addProperty("cutin", cutin);
            o.addProperty("group", group);
            o.addProperty("hot", hot);
            o.addProperty("hit", hit);
            o.addProperty("jackpot", jackpot);
            o.addProperty("promote", promote);
            o.addProperty("hold", holdColor);
            o.addProperty("left", leftAt);
            o.addProperty("right", rightAt);
            o.addProperty("center", centerAt);
            o.addProperty("support", support);
            return o;
        }
    }

    public enum Phase { OPENING, ROUND, INTERVAL, ENDING }

    /** 大当たりの最中。 */
    public static final class Round {
        public String jackpot = "";
        public int rounds;
        public int round;
        public int count;
        public int paid;
        public Phase phase = Phase.OPENING;
        public int timer;
        public boolean promoted;
    }

    /** 台の状態（台ごと。保存する）。 */
    public static final class State {
        public int setting = 1;
        public double nail = 18.0;
        public Mode mode = Mode.NORMAL;
        public int modeSpins;
        public boolean lt;
        public final List<Hold> heso = new ArrayList<>();
        public final List<Hold> denchu = new ArrayList<>();
        /** 回っている図柄（なければ null）と、回り始めてからの tick */
        public Script spin;
        public Hold spinHold;
        public int spinTick;
        /** 大当たりの最中（なければ null） */
        public Round round;
        /** 連チャン（電サポ中の当たりで続く）と、その出玉 */
        public int chain;
        public int chainBalls;
        /** 打つ間合いを数える */
        public int shotClock;

        public boolean support() {
            return mode != Mode.NORMAL;
        }

        public boolean inFeature() {
            return round != null || mode != Mode.NORMAL || spin != null && spin.hit()
                    || heso.stream().anyMatch(Hold::hit) || denchu.stream().anyMatch(Hold::hit);
        }

        public JsonObject save() {
            JsonObject o = new JsonObject();
            o.addProperty("setting", setting);
            o.addProperty("nail", nail);
            o.addProperty("mode", mode.name());
            o.addProperty("modeSpins", modeSpins);
            o.addProperty("lt", lt);
            o.addProperty("chain", chain);
            o.addProperty("chainBalls", chainBalls);
            JsonArray holds = new JsonArray();
            for (Hold h : heso) {
                holds.add(holdJson(h));
            }
            for (Hold h : denchu) {
                holds.add(holdJson(h));
            }
            // 回っている図柄と大当たりは、保存しない代わりに、当たりなら保留の先頭へ戻す（再起動しても失わない）
            if (spin != null && spinHold != null) {
                JsonObject h = holdJson(spinHold);
                h.addProperty("first", true);
                holds.add(h);
            }
            o.add("holds", holds);
            if (round != null) {
                o.addProperty("roundJackpot", round.jackpot);
                o.addProperty("roundRounds", round.rounds);
                o.addProperty("roundRound", round.round);
                o.addProperty("roundPhase", round.phase.name());
                o.addProperty("roundPaid", round.paid);
            }
            return o;
        }

        public void load(JsonObject o) {
            setting = ParlorJson.optInt(o, "setting", 1, 1, 6);
            nail = ParlorJson.optNumber(o, "nail", 18.0, 1, 100);
            try {
                mode = Mode.valueOf(ParlorJson.optString(o, "mode", "NORMAL"));
            } catch (IllegalArgumentException e) {
                mode = Mode.NORMAL;
            }
            modeSpins = ParlorJson.optInt(o, "modeSpins", 0, 0, 100000);
            lt = ParlorJson.optBool(o, "lt", false);
            chain = ParlorJson.optInt(o, "chain", 0, 0, 100000);
            chainBalls = ParlorJson.optInt(o, "chainBalls", 0, 0, 100000000);
            heso.clear();
            denchu.clear();
            JsonArray holds = ParlorJson.optArray(o, "holds");
            if (holds != null) {
                for (var e : holds) {
                    JsonObject h = e.getAsJsonObject();
                    Hold hold = new Hold(ParlorJson.optBool(h, "hit", false), ParlorJson.optString(h, "jackpot", ""),
                            ParlorJson.optInt(h, "color", 0, 0, 5), ParlorJson.optBool(h, "denchu", false));
                    List<Hold> list = hold.denchu() ? denchu : heso;
                    if (ParlorJson.optBool(h, "first", false)) {
                        list.add(0, hold);
                    } else if (list.size() < MAX_HOLDS) {
                        list.add(hold);
                    }
                }
            }
            spin = null;
            spinHold = null;
            round = null;
            String rj = ParlorJson.optString(o, "roundJackpot", "");
            if (!rj.isEmpty()) {
                // ラウンドの途中で止まったときは、そのラウンドをはじめからやり直す
                round = new Round();
                round.jackpot = rj;
                round.rounds = ParlorJson.optInt(o, "roundRounds", 1, 1, 30);
                round.round = ParlorJson.optInt(o, "roundRound", 0, 0, 30);
                round.paid = ParlorJson.optInt(o, "roundPaid", 0, 0, 1000000);
                try {
                    round.phase = Phase.valueOf(ParlorJson.optString(o, "roundPhase", "OPENING"));
                } catch (IllegalArgumentException e) {
                    round.phase = Phase.OPENING;
                }
                if (round.phase == Phase.ROUND) {
                    round.round = Math.max(0, round.round - 1);
                    round.phase = round.round == 0 ? Phase.OPENING : Phase.INTERVAL;
                }
                round.timer = 0;
            }
        }

        private static JsonObject holdJson(Hold h) {
            JsonObject o = new JsonObject();
            o.addProperty("hit", h.hit());
            o.addProperty("jackpot", h.jackpot());
            o.addProperty("color", h.color());
            o.addProperty("denchu", h.denchu());
            return o;
        }
    }

    /** 1個打った結果。 */
    public record Shot(int used, int back, boolean heso, boolean denchu, boolean attacker, Hold hold) {
    }

    /** 1tick の出来事。 */
    public record Tick(Script started, Script ended, boolean jackpotStart, boolean roundStart, boolean roundEnd,
                       String jackpotEnd, int jackpotPaid, Mode modeChanged, int spinsDone) {
        static final Tick NONE = new Tick(null, null, false, false, false, "", 0, null, 0);
    }

    // ================================================================ 打つ

    /** ハンドルの強さ（0〜100）での、ヘソへの入りやすさ（ストローク。55〜80 がいちばん良い）。10 より弱いと玉は飛ばない。 */
    public static double stroke(int strength) {
        if (strength < 10) {
            return 0.0;
        }
        if (strength >= 55 && strength <= 80) {
            return 1.0;
        }
        if (strength < 55) {
            return 0.55 + 0.45 * (strength - 10) / 45.0;
        }
        return 1.0 - 0.45 * (strength - 80) / 20.0;
    }

    /** 釘（1,000円＝250個あたりの回転数 R）から、1個がヘソに入る見込み q（R = 250q ÷（1 − 戻り×q））。 */
    public static double hesoChance(PachinkoSpec spec, double nail) {
        return nail / (250.0 + spec.hesoPay() * nail);
    }

    /**
     * 1個打つ。strength はハンドルの強さ、right は右打ち。玉を持っているかは呼ぶ側で確かめる。
     */
    public static Shot shoot(PachinkoSpec spec, State st, int strength, boolean right, RandomGenerator rnd) {
        double stroke = stroke(strength);
        if (stroke <= 0) {
            return new Shot(0, 0, false, false, false, null);
        }
        Round r = st.round;
        if (right) {
            if (r != null && r.phase == Phase.ROUND && r.count < spec.count()) {
                r.count++;
                r.paid += spec.ballPay();
                return new Shot(1, spec.ballPay(), false, false, true, null);
            }
            if (st.support() && r == null) {
                int back = 0;
                Hold hold = null;
                boolean in = rnd.nextDouble() < spec.denchu();
                if (in) {
                    back = spec.denchuPay();
                    if (st.denchu.size() < MAX_HOLDS) {
                        hold = draw(spec, st, true, rnd);
                        st.denchu.add(hold);
                    }
                }
                return new Shot(1, back, false, in, false, hold);
            }
            // 通常の右打ち・大当たりの間（ラウンドの間）の右打ちは、入らない
            return new Shot(1, 0, false, false, false, null);
        }
        // 左打ち：ヘソをねらう（電サポ中も、ヘソに入れば保留は増える）
        double q = hesoChance(spec, st.nail) * stroke * (r != null ? 0.5 : 1.0);
        if (rnd.nextDouble() < q) {
            Hold hold = null;
            if (st.heso.size() < MAX_HOLDS) {
                hold = draw(spec, st, false, rnd);
                st.heso.add(hold);
            }
            return new Shot(1, spec.hesoPay(), true, false, false, hold);
        }
        return new Shot(1, 0, false, false, false, null);
    }

    /** 入ったときの抽選（その時の確率で）と、保留の色（先読み）。 */
    static Hold draw(PachinkoSpec spec, State st, boolean denchu, RandomGenerator rnd) {
        boolean high = st.mode == Mode.KAKUHEN || st.mode == Mode.ST;
        double p = 1.0 / (high ? spec.high() : spec.normal(st.setting));
        boolean hit = rnd.nextDouble() < p;
        String jackpot = "";
        if (hit) {
            double total = 0;
            for (PachinkoSpec.Jackpot j : spec.jackpots()) {
                total += weight(j, denchu, st.lt);
            }
            double x = rnd.nextDouble() * total;
            for (PachinkoSpec.Jackpot j : spec.jackpots()) {
                x -= weight(j, denchu, st.lt);
                if (x < 0) {
                    jackpot = j.id();
                    break;
                }
            }
            if (jackpot.isEmpty()) {
                jackpot = spec.jackpots().get(spec.jackpots().size() - 1).id();
            }
        }
        int color = pick(rnd, hit ? new double[]{10, 15, 25, 35, 12, 3} : new double[]{93, 5, 1.5, 0.45, 0.05, 0});
        if (st.support()) {
            // 電サポ中の保留は、ほとんど白（当たりはときどき色が変わる）
            color = hit ? pick(rnd, new double[]{55, 10, 10, 15, 7, 3}) : 0;
        }
        return new Hold(hit, jackpot, color, denchu);
    }

    static double weight(PachinkoSpec.Jackpot j, boolean denchu, boolean lt) {
        return denchu ? (lt ? j.denchuLt() : j.denchu()) : j.heso();
    }

    static int pick(RandomGenerator rnd, double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double x = rnd.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            x -= weights[i];
            if (x < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    // ================================================================ 1tick

    /** 1tick 進める（図柄の回り・大当たりのラウンド）。 */
    public static Tick tick(PachinkoSpec spec, State st, RandomGenerator rnd) {
        Round r = st.round;
        if (r != null) {
            return tickRound(spec, st, r);
        }
        if (st.spin != null) {
            st.spinTick++;
            if (st.spinTick < st.spin.ticks()) {
                return Tick.NONE;
            }
            Script done = st.spin;
            Hold hold = st.spinHold;
            st.spin = null;
            st.spinHold = null;
            Mode changed = null;
            if (hold != null && hold.hit()) {
                Round nr = new Round();
                PachinkoSpec.Jackpot j = spec.jackpot(hold.jackpot());
                nr.jackpot = j.id();
                nr.rounds = j.rounds();
                nr.promoted = done.promote();
                st.round = nr;
                st.chain = st.support() ? st.chain + 1 : 1;
                if (st.chain == 1) {
                    st.chainBalls = 0;
                }
                return new Tick(null, done, true, false, false, "", 0, null, 1);
            }
            if (st.mode == Mode.ST || st.mode == Mode.JITAN) {
                st.modeSpins--;
                if (st.modeSpins <= 0) {
                    st.mode = Mode.NORMAL;
                    st.lt = false;
                    changed = Mode.NORMAL;
                }
            }
            return new Tick(null, done, false, false, false, "", 0, changed, 1);
        }
        // 次の保留を回す（電チューの方が先）
        Hold next = !st.denchu.isEmpty() ? st.denchu.remove(0) : !st.heso.isEmpty() ? st.heso.remove(0) : null;
        if (next == null) {
            return Tick.NONE;
        }
        int waiting = st.denchu.size() + st.heso.size();
        st.spin = script(spec, st, next, waiting, rnd);
        st.spinHold = next;
        st.spinTick = 0;
        return new Tick(st.spin, null, false, false, false, "", 0, null, 0);
    }

    static Tick tickRound(PachinkoSpec spec, State st, Round r) {
        r.timer++;
        switch (r.phase) {
            case OPENING -> {
                if (r.timer >= OPENING_TICKS) {
                    r.phase = Phase.ROUND;
                    r.round = Math.max(1, r.round + 1);
                    r.count = 0;
                    r.timer = 0;
                    return new Tick(null, null, false, true, false, "", 0, null, 0);
                }
            }
            case ROUND -> {
                if (r.count >= spec.count() || r.timer >= ROUND_MAX_TICKS) {
                    r.timer = 0;
                    if (r.round >= r.rounds) {
                        r.phase = Phase.ENDING;
                    } else {
                        r.phase = Phase.INTERVAL;
                    }
                    return new Tick(null, null, false, false, true, "", 0, null, 0);
                }
            }
            case INTERVAL -> {
                if (r.timer >= INTERVAL_TICKS) {
                    r.phase = Phase.ROUND;
                    r.round++;
                    r.count = 0;
                    r.timer = 0;
                    return new Tick(null, null, false, true, false, "", 0, null, 0);
                }
            }
            case ENDING -> {
                if (r.timer >= ENDING_TICKS) {
                    PachinkoSpec.Jackpot j = spec.jackpot(r.jackpot);
                    st.round = null;
                    st.chainBalls += r.paid;
                    switch (j.next()) {
                        case KAKUHEN -> {
                            st.mode = Mode.KAKUHEN;
                            st.modeSpins = 0;
                        }
                        case ST -> {
                            st.mode = Mode.ST;
                            st.modeSpins = j.spins();
                        }
                        case JITAN -> {
                            st.mode = Mode.JITAN;
                            st.modeSpins = j.spins();
                        }
                        case NORMAL -> {
                            st.mode = Mode.NORMAL;
                            st.modeSpins = 0;
                        }
                    }
                    st.lt = j.lt() || st.lt && j.kakuhen();
                    return new Tick(null, null, false, false, false, j.id(), r.paid, st.mode, 0);
                }
            }
        }
        return Tick.NONE;
    }

    // ================================================================ 見せ方（演出）

    /** 図柄の回り方を決める（当たり・はずれは保留で決まっている。ここでは見せ方だけ）。 */
    public static Script script(PachinkoSpec spec, State st, Hold hold, int waiting, RandomGenerator rnd) {
        return script(spec, st, hold, waiting, rnd, null);
    }

    /** forcedReach を決めると、そのリーチで見せる（管理者のコマンド・CIの写真で、時間を短くする）。 */
    public static Script script(PachinkoSpec spec, State st, Hold hold, int waiting, RandomGenerator rnd, String forcedReach) {
        boolean support = st.support();
        boolean hit = hold.hit();
        // リーチ：none・normal・super・special・all（全回転。当たり確定）
        String reach;
        if (forcedReach != null) {
            reach = forcedReach;
        } else if (hit) {
            reach = new String[]{"normal", "super", "special", "all"}[pick(rnd, new double[]{15, 47, 35, 3})];
        } else {
            reach = new String[]{"", "normal", "super", "special"}[pick(rnd, support ? new double[]{94, 5, 0.8, 0.2}
                    : new double[]{88, 10.68, 1.2, 0.12})];
        }
        boolean bigReach = "super".equals(reach) || "special".equals(reach);
        int pseudo = 0;
        int step = 0;
        int cutin = 0;
        boolean group = false;
        boolean hot = false;
        if (!support) {
            // 決めたリーチ（管理者・CI）は擬似連なし（リーチまでの時間を決まった長さにする）
            pseudo = forcedReach != null ? 0 : hit ? pick(rnd, new double[]{30, 25, 25, 20})
                    : reach.isEmpty() ? pick(rnd, new double[]{97, 3, 0, 0}) : pick(rnd, new double[]{80, 13, 5, 2});
            step = hit ? pick(rnd, new double[]{10, 10, 15, 20, 25, 20})
                    : pick(rnd, reach.isEmpty() ? new double[]{86, 9, 4, 0.9, 0.1, 0} : new double[]{55, 20, 13, 8, 3.5, 0.5});
            if (!reach.isEmpty()) {
                cutin = hit ? pick(rnd, new double[]{20, 10, 15, 35, 15, 5}) : pick(rnd, new double[]{80, 12, 6, 1.8, 0.2, 0});
            }
            group = hit ? rnd.nextDouble() < 0.3 : rnd.nextDouble() < (reach.isEmpty() ? 0.004 : 0.03);
            hot = bigReach && (hit ? rnd.nextDouble() < 0.35 : rnd.nextDouble() < 0.03);
        } else if (hit && !reach.isEmpty()) {
            cutin = pick(rnd, new double[]{40, 10, 15, 20, 10, 5});
        }
        // 図柄
        int[] digits = new int[3];
        boolean promote = false;
        PachinkoSpec.Jackpot jackpot = hit ? spec.jackpot(hold.jackpot()) : null;
        if (hit) {
            boolean odd = jackpot.kakuhen();
            if (odd && rnd.nextDouble() < 0.25) {
                // 確変なのに偶数でそろえて、大当たりの中で「昇格」させる
                odd = false;
                promote = true;
            }
            int[] pool = odd ? new int[]{1, 3, 5, 7, 7, 7, 9} : new int[]{2, 4, 6, 8};
            int v = pool[rnd.nextInt(pool.length)];
            digits[0] = v;
            digits[1] = v;
            digits[2] = v;
        } else if (!reach.isEmpty()) {
            int v = 1 + rnd.nextInt(9);
            int c;
            if (rnd.nextDouble() < 0.6) {
                c = rnd.nextBoolean() ? v % 9 + 1 : (v + 7) % 9 + 1;   // 前後の図柄で外れ
            } else {
                do {
                    c = 1 + rnd.nextInt(9);
                } while (c == v);
            }
            digits[0] = v;
            digits[1] = c;
            digits[2] = v;
        } else {
            digits[0] = 1 + rnd.nextInt(9);
            do {
                digits[2] = 1 + rnd.nextInt(9);
            } while (digits[2] == digits[0]);
            digits[1] = 1 + rnd.nextInt(9);
        }
        // 時間（tick）
        int base = support ? 30 : waiting <= 1 ? 200 : waiting == 2 ? 140 : 80;
        if (!reach.isEmpty() && !support) {
            base = Math.max(base, 140);
        }
        int leftAt = (int) (base * 0.55) + pseudo * 60;
        int rightAt = leftAt + (reach.isEmpty() ? 16 : 22);
        int reachTicks = switch (reach) {
            case "normal" -> support ? 80 : 160;
            case "super" -> support ? 200 : 500;
            case "special" -> support ? 300 : 800;
            case "all" -> 300;
            default -> 0;
        };
        int centerAt = reach.isEmpty() ? rightAt + 14 : rightAt + reachTicks;
        if ("all".equals(reach)) {
            leftAt = 60;
            rightAt = 60;
            centerAt = 60 + reachTicks;
        }
        int ticks = centerAt + 20;
        return new Script(ticks, digits, reach, pseudo, step, cutin, group, hot, hit, hit ? jackpot.id() : "", promote,
                hold.color(), leftAt, rightAt, centerAt, support);
    }

    /**
     * 台を離れたときの大当たり：残りのラウンドを、右打ちしたものとして消化する（玉を失わせない）。戻り値は持ち玉に足す数（打った玉を
     * 引いた分）。次の tick でラウンドが終わり、確変・時短などに移る。
     */
    public static int settleRound(PachinkoSpec spec, State st) {
        Round r = st.round;
        if (r == null || r.phase == Phase.ENDING) {
            return 0;
        }
        int counts = Math.max(0, r.rounds - r.round) * spec.count();
        if (r.phase == Phase.ROUND) {
            counts += Math.max(0, spec.count() - r.count);
        }
        r.paid += counts * spec.ballPay();
        r.round = r.rounds;
        r.phase = Phase.ENDING;
        r.timer = ENDING_TICKS;
        return counts * (spec.ballPay() - 1);
    }
}
