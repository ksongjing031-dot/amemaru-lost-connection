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
 *   <tr><td>buff</td><td>数値つきの強化（城壁の構え・不屈の誓い・不落の城・受け流し・誓いの円陣）</td><td>buff, duration, value（%）。
 *       受け流しは refund（成功したときにクールダウンの残りが減る割合）と on_success（反撃の効果）</td></tr>
 *   <tr><td>mp_transfer</td><td>自分のMPを味方に分ける</td><td>amount</td></tr>
 *   <tr><td>zone</td><td>地面に陣を残し、中の敵に効果をかけ続ける</td><td>radius, duration, interval, effects</td></tr>
 *   <tr><td>knockback</td><td>吹き飛ばす</td><td>strength</td></tr>
 *   <tr><td>chill</td><td>凍えを重ねる（5つで凍結）</td><td>stacks, duration, max, freeze（凍結の秒数）, freeze_if（この数以上なら、すぐ凍結）</td></tr>
 *   <tr><td>shock</td><td>感電させる（次の雷鎖がよく跳ぶ）</td><td>duration</td></tr>
 *   <tr><td>brand</td><td>星痕を刻む（最大5つ）</td><td>stacks, duration</td></tr>
 *   <tr><td>detonate</td><td>星痕を弾けさせる（星痕が多いほど強い）</td><td>stat, base, per_stack</td></tr>
 *   <tr><td>pull</td><td>手前に引き寄せる</td><td>strength, stop（止まる距離）</td></tr>
 *   <tr><td>gauge_burst</td><td>専用ゲージを残らず使い、使った量だけ強める</td><td>per_gauge（1あたりの上がり幅）</td></tr>
 *   <tr><td>barrier</td><td>ダメージを吸う障壁</td><td>stat, coefficient, duration</td></tr>
 * </table>
 *
 * <p>damage には、与えたダメージの何%を回復するか（{@code lifesteal}）と、決まった状態の相手へのダメージの倍率
 * （{@code bonus_status}・{@code bonus}・{@code consume_status}：例 凍結の敵に1.8倍で、凍結を砕く）を付けられる。</p>
 * <p>陣（zone）の中の効果には、{@code to}（enemies・allies）で、敵と味方のどちらに効くかを書く。</p>
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
        KNOCKBACK("knockback"),
        CHILL("chill"),
        SHOCK("shock"),
        BRAND("brand"),
        DETONATE("detonate"),
        PULL("pull"),
        GAUGE_BURST("gauge_burst"),
        BARRIER("barrier");

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
    private final Affects to;
    @Nullable
    private final ResourceLocation bonusStatus;
    private final boolean consumeStatus;

    private EffectDef(Type type, @Nullable StatType stat, @Nullable ResourceLocation statusEffect, @Nullable BuffKind buff,
                      Map<String, Ranked> params, List<EffectDef> nested, Affects to, @Nullable ResourceLocation bonusStatus,
                      boolean consumeStatus) {
        this.type = type;
        this.stat = stat;
        this.statusEffect = statusEffect;
        this.buff = buff;
        this.params = params;
        this.nested = nested;
        this.to = to;
        this.bonusStatus = bonusStatus;
        this.consumeStatus = consumeStatus;
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
        if (insideZone && type != Type.DAMAGE && type != Type.STATUS && type != Type.KNOCKBACK && type != Type.CHILL
                && type != Type.SHOCK && type != Type.TAUNT && type != Type.BUFF) {
            throw new DefinitionException("陣（zone）の中で使える効果は damage・status・knockback・chill・shock・taunt・buff だけです");
        }
        Map<String, Ranked> params = new LinkedHashMap<>();
        StatType stat = null;
        ResourceLocation statusEffect = null;
        BuffKind buff = null;
        List<EffectDef> nested = List.of();
        ResourceLocation bonusStatus = null;
        boolean consumeStatus = false;
        Affects to = Affects.ENEMIES;
        if (insideZone) {
            to = json.has("to") ? Affects.byId(Json.string(json, "to")) : type == Type.BUFF ? Affects.ALLIES : Affects.ENEMIES;
            if (type == Type.BUFF && to != Affects.ALLIES) {
                throw new DefinitionException("陣の中の buff は、味方（\"to\": \"allies\"）にだけかけられます");
            }
        }
        switch (type) {
            case DAMAGE -> {
                stat = readStat(json);
                put(params, "coefficient", Ranked.read(json, "coefficient", 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "lifesteal", Ranked.readOptional(json, "lifesteal", 0.0, 0.0, 100.0), maxRank, 0.0, 100.0);
                if (json.has("bonus_status")) {
                    bonusStatus = Json.id(json, "bonus_status");
                    put(params, "bonus", Ranked.read(json, "bonus", 1.0, 10.0), maxRank, 1.0, 10.0);
                    consumeStatus = Json.optionalBoolean(json, "consume_status", false);
                }
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
                if (buff == BuffKind.PARRY) {
                    if (insideZone) {
                        throw new DefinitionException("受け流し（parry）は、陣の中には置けません");
                    }
                    // 受け流しに成功したとき：攻撃してきた相手への効果（反撃）と、クールダウンの戻り
                    put(params, "refund", Ranked.readOptional(json, "refund", 0.0, 0.0, 1.0), maxRank, 0.0, 1.0);
                    nested = parseList(Json.optionalArray(json, "on_success"), maxRank, false);
                    for (EffectDef inner : nested) {
                        if (inner.type == Type.ZONE || inner.type == Type.BUFF || inner.type == Type.GAUGE_BURST
                                || inner.type == Type.GAUGE_EMPOWER || inner.type == Type.BARRIER || inner.type == Type.SHIELD_BOND
                                || inner.type == Type.MP_TRANSFER) {
                            throw new DefinitionException("受け流しの「on_success」に「" + inner.type.id() + "」は書けません");
                        }
                    }
                }
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
            case CHILL -> {
                put(params, "stacks", Ranked.readOptional(json, "stacks", 1.0, 1.0, 5.0), maxRank, 1.0, 5.0);
                put(params, "duration", Ranked.readOptional(json, "duration", 4.0, 0.25, 60.0), maxRank, 0.25, 60.0);
                put(params, "max", Ranked.readOptional(json, "max", 5.0, 1.0, 5.0), maxRank, 1.0, 5.0);
                put(params, "freeze", Ranked.readOptional(json, "freeze", 2.0, 0.0, 10.0), maxRank, 0.0, 10.0);
                put(params, "freeze_if", Ranked.readOptional(json, "freeze_if", 0.0, 0.0, 5.0), maxRank, 0.0, 5.0);
            }
            case SHOCK -> put(params, "duration", Ranked.readOptional(json, "duration", 6.0, 0.25, 60.0), maxRank, 0.25, 60.0);
            case BRAND -> {
                put(params, "stacks", Ranked.readOptional(json, "stacks", 1.0, 1.0, 5.0), maxRank, 1.0, 5.0);
                put(params, "duration", Ranked.readOptional(json, "duration", 8.0, 0.25, 60.0), maxRank, 0.25, 60.0);
            }
            case DETONATE -> {
                stat = readStat(json);
                put(params, "base", Ranked.read(json, "base", 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "per_stack", Ranked.read(json, "per_stack", 0.0, 100.0), maxRank, 0.0, 100.0);
            }
            case PULL -> {
                put(params, "strength", Ranked.readOptional(json, "strength", 1.0, 0.1, 5.0), maxRank, 0.1, 5.0);
                put(params, "stop", Ranked.readOptional(json, "stop", 2.0, 0.5, 8.0), maxRank, 0.5, 8.0);
            }
            case GAUGE_BURST -> put(params, "per_gauge", Ranked.read(json, "per_gauge", 0.0, 1.0), maxRank, 0.0, 1.0);
            case BARRIER -> {
                stat = readStat(json);
                put(params, "coefficient", Ranked.read(json, "coefficient", 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "duration", Ranked.read(json, "duration", 0.05, 600.0), maxRank, 0.05, 600.0);
            }
        }
        return new EffectDef(type, stat, statusEffect, buff, Collections.unmodifiableMap(params), nested, to, bonusStatus,
                consumeStatus);
    }

    private static StatType readStat(JsonObject json) {
        String statId = Json.string(json, "stat");
        if (!statId.equals("attack") && !statId.equals("magic")) {
            throw new DefinitionException("「stat」は attack か magic にしてください（今は " + statId + "）");
        }
        return statId.equals("attack") ? StatType.ATTACK : StatType.MAGIC;
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

    /** 陣（zone）の中の効果。受け流し（buff の parry）では、成功したときの反撃の効果。 */
    public List<EffectDef> nested() {
        return nested;
    }

    /** 陣の中の効果が、敵と味方のどちらに効くか（陣の外の効果では ENEMIES）。 */
    public Affects to() {
        return to;
    }

    /** この状態の相手へのダメージに {@code bonus} をかける（damage のときだけ。なければ null）。 */
    @Nullable
    public ResourceLocation bonusStatus() {
        return bonusStatus;
    }

    /** {@link #bonusStatus()} の状態を、当てたときに消すか（凍結を砕く、など）。 */
    public boolean consumeStatus() {
        return consumeStatus;
    }
}
