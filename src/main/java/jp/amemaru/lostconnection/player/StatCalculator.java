package jp.amemaru.lostconnection.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/**
 * ステータスの計算（基礎＋振り分け＋装備＋効果。Phase 0-B 13章）。
 *
 * <ul>
 *   <li>基礎：成長の定義の {@code base} ＋ {@code per_level} ×（レベル−1）</li>
 *   <li>振り分け：{@code per_point} × 振り分けたポイント</li>
 *   <li>装備と効果：{@link CollectStatsEvent} で集めた上乗せ（Phase 2以降）</li>
 * </ul>
 */
public final class StatCalculator {
    private StatCalculator() {
    }

    public static StatBlock compute(ServerPlayer player, PlayerData data, GrowthDef growth) {
        CollectStatsEvent bonus = new CollectStatsEvent(player, data);
        MinecraftForge.EVENT_BUS.post(bonus);

        int levelsGained = Math.max(0, data.getLevel() - 1);
        StatBlock block = new StatBlock();
        for (StatType type : StatType.all()) {
            double value = growth.base(type) + growth.perLevel(type) * levelsGained;
            if (type.isAllocatable()) {
                value += growth.perPoint(type) * data.getAllocated(type);
            }
            value += bonus.flat(type);
            value *= 1.0 + bonus.percent(type) / 100.0;
            block.set(type, normalize(type, value));
        }
        return block;
    }

    private static double normalize(StatType type, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            value = 0;
        }
        value = Math.max(0, value);
        if (type.isPercent()) {
            return Math.round(value * 100.0) / 100.0;
        }
        value = Math.floor(value);
        if (type == StatType.HP) {
            return Math.max(1, value);
        }
        return value;
    }
}
