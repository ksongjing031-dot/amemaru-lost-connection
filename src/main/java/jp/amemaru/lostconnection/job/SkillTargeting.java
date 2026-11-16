package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.combat.CombatTargets;
import jp.amemaru.lostconnection.job.def.Affects;
import jp.amemaru.lostconnection.job.def.DeliveryDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * スキルの狙いを、サーバーで確かめる（Phase 0-B 9章「射程と視線を確かめる」）。
 *
 * <p>クライアントは「狙った相手（エンティティ番号）」か「狙った地点」を送ってくる。サーバーは、射程・向き・視線
 * （壁の向こうでないか）を確かめ、だめなら自分で探し直す。数値（ダメージなど）はクライアントから受け取らない。</p>
 */
public final class SkillTargeting {
    /** 狙いの結果。 */
    public record Aim(@Nullable LivingEntity entity, @Nullable Vec3 point, @Nullable SkillService.Failure failure) {
        static final Aim NONE = new Aim(null, null, null);

        static Aim fail(SkillService.Failure failure) {
            return new Aim(null, null, failure);
        }

        public int entityId() {
            return entity == null ? -1 : entity.getId();
        }
    }

    /** 狙った相手として認める、視線からのずれ（約40度）。通信の遅れの分、少し甘くしている。 */
    private static final double MIN_DOT_CLIENT = Math.cos(Math.toRadians(40.0));
    /** サーバーが自分で探すときの、視線からのずれ（約15度）。 */
    private static final double MIN_DOT_SEARCH = Math.cos(Math.toRadians(15.0));

    private SkillTargeting() {
    }

    /**
     * 狙いを決める。
     *
     * @param entityId クライアントが狙った相手（ないときは -1）
     * @param point    クライアントが狙った地点（ないときは null）
     */
    public static Aim resolve(ServerPlayer player, SkillDef skill, int rank, int entityId, @Nullable Vec3 point) {
        DeliveryDef delivery = skill.delivery();
        return switch (delivery.type()) {
            case TARGET, CHAIN -> resolveEntity(player, delivery.range(rank), delivery.affects(), delivery.includeSelf(), entityId);
            case POINT -> new Aim(null, resolvePoint(player, delivery.range(rank), point), null);
            default -> Aim.NONE;
        };
    }

    /** 詠唱が終わったときに、狙いをもう一度確かめる（相手が射程から出ていないか）。 */
    public static Aim recheck(ServerPlayer player, SkillDef skill, int rank, int entityId, @Nullable Vec3 point) {
        DeliveryDef delivery = skill.delivery();
        return switch (delivery.type()) {
            case TARGET, CHAIN -> {
                Entity entity = player.level().getEntity(entityId);
                if (entity instanceof LivingEntity living && valid(player, living, delivery.range(rank) + 2.0, delivery.affects(),
                        delivery.includeSelf(), -1.0)) {
                    yield new Aim(living, null, null);
                }
                yield Aim.fail(delivery.affects() == Affects.ALLIES ? SkillService.Failure.NO_ALLY : SkillService.Failure.NO_TARGET);
            }
            case POINT -> {
                if (point == null || point.distanceTo(player.getEyePosition()) > delivery.range(rank) + 4.0) {
                    yield Aim.fail(SkillService.Failure.NO_TARGET);
                }
                yield new Aim(null, point, null);
            }
            default -> Aim.NONE;
        };
    }

    private static Aim resolveEntity(ServerPlayer player, double range, Affects affects, boolean includeSelf, int entityId) {
        if (entityId >= 0) {
            Entity entity = player.level().getEntity(entityId);
            if (entity instanceof LivingEntity living && valid(player, living, range + 1.0, affects, includeSelf, MIN_DOT_CLIENT)) {
                return new Aim(living, null, null);
            }
        }
        LivingEntity found = search(player, range, affects, includeSelf);
        if (found != null) {
            return new Aim(found, null, null);
        }
        return Aim.fail(affects == Affects.ALLIES ? SkillService.Failure.NO_ALLY : SkillService.Failure.NO_TARGET);
    }

