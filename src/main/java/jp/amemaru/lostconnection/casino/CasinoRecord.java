package jp.amemaru.lostconnection.casino;

import net.minecraft.nbt.CompoundTag;

/**
 * プレイヤーごとのカジノの記録（Phase 7 その3）：持っているチップ・今日買ったチップ（日は毎日4時に切り替わる）・ルーレットで
 * 続けて外した回数（称号「運命の女神に笑われた」）・勝負の数と勝った数・いちばん大きな勝ち。
 */
public final class CasinoRecord {
    private long chips;
    private String boughtDay = "";
    private long bought;
    private int loseStreak;
    private int rounds;
    private int wins;
    private long bestWin;

    public long chips() {
        return chips;
    }

    public void setChips(long value) {
        chips = Math.max(0, value);
    }

    public void addChips(long delta) {
        chips = Math.max(0, chips + delta);
    }

    /** 今日（day）に買ったチップ。 */
    public long boughtOn(String day) {
        return day.equals(boughtDay) ? bought : 0;
    }

    public void addBought(String day, long amount) {
        if (!day.equals(boughtDay)) {
            boughtDay = day;
            bought = 0;
        }
        bought += amount;
    }

    public int loseStreak() {
        return loseStreak;
    }

    public void setLoseStreak(int value) {
        loseStreak = Math.max(0, value);
    }

    public int rounds() {
        return rounds;
    }

    public int wins() {
        return wins;
    }

    public long bestWin() {
        return bestWin;
    }

    /** 1回の勝負を書く（net：払い戻しから賭けた額を引いた数）。 */
    public void recordRound(long net) {
        rounds++;
        if (net > 0) {
            wins++;
            bestWin = Math.max(bestWin, net);
        }
    }

    public void clear() {
        chips = 0;
        boughtDay = "";
        bought = 0;
        loseStreak = 0;
        rounds = 0;
        wins = 0;
        bestWin = 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("chips", chips);
        tag.putString("boughtDay", boughtDay);
        tag.putLong("bought", bought);
        tag.putInt("loseStreak", loseStreak);
        tag.putInt("rounds", rounds);
        tag.putInt("wins", wins);
        tag.putLong("bestWin", bestWin);
        return tag;
    }

    public void load(CompoundTag tag) {
        chips = Math.max(0, tag.getLong("chips"));
        boughtDay = tag.getString("boughtDay");
        bought = tag.getLong("bought");
        loseStreak = tag.getInt("loseStreak");
        rounds = tag.getInt("rounds");
        wins = tag.getInt("wins");
        bestWin = tag.getLong("bestWin");
    }

    public void copyFrom(CasinoRecord other) {
        load(other.save());
    }
}
