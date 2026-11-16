package jp.amemaru.lostconnection.job.def;

import jp.amemaru.lostconnection.core.data.DefinitionException;

/** スキルでかかる、数値つきの強化の種類（効果の数値はサーバーのプレイヤーデータに持つ）。 */
public enum BuffKind {
    /** 城壁の構え：正面からのダメージを減らす（値は%）。 */
    RAMPART("rampart"),
    /** 不屈の誓い：HPが1より下がらない。 */
    UNYIELDING("unyielding"),
    /** 不落の城：受けるダメージを減らす（値は%）。 */
    FORTRESS("fortress");

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
        throw new DefinitionException("「buff」は rampart・unyielding・fortress のどれかにしてください（今は " + id + "）");
    }
}
