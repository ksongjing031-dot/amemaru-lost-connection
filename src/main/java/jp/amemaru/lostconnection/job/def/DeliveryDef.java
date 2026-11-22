package jp.amemaru.lostconnection.job.def;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;

/**
 * スキルの「届け方」：誰に・どこに効くか（Phase 0-B 6章の「範囲の形・射程」）。
 *
 * <table>
 *   <tr><th>type</th><th>届け方</th><th>主な数値</th></tr>
 *   <tr><td>self</td><td>自分だけ</td><td>―</td></tr>
 *   <tr><td>around</td><td>自分のまわりの敵（または味方）</td><td>radius, affects, include_self</td></tr>
 *   <tr><td>target</td><td>狙った1体（敵または味方）。味方の技は、狙えなければ自分にもできる</td><td>range, affects, fallback_self</td></tr>
 *   <tr><td>point</td><td>狙った地点のまわり</td><td>range, radius, impact_delay</td></tr>
 *   <tr><td>projectile</td><td>前へ飛ぶ弾。最初に当たった敵。数と広がりを決められる（溜めると増える）</td><td>range, speed, count, count_charged, spread</td></tr>
 *   <tr><td>dash</td><td>前へ踏み込み、通り道で最初に触れた敵</td><td>distance, width</td></tr>
 *   <tr><td>chain</td><td>狙った敵から、近くの敵へ次々に跳ぶ。最初の敵が感電していれば強まる</td><td>range, jumps, jump_range, falloff,
 *       shocked_jumps, shocked_multiplier</td></tr>
 *   <tr><td>blink</td><td>前へ瞬間移動する</td><td>distance</td></tr>
 *   <tr><td>leap</td><td>狙った地点へ跳び、着地したところのまわり</td><td>range, radius</td></tr>
 *   <tr><td>line</td><td>前へまっすぐ貫き、線の上の敵すべて。{@code flat} なら、目の向きでなく地面と平行に（大きな斬撃）</td>
 *       <td>range, width, flat</td></tr>
 *   <tr><td>cone</td><td>前の扇の中の敵すべて</td><td>radius, angle（片側の角度）</td></tr>
 *   <tr><td>branded</td><td>まわりの、星痕のある敵すべて</td><td>range</td></tr>
 *   <tr><td>behind</td><td>狙った敵の背後へ回り込み、その敵（Phase 6：影討ち・縮地）</td><td>range</td></tr>
 *   <tr><td>backstep</td><td>後ろへ跳びながら、前へ弾を撃つ（Phase 6：後跳び）</td><td>distance, range, speed, count, spread, kind</td></tr>
 * </table>
 *
 * <p>Phase 6 その2で足したもの：</p>
 * <ul>
 *   <li>projectile・backstep の {@code kind}：弾の見た目（lamp・arrow・needle・ki・comet）。{@code explode}：当たった所のまわり（半径）の敵すべてに効く。
 *       {@code burst}・{@code burst_interval}：続けて何回撃つか（三連矢）と、その間（tick）。</li>
 *   <li>target の {@code smart}：狙った先に味方がいなければ、まわりでいちばん傷ついた味方（いなければ自分）を選ぶ（ヒーラーの回復）。
 *       {@code downed}：ダウンしている味方だけを狙う（呼び戻し）。</li>
 *   <li>around の {@code include_downed}：ダウンしている味方も含める（大いなる呼び声）。</li>
 * </ul>
 */
public final class DeliveryDef {
    public enum Type {
        SELF("self"),
        AROUND("around"),
        TARGET("target"),
        POINT("point"),
        PROJECTILE("projectile"),
        DASH("dash"),
        CHAIN("chain"),
        BLINK("blink"),
        LEAP("leap"),
        LINE("line"),
        CONE("cone"),
        BRANDED("branded"),
        BEHIND("behind"),
        BACKSTEP("backstep");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Type byId(String id) {
            for (Type type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            throw new DefinitionException("「delivery.type」は self・around・target・point・projectile・dash・chain・blink・leap・line・cone・branded・"
                    + "behind・backstep のどれかにしてください（今は " + id + "）");
        }

        public static Type byIndex(int index) {
            Type[] values = values();
            return index >= 0 && index < values.length ? values[index] : SELF;
        }
    }

    private static final double MAX_DISTANCE = 64.0;

