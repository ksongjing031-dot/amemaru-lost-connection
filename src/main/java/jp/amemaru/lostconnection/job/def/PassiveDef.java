package jp.amemaru.lostconnection.job.def;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;

/**
 * パッシブスキルの効き方。働きはコードで決まっていて、強さ（value）と範囲（radius）をJSONで調整する。
 *
 * <table>
 *   <tr><th>type</th><th>働き</th><th>value の意味</th></tr>
 *   <tr><td>guardian_pride</td><td>近くに味方がいると防御が上がる</td><td>防御の上がり幅（%）</td></tr>
 *   <tr><td>iron_wall</td><td>盾を持つと受けるダメージが減る</td><td>減る割合（%）</td></tr>
 *   <tr><td>oath_cycle</td><td>専用ゲージを使うと、使った量に応じてMPが戻る</td><td>戻る割合（%）</td></tr>
 *   <tr><td>swift_chant</td><td>詠唱が短くなる</td><td>短くなる割合（%）</td></tr>
 *   <tr><td>mana_resonance</td><td>近くに魔法を使う味方がいると魔力が上がる</td><td>魔力の上がり幅（%）</td></tr>
 *   <tr><td>mana_spring</td><td>MPの自然回復が増える</td><td>増える割合（%）</td></tr>
 * </table>
 */
public final class PassiveDef {
    public enum Type {
        GUARDIAN_PRIDE("guardian_pride", true),
        IRON_WALL("iron_wall", false),
        OATH_CYCLE("oath_cycle", false),
        SWIFT_CHANT("swift_chant", false),
        MANA_RESONANCE("mana_resonance", true),
        MANA_SPRING("mana_spring", false);

        private final String id;
        private final boolean usesRadius;

        Type(String id, boolean usesRadius) {
            this.id = id;
            this.usesRadius = usesRadius;
        }

        public String id() {
            return id;
        }

        public boolean usesRadius() {
            return usesRadius;
        }

        public static Type byId(String id) {
            for (Type type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            throw new DefinitionException("「passive.type」に、ない種類「" + id + "」があります");
        }
    }

    private final Type type;
    private final Ranked value;
    private final Ranked radius;

    private PassiveDef(Type type, Ranked value, Ranked radius) {
        this.type = type;
        this.value = value;
        this.radius = radius;
    }

    public static PassiveDef parse(JsonObject json, int maxRank) {
        Type type = Type.byId(Json.string(json, "type"));
        Ranked value = Ranked.read(json, "value", 0.0, 95.0);
        value.checkRange("passive.value", maxRank, 0.0, 95.0);
        Ranked radius = type.usesRadius() ? Ranked.read(json, "radius", 1.0, 64.0) : Ranked.ZERO;
        radius.checkRange("passive.radius", maxRank, 0.0, 64.0);
        return new PassiveDef(type, value, radius);
    }

    public Type type() {
        return type;
    }

    public double value(int rank) {
        return value.at(rank);
    }

    public double radius(int rank) {
        return radius.at(rank);
    }
}
