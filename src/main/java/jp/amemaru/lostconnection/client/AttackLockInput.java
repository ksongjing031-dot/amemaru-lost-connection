package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.job.AttackLockService;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 硬直（11月26日。クライアント専用）：足がほぼ止まる段階（3段階）の間は、ジャンプとダッシュをさせない。
 *
 * <p>足の遅さそのものは、状態の属性（移動の速さ −90%）で、サーバーとクライアントの両方に効く。ダッシュしながら跳ぶと、
 * 移動の速さに関係なく前へ跳べてしまうので、入力で止める。バニラは前へ進む入力が0.8以上のときにダッシュを始めるので、
 * 0.7までに抑える。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class AttackLockInput {
    private AttackLockInput() {
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        MobEffectInstance lock = player.getEffect(ModEffects.ATTACK_LOCK.get());
        if (lock == null || !AttackLockService.isFull(lock.getAmplifier())) {
            return;
        }
        Input input = event.getInput();
        input.jumping = false;
        input.forwardImpulse = Math.min(input.forwardImpulse, 0.7F);
        if (player.isSprinting()) {
            player.setSprinting(false);
        }
    }
}
