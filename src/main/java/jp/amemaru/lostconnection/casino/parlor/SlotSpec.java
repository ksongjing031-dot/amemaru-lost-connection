package jp.amemaru.lostconnection.casino.parlor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * スロット（パチスロ）の台の機種（{@code casino_machines/<機種>.json} の {@code "kind": "slot"}。11月27日のご指示「パチンコとスロットは
 * 本物ように設定を作る」【補完】）。
 *
 * <ul>
 *   <li>{@code type}：{@code a}（Aタイプ：ボーナスの印を持ち越し、告知ランプで知らせる）・{@code at}（AT：押し順のナビで小役を取り、
 *       チャンスゾーンと上乗せと天井）</li>
 *   <li>{@code reels}：左・中・右のリールの図柄の並び（上から下へ。同じ長さ）。{@code lines}：有効ライン（リールごとの段。0 上・1 中・2 下）</li>
 *   <li>{@code roles}：役。毎ゲーム、レバーを叩いたときに1つ引く（内部抽選）。確率は 1/x の x を、数1つか設定1〜6の6つで書く</li>
 *   <li>{@code bonuses}：BIG・REG（Aタイプ）。払い出しの合計が {@code cap} を超えるまで、{@code bet} 枚掛けで {@code pay} 枚の役がそろう</li>
 * </ul>
 */
public final class SlotSpec {
    public enum Type { A, AT }

    /** 役の形。LINE：3つの図柄が有効ラインにそろう。CHERRY：左のリールの決まった段に図柄が出る。ORDER：押し順で取れる（AT）。BONUS：ボーナス */
    public enum RoleKind { LINE, CHERRY, ORDER, BONUS }

    public record Role(String id, RoleKind kind, String pattern, int[] rows, int pay, int missPay, boolean replay, String bonus,
                       double[] denominators, boolean rare) {
        /** 設定（1〜6）での1ゲームあたりの確率。 */
        public double chance(int setting) {
            double d = denominators[Math.max(1, Math.min(6, setting)) - 1];
            return d <= 0 ? 0.0 : 1.0 / d;
        }

        public boolean isBonus() {
            return kind == RoleKind.BONUS;
        }
    }

    public record Bonus(String id, int cap, int bet, int pay, String role) {
    }

    public record Lamp(String name, double pre, double premium) {
    }

    /** AT の決まり（%は0〜100。設定ごと）。 */
    public record At(int ceiling, int initial, int czGames, Map<String, double[]> cz, Map<String, double[]> direct,
                     Map<String, double[]> czWin, Map<String, double[]> addChance, Map<String, Integer> addGames) {
        public double pct(Map<String, double[]> table, String role, int setting) {
            double[] v = table.containsKey(role) ? table.get(role) : table.get("other");
            return v == null ? 0.0 : v[Math.max(1, Math.min(6, setting)) - 1] / 100.0;
        }

        public int games(String role) {
            Integer g = addGames.containsKey(role) ? addGames.get(role) : addGames.get("other");
            return g == null ? 0 : g;
        }
    }

    public record Symbol(char code, String name, int color) {
    }

    private final String id;
    private final String name;
    private final Type type;
    private final int bet;
    private final int waitMs;
    private final int rpm;
    private final List<Symbol> symbols;
    private final String[] reels;
    private final int[][] lines;
    private final List<Role> roles;
    private final Map<String, Bonus> bonuses;
    private final Lamp lamp;
    private final At at;
    private final double[] targetPayout;
    /** 形の組（同じ図柄の並びの役は同じ組。チェリーは役ごと）：役の番号 → 組・組 → 図柄・チェリーの組の段 */
    private final int[] roleGroup;
    private final char[][] groupPattern;
    private final boolean[] groupCherry;
    private final int[][] groupRows;

