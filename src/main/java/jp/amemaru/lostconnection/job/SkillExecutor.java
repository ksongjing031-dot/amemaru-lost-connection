package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.combat.CombatTargets;
import jp.amemaru.lostconnection.combat.LinkComboTracker;
import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.combat.TauntService;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModSounds;
import jp.amemaru.lostconnection.entity.LampBolt;
import jp.amemaru.lostconnection.job.def.BuffKind;
import jp.amemaru.lostconnection.job.def.DeliveryDef;
import jp.amemaru.lostconnection.job.def.EffectDef;
import jp.amemaru.lostconnection.job.def.LinkType;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.VitalsService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * スキルの効果を出す（届け方 → 相手 → 効果の順）。すべてサーバーで行い、演出は {@link SkillFxS2C} で近くの人に送る。
 */
public final class SkillExecutor {
    /** 跳躍の飛ぶ時間（tick）の下限と上限。 */
    private static final int LEAP_MIN_TICKS = 8;
    private static final int LEAP_MAX_TICKS = 22;
    /** 地面と平行な line（flat）を、足もとからどの高さに通すか。 */
    private static final double FLAT_LINE_HEIGHT = 1.5;

    private SkillExecutor() {
    }

    // ---------------------------------------------------------------- 届け方

    /** スキルを放つ（コストとクールダウンは呼ぶ側で済ませる）。 */
    public static void deliver(SkillContext ctx, ServerPlayer caster, SkillTargeting.Aim aim) {
        SkillDef skill = ctx.skill;
        DeliveryDef delivery = skill.delivery();
        int rank = ctx.rank;
        ServerLevel level = ctx.level;
        Vec3 origin = caster.position();
        prepare(ctx, caster);
        playSound(level, origin, skill.sound(), 1.0F);
        float power = fxPower(ctx);

        switch (delivery.type()) {
            case SELF -> {
                fx(ctx, SkillFxS2C.Phase.RELEASE, origin, origin, 0, zoneTicks(skill, rank), new int[]{caster.getId()}, power);
                apply(ctx, List.of(caster), null, origin, null);
            }
            case AROUND -> {
                double radius = delivery.radius(rank);
                List<LivingEntity> targets = SkillTargeting.around(caster, radius, delivery.affects(), delivery.includeSelf());
                fx(ctx, SkillFxS2C.Phase.RELEASE, origin, origin, radius, zoneTicks(skill, rank), ids(targets), power);
                apply(ctx, targets, null, origin, null);
            }
            case TARGET -> {
                LivingEntity target = aim.entity();
                if (target == null) {
                    return;
                }
                fx(ctx, SkillFxS2C.Phase.RELEASE, caster.getEyePosition(), target.getBoundingBox().getCenter(), 0,
                        zoneTicks(skill, rank), new int[]{target.getId()}, power);
                apply(ctx, List.of(target), null, target.position(), null);
            }
            case POINT -> {
                Vec3 center = aim.point() != null ? aim.point() : origin;
                double radius = delivery.radius(rank);
                int delay = Mth.ceil(delivery.impactDelay(rank) * 20.0);
                fx(ctx, SkillFxS2C.Phase.RELEASE, caster.getEyePosition(), center, radius, delay > 0 ? delay : zoneTicks(skill, rank),
                        new int[0], power);
                if (delay > 0) {
                    SkillScheduler.schedule(level, delay, () -> impact(ctx, center, radius));
                } else {
                    impact(ctx, center, radius);
                }
            }
            case PROJECTILE -> {
                int count = delivery.count(rank);
                int charged = delivery.countCharged(rank);
                if (skill.isCharged() && charged > count) {
                    count += (int) Math.floor((charged - count) * ctx.chargeRatio + 1.0E-6);
                }
                double spread = count > 1 ? delivery.spread(rank) : 0.0;
                Vec3 look = caster.getLookAngle();
                for (int i = 0; i < count; i++) {
                    double offset = count == 1 ? 0.0 : -spread / 2.0 + spread * i / (count - 1);
                    Vec3 direction = look.yRot((float) Math.toRadians(-offset));
                    LampBolt.launch(ctx, caster, direction, delivery.speed(rank), delivery.range(rank), delivery.radius(rank));
                }
                fx(ctx, SkillFxS2C.Phase.RELEASE, caster.getEyePosition(), caster.getEyePosition().add(look),
                        (float) spread, count, new int[0], power);
            }
            case DASH -> {
                Vec3 direction = horizontalLook(caster);
                double distance = delivery.distance(rank);
                Vec3 end = dashEnd(caster, direction, distance);
                LivingEntity first = firstAlongPath(caster, origin, end, delivery.width(rank));
                double speed = Math.min(3.0, 0.32 * distance + 0.2);
                caster.setDeltaMovement(direction.x * speed, 0.12, direction.z * speed);
                caster.hurtMarked = true;
                caster.fallDistance = 0.0F;
                fx(ctx, SkillFxS2C.Phase.RELEASE, origin, end, (float) delivery.width(rank), 0,
                        first == null ? new int[0] : new int[]{first.getId()}, power);
                if (first != null) {
                    apply(ctx, List.of(first), null, first.position(), null);
                } else {
                    apply(ctx, List.of(), null, end, null);
                }
            }
            case CHAIN -> {
                LivingEntity first = aim.entity();
                if (first == null) {
                    return;
                }
                int jumps = delivery.jumps(rank);
                if ((delivery.shockedJumps(rank) > 0 || delivery.shockedMultiplier(rank) > 1.0) && StatusService.consumeShock(first)) {
                    jumps += delivery.shockedJumps(rank);
                    ctx.multiplier *= delivery.shockedMultiplier(rank);
                    ctx.empowered = true;
                }
                List<LivingEntity> chain = chain(ctx, first, jumps, delivery.jumpRange(rank));
                double[] falloff = new double[chain.size()];
                double factor = delivery.falloff(rank);
                for (int i = 0; i < falloff.length; i++) {
                    falloff[i] = Math.pow(factor, i);
                }
                fx(ctx, SkillFxS2C.Phase.RELEASE, caster.getEyePosition(), first.getBoundingBox().getCenter(), 0, 0, ids(chain),
                        ctx.empowered ? 1.0F : 0.0F);
                apply(ctx, chain, falloff, first.position(), null);
            }
            case BLINK -> {
                Vec3 to = blinkDestination(caster, delivery.distance(rank));
                caster.teleportTo(to.x, to.y, to.z);
                caster.fallDistance = 0.0F;
                fx(ctx, SkillFxS2C.Phase.RELEASE, origin, to, 0, 0, new int[]{caster.getId()}, power);
                apply(ctx, List.of(caster), null, to, null);
            }
            case LEAP -> {
                Vec3 target = aim.point() != null ? aim.point() : origin.add(horizontalLook(caster).scale(delivery.range(rank) * 0.5));
                int flight = leap(caster, target);
                SkillScheduler.addLeap(ctx, caster, flight, delivery.radius(rank));
                fx(ctx, SkillFxS2C.Phase.RELEASE, origin, target, (float) delivery.radius(rank), flight, new int[]{caster.getId()}, power);
            }
            case LINE -> {
                // flat：胸の高さから地面と平行に（1段の段差では止まらず、2段の壁で止まる）
                Vec3 start = delivery.flat() ? caster.position().add(0.0, FLAT_LINE_HEIGHT, 0.0) : caster.getEyePosition();
                Vec3 look = delivery.flat() ? horizontalLook(caster) : caster.getLookAngle();
                Vec3 far = start.add(look.scale(delivery.range(rank)));
                BlockHitResult block = level.clip(new ClipContext(start, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
                List<LivingEntity> targets = SkillTargeting.alongLine(level, start, end, delivery.width(rank));
                if (delivery.flat()) {
                    // 演出は足もとの高さで（地面を走る斬撃）
                    fx(ctx, SkillFxS2C.Phase.RELEASE, caster.position(), end.subtract(0.0, FLAT_LINE_HEIGHT, 0.0), (float) delivery.width(rank), 0,
                            ids(targets), power);
                } else {
                    fx(ctx, SkillFxS2C.Phase.RELEASE, start.add(0.0, -0.25, 0.0), end, (float) delivery.width(rank), 0, ids(targets), power);
                }
                apply(ctx, targets, null, end, null);
            }
            case CONE -> {
                double radius = delivery.radius(rank);
                List<LivingEntity> targets = SkillTargeting.inCone(caster, radius, delivery.angle(rank));
                fx(ctx, SkillFxS2C.Phase.RELEASE, origin, origin.add(horizontalLook(caster).scale(radius)), radius, 0, ids(targets), power);
                apply(ctx, targets, null, origin, null);
            }
            case BRANDED -> {
                List<LivingEntity> targets = SkillTargeting.branded(caster, delivery.range(rank));
                fx(ctx, SkillFxS2C.Phase.RELEASE, caster.getEyePosition(), caster.getEyePosition(), delivery.range(rank), 0,
                        ids(targets), power);
                apply(ctx, targets, null, origin, null);
            }
        }
    }

    /** 地点に着弾する（流星群など）。 */
    private static void impact(SkillContext ctx, Vec3 center, double radius) {
        List<LivingEntity> targets = SkillTargeting.enemiesNear(ctx.level, center, radius);
        fx(ctx, SkillFxS2C.Phase.IMPACT, center, center, radius, zoneTicks(ctx.skill, ctx.rank), ids(targets), fxPower(ctx));
        playSound(ctx.level, center, ctx.skill.impactSound(), 1.0F);
        apply(ctx, targets, null, center, null);
    }

    /** 弾が敵に当たったとき（{@link LampBolt}）。 */
    public static void projectileHit(SkillContext ctx, LivingEntity target, Entity projectile) {
        Vec3 at = target.getBoundingBox().getCenter();
        fx(ctx, SkillFxS2C.Phase.IMPACT, projectile.position(), at, 0.6F, 0, new int[]{target.getId()}, fxPower(ctx));
        playSound(ctx.level, at, ctx.skill.impactSound(), 0.8F);
        apply(ctx, List.of(target), null, at, projectile);
    }

    /**
     * 跳ぶ：狙った地点に届くように、使った人に勢いをつける（プレイヤーの動きはクライアントが計算するため、
     * 空気の抵抗と重さを同じ式で見積もる）。
     *
     * @return 飛ぶ時間の見積もり（tick）
     */
    private static int leap(ServerPlayer caster, Vec3 target) {
        Vec3 origin = caster.position();
        Vec3 flat = new Vec3(target.x - origin.x, 0.0, target.z - origin.z);
        double distance = flat.length();
        int ticks = Mth.clamp((int) Math.round(LEAP_MIN_TICKS + distance * 1.1), LEAP_MIN_TICKS, LEAP_MAX_TICKS);
        // 空中では、横の速さが毎 tick 0.91倍になる：n tick で進む距離 ＝ v × (1 − 0.91^n) ÷ 0.09
        double horizontal = distance < 0.3 ? 0.0 : Math.min(2.2, distance * 0.09 / (1.0 - Math.pow(0.91, ticks)));
        Vec3 direction = distance < 0.3 ? Vec3.ZERO : flat.scale(1.0 / distance);
        double vertical = solveLaunchSpeed(target.y - origin.y, ticks);
        caster.setDeltaMovement(direction.x * horizontal, vertical, direction.z * horizontal);
        caster.hurtMarked = true;
        caster.hasImpulse = true;
        caster.fallDistance = 0.0F;
        return ticks;
    }

    /** n tick 後に高さ dy に着くための、上向きの速さ（重さ0.08・空気0.98を、1 tick ずつ数えて二分探索で求める）。 */
    private static double solveLaunchSpeed(double dy, int ticks) {
        double low = 0.25;
        double high = 2.4;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2.0;
            double y = 0.0;
            double v = mid;
            for (int t = 0; t < ticks; t++) {
                y += v;
                v = (v - 0.08) * 0.98;
            }
            if (y < dy) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2.0;
    }

    /** 跳躍の着地（{@link SkillScheduler}）。 */
    static void leapLanded(SkillContext ctx, ServerPlayer caster, double radius) {
        Vec3 center = caster.position();
        caster.fallDistance = 0.0F;
        List<LivingEntity> targets = SkillTargeting.enemiesNear(ctx.level, center, radius);
        fx(ctx, SkillFxS2C.Phase.IMPACT, center, center, radius, 0, ids(targets), fxPower(ctx));
        playSound(ctx.level, center, ctx.skill.impactSound(), 1.0F);
        apply(ctx, targets, null, center, null);
    }

    // ---------------------------------------------------------------- 効果

    /**
     * ゲージを使って強める（旋風断・誓気解放）。1回のスキルで1回だけ。
     *
     * @param caster 使った人（放つときに渡す。遅れて効く処理では null で、そのときは一覧から探す）
     */
    private static void prepare(SkillContext ctx, @Nullable ServerPlayer caster) {
        if (ctx.prepared) {
            return;
        }
        ctx.prepared = true;
        if (caster == null) {
            caster = ctx.caster();
        }
        PlayerData data = caster == null ? null : PlayerDataCapability.get(caster).orElse(null);
        if (caster == null || data == null) {
            return;
        }
        for (EffectDef effect : ctx.skill.effects()) {
            switch (effect.type()) {
                case GAUGE_EMPOWER -> {
                    double consume = effect.value("consume", ctx.rank);
                    if (data.getGauge() + 1.0E-6 >= consume && GaugeService.consume(caster, data, consume)) {
                        ctx.multiplier *= effect.value("multiplier", ctx.rank);
                        ctx.empowered = true;
                    }
                }
                case GAUGE_BURST -> {
                    double spent = ctx.gaugeSpent;
                    double rest = data.getGauge();
                    if (rest > 0 && GaugeService.consume(caster, data, rest)) {
                        spent += rest;
                    }
                    ctx.gaugeSpent = spent;
                    ctx.multiplier *= 1.0 + spent * effect.value("per_gauge", ctx.rank);
                    ctx.empowered = spent > 0;
                }
                default -> {
                }
            }
        }
    }

    /**
     * 相手たちに効果をかける。
     *
     * @param falloff 相手ごとの威力の倍率（雷鎖の減衰。null なら1）
     * @param center  陣を置く場所など
     * @param direct  直接当たったもの（弾）。null なら使った人
     */
    static void apply(SkillContext ctx, List<LivingEntity> targets, @Nullable double[] falloff, Vec3 center, @Nullable Entity direct) {
        SkillDef skill = ctx.skill;
        int rank = ctx.rank;
        prepare(ctx, null);
        ServerPlayer caster = ctx.caster();
        PlayerData casterData = caster == null ? null : PlayerDataCapability.get(caster).orElse(null);
        long now = ctx.level.getGameTime();

        int enemies = 0;
        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get(i);
            boolean enemy = CombatTargets.isEnemy(target);
            double multiplier = ctx.multiplier * (falloff != null && i < falloff.length ? falloff[i] : 1.0);
            if (enemy) {
                enemies++;
                if (caster != null) {
                    LinkComboTracker.Result link = LinkComboTracker.onSkillHit(target, caster, skill.link(), now);
                    if (link.outcome() == LinkComboTracker.Outcome.LINK || link.outcome() == LinkComboTracker.Outcome.LINK3) {
                        multiplier *= link.multiplier();
                        announceLink(ctx.level, target, link);
                    }
                }
                if (casterData != null) {
                    casterData.markCombat(now);
                }
            }
            for (EffectDef effect : skill.effects()) {
                applyToTarget(ctx, effect, target, multiplier, center, direct, caster, now);
            }
            if (ctx.fullCharge) {
                for (EffectDef effect : skill.fullChargeEffects()) {
                    applyToTarget(ctx, effect, target, multiplier, center, direct, caster, now);
                }
            }
            if (skill.link() == LinkType.SUPPORT && caster != null && target instanceof ServerPlayer ally && ally != caster) {
                grantSupport(caster, ally, now);
            }
        }

        for (EffectDef effect : skill.effects()) {
            switch (effect.type()) {
                case GAUGE -> {
                    if (casterData != null) {
                        double gain = effect.value("amount", rank);
                        double perTarget = effect.value("per_target", rank) * enemies;
                        double max = effect.value("max", rank);
                        gain += max > 0 ? Math.min(max, perTarget) : perTarget;
                        if (gain > 0) {
                            GaugeService.add(casterData, gain);
                        }
                    }
                }
                case ZONE -> SkillScheduler.addZone(ctx, center, effect.value("radius", rank),
                        Mth.ceil(effect.value("duration", rank) * 20.0), Mth.ceil(effect.value("interval", rank) * 20.0),
                        effect.nested());
                default -> {
                }
            }
        }
    }

    private static void applyToTarget(SkillContext ctx, EffectDef effect, LivingEntity target, double multiplier, Vec3 center,
                                      @Nullable Entity direct, @Nullable ServerPlayer caster, long now) {
        int rank = ctx.rank;
        boolean enemy = CombatTargets.isEnemy(target);
        switch (effect.type()) {
            case DAMAGE -> {
                if (enemy) {
                    damage(ctx, effect, target, multiplier, direct, caster);
                }
            }
            case STATUS -> {
                if (enemy) {
                    addStatus(effect, target, rank, caster);
                }
            }
            case TAUNT -> {
                if (enemy && caster != null) {
                    TauntService.taunt(target, caster, Mth.ceil(effect.value("duration", rank) * 20.0), now);
                }
            }
            case SHIELD_BOND -> {
                if (caster != null && target instanceof ServerPlayer ally && ally != caster) {
                    PlayerDataCapability.get(ally).ifPresent(data -> {
                        int ticks = Mth.ceil(effect.value("duration", rank) * 20.0);
                        BuffState buffs = data.getBuffs();
                        buffs.bondGiver = caster.getUUID();
                        buffs.bondUntil = now + ticks;
                        buffs.bondShare = Math.min(90.0, effect.value("share", rank) * multiplier);
                        ally.addEffect(new MobEffectInstance(ModEffects.SHIELD_BOND.get(), ticks, 0, false, false, true), caster);
                        StatusService.sync(ally, StatusFxS2C.Event.NONE);
                    });
                }
            }
            case BUFF -> {
                if (target instanceof ServerPlayer player && effect.buff() != null) {
                    int ticks = Mth.ceil(effect.value("duration", rank) * 20.0);
                    if (effect.buff() == BuffKind.PARRY) {
                        ReactiveSkills.startParry(ctx, player, effect, ticks, now);
                    } else {
                        applyBuff(player, effect.buff(), ticks, Math.min(95.0, effect.value("value", rank) * multiplier), now, caster);
                    }
                }
            }
            case MP_TRANSFER -> {
                if (caster != null && target instanceof ServerPlayer ally && ally != caster) {
                    PlayerData from = PlayerDataCapability.get(caster).orElse(null);
                    PlayerData to = PlayerDataCapability.get(ally).orElse(null);
                    if (from != null && to != null && to.isStatsReady()) {
                        double wanted = effect.value("amount", rank) * multiplier;
                        double room = Math.max(0.0, to.getStats().maxMp() - to.getMp());
                        double give = Math.min(Math.min(wanted, from.getMp()), room);
                        if (give > 0) {
                            from.setMp(from.getMp() - give);
                            to.setMp(to.getMp() + give);
                            CombatTextS2C.send(ally, give, CombatTextS2C.Kind.MP);
                        }
                    }
                }
            }
            case KNOCKBACK -> {
                if (enemy) {
                    knockback(target, effect.value("strength", rank), caster != null ? caster.position() : center);
                }
            }
            case CHILL -> {
                if (enemy) {
                    chill(ctx, effect, target, caster);
                }
            }
            case SHOCK -> {
                if (enemy) {
                    StatusService.shock(target, Mth.ceil(effect.value("duration", rank) * 20.0), caster);
                }
            }
            case BRAND -> {
                if (enemy) {
                    StatusService.addBrand(target, (int) Math.round(effect.value("stacks", rank)),
                            Mth.ceil(effect.value("duration", rank) * 20.0), caster);
                }
            }
            case DETONATE -> {
                if (enemy && effect.stat() != null) {
                    int stacks = StatusService.consumeBrands(target);
                    if (stacks > 0) {
                        double coefficient = effect.value("base", rank) + effect.value("per_stack", rank) * stacks;
                        double power = ctx.stat(effect.stat()) * coefficient * multiplier * ctx.passiveDamageMultiplier(target);
                        CombatService.dealSkillDamage(ctx, target, power, direct);
                        if (ctx.afterglowMp > 0 && caster != null) {
                            restoreMp(caster, ctx.afterglowMp * stacks);
                        }
                    }
                }
            }
            case PULL -> {
                if (enemy && caster != null) {
                    pull(target, caster, effect.value("strength", rank), effect.value("stop", rank));
                }
            }
            case BARRIER -> {
                if (target instanceof ServerPlayer player && effect.stat() != null) {
                    double amount = ctx.stat(effect.stat()) * effect.value("coefficient", rank) * multiplier;
                    applyBarrier(player, amount, Mth.ceil(effect.value("duration", rank) * 20.0), now);
                }
            }
            default -> {
            }
        }
    }

    /** ダメージ：状態へのボーナス（凍結を砕くなど）・パッシブ・吸収をかけて与える。 */
    private static void damage(SkillContext ctx, EffectDef effect, LivingEntity target, double multiplier, @Nullable Entity direct,
                               @Nullable ServerPlayer caster) {
        if (effect.stat() == null) {
            return;
        }
        int rank = ctx.rank;
        double power = ctx.stat(effect.stat()) * effect.value("coefficient", rank) * multiplier;
        boolean bonus = effect.bonusStatus() != null && StatusService.has(target, effect.bonusStatus());
        if (bonus) {
            power *= effect.value("bonus", rank);
        }
        power *= ctx.passiveDamageMultiplier(target);
        double dealt = CombatService.dealSkillDamage(ctx, target, power, direct);
        if (bonus && effect.consumeStatus()) {
            StatusService.consume(target, effect.bonusStatus());
        }
        double lifesteal = effect.value("lifesteal", rank);
        if (dealt > 0 && lifesteal > 0 && caster != null) {
            heal(caster, dealt * lifesteal / 100.0);
        }
    }

    private static void chill(SkillContext ctx, EffectDef effect, LivingEntity target, @Nullable Entity source) {
        int rank = ctx.rank;
        int ticks = Mth.ceil(effect.value("duration", rank) * 20.0 * ctx.chillDurationMultiplier);
        double freeze = effect.value("freeze", rank);
        int freezeTicks = freeze <= 0 ? 0 : Mth.ceil(freeze * 20.0) + ctx.freezeBonusTicks;
        StatusService.addChill(target, (int) Math.round(effect.value("stacks", rank)), ticks, (int) Math.round(effect.value("max", rank)),
                freezeTicks, (int) Math.round(effect.value("freeze_if", rank)), source);
    }

    /** 陣の中の相手に効果をかける（{@link SkillScheduler}。敵か味方かは陣の側で選ぶ）。 */
    static void applyZoneEffect(SkillContext ctx, EffectDef effect, LivingEntity target, Vec3 center) {
        ServerPlayer caster = ctx.caster();
        long now = ctx.level.getGameTime();
        switch (effect.type()) {
            case DAMAGE -> damage(ctx, effect, target, ctx.multiplier, null, caster);
            case STATUS -> addStatus(effect, target, ctx.rank, caster);
            case KNOCKBACK -> knockback(target, effect.value("strength", ctx.rank), center);
            case CHILL -> chill(ctx, effect, target, caster);
            case SHOCK -> StatusService.shock(target, Mth.ceil(effect.value("duration", ctx.rank) * 20.0), caster);
            case TAUNT -> {
                if (caster != null) {
                    TauntService.taunt(target, caster, Mth.ceil(effect.value("duration", ctx.rank) * 20.0), now);
                }
            }
            case BUFF -> {
                if (target instanceof ServerPlayer player && effect.buff() != null) {
                    applyBuff(player, effect.buff(), Mth.ceil(effect.value("duration", ctx.rank) * 20.0),
                            Math.min(95.0, effect.value("value", ctx.rank) * ctx.multiplier), now, caster);
                }
            }
            default -> {
            }
        }
    }

    /**
     * 構えて待つスキルの効果（受け流しの反撃）。攻撃してきた相手にかける。ゲージは構えた本人にたまる。
     */
    static void applyTriggered(SkillContext ctx, EffectDef effect, LivingEntity attacker, ServerPlayer owner) {
        int rank = ctx.rank;
        long now = ctx.level.getGameTime();
        switch (effect.type()) {
            case DAMAGE -> {
                if (CombatTargets.isEnemy(attacker)) {
                    damage(ctx, effect, attacker, ctx.multiplier, null, owner);
                }
            }
            case STATUS -> addStatus(effect, attacker, rank, owner);
            case KNOCKBACK -> knockback(attacker, effect.value("strength", rank), owner.position());
            case TAUNT -> TauntService.taunt(attacker, owner, Mth.ceil(effect.value("duration", rank) * 20.0), now);
            case CHILL -> chill(ctx, effect, attacker, owner);
            case SHOCK -> StatusService.shock(attacker, Mth.ceil(effect.value("duration", rank) * 20.0), owner);
            case BRAND -> StatusService.addBrand(attacker, (int) Math.round(effect.value("stacks", rank)),
                    Mth.ceil(effect.value("duration", rank) * 20.0), owner);
            case PULL -> pull(attacker, owner, effect.value("strength", rank), effect.value("stop", rank));
            case GAUGE -> PlayerDataCapability.get(owner).ifPresent(data -> GaugeService.add(data, effect.value("amount", rank)));
            default -> {
            }
        }
    }

    private static void addStatus(EffectDef effect, LivingEntity target, int rank, @Nullable Entity source) {
        ResourceLocation id = effect.statusEffect();
        MobEffect mobEffect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
        if (mobEffect == null) {
            return;
        }
        int ticks = Mth.ceil(effect.value("duration", rank) * 20.0);
        int amplifier = Math.max(0, (int) Math.round(effect.value("level", rank)) - 1);
        if (mobEffect == ModEffects.ARMOR_BREAK.get()) {
            StatusService.armorBreak(target, ticks, source);
            return;
        }
        if (mobEffect == ModEffects.FROZEN.get()) {
            StatusService.freeze(target, ticks, source);
            return;
        }
        target.addEffect(new MobEffectInstance(mobEffect, ticks, amplifier, false, false, true), source);
        if (id.getNamespace().equals(jp.amemaru.lostconnection.AmemaruMod.MOD_ID)) {
            StatusService.sync(target, StatusFxS2C.Event.NONE);
        }
    }

    private static void knockback(LivingEntity target, double strength, Vec3 from) {
        if (strength <= 0) {
            return;
        }
        target.knockback(strength, from.x - target.getX(), from.z - target.getZ());
        target.hurtMarked = true;
    }

    /** 使った人の手前（{@code stop} ブロック）へ引き寄せる。 */
    private static void pull(LivingEntity target, ServerPlayer caster, double strength, double stop) {
        Vec3 destination = caster.position().add(horizontalLook(caster).scale(stop));
        Vec3 delta = destination.subtract(target.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.4) {
            return;
        }
        // 浮かせてから約10 tick で着く速さ（空中では横の速さが毎 tick 0.91倍）
        double speed = Math.min(2.4, horizontal / 6.2) * Math.max(0.1, strength);
        double lift = 0.32 + Mth.clamp(delta.y * 0.12, 0.0, 0.4);
        target.setDeltaMovement(delta.x / horizontal * speed, lift, delta.z / horizontal * speed);
        target.hurtMarked = true;
        target.hasImpulse = true;
    }

    /** 回復（吸収など）。 */
    private static void heal(ServerPlayer player, double amount) {
        PlayerDataCapability.get(player).ifPresent(data -> {
            if (!data.isStatsReady() || amount <= 0 || player.isDeadOrDying()) {
                return;
            }
            double before = data.getHp();
            data.setHp(before + amount);
            double healed = data.getHp() - before;
            if (healed > 0) {
                VitalsService.pushVanillaHealth(player, data);
                CombatTextS2C.send(player, healed, CombatTextS2C.Kind.HEAL);
            }
        });
    }

    /** MPを戻す（星の残光など）。 */
    private static void restoreMp(ServerPlayer player, double amount) {
        PlayerDataCapability.get(player).ifPresent(data -> {
            if (!data.isStatsReady() || amount <= 0) {
                return;
            }
            double before = data.getMp();
            data.setMp(before + amount);
            double restored = data.getMp() - before;
            if (restored > 0) {
                CombatTextS2C.send(player, restored, CombatTextS2C.Kind.MP);
            }
        });
    }

    /** 障壁を張る（光の帳）。すでにあれば、多い方の量を残し、時間を延ばす。 */
    public static void applyBarrier(ServerPlayer player, double amount, int ticks, long now) {
        if (amount <= 0 || ticks <= 0) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(data -> {
            BuffState buffs = data.getBuffs();
            double next = Math.max(buffs.barrier(now) ? buffs.barrierAmount : 0.0, amount);
            buffs.barrierAmount = next;
            buffs.barrierMax = next;
            buffs.barrierUntil = now + ticks;
            data.markDirty(PlayerData.SYNC_VITALS);
            player.addEffect(new MobEffectInstance(ModEffects.BARRIER.get(), ticks, 0, false, false, true));
            StatusService.sync(player, StatusFxS2C.Event.NONE);
        });
    }

    /** 数値つきの強化をかける（城壁の構え・不屈の誓い・不落の城・誓いの円陣）。 */
    public static void applyBuff(ServerPlayer player, BuffKind kind, int ticks, double value, long now, @Nullable Entity source) {
        PlayerDataCapability.get(player).ifPresent(data -> {
            BuffState buffs = data.getBuffs();
            boolean fresh = true;
            switch (kind) {
                case RAMPART -> {
                    buffs.rampartUntil = now + ticks;
                    buffs.rampartReduction = value;
                    player.addEffect(new MobEffectInstance(ModEffects.RAMPART.get(), ticks, 0, false, false, true), source);
                }
                case UNYIELDING -> {
                    buffs.unyieldingUntil = now + ticks;
                    player.addEffect(new MobEffectInstance(ModEffects.UNYIELDING.get(), ticks, 0, false, false, true), source);
                }
                case FORTRESS -> {
                    // まだ効いている不落の城があれば、強い方の割合と、長い方の残り時間を使う
                    boolean active = buffs.fortress(now);
                    buffs.fortressReduction = Math.max(active ? buffs.fortressReduction : 0.0, value);
                    buffs.fortressUntil = Math.max(active ? buffs.fortressUntil : 0L, now + ticks);
                    player.addEffect(new MobEffectInstance(ModEffects.FORTRESS.get(), ticks, 0, false, false, true), source);
                }
                case WARD -> {
                    // 陣の中にいる間、かけ直し続ける（新しくかかったときだけ見た目を送る）
                    boolean active = buffs.warded(now);
                    fresh = !active;
                    buffs.wardReduction = Math.max(active ? buffs.wardReduction : 0.0, value);
                    buffs.wardUntil = Math.max(active ? buffs.wardUntil : 0L, now + ticks);
                    player.addEffect(new MobEffectInstance(ModEffects.WARD.get(), ticks, 0, false, false, true), source);
                }
                case PARRY -> {
                    // 受け流しは ReactiveSkills で扱う（ここには来ない）
                    fresh = false;
                }
            }
            if (fresh) {
                StatusService.sync(player, StatusFxS2C.Event.NONE);
            }
        });
    }

    /** 連携支援を渡す：味方が時間内に使う次のスキルの効果が上がる。 */
    private static void grantSupport(ServerPlayer caster, ServerPlayer ally, long now) {
        PlayerDataCapability.get(ally).ifPresent(data -> {
            int window = (int) LinkComboTracker.windowTicks();
            BuffState buffs = data.getBuffs();
            buffs.supportFrom = caster.getUUID();
            buffs.supportUntil = now + window;
            buffs.supportBonus = ServerConfig.SUPPORT_BONUS.get();
            ally.addEffect(new MobEffectInstance(ModEffects.LINK_SUPPORT.get(), window, 0, false, false, true), caster);
        });
    }

    /** 連携の成立を、近くの人に知らせる（関わった人の画面には大きく出る）。 */
    private static void announceLink(ServerLevel level, LivingEntity target, LinkComboTracker.Result link) {
        List<Integer> involved = new ArrayList<>();
        for (UUID uuid : link.players()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                involved.add(player.getId());
            }
        }
        int[] ids = involved.stream().mapToInt(Integer::intValue).toArray();
        boolean three = link.outcome() == LinkComboTracker.Outcome.LINK3;
        CombatTextS2C.send(target, (link.multiplier() - 1.0) * 100.0, three ? CombatTextS2C.Kind.LINK3 : CombatTextS2C.Kind.LINK, ids);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.get(three ? "combat.link3" : "combat.link"),
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // ---------------------------------------------------------------- 道具

    private static List<LivingEntity> chain(SkillContext ctx, LivingEntity first, int jumps, double jumpRange) {
        List<LivingEntity> chain = new ArrayList<>();
        chain.add(first);
        LivingEntity last = first;
        for (int i = 0; i < jumps; i++) {
            LivingEntity from = last;
            AABB box = from.getBoundingBox().inflate(jumpRange);
            LivingEntity next = ctx.level.getEntitiesOfClass(LivingEntity.class, box, entity ->
                            CombatTargets.isEnemy(entity) && !chain.contains(entity)
                                    && entity.distanceToSqr(from) <= jumpRange * jumpRange && from.hasLineOfSight(entity))
                    .stream()
                    .min((a, b) -> Double.compare(a.distanceToSqr(from), b.distanceToSqr(from)))
                    .orElse(null);
            if (next == null) {
                break;
            }
            chain.add(next);
            last = next;
        }
        return chain;
    }

    static Vec3 horizontalLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-6) {
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            flat = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        }
        return flat.normalize();
    }

    /** 踏み込みの終わりの位置（壁に当たればその手前）。 */
    private static Vec3 dashEnd(ServerPlayer player, Vec3 direction, double distance) {
        Vec3 from = player.position().add(0.0, 0.6, 0.0);
        Vec3 to = from.add(direction.scale(distance));
        BlockHitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation().subtract(direction.scale(0.4));
        return end.subtract(0.0, 0.6, 0.0);
    }

    /** 通り道で最初に触れる敵。 */
    @Nullable
    private static LivingEntity firstAlongPath(ServerPlayer player, Vec3 from, Vec3 to, double width) {
        Vec3 path = to.subtract(from);
        double length = path.length();
        if (length < 1.0E-3) {
            return null;
        }
        Vec3 direction = path.scale(1.0 / length);
        AABB box = player.getBoundingBox().expandTowards(path).inflate(width);
        LivingEntity best = null;
        double bestAlong = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box, CombatTargets::isEnemy)) {
            Vec3 rel = entity.position().subtract(from);
            double along = rel.dot(direction);
            if (along < -0.5 || along > length + 1.0) {
                continue;
            }
            Vec3 closest = from.add(direction.scale(Math.max(0.0, Math.min(length, along))));
            double side = new Vec3(entity.getX() - closest.x, 0.0, entity.getZ() - closest.z).length();
            if (side <= width / 2.0 + entity.getBbWidth() / 2.0 + 0.3 && Math.abs(entity.getY() - from.y) < 2.5 && along < bestAlong) {
                best = entity;
                bestAlong = along;
            }
        }
        return best;
    }

    /** 瞬間移動の行き先：向いている方向へ、壁に当たらず立てる一番遠い場所。 */
    private static Vec3 blinkDestination(ServerPlayer player, double distance) {
        Vec3 direction = horizontalLook(player);
        Vec3 start = player.position();
        Vec3 eye = player.getEyePosition();
        for (double d = distance; d >= 0.5; d -= 0.5) {
            Vec3 candidate = start.add(direction.scale(d));
            Vec3 candidateEye = candidate.add(0.0, player.getEyeHeight(), 0.0);
            BlockHitResult sight = player.level().clip(new ClipContext(eye, candidateEye, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            if (sight.getType() != HitResult.Type.MISS) {
                continue;
            }
            for (double dy : new double[]{0.0, 1.0, -1.0}) {
                Vec3 spot = candidate.add(0.0, dy, 0.0);
                AABB moved = player.getBoundingBox().move(spot.subtract(start));
                if (player.level().noCollision(player, moved)) {
                    return spot;
                }
            }
        }
        return start;
    }

    private static int[] ids(List<LivingEntity> entities) {
        int[] ids = new int[entities.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = entities.get(i).getId();
        }
        return ids;
    }

    /** スキルに陣・強化があれば、その残る時間（tick）。 */
    private static int zoneTicks(SkillDef skill, int rank) {
        for (EffectDef effect : skill.effects()) {
            switch (effect.type()) {
                case ZONE, BUFF, SHIELD_BOND, BARRIER -> {
                    return Mth.ceil(effect.value("duration", rank) * 20.0);
                }
                default -> {
                }
            }
        }
        return 0;
    }

    /** 演出の強さ：溜めるスキルは溜めの割合（0〜1）、ほかは強化の倍率。 */
    private static float fxPower(SkillContext ctx) {
        return (float) (ctx.skill.isCharged() ? ctx.chargeRatio : ctx.multiplier);
    }

    private static void fx(SkillContext ctx, SkillFxS2C.Phase phase, Vec3 from, Vec3 to, double radius, int ticks, int[] targets,
                           float power) {
        new SkillFxS2C(phase, ctx.casterEntityId, ctx.skillIndex, from, to, (float) radius, ticks, targets, power)
                .sendNear(ctx.level.dimension(), to);
    }

    private static void playSound(ServerLevel level, Vec3 at, @Nullable ResourceLocation id, float volume) {
        SoundEvent sound = ModSounds.byId(id);
        if (sound != null) {
            level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, 1.0F);
        }
    }
}