    private final Type type;
    private final Affects affects;
    private final boolean includeSelf;
    private final boolean fallbackSelf;
    private final boolean flat;
    private final Ranked range;
    private final Ranked radius;
    private final Ranked speed;
    private final Ranked distance;
    private final Ranked width;
    private final Ranked jumps;
    private final Ranked jumpRange;
    private final Ranked falloff;
    private final Ranked impactDelay;
    private final Ranked angle;
    private final Ranked count;
    private final Ranked countCharged;
    private final Ranked spread;
    private final Ranked shockedJumps;
    private final Ranked shockedMultiplier;
    private final String kind;
    private final Ranked explode;
    private final Ranked burst;
    private final int burstInterval;
    private final boolean smart;
    private final boolean downed;
    private final boolean includeDowned;

    private DeliveryDef(Builder b) {
        this.type = b.type;
        this.affects = b.affects;
        this.includeSelf = b.includeSelf;
        this.fallbackSelf = b.fallbackSelf;
        this.flat = b.flat;
        this.range = b.range;
        this.radius = b.radius;
        this.speed = b.speed;
        this.distance = b.distance;
        this.width = b.width;
        this.jumps = b.jumps;
        this.jumpRange = b.jumpRange;
        this.falloff = b.falloff;
        this.impactDelay = b.impactDelay;
        this.angle = b.angle;
        this.count = b.count;
        this.countCharged = b.countCharged;
        this.spread = b.spread;
        this.shockedJumps = b.shockedJumps;
        this.shockedMultiplier = b.shockedMultiplier;
        this.kind = b.kind;
        this.explode = b.explode;
        this.burst = b.burst;
        this.burstInterval = b.burstInterval;
        this.smart = b.smart;
        this.downed = b.downed;
        this.includeDowned = b.includeDowned;
    }

    /** 届け方のない定義（パッシブ）。 */
    public static final DeliveryDef NONE = new DeliveryDef(new Builder(Type.SELF, Affects.ALLIES, true));

