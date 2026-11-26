package jp.amemaru.lostconnection.casino.parlor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * パチンコの台の機種（{@code casino_machines/<機種>.json} の {@code "kind": "pachinko"}。11月27日のご指示「パチンコとスロットは本物ように
 * 設定を作る」【補完】）。
 *
 * <ul>
 *   <li>{@code normal}：通常の大当たり確率 1/x（設定付きの台は設定1〜6の6つ）。{@code high}：確変・ST の大当たり確率 1/x</li>
 *   <li>{@code jackpots}：大当たりの種類（ラウンド数・あとの状態：確変（次の当たりまで）・ST（決まった回数）・時短・通常）と、ヘソ・電チュー
 *       （・LT の電チュー）での振り分け（重み）</li>
 *   <li>{@code count}・{@code ball_pay}：1ラウンドに入る玉の数と、1個あたりの払い出し（10カウント×15個で150個）</li>
 *   <li>{@code heso_pay}・{@code denchu_pay}：ヘソ・電チューに入ったときの払い出し。{@code denchu}：電サポ中に右打ちした1個が電チューに入る
 *       見込み（電チューの払い出しが1個なら、これがそのまま「ベース」：0.95 なら100個打って95個戻る）</li>
 *   <li>{@code nails}：釘（1,000円＝250個あたりの回転数）。ふつうの日とイベントの日（7のつく日）の、狭い・広いの幅</li>
 * </ul>
 */
public final class PachinkoSpec {
    public enum Next { KAKUHEN, ST, JITAN, NORMAL }

    public record Jackpot(String id, String name, int rounds, Next next, int spins, boolean lt, double heso, double denchu,
                          double denchuLt) {
        /** 確変・ST に入る当たり（図柄は奇数）。 */
        public boolean kakuhen() {
            return next == Next.KAKUHEN || next == Next.ST;
        }
    }

    private final String id;
    private final String name;
    private final String specName;
    private final double[] normal;
    private final double high;
    private final boolean settings;
    private final int count;
    private final int ballPay;
    private final int hesoPay;
    private final int denchuPay;
    private final double denchu;
    private final double[] nailsNormal;
    private final double[] nailsEvent;
    private final List<Jackpot> jackpots;
    private final double border;

