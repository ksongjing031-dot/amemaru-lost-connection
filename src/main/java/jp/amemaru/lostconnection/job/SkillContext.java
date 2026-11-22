package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.StatBlock;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * スキル1回分の情報。使った人のステータスとパッシブは、使った瞬間の写しを持つ（地面に残る陣や、遅れて落ちる流星が、
 * 使った人がログアウトした後でも同じ強さで効くようにするため）。
 */
public final class SkillContext {
    public final ServerLevel level;
    public final UUID casterId;
    public final int casterEntityId;
    public final SkillDef skill;
    public final int skillIndex;
    public final int rank;
    public final long startTick;
    private final StatBlock stats = new StatBlock();
    private final RandomSource random;
    /** 強化の倍率（ゲージの消費・連携支援・溜めなど）。ダメージ・障壁・MPの受け渡しにかかる。 */
    public double multiplier = 1.0;
    /** この1回で「連携支援」が効いたか。 */
    public boolean supported;
    /** 溜めの割合（0〜1。溜めないスキルは1）。 */
    public double chargeRatio = 1.0;
    /** 溜めきって放ったか。 */
    public boolean fullCharge;
    /** コストとして使ったゲージの量。 */
    public double gaugeSpent;
    /** ゲージを使って強める処理（旋風断・誓気解放）を済ませたか（弾が何発当たっても1回だけ）。 */
    boolean prepared;
    /** 感電した敵から始まった雷鎖など、強まった1回か（演出に使う）。 */
    public boolean empowered;
    /** 気配を、このスキルでもうためたか（1回のスキルで1回だけ。Phase 6 その2）。 */
    public boolean presenceCounted;
    /** 影分身の「もう一度斬る」のダメージか（影分身をくり返さないため）。 */
    public boolean echo;
    /** 必ず会心にするか（極めの一撃で、連撃が最大のとき）。 */
    public boolean forceCrit;
    /** いま与えているダメージが無視する防御の割合（%。ダメージの効果ごとに決める）。 */
    public double currentPierce;
    /** 連ね斬り：このスキルの倍率をもう決めたか（1回のスキルで1回だけ）。 */
    boolean bladeChecked;
    /** ゲージ・陣・使った人にかける効果を、もう出したか（弾が何発当たっても1回だけ）。 */
    boolean selfApplied;

    // 使った瞬間のパッシブ
    /** 砕きの心得：よろめき・防御崩しの敵へのダメージの上がり幅（%）。 */
    public double breakerBonus;
    /** 氷の心臓：凍え・凍結の敵へのダメージの上がり幅（%）。 */
    public double frostHeartBonus;
    /** 深冷：凍えの時間の倍率と、凍結が長くなる tick。 */
    public double chillDurationMultiplier = 1.0;
    public int freezeBonusTicks;
    /** 星の残光：星痕1つにつき戻るMP。 */
    public double afterglowMp;

    public SkillContext(ServerPlayer caster, StatBlock casterStats, SkillDef skill, int skillIndex, int rank) {
        this.level = caster.serverLevel();
        this.casterId = caster.getUUID();
        this.casterEntityId = caster.getId();
        this.skill = skill;
        this.skillIndex = skillIndex;
        this.rank = rank;
        this.startTick = level.getGameTime();
        this.stats.copyFrom(casterStats);
        this.random = caster.getRandom();
    }

    /** 使った人のパッシブを写す。 */
    public SkillContext loadPassives(PlayerData data) {
        PassiveService.Active breaker = PassiveService.find(data, PassiveDef.Type.BREAKER);
        breakerBonus = breaker == null ? 0.0 : breaker.value();
        PassiveService.Active frostHeart = PassiveService.find(data, PassiveDef.Type.FROST_HEART);
        frostHeartBonus = frostHeart == null ? 0.0 : frostHeart.value();
        PassiveService.Active deepChill = PassiveService.find(data, PassiveDef.Type.DEEP_CHILL);
        chillDurationMultiplier = deepChill == null ? 1.0 : PassiveService.DEEP_CHILL_DURATION;
        freezeBonusTicks = deepChill == null ? 0 : (int) Math.round(deepChill.value() * 20.0);
        PassiveService.Active afterglow = PassiveService.find(data, PassiveDef.Type.STAR_AFTERGLOW);
        afterglowMp = afterglow == null ? 0.0 : afterglow.value();
        return this;
    }

    /** パッシブで、この相手へのダメージにかかる倍率（砕きの心得・氷の心臓）。 */
    public double passiveDamageMultiplier(LivingEntity target) {
        double result = 1.0;
        if (breakerBonus > 0 && (StatusService.staggered(target) || StatusService.armorBroken(target))) {
            result *= 1.0 + breakerBonus / 100.0;
        }
        if (frostHeartBonus > 0 && StatusService.chilledOrFrozen(target)) {
            result *= 1.0 + frostHeartBonus / 100.0;
        }
        return result;
    }

    /** 使った人（ログアウトした・別の場所にいる・死んでいるときは null）。 */
    @Nullable
    public ServerPlayer caster() {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(casterId);
        if (player == null || player.level() != level || !player.isAlive()) {
            return null;
        }
        return player;
    }

    /** ダメージの持ち主（倒したときの記録に使う）。 */
    @Nullable
    public Entity owner() {
        return caster();
    }

    /** 使った瞬間のステータス（「守る者の誇り」「魔力の共鳴」「闘志」などの上がり幅も含む）。 */
    public double stat(StatType type) {
        return stats.get(type);
    }

    public RandomSource random() {
        return random;
    }
}
