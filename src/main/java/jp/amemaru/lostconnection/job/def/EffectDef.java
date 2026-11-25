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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * スキルの効果の1つ（Phase 0-B 6章の「係数と参照するステータス、効果」）。届け方（{@link DeliveryDef}）で選んだ相手に、
 * 上から順に効く。
 *
 * <table>
 *   <tr><th>type</th><th>効果</th><th>数値</th></tr>
 *   <tr><td>damage</td><td>ダメージ</td><td>stat（attack / magic）, coefficient（ステータスの何倍か）。hits・hit_interval（連撃）、
 *       backstab（背後からの倍率）、pierce（無視する防御の%）、delay（遅れる tick）、bonus_linked（仲間の起点の印がある敵への倍率）</td></tr>
 *   <tr><td>status</td><td>状態（よろめき・凍えなど）</td><td>effect, duration（秒）, level</td></tr>
 *   <tr><td>taunt</td><td>敵の狙いを自分に向ける</td><td>duration</td></tr>
 *   <tr><td>gauge</td><td>専用ゲージをためる</td><td>amount, per_target, max</td></tr>
 *   <tr><td>gauge_empower</td><td>ゲージを使って、この後のダメージを強める</td><td>consume, multiplier</td></tr>
 *   <tr><td>shield_bond</td><td>味方の受けるダメージを肩代わりする</td><td>duration, share（%）</td></tr>
 *   <tr><td>buff</td><td>数値つきの強化（{@link BuffKind}）</td><td>buff, duration, value（%など）。count・burst・threshold・
 *       counters・all_directions・area（自分のまわりの味方にかける半径）・受け流しは refund と on_success</td></tr>
 *   <tr><td>mp_transfer</td><td>自分のMPを味方に分ける</td><td>amount</td></tr>
 *   <tr><td>zone</td><td>地面に陣を残し、中の相手に効果をかけ続ける</td><td>radius, duration, interval, effects。follow（使った人について動く）、
 *       mode（area・cone・nearest）、angle</td></tr>
 *   <tr><td>knockback</td><td>吹き飛ばす</td><td>strength</td></tr>
 *   <tr><td>chill</td><td>凍えを重ねる（5つで凍結）</td><td>stacks, duration, max, freeze, freeze_if</td></tr>
 *   <tr><td>shock</td><td>感電させる</td><td>duration</td></tr>
 *   <tr><td>brand</td><td>星痕を刻む（最大5つ）</td><td>stacks, duration</td></tr>
 *   <tr><td>detonate</td><td>星痕を弾けさせる</td><td>stat, base, per_stack</td></tr>
 *   <tr><td>pull</td><td>手前に引き寄せる</td><td>strength, stop</td></tr>
 *   <tr><td>gauge_burst</td><td>専用ゲージを残らず使い、使った量だけ強める</td><td>per_gauge</td></tr>
 *   <tr><td>barrier</td><td>ダメージを吸う障壁</td><td>stat, coefficient, duration, heal_on_end（消えるときに癒す、障壁の%）</td></tr>
 *   <tr><td>heal</td><td>味方のHPを戻す（Phase 6）</td><td>stat, coefficient</td></tr>
 *   <tr><td>heal_percent</td><td>最大HPの割合だけ戻す</td><td>percent</td></tr>
 *   <tr><td>regen</td><td>毎秒HPを戻し続ける</td><td>stat, coefficient（毎秒）, duration</td></tr>
 *   <tr><td>cleanse</td><td>悪い状態をほどく</td><td>count（0ならすべて）</td></tr>
 *   <tr><td>revive</td><td>ダウンしている味方を起こす</td><td>hp（最大HPの%）</td></tr>
 *   <tr><td>poison</td><td>毒を重ねる（毎秒ダメージ）</td><td>stat, coefficient（毎秒・1つあたり）, duration, stacks, max</td></tr>
 *   <tr><td>detonate_poison</td><td>毒を弾けさせる</td><td>stat, base, per_stack, radius（まわりの敵も）</td></tr>
 *   <tr><td>mark</td><td>標：プレイヤーから受けるダメージが上がる</td><td>value（%）, duration</td></tr>
 *   <tr><td>root</td><td>その場から動けなくする（攻撃はできる）</td><td>duration</td></tr>
 *   <tr><td>weaken</td><td>敵の攻撃を弱める</td><td>value（%）, duration</td></tr>
 *   <tr><td>blind</td><td>敵の攻撃を外れやすくする</td><td>value（外れる%）, duration</td></tr>
 *   <tr><td>stealth</td><td>使った人が、敵に見失われる</td><td>duration</td></tr>
 *   <tr><td>teleport_to</td><td>狙った味方のそばへ移る</td><td>―</td></tr>
 *   <tr><td>throw</td><td>敵を自分の後ろへ投げる（ボスは投げない）</td><td>strength</td></tr>
 *   <tr><td>launch</td><td>敵を打ち上げる</td><td>strength</td></tr>
 *   <tr><td>combo_step</td><td>モンクの連撃を1つ足す</td><td>―</td></tr>
 * </table>
 *
 * <p>{@code "self": true} を付けた buff・status・barrier・heal・regen は、相手でなく使った人にかかる（1回だけ）。</p>
 * <p>陣（zone）の中の効果には、{@code to}（enemies・allies）で、敵と味方のどちらに効くかを書く（回復・強化・障壁は、書かなければ味方）。</p>
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
        BARRIER("barrier"),
        // Phase 6 その2
        HEAL("heal"),
        HEAL_PERCENT("heal_percent"),
        REGEN("regen"),
        CLEANSE("cleanse"),
        REVIVE("revive"),
        POISON("poison"),
        DETONATE_POISON("detonate_poison"),
        MARK("mark"),
        ROOT("root"),
        WEAKEN("weaken"),
        BLIND("blind"),
        STEALTH("stealth"),
        TELEPORT_TO("teleport_to"),
        THROW("throw"),
        LAUNCH("launch"),
        COMBO_STEP("combo_step");

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

        /** 味方にかける効果か（陣の中で、to を書かなければ味方に効く）。 */
        public boolean forAllies() {
            return this == BUFF || this == HEAL || this == HEAL_PERCENT || this == REGEN || this == CLEANSE || this == REVIVE
                    || this == BARRIER || this == SHIELD_BOND || this == MP_TRANSFER;
        }
    }

    /** 陣（zone）の中で使える効果。 */
    private static final Set<Type> ZONE_TYPES = EnumSet.of(Type.DAMAGE, Type.STATUS, Type.KNOCKBACK, Type.CHILL, Type.SHOCK,
            Type.TAUNT, Type.BUFF, Type.HEAL, Type.REGEN, Type.CLEANSE, Type.POISON, Type.MARK, Type.ROOT, Type.WEAKEN, Type.BLIND,
            Type.PULL, Type.LAUNCH, Type.BRAND);
    /** 「self」（使った人にかける）を付けられる効果。 */
    private static final Set<Type> SELF_TYPES = EnumSet.of(Type.BUFF, Type.STATUS, Type.BARRIER, Type.HEAL, Type.HEAL_PERCENT,
            Type.REGEN);

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
    private final boolean self;
    private final boolean follow;
    private final ZoneMode zoneMode;

    /** 陣の効き方。 */
    public enum ZoneMode {
        /** 半径の中の相手すべて。 */
        AREA("area"),
        /** 使った人の前の扇の中だけ（follow と組み合わせる）。 */
        CONE("cone"),
        /** 半径の中で、いちばん近い敵1体（矢嵐）。 */
        NEAREST("nearest");

        private final String id;

        ZoneMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static ZoneMode byId(String id) {
            for (ZoneMode mode : values()) {
                if (mode.id.equals(id)) {
                    return mode;
                }
            }
            throw new DefinitionException("陣の「mode」は area・cone・nearest のどれかにしてください（今は " + id + "）");
        }
    }

    private EffectDef(Type type, @Nullable StatType stat, @Nullable ResourceLocation statusEffect, @Nullable BuffKind buff,
                      Map<String, Ranked> params, List<EffectDef> nested, Affects to, @Nullable ResourceLocation bonusStatus,
                      boolean consumeStatus, boolean self, boolean follow, ZoneMode zoneMode) {
        this.type = type;
        this.stat = stat;
        this.statusEffect = statusEffect;
        this.buff = buff;
        this.params = params;
        this.nested = nested;
        this.to = to;
        this.bonusStatus = bonusStatus;
        this.consumeStatus = consumeStatus;
        this.self = self;
        this.follow = follow;
        this.zoneMode = zoneMode;
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
        if (insideZone && !ZONE_TYPES.contains(type)) {
            throw new DefinitionException("陣（zone）の中で、効果「" + type.id() + "」は使えません");
        }
        Map<String, Ranked> params = new LinkedHashMap<>();
        StatType stat = null;
        ResourceLocation statusEffect = null;
        BuffKind buff = null;
        List<EffectDef> nested = List.of();
        ResourceLocation bonusStatus = null;
        boolean consumeStatus = false;
        boolean self = Json.optionalBoolean(json, "self", false);
        boolean follow = false;
        ZoneMode zoneMode = ZoneMode.AREA;
        if (self && !SELF_TYPES.contains(type)) {
            throw new DefinitionException("「self」は buff・status・barrier・heal・heal_percent・regen にだけ付けられます");
        }
        if (self && insideZone) {
            throw new DefinitionException("陣の中の効果に「self」は付けられません");
        }
        Affects to = Affects.ENEMIES;
        if (insideZone) {
            to = json.has("to") ? Affects.byId(Json.string(json, "to")) : type.forAllies() ? Affects.ALLIES : Affects.ENEMIES;
            if (type.forAllies() && to != Affects.ALLIES) {
                throw new DefinitionException("陣の中の「" + type.id() + "」は、味方（\"to\": \"allies\"）にだけかけられます");
            }
        }
        switch (type) {
            case DAMAGE -> {
                stat = readStat(json);
                put(params, "coefficient", Ranked.read(json, "coefficient", 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "lifesteal", Ranked.readOptional(json, "lifesteal", 0.0, 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "hits", Ranked.readOptional(json, "hits", 1.0, 1.0, 40.0), maxRank, 1.0, 40.0);
                put(params, "hit_interval", Ranked.readOptional(json, "hit_interval", 4.0, 1.0, 40.0), maxRank, 1.0, 40.0);
                put(params, "backstab", Ranked.readOptional(json, "backstab", 1.0, 1.0, 10.0), maxRank, 1.0, 10.0);
                put(params, "pierce", Ranked.readOptional(json, "pierce", 0.0, 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "delay", Ranked.readOptional(json, "delay", 0.0, 0.0, 200.0), maxRank, 0.0, 200.0);
                put(params, "bonus_linked", Ranked.readOptional(json, "bonus_linked", 1.0, 1.0, 10.0), maxRank, 1.0, 10.0);
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
                put(params, "count", Ranked.readOptional(json, "count", 0.0, 0.0, 50.0), maxRank, 0.0, 50.0);
                put(params, "burst", Ranked.readOptional(json, "burst", 0.0, 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "threshold", Ranked.readOptional(json, "threshold", 0.0, 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "area", Ranked.readOptional(json, "area", 0.0, 0.0, 64.0), maxRank, 0.0, 64.0);
                if (buff == BuffKind.PARRY) {
                    if (insideZone) {
                        throw new DefinitionException("受け流し（parry）は、陣の中には置けません");
                    }
                    // 受け流しに成功したとき：攻撃してきた相手への効果（反撃）と、クールダウンの戻り
                    put(params, "refund", Ranked.readOptional(json, "refund", 0.0, 0.0, 1.0), maxRank, 0.0, 1.0);
                    // 反撃できる回数（1なら最初の1回だけ）と、どの向きからの攻撃も受け流すか（流水の構え）
                    put(params, "counters", Ranked.readOptional(json, "counters", 1.0, 1.0, 999.0), maxRank, 1.0, 999.0);
                    put(params, "all_directions", Ranked.readOptional(json, "all_directions", 0.0, 0.0, 1.0), maxRank, 0.0, 1.0);
                    nested = parseList(Json.optionalArray(json, "on_success"), maxRank, false);
                    for (EffectDef inner : nested) {
                        if (inner.type == Type.ZONE || inner.type == Type.BUFF || inner.type == Type.GAUGE_BURST
                                || inner.type == Type.GAUGE_EMPOWER || inner.type == Type.BARRIER || inner.type == Type.SHIELD_BOND
                                || inner.type == Type.MP_TRANSFER || inner.type == Type.HEAL || inner.type == Type.REVIVE
                                || inner.type == Type.TELEPORT_TO) {
                            throw new DefinitionException("受け流しの「on_success」に「" + inner.type.id() + "」は書けません");
                        }
                    }
                }
            }
            case MP_TRANSFER -> put(params, "amount", Ranked.read(json, "amount", 1.0, 10000.0), maxRank, 1.0, 10000.0);
            case ZONE -> {
                put(params, "radius", Ranked.read(json, "radius", 0.5, 32.0), maxRank, 0.5, 32.0);
                put(params, "duration", Ranked.read(json, "duration", 0.05, 120.0), maxRank, 0.05, 120.0);
                put(params, "interval", Ranked.readOptional(json, "interval", 0.5, 0.1, 5.0), maxRank, 0.1, 5.0);
                put(params, "angle", Ranked.readOptional(json, "angle", 60.0, 5.0, 180.0), maxRank, 5.0, 180.0);
                follow = Json.optionalBoolean(json, "follow", false);
                zoneMode = ZoneMode.byId(Json.optionalString(json, "mode", "area"));
                if (zoneMode == ZoneMode.CONE && !follow) {
                    throw new DefinitionException("陣の「mode」が cone のときは、「follow」を true にしてください（向きは使った人の向き）");
                }
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
                put(params, "heal_on_end", Ranked.readOptional(json, "heal_on_end", 0.0, 0.0, 100.0), maxRank, 0.0, 100.0);
            }
            case HEAL -> {
                stat = readStat(json);
                put(params, "coefficient", Ranked.read(json, "coefficient", 0.0, 100.0), maxRank, 0.0, 100.0);
            }
            case HEAL_PERCENT -> put(params, "percent", Ranked.read(json, "percent", 0.0, 100.0), maxRank, 0.0, 100.0);
            case REGEN -> {
                stat = readStat(json);
                put(params, "coefficient", Ranked.read(json, "coefficient", 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "duration", Ranked.read(json, "duration", 0.5, 120.0), maxRank, 0.5, 120.0);
            }
            case CLEANSE -> put(params, "count", Ranked.readOptional(json, "count", 0.0, 0.0, 50.0), maxRank, 0.0, 50.0);
            case REVIVE -> put(params, "hp", Ranked.read(json, "hp", 1.0, 100.0), maxRank, 1.0, 100.0);
            case POISON -> {
                stat = readStat(json);
                put(params, "coefficient", Ranked.read(json, "coefficient", 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "duration", Ranked.readOptional(json, "duration", 6.0, 0.5, 60.0), maxRank, 0.5, 60.0);
                put(params, "stacks", Ranked.readOptional(json, "stacks", 1.0, 1.0, 10.0), maxRank, 1.0, 10.0);
                put(params, "max", Ranked.readOptional(json, "max", 3.0, 1.0, 10.0), maxRank, 1.0, 10.0);
            }
            case DETONATE_POISON -> {
                stat = readStat(json);
                put(params, "base", Ranked.read(json, "base", 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "per_stack", Ranked.read(json, "per_stack", 0.0, 100.0), maxRank, 0.0, 100.0);
                put(params, "radius", Ranked.readOptional(json, "radius", 0.0, 0.0, 16.0), maxRank, 0.0, 16.0);
            }
            case MARK, WEAKEN, BLIND -> {
                put(params, "value", Ranked.read(json, "value", 0.0, 95.0), maxRank, 0.0, 95.0);
                put(params, "duration", Ranked.read(json, "duration", 0.05, 120.0), maxRank, 0.05, 120.0);
            }
            case ROOT, STEALTH -> put(params, "duration", Ranked.read(json, "duration", 0.05, 60.0), maxRank, 0.05, 60.0);
            case THROW -> put(params, "strength", Ranked.readOptional(json, "strength", 1.0, 0.1, 5.0), maxRank, 0.1, 5.0);
            case LAUNCH -> put(params, "strength", Ranked.read(json, "strength", 0.05, 3.0), maxRank, 0.05, 3.0);
            case TELEPORT_TO, COMBO_STEP -> {
            }
        }
        return new EffectDef(type, stat, statusEffect, buff, Collections.unmodifiableMap(params), nested, to, bonusStatus,
                consumeStatus, self, follow, zoneMode);
    }

    private static StatType readStat(JsonObject json) {
        String statId = Json.string(json, "stat");
        return switch (statId) {
            case "attack" -> StatType.ATTACK;
            case "magic" -> StatType.MAGIC;
            case "defense" -> StatType.DEFENSE;
            default -> throw new DefinitionException("「stat」は attack・magic・defense のどれかにしてください（今は " + statId + "）");
        };
    }

    private static void put(Map<String, Ranked> params, String key, Ranked value, int maxRank, double min, double max) {
        value.checkRange(key, maxRank, min, max);
        params.put(key, value);
    }

    public Type type() {
        return type;
    }

    /** ダメージ・回復などの元になるステータス。 */
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

    /** 相手でなく、使った人にかける効果か（1回だけ）。 */
    public boolean self() {
        return self;
    }

    /** 陣が、使った人について動くか。 */
    public boolean follow() {
        return follow;
    }

    /** 陣の効き方。 */
    public ZoneMode zoneMode() {
        return zoneMode;
    }
}
