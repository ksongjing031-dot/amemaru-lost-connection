package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.PlayerData;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * 専用ゲージ（Phase 0-A 19章）。今あるのはナイトの「誓気」（{@code oath}）だけ。
 *
 * <p>誓気のたまり方【補完】：</p>
 * <ul>
 *   <li>敵の攻撃を受けると、最大HPの1%につき {@code damage_gain}（初期値1）。城壁の構えの間は {@code rampart_multiplier} 倍。</li>
 *   <li>盾結びで味方の代わりに受けると、自分の最大HPの1%につき {@code bond_gain}（初期値1）。</li>
 *   <li>スキルの効果（呼び声など）でたまる。</li>
 * </ul>
 * <p>戦闘が終わると0に戻る（Phase 0-B 4.1）。</p>
 */
public final class GaugeService {
    /** 誓気の種類の名前。 */
    public static final String OATH = "oath";

    private GaugeService() {
    }

    /** 今の職業の専用ゲージ（ないときは null）。 */
    @Nullable
    public static JobDef.GaugeDef gaugeOf(PlayerData data) {
        JobDef job = SkillRegistry.get().job(data.getCombatJob());
        return job == null ? null : job.gauge();
    }

    public static int max(PlayerData data) {
        JobDef.GaugeDef gauge = gaugeOf(data);
        return gauge == null ? 0 : gauge.max();
    }

    /** ゲージをためる（最大値で止まる）。 */
    public static void add(PlayerData data, double amount) {
        JobDef.GaugeDef gauge = gaugeOf(data);
        if (gauge == null || amount <= 0) {
            return;
        }
        data.setGauge(Math.min(gauge.max(), data.getGauge() + amount));
    }

    /**
     * ゲージを使う。足りなければ使わずに false。使ったときは、パッシブ「誓気の循環」の分だけMPが戻る。
     */
    public static boolean consume(ServerPlayer player, PlayerData data, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (data.getGauge() + 1.0E-6 < amount) {
            return false;
        }
        data.setGauge(data.getGauge() - amount);
        double refund = PassiveService.oathRefundRatio(data);
        if (refund > 0 && data.isStatsReady()) {
            data.setMp(data.getMp() + amount * refund);
        }
        return true;
    }

    /** 敵の攻撃を受けたとき。 */
    public static void onDamageTaken(ServerPlayer player, PlayerData data, double damage, long now) {
        JobDef.GaugeDef gauge = gaugeOf(data);
        if (gauge == null || !OATH.equals(gauge.type()) || !data.isStatsReady() || data.getStats().maxHp() <= 0) {
            return;
        }
        double percent = damage / data.getStats().maxHp() * 100.0;
        double gain = percent * gauge.param("damage_gain", 1.0);
        if (data.getBuffs().rampart(now)) {
            gain *= gauge.param("rampart_multiplier", 2.0);
        }
        add(data, gain);
    }

    /** 盾結びで、味方の代わりにダメージを受けたとき。 */
    public static void onBondAbsorbed(ServerPlayer knight, PlayerData data, double damage, long now) {
        JobDef.GaugeDef gauge = gaugeOf(data);
        if (gauge == null || !OATH.equals(gauge.type()) || !data.isStatsReady() || data.getStats().maxHp() <= 0) {
            return;
        }
        add(data, damage / data.getStats().maxHp() * 100.0 * gauge.param("bond_gain", 1.0));
    }

    /** 毎 tick：戦闘が終わったらゲージを0に戻す。 */
    public static void tick(ServerPlayer player, PlayerData data, long now) {
        if (data.getGauge() <= 0) {
            return;
        }
        boolean inCombat = now - data.getLastCombatTick() < ServerConfig.OUT_OF_COMBAT_SECONDS.get() * 20L;
        if (!inCombat || gaugeOf(data) == null) {
            data.setGauge(0.0);
        }
    }
}
