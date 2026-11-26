package jp.amemaru.lostconnection.casino.parlor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * スロットの台の動き（11月27日【補完】）。レバーで内部抽選 → 止めボタンでリール制御 → 3つ止まったら払い出し。
 *
 * <p>Aタイプ：ボーナス（BIG・REG）が当たると印を持ち越し、告知ランプが光る（レバーで光る「先告知」、3つ目を止めたあとに光る「後告知」、
 * まれにリールが止まる「フリーズ」・虹色に光る・回り始めが遅れる）。印のあるうちに7をそろえると、ボーナスが始まる。小役が当たった
 * ゲームは小役が先（ボーナスはそろわない）。</p>
 *
 * <p>AT：押し順ベル（6通りの押し順のうち1つで9枚、ちがうと1枚）。通常はレア役でチャンスゾーン（CZ）や AT に入り、CZ で当てると AT。
 * AT の間は押し順のナビが出て、ベルを毎回取れる。レア役でゲーム数が増える（上乗せ）。AT のないまま天井のゲーム数に来ると AT。</p>
 */
public final class SlotEngine {
    /** 押し順（左 0・中 1・右 2）の6通り。 */
    public static final int[][] ORDERS = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};

    private SlotEngine() {
    }

    /** 台の状態（台ごと。保存する）。 */
    public static final class State {
        public int setting = 1;
        /** 持ち越しているボーナス（Aタイプ。まだそろっていない）。空ならなし */
        public String carried = "";
        /** 告知ランプが光っている */
        public boolean lamp;
        /** 消化しているボーナス（空ならなし）と、その払い出し */
        public String bonus = "";
        public int bonusPaid;
        /** AT：残りのゲーム数・CZ の残り・CZ で当てたか・天井までの数え（AT のない通常のゲーム数）・この AT で増えた枚数と上乗せ */
        public int atGames;
        public int czGames;
        public boolean czWon;
        public int sinceAt;
        public int atGot;
        public int atAdded;
        /** 次のゲームはリプレイ（掛けなくてよい） */
        public boolean replay;
        /** 遊んでいる途中のゲーム（なければ null） */
        public Game game;

        public JsonObject save() {
            JsonObject o = new JsonObject();
            o.addProperty("setting", setting);
            o.addProperty("carried", carried);
            o.addProperty("lamp", lamp);
            o.addProperty("bonus", bonus);
            o.addProperty("bonusPaid", bonusPaid);
            o.addProperty("atGames", atGames);
            o.addProperty("czGames", czGames);
            o.addProperty("czWon", czWon);
            o.addProperty("sinceAt", sinceAt);
            o.addProperty("atGot", atGot);
            o.addProperty("atAdded", atAdded);
            o.addProperty("replay", replay);
            return o;
        }

        public void load(JsonObject o) {
            setting = Math.max(1, Math.min(6, ParlorJson.optInt(o, "setting", 1, 1, 6)));
            carried = ParlorJson.optString(o, "carried", "");
            lamp = ParlorJson.optBool(o, "lamp", false);
            bonus = ParlorJson.optString(o, "bonus", "");
            bonusPaid = ParlorJson.optInt(o, "bonusPaid", 0, 0, 100000);
            atGames = ParlorJson.optInt(o, "atGames", 0, 0, 100000);
            czGames = ParlorJson.optInt(o, "czGames", 0, 0, 1000);
            czWon = ParlorJson.optBool(o, "czWon", false);
            sinceAt = ParlorJson.optInt(o, "sinceAt", 0, 0, 1000000);
            atGot = ParlorJson.optInt(o, "atGot", 0, -1000000, 1000000);
            atAdded = ParlorJson.optInt(o, "atAdded", 0, 0, 100000);
            replay = ParlorJson.optBool(o, "replay", false);
            game = null;
        }

        /** ボーナス・CZ・AT の最中か（台を離れても、しばらくその人の台にしておく）。 */
        public boolean inFeature() {
            return !bonus.isEmpty() || !carried.isEmpty() || atGames > 0 || czGames > 0;
        }
    }

    /** 1ゲーム。 */
    public static final class Game {
        /** 引いた役（はずれは空） */
        public String role = "";
        /** 掛けた枚数（リプレイなら 0） */
        public int bet;
        /** このゲームで、ボーナス中か（そのボーナスのID） */
        public String inBonus = "";
        /** 押し順ベルの正解（ORDERS の番号。なければ −1） */
        public int order = -1;
        /** 押し順のナビを出すか（AT の間） */
        public boolean nav;
        public final int[] pos = {-1, -1, -1};
        public final int[] pressed = {-1, -1, -1};
        public final int[] stopOrder = {-1, -1, -1};
        public int stops;
        /** このゲームで当たったボーナス（新しく引いた）と、そのランプの出し方 */
        public String newBonus = "";
        public String lampTiming = "";
        /** AT のできごと（CZ・AT・上乗せ・天井） */
        public final List<String> events = new ArrayList<>();
    }

    /** レバーを叩いたときに画面へ送る中身（引いた役そのものは送らない）。 */
    public record Start(int bet, boolean free, boolean lampNow, String effect, int navOrder, List<String> events) {
    }

    /** 3つ止まったあとの払い出し（bet はそのゲームで掛けた枚数。リプレイなら 0。bonusGame はボーナス中のゲーム）。 */
    public record Result(int payout, boolean replay, String aligned, boolean lampNow, String bonusStarted, String bonusEnded,
                         int bonusPaid, List<String> events, int bet, boolean bonusGame) {
    }

    // ================================================================ レバー

    /** このゲームで掛ける枚数（リプレイなら 0）。 */
    public static int betFor(SlotSpec spec, State st) {
        if (st.replay) {
            return 0;
        }
        if (!st.bonus.isEmpty()) {
            SlotSpec.Bonus b = spec.bonus(st.bonus);
            return b == null ? spec.bet() : b.bet();
        }
        return spec.bet();
    }

    /** レバー：内部抽選。呼ぶ前に、掛ける枚数（{@link #betFor}）を持っているかを確かめる。 */
    public static Start lever(SlotSpec spec, State st, RandomGenerator rnd) {
        Game g = new Game();
        g.bet = betFor(spec, st);
        boolean free = st.replay;
        st.replay = false;
        g.inBonus = st.bonus;
        boolean lampNow = false;
        String effect = "";
        if (!st.bonus.isEmpty()) {
            // ボーナス中：ボーナスの役がそろう
            SlotSpec.Bonus b = spec.bonus(st.bonus);
            g.role = b == null ? "" : b.role();
        } else {
            g.role = draw(spec, st, rnd);
            SlotSpec.Role role = spec.role(g.role);
            if (role != null && role.isBonus()) {
                // ボーナスの印（Aタイプ）：持ち越す。小役ではないので、このゲームでもそろえられる
                st.carried = role.bonus();
                g.newBonus = role.bonus();
                g.role = "";
                double x = rnd.nextDouble();
                SlotSpec.Lamp lamp = spec.lamp();
                if (x < lamp.premium()) {
                    String[] kinds = {"freeze", "rainbow", "late"};
                    effect = kinds[rnd.nextInt(kinds.length)];
                    g.lampTiming = "premium";
                    st.lamp = true;
                    lampNow = !"late".equals(effect);
                } else if (x < lamp.premium() + lamp.pre()) {
                    g.lampTiming = "pre";
                    st.lamp = true;
                    lampNow = true;
                } else {
                    g.lampTiming = "post";
                }
            }
            if (role != null && role.kind() == SlotSpec.RoleKind.ORDER) {
                g.order = rnd.nextInt(ORDERS.length);
            }
            if (spec.type() == SlotSpec.Type.AT) {
                atLever(spec, st, g, rnd);
            }
        }
        st.game = g;
        int nav = g.nav && g.order >= 0 ? g.order : -1;
        return new Start(g.bet, free, lampNow, effect, nav, List.copyOf(g.events));
    }

    /** 内部抽選。ボーナスを持ち越しているあいだは、ボーナスの役は引かない（その分ははずれ）。 */
    static String draw(SlotSpec spec, State st, RandomGenerator rnd) {
        double x = rnd.nextDouble();
        double acc = 0;
        for (SlotSpec.Role role : spec.roles()) {
            double p = role.chance(st.setting);
            acc += p;
            if (x < acc) {
                if (role.isBonus() && !st.carried.isEmpty()) {
                    return "";
                }
                return role.id();
            }
        }
        return "";
    }

    /** AT の台のレバー：天井・AT の直撃・CZ・CZ の当たり・上乗せ・ナビ。 */
    static void atLever(SlotSpec spec, State st, Game g, RandomGenerator rnd) {
        SlotSpec.At at = spec.at();
        String role = g.role.isEmpty() ? "other" : g.role;
        if (st.atGames > 0) {
            g.nav = true;
            double add = at.pct(at.addChance(), role, st.setting);
            if (add > 0 && rnd.nextDouble() < add) {
                int games = at.games(role);
                st.atGames += games;
                st.atAdded += games;
                g.events.add("add:" + games);
            }
            return;
        }
        if (st.czGames > 0) {
            if (!st.czWon && rnd.nextDouble() < at.pct(at.czWin(), role, st.setting)) {
                st.czWon = true;
                g.events.add("cz_hit");
            }
            return;
        }
        st.sinceAt++;
        if (st.sinceAt >= at.ceiling()) {
            startAt(spec, st);
            g.events.add("ceiling");
            g.events.add("at_start");
            return;
        }
        if (rnd.nextDouble() < at.pct(at.direct(), role, st.setting)) {
            startAt(spec, st);
            g.events.add("at_start");
            return;
        }
        if (rnd.nextDouble() < at.pct(at.cz(), role, st.setting)) {
            st.czGames = at.czGames() + 1;   // このゲームの終わりに1つ減って、次のゲームから数える
            st.czWon = false;
            g.events.add("cz_start");
        }
    }

    static void startAt(SlotSpec spec, State st) {
        // このゲームの終わりに1つ減るので、1つ多く入れる
        st.atGames = spec.at().initial() + 1;
        st.atGot = 0;
        st.atAdded = 0;
        st.czGames = 0;
        st.czWon = false;
        st.sinceAt = 0;
    }

    // ================================================================ 止めボタン

    /** そのゲームでねらう組（なければ −1）と、そろってよい組のビット（{goal, allowed}）。 */
    static int[] goal(SlotSpec spec, State st, Game g) {
        SlotSpec.Role role = spec.role(g.role);
        if (role != null) {
            if (role.kind() == SlotSpec.RoleKind.ORDER) {
                // 押し順ベル：ここまでの押し順が正解と同じなら、そろえる
                for (int i = 0; i < g.stops; i++) {
                    if (g.stopOrder[i] != ORDERS[g.order][i]) {
                        return new int[]{-1, 0};
                    }
                }
            }
            int group = spec.groupOf(role);
            return new int[]{group, 1 << group};
        }
        if (!st.carried.isEmpty() && st.bonus.isEmpty()) {
            SlotSpec.Role bonusRole = bonusRole(spec, st.carried);
            if (bonusRole != null) {
                int group = spec.groupOf(bonusRole);
                return new int[]{group, 1 << group};
            }
        }
        return new int[]{-1, 0};
    }

    static SlotSpec.Role bonusRole(SlotSpec spec, String bonus) {
        for (SlotSpec.Role r : spec.roles()) {
            if (r.isBonus() && r.bonus().equals(bonus)) {
                return r;
            }
        }
        return null;
    }

    /** 止めボタン：リール reel を、押した位置 press から止める。止まった位置を返す（もう止まっていれば、その位置）。 */
    public static int stop(SlotSpec spec, State st, int reel, int press) {
        Game g = st.game;
        if (g == null || reel < 0 || reel > 2) {
            return -1;
        }
        if (g.pos[reel] >= 0) {
            return g.pos[reel];
        }
        g.stopOrder[g.stops] = reel;
        g.stops++;
        int[] aim = goal(spec, st, g);
        g.pressed[reel] = ReelControl.mod(press, spec.reelLength());
        int[] pos = g.pos.clone();
        int at = ReelControl.stop(spec, pos, reel, press, aim[0], aim[1]);
        g.pos[reel] = at;
        return at;
    }

    // ================================================================ 払い出し

    /** 3つ止まったあと：払い出し・ボーナスの始まりと終わり・AT のゲーム数。 */
    public static Result finish(SlotSpec spec, State st) {
        Game g = st.game;
        st.game = null;
        if (g == null) {
            return new Result(0, false, "", false, "", "", 0, List.of(), 0, false);
        }
        List<String> events = new ArrayList<>(g.events);
        int done = ReelControl.completed(spec, g.pos);
        int payout = 0;
        boolean replay = false;
        String aligned = "";
        String started = "";
        String ended = "";
        SlotSpec.Role role = spec.role(g.role);
        if (!g.inBonus.isEmpty()) {
            SlotSpec.Bonus b = spec.bonus(g.inBonus);
            if (b != null && role != null && has(done, spec.groupOf(role))) {
                payout = b.pay();
                aligned = role.id();
            }
            st.bonusPaid += payout;
            if (b == null || st.bonusPaid >= b.cap()) {
                ended = st.bonus;
                st.bonus = "";
            }
        } else {
            if (role != null && has(done, spec.groupOf(role))) {
                aligned = role.id();
                payout = role.pay();
                replay = role.replay();
            } else if (role != null && role.kind() == SlotSpec.RoleKind.ORDER) {
                payout = role.missPay();
                aligned = role.id() + ":miss";
            }
            SlotSpec.Role carriedRole = st.carried.isEmpty() ? null : bonusRole(spec, st.carried);
            if (carriedRole != null && has(done, spec.groupOf(carriedRole)) && (role == null || !aligned.equals(role.id()))) {
                started = st.carried;
                st.bonus = st.carried;
                st.bonusPaid = 0;
                st.carried = "";
                st.lamp = false;
                aligned = carriedRole.id();
            }
        }
        boolean lampNow = false;
        if ("post".equals(g.lampTiming) && !st.carried.isEmpty() && !st.lamp) {
            st.lamp = true;
            lampNow = true;
        }
        if ("premium".equals(g.lampTiming) && !st.lamp && !st.carried.isEmpty()) {
            st.lamp = true;
            lampNow = true;
        }
        st.replay = replay;
        if (spec.type() == SlotSpec.Type.AT) {
            if (g.nav) {
                // AT の1ゲーム
                st.atGot += payout - g.bet;
                st.atGames--;
                if (st.atGames == 0) {
                    events.add("at_end:" + st.atGot);
                }
            } else if (st.atGames > 0) {
                // このゲームのレバーで AT に入った（直撃・天井）：次のゲームから数える
                st.atGames--;
            } else if (st.czGames > 0) {
                st.czGames--;
                if (st.czGames == 0) {
                    if (st.czWon) {
                        startAt(spec, st);
                        st.atGames--;      // 次のゲームから数える（startAt の1つ多い分を戻す）
                        events.add("cz_success");
                        events.add("at_start");
                    } else {
                        events.add("cz_fail");
                    }
                    st.czWon = false;
                }
            }
        }
        return new Result(payout, replay, aligned, lampNow, started, ended, st.bonusPaid, events, g.bet, !g.inBonus.isEmpty());
    }

    static boolean has(int mask, int group) {
        return group >= 0 && (mask & (1 << group)) != 0;
    }

    /** 1ゲームの払い出しの役の名前（画面の表示に使う）。 */
    public static String describe(SlotSpec spec, Result r) {
        return r.aligned();
    }

    /** ゲームの途中で台を離れたとき：残りのリールを、押した所の見本で止めて終える（チップは失わせない）。 */
    public static Result autoFinish(SlotSpec spec, State st, RandomGenerator rnd) {
        Game g = st.game;
        if (g == null) {
            return new Result(0, false, "", false, "", "", 0, List.of(), 0, false);
        }
        for (int r = 0; r < 3; r++) {
            if (g.pos[r] < 0) {
                stop(spec, st, r, rnd.nextInt(spec.reelLength()));
            }
        }
        return finish(spec, st);
    }

    /** 画面へ送る、止まっているリールの位置。 */
    public static JsonArray positions(Game g) {
        JsonArray a = new JsonArray();
        for (int p : g.pos) {
            a.add(p);
        }
        return a;
    }
}
