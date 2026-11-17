package jp.amemaru.lostconnection.enemy;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * このModの敵がプレイヤーに与えるダメージ。
 *
 * <p>ダメージの元は「敵の攻撃のステータス × 技の倍率 × ばらつき」。防御での軽減・挑発の極意・盾結び・障壁などは、
 * バニラの魔物と同じく {@code CombatService} がかける（バニラの「体力の値 × 倍率」の代わりに、この値を使う）。</p>
 */
public final class EnemyCombat {
    @Nullable
    private static UUID pendingPlayer;
    private static double pendingPower = -1.0;

    private EnemyCombat() {
    }

    /**
     * 技を当てる。
     *
     * @param coefficient 技の倍率（定義の power）
     * @param direct      直接当たったもの（針などの弾。近接攻撃なら null）
     * @return 当たったか（受け流し・盾の構えなどで防がれたら false）
     */
    public static boolean strike(AmemaruMob mob, ServerPlayer player, double coefficient, @Nullable Entity direct) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || mob.isDeadOrDying()) {
            return false;
        }
        double variance = ServerConfig.DAMAGE_VARIANCE.get() / 100.0;
        double spread = 1.0 + (mob.getRandom().nextDouble() * 2.0 - 1.0) * variance;
        double raw = Math.max(0.0, mob.attackPower() * coefficient * spread);
        DamageSource source = direct == null ? mob.damageSources().mobAttack(mob) : mob.damageSources().mobProjectile(direct, mob);
        UUID previousPlayer = pendingPlayer;
        double previousPower = pendingPower;
        pendingPlayer = player.getUUID();
        pendingPower = raw;
        try {
            player.invulnerableTime = 0;
            return player.hurt(source, 1.0F);
        } finally {
            pendingPlayer = previousPlayer;
            pendingPower = previousPower;
            player.invulnerableTime = 0;
        }
    }

    /** いまの攻撃の元のダメージ（このModの敵の技でなければ -1）。{@code CombatService} が読む。 */
    public static double takePending(ServerPlayer player) {
        if (pendingPlayer != null && pendingPlayer.equals(player.getUUID()) && pendingPower >= 0) {
            double value = pendingPower;
            pendingPower = -1.0;
            return value;
        }
        return -1.0;
    }
}
