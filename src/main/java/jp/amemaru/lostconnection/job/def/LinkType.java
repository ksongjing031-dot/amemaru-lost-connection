package jp.amemaru.lostconnection.job.def;

import jp.amemaru.lostconnection.core.data.DefinitionException;

/**
 * 連携技の属性（Phase 0-A 13章）。
 *
 * <ul>
 *   <li>起点：当てた敵に「起点」の印をつける。</li>
 *   <li>追撃：ほかのプレイヤーの起点から4秒以内に同じ敵へ当てると「連携！」（追撃のダメージ+30%）。
 *       さらに別の人がつなぐと「三連携」（+50%）。</li>
 *   <li>支援：味方にかけると、その味方が4秒以内に使うスキルの効果が+20%（連携支援）。</li>
 * </ul>
 */
public enum LinkType {
    NONE("none", ""),
    STARTER("starter", "起点"),
    FINISHER("finisher", "追撃"),
    SUPPORT("support", "支援");

    private final String id;
    private final String label;

    LinkType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    /** 画面に出す名前（なしのときは空）。 */
    public String label() {
        return label;
    }

    public static LinkType byId(String id) {
        for (LinkType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        throw new DefinitionException("「link」は none・starter・finisher・support のどれかにしてください（今は " + id + "）");
    }

    public static LinkType byIndex(int index) {
        LinkType[] values = values();
        return index >= 0 && index < values.length ? values[index] : NONE;
    }
}
