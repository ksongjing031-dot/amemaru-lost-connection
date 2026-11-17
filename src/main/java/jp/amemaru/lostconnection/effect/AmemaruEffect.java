package jp.amemaru.lostconnection.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;

/**
 * このModの状態（よろめき・凍え・城壁の構えなど）。動きの遅さなどはバニラの属性の仕組みで、
 * ダメージの増減などの数値はサーバーのプレイヤーデータ（{@code BuffState}）や {@code StatusService} で扱う。
 *
 * <p>「よろめき」「凍結」の間は、魔物の移動の予定を毎 tick 消して、足を止めさせる。「凍結」の間は、クリーパーの
 * 爆発の準備も止める（攻撃そのものは {@code CombatService} で止める）。</p>
 */
public class AmemaruEffect extends MobEffect {
    private final boolean stopsMovement;
    private boolean freezes;

    public AmemaruEffect(MobEffectCategory category, int color, boolean stopsMovement) {
        super(category, color);
        this.stopsMovement = stopsMovement;
    }

    /** 凍結のように、体ごと止める状態にする。 */
    public AmemaruEffect freezing() {
        this.freezes = true;
        return this;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!stopsMovement || !(entity instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        mob.getNavigation().stop();
        if (freezes) {
            if (mob instanceof Creeper creeper) {
                creeper.setSwellDir(-1);
            }
            // 歩く勢いも止める（落ちる・吹き飛ぶ動きは残す）
            mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.2, 1.0, 0.2));
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return stopsMovement;
    }
}
