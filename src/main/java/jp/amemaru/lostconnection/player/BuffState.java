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
    /** 祝福の盾：障壁が消えるときに癒す量（0ならなし）と、張った人。 */
    public double barrierHealOnEnd;
    @Nullable
    public UUID barrierGiver;
    /** 連携支援：支援してくれた人と、次のスキルの効果の上がり幅（%）。 */
    @Nullable
    public UUID supportFrom;
    public long supportUntil;
    public double supportBonus;

    // ---------------------------------------------------------------- Phase 6 その2
    /** 加護の灯：防御の上がり幅（%）。 */
    public long defenseUpUntil;
    public double defenseUp;
    /** 守り（結びの聖域・清めの心得・守り手の心）：受けるダメージを減らす割合（%）。 */
    public long protectUntil;
    public double protect;
    /** 鷹の眼差し・気功：会心の上がり幅。 */
    public long critUpUntil;
    public double critUp;
    /** 足の速さの上がり幅（%）。 */
    public long hasteUntil;
    public double haste;
    /** 次のスキルのダメージの上がり幅（%。百烈・結）。 */
    public long empowerUntil;
    public double empower;
    /** 狙撃の構え：残りの矢の数と、上がり幅（%）。 */
    public long aimUntil;
    public int aimShots;
    public double aimBonus;
    /** 命の糸：つないだ人、毎秒の回復量、一度だけの大きな回復の量、その境目（HPの%）。 */
    @Nullable
    public UUID threadGiver;
    public long threadUntil;
    public double threadPerSecond;
    public double threadBurst;
    public double threadThreshold;
    /** 身代わりの鈴：倒れるほどの攻撃を一度だけ防ぎ、HPをこの割合（%）まで戻す。 */
    public long guardianUntil;
    public double guardianHp;
    /** 星の環：残りの星の数と、1つのダメージ（使った瞬間の魔力×係数）。 */
    public long starRingUntil;
    public int starRingCount;
    public double starRingPower;
    /** 影分身：スキルが当たるたびに、ダメージの何%でもう一度斬るか。 */
    public long cloneUntil;
    public double clonePercent;
    /** 毒塗り：攻撃が当たるたびに重ねる毒の、1秒あたりのダメージ（使った瞬間の攻撃×係数）。 */
    public long venomUntil;
    public double venomPower;
    /** 疾風の弓舞：上がり幅（%）。 */
    public long bowDanceUntil;
    public double bowDance;
    /** 影走り：敵の攻撃をすり抜ける。 */
    public long evadeUntil;
    /** 天地流転：【極】の技を使っても連撃が消えない。 */
    public long endlessFlowUntil;
    /** 回復し続ける（余韻・満ちる鈴・癒しの雨は陣）：1秒あたりの量と、かけた人。 */
    public long regenUntil;
    public double regenPerSecond;
    @Nullable
    public UUID regenGiver;
    /** 潜伏（煙玉・気配消し）：この時刻まで、敵に見失われる。 */
    public long stealthUntil;

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
        barrierHealOnEnd = 0;
        barrierGiver = null;
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

    public double defenseUp(long now) {
        return now < defenseUpUntil ? defenseUp : 0.0;
    }

    public double protect(long now) {
        return now < protectUntil ? protect : 0.0;
    }

    public double critUp(long now) {
        return now < critUpUntil ? critUp : 0.0;
    }

    public double haste(long now) {
        return now < hasteUntil ? haste : 0.0;
    }

    public double empower(long now) {
        return now < empowerUntil ? empower : 0.0;
    }

    public boolean aiming(long now) {
        return now < aimUntil && aimShots > 0;
    }

    public boolean threaded(long now) {
        return threadGiver != null && now < threadUntil;
    }

    public boolean guarded(long now) {
        return now < guardianUntil;
    }

    public boolean starRing(long now) {
        return now < starRingUntil && starRingCount > 0;
    }

    public boolean cloned(long now) {
        return now < cloneUntil;
    }

    public boolean venom(long now) {
        return now < venomUntil;
    }

    public double bowDance(long now) {
        return now < bowDanceUntil ? bowDance : 0.0;
    }

    public boolean evading(long now) {
        return now < evadeUntil;
    }

    public boolean endlessFlow(long now) {
        return now < endlessFlowUntil;
    }

    public boolean regenerating(long now) {
        return now < regenUntil && regenPerSecond > 0;
    }

    public boolean stealthed(long now) {
        return now < stealthUntil;
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
        defenseUpUntil = 0;
        defenseUp = 0;
        protectUntil = 0;
        protect = 0;
        critUpUntil = 0;
        critUp = 0;
        hasteUntil = 0;
        haste = 0;
        empowerUntil = 0;
        empower = 0;
        aimUntil = 0;
        aimShots = 0;
        aimBonus = 0;
        threadGiver = null;
        threadUntil = 0;
        threadPerSecond = 0;
        threadBurst = 0;
        threadThreshold = 0;
        guardianUntil = 0;
        guardianHp = 0;
        starRingUntil = 0;
        starRingCount = 0;
        starRingPower = 0;
        cloneUntil = 0;
        clonePercent = 0;
        venomUntil = 0;
        venomPower = 0;
        bowDanceUntil = 0;
        bowDance = 0;
        evadeUntil = 0;
        endlessFlowUntil = 0;
        regenUntil = 0;
        regenPerSecond = 0;
        regenGiver = null;
        stealthUntil = 0;
    }
}
