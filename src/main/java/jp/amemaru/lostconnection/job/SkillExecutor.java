package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.combat.CombatTargets;
import jp.amemaru.lostconnection.combat.LinkComboTracker;
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
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
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
        playSound(level, origin, skill.sound(), 1.0F);

        switch (delivery.type()) {
            case SELF -> {
                fx(ctx, SkillFxS2C.Phase.RELEASE, origin, origin, 0, zoneTicks(skill, rank), new int[]{caster.getId()});
                apply(ctx, List.of(caster), null, origin, null);
            }
            case AROUND -> {
                double radius = delivery.radius(rank);
                List<LivingEntity> targets = SkillTargeting.around(caster, radius, delivery.affects(), delivery.includeSelf());
                fx(ctx, SkillFxS2C.Phase.RELEASE, origin, origin, radius, zoneTicks(skill, rank), ids(targets));
                apply(ctx, targets, null, origin, null);
            }
            case TARGET -> {
                LivingEntity target = aim.entity();
                if (target == null) {
                    return;
                }
                fx(ctx, SkillFxS2C.Phase.RELEASE, caster.getEyePosition(), target.getBoundingBox().getCenter(), 0, 0,
                        new int[]{target.getId()});
                apply(ctx, List.of(target), null, target.position(), null);
            }
            case POINT -> {
                Vec3 center = aim.point() != null ? aim.point() : origin;
                double radius = delivery.radius(rank);
                int delay = Mth.ceil(delivery.impactDelay(rank) * 20.0);
                fx(ctx, SkillFxS2C.Phase.RELEASE, caster.getEyePosition(), center, radius, delay > 0 ? delay : zoneTicks(skill, rank),
                        new int[0]);
                if (delay > 0) {
                    SkillScheduler.schedule(level, delay, () -> impact(ctx, center, radius));
                } else {
                    impact(ctx, center, radius);
                }
            }
            case PROJECTILE -> {
                LampBolt.launch(ctx, caster, delivery.speed(rank), delivery.range(rank), delivery.radius(rank));
                fx(ctx, SkillFxS2C.Phase.RELEASE, caster.getEyePosition(), caster.getEyePosition().add(caster.getLookAngle()),
                        0, 0, new int[0]);
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
                        first == null ? new int[0] : new int[]{first.getId()});
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
                List<LivingEntity> chain = chain(ctx, first, delivery.jumps(rank), delivery.jumpRange(rank));
                double[] falloff = new double[chain.size()];
                double factor = delivery.falloff(rank);
                for (int i = 0; i < falloff.length; i++) {
                    falloff[i] = Math.pow(factor, i);
                }
                fx(ctx, SkillFxS2C.Phase.RELEASE, caster.getEyePosition(), first.getBoundingBox().getCenter(), 0, 0, ids(chain));
                apply(ctx, chain, falloff, first.position(), null);
            }
            case BLINK -> {
                Vec3 to = blinkDestination(caster, delivery.distance(rank));
                caster.teleportTo(to.x, to.y, to.z);
                caster.fallDistance = 0.0F;
                fx(ctx, SkillFxS2C.Phase.RELEASE, origin, to, 0, 0, new int[]{caster.getId()});
                apply(ctx, List.of(caster), null, to, null);
            }
        }
    }

    /** 地点に着弾する（流星群など）。 */
    private static void impact(SkillContext ctx, Vec3 center, double radius) {
        List<LivingEntity> targets = SkillTargeting.enemiesNear(ctx.level, center, radius);
        fx(ctx, SkillFxS2C.Phase.IMPACT, center, center, radius, zoneTicks(ctx.skill, ctx.rank), ids(targets));
        playSound(ctx.level, center, ctx.skill.impactSound(), 1.0F);
        apply(ctx, targets, null, center, null);
    }

    /** 弾が敵に当たったとき（{@link LampBolt}）。 */
    public static void projectileHit(SkillContext ctx, LivingEntity target, Entity projectile) {
        Vec3 at = target.getBoundingBox().getCenter();
        fx(ctx, SkillFxS2C.Phase.IMPACT, projectile.position(), at, 0.6F, 0, new int[]{target.getId()});
        playSound(ctx.level, at, ctx.skill.impactSound(), 0.8F);
        apply(ctx, List.of(target), null, at, projectile);
    }

    // ---------------------------------------------------------------- 効果

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
        ServerPlayer caster = ctx.caster();
        PlayerData casterData = caster == null ? null : PlayerDataCapability.get(caster).orElse(null);
        long now = ctx.level.getGameTime();

        // ゲージを使って強める（旋風断など）
        if (caster != null && casterData != null) {
            for (EffectDef effect : skill.effects()) {
                if (effect.type() == EffectDef.Type.GAUGE_EMPOWER) {
                    double consume = effect.value("consume", rank);
                    if (casterData.getGauge() + 1.0E-6 >= consume && GaugeService.consume(caster, casterData, consume)) {
                        ctx.multiplier *= effect.value("multiplier", rank);
                    }
                }
            }
        }

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
                if (enemy && effect.stat() != null) {
                    double power = ctx.stat(effect.stat()) * effect.value("coefficient", rank) * multiplier;
                    CombatService.dealSkillDamage(ctx, target, power, direct);
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
                    });
                }
            }
            case BUFF -> {
                if (target instanceof ServerPlayer player && effect.buff() != null) {
                    applyBuff(player, effect.buff(), Mth.ceil(effect.value("duration", rank) * 20.0),
                            Math.min(95.0, effect.value("value", rank) * multiplier), now, caster);
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
            default -> {
            }
        }
    }

    /** 陣の中の敵に効果をかける（{@link SkillScheduler}）。 */
    static void applyZoneEffect(SkillContext ctx, EffectDef effect, LivingEntity target, Vec3 center) {
        ServerPlayer caster = ctx.caster();
        switch (effect.type()) {
            case DAMAGE -> {
                if (effect.stat() != null) {
                    double power = ctx.stat(effect.stat()) * effect.value("coefficient", ctx.rank) * ctx.multiplier;
                    CombatService.dealSkillDamage(ctx, target, power, null);
                }
            }
            case STATUS -> addStatus(effect, target, ctx.rank, caster);
            case KNOCKBACK -> knockback(target, effect.value("strength", ctx.rank), center);
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
        target.addEffect(new MobEffectInstance(mobEffect, ticks, amplifier, false, false, true), source);
    }

    private static void knockback(LivingEntity target, double strength, Vec3 from) {
        if (strength <= 0) {
            return;
        }
        target.knockback(strength, from.x - target.getX(), from.z - target.getZ());
        target.hurtMarked = true;
    }

    /** 数値つきの強化をかける（城壁の構え・不屈の誓い・不落の城）。 */
    public static void applyBuff(ServerPlayer player, BuffKind kind, int ticks, double value, long now, @Nullable Entity source) {
        PlayerDataCapability.get(player).ifPresent(data -> {
            BuffState buffs = data.getBuffs();
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

    private static Vec3 horizontalLook(ServerPlayer player) {
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

    /** スキルに陣があれば、その残る時間（tick）。 */
    private static int zoneTicks(SkillDef skill, int rank) {
        for (EffectDef effect : skill.effects()) {
            if (effect.type() == EffectDef.Type.ZONE) {
                return Mth.ceil(effect.value("duration", rank) * 20.0);
            }
            if (effect.type() == EffectDef.Type.BUFF) {
                return Mth.ceil(effect.value("duration", rank) * 20.0);
            }
        }
        return 0;
    }

    private static void fx(SkillContext ctx, SkillFxS2C.Phase phase, Vec3 from, Vec3 to, double radius, int ticks, int[] targets) {
        new SkillFxS2C(phase, ctx.casterEntityId, ctx.skillIndex, from, to, (float) radius, ticks, targets)
                .sendNear(ctx.level.dimension(), to);
    }

    private static void playSound(ServerLevel level, Vec3 at, @Nullable ResourceLocation id, float volume) {
        SoundEvent sound = ModSounds.byId(id);
        if (sound != null) {
            level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, 1.0F);
        }
    }
}
