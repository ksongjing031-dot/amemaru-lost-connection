package jp.amemaru.lostconnection.job.def;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * スキルの効果の1つ（Phase 0-B 6章の「係数と参照するステータス、効果」）。届け方（{@link DeliveryDef}）で選んだ相手に、
 * 上から順に効く。
 *
 * <table>
 *   <tr><th>type</th><th>効果</th><th>数値</th></tr>
 *   <tr><td>damage</td><td>ダメージ</td><td>stat（attack / magic）, coefficient（ステータスの何倍か）</td></tr>
 *   <tr><td>status</td><td>状態（よろめき・凍えなど）</td><td>effect, duration（秒）, level</td></tr>
 *   <tr><td>taunt</td><td>敵の狙いを自分に向ける</td><td>duration</td></tr>
 *   <tr><td>gauge</td><td>専用ゲージをためる</td><td>amount, per_target, max</td></tr>
 *   <tr><td>gauge_empower</td><td>ゲージを使って、この後のダメージを強める</td><td>consume, multiplier</td></tr>
 *   <tr><td>shield_bond</td><td>味方の受けるダメージを肩代わりする</td><td>duration, share（%）</td></tr>
 *   <tr><td>buff</td><td>数値つきの強化（城壁の構え・不屈の誓い・不落の城）</td><td>buff, duration, value（%）</td></tr>
 *   <tr><td>mp_transfer</td><td>自分のMPを味方に分ける</td><td>amount</td></tr>
 *   <tr><td>zone</td><td>地面に陣を残し、中の敵に効果をかけ続ける</td><td>radius, duration, interval, effects</td></tr>
 *   <tr><td>knockback</td><td>吹き飛ばす</td><td>strength</td></tr>
 * </table>
 */
