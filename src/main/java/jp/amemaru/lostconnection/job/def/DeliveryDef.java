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
 *   <tr><td>target</td><td>狙った1体（敵または味方）</td><td>range, affects</td></tr>
 *   <tr><td>point</td><td>狙った地点のまわり</td><td>range, radius, impact_delay</td></tr>
 *   <tr><td>projectile</td><td>前へ飛ぶ弾。最初に当たった敵</td><td>range, speed</td></tr>
 *   <tr><td>dash</td><td>前へ踏み込み、通り道で最初に触れた敵</td><td>distance, width</td></tr>
 *   <tr><td>chain</td><td>狙った敵から、近くの敵へ次々に跳ぶ</td><td>range, jumps, jump_range, falloff</td></tr>
 *   <tr><td>blink</td><td>前へ瞬間移動する</td><td>distance</td></tr>
 * </table>
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
        BLINK("blink");

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
            throw new DefinitionException("「delivery.type」は self・around・target・point・projectile・dash・chain・blink のどれかにしてください（今は " + id + "）");
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
    private final Ranked range;
    private final Ranked radius;
    private final Ranked speed;
    private final Ranked distance;
    private final Ranked width;
    private final Ranked jumps;
    private final Ranked jumpRange;
    private final Ranked falloff;
    private final Ranked impactDelay;

    private DeliveryDef(Type type, Affects affects, boolean includeSelf, Ranked range, Ranked radius, Ranked speed,
                        Ranked distance, Ranked width, Ranked jumps, Ranked jumpRange, Ranked falloff, Ranked impactDelay) {
        this.type = type;
        this.affects = affects;
        this.includeSelf = includeSelf;
        this.range = range;
        this.radius = radius;
        this.speed = speed;
        this.distance = distance;
        this.width = width;
        this.jumps = jumps;
        this.jumpRange = jumpRange;
        this.falloff = falloff;
        this.impactDelay = impactDelay;
    }

    /** 届け方のない定義（パッシブ）。 */
    public static final DeliveryDef NONE = new DeliveryDef(Type.SELF, Affects.ALLIES, true, Ranked.ZERO, Ranked.ZERO,
            Ranked.ZERO, Ranked.ZERO, Ranked.ZERO, Ranked.ZERO, Ranked.ZERO, Ranked.ZERO, Ranked.ZERO);

    public static DeliveryDef parse(JsonObject json, int maxRank) {
        Type type = Type.byId(Json.string(json, "type"));
        Affects defaultAffects = switch (type) {
            case SELF, BLINK -> Affects.ALLIES;
            default -> Affects.ENEMIES;
        };
        Affects affects = json.has("affects") ? Affects.byId(Json.string(json, "affects")) : defaultAffects;
        boolean includeSelf = Json.optionalBoolean(json, "include_self", type == Type.SELF);

        Ranked range = Ranked.ZERO;
        Ranked radius = Ranked.ZERO;
        Ranked speed = Ranked.ZERO;
        Ranked distance = Ranked.ZERO;
        Ranked width = Ranked.ZERO;
        Ranked jumps = Ranked.ZERO;
        Ranked jumpRange = Ranked.ZERO;
        Ranked falloff = Ranked.of(1.0);
        Ranked impactDelay = Ranked.ZERO;
        switch (type) {
            case SELF -> {
            }
            case AROUND -> radius = Ranked.read(json, "radius", 0.5, MAX_DISTANCE);
            case TARGET -> range = Ranked.read(json, "range", 1.0, MAX_DISTANCE);
            case POINT -> {
                range = Ranked.read(json, "range", 1.0, MAX_DISTANCE);
                radius = Ranked.read(json, "radius", 0.5, MAX_DISTANCE);
                impactDelay = Ranked.readOptional(json, "impact_delay", 0.0, 0.0, 10.0);
            }
            case PROJECTILE -> {
                range = Ranked.read(json, "range", 1.0, MAX_DISTANCE);
                speed = Ranked.readOptional(json, "speed", 1.5, 0.2, 5.0);
                radius = Ranked.readOptional(json, "radius", 0.0, 0.0, 8.0);
            }
            case DASH -> {
                distance = Ranked.read(json, "distance", 0.5, 16.0);
                width = Ranked.readOptional(json, "width", 1.2, 0.2, 6.0);
            }
            case CHAIN -> {
                range = Ranked.read(json, "range", 1.0, MAX_DISTANCE);
                jumps = Ranked.readOptional(json, "jumps", 0.0, 0.0, 16.0);
                jumpRange = Ranked.readOptional(json, "jump_range", 6.0, 1.0, 32.0);
                falloff = Ranked.readOptional(json, "falloff", 0.8, 0.05, 1.5);
            }
            case BLINK -> distance = Ranked.read(json, "distance", 0.5, 32.0);
        }
        DeliveryDef def = new DeliveryDef(type, affects, includeSelf, range, radius, speed, distance, width, jumps,
                jumpRange, falloff, impactDelay);
        def.range.checkRange("delivery.range", maxRank, 0.0, MAX_DISTANCE);
        def.radius.checkRange("delivery.radius", maxRank, 0.0, MAX_DISTANCE);
        def.distance.checkRange("delivery.distance", maxRank, 0.0, 32.0);
        def.jumps.checkRange("delivery.jumps", maxRank, 0.0, 16.0);
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

    /** クライアントが「狙った相手」を送る届け方か。 */
    public boolean aimsAtEntity() {
        return type == Type.TARGET || type == Type.CHAIN;
    }

    /** クライアントが「狙った地点」を送る届け方か。 */
    public boolean aimsAtPoint() {
        return type == Type.POINT;
    }

    /** 狙うときの最大の距離（狙わない届け方では0）。 */
    public double aimRange(int rank) {
        return switch (type) {
            case TARGET, POINT, CHAIN, PROJECTILE -> range.at(rank);
            case DASH, BLINK -> distance.at(rank);
            default -> 0.0;
        };
    }
}