    public static DeliveryDef parse(JsonObject json, int maxRank) {
        Type type = Type.byId(Json.string(json, "type"));
        Affects defaultAffects = switch (type) {
            case SELF, BLINK -> Affects.ALLIES;
            default -> Affects.ENEMIES;
        };
        Affects affects = json.has("affects") ? Affects.byId(Json.string(json, "affects")) : defaultAffects;
        Builder b = new Builder(type, affects, Json.optionalBoolean(json, "include_self", type == Type.SELF));
        b.fallbackSelf = Json.optionalBoolean(json, "fallback_self", false);
        if (b.fallbackSelf && (type != Type.TARGET || affects != Affects.ALLIES)) {
            throw new DefinitionException("「fallback_self」は、味方を狙う target のときだけ使えます");
        }
        b.flat = Json.optionalBoolean(json, "flat", false);
        if (b.flat && type != Type.LINE) {
            throw new DefinitionException("「flat」は line のときだけ使えます");
        }
        b.smart = Json.optionalBoolean(json, "smart", false);
        b.downed = Json.optionalBoolean(json, "downed", false);
        if ((b.smart || b.downed) && (type != Type.TARGET || affects != Affects.ALLIES)) {
            throw new DefinitionException("「smart」「downed」は、味方を狙う target のときだけ使えます");
        }
        b.includeDowned = Json.optionalBoolean(json, "include_downed", false);
        if (b.includeDowned && (type != Type.AROUND || affects != Affects.ALLIES)) {
            throw new DefinitionException("「include_downed」は、味方に効く around のときだけ使えます");
        }
        switch (type) {
            case SELF -> {
            }
            case AROUND -> b.radius = Ranked.read(json, "radius", 0.5, MAX_DISTANCE);
            case TARGET -> b.range = Ranked.read(json, "range", 1.0, MAX_DISTANCE);
            case POINT -> {
                b.range = Ranked.read(json, "range", 1.0, MAX_DISTANCE);
                b.radius = Ranked.read(json, "radius", 0.5, MAX_DISTANCE);
                b.impactDelay = Ranked.readOptional(json, "impact_delay", 0.0, 0.0, 10.0);
            }
            case PROJECTILE, BACKSTEP -> {
                b.range = Ranked.read(json, "range", 1.0, MAX_DISTANCE);
                b.speed = Ranked.readOptional(json, "speed", 1.5, 0.2, 5.0);
                b.radius = Ranked.readOptional(json, "radius", 0.0, 0.0, 8.0);
                b.count = Ranked.readOptional(json, "count", 1.0, 1.0, 12.0);
                b.countCharged = Ranked.readOptional(json, "count_charged", 0.0, 0.0, 12.0);
                b.spread = Ranked.readOptional(json, "spread", 0.0, 0.0, 90.0);
                b.kind = Json.optionalString(json, "kind", "lamp");
                if (!b.kind.matches("lamp|arrow|needle|ki|comet")) {
                    throw new DefinitionException("弾の「kind」は lamp・arrow・needle・ki・comet のどれかにしてください（今は " + b.kind + "）");
                }
                b.explode = Ranked.readOptional(json, "explode", 0.0, 0.0, 12.0);
                b.burst = Ranked.readOptional(json, "burst", 1.0, 1.0, 8.0);
                b.burstInterval = Json.optionalInt(json, "burst_interval", 4, 1, 40);
                if (type == Type.BACKSTEP) {
                    b.distance = Ranked.read(json, "distance", 0.5, 16.0);
                }
            }
            case BEHIND -> b.range = Ranked.read(json, "range", 1.0, 32.0);
            case DASH -> {
                b.distance = Ranked.read(json, "distance", 0.5, 16.0);
                b.width = Ranked.readOptional(json, "width", 1.2, 0.2, 6.0);
            }
            case CHAIN -> {
                b.range = Ranked.read(json, "range", 1.0, MAX_DISTANCE);
                b.jumps = Ranked.readOptional(json, "jumps", 0.0, 0.0, 16.0);
                b.jumpRange = Ranked.readOptional(json, "jump_range", 6.0, 1.0, 32.0);
                b.falloff = Ranked.readOptional(json, "falloff", 0.8, 0.05, 1.5);
                b.shockedJumps = Ranked.readOptional(json, "shocked_jumps", 0.0, 0.0, 16.0);
                b.shockedMultiplier = Ranked.readOptional(json, "shocked_multiplier", 1.0, 1.0, 10.0);
            }
            case BLINK -> b.distance = Ranked.read(json, "distance", 0.5, 32.0);
            case LEAP -> {
                b.range = Ranked.read(json, "range", 1.0, 24.0);
                b.radius = Ranked.read(json, "radius", 0.5, 16.0);
            }
            case LINE -> {
                b.range = Ranked.read(json, "range", 1.0, MAX_DISTANCE);
                b.width = Ranked.readOptional(json, "width", 1.2, 0.2, 6.0);
            }
            case CONE -> {
                b.radius = Ranked.read(json, "radius", 0.5, 16.0);
                b.angle = Ranked.readOptional(json, "angle", 60.0, 5.0, 180.0);
            }
            case BRANDED -> b.range = Ranked.read(json, "range", 1.0, MAX_DISTANCE);
        }
        DeliveryDef def = new DeliveryDef(b);
        def.range.checkRange("delivery.range", maxRank, 0.0, MAX_DISTANCE);
        def.radius.checkRange("delivery.radius", maxRank, 0.0, MAX_DISTANCE);
        def.distance.checkRange("delivery.distance", maxRank, 0.0, 32.0);
        def.jumps.checkRange("delivery.jumps", maxRank, 0.0, 16.0);
        def.count.checkRange("delivery.count", maxRank, 0.0, 12.0);
        def.countCharged.checkRange("delivery.count_charged", maxRank, 0.0, 12.0);
        def.angle.checkRange("delivery.angle", maxRank, 0.0, 180.0);
        def.explode.checkRange("delivery.explode", maxRank, 0.0, 12.0);
        def.burst.checkRange("delivery.burst", maxRank, 1.0, 8.0);
        return def;
    }

    public Type type() {
        return type;
    }

    public Affects affects() {
        return affects;
    }

    /** 味方に効くとき、自分も含めるか。 */
    public boolean includeSelf() {
        return includeSelf;
    }

    /** 味方を狙う技で、狙える味方がいなければ自分にかけるか。 */
    public boolean fallbackSelf() {
        return fallbackSelf;
    }

    /** line：目の向き（上下）でなく、地面と平行に前へのばすか（下を向いて使っても、すぐ地面で止まらない）。 */
    public boolean flat() {
        return flat;
    }

    public double range(int rank) {
        return range.at(rank);
    }

    public double radius(int rank) {
        return radius.at(rank);
    }

    public double speed(int rank) {
        return speed.at(rank);
    }

    public double distance(int rank) {
        return distance.at(rank);
    }

    public double width(int rank) {
        return width.at(rank);
    }

