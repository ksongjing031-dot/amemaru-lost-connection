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
            boolean hit = player.hurt(source, 1.0F);
            if (hit) {
                // 打った：戦っている間は、離れても戻らない（11月22日）
                mob.noteCombat();
            }
            return hit;
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
    /**
     * 壁に埋まったプレイヤーを、いちばん近い空いた所（足もとに床がある所）へ出す。ボスの突進などで押し込まれたとき（11月23日の
     * ご指示「ボスの突進でブロックに埋まり、ダメージを受けてすぐに死んでしまう」）。出せたら true。
     */
    public static boolean unstick(ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        net.minecraft.world.phys.AABB here = player.getBoundingBox();
        if (level.noCollision(player, here)) {
            return false;
        }
        net.minecraft.world.phys.AABB local = here.move(-player.getX(), -player.getY(), -player.getZ());
        net.minecraft.core.BlockPos base = player.blockPosition();
        for (int r = 0; r <= 5; r++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        double x = base.getX() + dx + 0.5;
                        double y = base.getY() + dy;
                        double z = base.getZ() + dz + 0.5;
                        net.minecraft.world.phys.AABB box = local.move(x, y, z);
                        if (level.noCollision(player, box) && !level.noCollision(player, box.move(0.0, -0.6, 0.0))) {
                            player.teleportTo(x, y, z);
                            player.fallDistance = 0.0F;
                            return true;
                        }
                    }
                }
            }
        }
        // 近くに足場がなければ、真上の空いた所へ
        for (int dy = 1; dy <= 12; dy++) {
            net.minecraft.world.phys.AABB box = here.move(0.0, dy, 0.0);
            if (level.noCollision(player, box)) {
                player.teleportTo(player.getX(), player.getY() + dy, player.getZ());
                player.fallDistance = 0.0F;
                return true;
            }
        }
        return false;
    }
}
