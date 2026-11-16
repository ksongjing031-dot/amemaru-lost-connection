package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.nbt.CompoundTag;

/**
 * プレイヤーごとのデータ（Capability。Phase 0-B 4.1）。
 *
 * <p>Phase 1で持つのは「成長」（レベル・経験値・振り分けたステータス）と「戦闘」のうちHPとMPだけ。
 * 職業・スキル・所持金・物語などのまとまりは、使い始めるPhaseで足し、{@link #DATA_VERSION} を上げて
 * 古い保存データを読み替える。</p>
 *
 * <p>ステータスポイントとスキルポイントの残りは保存せず、レベルと振り分けた量から毎回求める
 * （定義の数値を変えても、ずれが起きないようにするため）。</p>
 */
public final class PlayerData {
    /** 保存データの版。項目を増やしたら上げ、{@link #load} で古い版を読み替える。 */
    public static final int DATA_VERSION = 1;

    /** 同期するまとまり：レベル・経験値・ポイント・振り分け。 */
    public static final int SYNC_GROWTH = 1;
    /** 同期するまとまり：計算済みのステータス。 */
    public static final int SYNC_STATS = 1 << 1;
    /** 同期するまとまり：今のHPとMP。 */
    public static final int SYNC_VITALS = 1 << 2;
    /** すべてのまとまり。 */
    public static final int SYNC_ALL = SYNC_GROWTH | SYNC_STATS | SYNC_VITALS;

    // ---- 保存する項目
    private int level = 1;
    private int exp;
    private final int[] allocated = new int[StatType.ALLOCATABLE_COUNT];
    /** 今のHP。負の値は「まだ決めていない」（初めてのログイン）。 */
    private double hp = -1;
    /** 今のMP。負の値は「まだ決めていない」。 */
    private double mp = -1;

    // ---- 保存しない項目
    private final StatBlock stats = new StatBlock();
    private boolean statsReady;
    private long lastCombatTick = Long.MIN_VALUE / 4;
    private float expectedVanillaHealth = -1.0F;
    private int dirty = SYNC_ALL;

