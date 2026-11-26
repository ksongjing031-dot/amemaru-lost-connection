package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.job.HitHooks;
import jp.amemaru.lostconnection.job.PassiveService;
import jp.amemaru.lostconnection.job.ReactiveSkills;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Phase 6 その2で足した、ダメージの上がり下がり（職業の強化とパッシブ）をまとめて計算する。
 *
 * <p>与えるとき（プレイヤーの攻撃）：</p>
 * <ul>
 *   <li>相手の状態：標（+%）・蝕み（毒の敵 +%）・星の導き（星痕1つにつき +%）。</li>
 *   <li>自分：流派の極意（その流派のスキル +%）・背後の心得（背後から +%）・気配消し（潜んでいる間の一撃 +%）・
 *       遠矢（遠いほど +%）・連なる矢・疾風の弓舞・風読み（矢 +%）。</li>
 *   <li>会心：弱り目（毒の敵への会心 +）・影の歩法（移動の後の一撃の会心 +）・弱点看破（標・縫い止めの敵への会心ダメージ +%）。</li>
 * </ul>
 * <p>受けるとき（魔物の攻撃）：戒め（その魔物の攻撃 -%）・守り・鈴の守り（まわりのヒーラーのパッシブ）・揺るがぬ祈り（詠唱と溜めの間）・
 * 揺るがぬ盾（構えの間）。煙・影走りの間は、攻撃が外れることがある。</p>
 */
public final class CombatBonuses {
    /** 遠矢が効き始める距離。 */
    private static final double LONG_SHOT_FROM = 6.0;
    /** 背後とみなす角度（相手の向きと、相手から見た自分の向きの cos）。 */
    private static final double BEHIND_DOT = -0.3;

    /** 与えるダメージの上がり方。 */
    public record Offense(double multiplier, double critBonus, double critDamageBonus) {
        public static final Offense NONE = new Offense(1.0, 0.0, 0.0);
    }

    private CombatBonuses() {
    }

    /**
     * プレイヤーの攻撃の上がり方。
     *
     * @param skill スキル（通常の攻撃は null）
     * @param arrow 矢か（矢のスキルと、弓の通常の射撃）
     */
    public static Offense offense(ServerPlayer attacker, PlayerData data, LivingEntity target, @Nullable SkillDef skill, boolean arrow,
                                  long now) {
        double multiplier = 1.0;
        // 相手の状態（誰の攻撃にも効く）
        multiplier *= 1.0 + AfflictionService.markBonus(target) / 100.0;
        multiplier *= 1.0 + AfflictionService.corrodeBonus(target) / 100.0;
        multiplier *= 1.0 + AfflictionService.starGuideBonus(target) / 100.0;
        double thunder = PassiveService.value(data, PassiveDef.Type.THUNDER_MASTERY);
        if (thunder > 0 && StatusService.shocked(target)) {
            multiplier *= 1.0 + thunder / 100.0;
        }
        // 自分のパッシブと強化
        double schoolPower = PassiveService.schoolValue(data, PassiveDef.Type.SCHOOL_POWER, skill);
        if (schoolPower > 0) {
            multiplier *= 1.0 + schoolPower / 100.0;
        }
        double backstab = PassiveService.value(data, PassiveDef.Type.BACKSTAB_MASTERY);
        if (backstab > 0 && isBehind(attacker, target)) {
            multiplier *= 1.0 + backstab / 100.0;
        }
        BuffState buffs = data.getBuffs();
        // 気炎・魔力集中・鬨の声など（11月24日）：与えるダメージがすべて上がる
        multiplier *= 1.0 + buffs.powerUp(now) / 100.0;
        double fade = PassiveService.value(data, PassiveDef.Type.FADE);
        if (fade > 0 && buffs.stealthed(now)) {
            multiplier *= 1.0 + fade / 100.0;
        }
        if (arrow) {
            double longShot = PassiveService.value(data, PassiveDef.Type.LONG_SHOT);
            double distance = attacker.distanceTo(target);
            if (longShot > 0 && distance > LONG_SHOT_FROM) {
                multiplier *= 1.0 + Math.min(longShot, distance - LONG_SHOT_FROM) / 100.0;
            }
            multiplier *= 1.0 + HitHooks.arrowChainBonus(attacker, data, now) / 100.0;
            multiplier *= 1.0 + buffs.bowDance(now) / 100.0;
            if (skill != null) {
                multiplier *= 1.0 + PassiveService.value(data, PassiveDef.Type.WIND_READING) / 100.0;
            }
        }
        // 覚醒（11月27日）：与えるダメージ・スキル・締めの技・会心
        multiplier *= 1.0 + jp.amemaru.lostconnection.job.AwakeningService.value(data, "damage") / 100.0;
        if (skill != null) {
            multiplier *= 1.0 + jp.amemaru.lostconnection.job.AwakeningService.value(data, "skill_damage") / 100.0;
            if (skill.combo() == SkillDef.ComboRole.FINISHER || skill.link() == jp.amemaru.lostconnection.job.def.LinkType.FINISHER) {
                multiplier *= 1.0 + jp.amemaru.lostconnection.job.AwakeningService.value(data, "finisher_damage") / 100.0;
            }
        }
        double critBonus = jp.amemaru.lostconnection.job.AwakeningService.value(data, "crit");
        double critDamageBonus = jp.amemaru.lostconnection.job.AwakeningService.value(data, "crit_damage");
        if (AfflictionService.poisoned(target)) {
            critBonus += PassiveService.value(data, PassiveDef.Type.EXPLOIT);
        }
        critBonus += HitHooks.consumeShadowStep(attacker, data, now);
        if (AfflictionService.marked(target) || AfflictionService.rooted(target)) {
            critDamageBonus += PassiveService.value(data, PassiveDef.Type.WEAK_POINT);
        }
        return new Offense(multiplier, critBonus, critDamageBonus);
    }

