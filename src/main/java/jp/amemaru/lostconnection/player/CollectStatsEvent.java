package jp.amemaru.lostconnection.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * ステータスを計算するときに、装備・効果・称号などの上乗せを集めるイベント（Forgeのイベントバスに流す）。
 *
 * <p>計算式は「（基礎 ＋ レベルの伸び ＋ 振り分け ＋ {@link #addFlat}）×（1 ＋ {@link #addPercent} の合計 ÷ 100）」。
 * Phase 1では何も上乗せしない。Phase 2以降の装備・効果は、このイベントを受け取って値を足す。</p>
 */
public class CollectStatsEvent extends Event {
    @Nullable
    private final ServerPlayer player;
    @Nullable
    private final PlayerData data;
    private final double[] flat = new double[StatType.COUNT];
    private final double[] percent = new double[StatType.COUNT];

    /** イベントバスが内部で使うための空のコンストラクタ。 */
    public CollectStatsEvent() {
        this(null, null);
    }

    public CollectStatsEvent(@Nullable ServerPlayer player, @Nullable PlayerData data) {
        this.player = player;
        this.data = data;
    }

    /** 計算しているプレイヤー。 */
    @Nullable
    public ServerPlayer getPlayer() {
        return player;
    }

    /** 計算しているプレイヤーのデータ。 */
    @Nullable
    public PlayerData getData() {
        return data;
    }

    /** 値をそのまま足す（例：防御 +8）。 */
    public void addFlat(StatType type, double amount) {
        flat[type.ordinal()] += amount;
    }

    /** 割合で上げる（例：攻撃 +10%）。 */
    public void addPercent(StatType type, double amount) {
        percent[type.ordinal()] += amount;
    }

    public double flat(StatType type) {
        return flat[type.ordinal()];
    }

    public double percent(StatType type) {
        return percent[type.ordinal()];
    }
}
