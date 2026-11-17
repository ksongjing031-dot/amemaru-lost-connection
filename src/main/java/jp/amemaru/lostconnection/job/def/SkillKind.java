package jp.amemaru.lostconnection.job.def;

/** スキルの種類。 */
public enum SkillKind {
    /** キーを押して使うスキル。 */
    ACTIVE("active", "アクティブ"),
    /** 覚えるだけで効くスキル。 */
    PASSIVE("passive", "パッシブ");

    private final String id;
    private final String label;

    SkillKind(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    /** 画面に出す名前。 */
    public String label() {
        return label;
    }

    public static SkillKind byId(String id) {
        for (SkillKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        throw new jp.amemaru.lostconnection.core.data.DefinitionException("「type」は active か passive にしてください（今は " + id + "）");
    }
}
