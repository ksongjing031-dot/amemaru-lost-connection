package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ロスト状態（Phase 0-A 13章「ロスト状態は糸が一時的に切れた状態」。11月25日、7階から）。
 *
 * <p>共通の決まり（確定）：</p>
 * <ul>
 *   <li>仲間に触れれば解ける：味方のそばでしゃがみ、3秒触れる「結び直し」で、ほぼすべてのロスト状態が解ける。ヒーラーのほどく技
 *       （害のある状態をほどく。{@link jp.amemaru.lostconnection.job.HealService#cleanse}）と、消耗品「結び紐」でも解ける</li>
 *   <li>蘇生は妨げない（ダウンした仲間の蘇生は、どのロスト状態でもできる）</li>
 *   <li>「ロスト耐性」で短くなる（ステータスのロスト耐性。持続時間を最大50%まで短くする）</li>
 *   <li>ロスト状態のあいだは、画面の縁に白いノイズを出す（クライアント）</li>
 * </ul>
 * <p>結び直しは、しゃがんだ人と、そのそば {@link #RETIE_RANGE} ブロックのほかの人（パーティでなくてよい【補完】）の、どちらかに
 * ロスト状態があるときに数え、{@link #RETIE_TICKS} tick 続くと、ふたりともロスト状態が解ける【補完】。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class LostService {
    /** 結び直しにかかる時間（3秒）。 */
    public static final int RETIE_TICKS = 60;
    /** 結び直しで「そば」とする距離。 */
    public static final double RETIE_RANGE = 2.5;
    /** ロスト耐性で短くなる上限（%）。 */
    public static final double MAX_RESIST = 50.0;

    private static final Map<UUID, Retie> RETIE = new HashMap<>();
    private static final Set<UUID> TOLD = new HashSet<>();

    private record Retie(UUID partner, int ticks) {
    }

    private LostService() {
    }

    /** ロスト状態の一覧（7階の「薄れ」・8階の「断線」「静寂」。忘却などは、その階を作るときに足す）。 */
    public static List<MobEffect> states() {
        return List.of(ModEffects.LOST_FADE.get(), ModEffects.LOST_SEVER.get(), ModEffects.LOST_SILENCE.get());
    }

    public static boolean isLost(MobEffect effect) {
        return states().contains(effect);
    }

    /** なにかのロスト状態にあるか。 */
    public static boolean anyLost(LivingEntity entity) {
        for (MobEffect effect : states()) {
            if (entity.hasEffect(effect)) {
                return true;
            }
        }
        return false;
    }

    /** ロスト耐性（%。ステータスと、装備の特殊効果 lost_resist の合計。0〜{@link #MAX_RESIST}）。 */
    public static double resist(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || !data.isStatsReady()) {
            return 0.0;
        }
        double sum = data.getStats().get(StatType.LOST_RESIST)
                + jp.amemaru.lostconnection.equipment.GearEffects.lostResist(player, data);
        return Math.max(0.0, Math.min(MAX_RESIST, sum));
    }

    /** ロスト状態にする（ticks はロスト耐性の分だけ短くなる。今の残りより短ければ、のばさない）。 */
    public static void inflict(ServerPlayer player, MobEffect state, int ticks) {
        inflict(player, state, ticks, 0);
    }

    /**
     * 強さつきでロスト状態にする（11月25日：8階のボスの「静寂の帳」は、静寂の強さ1。強い方を残す）。今の残りの方が長く、
     * 強さも同じか強ければ、何もしない。
     */
    public static void inflict(ServerPlayer player, MobEffect state, int ticks, int amplifier) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || ticks <= 0) {
            return;
        }
        int length = (int) Math.round(ticks * (1.0 - resist(player) / 100.0));
        MobEffectInstance current = player.getEffect(state);
        if (length <= 0 || current != null && current.getDuration() >= length && current.getAmplifier() >= amplifier) {
            return;
        }
        if (current != null && current.getAmplifier() > amplifier) {
            amplifier = current.getAmplifier();
        }
        boolean was = player.hasEffect(state);
        if (was && current != null && current.getAmplifier() < amplifier) {
            player.removeEffect(state);
        }
        player.addEffect(new MobEffectInstance(state, Math.max(length, current == null ? 0 : current.getDuration()), amplifier, false, true,
                true));
        if (!was) {
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 0.8F, 0.5F);
            String key = "message.amemaru.lost." + net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(state).getPath();
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.WHITE), true);
            if (TOLD.add(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("message.amemaru.lost.retie_hint").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /** ロスト状態をすべて解く（解いた数）。 */
    public static int clear(LivingEntity entity) {
        int n = 0;
        for (MobEffect effect : states()) {
            if (entity.hasEffect(effect)) {
                entity.removeEffect(effect);
                n++;
            }
        }
        return n;
    }

    // ================================================================ 結び直し

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        tickRetie(player, player.serverLevel().players());
    }

    /** 1 tick 分の結び直し（自動テストからも、まわりの人を渡して呼ぶ）。結び直したら true。 */
    public static boolean tickRetie(ServerPlayer player, Iterable<? extends ServerPlayer> around) {
        if (!player.isShiftKeyDown() || !player.isAlive() || player.isSpectator() || down(player)) {
            RETIE.remove(player.getUUID());
            return false;
        }
        ServerPlayer partner = partner(player, around);
        if (partner == null) {
            RETIE.remove(player.getUUID());
            return false;
        }
        Retie current = RETIE.get(player.getUUID());
        int ticks = current != null && current.partner().equals(partner.getUUID()) ? current.ticks() + 1 : 1;
        RETIE.put(player.getUUID(), new Retie(partner.getUUID(), ticks));
        ServerLevel level = player.serverLevel();
        if (ticks % 5 == 0) {
            Vec3 a = player.position().add(0, 1.0, 0);
            Vec3 b = partner.position().add(0, 1.0, 0);
            for (int i = 0; i <= 6; i++) {
                Vec3 p = a.lerp(b, i / 6.0);
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
        if (ticks % 10 == 0 && ticks < RETIE_TICKS) {
            int percent = ticks * 100 / RETIE_TICKS;
            player.displayClientMessage(Component.translatable("message.amemaru.lost.retying", partner.getDisplayName(), percent)
                    .withStyle(ChatFormatting.AQUA), true);
            partner.displayClientMessage(Component.translatable("message.amemaru.lost.retying", player.getDisplayName(), percent)
                    .withStyle(ChatFormatting.AQUA), true);
        }
        if (ticks < RETIE_TICKS) {
            return false;
        }
        RETIE.remove(player.getUUID());
        int n = clear(player) + clear(partner);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4F, 1.3F);
        level.sendParticles(ParticleTypes.END_ROD, partner.getX(), partner.getY() + 1.0, partner.getZ(), 16, 0.4, 0.6, 0.4, 0.03);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.6, 0.4, 0.03);
        player.displayClientMessage(Component.translatable("message.amemaru.lost.retied", partner.getDisplayName()).withStyle(ChatFormatting.GOLD),
                true);
        partner.displayClientMessage(Component.translatable("message.amemaru.lost.retied", player.getDisplayName())
                .withStyle(ChatFormatting.GOLD), true);
        AmemaruMod.LOGGER.info("[amemaru] {} retied with {} ({} lost state(s) cleared)", player.getGameProfile().getName(),
                partner.getGameProfile().getName(), n);
        return true;
    }

    /** 結び直しの相手：そばのほかの人で、どちらかにロスト状態があるとき（いちばん近い人）。 */
    @Nullable
    private static ServerPlayer partner(ServerPlayer player, Iterable<? extends ServerPlayer> around) {
        boolean self = anyLost(player);
        ServerPlayer best = null;
        double bestDistance = RETIE_RANGE * RETIE_RANGE;
        for (ServerPlayer other : around) {
            if (other == player || !other.isAlive() || other.isSpectator() || other.level() != player.level()) {
                continue;
            }
            if (!self && !anyLost(other)) {
                continue;
            }
            double d = other.distanceToSqr(player);
            if (d <= bestDistance) {
                bestDistance = d;
                best = other;
            }
        }
        return best;
    }

    private static boolean down(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        return data != null && data.down().isDown();
    }

    /** 結び直しの途中の tick 数（自動テスト用）。 */
    public static int retieTicks(ServerPlayer player) {
        Retie retie = RETIE.get(player.getUUID());
        return retie == null ? 0 : retie.ticks();
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RETIE.remove(event.getEntity().getUUID());
        TOLD.remove(event.getEntity().getUUID());
    }
}