    /** 視線の先にいる相手を探す（ずれの小さい順、同じなら近い順）。 */
    @Nullable
    private static LivingEntity search(ServerPlayer player, double range, Affects affects, boolean includeSelf) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().inflate(range + 1.0);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> valid(player, entity, range + 1.0, affects, includeSelf, MIN_DOT_SEARCH));
        return candidates.stream()
                .min(Comparator.<LivingEntity>comparingDouble(entity -> -direction(eye, entity).dot(look))
                        .thenComparingDouble(entity -> entity.distanceToSqr(eye)))
                .orElse(null);
    }

    /**
     * 相手として認めるか。
     *
     * @param minDot 視線とのずれの下限（cos）。負なら向きは見ない
     */
    public static boolean valid(ServerPlayer player, LivingEntity entity, double range, Affects affects, boolean includeSelf,
                                double minDot) {
        boolean kind = affects == Affects.ENEMIES ? CombatTargets.isEnemy(entity) : CombatTargets.isAlly(player, entity, includeSelf);
        if (!kind) {
            return false;
        }
        if (entity == player) {
            return true;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 center = entity.getBoundingBox().getCenter();
        double distance = eye.distanceTo(center) - entity.getBbWidth() / 2.0;
        if (distance > range) {
            return false;
        }
        if (minDot > -1.0 && distance > 2.5 && direction(eye, entity).dot(player.getLookAngle()) < minDot) {
            return false;
        }
        return player.hasLineOfSight(entity);
    }

    private static Vec3 direction(Vec3 eye, Entity entity) {
        return entity.getBoundingBox().getCenter().subtract(eye).normalize();
    }

    /** 地点を決める。クライアントの地点が射程内で、壁の向こうでなければそれを使い、だめならサーバーの視線の先。足もとの地面に下ろす。 */
    static Vec3 resolvePoint(ServerPlayer player, double range, @Nullable Vec3 clientPoint) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 point = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        if (clientPoint != null && clientPoint.distanceTo(eye) <= range + 2.0) {
            BlockHitResult check = level.clip(new ClipContext(eye, clientPoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (check.getType() == HitResult.Type.MISS || check.getLocation().distanceTo(clientPoint) < 1.0) {
                point = clientPoint;
            }
        }
        return ground(level, point, player);
    }

    /** 地点を、下の地面（6ブロックまで）に下ろす。 */
    public static Vec3 ground(ServerLevel level, Vec3 point, Entity viewer) {
        Vec3 from = point.add(0.0, 0.5, 0.0);
        BlockHitResult down = level.clip(new ClipContext(from, from.add(0.0, -6.5, 0.0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, viewer));
        return down.getType() == HitResult.Type.MISS ? point : down.getLocation();
    }

    /** 地点のまわりの敵（高さは上下3ブロックまで）。 */
    public static List<LivingEntity> enemiesNear(ServerLevel level, Vec3 center, double radius) {
        AABB box = new AABB(center.x - radius, center.y - 3.0, center.z - radius, center.x + radius, center.y + 3.0, center.z + radius);
        List<LivingEntity> list = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, entity -> {
            if (!CombatTargets.isEnemy(entity)) {
                return false;
            }
            double dx = entity.getX() - center.x;
            double dz = entity.getZ() - center.z;
            double reach = radius + entity.getBbWidth() / 2.0;
            return dx * dx + dz * dz <= reach * reach;
        }));
        list.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
        return list;
    }

    /** 自分のまわりの敵、または味方。 */
    public static List<LivingEntity> around(ServerPlayer player, double radius, Affects affects, boolean includeSelf) {
        AABB box = player.getBoundingBox().inflate(radius, 3.0, radius);
        List<LivingEntity> list = new ArrayList<>(player.level().getEntitiesOfClass(LivingEntity.class, box, entity -> {
            boolean kind = affects == Affects.ENEMIES ? CombatTargets.isEnemy(entity) : CombatTargets.isAlly(player, entity, includeSelf);
            if (!kind) {
                return false;
            }
            double reach = radius + entity.getBbWidth() / 2.0;
            return entity.distanceToSqr(player) <= reach * reach;
        }));
        list.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)));
        return list;
    }
}
