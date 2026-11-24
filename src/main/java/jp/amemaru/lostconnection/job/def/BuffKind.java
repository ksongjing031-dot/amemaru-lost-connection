package jp.amemaru.lostconnection.job.def;

import jp.amemaru.lostconnection.core.data.DefinitionException;

/** スキルでかかる、数値つきの強化の種類（効果の数値はサーバーのプレイヤーデータに持つ）。 */
public enum BuffKind {
    /** 城壁の構え：正面からのダメージを減らす（値は%）。 */
    RAMPART("rampart"),
    /** 不屈の誓い：HPが1より下がらない。 */
    UNYIELDING("unyielding"),
    /** 不落の城：受けるダメージを減らす（値は%）。 */
    FORTRESS("fortress"),
    /** 受け流し：構えている間の攻撃を無効にして反撃する（流水の構えは、どの向きからも・何度でも）。 */
    PARRY("parry"),
    /** 誓いの円陣：陣の中にいる間、受けるダメージを減らす（値は%）。 */
    WARD("ward"),
    // ---------------------------------------------------------------- Phase 6 その2
    /** 加護の灯：防御が上がる（値は%）。 */
    DEFENSE_UP("defense_up"),
    /** 守り（結びの聖域・清めの心得）：受けるダメージを減らす（値は%）。 */
    PROTECT("protect"),
    /** 鷹の眼差し・気功：会心が上がる（値は会心の数）。 */
    CRIT_UP("crit_up"),
    /** 足が速くなる（値は%）。 */
    HASTE("haste"),
    /** 次に使うスキルのダメージが上がる（値は%。百烈・結）。 */
    EMPOWER_NEXT("empower_next"),
    /** 狙撃の構え：次の矢（count 本）のダメージが上がる（値は%）。足が遅くなる。 */
    AIM("aim"),
    /** 命の糸：毎秒、魔力×値だけ癒し、HPが threshold% を切ると一度だけ魔力×burst で癒す。 */
    LIFE_THREAD("life_thread"),
    /** 身代わりの鈴：倒れるほどの攻撃を一度だけ防ぎ、HPを値（%）まで戻す。 */
    GUARDIAN("guardian"),
    /** 星の環：count 個の星が巡り、近づいた敵に魔力×値のダメージ。 */
    STAR_RING("star_ring"),
    /** 影分身：スキルが当たるたびに、ダメージの値（%）でもう一度斬る。 */
    SHADOW_CLONE("shadow_clone"),
    /** 毒塗り：攻撃が当たるたびに毒が重なる（値は毒の係数）。 */
    VENOM("venom"),
    /** 疾風の弓舞：足・矢のダメージ・クールダウンの戻りが上がる（値は%）。 */
    BOW_DANCE("bow_dance"),
    /** 影走り：敵の攻撃をすり抜ける。 */
    EVADE("evade"),
    /** 天地流転：【極】の技を使っても連撃が消えない。 */
    ENDLESS_FLOW("endless_flow"),
    // ---------------------------------------------------------------- 11月24日（スキルツリーの作り直し）
    /** 気炎・魔力集中・鬨の声など：しばらく与えるダメージが上がる（値は%。いくつ重なっても大きい方）。 */
    POWER_UP("power_up");

    private final String id;

    BuffKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static BuffKind byId(String id) {
        for (BuffKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        throw new DefinitionException("「buff」に、ない種類「" + id + "」があります");
    }
}
