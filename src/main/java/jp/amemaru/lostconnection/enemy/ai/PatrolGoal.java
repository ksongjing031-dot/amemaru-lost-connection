package jp.amemaru.lostconnection.enemy.ai;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * 決まった道を巡回する（石くれ兵）。道の点を順に歩き、着いたら少し立ち止まって見回す。相手がいる間は休む。
 */
public class PatrolGoal extends Goal {
    private final AmemaruMob mob;
    private final Supplier<List<BlockPos>> route;
    private final double speed;
    private int index;
    private int wait;

    public PatrolGoal(AmemaruMob mob, Supplier<List<BlockPos>> route, double speed) {
        this.mob = mob;
        this.route = route;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null && !mob.isReturning() && !route.get().isEmpty();
    }

    @Override
    public void tick() {
        List<BlockPos> points = route.get();
        if (points.isEmpty()) {
            return;
        }
        if (index >= points.size()) {
            index = 0;
        }
        BlockPos point = points.get(index);
        if (point.distToCenterSqr(mob.position()) < 2.5) {
            if (wait <= 0) {
                wait = 40 + mob.getRandom().nextInt(40);
            }
            mob.getNavigation().stop();
            if (--wait <= 0) {
                index = (index + 1) % points.size();
            }
            return;
        }
        if (mob.getNavigation().isDone() || mob.tickCount % 40 == 0) {
            mob.getNavigation().moveTo(point.getX() + 0.5, point.getY(), point.getZ() + 0.5, speed);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    /** 今向かっている点の番号（保存用）。 */
    public int index() {
        return index;
    }
}