public final class EffectDef {
    public enum Type {
        DAMAGE("damage"),
        STATUS("status"),
        TAUNT("taunt"),
        GAUGE("gauge"),
        GAUGE_EMPOWER("gauge_empower"),
        SHIELD_BOND("shield_bond"),
        BUFF("buff"),
        MP_TRANSFER("mp_transfer"),
        ZONE("zone"),
        KNOCKBACK("knockback");

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
            throw new DefinitionException("効果の「type」に、ない種類「" + id + "」があります");
        }
    }

    private final Type type;
    @Nullable
    private final StatType stat;
    @Nullable
    private final ResourceLocation statusEffect;
    @Nullable
    private final BuffKind buff;
    private final Map<String, Ranked> params;
    private final List<EffectDef> nested;

    private EffectDef(Type type, @Nullable StatType stat, @Nullable ResourceLocation statusEffect, @Nullable BuffKind buff,
                      Map<String, Ranked> params, List<EffectDef> nested) {
        this.type = type;
        this.stat = stat;
        this.statusEffect = statusEffect;
        this.buff = buff;
        this.params = params;
        this.nested = nested;
    }

    public static List<EffectDef> parseList(JsonArray array, int maxRank, boolean insideZone) {
        List<EffectDef> list = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String where = "effects[" + i + "]";
            try {
                list.add(parse(Json.asObject(array.get(i), where), maxRank, insideZone));
            } catch (DefinitionException e) {
                throw new DefinitionException(where + "：" + e.getMessage());
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static EffectDef parse(JsonObject json, int maxRank, boolean insideZone) {
        Type type = Type.byId(Json.string(json, "type"));
        if (insideZone && type != Type.DAMAGE && type != Type.STATUS && type != Type.KNOCKBACK) {
            throw new DefinitionException("陣（zone）の中で使える効果は damage・status・knockback だけです");
        }
        Map<String, Ranked> params = new LinkedHashMap<>();
        StatType stat = null;
        ResourceLocation statusEffect = null;
        BuffKind buff = null;
        List<EffectDef> nested = List.of();
        switch (type) {
            case DAMAGE -> {
                String statId = Json.string(json, "stat");
                if (!statId.equals("attack") && !statId.equals("magic")) {
                    throw new DefinitionException("「stat」は attack か magic にしてください（今は " + statId + "）");
                }
                stat = statId.equals("attack") ? StatType.ATTACK : StatType.MAGIC;
                put(params, "coefficient", Ranked.read(json, "coefficient", 0.0, 100.0), maxRank, 0.0, 100.0);
            }
            case STATUS -> {
                statusEffect = Json.id(json, "effect");
                put(params, "duration", Ranked.read(json, "duration", 0.05, 600.0), maxRank, 0.05, 600.0);
                put(params, "level", Ranked.readOptional(json, "level", 1.0, 1.0, 10.0), maxRank, 1.0, 10.0);
            }
            case TAUNT -> put(params, "duration", Ranked.read(json, "duration", 0.05, 120.0), maxRank, 0.05, 120.0);
            case GAUGE -> {
                put(params, "amount", Ranked.readOptional(json, "amount", 0.0, -1000.0, 1000.0), maxRank, -1000.0, 1000.0);
                put(params, "per_target", Ranked.readOptional(json, "per_target", 0.0, 0.0, 1000.0), maxRank, 0.0, 1000.0);
                put(params, "max", Ranked.readOptional(json, "max", 0.0, 0.0, 1000.0), maxRank, 0.0, 1000.0);
            }
            case GAUGE_EMPOWER -> {
                put(params, "consume", Ranked.read(json, "consume", 1.0, 1000.0), maxRank, 1.0, 1000.0);
                put(params, "multiplier", Ranked.read(json, "multiplier", 1.0, 10.0), maxRank, 1.0, 10.0);
            }
            case SHIELD_BOND -> {
                put(params, "duration", Ranked.read(json, "duration", 0.05, 600.0), maxRank, 0.05, 600.0);
                put(params, "share", Ranked.read(json, "share", 1.0, 90.0), maxRank, 1.0, 90.0);
            }
            case BUFF -> {
                buff = BuffKind.byId(Json.string(json, "buff"));
                put(params, "duration", Ranked.read(json, "duration", 0.05, 600.0), maxRank, 0.05, 600.0);
                put(params, "value", Ranked.readOptional(json, "value", 0.0, 0.0, 95.0), maxRank, 0.0, 95.0);
            }
            case MP_TRANSFER -> put(params, "amount", Ranked.read(json, "amount", 1.0, 10000.0), maxRank, 1.0, 10000.0);
            case ZONE -> {
                put(params, "radius", Ranked.read(json, "radius", 0.5, 32.0), maxRank, 0.5, 32.0);
                put(params, "duration", Ranked.read(json, "duration", 0.05, 120.0), maxRank, 0.05, 120.0);
                put(params, "interval", Ranked.readOptional(json, "interval", 0.5, 0.25, 5.0), maxRank, 0.25, 5.0);
                JsonArray inner = Json.array(json, "effects");
                if (inner.isEmpty()) {
                    throw new DefinitionException("陣（zone）の「effects」が空です");
                }
                nested = parseList(inner, maxRank, true);
            }
            case KNOCKBACK -> put(params, "strength", Ranked.read(json, "strength", 0.0, 5.0), maxRank, 0.0, 5.0);
        }
        return new EffectDef(type, stat, statusEffect, buff, Collections.unmodifiableMap(params), nested);
    }

    private static void put(Map<String, Ranked> params, String key, Ranked value, int maxRank, double min, double max) {
        value.checkRange(key, maxRank, min, max);
        params.put(key, value);
    }

    public Type type() {
        return type;
    }

    /** ダメージの元になるステータス（damage のときだけ）。 */
    @Nullable
    public StatType stat() {
        return stat;
    }

    /** かける状態のID（status のときだけ）。 */
    @Nullable
    public ResourceLocation statusEffect() {
        return statusEffect;
    }

    /** 強化の種類（buff のときだけ）。 */
    @Nullable
    public BuffKind buff() {
        return buff;
    }

    /** 数値（その段階の値）。定義にない名前は0。 */
    public double value(String key, int rank) {
        Ranked ranked = params.get(key);
        return ranked == null ? 0.0 : ranked.at(rank);
    }

    /** 陣（zone）の中の効果。 */
    public List<EffectDef> nested() {
        return nested;
    }
}
