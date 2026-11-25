package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.LinkComboTracker;
import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.combat.TauntService;
import jp.amemaru.lostconnection.job.def.Affects;
import jp.amemaru.lostconnection.job.def.EffectDef;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.job.def.LinkType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 時間をおいて起きるスキルの処理：遅れて落ちる攻撃（流星群など）、地面に残る陣（氷結陣・誓いの円陣）、跳躍の着地（跳躍斬）。
 * 敵視の固定・連携の記録・状態の記録の後片付けも、ここでまとめて行う（毎 tick 行う処理を減らすため。Phase 0-B 11章）。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class SkillScheduler {
    /** 1つのレベルに同時に置ける陣の上限（負荷対策）。 */
    private static final int MAX_ZONES = 64;

    private record Delayed(ServerLevel level, long runAt, Runnable task) {
    }

    private static final class Zone {
        final SkillContext ctx;
        Vec3 center;
        final double radius;
        final long endTick;
        final int interval;
        long nextTick;
        final List<EffectDef> effects;
        /** 使った人について動くか（百裂拳・矢嵐・大車輪）。 */
        final boolean follow;
        final EffectDef.ZoneMode mode;
        final double angle;

        Zone(SkillContext ctx, Vec3 center, double radius, long endTick, int interval, List<EffectDef> effects, boolean follow,
             EffectDef.ZoneMode mode, double angle) {
            this.ctx = ctx;
            this.center = center;
            this.radius = radius;
            this.endTick = endTick;
            this.interval = interval;
            // ついて動く陣は、すぐに1回目を出す（溜めの間がないため）
            this.nextTick = ctx.level.getGameTime() + (follow ? 1 : interval);
            this.effects = effects;
            this.follow = follow;
            this.mode = mode;
            this.angle = angle;
        }
    }

    /** 跳躍中のプレイヤー（着地したら効果を出す）。 */
    private static final class Leap {
        final SkillContext ctx;
        final long startTick;
        final long deadline;
        final double radius;

        Leap(SkillContext ctx, long startTick, long deadline, double radius) {
            this.ctx = ctx;
            this.startTick = startTick;
            this.deadline = deadline;
            this.radius = radius;
        }
    }

    /** 跳んでから着地の判定を始めるまで（tick）。 */
    private static final int LEAP_GRACE = 4;
    /** 着地してから、落下のダメージを受けない時間（tick）。 */
    private static final int LANDING_GRACE = 10;

    private static final List<Delayed> DELAYED = new ArrayList<>();
    private static final List<Zone> ZONES = new ArrayList<>();
    private static final Map<UUID, Leap> LEAPS = new HashMap<>();
    /** 着地した時刻（落下のダメージを消すため）。 */
    private static final Map<UUID, Long> LANDED = new HashMap<>();

    private SkillScheduler() {
    }

    /** {@code delayTicks} 後に処理を動かす。 */
    public static void schedule(ServerLevel level, int delayTicks, Runnable task) {
        DELAYED.add(new Delayed(level, level.getGameTime() + Math.max(1, delayTicks), task));
    }

    /** 陣を置く。 */
    public static void addZone(SkillContext ctx, Vec3 center, double radius, int durationTicks, int intervalTicks, List<EffectDef> effects) {
        addZone(ctx, center, radius, durationTicks, intervalTicks, effects, false, EffectDef.ZoneMode.AREA, 60.0);
    }

    /** 陣を置く（ついて動く陣・扇の陣・いちばん近い敵を射る陣。Phase 6 その2）。 */
    public static void addZone(SkillContext ctx, Vec3 center, double radius, int durationTicks, int intervalTicks, List<EffectDef> effects,
                               boolean follow, EffectDef.ZoneMode mode, double angle) {
        long count = ZONES.stream().filter(zone -> zone.ctx.level == ctx.level).count();
        if (count >= MAX_ZONES) {
            AmemaruMod.LOGGER.warn("[amemaru] Too many skill zones in {}; ignoring a new one", ctx.level.dimension().location());
            return;
        }
        ZONES.add(new Zone(ctx, center, radius, ctx.level.getGameTime() + durationTicks, Math.max(1, intervalTicks), effects, follow,
                mode, angle));
    }

    /** 跳躍を始めた（{@code flightTicks} は飛ぶ時間の見積もり）。 */
    public static void addLeap(SkillContext ctx, ServerPlayer player, int flightTicks, double radius) {
        long now = ctx.level.getGameTime();
        LEAPS.put(player.getUUID(), new Leap(ctx, now, now + flightTicks + 30L, radius));
        LANDED.remove(player.getUUID());
    }

    /** 跳躍中、または着地したすぐ後か（落下のダメージを受けない）。 */
    public static boolean isLeaping(UUID player, long now) {
        if (LEAPS.containsKey(player)) {
            return true;
        }
        Long landed = LANDED.get(player);
        return landed != null && now - landed <= LANDING_GRACE;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        runDelayed();
        tickZones();
        tickLeaps(server);
        long ticks = server.getTickCount();
        if (ticks % 10 == 0) {
            TauntService.tick(server);
        }
        if (ticks % 20 == 0) {
            long now = server.overworld().getGameTime();
            LinkComboTracker.cleanup(now);
            ReactiveSkills.cleanup(now);
            jp.amemaru.lostconnection.combat.AfflictionService.cleanup(now);
            LANDED.values().removeIf(at -> now - at > LANDING_GRACE * 4L);
        }
        if (ticks % 400 == 0) {
            StatusService.cleanup(server.overworld().getGameTime());
        }
    }

    /** 跳躍の着地を見る：跳んで少したってから、地面（か水）に着いたら効果を出す。遅すぎるときも出す。 */
    private static void tickLeaps(MinecraftServer server) {
        if (LEAPS.isEmpty()) {
            return;
        }
        List<Map.Entry<UUID, Leap>> landed = new ArrayList<>();
        Iterator<Map.Entry<UUID, Leap>> it = LEAPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Leap> entry = it.next();
            Leap leap = entry.getValue();
            ServerPlayer player = leap.ctx.caster();
            if (player == null) {
                it.remove();
                continue;
            }
            long now = leap.ctx.level.getGameTime();
            player.fallDistance = 0.0F;
            boolean touched = now - leap.startTick >= LEAP_GRACE && (player.onGround() || player.isInWater() || player.isInLava());
            if (touched || now >= leap.deadline) {
                it.remove();
                landed.add(entry);
                LANDED.put(entry.getKey(), now);
            }
        }
        for (Map.Entry<UUID, Leap> entry : landed) {
            Leap leap = entry.getValue();
            ServerPlayer player = leap.ctx.caster();
            if (player == null) {
                continue;
            }
            try {
                SkillExecutor.leapLanded(leap.ctx, player, leap.radius);
            } catch (RuntimeException e) {
                AmemaruMod.LOGGER.error("[amemaru] A leap landing failed", e);
            }
        }
    }

    /** 跳躍の間と着地のすぐ後は、落下のダメージを受けない。 */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLeaping(player.getUUID(), player.level().getGameTime())) {
            event.setDistance(0.0F);
            event.setCanceled(true);
        }
    }

    private static void runDelayed() {
        if (DELAYED.isEmpty()) {
            return;
        }
        List<Delayed> due = new ArrayList<>();
        Iterator<Delayed> it = DELAYED.iterator();
        while (it.hasNext()) {
            Delayed delayed = it.next();
            if (delayed.level.getGameTime() >= delayed.runAt) {
                due.add(delayed);
                it.remove();
            }
        }
        for (Delayed delayed : due) {
            try {
                delayed.task.run();
            } catch (RuntimeException e) {
                AmemaruMod.LOGGER.error("[amemaru] A delayed skill effect failed", e);
            }
        }
    }

    private static void tickZones() {
        Iterator<Zone> it = ZONES.iterator();
        while (it.hasNext()) {
            Zone zone = it.next();
            long now = zone.ctx.level.getGameTime();
            if (now >= zone.endTick) {
                it.remove();
                continue;
            }
            ServerPlayer caster = zone.ctx.caster();
            if (zone.follow) {
                if (caster == null || DownHandler.isDown(caster)) {
                    it.remove();
                    continue;
                }
                zone.center = caster.position();
            }
            if (now < zone.nextTick) {
                continue;
            }
            zone.nextTick = now + zone.interval;
            List<LivingEntity> enemies;
            if (zone.mode == EffectDef.ZoneMode.CONE && caster != null) {
                enemies = SkillTargeting.inCone(caster, zone.radius, zone.angle);
            } else {
                enemies = SkillTargeting.enemiesNear(zone.ctx.level, zone.center, zone.radius);
            }
            if (zone.mode == EffectDef.ZoneMode.NEAREST) {
                enemies = enemies.isEmpty() ? List.of() : List.of(enemies.get(0));
                if (!enemies.isEmpty() && caster != null) {
                    // 矢嵐：いちばん近い敵へ矢を放つ（見た目は TRIGGER で送る）
                    LivingEntity shot = enemies.get(0);
                    new SkillFxS2C(SkillFxS2C.Phase.TRIGGER, caster.getId(), zone.ctx.skillIndex, caster.getEyePosition(),
                            shot.getBoundingBox().getCenter(), 0.0F, 0, new int[]{shot.getId()}, 0.0F)
                            .sendNear(zone.ctx.level.dimension(), caster.position());
                }
            }
            List<LivingEntity> allies = null;
            if (caster != null && zone.ctx.skill.link() == LinkType.STARTER) {
                // 陣の中にいる敵には、起点の印をつけ続ける（ほかの人の追撃がつながる）
                for (LivingEntity target : enemies) {
                    LinkComboTracker.onSkillHit(target, caster, LinkType.STARTER, now);
                }
            }
            for (EffectDef effect : zone.effects) {
                List<LivingEntity> targets;
                if (effect.to() == Affects.ALLIES) {
                    if (allies == null) {
                        allies = SkillTargeting.alliesNear(zone.ctx.level, zone.center, zone.radius);
                    }
                    targets = allies;
                } else {
                    targets = enemies;
                }
                for (LivingEntity target : targets) {
                    SkillExecutor.applyZoneEffect(zone.ctx, effect, target, zone.center);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clear();
    }

    /** すべて消す（サーバーが止まったとき）。 */
    public static void clear() {
        DELAYED.clear();
        ZONES.clear();
        LEAPS.clear();
        LANDED.clear();
        TauntService.clear();
        LinkComboTracker.clear();
        StatusService.clear();
        ReactiveSkills.clear();
        jp.amemaru.lostconnection.combat.AfflictionService.clear();
        jp.amemaru.lostconnection.combat.GuardianHeart.clear();
        ComboService.clear();
        HitHooks.clear();
        StarRingService.clear();
    }
}
