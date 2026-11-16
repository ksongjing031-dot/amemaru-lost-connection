package jp.amemaru.lostconnection.job.def;

import jp.amemaru.lostconnection.core.data.DefinitionException;

/** スキルが効く相手。 */
public enum Affects {
    /** 敵（魔物・訓練人形）。 */
    ENEMIES("enemies"),
    /** 味方（ほかのプレイヤー。自分を含めるかは定義で決める）。 */
    ALLIES("allies");

    private final String id;

    Affects(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Affects byId(String id) {
        return switch (id) {
            case "enemies", "enemy" -> ENEMIES;
            case "allies", "ally" -> ALLIES;
            default -> throw new DefinitionException("「affects」は enemies か allies にしてください（今は " + id + "）");
        };
    }
}
