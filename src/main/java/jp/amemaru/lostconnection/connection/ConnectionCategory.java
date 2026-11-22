package jp.amemaru.lostconnection.connection;

import jp.amemaru.lostconnection.core.data.DefinitionException;

import java.util.Locale;

/**
 * コネクション量（CP）の7分野（Phase 0-A 13章）。1人1日の上限は分野ごとに150CP、合計600CP（config）。
 * 戦闘だけでは150しか稼げず、戦闘以外の5分野だけで合計の上限に届く。
 */
public enum ConnectionCategory {
    COMBAT, PRODUCTION, TRADE, SOCIAL, PLAY, TOWN, STORY;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 言語ファイルのキー（「戦闘」など）。 */
    public String translationKey() {
        return "connection.amemaru.category." + key();
    }

    public static ConnectionCategory parse(String text) {
        for (ConnectionCategory category : values()) {
            if (category.key().equals(text)) {
                return category;
            }
        }
        throw new DefinitionException("「category」は combat・production・trade・social・play・town・story のどれかにしてください（今は "
                + text + "）");
    }

    public static ConnectionCategory byIndex(int index) {
        ConnectionCategory[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, index))];
    }
}
