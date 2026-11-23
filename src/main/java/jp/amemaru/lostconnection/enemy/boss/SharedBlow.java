package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.core.registry.ModDamageTypes;
import net.minecraft.server.level.ServerPlayer;

/**
 * ボスの「みんなで受ける一撃」（11月24日のご指示「ソロでは全然倒せない、パーティーで挑まないと絶対にクリアできないぐらいの
 * 難易度」【補完】）の受け方。青い円の中の人みなで、最大HPの割合（{@code percent}%）を人数で分けて受ける。
 *
 * <ul>
 *   <li>ダメージは、受ける人それぞれの最大HPの割合。防御・回避・盾は効かない（ダメージの種類 {@code amemaru:heavy_blow}）</li>
 *   <li>障壁・不屈・身代わりの鈴は効く（ふつうの被ダメージの流れを通る）</li>
 *   <li>{@link AmemaruBoss#shareSafe} のときは、ダメージを与えない（管理者・CIの写真）</li>
 * </ul>
 */
public final class SharedBlow {
    private SharedBlow() {
    }

    /** ひとりぶんの割合（0〜。1で最大HPと同じ）。 */
    public static double split(double percent, int players) {
        return Math.max(0.0, percent) / 100.0 / Math.max(1, players);
    }

    /** 最大HPの {@code fraction} 倍を受ける。 */
    public static void hit(ServerPlayer player, double fraction) {
        if (AmemaruBoss.shareSafe || fraction <= 0.0 || player.isCreative() || player.isSpectator()) {
            return;
        }
        // CombatService は、敵を出どころにしないダメージを「バニラの最大HPに対する割合」として、このModの最大HPに直して受ける
        float amount = (float) (fraction * player.getMaxHealth());
        player.invulnerableTime = 0;
        player.hurt(ModDamageTypes.heavyBlow(player.level()), amount);
        player.invulnerableTime = 0;
    }
}
