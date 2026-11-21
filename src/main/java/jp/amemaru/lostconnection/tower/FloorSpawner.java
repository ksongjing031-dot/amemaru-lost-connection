package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.IshikureSoldier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 階の敵の湧く所（Phase 0-B 11章「敵の数：階ごと・区画ごとに、同時に出る数の上限をJSONで決める。人のいない階には敵を出さない」）。
 *
 * <ul>
 *   <li>湧く所ごとに、{@code activation} の中に人がいる間だけ、{@code respawn_seconds} ごとに {@code max} まで湧かせる（{@code group} の数ずつ）</li>
 *   <li>湧いた敵は保存しない（人が離れて区画が閉じたら消え、また人が来たら出し直す）。近くにだれもいなくなった敵は消す</li>
 *   <li>巡回の道（{@code route}）があれば、その道を回る（石くれ兵）</li>
 *   <li>湧く所は、見えている人の目の前を避ける（12ブロック以内に人がいる所には出さない）</li>
 * </ul>
 */
public final class FloorSpawner {
    private static final double SAFE_DISTANCE = 12.0;
    private static final double DESPAWN_DISTANCE = 96.0;

    private final FloorDef floor;
    private final Map<String, List<UUID>> alive = new HashMap<>();
    private final Map<String, Long> nextSpawn = new HashMap<>();

    public FloorSpawner(FloorDef floor) {
        this.floor = floor;
    }

    /** 数秒ごとに呼ぶ。 */
    public void tick(ServerLevel level, List<ServerPlayer> onFloor) {
        long now = level.getGameTime();
        RandomSource random = level.getRandom();
        for (FloorDef.SpawnZone zone : floor.spawns()) {
            List<UUID> list = alive.computeIfAbsent(zone.id(), k -> new ArrayList<>());
            // いなくなった敵を数えない。だれも近くにいない敵は消す
            list.removeIf(id -> {
                Entity entity = level.getEntity(id);
                if (!(entity instanceof AmemaruMob mob) || !mob.isAlive()) {
                    return true;
                }
                if (!near(mob.position(), onFloor, DESPAWN_DISTANCE) && mob.getTarget() == null) {
                    mob.discard();
                    return true;
                }
                return false;
            });
            Vec3 center = floor.world(zone.center());
            if (!near(center, onFloor, zone.activation())) {
                continue;
            }
            if (list.size() >= zone.max() || now < nextSpawn.getOrDefault(zone.id(), 0L)) {
                continue;
            }
            int group = zone.groupMin() + (zone.groupMax() > zone.groupMin() ? random.nextInt(zone.groupMax() - zone.groupMin() + 1) : 0);
            group = Math.min(group, zone.max() - list.size());
            Vec3 groupCenter = null;
            for (int i = 0; i < group; i++) {
                Vec3 at = findSpot(level, zone, groupCenter, onFloor, random);
                if (at == null) {
                    break;
                }
                if (groupCenter == null) {
                    groupCenter = at;
                }
                AmemaruMob mob = spawn(level, zone, at, random);
                if (mob != null) {
                    list.add(mob.getUUID());
                }
            }
            nextSpawn.put(zone.id(), now + zone.respawnSeconds() * 20L);
        }
    }

    private static boolean near(Vec3 pos, List<ServerPlayer> players, double range) {
        for (ServerPlayer player : players) {
            if (player.position().distanceToSqr(pos) < range * range) {
                return true;
            }
        }
        return false;
    }

    /** 湧かせる所（地面の上で、足もとと頭の所が空いていて、近くに人がいない所）。 */
    private Vec3 findSpot(ServerLevel level, FloorDef.SpawnZone zone, Vec3 groupCenter, List<ServerPlayer> players, RandomSource random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            Vec3 base;
            double radius;
            if (groupCenter != null) {
                base = groupCenter;
                radius = 3.0;
            } else if (!zone.route().isEmpty()) {
                BlockPos point = zone.route().get(random.nextInt(zone.route().size()));
                base = floor.world(Vec3.atBottomCenterOf(point));
                radius = 2.0;
            } else {
                base = floor.world(zone.center());
                radius = zone.radius();
            }
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(random.nextDouble()) * radius;
            int x = (int) Math.floor(base.x + Math.cos(a) * r);
            int z = (int) Math.floor(base.z + Math.sin(a) * r);
            if (!floor.contains(new Vec3(x + 0.5, base.y, z + 0.5))) {
                continue;
            }
            Integer y = ground(level, x, (int) Math.floor(base.y) + 8, z, 20);
            if (y == null) {
                continue;
            }
            Vec3 at = new Vec3(x + 0.5, y, z + 0.5);
            if (groupCenter == null && near(at, players, SAFE_DISTANCE)) {
                continue;
            }
            return at;
        }
        return null;
    }

    /** 上から下へ、立てる地面を探す（足もとの高さを返す）。足もとと頭の所は、草や花のような当たりのないものでもよい。 */
    private static Integer ground(ServerLevel level, int x, int top, int z, int depth) {
        for (int y = top; y > top - depth; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockState below = level.getBlockState(feet.below());
            if (!below.isAir() && below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                    && passable(level, feet) && passable(level, feet.above()) && level.getFluidState(feet.below()).isEmpty()) {
                return y;
            }
        }
        return null;
    }

    private static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty()
                && !(state.getBlock() instanceof FloorSealBlock);
    }

    private AmemaruMob spawn(ServerLevel level, FloorDef.SpawnZone zone, Vec3 at, RandomSource random) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(zone.enemy());
        if (type == null) {
            return null;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof AmemaruMob mob)) {
            return null;
        }
        mob.moveTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
        mob.setHome(BlockPos.containing(at));
        mob.setTransient(true);
        if (mob instanceof IshikureSoldier soldier && !zone.route().isEmpty()) {
            List<BlockPos> route = new ArrayList<>();
            for (BlockPos point : zone.route()) {
                route.add(floor.world(point));
            }
            soldier.setPatrol(route);
        }
        level.addFreshEntity(mob);
        return mob;
    }

    /** 湧いた敵をすべて消す（階を建て直したとき）。 */
    public void clear(ServerLevel level) {
        for (List<UUID> list : alive.values()) {
            for (UUID id : list) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
        alive.clear();
        nextSpawn.clear();
    }

    /** 今いる数（管理者の確かめ用）。 */
    public int count() {
        int total = 0;
        for (List<UUID> list : alive.values()) {
            total += list.size();
        }
        return total;
    }
}
