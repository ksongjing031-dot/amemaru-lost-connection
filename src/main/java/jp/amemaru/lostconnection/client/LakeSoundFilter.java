package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 音が消える（11月25日。8階「沈黙の湖」）【補完】。
 *
 * <ul>
 *   <li>8階の湖の鐘を鳴らすまでは、階じゅうの効果音が消えている（Phase 0-A 9章の8階「足音も環境音も消えている」。
 *       {@link ClientTowerEnv#silent()}）</li>
 *   <li>ロスト状態「静寂」の間も、効果音と予兆の音が消える（13章の表）。強さ1（8階のボスの「静寂の帳」）は、予兆の印と
 *       パーティHUDも消える（{@link #deepSilence()}。9章の8階のボス）</li>
 *   <li>音楽（ボスの曲）と、ボイスチャットの声は消えない（声かけだけが頼り）</li>
 *   <li>8階のボスのレア「湖主の髭弓」（特殊効果 {@code warning_ear}）を持っていると、静寂の間も、予兆の音が自分にだけ聞こえる
 *       （20章。予兆が出た所から、鈴の音が鳴る）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class LakeSoundFilter {
    private static boolean wasMuted;

    private LakeSoundFilter() {
    }

    /** 予兆の音（髭弓を持つ人にだけ、静寂の間も聞こえる。消す決まりを通す印）。 */
    public static final class WarningSound extends SimpleSoundInstance {
        public WarningSound(double x, double y, double z, float pitch) {
            super(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 1.0F, pitch, RandomSource.create(), x, y, z);
        }
    }

    /** 今、効果音が消えているか。 */
    public static boolean muted() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        return ClientTowerEnv.silent() || minecraft.player.hasEffect(ModEffects.LOST_SILENCE.get());
    }

    /** ボスの「静寂の帳」（静寂の強さ1）：予兆の印とパーティHUDも消える。 */
    public static boolean deepSilence() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        MobEffectInstance silence = minecraft.player.getEffect(ModEffects.LOST_SILENCE.get());
        return silence != null && silence.getAmplifier() >= 1;
    }

    /** 予兆の音が聞こえる武器（湖主の髭弓）を持っているか、アクセサリー（真珠のお守り）を付けているか。 */
    public static boolean hearsWarnings() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        for (ItemStack stack : new ItemStack[]{minecraft.player.getMainHandItem(), minecraft.player.getOffhandItem()}) {
            if (stack.isEmpty()) {
                continue;
            }
            EquipmentDef def = EquipmentDefs.client().get(stack);
            if (def == null) {
                continue;
            }
            for (SpecialEffect effect : def.effects()) {
                if (effect.type() == SpecialEffect.Type.WARNING_EAR) {
                    return true;
                }
            }
        }
        // 11月27日：付けているアクセサリー（祈願の「真珠のお守り」）
        for (ItemStack stack : jp.amemaru.lostconnection.accessory.AccessoryService.equipped(minecraft.player)) {
            if (stack.getItem() instanceof jp.amemaru.lostconnection.accessory.AccessoryItem accessory && accessory.hearsWarnings()) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof WarningSound || !muted()) {
            return;
        }
        SoundSource source = sound.getSource();
        if (source == SoundSource.MUSIC || source == SoundSource.RECORDS || source == SoundSource.MASTER || source == SoundSource.VOICE) {
            return;
        }
        event.setSound(null);
    }

    /** 音が消えたとき、鳴っている効果音（風・環境の音など）も止める。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        boolean muted = muted();
        if (muted && !wasMuted) {
            Minecraft minecraft = Minecraft.getInstance();
            for (SoundSource source : new SoundSource[]{SoundSource.AMBIENT, SoundSource.WEATHER, SoundSource.BLOCKS, SoundSource.HOSTILE,
                    SoundSource.NEUTRAL, SoundSource.PLAYERS}) {
                minecraft.getSoundManager().stop(null, source);
            }
        }
        wasMuted = muted;
    }

    /** 敵が予兆を出した（クライアント）：音が消えていても、髭弓を持つ人には、予兆の所から鈴の音が聞こえる。 */
    public static void onTelegraph(Entity source) {
        if (!muted() || !hearsWarnings()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || source.distanceToSqr(minecraft.player) > 48.0 * 48.0) {
            return;
        }
        minecraft.getSoundManager().play(new WarningSound(source.getX(), source.getY() + 0.5, source.getZ(), 1.6F));
    }
}
