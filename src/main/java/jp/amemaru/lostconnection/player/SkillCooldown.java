package jp.amemaru.lostconnection.player;

/**
 * スキル1つのクールダウン。時刻はゲーム内の tick（{@code level.getGameTime()}。すべてのディメンションで共通）。
 * ログアウトしても、死んでも続く（{@link PlayerData#save()}・{@link PlayerData#copyFrom}）。
 *
 * @param endTick    使えるようになる時刻
 * @param totalTicks クールダウンの長さ（HUDの表示用）
 */
public record SkillCooldown(long endTick, int totalTicks) {
    /** 残りの tick（終わっていれば0）。 */
    public int remaining(long now) {
        return (int) Math.max(0L, endTick - now);
    }
}
