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
    /** 受け流し：構えている間の、正面からの攻撃を1回だけ無効にして反撃する（値は反撃の、攻撃の何倍か）。 */
    PARRY("parry"),
    /** 誓いの円陣：陣の中にいる間、受けるダメージを減らす（値は%）。 */
    WARD("ward");

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
        throw new DefinitionException("「buff」は rampart・unyielding・fortress・parry・ward のどれかにしてください（今は " + id + "）");
    }
}
