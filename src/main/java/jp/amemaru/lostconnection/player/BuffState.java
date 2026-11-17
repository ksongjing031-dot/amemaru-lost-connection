package jp.amemaru.lostconnection.player;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * スキルでかかっている、数値つきの強化（保存しない）。画面のアイコンと残り時間はバニラの効果（MobEffect）で出し、
 * 強さ（何%減らすかなど）はここに持つ。時刻はゲーム内の tick。
 */
public final class BuffState {
    /** 城壁の構え：正面からのダメージを減らす割合（%）。 */
    public long rampartUntil;
    public double rampartReduction;
    /** 不屈の誓い：この時刻まで、HPが1より下がらない。 */
    public long unyieldingUntil;
    /** 不落の城：受けるダメージを減らす割合（%）。 */
    public long fortressUntil;
    public double fortressReduction;
    /** 盾結び：守ってくれているナイトと、肩代わりする割合（%）。 */
    @Nullable
    public UUID bondGiver;
    public long bondUntil;
    public double bondShare;
    /** 誓いの円陣：陣の中で受けるダメージを減らす割合（%）。 */
    public long wardUntil;
    public double wardReduction;
    /** 光の帳：残っている障壁の量（独自のHPと同じ単位）と、張ったときの量。 */
    public long barrierUntil;
    public double barrierAmount;
    public double barrierMax;
    /** 連携支援：支援してくれた人と、次のスキルの効果の上がり幅（%）。 */
    @Nullable
    public UUID supportFrom;
    public long supportUntil;
    public double supportBonus;

    public boolean rampart(long now) {
        return now < rampartUntil;
    }

    public boolean unyielding(long now) {
        return now < unyieldingUntil;
    }

    public boolean fortress(long now) {
        return now < fortressUntil;
    }

    public boolean warded(long now) {
        return now < wardUntil;
    }

    /** 障壁が残っているか。 */
    public boolean barrier(long now) {
        return now < barrierUntil && barrierAmount > 0;
    }

    /** 障壁を消す。 */
    public void clearBarrier() {
        barrierUntil = 0;
        barrierAmount = 0;
        barrierMax = 0;
    }

    public boolean bonded(long now) {
        return bondGiver != null && now < bondUntil;
    }

    public boolean supported(long now) {
        return supportFrom != null && now < supportUntil;
    }

    /** 連携支援を使い切る。 */
    public void consumeSupport() {
        supportFrom = null;
        supportUntil = 0;
        supportBonus = 0;
    }

    public void clear() {
        rampartUntil = 0;
        rampartReduction = 0;
        unyieldingUntil = 0;
        fortressUntil = 0;
        fortressReduction = 0;
        bondGiver = null;
        bondUntil = 0;
        bondShare = 0;
        wardUntil = 0;
        wardReduction = 0;
        clearBarrier();
        consumeSupport();
    }
}