    /** 攻撃した人が、相手の背後にいるか。 */
    public static boolean isBehind(Entity attacker, LivingEntity target) {
        Vec3 look = target.getViewVector(1.0F);
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        Vec3 to = new Vec3(attacker.getX() - target.getX(), 0.0, attacker.getZ() - target.getZ());
        if (flatLook.lengthSqr() < 1.0E-6 || to.lengthSqr() < 1.0E-6) {
            return false;
        }
        return flatLook.normalize().dot(to.normalize()) < BEHIND_DOT;
    }

    /**
     * 魔物の攻撃が外れるか（煙・影走り）。外れたら true。
     */
    public static boolean misses(ServerPlayer player, PlayerData data, LivingEntity attacker, long now) {
        if (data.getBuffs().evading(now)) {
            return true;
        }
        double miss = AfflictionService.missChance(attacker);
        return miss > 0 && player.getRandom().nextDouble() * 100.0 < miss;
    }

    /**
     * プレイヤーが魔物から受けるダメージの倍率（戒め・守り・鈴の守り・揺るがぬ祈り・揺るがぬ盾）。
     */
    public static double incoming(ServerPlayer player, PlayerData data, @Nullable Entity attacker, long now) {
        double result = 1.0;
        if (attacker instanceof LivingEntity living) {
            result *= 1.0 - Math.min(95.0, AfflictionService.weakness(living)) / 100.0;
        }
        BuffState buffs = data.getBuffs();
        result *= 1.0 - Math.min(95.0, buffs.protect(now)) / 100.0;
        double bellGuard = bellGuard(player);
        if (bellGuard > 0) {
            result *= 1.0 - Math.min(95.0, bellGuard) / 100.0;
        }
        if (data.getCasting() != null) {
            double steady = PassiveService.value(data, PassiveDef.Type.STEADY_PRAYER);
            result *= 1.0 - Math.min(95.0, steady) / 100.0;
        }
        double steadfast = PassiveService.value(data, PassiveDef.Type.STEADFAST_SHIELD);
        if (steadfast > 0 && (buffs.rampart(now) || buffs.warded(now) || ReactiveSkills.parrying(player, now))) {
            result *= 1.0 - Math.min(95.0, steadfast) / 100.0;
        }
        // 覚醒（11月27日）：受けるダメージ
        double taken = jp.amemaru.lostconnection.job.AwakeningService.value(data, "taken");
        if (taken > 0) {
            result *= 1.0 - Math.min(95.0, taken) / 100.0;
        }
        return result;
    }

    /** 鈴の守り：まわりにいるヒーラーのパッシブのうち、いちばん強いもの（%）。 */
    private static double bellGuard(ServerPlayer player) {
        double best = 0.0;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (!other.isAlive() || other.isSpectator() || DownHandler.isDown(other)) {
                continue;
            }
            PlayerData data = PlayerDataCapability.get(other).orElse(null);
            PassiveService.Active guard = data == null ? null : PassiveService.find(data, PassiveDef.Type.BELL_GUARD);
            if (guard != null && other.distanceToSqr(player) <= guard.radius() * guard.radius()) {
                best = Math.max(best, guard.value());
            }
        }
        return best;
    }
}
