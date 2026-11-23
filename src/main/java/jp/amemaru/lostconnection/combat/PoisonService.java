package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.VitalsService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーがかかる毒（Phase 8 その1。2階「忘れられた森」のポコダケの胞子から。後の階でも使う）。
 *
 * <ul>
 *   <li>1秒ごとに、最大HPの2%（段階1つにつき）が減る【補完】。毒ではダウンしない（HPは1で止まる）</li>
 *   <li>解毒薬（薬屋）で消え、30秒は毒にならない（{@link ModEffects#ANTIDOTE}）</li>
 *   <li>アイコンは、敵にかける毒（Phase 6 その2。{@link AfflictionService}）と同じ {@link ModEffects#POISON}。減り方は、
 *       ここで毒にした人だけを数える（決闘でプレイヤーにかかる毒は {@link AfflictionService} が数えるので、二重に減らない）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class PoisonService {
    public static final double PERCENT_PER_SECOND = 2.0;
    private static final DustParticleOptions SPORE = new DustParticleOptions(new Vector3f(0.56F, 0.75F, 0.23F), 1.0F);
    /** 毒にした人（いつまで・段階）。 */
    private static final Map<UUID, Spore> POISONED = new ConcurrentHashMap<>();

    private record Spore(long until, int amplifier) {
    }

    private PoisonService() {
    }

    /** 毒にする（解毒薬が効いている間はならない）。なったら true。 */
    public static boolean poison(ServerPlayer player, int seconds, int amplifier) {
        if (player.hasEffect(ModEffects.ANTIDOTE.get()) || player.isCreative() || player.isSpectator()) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        int ticks = Math.max(1, seconds * 20);
        Spore old = POISONED.get(player.getUUID());
        boolean first = old == null || old.until() <= now;
        long until = Math.max(now + ticks, first ? 0L : old.until());
        int amp = Math.max(amplifier, first ? 0 : old.amplifier());
        POISONED.put(player.getUUID(), new Spore(until, amp));
        MobEffectInstance icon = player.getEffect(ModEffects.POISON.get());
        int left = (int) (until - now);
        if (icon == null || icon.getDuration() < left || icon.getAmplifier() < amp) {
            player.addEffect(new MobEffectInstance(ModEffects.POISON.get(), left, amp, false, true, true));
        }
        if (first) {
            player.displayClientMessage(Component.translatable("message.amemaru.poison.start").withStyle(ChatFormatting.DARK_GREEN), true);
            AfflictionService.poisonSound(player);
        }
        return true;
    }

    /** 毒にかかっているか（ここで毒にした分）。 */
    public static boolean poisoned(ServerPlayer player) {
        Spore spore = POISONED.get(player.getUUID());
        return spore != null && spore.until() > player.serverLevel().getGameTime();
    }

    /** 毒を消し、しばらく毒にならないようにする（解毒薬）。 */
    public static void cure(ServerPlayer player, int wardSeconds) {
        POISONED.remove(player.getUUID());
        player.removeEffect(ModEffects.POISON.get());
        player.addEffect(new MobEffectInstance(ModEffects.ANTIDOTE.get(), wardSeconds * 20, 0, false, true, true));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        Spore spore = POISONED.get(player.getUUID());
        if (spore == null) {
            return;
        }
        if (spore.until() <= player.serverLevel().getGameTime() || !player.isAlive()) {
            POISONED.remove(player.getUUID());
            return;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || !data.isStatsReady() || data.down().isDown()) {
            return;
        }
        double max = data.getStats().maxHp();
        double hp = data.getHp();
        double loss = Math.min(Math.max(0.0, hp - 1.0), max * PERCENT_PER_SECOND / 100.0 * (spore.amplifier() + 1));
        if (loss <= 0) {
            return;
        }
        data.setHp(hp - loss);
        VitalsService.pushVanillaHealth(player, data);
        CombatTextS2C.send(player, loss, CombatTextS2C.Kind.TAKEN);
        player.serverLevel().sendParticles(SPORE, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.5, 0.3, 0.0);
    }

    /** 教会で生き返った・抜けた人の毒は消える。 */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        POISONED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        POISONED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        POISONED.clear();
    }
}
