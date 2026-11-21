package jp.amemaru.lostconnection.player;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * ダウン（蘇生待ち）の状態（Phase 4。Phase 0-A 20章「ダウンとデスペナルティ」、Phase 0-B 4.1「戦闘」）。
 *
 * <p>保存するのは「ダウン中か」だけ（{@link #leftWhileDown}：ダウン中に抜けたら、入り直したときに教会へ戻す）。
 * 残り時間・起こしている人・起こした量は保存しない。</p>
 */
public final class DownState {
    private boolean down;
    /** ダウンが終わる時刻（ゲーム内時刻。すべてのディメンションで共通） */
    private long until;
    /** ダウンの長さ（tick。画面の残りの割合に使う） */
    private int length;
    /** いま起こしている人と、たまった量・要る量（tick） */
    @Nullable
    private UUID reviver;
    private int progress;
    private int needed;
    /** 起き上がってすぐ、また倒れないようにする無敵の終わり（ゲーム内時刻） */
    private long invulnerableUntil = Long.MIN_VALUE / 4;
    /** ダウン中に抜けた（保存データから。入り直したら教会へ） */
    private boolean leftWhileDown;
    /** 奈落・/kill などでバニラの死亡になった（古い体から写す。リスポーンしたら教会へ） */
    private boolean diedForReal;
    /** まわりに最後に送った内容（変わったときと、ときどき送る） */
    private int lastSentProgress = -1;
    @Nullable
    private UUID lastSentReviver;

    public boolean isDown() {
        return down;
    }

    public void start(long now, int ticks) {
        down = true;
        until = now + ticks;
        length = ticks;
        resetRevive();
        lastSentProgress = -1;
        lastSentReviver = null;
    }

    /** ダウンを終える（起き上がった・教会へ戻った）。 */
    public void clear() {
        down = false;
        until = 0;
        length = 0;
        resetRevive();
        leftWhileDown = false;
        diedForReal = false;
        lastSentProgress = -1;
        lastSentReviver = null;
    }

    public long until() {
        return until;
    }

    public int length() {
        return length;
    }

    public int ticksLeft(long now) {
        return down ? (int) Math.max(0L, until - now) : 0;
    }

    @Nullable
    public UUID reviver() {
        return reviver;
    }

    public int progress() {
        return progress;
    }

    public int needed() {
        return needed;
    }

    /** 起こす人が変わった・はじめて来た：たまった量を0からにする。 */
    public void startRevive(UUID who, int neededTicks) {
        reviver = who;
        progress = 0;
        needed = Math.max(1, neededTicks);
    }

    /** 1 tick ぶん起こす。たまりきったら true。 */
    public boolean addProgress() {
        progress++;
        return progress >= needed;
    }

    /** 起こす手が離れた（しゃがむのをやめた・離れた）：たまった量は0に戻る。 */
    public void resetRevive() {
        reviver = null;
        progress = 0;
        needed = 0;
    }

    public void setInvulnerableUntil(long time) {
        invulnerableUntil = time;
    }

    public boolean invulnerable(long now) {
        return now < invulnerableUntil;
    }

    public boolean leftWhileDown() {
        return leftWhileDown;
    }

    public void setLeftWhileDown(boolean value) {
        leftWhileDown = value;
    }

    public boolean diedForReal() {
        return diedForReal;
    }

    public void setDiedForReal(boolean value) {
        diedForReal = value;
    }

    /** まわりに送り直す必要があるか（起こす人か、たまった量の1/10が変わった）。送ったことにする。 */
    public boolean consumeChanged() {
        int step = needed > 0 ? progress * 10 / needed : -1;
        boolean changed = step != lastSentProgress || (reviver == null ? lastSentReviver != null : !reviver.equals(lastSentReviver));
        lastSentProgress = step;
        lastSentReviver = reviver;
        return changed;
    }
}
