package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModSounds;
import jp.amemaru.lostconnection.entity.LampBolt;
import jp.amemaru.lostconnection.equipment.GearService;
import jp.amemaru.lostconnection.job.PassiveService;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 弓の通常の射撃（Phase 6 その2【補完】）：弓を持って左クリックすると、前へ矢を射る（近接攻撃はしない）。
 *
 * <ul>
 *   <li>ダメージは、攻撃のステータス × 通常の攻撃の係数（近接攻撃と同じ）。標・遠矢・連なる矢・疾風の弓舞が効き、集中がたまる。</li>
 *   <li>射てる間隔は、攻撃の速さ（弓は約0.8秒。素早さで速くなる）。押し続けると、間隔ごとに射続ける。</li>
 *   <li>狙撃の構えの間は、矢1本ごとに構えを使って強くなる。</li>
 *   <li>11月26日：射るたびに、職業の {@code attack_lock} の {@code basic} の間だけ足が遅くなる（アーチャーは0.9秒・60%。
 *       押し続けている間は、ずっと遅い）。</li>
 * </ul>
 */
public final class BasicShotService {
    /** 矢の速さと射程。 */
    private static final double SPEED = 2.8;
    private static final double RANGE = 36.0;
    /** いちばん短い間隔（tick）。 */
    private static final int MIN_INTERVAL = 8;

    private static final Map<UUID, Long> NEXT_SHOT = new HashMap<>();

    private BasicShotService() {
    }

    /** 矢を射る（間隔が足りなければ何もしない）。 */
    public static void tryShoot(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || DownHandler.isDown(player) || !PassiveService.holdsBow(player)) {
            return;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || !data.isStatsReady() || data.getCasting() != null) {
            return;
        }
        long now = player.level().getGameTime();
        Long next = NEXT_SHOT.get(player.getUUID());
        // 通信の遅れの分、2 tick だけ早めを許す
        if (next != null && now < next - 2) {
            return;
        }
        int interval = Math.max(MIN_INTERVAL, Math.round(player.getCurrentItemAttackStrengthDelay()));
        NEXT_SHOT.put(player.getUUID(), now + interval);
        double coefficient = ServerConfig.BASIC_ATTACK_COEFFICIENT.get();
        BuffState buffs = data.getBuffs();
        if (buffs.aiming(now)) {
            coefficient *= 1.0 + buffs.aimBonus / 100.0;
            buffs.aimShots--;
            if (buffs.aimShots <= 0) {
                buffs.aimUntil = 0;
                player.removeEffect(ModEffects.AIMING.get());
            }
        }
        LampBolt.launchBasic(player, player.getLookAngle(), SPEED, RANGE, coefficient);
        // 11月26日：射っている間は、足が遅くなる（職業の attack_lock の basic。アーチャーは60%）
        jp.amemaru.lostconnection.job.AttackLockService.afterBasicShot(player, data.getCombatJob());
        player.swing(InteractionHand.MAIN_HAND, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.get("skill.archer.basic_shot"),
                SoundSource.PLAYERS, 0.7F, 0.95F + player.getRandom().nextFloat() * 0.1F);
        data.markCombat(now);
        GearService.wearWeapon(player, false);
    }

    /** 次に射てるまでの tick（クライアントの表示用ではなく、テスト用）。 */
    public static long nextShot(UUID player) {
        Long next = NEXT_SHOT.get(player);
        return next == null ? 0L : next;
    }

    public static void forget(UUID player) {
        NEXT_SHOT.remove(player);
    }
}
