package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * CI 専用（11月29日）：「技を打った時に動けなくなるときに鳴る音がうるさい（多分、硬直の効果が消えるとき）」のご指摘を調べるため、
 * 硬直（{@link ModEffects#ATTACK_LOCK}）がかかってから消えて3秒たつまでの間に、プレイヤーのまわりで鳴った音を、ログに書く。
 * 書く形：{@code AMEMARU_SOUNDLOG|<ゲームの時刻>|<音>|<距離>|<種類>}・{@code AMEMARU_SOUNDLOG|<時刻>|lock on|<段階−1>:<残りtick>}。
 * CI の写真を撮るときだけ動く（{@code -Damemaru.ci.screenshots=true}）。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class CiSoundLog {
    private static final boolean ENABLED = Boolean.getBoolean("amemaru.ci.screenshots");
    private static final int AFTER_TICKS = 60;
    private static final int LIMIT = 6000;
    private static long logUntil = -1L;
    private static boolean wasLocked;
    private static int logged;

    private CiSoundLog() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        MobEffectInstance lock = player.getEffect(ModEffects.ATTACK_LOCK.get());
        boolean locked = lock != null;
        long now = minecraft.level.getGameTime();
        if (locked != wasLocked && logged < LIMIT) {
            AmemaruMod.LOGGER.info("AMEMARU_SOUNDLOG|{}|lock {}|{}", now, locked ? "on" : "off",
                    locked ? lock.getAmplifier() + ":" + lock.getDuration() : "");
            logged++;
        }
        wasLocked = locked;
        if (locked) {
            logUntil = now + AFTER_TICKS;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlaySound(PlaySoundEvent event) {
        if (!ENABLED || logged >= LIMIT) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        SoundInstance sound = event.getOriginalSound();
        if (player == null || minecraft.level == null || sound == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (now > logUntil) {
            return;
        }
        double distance = sound.isRelative() ? 0.0 : Math.sqrt(player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()));
        if (distance > 24.0) {
            return;
        }
        AmemaruMod.LOGGER.info("AMEMARU_SOUNDLOG|{}|{}|{}|{}", now, sound.getLocation(), String.format(Locale.ROOT, "%.1f", distance),
                sound.getSource().getName());
        logged++;
    }
}
