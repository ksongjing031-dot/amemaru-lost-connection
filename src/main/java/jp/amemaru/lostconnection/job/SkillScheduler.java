package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.LinkComboTracker;
import jp.amemaru.lostconnection.combat.TauntService;
import jp.amemaru.lostconnection.job.def.EffectDef;
import jp.amemaru.lostconnection.job.def.LinkType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 時間をおいて起きるスキルの処理：遅れて落ちる攻撃（流星群など）と、地面に残る陣（氷結陣など）。
 * 敵視の固定と連携の記録の後片付けも、ここでまとめて行う（毎 tick 行う処理を減らすため。Phase 0-B 11章）。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class SkillScheduler {
    /** 1つのレベルに同時に置ける陣の上限（負荷対策）。 */
    private static final int MAX_ZONES = 64;

    private record Delayed(ServerLevel level, long runAt, Runnable task) {
    }

    private static final class Zone {
        final SkillContext ctx;
        final Vec3 center;
        final double radius;
        final long endTick;
        final int interval;
        long nextTick;
        final List<EffectDef> effects;

        Zone(SkillContext ctx, Vec3 center, double radius, long endTick, int interval, List<EffectDef> effects) {
            this.ctx = ctx;
            this.center = center;
            this.radius = radius;
            this.endTick = endTick;
            this.interval = interval;
            this.nextTick = ctx.level.getGameTime() + interval;
            this.effects = effects;
        }
    }

    private static final List<Delayed> DELAYED = new ArrayList<>();
    private static final List<Zone> ZONES = new ArrayList<>();

    private SkillScheduler() {
    }

    /** {@code delayTicks} 後に処理を動かす。 */
    public static void schedule(ServerLevel level, int delayTicks, Runnable task) {
        DELAYED.add(new Delayed(level, level.getGameTime() + Math.max(1, delayTicks), task));
    }

    /** 陣を置く。 */
    public static void addZone(SkillContext ctx, Vec3 center, double radius, int durationTicks, int intervalTicks, List<EffectDef> effects) {
        long count = ZONES.stream().filter(zone -> zone.ctx.level == ctx.level).count();
        if (count >= MAX_ZONES) {
            AmemaruMod.LOGGER.warn("[amemaru] Too many skill zones in {}; ignoring a new one", ctx.level.dimension().location());
            return;
        }
        ZONES.add(new Zone(ctx, center, radius, ctx.level.getGameTime() + durationTicks, Math.max(1, intervalTicks), effects));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        runDelayed();
        tickZones();
        long ticks = server.getTickCount();
        if (ticks % 10 == 0) {
            TauntService.tick(server);
        }
        if (ticks % 20 == 0) {
            LinkComboTracker.cleanup(server.overworld().getGameTime());
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
            if (now < zone.nextTick) {
                continue;
            }
            zone.nextTick = now + zone.interval;
            List<LivingEntity> targets = SkillTargeting.enemiesNear(zone.ctx.level, zone.center, zone.radius);
            ServerPlayer caster = zone.ctx.caster();
            for (LivingEntity target : targets) {
                if (caster != null && zone.ctx.skill.link() == LinkType.STARTER) {
                    // 陣の中にいる敵には、起点の印をつけ続ける（ほかの人の追撃がつながる）
                    LinkComboTracker.onSkillHit(target, caster, LinkType.STARTER, now);
                }
                for (EffectDef effect : zone.effects) {
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
        TauntService.clear();
        LinkComboTracker.clear();
    }
}
