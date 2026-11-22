package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.PlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 専用ゲージ（Phase 0-A 19章）。職業の定義の {@code gauge.type} で、たまり方が決まる。
 *
 * <ul>
 *   <li>誓気（{@code oath}・ナイト）【補完】：敵の攻撃を受けると、最大HPの1%につき {@code damage_gain}（城壁の構えの間は
 *       {@code rampart_multiplier} 倍）。盾結びで味方の代わりに受けると {@code bond_gain}。スキルの効果でもたまる。</li>
 *   <li>集中（{@code focus}・アーチャー。Phase 6 その2）【補完】：戦闘の間、立ち止まっていると毎秒 {@code still_gain}（静止集中で増える）。
 *       攻撃を当てると {@code hit_gain}。集中の極意で、スキルで使った分の一部が戻る。</li>
 *   <li>気配（{@code presence}・ローグ。Phase 6 その2）：攻撃を当てるたびに1（スキルは1回使うごとに1。最大5）。</li>
 *   <li>気（{@code ki}・モンク。Phase 6 その2）【補完】：攻撃を当てると、前に当ててから {@code window} 秒以内なら {@code hit_gain}、
 *       あいていれば {@code first_gain}。{@code decay_delay} 秒当てないでいると、毎秒 {@code decay} ずつ減る。</li>
 * </ul>
 * <p>どのゲージも、戦闘が終わると0に戻る（Phase 0-B 4.1）。</p>
 */
public final class GaugeService {
    public static final String OATH = "oath";
    public static final String FOCUS = "focus";
    public static final String PRESENCE = "presence";
    public static final String KI = "ki";

    /** 気：最後に当てた時刻。 */
    private static final Map<UUID, Long> LAST_HIT = new HashMap<>();
    /** 集中：0.5秒前の位置（立ち止まっているかを見る）。 */
    private static final Map<UUID, Vec3> LAST_POSITION = new HashMap<>();

    private GaugeService() {
    }

    /** 今の職業の専用ゲージ（ないときは null）。 */
    @Nullable
    public static JobDef.GaugeDef gaugeOf(PlayerData data) {
        JobDef job = SkillRegistry.get().job(data.getCombatJob());
        return job == null ? null : job.gauge();
    }

    /** 今の職業の専用ゲージの種類（ないときは空）。 */
    public static String typeOf(PlayerData data) {
        JobDef.GaugeDef gauge = gaugeOf(data);
        return gauge == null ? "" : gauge.type();
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
     * ゲージを使う。足りなければ使わずに false。使ったときは、パッシブ「誓気の循環」の分だけMPが戻り、
     * 「集中の極意」の分だけ集中が戻る。
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
        double focusRefund = PassiveService.value(data, PassiveDef.Type.FOCUS_REFUND);
        if (focusRefund > 0 && FOCUS.equals(typeOf(data))) {
            add(data, amount * focusRefund / 100.0);
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

    /**
     * 攻撃を敵に当てたとき（{@code HitHooks}）。
     *
     * @param countPresence 気配を1ためるか（スキルは1回使うごとに1回だけ true）
     */
    public static void onHit(ServerPlayer player, PlayerData data, long now, boolean countPresence) {
        JobDef.GaugeDef gauge = gaugeOf(data);
        if (gauge == null) {
            return;
        }
        switch (gauge.type()) {
            case FOCUS -> add(data, gauge.param("hit_gain", 5.0));
            case PRESENCE -> {
                if (countPresence) {
                    add(data, 1.0);
                }
            }
            case KI -> {
                Long last = LAST_HIT.get(player.getUUID());
                double window = gauge.param("window", 2.0) * 20.0;
                boolean chained = last != null && now - last <= window;
                add(data, chained ? gauge.param("hit_gain", 6.0) : gauge.param("first_gain", 3.0));
                LAST_HIT.put(player.getUUID(), now);
            }
            default -> {
            }
        }
    }

    /** 毎 tick：戦闘が終わったらゲージを0に戻す。集中は立ち止まるとたまり、気は当てないと減る。 */
    public static void tick(ServerPlayer player, PlayerData data, long now) {
        JobDef.GaugeDef gauge = gaugeOf(data);
        boolean inCombat = now - data.getLastCombatTick() < ServerConfig.OUT_OF_COMBAT_SECONDS.get() * 20L;
        if (!inCombat || gauge == null) {
            if (data.getGauge() > 0) {
                data.setGauge(0.0);
            }
            return;
        }
        switch (gauge.type()) {
            case FOCUS -> {
                if (player.tickCount % 10 != 0) {
                    return;
                }
                Vec3 position = player.position();
                Vec3 before = LAST_POSITION.put(player.getUUID(), position);
                boolean still = before != null && player.onGround()
                        && new Vec3(position.x - before.x, 0.0, position.z - before.z).lengthSqr() < 0.0025;
                if (still) {
                    double bonus = PassiveService.value(data, PassiveDef.Type.STILL_FOCUS);
                    add(data, gauge.param("still_gain", 6.0) * 0.5 * (1.0 + bonus / 100.0));
                }
            }
            case KI -> {
                if (data.getGauge() <= 0) {
                    return;
                }
                Long last = LAST_HIT.get(player.getUUID());
                double delay = gauge.param("decay_delay", 3.0) * 20.0;
                if (last == null || now - last > delay) {
                    data.setGauge(Math.max(0.0, data.getGauge() - gauge.param("decay", 15.0) / 20.0));
                }
            }
            default -> {
            }
        }
    }

    /** ログアウトしたプレイヤーの記録を消す。 */
    public static void forget(UUID player) {
        LAST_HIT.remove(player);
        LAST_POSITION.remove(player);
    }
}
