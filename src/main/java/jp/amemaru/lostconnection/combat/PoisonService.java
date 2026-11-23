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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * 毒（Phase 8 その1。2階「忘れられた森」のポコダケの胞子から。後の階でも使う）。
 *
 * <ul>
 *   <li>1秒ごとに、最大HPの2%（段階1つにつき）が減る【補完】。毒ではダウンしない（HPは1で止まる）</li>
 *   <li>毒消しの薬（薬屋）で消え、30秒は毒にならない（{@link ModEffects#ANTIDOTE}）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class PoisonService {
    public static final double PERCENT_PER_SECOND = 2.0;
    private static final DustParticleOptions SPORE = new DustParticleOptions(new Vector3f(0.56F, 0.75F, 0.23F), 1.0F);

    private PoisonService() {
    }

    /** 毒にする（毒消しが効いている間はならない）。なったら true。 */
    public static boolean poison(ServerPlayer player, int seconds, int amplifier) {
        if (player.hasEffect(ModEffects.ANTIDOTE.get()) || player.isCreative() || player.isSpectator()) {
            return false;
        }
        MobEffectInstance now = player.getEffect(ModEffects.POISON.get());
        int ticks = seconds * 20;
        if (now != null && now.getAmplifier() >= amplifier && now.getDuration() >= ticks) {
            return true;
        }
        boolean first = now == null;
        player.addEffect(new MobEffectInstance(ModEffects.POISON.get(), Math.max(ticks, now == null ? 0 : now.getDuration()),
                Math.max(amplifier, now == null ? 0 : now.getAmplifier()), false, true, true));
        if (first) {
            player.displayClientMessage(Component.translatable("message.amemaru.poison.start").withStyle(ChatFormatting.DARK_GREEN), true);
        }
        return true;
    }

    /** 毒を消し、しばらく毒にならないようにする（毒消しの薬）。 */
    public static void cure(ServerPlayer player, int wardSeconds) {
        player.removeEffect(ModEffects.POISON.get());
        player.addEffect(new MobEffectInstance(ModEffects.ANTIDOTE.get(), wardSeconds * 20, 0, false, true, true));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        MobEffectInstance poison = player.getEffect(ModEffects.POISON.get());
        if (poison == null) {
            return;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || !data.isStatsReady() || data.down().isDown()) {
            return;
        }
        double max = data.getStats().maxHp();
        double hp = data.getHp();
        double loss = Math.min(Math.max(0.0, hp - 1.0), max * PERCENT_PER_SECOND / 100.0 * (poison.getAmplifier() + 1));
        if (loss <= 0) {
            return;
        }
        data.setHp(hp - loss);
        VitalsService.pushVanillaHealth(player, data);
        CombatTextS2C.send(player, loss, CombatTextS2C.Kind.TAKEN);
        player.serverLevel().sendParticles(SPORE, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.5, 0.3, 0.0);
    }
}
