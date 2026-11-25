package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.combat.LinkComboTracker;
import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.job.def.EffectDef;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.SkillCooldown;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 構えて待つスキル（ナイトの「受け流し」）。
 *
 * <p>構えている間に、正面から魔物の攻撃が来たら、その攻撃を無効にして（{@code CombatService} が攻撃を取り消す）、
 * 1回だけ反撃する：攻撃してきた相手に「on_success」の効果（反撃のダメージ・よろめきなど）をかけ、
 * クールダウンの残りを「refund」の割合だけ減らす。反撃した後も、構えの残りの間は正面からの攻撃を無効にする。</p>
 */
public final class ReactiveSkills {
    private static final class Parry {
        final SkillContext ctx;
        final EffectDef effect;
        final long until;
        /** 反撃した回数と、その上限（流水の構えは何度でも）。 */
        int counters;
        long lastCounter = -1000L;
        final int maxCounters;
        /** どの向きからの攻撃も受け流すか（流水の構え・天地流転）。 */
        final boolean allDirections;

        Parry(SkillContext ctx, EffectDef effect, long until) {
            this.ctx = ctx;
            this.effect = effect;
            this.until = until;
            this.maxCounters = Math.max(1, (int) Math.round(effect.value("counters", ctx.rank)));
            this.allDirections = effect.value("all_directions", ctx.rank) > 0.5;
        }
    }

    /** 流水の構えの反撃は、同じ相手には少しあけてから（1回の攻撃で何度も反撃しないため）。 */
    private static final int COUNTER_GAP = 8;

    private static final Map<UUID, Parry> PARRIES = new HashMap<>();

    private ReactiveSkills() {
    }

    /** 受け流しの構えを始める。 */
    static void startParry(SkillContext ctx, ServerPlayer player, EffectDef effect, int ticks, long now) {
        PARRIES.put(player.getUUID(), new Parry(ctx, effect, now + ticks));
        player.addEffect(new MobEffectInstance(ModEffects.PARRY.get(), ticks, 0, false, false, true));
        StatusService.sync(player, StatusFxS2C.Event.NONE);
    }

    /** 構えているか。 */
    public static boolean parrying(ServerPlayer player, long now) {
        Parry parry = PARRIES.get(player.getUUID());
        return parry != null && now <= parry.until;
    }

    /**
     * 魔物の攻撃を受け流せるか。受け流せたら true（攻撃は取り消す）。最初の1回だけ反撃する。
     */
    public static boolean tryParry(ServerPlayer player, LivingEntity attacker, long now) {
        Parry parry = PARRIES.get(player.getUUID());
        if (parry == null) {
            return false;
        }
        if (now > parry.until) {
            PARRIES.remove(player.getUUID());
            return false;
        }
        if (!parry.allDirections && !CombatService.isInFront(player, attacker)) {
            return false;
        }
        if (parry.counters < parry.maxCounters && now - parry.lastCounter >= COUNTER_GAP) {
            parry.counters++;
            parry.lastCounter = now;
            counter(parry, player, attacker, now);
        }
        return true;
    }

    /** 回避したとき（柔の理の気・流れを読む）。 */
    public static void onDodged(ServerPlayer player, PlayerData data, long now) {
        double softWay = PassiveService.value(data, jp.amemaru.lostconnection.job.def.PassiveDef.Type.SOFT_WAY);
        if (softWay > 0) {
            GaugeService.add(data, 8.0);
        }
        ComboService.onFlowRead(player, data, now);
    }

    private static void counter(Parry parry, ServerPlayer player, LivingEntity attacker, long now) {
        SkillContext ctx = parry.ctx;
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        // 受けの心得（モンク）：反撃のダメージが上がり、気がたまる（Phase 6 その2）
        double mastery = data == null ? 0.0 : PassiveService.value(data, jp.amemaru.lostconnection.job.def.PassiveDef.Type.COUNTER_MASTERY);
        double saved = ctx.multiplier;
        if (mastery > 0) {
            ctx.multiplier *= 1.0 + mastery / 100.0;
            GaugeService.add(data, 10.0);
        }
        // 反撃も連携の起点・追撃になる
        LinkComboTracker.onSkillHit(attacker, player, ctx.skill.link(), now);
        for (EffectDef effect : parry.effect.nested()) {
            SkillExecutor.applyTriggered(ctx, effect, attacker, player);
        }
        ctx.multiplier = saved;
        if (data != null) {
            ComboService.onFlowRead(player, data, now);
        }
        double refund = parry.counters == 1 ? parry.effect.value("refund", ctx.rank) : 0.0;
        if (data != null && refund > 0) {
            SkillCooldown cooldown = data.getCooldown(ctx.skill.id());
            if (cooldown != null && cooldown.endTick() > now) {
                int remaining = cooldown.remaining(now);
                int next = Mth.ceil(remaining * (1.0 - refund));
                data.setCooldown(ctx.skill.id(), new SkillCooldown(now + next, cooldown.totalTicks()));
            }
        }
        Vec3 at = attacker.getBoundingBox().getCenter();
        new SkillFxS2C(SkillFxS2C.Phase.TRIGGER, player.getId(), ctx.skillIndex, player.getEyePosition(), at, 1.0F, 0,
                new int[]{attacker.getId()}).sendNear(player.level().dimension(), at);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS,
                1.0F, 1.4F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS,
                0.35F, 1.8F);
        CombatTextS2C.send(player, 0, CombatTextS2C.Kind.PARRY);
        StatusService.sync(player, StatusFxS2C.Event.PARRIED);
    }

    /** ログアウトしたプレイヤーの構えを消す。 */
    public static void forget(UUID player) {
        PARRIES.remove(player);
    }

    /** 終わった構えを消す（{@code SkillScheduler} から）。 */
    static void cleanup(long now) {
        PARRIES.values().removeIf(parry -> now > parry.until + 20);
    }

    public static void clear() {
        PARRIES.clear();
    }
}