    private SlotSpec(String id, String name, Type type, int bet, int waitMs, int rpm, List<Symbol> symbols, String[] reels,
                     int[][] lines, List<Role> roles, Map<String, Bonus> bonuses, Lamp lamp, At at, double[] targetPayout) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.bet = bet;
        this.waitMs = waitMs;
        this.rpm = rpm;
        this.symbols = symbols;
        this.reels = reels;
        this.lines = lines;
        this.roles = roles;
        this.bonuses = bonuses;
        this.lamp = lamp;
        this.at = at;
        this.targetPayout = targetPayout;
        Map<String, Integer> keys = new LinkedHashMap<>();
        this.roleGroup = new int[roles.size()];
        for (int i = 0; i < roles.size(); i++) {
            Role r = roles.get(i);
            String key = r.kind() == RoleKind.CHERRY ? "C:" + r.id() : "L:" + r.pattern();
            Integer g = keys.get(key);
            if (g == null) {
                g = keys.size();
                keys.put(key, g);
            }
            roleGroup[i] = g;
        }
        if (keys.size() > 30) {
            throw new DefinitionException("役の形が多すぎます（30まで）");
        }
        this.groupPattern = new char[keys.size()][];
        this.groupCherry = new boolean[keys.size()];
        this.groupRows = new int[keys.size()][];
        for (int i = 0; i < roles.size(); i++) {
            Role r = roles.get(i);
            int g = roleGroup[i];
            groupPattern[g] = r.pattern().toCharArray();
            groupCherry[g] = r.kind() == RoleKind.CHERRY;
            groupRows[g] = r.rows();
        }
    }

    /** 形の組の数。 */
    public int groups() {
        return groupPattern.length;
    }

    /** 役の形の組（同じ図柄の並びの役は同じ組）。 */
    public int groupOf(Role role) {
        int i = roles.indexOf(role);
        return i < 0 ? -1 : roleGroup[i];
    }

    public char[] groupPattern(int group) {
        return groupPattern[group];
    }

    public boolean groupCherry(int group) {
        return groupCherry[group];
    }

    public int[] groupRows(int group) {
        return groupRows[group];
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Type type() {
        return type;
    }

    public int bet() {
        return bet;
    }

    /** 1ゲームの最短の時間（ウェイト）。 */
    public int waitMs() {
        return waitMs;
    }

    /** リールの回る速さ（1分の回転数）。 */
    public int rpm() {
        return rpm;
    }

    public List<Symbol> symbols() {
        return symbols;
    }

    public String reel(int r) {
        return reels[r];
    }

    public int reelLength() {
        return reels[0].length();
    }

    public int[][] lines() {
        return lines;
    }

    public List<Role> roles() {
        return roles;
    }

    public Role role(String roleId) {
        for (Role r : roles) {
            if (r.id().equals(roleId)) {
                return r;
            }
        }
        return null;
    }

    public Bonus bonus(String bonusId) {
        return bonuses.get(bonusId);
    }

    public Map<String, Bonus> bonuses() {
        return bonuses;
    }

    public Lamp lamp() {
        return lamp;
    }

    public At at() {
        return at;
    }

    /** 機種の資料に書く機械割の目安（%。設定1〜6）。数は tools/balance/ParlorSim.java で確かめる。 */
    public double[] targetPayout() {
        return targetPayout;
    }

    public static SlotSpec parse(String id, JsonObject json) {
        String name = ParlorJson.string(json, "name");
        String typeName = ParlorJson.optString(json, "type", "a");
        Type type = switch (typeName) {
            case "a" -> Type.A;
            case "at" -> Type.AT;
            default -> throw new DefinitionException("「type」は a か at です（今は " + typeName + "）");
        };
        int bet = ParlorJson.optInt(json, "bet", 3, 1, 3);
        int waitMs = ParlorJson.optInt(json, "wait_ms", 3200, 500, 10000);
        int rpm = ParlorJson.optInt(json, "rpm", 60, 20, 120);
        List<Symbol> symbols = new ArrayList<>();
        for (JsonElement e : ParlorJson.array(json, "symbols")) {
            JsonObject s = e.getAsJsonObject();
            String code = ParlorJson.string(s, "id");
            if (code.length() != 1) {
                throw new DefinitionException("図柄の「id」は1文字です（今は " + code + "）");
            }
            symbols.add(new Symbol(code.charAt(0), ParlorJson.string(s, "name"),
                    Integer.parseInt(ParlorJson.optString(s, "color", "#FFFFFF").substring(1), 16)));
        }
        JsonArray reelJson = ParlorJson.array(json, "reels");
        if (reelJson.size() != 3) {
            throw new DefinitionException("「reels」は左・中・右の3つです");
        }
        String[] reels = new String[3];
        for (int r = 0; r < 3; r++) {
            reels[r] = reelJson.get(r).getAsString();
            if (reels[r].length() != reels[0].length() || reels[r].length() < 10) {
                throw new DefinitionException("リールの図柄の数は、3つとも同じで10以上です");
            }
            for (char c : reels[r].toCharArray()) {
                if (symbols.stream().noneMatch(s -> s.code() == c)) {
                    throw new DefinitionException("リール" + (r + 1) + "の図柄「" + c + "」が「symbols」にありません");
                }
            }
        }
        JsonArray lineJson = ParlorJson.array(json, "lines");
        int[][] lines = new int[lineJson.size()][3];
        for (int i = 0; i < lineJson.size(); i++) {
            JsonArray l = lineJson.get(i).getAsJsonArray();
            for (int r = 0; r < 3; r++) {
                lines[i][r] = l.get(r).getAsInt();
                if (lines[i][r] < 0 || lines[i][r] > 2) {
                    throw new DefinitionException("有効ラインの段は 0（上）・1（中）・2（下）です");
                }
            }
        }
        List<Role> roles = new ArrayList<>();
        for (JsonElement e : ParlorJson.array(json, "roles")) {
            JsonObject r = e.getAsJsonObject();
            String rid = ParlorJson.string(r, "id");
            String kindName = ParlorJson.optString(r, "type", "line");
            RoleKind kind = switch (kindName) {
                case "line" -> RoleKind.LINE;
                case "cherry" -> RoleKind.CHERRY;
                case "order" -> RoleKind.ORDER;
                case "bonus" -> RoleKind.BONUS;
                default -> throw new DefinitionException("役「" + rid + "」の「type」は line・cherry・order・bonus です");
            };
            String pattern = ParlorJson.string(r, "pattern");
            if (kind == RoleKind.CHERRY ? pattern.length() != 1 : pattern.length() != 3) {
                throw new DefinitionException("役「" + rid + "」の「pattern」の長さがちがいます（チェリーは1文字、ほかは3文字）");
            }
            int[] rows = {0, 1, 2};
            JsonArray rowJson = ParlorJson.optArray(r, "rows");
            if (rowJson != null) {
                rows = new int[rowJson.size()];
                for (int i = 0; i < rows.length; i++) {
                    rows[i] = rowJson.get(i).getAsInt();
                }
            }
            roles.add(new Role(rid, kind, pattern, rows, ParlorJson.optInt(r, "pay", 0, 0, 15),
                    ParlorJson.optInt(r, "miss_pay", 0, 0, 15), ParlorJson.optBool(r, "replay", false),
                    ParlorJson.optString(r, "bonus", ""), ParlorJson.perSetting(r, "p", 0, 1.0E7),
                    ParlorJson.optBool(r, "rare", false)));
        }
        double total = 0;
        for (Role r : roles) {
            total += r.chance(1);
        }
        if (total >= 1.0) {
            throw new DefinitionException("役の確率の合計が1以上です（はずれが残りません）");
        }
        Map<String, Bonus> bonuses = new LinkedHashMap<>();
        JsonObject bonusJson = ParlorJson.optObject(json, "bonuses");
        if (bonusJson != null) {
            for (Map.Entry<String, JsonElement> e : bonusJson.entrySet()) {
                JsonObject b = e.getValue().getAsJsonObject();
                bonuses.put(e.getKey(), new Bonus(e.getKey(), ParlorJson.optInt(b, "cap", 252, 1, 3000),
                        ParlorJson.optInt(b, "bet", 2, 1, 3), ParlorJson.optInt(b, "pay", 15, 1, 15), ParlorJson.string(b, "role")));
            }
        }
        for (Role r : roles) {
            if (r.isBonus() && !bonuses.containsKey(r.bonus())) {
                throw new DefinitionException("役「" + r.id() + "」のボーナス「" + r.bonus() + "」が「bonuses」にありません");
            }
        }
        JsonObject lampJson = ParlorJson.optObject(json, "lamp");
        Lamp lamp = lampJson == null ? new Lamp("", 0.0, 0.0) : new Lamp(ParlorJson.optString(lampJson, "name", ""),
                ParlorJson.optNumber(lampJson, "pre", 0.25, 0, 1), ParlorJson.optNumber(lampJson, "premium", 0.05, 0, 1));
        At at = null;
        JsonObject atJson = ParlorJson.optObject(json, "at");
        if (type == Type.AT) {
            if (atJson == null) {
                throw new DefinitionException("AT の台には「at」が要ります");
            }
            Map<String, Integer> addGames = new LinkedHashMap<>();
            Map<String, double[]> addChance = new LinkedHashMap<>();
            JsonObject add = ParlorJson.object(atJson, "add");
            for (Map.Entry<String, JsonElement> e : add.entrySet()) {
                JsonObject a = e.getValue().getAsJsonObject();
                addChance.put(e.getKey(), ParlorJson.perSetting(a, "pct", 0, 100));
                addGames.put(e.getKey(), ParlorJson.optInt(a, "games", 10, 1, 1000));
            }
            at = new At(ParlorJson.optInt(atJson, "ceiling", 800, 50, 5000), ParlorJson.optInt(atJson, "initial", 50, 1, 1000),
                    ParlorJson.optInt(atJson, "cz_games", 10, 1, 100), table(atJson, "cz"), table(atJson, "direct"),
                    table(atJson, "cz_win"), addChance, addGames);
        }
        double[] target = ParlorJson.optPerSetting(json, "payout", 0, 0, 200);
        return new SlotSpec(id, name, type, bet, waitMs, rpm, Collections.unmodifiableList(symbols), reels, lines,
                Collections.unmodifiableList(roles), Collections.unmodifiableMap(bonuses), lamp, at, target);
    }

    private static Map<String, double[]> table(JsonObject parent, String key) {
        Map<String, double[]> out = new LinkedHashMap<>();
        JsonObject t = ParlorJson.optObject(parent, key);
        if (t != null) {
            for (Map.Entry<String, JsonElement> e : t.entrySet()) {
                out.put(e.getKey(), ParlorJson.perSetting(e.getValue(), key + "." + e.getKey(), 0, 100));
            }
        }
        return out;
    }
}
