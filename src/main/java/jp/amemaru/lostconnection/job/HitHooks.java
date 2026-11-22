package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.combat.AfflictionService;
import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.combat.CombatTargets;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーの攻撃が敵に当たった後の処理（Phase 6 その2）。
 *
 * <ul>
 *   <li>専用ゲージ：集中・気配・気がたまる（{@link GaugeService#onHit}）。</li>
 *   <li>毒塗り：毒が1つ重なる。戒めの響き：その敵の攻撃が弱まる。鈴の余韻：いちばん傷ついた仲間が癒える。</li>
 *   <li>影分身：スキルが当たると、少し遅れて影がもう一度斬る。</li>
 *   <li>連なる矢：矢が当たるたびに、次の矢のダメージが上がる。</li>
 *   <li>気配消し：攻撃すると潜伏が解け、また3秒数え直す。影の歩法：移動の技の後、次の一撃の会心が上がる。</li>
 * </ul>
 */
public final class HitHooks {
    /** 連なる矢：続けて当てる時間と、上限。 */
    private static final int ARROW_CHAIN_WINDOW = 40;
    private static final int ARROW_CHAIN_MAX = 10;
    /** 影の歩法：移動の技の後、会心が上がる時間。 */
    private static final int SHADOW_STEP_TICKS = 60;
    /** 戒めの響きの時間。 */
    private static final int WEAKEN_ECHO_TICKS = 80;
    /** 影分身の、もう一度斬るまでの遅れ。 */
    private static final int CLONE_DELAY = 6;

    private static final class ArrowChain {
        int stacks;
        long lastTick;
    }

    private static final Map<UUID, Long> LAST_ATTACK = new HashMap<>();
    private static final Map<UUID, Long> SHADOW_STEP = new HashMap<>();
    private static final Map<UUID, ArrowChain> ARROW_CHAINS = new HashMap<>();

    private HitHooks() {
    }

    /** 最後に攻撃した時刻（気配消し）。攻撃していなければ0。 */
    public static long lastAttack(UUID player) {
        Long last = LAST_ATTACK.get(player);
        return last == null ? 0L : last;
    }

    /**
     * 攻撃が当たった後。
     *
     * @param ctx   スキルのとき（通常の攻撃は null）
     * @param arrow 矢か（矢のスキルと、弓の通常の射撃）
     */
    public static void afterHit(ServerPlayer attacker, PlayerData data, LivingEntity target, double dealt, @Nullable SkillContext ctx,
                                boolean arrow, long now) {
        LAST_ATTACK.put(attacker.getUUID(), now);
        if (data.getBuffs().stealthed(now) && PassiveService.value(data, PassiveDef.Type.FADE) > 0) {
            StealthService.reveal(attacker, data);
        }
        boolean countPresence = ctx == null || !ctx.presenceCounted;
        if (ctx != null) {
            ctx.presenceCounted = true;
        }
        GaugeService.onHit(attacker, data, now, countPresence);
        BuffState buffs = data.getBuffs();
        if (buffs.venom(now) && target.isAlive()) {
            int cap = 3 + PassiveService.extraPoisonStacks(data);
            double toxin = PassiveService.value(data, PassiveDef.Type.TOXIN_POWER);
            AfflictionService.addPoison(target, 1, cap, buffs.venomPower * (1.0 + toxin / 100.0), 120, attacker);
        }
        if (arrow) {
            ArrowChain chain = ARROW_CHAINS.computeIfAbsent(attacker.getUUID(), id -> new ArrowChain());
            chain.stacks = now - chain.lastTick <= ARROW_CHAIN_WINDOW ? Math.min(ARROW_CHAIN_MAX, chain.stacks + 1) : 1;
            chain.lastTick = now;
        }
        if (ctx == null || dealt <= 0) {
            return;
        }
        double echo = PassiveService.value(data, PassiveDef.Type.WEAKEN_ECHO);
        if (echo > 0 && target.isAlive()) {
            AfflictionService.weaken(target, echo, WEAKEN_ECHO_TICKS, attacker);
        }
        PassiveService.Active bell = PassiveService.find(data, PassiveDef.Type.BELL_ECHO);
        if (bell != null) {
            ServerPlayer lowest = lowestAlly(attacker, bell.radius());
            if (lowest != null) {
                HealService.healBySkill(attacker, lowest, dealt * bell.value() / 100.0);
            }
        }
        if (buffs.cloned(now) && !ctx.echo && target.isAlive()) {
            double power = dealt * buffs.clonePercent / 100.0;
            int skillIndex = ctx.skillIndex;
            SkillScheduler.schedule(attacker.serverLevel(), CLONE_DELAY, () -> {
                if (target.isAlive() && attacker.isAlive()) {
                    CombatService.dealStatusDamage(target, power, attacker, CombatTextS2C.Kind.DAMAGE);
                    new SkillFxS2C(SkillFxS2C.Phase.TRIGGER, attacker.getId(), skillIndex, attacker.position(),
                            target.getBoundingBox().getCenter(), 0.0F, 1, new int[]{target.getId()}, 1.0F)
                            .sendNear(attacker.level().dimension(), target.position());
                }
            });
        }
    }

    /** 連なる矢の上がり幅（%）。 */
    public static double arrowChainBonus(ServerPlayer attacker, PlayerData data, long now) {
        double value = PassiveService.value(data, PassiveDef.Type.ARROW_CHAIN);
        ArrowChain chain = ARROW_CHAINS.get(attacker.getUUID());
        if (value <= 0 || chain == null || now - chain.lastTick > ARROW_CHAIN_WINDOW) {
            return 0.0;
        }
        return value * chain.stacks;
    }

    /** 移動の技（影走り・影討ち・影縫い）を使った。 */
    public static void onMoveSkill(ServerPlayer player, PlayerData data, long now) {
        if (PassiveService.value(data, PassiveDef.Type.SHADOW_STEP) > 0) {
            SHADOW_STEP.put(player.getUUID(), now + SHADOW_STEP_TICKS);
        }
    }

    /** 影の歩法：会心の上がり幅（使うと消える）。 */
    public static double consumeShadowStep(ServerPlayer player, PlayerData data, long now) {
        Long until = SHADOW_STEP.get(player.getUUID());
        if (until == null || now > until) {
            return 0.0;
        }
        SHADOW_STEP.remove(player.getUUID());
        return PassiveService.value(data, PassiveDef.Type.SHADOW_STEP);
    }

    /** まわりで、HPの割合がいちばん低い仲間（自分も。満タンなら null）。 */
    @Nullable
    public static ServerPlayer lowestAlly(ServerPlayer caster, double radius) {
        ServerLevel level = caster.serverLevel();
        ServerPlayer best = null;
        double bestRatio = 1.0;
        for (ServerPlayer player : level.players()) {
            if (!CombatTargets.isAlly(caster, player, true) || DownHandler.isDown(player)
                    || player.distanceToSqr(caster) > radius * radius) {
                continue;
            }
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data == null || !data.isStatsReady() || data.getStats().maxHp() <= 0) {
                continue;
            }
            double ratio = data.getHp() / data.getStats().maxHp();
            if (ratio < bestRatio - 1.0E-6) {
                best = player;
                bestRatio = ratio;
            }
        }
        return best;
    }

    public static void forget(UUID player) {
        LAST_ATTACK.remove(player);
        SHADOW_STEP.remove(player);
        ARROW_CHAINS.remove(player);
    }

    public static void clear() {
        LAST_ATTACK.clear();
        SHADOW_STEP.clear();
        ARROW_CHAINS.clear();
    }
}
