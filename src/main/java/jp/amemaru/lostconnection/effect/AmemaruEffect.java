package jp.amemaru.lostconnection.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * このModの状態（よろめき・凍え・城壁の構えなど）。動きの遅さなどはバニラの属性の仕組みで、
 * ダメージの増減などの数値はサーバーのプレイヤーデータ（{@code BuffState}）で扱う。
 *
 * <p>「よろめき」の間は、魔物の移動の予定を毎 tick 消して、足を止めさせる。</p>
 */
public class AmemaruEffect extends MobEffect {
    private final boolean stopsMovement;

    public AmemaruEffect(MobEffectCategory category, int color, boolean stopsMovement) {
        super(category, color);
        this.stopsMovement = stopsMovement;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (stopsMovement && entity instanceof Mob mob && !mob.level().isClientSide()) {
            mob.getNavigation().stop();
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return stopsMovement;
    }
}
