package jp.amemaru.lostconnection.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 素早さのステータスの効き目（Phase 0-A 19章「素早さ：移動・攻撃の速さ、回避」）。数値は {@code growth} の
 * {@code speed_effects}【補完】。
 *
 * <ul>
 *   <li>移動の速さ：基礎値（レベル1の素早さ）を超えた1あたり +0.5%（最大 +25%）</li>
 *   <li>攻撃の速さ（近接攻撃の溜まり方）：1あたり +0.5%（最大 +25%）</li>
 *   <li>回避：敵の攻撃を、1あたり 0.2%（最大 10%）の確率でかわす（受けるダメージが0になる）</li>
 * </ul>
 *
 * <p>移動と攻撃の速さはバニラの属性（{@code MULTIPLY_BASE}）で足すので、走る・装備の移動の速さの効果とも重なる。</p>
 */
public final class SpeedService {
    private static final UUID MOVE_ID = UUID.fromString("5c1f7a39-8d3e-4b6a-a1f2-7e0b9c4d2a61");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("9a2e4c71-3b5d-4f08-b6c9-1d7e8f2a3c45");

    private SpeedService() {
    }

    /** 素早さの、基礎値を超えた分。 */
    public static double bonusPoints(PlayerData data, GrowthDef growth) {
        return Math.max(0.0, data.getStats().get(StatType.SPEED) - growth.base(StatType.SPEED));
    }

    /** 移動の速さの増え方（%）。 */
    public static double movePercent(PlayerData data, GrowthDef growth) {
        GrowthDef.SpeedEffects effects = growth.speedEffects();
        return Math.min(effects.moveCap(), bonusPoints(data, growth) * effects.movePerPoint());
    }

    /** 攻撃の速さの増え方（%）。 */
    public static double attackSpeedPercent(PlayerData data, GrowthDef growth) {
        GrowthDef.SpeedEffects effects = growth.speedEffects();
        return Math.min(effects.attackSpeedCap(), bonusPoints(data, growth) * effects.attackSpeedPerPoint());
    }

    /** 回避の確率（%）。 */
    public static double dodgeChance(PlayerData data, GrowthDef growth) {
        GrowthDef.SpeedEffects effects = growth.speedEffects();
        return Math.min(effects.dodgeCap(), bonusPoints(data, growth) * effects.dodgePerPoint());
    }

    /** ステータスを計算し直したときに、移動と攻撃の速さの属性を合わせる。 */
    public static void apply(ServerPlayer player, PlayerData data) {
        GrowthDef growth = GrowthRules.current();
        set(player, Attributes.MOVEMENT_SPEED, MOVE_ID, "amemaru:speed_move", movePercent(data, growth) / 100.0);
        set(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, "amemaru:speed_attack", attackSpeedPercent(data, growth) / 100.0);
    }

    /** 敵の攻撃をかわすか（サーバーで抽選）。 */
    public static boolean dodges(ServerPlayer player, PlayerData data) {
        // 身軽（ローグ）・柔の理（モンク）の分を足す（Phase 6 その2）
        double chance = dodgeChance(data, GrowthRules.current()) + jp.amemaru.lostconnection.job.PassiveService.dodgeBonus(data);
        return chance > 0 && player.getRandom().nextDouble() * 100.0 < chance;
    }

    private static void set(ServerPlayer player, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && Math.abs(current.getAmount() - amount) < 1.0E-9) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        if (amount > 0) {
            instance.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }
}
