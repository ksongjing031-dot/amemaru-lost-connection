import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.amemaru.lostconnection.casino.parlor.PachinkoEngine;
import jp.amemaru.lostconnection.casino.parlor.PachinkoSpec;
import jp.amemaru.lostconnection.casino.parlor.ReelControl;
import jp.amemaru.lostconnection.casino.parlor.SlotEngine;
import jp.amemaru.lostconnection.casino.parlor.SlotSpec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * 地下遊技場の機種の数を確かめる（11月27日【補完】）。Mod と同じ台の動き（casino/parlor の SlotEngine・ReelControl・PachinkoEngine）を
 * そのまま使う。Minecraft なしで動く。
 *
 * <pre>
 *   bash tools/balance/parlor_sim.sh [機種のID ...]
 * </pre>
 *
 * スロット：リール制御の確かめ（そろえてはいけない役が、どの押し方でもそろわないか・リプレイとぶどうは必ずそろうか）と、設定1〜6の
 * 機械割（ボーナスは7をねらう・AT は押し順のナビに従う・ほかは適当に押す）。パチンコ：釘ごとの出玉率と、ボーダー（等価で釣り合う
 * 1,000円あたりの回転数）・初当たりの平均出玉。
 */
public final class ParlorSim {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        Set<String> only = new LinkedHashSet<>();
        for (int i = 1; i < args.length; i++) {
            only.add(args[i]);
        }
        try (var files = Files.list(dir)) {
            for (Path p : files.sorted().toList()) {
                String id = p.getFileName().toString().replace(".json", "");
                if (!only.isEmpty() && !only.contains(id)) {
                    continue;
                }
                JsonObject json = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                if ("slot".equals(json.get("kind").getAsString())) {
                    SlotSpec spec = SlotSpec.parse(id, json);
                    controlCheck(spec);
                    slotPayout(spec);
                } else {
                    pachinko(PachinkoSpec.parse(id, json));
                }
            }
        }
    }

    // ================================================================ スロット：リール制御

    static void controlCheck(SlotSpec spec) {
        System.out.println("== " + spec.id() + " " + spec.name() + "：リール制御");
        SplittableRandom rnd = new SplittableRandom(1);
        int n = spec.reelLength();
        // 見る形：はずれ・小役それぞれ・ボーナスの持ち越し
        java.util.List<String> cases = new java.util.ArrayList<>();
        cases.add("");
        for (SlotSpec.Role r : spec.roles()) {
            cases.add(r.id());
        }
        int samples = 30000;
        for (String c : cases) {
            int bad = 0;
            int hit = 0;
            String badExample = "";
            SlotSpec.Role role = spec.role(c);
            for (int s = 0; s < samples; s++) {
                SlotEngine.State st = new SlotEngine.State();
                SlotEngine.Game g = new SlotEngine.Game();
                if (role != null && role.isBonus()) {
                    st.carried = role.bonus();
                } else {
                    g.role = c;
                }
                if (role != null && role.kind() == SlotSpec.RoleKind.ORDER) {
                    g.order = rnd.nextInt(6);
                }
                st.game = g;
                int[] order = SlotEngine.ORDERS[rnd.nextInt(6)];
                for (int k = 0; k < 3; k++) {
                    SlotEngine.stop(spec, st, order[k], rnd.nextInt(n));
                }
                int done = ReelControl.completed(spec, g.pos);
                int want = role == null ? -1 : spec.groupOf(role);
                boolean orderOk = role == null || role.kind() != SlotSpec.RoleKind.ORDER
                        || java.util.Arrays.equals(order, SlotEngine.ORDERS[g.order]);
                int allowedMask = want >= 0 && orderOk ? 1 << want : 0;
                if ((done & ~allowedMask) != 0) {
                    bad++;
                    if (badExample.isEmpty()) {
                        badExample = "組" + Integer.toBinaryString(done) + " pos=" + java.util.Arrays.toString(g.pos)
                                + " order=" + java.util.Arrays.toString(order);
                    }
                }
                if (want >= 0 && (done & (1 << want)) != 0) {
                    hit++;
                }
            }
            System.out.printf(Locale.ROOT, "  %-8s そろう %6.2f%%  そろってはいけない形 %d %s%n", c.isEmpty() ? "はずれ" : c,
                    100.0 * hit / samples, bad, badExample);
        }
        // ボーナスの7を、ねらって押す（目押し）ときにそろうか
        for (SlotSpec.Role r : spec.roles()) {
            if (!r.isBonus()) {
                continue;
            }
            int ok = 0;
            int tries = 2000;
            for (int s = 0; s < tries; s++) {
                SlotEngine.State st = new SlotEngine.State();
                st.carried = r.bonus();
                st.game = new SlotEngine.Game();
                if (aim(spec, st, rnd)) {
                    ok++;
                }
            }
            System.out.printf(Locale.ROOT, "  %-8s ねらって押すと %.1f%%%n", r.id(), 100.0 * ok / tries);
        }
    }

    /** ボーナスをねらって押す（目押し）：リールごとに、その図柄が中段に来る2コマ手前で押す。そろったら true。 */
    static boolean aim(SlotSpec spec, SlotEngine.State st, SplittableRandom rnd) {
        aimNoFinish(spec, st, rnd);
        SlotEngine.Result res = SlotEngine.finish(spec, st);
        return !res.bonusStarted().isEmpty();
    }

    /** 目押し：その図柄が中段に止まる押し所（図柄の2コマ手前）のうち、本当に中段に止まる所を選ぶ（遊ぶ人は、どの7をねらうかを覚える）。 */
    static int aimPress(SlotSpec spec, SlotEngine.State st, int reel, char want, SplittableRandom rnd) {
        String strip = spec.reel(reel);
        java.util.List<Integer> good = new java.util.ArrayList<>();
        for (int i = 0; i < strip.length(); i++) {
            if (strip.charAt(i) != want) {
                continue;
            }
            int press = ReelControl.mod(i + 2, strip.length());
            SlotEngine.State copy = new SlotEngine.State();
            copy.load(st.save());
            copy.carried = st.carried;
            SlotEngine.Game g = new SlotEngine.Game();
            g.role = st.game.role;
            g.inBonus = st.game.inBonus;
            System.arraycopy(st.game.pos, 0, g.pos, 0, 3);
            System.arraycopy(st.game.stopOrder, 0, g.stopOrder, 0, 3);
            g.stops = st.game.stops;
            copy.game = g;
            int at = SlotEngine.stop(spec, copy, reel, press);
            if (ReelControl.symbolAt(spec, reel, at, 1) == want) {
                good.add(press);
            }
        }
        if (good.isEmpty()) {
            return rnd.nextInt(strip.length());
        }
        return good.get(rnd.nextInt(good.size()));
    }

    // ================================================================ スロット：機械割

    static void slotPayout(SlotSpec spec) {
        System.out.println("== " + spec.id() + " " + spec.name() + "：機械割（設定1〜6）");
        long games = 1_500_000;
        for (int setting = 1; setting <= 6; setting++) {
            SplittableRandom sr = new SplittableRandom(100 + setting);
            RandomGenerator rnd = sr::nextLong;
            SlotEngine.State st = new SlotEngine.State();
            st.setting = setting;
            long in = 0;
            long out = 0;
            long normalGames = 0;
            int big = 0;
            int reg = 0;
            int at = 0;
            int cz = 0;
            long atGames = 0;
            for (long i = 0; i < games; i++) {
                boolean bonusGame = !st.bonus.isEmpty();
                boolean atGame = st.atGames > 0;
                SlotEngine.Start start = SlotEngine.lever(spec, st, rnd);
                in += start.bet();
                if (!bonusGame) {
                    normalGames++;
                }
                if (atGame) {
                    atGames++;
                }
                SlotEngine.Game g = st.game;
                int n = spec.reelLength();
                if (!st.carried.isEmpty() && g.role.isEmpty() && st.bonus.isEmpty()) {
                    // 告知ランプが光った（このゲームで当たったときは、後告知なら次のゲームから）ら、7をねらう
                    if (st.lamp || "pre".equals(g.lampTiming)) {
                        aimNoFinish(spec, st, sr);
                    } else {
                        for (int r = 0; r < 3; r++) {
                            SlotEngine.stop(spec, st, r, sr.nextInt(n));
                        }
                    }
                } else if (start.navOrder() >= 0) {
                    int[] order = SlotEngine.ORDERS[start.navOrder()];
                    for (int r : order) {
                        SlotEngine.stop(spec, st, r, sr.nextInt(n));
                    }
                } else {
                    int[] order = SlotEngine.ORDERS[sr.nextInt(6)];
                    if (spec.type() == SlotSpec.Type.A) {
                        order = SlotEngine.ORDERS[0];
                    }
                    for (int r : order) {
                        SlotEngine.stop(spec, st, r, sr.nextInt(n));
                    }
                }
                SlotEngine.Result res = SlotEngine.finish(spec, st);
                out += res.payout();
                if (res.replay()) {
                    out += 0;  // リプレイは次のゲームの掛けがいらない（in に足さない）
                }
                if ("big".equals(res.bonusStarted())) {
                    big++;
                }
                if ("reg".equals(res.bonusStarted())) {
                    reg++;
                }
                for (String e : res.events()) {
                    if (e.equals("at_start")) {
                        at++;
                    }
                    if (e.equals("cz_start")) {
                        cz++;
                    }
                }
            }
            double rate = 100.0 * out / in;
            if (spec.type() == SlotSpec.Type.A) {
                System.out.printf(Locale.ROOT, "  設定%d  機械割 %6.2f%%（目標 %.1f）  BIG 1/%.1f  REG 1/%.1f  合算 1/%.1f%n", setting, rate,
                        spec.targetPayout()[setting - 1], (double) normalGames / big, (double) normalGames / reg,
                        (double) normalGames / (big + reg));
            } else {
                System.out.printf(Locale.ROOT, "  設定%d  機械割 %6.2f%%（目標 %.1f）  AT 1/%.1f  CZ 1/%.1f  AT 平均 %.1f ゲーム%n", setting, rate,
                        spec.targetPayout()[setting - 1], (double) (normalGames - atGames) / Math.max(1, at),
                        (double) (normalGames - atGames) / Math.max(1, cz), (double) atGames / Math.max(1, at));
            }
        }
    }

    static void aimNoFinish(SlotSpec spec, SlotEngine.State st, SplittableRandom rnd) {
        SlotSpec.Role bonus = null;
        for (SlotSpec.Role r : spec.roles()) {
            if (r.isBonus() && r.bonus().equals(st.carried)) {
                bonus = r;
            }
        }
        for (int r = 0; r < 3; r++) {
            int press = bonus == null ? rnd.nextInt(spec.reelLength()) : aimPress(spec, st, r, bonus.pattern().charAt(r), rnd);
            SlotEngine.stop(spec, st, r, press);
        }
    }

    // ================================================================ パチンコ

    static void pachinko(PachinkoSpec spec) {
        System.out.println("== " + spec.id() + " " + spec.name() + "：初当たり1回の出玉とボーダー（等価）");
        int[] settings = spec.settings() ? new int[]{1, 2, 3, 4, 5, 6} : new int[]{1};
        for (int setting : settings) {
            double[] r = firstHit(spec, setting, 12_000);
            // ボーダー：通常の1回転に使う玉（250 ÷ 回転数）× 初当たりまでの回転数 ＝ 初当たり1回の差玉
            double border = 250.0 * spec.normal(setting) / r[0];
            System.out.printf(Locale.ROOT, "  設定%d  初当たり 1/%.1f  初当たり1回の差玉 %.0f 個（払い出し %.0f）  連チャン %.2f 回  ボーダー %.1f 回/1000円%n",
                    setting, spec.normal(setting), r[0], r[1], r[2], border);
        }
    }

    /**
     * 初当たり（ヘソの当たり）から、電サポが終わって通常に戻るまでを打つ（右打ち）。戻り値：差玉の平均・払い出しの平均・大当たりの回数の平均。
     */
    static double[] firstHit(PachinkoSpec spec, int setting, int times) {
        SplittableRandom sr = new SplittableRandom(7L + setting);
        RandomGenerator rnd = sr::nextLong;
        long net = 0;
        long paid = 0;
        long hits = 0;
        for (int t = 0; t < times; t++) {
            PachinkoEngine.State st = new PachinkoEngine.State();
            st.setting = setting;
            st.nail = 18;
            // ヘソの当たりを1つ置く
            PachinkoEngine.Hold hold;
            do {
                hold = drawHeso(spec, st, rnd);
            } while (!hold.hit());
            st.heso.add(hold);
            boolean started = false;
            int guard = 0;
            while (guard++ < 2_000_000) {
                boolean right = st.support() || st.round != null;
                st.shotClock++;
                if (st.shotClock >= PachinkoEngine.SHOT_TICKS && (right || started)) {
                    st.shotClock = 0;
                    if (right) {
                        PachinkoEngine.Shot shot = PachinkoEngine.shoot(spec, st, 70, true, rnd);
                        net += shot.back() - shot.used();
                        if (shot.attacker()) {
                            paid += shot.back();
                        }
                    }
                }
                PachinkoEngine.Tick tk = PachinkoEngine.tick(spec, st, rnd);
                if (tk.jackpotStart()) {
                    hits++;
                    started = true;
                }
                if (started && !st.support() && st.round == null && st.spin == null && st.denchu.isEmpty()) {
                    break;
                }
            }
        }
        return new double[]{(double) net / times, (double) paid / times, (double) hits / times};
    }

    static PachinkoEngine.Hold drawHeso(PachinkoSpec spec, PachinkoEngine.State st, RandomGenerator rnd) {
        // 当たりだけを引くまで、ヘソの抽選をくり返す（ヘソの振り分けを使う）
        double total = 0;
        for (PachinkoSpec.Jackpot j : spec.jackpots()) {
            total += j.heso();
        }
        double x = rnd.nextDouble() * total;
        for (PachinkoSpec.Jackpot j : spec.jackpots()) {
            x -= j.heso();
            if (x < 0) {
                return new PachinkoEngine.Hold(true, j.id(), 0, false);
            }
        }
        return new PachinkoEngine.Hold(true, spec.jackpots().get(0).id(), 0, false);
    }
}
