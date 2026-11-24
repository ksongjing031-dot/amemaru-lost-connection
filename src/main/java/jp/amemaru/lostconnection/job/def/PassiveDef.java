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
 *   <tr><td>taunt_mastery</td><td>自分が挑発した敵の攻撃が弱まる</td><td>弱まる割合（%）</td></tr>
 *   <tr><td>breaker</td><td>よろめき・防御崩しの敵へのダメージが上がる</td><td>上がる割合（%）</td></tr>
 *   <tr><td>fighting_spirit</td><td>HPが半分を切ると攻撃が上がる</td><td>攻撃の上がり幅（%）</td></tr>
 *   <tr><td>star_afterglow</td><td>星痕を弾けさせると、星痕1つにつきMPが戻る</td><td>戻るMP</td></tr>
 *   <tr><td>frost_heart</td><td>凍え・凍結の敵へのダメージが上がる</td><td>上がる割合（%）</td></tr>
 *   <tr><td>deep_chill</td><td>凍えが長く続き、凍結も長くなる</td><td>凍結が長くなる秒数（凍えは1.5倍）</td></tr>
 * </table>
 *
 * <p>Phase 6 その2（11月22日のご指示「スキルとパッシブを増やす」）で足した種類は、{@link Type} の説明のとおり。
 * 「この流派の」と書いたものは、そのパッシブがある流派（スキルの school）のスキルに効く。</p>
 */