    // ---------------------------------------------------------------- 成長

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        int next = Math.max(1, level);
        if (next != this.level) {
            this.level = next;
            dirty |= SYNC_GROWTH;
        }
    }

    /** 今のレベルの中でたまっている経験値。 */
    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        int next = Math.max(0, exp);
        if (next != this.exp) {
            this.exp = next;
            dirty |= SYNC_GROWTH;
        }
    }

    /** 振り分けたポイントの数。振り分けられない種類は 0。 */
    public int getAllocated(StatType type) {
        int index = type.allocIndex();
        return index < 0 ? 0 : allocated[index];
    }

    public void setAllocated(StatType type, int points) {
        int index = type.allocIndex();
        if (index < 0) {
            return;
        }
        int next = Math.max(0, points);
        if (next != allocated[index]) {
            allocated[index] = next;
            dirty |= SYNC_GROWTH;
        }
    }

    /** 振り分けたポイントの合計。 */
    public int totalAllocated() {
        int total = 0;
        for (int points : allocated) {
            total += points;
        }
        return total;
    }

    /** 振り分けをすべて戻す。 */
    public void clearAllocated() {
        for (int i = 0; i < allocated.length; i++) {
            if (allocated[i] != 0) {
                allocated[i] = 0;
                dirty |= SYNC_GROWTH;
            }
        }
    }

    // ---------------------------------------------------------------- ステータス

    /** 計算済みのステータス（{@link #isStatsReady()} が false の間は 0）。 */
    public StatBlock getStats() {
        return stats;
    }

    public boolean isStatsReady() {
        return statsReady;
    }

    /**
     * 計算し直したステータスを入れる。最大HP・最大MPが増えたときは今の値も同じだけ増やし、
     * 減ったときは新しい最大値に収める。
     */
    public void applyStats(StatBlock block) {
        boolean changed = !statsReady || !stats.sameAs(block);
        double oldMaxHp = statsReady ? stats.maxHp() : -1;
        double oldMaxMp = statsReady ? stats.maxMp() : -1;
        stats.copyFrom(block);
        statsReady = true;

        if (hp >= 0) {
            double next = hp;
            if (oldMaxHp > 0 && block.maxHp() > oldMaxHp) {
                next += block.maxHp() - oldMaxHp;
            }
            setHp(next);
        }
        if (mp >= 0) {
            double next = mp;
            if (oldMaxMp > 0 && block.maxMp() > oldMaxMp) {
                next += block.maxMp() - oldMaxMp;
            }
            setMp(next);
        }
        if (changed) {
            dirty |= SYNC_STATS | SYNC_VITALS;
        }
    }

    /** ステータスを計算し直すよう印をつける（次の tick で計算される）。 */
    public void invalidateStats() {
        statsReady = false;
    }

    // ---------------------------------------------------------------- HPとMP

    /** HPとMPが決まっているか（初めてのログインでは false）。 */
    public boolean vitalsInitialized() {
        return hp >= 0 && mp >= 0;
    }

    public double getHp() {
        return Math.max(0, hp);
    }

    /** HPを入れる（0〜最大HPに収める）。 */
    public void setHp(double value) {
        double max = statsReady ? stats.maxHp() : Double.MAX_VALUE;
        double next = Math.min(Math.max(0, value), max);
        if (next != hp) {
            hp = next;
            dirty |= SYNC_VITALS;
        }
    }

    public double getMp() {
        return Math.max(0, mp);
    }

    /** MPを入れる（0〜最大MPに収める）。 */
    public void setMp(double value) {
        double max = statsReady ? stats.maxMp() : Double.MAX_VALUE;
        double next = Math.min(Math.max(0, value), max);
        if (next != mp) {
            mp = next;
            dirty |= SYNC_VITALS;
        }
    }

    /** 最後に攻撃した・攻撃を受けたときの時刻（ゲーム内の tick）。 */
    public long getLastCombatTick() {
        return lastCombatTick;
    }

    public void markCombat(long gameTime) {
        lastCombatTick = gameTime;
    }

    /** 最後にこちらで決めたバニラの体力。外から変わったかを見分けるために使う。 */
    public float getExpectedVanillaHealth() {
        return expectedVanillaHealth;
    }

    public void setExpectedVanillaHealth(float value) {
        expectedVanillaHealth = value;
    }

    // ---------------------------------------------------------------- 同期の印

    public void markDirty(int sections) {
        dirty |= sections;
    }

    /** 同期が必要なまとまりを返し、印を消す。 */
    public int consumeDirty() {
        int value = dirty;
        dirty = 0;
        return value;
    }

    /** 保存しない項目を初めの状態に戻す（リスポーンの後など）。 */
    public void resetTransient() {
        statsReady = false;
        lastCombatTick = Long.MIN_VALUE / 4;
        expectedVanillaHealth = -1.0F;
        dirty = SYNC_ALL;
    }

    // ---------------------------------------------------------------- 保存

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", DATA_VERSION);

        CompoundTag growth = new CompoundTag();
        growth.putInt("level", level);
        growth.putInt("exp", exp);
        CompoundTag alloc = new CompoundTag();
        for (StatType type : StatType.allocatable()) {
            alloc.putInt(type.id(), allocated[type.allocIndex()]);
        }
        growth.put("allocated", alloc);
        tag.put("growth", growth);

        CompoundTag vitals = new CompoundTag();
        vitals.putDouble("hp", hp);
        vitals.putDouble("mp", mp);
        tag.put("vitals", vitals);
        return tag;
    }

    public void load(CompoundTag tag) {
        int version = tag.getInt("version");
        if (version > DATA_VERSION) {
            AmemaruMod.LOGGER.warn("[amemaru] Player data version {} is newer than this mod ({}). Unknown fields are ignored.",
                    version, DATA_VERSION);
        }
        // 版1が最初の版のため、読み替えはまだない。版2以降はここで古い版を読み替える。

        CompoundTag growth = tag.getCompound("growth");
        level = growth.contains("level") ? Math.max(1, growth.getInt("level")) : 1;
        exp = Math.max(0, growth.getInt("exp"));
        CompoundTag alloc = growth.getCompound("allocated");
        for (StatType type : StatType.allocatable()) {
            allocated[type.allocIndex()] = Math.max(0, alloc.getInt(type.id()));
        }

        CompoundTag vitals = tag.getCompound("vitals");
        hp = vitals.contains("hp") ? vitals.getDouble("hp") : -1;
        mp = vitals.contains("mp") ? vitals.getDouble("mp") : -1;

        resetTransient();
    }

    /** 死亡・リスポーン・エンドからの帰還のとき、古い体のデータを写す。 */
    public void copyFrom(PlayerData other) {
        level = other.level;
        exp = other.exp;
        System.arraycopy(other.allocated, 0, allocated, 0, allocated.length);
        hp = other.hp;
        mp = other.mp;
        resetTransient();
    }
}
