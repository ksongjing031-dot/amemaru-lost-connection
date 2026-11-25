package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.equipment.GearEffects;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーの寒さ（11月25日。第2章「凍てついた記憶」：6階の霜の敵の攻撃は、当たった人を凍えさせる【補完】）。
 *
 * <ul>
 *   <li>凍え（{@link ModEffects#FROST}。敵にかける凍えと同じ効果・同じアイコン）：1つにつき移動 −10%。人は {@link #MAX_CHILL} まで</li>
 *   <li>寒さに強い装備（特殊効果 {@code cold_resist}。6階のボスのレア「村長の毛皮」。Phase 0-A 20章「寒さと遅くなる効果を減らす」）：
 *       凍え・凍結・遅くなる効果（バニラの鈍足）の時間を、その割合だけ短くする（80%まで）。ほかの所でかかった鈍足も、次の tick に
 *       短くし直す</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class PlayerColdService {
    /** 人にかかる凍えの上限（−30%）。 */
    public static final int MAX_CHILL = 3;
    private static final Map<UUID, Boolean> PENDING = new HashMap<>();

    private PlayerColdService() {
    }

    /** 寒さに強い割合（0〜80%）。 */
    public static double resist(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (SpecialEffect effect : GearEffects.active(player, data)) {
            if (effect.type() == SpecialEffect.Type.COLD_RESIST) {
                sum += effect.percent();
            }
        }
        return Math.min(80.0, sum);
    }

    /** 凍えさせる（stacks 段。ticks は寒さに強い分だけ短くなる）。 */
    public static void chill(ServerPlayer player, int stacks, int ticks) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || stacks <= 0) {
            return;
        }
        int length = (int) Math.round(ticks * (1.0 - resist(player) / 100.0));
        if (length <= 0) {
            return;
        }
        MobEffect frost = ModEffects.FROST.get();
        MobEffectInstance current = player.getEffect(frost);
        int have = current == null ? 0 : current.getAmplifier() + 1;
        int next = Math.min(MAX_CHILL, have + stacks);
        int duration = Math.max(length, current == null ? 0 : current.getDuration());
        PENDING.put(player.getUUID(), Boolean.TRUE);
        player.addEffect(new MobEffectInstance(frost, duration, next - 1, false, true, true));
        PENDING.remove(player.getUUID());
    }

    /** 凍えの段（0なら凍えていない）。 */
    public static int chillOf(Player player) {
        MobEffectInstance current = player.getEffect(ModEffects.FROST.get());
        return current == null ? 0 : current.getAmplifier() + 1;
    }

    /** ほかの所でかかった鈍足・凍結も、寒さに強い分だけ短くする（次の tick に、短い同じ効果にかけ直す）。 */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || PENDING.containsKey(player.getUUID())) {
            return;
        }
        MobEffect effect = event.getEffectInstance().getEffect();
        if (effect == MobEffects.MOVEMENT_SLOWDOWN || effect == ModEffects.FROZEN.get()) {
            SHORTEN.put(player.getUUID(), effect);
        }
    }

    private static final Map<UUID, MobEffect> SHORTEN = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        MobEffect effect = SHORTEN.remove(player.getUUID());
        if (effect == null) {
            return;
        }
        double resist = resist(player);
        MobEffectInstance current = player.getEffect(effect);
        if (resist <= 0.0 || current == null || current.isInfiniteDuration()) {
            return;
        }
        int shorter = (int) Math.round(current.getDuration() * (1.0 - resist / 100.0));
        player.removeEffect(effect);
        if (shorter > 0) {
            PENDING.put(player.getUUID(), Boolean.TRUE);
            player.addEffect(new MobEffectInstance(effect, shorter, current.getAmplifier(), current.isAmbient(), current.isVisible(),
                    current.showIcon()));
            PENDING.remove(player.getUUID());
        }
    }
}