public final class PassiveDef {
    public enum Type {
        GUARDIAN_PRIDE("guardian_pride", true),
        IRON_WALL("iron_wall", false),
        OATH_CYCLE("oath_cycle", false),
        SWIFT_CHANT("swift_chant", false),
        MANA_RESONANCE("mana_resonance", true),
        MANA_SPRING("mana_spring", false),
        TAUNT_MASTERY("taunt_mastery", false),
        BREAKER("breaker", false),
        FIGHTING_SPIRIT("fighting_spirit", false),
        STAR_AFTERGLOW("star_afterglow", false),
        FROST_HEART("frost_heart", false),
        DEEP_CHILL("deep_chill", false),
        // ---------------------------------------------------------------- ヒーラー
        /** 慈愛：回復量 +value%。 */
        HEAL_POWER("heal_power", false),
        /** 余韻：癒した量の value% を、5秒かけてさらに癒す。 */
        AFTERCARE("aftercare", false),
        /** つながりの癒し：まわり（radius）のパーティの仲間1人につき回復量 +value%（4人まで）。 */
        BOND_HEAL("bond_heal", true),
        /** 溢れる癒し：満タンを超えた分の value% を障壁にする。 */
        OVERHEAL_BARRIER("overheal_barrier", false),
        /** 満ちる心：ほかの仲間を癒した量の value% のMPが戻る。 */
        HEAL_MP("heal_mp", false),
        /** 鈴の守り：まわり（radius）の仲間の受けるダメージ -value%。 */
        BELL_GUARD("bell_guard", true),
        /** 清めの心得：ほどく技を受けた仲間は、4秒受けるダメージ -value%。 */
        CLEANSE_GUARD("cleanse_guard", false),
        /** 戒めの響き：スキルでダメージを与えた敵の攻撃を、4秒 -value%。 */
        WEAKEN_ECHO("weaken_echo", false),
        /** 揺るがぬ祈り：詠唱と溜めの間、受けるダメージ -value%。 */
        STEADY_PRAYER("steady_prayer", false),
        /** 鈴の余韻：スキルで与えたダメージの value% で、まわり（radius）のいちばん傷ついた仲間を癒す。 */
        BELL_ECHO("bell_echo", true),
        // ---------------------------------------------------------------- アーチャー
        /** 鷹の目：会心 +value。 */
        HAWK_EYE("hawk_eye", false),
        /** 静止集中：立ち止まっている間の集中のたまり方 +value%。 */
        STILL_FOCUS("still_focus", false),
        /** 遠矢：6ブロックより遠い敵へ、1ブロックにつき +1%（最大 value%）。 */
        LONG_SHOT("long_shot", false),
        /** 弱点看破：標・縫い止めの敵への会心ダメージ +value%。 */
        WEAK_POINT("weak_point", false),
        /** 集中の極意：スキルで使った集中の value% が戻る。 */
        FOCUS_REFUND("focus_refund", false),
        /** 身軽な射手：弓を持っていると、移動 +value%。 */
        NIMBLE_ARCHER("nimble_archer", false),
        /** 速射の心得など：この流派のスキルのクールダウン -value%。 */
        SCHOOL_HASTE("school_haste", false),
        /** 連なる矢：2秒以内に矢を当て続けると、1本ごとに +value%（10本まで）。 */
        ARROW_CHAIN("arrow_chain", false),
        /** 風読み：矢のスキルのダメージ +value%、矢が速く遠くまで飛ぶ。 */
        WIND_READING("wind_reading", false),
        /** 矢筒の名手：この流派のスキルを使うと、value% の確率でクールダウンが半分。 */
        QUIVER_MASTER("quiver_master", false),
        // ---------------------------------------------------------------- ローグ
        /** 背後の心得：背後から当てたダメージ +value%。 */
        BACKSTAB_MASTERY("backstab_mastery", false),
        /** 身軽：回避 +value%。 */
        NIMBLE("nimble", false),
        /** 気配消し：3秒攻撃しないと敵に狙われにくく、次の一撃 +value%。 */
        FADE("fade", false),
        /** 影の歩法：移動の技の後3秒、次の一撃の会心 +value。 */
        SHADOW_STEP("shadow_step", false),
        /** 闇の刃：会心ダメージ +value%。 */
        DARK_EDGE("dark_edge", false),
        /** 毒の心得：毒のダメージ +value%。 */
        TOXIN_POWER("toxin_power", false),
        /** 蝕み：毒の敵が受けるダメージ +value%（誰からでも）。 */
        CORRODE("corrode", false),
        /** しみる毒：毒の重なる上限 +value。 */
        DEEP_TOXIN("deep_toxin", false),
        /** 弱り目：毒の敵への会心 +value。 */
        EXPLOIT("exploit", false),
        /** 毒の連鎖：毒の敵が倒れると、まわり（radius）の敵に毒を value 重ねる。 */
        CONTAGION("contagion", true),
        // ---------------------------------------------------------------- モンク
        /** 連撃の心得：連撃1つごとのダメージ +value%（足す分）。 */
        CHAIN_MASTERY("chain_mastery", false),
        /** 鋼の肉体：防御 +value%。 */
        IRON_BODY("iron_body", false),
        /** 闘気：気が50以上の間、攻撃 +value%。 */
        BATTLE_AURA("battle_aura", false),
        /** 気の巡り：【極】で連撃を使うと、連撃1つにつき最大HPの value% が戻る。 */
        FLOWING_KI("flowing_ki", false),
        /** 極めの一撃：【極】のダメージ +value%、連撃5なら会心。 */
        FINISHING_BLOW("finishing_blow", false),
        /** 仲間の拳：まわり（radius）に近接の仲間がいると、素早さ +value%。 */
        ALLY_FIST("ally_fist", true),
        /** 受けの心得：反撃のダメージ +value%、反撃すると気 +10。 */
        COUNTER_MASTERY("counter_mastery", false),
        /** 柔の理：回避 +value%、かわすと気 +8。 */
        SOFT_WAY("soft_way", false),
        /** 静の呼吸：戦いの間、気が50以上なら毎秒、最大HPの value% が戻る。 */
        CALM_BREATH("calm_breath", false),
        /** 流れを読む：受け流し・回避の後3秒、次のスキルは同じ技でも連撃をつなぎ、+value%。 */
        READ_FLOW("read_flow", false),
        // ---------------------------------------------------------------- ナイト・ウィザード（足した段）
        /** 揺るがぬ盾：城壁の構え・受け流し・誓いの円陣の間、受けるダメージがさらに -value%。 */
        STEADFAST_SHIELD("steadfast_shield", false),
        /** 守り手の心：まわり（radius）の仲間のHPが3割を切ると、誓気 +20、5秒受けるダメージ -value%（20秒に1回）。 */
        GUARDIAN_HEART("guardian_heart", true),
        /** 剛剣の極意など：この流派のスキルのダメージ +value%。 */
        SCHOOL_POWER("school_power", false),
        /** 連ね斬り：3秒以内に、この流派の違うスキルを続けて当てると、1つごとに +value%（3つまで）。 */
        BLADE_CHAIN("blade_chain", false),
        /** 星の導き：星痕のある敵が受けるダメージ、星痕1つにつき +value%。 */
        STAR_GUIDE("star_guide", false),
        /** 満天：星痕を弾けさせると、星痕1つにつき、この流派のクールダウンが value 秒縮む。 */
        STARRY_SKY("starry_sky", false),
        /** 雷の心得：感電した敵へのダメージ +value%、感電 +2秒。 */
        THUNDER_MASTERY("thunder_mastery", false),
        /** 凍土：凍結を砕くと、まわりの敵に魔力×value のダメージと凍え。 */
        PERMAFROST("permafrost", false),
        // ---------------------------------------------------------------- 近接職の守り（11月25日）
        /**
         * 不屈の再起（ナイト）：HPが3割を切ると、すぐに最大HPの value% が戻り、5秒かけてさらに value% 戻る（90秒に1回）。
         * 11月25日のご指示「剣士は後半のパッシブで体力が少なくなると体力が急激に回復するなどを追加する」。
         */
        LAST_STAND("last_stand", false),
        /** 影吸い（ローグ）：与えたダメージの value% だけHPが戻る（1回で最大HPの3%まで）。 */
        LIFE_DRAIN("life_drain", false),
        /** 金剛身（モンク）：受けるダメージ -value%。気が50以上の間は、さらに -value%。 */
        ADAMANT_BODY("adamant_body", false);

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
