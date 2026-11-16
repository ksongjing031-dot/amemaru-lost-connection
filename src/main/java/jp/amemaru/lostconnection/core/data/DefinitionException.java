package jp.amemaru.lostconnection.core.data;

/**
 * データパックの定義に問題があったときの例外。
 *
 * <p>メッセージは管理者に見せるため、日本語で「どこを・どう直せばよいか」を書く。</p>
 */
public class DefinitionException extends RuntimeException {
    public DefinitionException(String message) {
        super(message);
    }
}
