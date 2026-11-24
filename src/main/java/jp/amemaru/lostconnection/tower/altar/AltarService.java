package jp.amemaru.lostconnection.tower.altar;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.boss.AmemaruBoss;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.forest.WaypointService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 5階「古代守護者の祭壇」の仕組み（11月24日【補完】）。
 *
 * <ul>
 *   <li>警戒：監視眼ウォッチャーに見つかると、階の「警戒」が1つ上がる（Phase 0-A 9章「見つかると守護者が強くなる」）。
 *       警戒は {@link #ALERT_SECONDS} 秒で1つずつ消え、{@link #MAX_ALERT} まで。守護者は戦いが始まったときの警戒1つにつき
 *       攻撃 +{@link #ALERT_ATTACK_PERCENT}%</li>
 *   <li>奈落：階の定義の {@code abyss_y} より下へ落ちた人は、ともした中継の灯（いちばん近い所。なければ入口）へ戻され、
 *       最大HPの {@link #ABYSS_DAMAGE}（割合）のダメージ。奈落へ落ちた魔物は消える</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class AltarService {
    public static final int MAX_ALERT = 3;
    public static final int ALERT_SECONDS = 300;
    public static final int ALERT_ATTACK_PERCENT = 10;
    public static final double ABYSS_DAMAGE = 0.15;
    /** 階ごとの警戒（消える時刻の並び。ゲームの時間）。 */
    private static final Map<Integer, List<Long>> ALERTS = new HashMap<>();

    private AltarService() {
    }

    // ---------------------------------------------------------------- 警戒

    /** 警戒を上げる（監視眼に見つかった）。上がったら true。 */
    public static boolean raiseAlert(ServerLevel level, int floor, ServerPlayer spotted, int amount) {
        List<Long> list = ALERTS.computeIfAbsent(floor, k -> new ArrayList<>());
        long now = level.getGameTime();
        list.removeIf(until -> until <= now);
        boolean raised = false;
        for (int i = 0; i < Math.max(1, amount); i++) {
            if (list.size() >= MAX_ALERT) {
                // いっぱいなら、いちばん古い警戒を延ばす
                list.sort(Long::compare);
                list.set(0, now + ALERT_SECONDS * 20L);
            } else {
                list.add(now + ALERT_SECONDS * 20L);
                raised = true;
            }
        }
        int count = list.size();
        FloorDef def = FloorDefs.byNumber(floor);
        for (ServerPlayer player : level.players()) {
            FloorDef at = FloorDefs.floorAt(level, player.position());
            if (at != null && at.number() == floor) {
                player.displayClientMessage(Component.translatable("message.amemaru.altar.alert", spotted.getDisplayName(), count, MAX_ALERT,
                        count * ALERT_ATTACK_PERCENT).withStyle(ChatFormatting.RED), false);
            }
        }
        level.playSound(null, spotted.getX(), spotted.getY(), spotted.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.7F, 1.6F);
        if (def != null) {
            AmemaruMod.LOGGER.info("[amemaru] Altar alert on floor {} raised to {} by {}", floor, count, spotted.getGameProfile().getName());
        }
        return raised;
    }

    /** 今の警戒（0〜{@link #MAX_ALERT}）。 */
    public static int alert(ServerLevel level, int floor) {
        List<Long> list = ALERTS.get(floor);
        if (list == null) {
            return 0;
        }
        long now = level.getGameTime();
        list.removeIf(until -> until <= now);
        return list.size();
    }

    /** 警戒を消す（自動テスト・管理者）。 */
    public static void clearAlert(int floor) {
        ALERTS.remove(floor);
    }

    // ---------------------------------------------------------------- 奈落

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 5 != 3) {
            return;
        }
        ServerLevel tower = server.getLevel(FloorDefs.TOWER);
        if (tower == null) {
            return;
        }
        for (FloorDef def : FloorDefs.all()) {
            int abyss = def.features().abyssY();
            if (abyss == FloorFeatures.NO_ABYSS) {
                continue;
            }
            double limit = def.originVec().y + abyss;
            for (ServerPlayer player : tower.players()) {
                if (player.isSpectator() || player.getY() >= limit || !insideXZ(def, player.position())) {
                    continue;
                }
                rescue(player, def);
            }
            if (server.getTickCount() % 40 == 3) {
                Vec3 o = def.originVec();
                AABB below = new AABB(o.x, limit - 64, o.z, o.x + def.sizeX(), limit, o.z + def.sizeZ());
                for (AmemaruMob mob : tower.getEntitiesOfClass(AmemaruMob.class, below)) {
                    if (!(mob instanceof AmemaruBoss)) {
                        mob.discard();
                    }
                }
            }
        }
    }

    private static boolean insideXZ(FloorDef def, Vec3 pos) {
        Vec3 o = def.originVec();
        return pos.x >= o.x && pos.x < o.x + def.sizeX() && pos.z >= o.z && pos.z < o.z + def.sizeZ();
    }

    /** 奈落に落ちた人を、いちばん近い、ともした中継の灯（なければ入口）へ戻す。 */
    public static void rescue(ServerPlayer player, FloorDef def) {
        Vec3 fell = player.position();
        Vec3 to = null;
        float yaw = def.entrance().yaw();
        double best = Double.MAX_VALUE;
        for (FloorFeatures.Waypoint point : def.features().waypoints()) {
            if (!WaypointService.Data.get(player.server).has(player.getUUID(), def.number(), point.index())) {
                continue;
            }
            Vec3 at = def.world(point.arrive().pos());
            double d = at.distanceToSqr(fell.x, at.y, fell.z);
            if (d < best) {
                best = d;
                to = at;
                yaw = point.arrive().yaw();
            }
        }
        if (to == null) {
            to = def.world(def.entrance().pos());
        }
        player.fallDistance = 0.0F;
        player.teleportTo(player.serverLevel(), to.x, to.y, to.z, yaw, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        float damage = (float) (player.getMaxHealth() * ABYSS_DAMAGE);
        if (player.getHealth() > damage + 1.0F) {
            player.setHealth(player.getHealth() - damage);
        } else {
            player.setHealth(1.0F);
        }
        player.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 1.0, to.z, 30, 0.4, 0.8, 0.4, 0.05);
        player.serverLevel().playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.7F);
        player.displayClientMessage(Component.translatable("message.amemaru.altar.abyss").withStyle(ChatFormatting.GRAY), true);
    }
}
