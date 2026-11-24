package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.job.GaugeService;
import jp.amemaru.lostconnection.job.HealService;
import jp.amemaru.lostconnection.job.PassiveService;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.player.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 近接職の守り（11月25日のご指示「近接職がボス戦でのダメージ受けすぎてすぐに倒れてしまう。回復系のスキルや防御力を上げるなどで
 * 対策をする」「剣士は後半のパッシブで体力が少なくなると体力が急激に回復するなどを追加する」）【補完】。
 *
 * <ul>
 *   <li>不屈の再起（ナイト・守護の5段目の列。{@link PassiveDef.Type#LAST_STAND}）：HPが3割を切ると、すぐに最大HPの value% が戻り、
 *       5秒かけてさらに value% 戻る。90秒に1回</li>
 *   <li>影吸い（ローグ・影の2段目。{@link PassiveDef.Type#LIFE_DRAIN}）：与えたダメージの value% だけHPが戻る（1回で最大HPの3%まで）</li>
 *   <li>金剛身（モンク・剛の2段目。{@link PassiveDef.Type#ADAMANT_BODY}）：受けるダメージ -value%。気が50以上の間は、さらに -value%</li>
 * </ul>
 */
public final class MeleeGuard {
    /** 不屈の再起が働く、HPの割合。 */
    public static final double LAST_STAND_HP = 0.3;
    /** 不屈の再起の、残りを戻す長さ（tick）。 */
    public static final int LAST_STAND_REGEN_TICKS = 100;
    /** 不屈の再起の、次に働くまで（tick）。 */
    public static final int LAST_STAND_COOLDOWN = 90 * 20;
    /** 影吸いの、1回で戻る上限（最大HPの割合）。 */
    public static final double DRAIN_CAP = 0.03;

    private static final Map<UUID, Long> LAST_STAND_READY = new HashMap<>();

    private MeleeGuard() {
    }

    /** ダメージを受けてHPが3割を切った（{@link CombatService}）。不屈の再起が働けば true。 */
    public static boolean onLowHp(ServerPlayer player, PlayerData data, long now) {
        PassiveService.Active stand = PassiveService.find(data, PassiveDef.Type.LAST_STAND);
        if (stand == null || !data.isStatsReady()) {
            return false;
        }
        Long ready = LAST_STAND_READY.get(player.getUUID());
        if (ready != null && now < ready) {
            return false;
        }
        LAST_STAND_READY.put(player.getUUID(), now + LAST_STAND_COOLDOWN);
        double maxHp = data.getStats().maxHp();
        double percent = stand.value();
        HealService.heal(player, data, maxHp * percent / 100.0);
        HealService.applyRegen(player, maxHp * percent / 100.0 / (LAST_STAND_REGEN_TICKS / 20.0), LAST_STAND_REGEN_TICKS, player);
        GaugeService.add(data, 20.0);
        player.displayClientMessage(Component.translatable("message.amemaru.passive.last_stand").withStyle(ChatFormatting.GOLD,
                ChatFormatting.BOLD), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                1.0F, 1.4F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS,
                0.5F, 1.6F);
        player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 40,
                0.4, 0.6, 0.4, 0.35);
        return true;
    }

    /** 不屈の再起が、次に働けるまでの tick（働けるなら 0。自動テスト用）。 */
    public static long lastStandWait(ServerPlayer player, long now) {
        Long ready = LAST_STAND_READY.get(player.getUUID());
        return ready == null ? 0L : Math.max(0L, ready - now);
    }

    /** 影吸い：与えたダメージの一部で癒す（{@code HitHooks}）。 */
    public static void drain(ServerPlayer attacker, PlayerData data, double dealt) {
        if (dealt <= 0 || !data.isStatsReady()) {
            return;
        }
        double percent = PassiveService.value(data, PassiveDef.Type.LIFE_DRAIN);
        if (percent <= 0) {
            return;
        }
        double amount = Math.min(dealt * percent / 100.0, data.getStats().maxHp() * DRAIN_CAP);
        HealService.heal(attacker, data, amount);
    }

    /** 金剛身：受けるダメージを減らす割合（%）。気が50以上なら2倍。 */
    public static double adamantReduction(PlayerData data) {
        double value = PassiveService.value(data, PassiveDef.Type.ADAMANT_BODY);
        if (value <= 0) {
            return 0.0;
        }
        return data.getGauge() >= PassiveService.KI_THRESHOLD ? value * 2.0 : value;
    }

    public static void clear() {
        LAST_STAND_READY.clear();
    }
}
