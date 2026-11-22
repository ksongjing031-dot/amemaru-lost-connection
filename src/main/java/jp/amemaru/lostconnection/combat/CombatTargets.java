package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.entity.TrainingDummy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * スキルの相手になれるかの決まり。
 *
 * <ul>
 *   <li>敵：魔物（{@link Enemy}）と訓練人形。プレイヤー・動物・村人には当たらない（協力のゲームのため）。
 *       Phase 3でこのModの敵（AmemaruMob）を足す。</li>
 *   <li>味方：ほかのプレイヤー（観戦モードを除く）。Phase 4：ダウンしている人は、回復や強化の相手にならない（起こすのは
 *       しゃがんで手を取る）。範囲の強化・回復は、パーティのメンバーを先にする（{@code SkillTargeting#around}）。</li>
 * </ul>
 */
public final class CombatTargets {
    private CombatTargets() {
    }

    /**
     * {@code caster} から見て敵か（Phase 6 その4：決闘の相手のプレイヤーは、相手どうしでだけ敵になる。決闘の間だけ）。
     */
    public static boolean isEnemyOf(@javax.annotation.Nullable Entity caster, Entity entity) {
        if (isEnemy(entity)) {
            return true;
        }
        return caster instanceof ServerPlayer a && entity instanceof ServerPlayer b && a != b && b.isAlive()
                && jp.amemaru.lostconnection.social.DuelService.areOpponents(a, b);
    }

    public static boolean isEnemy(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity instanceof Player || entity instanceof ArmorStand) {
            return false;
        }
        if (living.isInvulnerable()) {
            return false;
        }
        return entity instanceof TrainingDummy || entity instanceof Enemy;
    }

    /** ボス（投げられない・縫い止めが半分など。Phase 6 その2）。 */
    public static boolean isBoss(Entity entity) {
        return entity instanceof jp.amemaru.lostconnection.enemy.boss.AmemaruBoss;
    }

    public static boolean isAlly(ServerPlayer caster, Entity entity, boolean includeSelf) {
        if (!(entity instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                || jp.amemaru.lostconnection.player.DownHandler.isDown(player)) {
            return false;
        }
        // 決闘の間は、決闘している人と、ほかの人の間で回復や強化が届かない（Phase 6 その4。自分には届く）
        if (player != caster && (jp.amemaru.lostconnection.social.DuelService.isolated(caster)
                || jp.amemaru.lostconnection.social.DuelService.isolated(player))) {
            return false;
        }
        return includeSelf || player != caster;
    }
}