    private PachinkoSpec(String id, String name, String specName, double[] normal, double high, boolean settings, int count,
                         int ballPay, int hesoPay, int denchuPay, double denchu, double[] nailsNormal,
                         double[] nailsEvent, List<Jackpot> jackpots, double border) {
        this.id = id;
        this.name = name;
        this.specName = specName;
        this.normal = normal;
        this.high = high;
        this.settings = settings;
        this.count = count;
        this.ballPay = ballPay;
        this.hesoPay = hesoPay;
        this.denchuPay = denchuPay;
        this.denchu = denchu;
        this.nailsNormal = nailsNormal;
        this.nailsEvent = nailsEvent;
        this.jackpots = jackpots;
        this.border = border;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** スペックの名前（ミドル・甘デジ・ハイスペック）。 */
    public String specName() {
        return specName;
    }

    /** 通常の大当たり確率の分母（設定 1〜6）。 */
    public double normal(int setting) {
        return normal[Math.max(1, Math.min(6, setting)) - 1];
    }

    public double high() {
        return high;
    }

    /** 設定付きの台か（設定で通常の大当たり確率が変わる）。 */
    public boolean settings() {
        return settings;
    }

    public int count() {
        return count;
    }

    public int ballPay() {
        return ballPay;
    }

    public int hesoPay() {
        return hesoPay;
    }

    public int denchuPay() {
        return denchuPay;
    }

    public double denchu() {
        return denchu;
    }

    /** 釘の幅（1,000円あたりの回転数の下と上）。event はイベントの日。 */
    public double[] nails(boolean event) {
        return event ? nailsEvent : nailsNormal;
    }

    public List<Jackpot> jackpots() {
        return jackpots;
    }

    public Jackpot jackpot(String jackpotId) {
        for (Jackpot j : jackpots) {
            if (j.id().equals(jackpotId)) {
                return j;
            }
        }
        return jackpots.get(0);
    }

    /** 資料に書くボーダー（等価で、1,000円あたり何回転で釣り合うか。tools/balance/ParlorSim.java で数える）。 */
    public double border() {
        return border;
    }

    public static PachinkoSpec parse(String id, JsonObject json) {
        String name = ParlorJson.string(json, "name");
        String specName = ParlorJson.optString(json, "spec", "");
        double[] normal = ParlorJson.perSetting(json, "normal", 10, 100000);
        double high = ParlorJson.number(json, "high", 2, 10000);
        boolean settings = ParlorJson.optBool(json, "settings", false);
        int count = ParlorJson.optInt(json, "count", 10, 1, 30);
        int ballPay = ParlorJson.optInt(json, "ball_pay", 15, 1, 30);
        int hesoPay = ParlorJson.optInt(json, "heso_pay", 3, 0, 15);
        int denchuPay = ParlorJson.optInt(json, "denchu_pay", 1, 0, 15);
        double denchu = ParlorJson.optNumber(json, "denchu", 0.95, 0.01, 1);
        JsonObject nails = ParlorJson.object(json, "nails");
        double[] nn = range(nails, "normal");
        double[] ne = range(nails, "event");
        List<Jackpot> jackpots = new ArrayList<>();
        for (JsonElement e : ParlorJson.array(json, "jackpots")) {
            JsonObject j = e.getAsJsonObject();
            String jid = ParlorJson.string(j, "id");
            String nextName = ParlorJson.string(j, "next");
            Next next = switch (nextName) {
                case "kakuhen" -> Next.KAKUHEN;
                case "st" -> Next.ST;
                case "jitan" -> Next.JITAN;
                case "normal" -> Next.NORMAL;
                default -> throw new DefinitionException("大当たり「" + jid + "」の「next」は kakuhen・st・jitan・normal です");
            };
            double denchuWeight = ParlorJson.optNumber(j, "denchu", 0, 0, 1000);
            jackpots.add(new Jackpot(jid, ParlorJson.string(j, "name"), ParlorJson.optInt(j, "rounds", 10, 1, 30), next,
                    ParlorJson.optInt(j, "spins", 0, 0, 10000), ParlorJson.optBool(j, "lt", false),
                    ParlorJson.optNumber(j, "heso", 0, 0, 1000), denchuWeight,
                    ParlorJson.optNumber(j, "denchu_lt", denchuWeight, 0, 1000)));
        }
        if (jackpots.isEmpty()) {
            throw new DefinitionException("「jackpots」がありません");
        }
        if (jackpots.stream().mapToDouble(Jackpot::heso).sum() <= 0 || jackpots.stream().mapToDouble(Jackpot::denchu).sum() <= 0) {
            throw new DefinitionException("大当たりの振り分け（heso・denchu）の重みの合計が0です");
        }
        double border = ParlorJson.optNumber(json, "border", 0, 0, 100);
        return new PachinkoSpec(id, name, specName, normal, high, settings, count, ballPay, hesoPay, denchuPay, denchu,
                nn, ne, Collections.unmodifiableList(jackpots), border);
    }

    private static double[] range(JsonObject nails, String key) {
        var a = ParlorJson.array(nails, key);
        if (a.size() != 2) {
            throw new DefinitionException("「nails." + key + "」は [狭い, 広い] の2つです");
        }
        double lo = a.get(0).getAsDouble();
        double hi = a.get(1).getAsDouble();
        if (lo < 5 || hi > 60 || lo > hi) {
            throw new DefinitionException("「nails." + key + "」は 5〜60 で、狭い ≦ 広い です");
        }
        return new double[]{lo, hi};
    }
}
