package jp.amemaru.lostconnection.combat;

import net.minecraft.util.RandomSource;

/**
 * ダメージの計算（すべてサーバーで行う。Phase 0-B 2章）。
 *
 * <pre>
 * ダメージ ＝ 威力（ステータス × 係数 × 強化の倍率）× ばらつき × 会心 × 100 ÷（100 ＋ 相手の防御）
 * </pre>
 *
 * <ul>
 *   <li>ばらつき：configの damageVariance（初期値 ±5%）【補完】</li>
 *   <li>会心：会心（%）の確率で、×（1 ＋ 会心ダメージ ÷ 100）【補完】</li>
 *   <li>防御：防御100で半分になる。防御が上がるほど効き目はゆるやかになる【補完】</li>
 * </ul>
 */
public final class DamageCalculator {
    /** 計算の結果。 */
    public record Result(double amount, boolean crit) {
    }

    private DamageCalculator() {
    }

    /**
     * @param power      威力（ステータス × 係数 × 強化の倍率）
     * @param critChance 会心の確率（%）
     * @param critDamage 会心ダメージ（%）
     * @param defense    相手の防御
     * @param variance   ばらつき（%）
     */
    public static Result compute(double power, double critChance, double critDamage, double defense, double variance,
                                 RandomSource random) {
        if (power <= 0) {
            return new Result(0, false);
        }
        double spread = variance <= 0 ? 1.0 : 1.0 + (random.nextDouble() * 2.0 - 1.0) * variance / 100.0;
        boolean crit = random.nextDouble() * 100.0 < Math.max(0.0, Math.min(100.0, critChance));
        double critMultiplier = crit ? 1.0 + Math.max(0.0, critDamage) / 100.0 : 1.0;
        double amount = power * spread * critMultiplier * mitigation(defense);
        return new Result(Math.max(1.0, Math.round(amount)), crit);
    }

    /** 防御による軽減の倍率（防御0で1、防御100で0.5）。 */
    public static double mitigation(double defense) {
        return 100.0 / (100.0 + Math.max(0.0, defense));
    }
}