    public int jumps(int rank) {
        return Math.max(0, jumps.intAt(rank));
    }

    public double jumpRange(int rank) {
        return jumpRange.at(rank);
    }

    public double falloff(int rank) {
        return falloff.at(rank);
    }

    /** 地点に効くまでの遅れ（秒）。 */
    public double impactDelay(int rank) {
        return impactDelay.at(rank);
    }

    /** 扇の片側の角度（度）。 */
    public double angle(int rank) {
        return angle.at(rank);
    }

    /** 弾の数（溜めない、または溜めが0のとき）。 */
    public int count(int rank) {
        return Math.max(1, count.intAt(rank));
    }

    /** 溜めきったときの弾の数（0なら溜めても増えない）。 */
    public int countCharged(int rank) {
        return Math.max(0, countCharged.intAt(rank));
    }

    /** 弾を扇に広げる角度（度。全体の幅）。 */
    public double spread(int rank) {
        return spread.at(rank);
    }

    /** 雷鎖：最初の敵が感電していたときに増える、跳ぶ回数。 */
    public int shockedJumps(int rank) {
        return Math.max(0, shockedJumps.intAt(rank));
    }

    /** 雷鎖：最初の敵が感電していたときの威力の倍率。 */
    public double shockedMultiplier(int rank) {
        return Math.max(1.0, shockedMultiplier.at(rank));
    }

    /** 弾の見た目（lamp・arrow・needle・ki・comet）。 */
    public String kind() {
        return kind;
    }

    /** 当たった所で弾ける半径（0なら弾けない）。 */
    public double explode(int rank) {
        return explode.at(rank);
    }

    /** 続けて撃つ回数（1なら1回）。 */
    public int burst(int rank) {
        return Math.max(1, burst.intAt(rank));
    }

    /** 続けて撃つときの間（tick）。 */
    public int burstInterval() {
        return burstInterval;
    }

    /** 狙った先に味方がいなければ、いちばん傷ついた味方を選ぶか。 */
    public boolean smart() {
        return smart;
    }

    /** ダウンしている味方だけを狙うか（呼び戻し）。 */
    public boolean downed() {
        return downed;
    }

    /** まわりの味方に、ダウンしている人も含めるか（大いなる呼び声）。 */
    public boolean includeDowned() {
        return includeDowned;
    }

    /** クライアントが「狙った相手」を送る届け方か。 */
    public boolean aimsAtEntity() {
        return type == Type.TARGET || type == Type.CHAIN || type == Type.BEHIND;
    }

    /** クライアントが「狙った地点」を送る届け方か。 */
    public boolean aimsAtPoint() {
        return type == Type.POINT || type == Type.LEAP;
    }

    /** 狙うときの最大の距離（狙わない届け方では0）。 */
    public double aimRange(int rank) {
        return switch (type) {
            case TARGET, POINT, CHAIN, PROJECTILE, LEAP, LINE, BRANDED, BEHIND, BACKSTEP -> range.at(rank);
            case DASH, BLINK -> distance.at(rank);
            case CONE -> radius.at(rank);
            default -> 0.0;
        };
    }

    private static final class Builder {
        final Type type;
        final Affects affects;
        final boolean includeSelf;
        boolean fallbackSelf;
        boolean flat;
        Ranked range = Ranked.ZERO;
        Ranked radius = Ranked.ZERO;
        Ranked speed = Ranked.ZERO;
        Ranked distance = Ranked.ZERO;
        Ranked width = Ranked.ZERO;
        Ranked jumps = Ranked.ZERO;
        Ranked jumpRange = Ranked.ZERO;
        Ranked falloff = Ranked.of(1.0);
        Ranked impactDelay = Ranked.ZERO;
        Ranked angle = Ranked.ZERO;
        Ranked count = Ranked.of(1.0);
        Ranked countCharged = Ranked.ZERO;
        Ranked spread = Ranked.ZERO;
        Ranked shockedJumps = Ranked.ZERO;
        Ranked shockedMultiplier = Ranked.of(1.0);
        String kind = "lamp";
        Ranked explode = Ranked.ZERO;
        Ranked burst = Ranked.of(1.0);
        int burstInterval = 4;
        boolean smart;
        boolean downed;
        boolean includeDowned;

        Builder(Type type, Affects affects, boolean includeSelf) {
            this.type = type;
            this.affects = affects;
            this.includeSelf = includeSelf;
        }
    }
}
