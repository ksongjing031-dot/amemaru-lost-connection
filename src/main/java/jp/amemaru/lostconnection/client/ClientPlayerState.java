package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.network.s2c.SyncPlayerDataS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.StatType;

/**
 * サーバーから受け取った、自分のデータの写し（クライアント専用）。HUDと画面はここを読む。
 *
 * <p>数値の計算はサーバーで済んでいるため、ここでは受け取った値をそのまま持つだけ。</p>
 */
public final class ClientPlayerState {
    private static final ClientPlayerState INSTANCE = new ClientPlayerState();

    private boolean growthReceived;
    private boolean statsReceived;
    private boolean vitalsReceived;

    private int level = 1;
    private int maxLevel = 1;
    private int exp;
    private int expToNext;
    private int statPoints;
    private int skillPoints;
    private final int[] allocated = new int[StatType.ALLOCATABLE_COUNT];
    private final double[] stats = new double[StatType.COUNT];
    private double hp;
    private double mp;

    private ClientPlayerState() {
    }

    public static ClientPlayerState get() {
        return INSTANCE;
    }

    /** 受け取ったまとまりだけを書きかえる。 */
    public void apply(SyncPlayerDataS2C message) {
        int sections = message.sections();
        if ((sections & PlayerData.SYNC_GROWTH) != 0) {
            level = message.level();
            maxLevel = message.maxLevel();
            exp = message.exp();
            expToNext = message.expToNext();
            statPoints = message.statPoints();
            skillPoints = message.skillPoints();
            int[] received = message.allocated();
            for (int i = 0; i < allocated.length; i++) {
                allocated[i] = i < received.length ? received[i] : 0;
            }
            growthReceived = true;
        }
        if ((sections & PlayerData.SYNC_STATS) != 0) {
            double[] received = message.stats();
            for (int i = 0; i < stats.length; i++) {
                stats[i] = i < received.length ? received[i] : 0;
            }
            statsReceived = true;
        }
        if ((sections & PlayerData.SYNC_VITALS) != 0) {
            hp = message.hp();
            mp = message.mp();
            vitalsReceived = true;
        }
    }

    /** ログアウトしたときに消す。 */
    public void reset() {
        growthReceived = false;
        statsReceived = false;
        vitalsReceived = false;
        level = 1;
        maxLevel = 1;
        exp = 0;
        expToNext = 0;
        statPoints = 0;
        skillPoints = 0;
        java.util.Arrays.fill(allocated, 0);
        java.util.Arrays.fill(stats, 0);
        hp = 0;
        mp = 0;
    }

    /** すべてのまとまりを一度は受け取ったか（このModのサーバーにつながっているか）。 */
    public boolean hasData() {
        return growthReceived && statsReceived && vitalsReceived;
    }

    public int level() {
        return level;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public boolean isMaxLevel() {
        return level >= maxLevel;
    }

    public int exp() {
        return exp;
    }

    public int expToNext() {
        return expToNext;
    }

    /** 次のレベルまでの進み具合（0〜1）。最大レベルでは1。 */
    public float expProgress() {
        if (isMaxLevel() || expToNext <= 0) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (float) exp / expToNext));
    }

    public int statPoints() {
        return statPoints;
    }

    public int skillPoints() {
        return skillPoints;
    }

    public int allocated(StatType type) {
        int index = type.allocIndex();
        return index < 0 ? 0 : allocated[index];
    }

    public double stat(StatType type) {
        return stats[type.ordinal()];
    }

    public double hp() {
        return hp;
    }

    public double maxHp() {
        return stat(StatType.HP);
    }

    public double mp() {
        return mp;
    }

    public double maxMp() {
        return stat(StatType.MP);
    }
}
