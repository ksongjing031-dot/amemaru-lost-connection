package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.combat.CombatTargets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * ロックオン（11月24日のご指示「スキルにターゲットロックオン機能を追加しよう」）：スキルを使う前に、
 * ロックした敵のほうへ向きを合わせる（サーバー）。
 *
 * <p>クライアントは、ロックしている間に使ったスキルへ、ロックした敵の番号を添える（{@code UseSkillC2S}）。
 * ここでは、その敵が生きていて、同じ世界の {@value #MAX_DISTANCE} ブロック以内にいて、自分の敵であるときだけ向きを変える
 * （味方には向かない。回復などの味方向けのスキルは、ロックに関係なく今までどおり）。
 * 扇・線・飛び道具は、使った人の向きで飛ぶので、これでロックした敵に向かう。</p>
 * <p>詠唱のあるスキルと、溜めきって自動で放つスキルのために、向いた相手を覚えておき、放つときにもう一度向き直す。
 * 向きはサーバーの中だけで変える（クライアントは自分で、なめらかに向き直る。画面が急に跳ばないように、
 * {@code ServerPlayer#lookAt} の知らせは送らない）。</p>
 */
public final class LockOnFacing {
    /** これより遠い相手には向かない（クライアントは 32 ブロックで外れる。少しゆとりを持たせる）。 */
    public static final double MAX_DISTANCE = 48.0;

    private static final Map<ServerPlayer, Integer> LAST = new WeakHashMap<>();

    private LockOnFacing() {
    }

    /**
     * ロックした敵のほうへ向く。
     *
     * @return 向いたか（相手がいない・味方・遠すぎるときは向かず、覚えていた相手も忘れる）
     */
    public static boolean face(ServerPlayer player, int entityId) {
        Entity entity = entityId < 0 ? null : player.level().getEntity(entityId);
        if (!(entity instanceof LivingEntity living) || !valid(player, living)) {
            LAST.remove(player);
            return false;
        }
        LAST.put(player, entityId);
        turn(player, living);
        return true;
    }

    /** ロックせずに使ったとき：覚えていた相手を忘れる。 */
    public static void forget(ServerPlayer player) {
        LAST.remove(player);
    }

    /** 詠唱が終わったとき・溜めきったとき：使ったときにロックしていた敵がまだいれば、もう一度向き直す。 */
    public static void refresh(ServerPlayer player) {
        Integer id = LAST.get(player);
        if (id != null) {
            face(player, id);
        }
    }

    /** 覚えているロックの相手（ないときは -1）。 */
    public static int lockedId(ServerPlayer player) {
        Integer id = LAST.get(player);
        return id == null ? -1 : id;
    }

    /** 向いてよい相手か。 */
    public static boolean valid(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && !target.isRemoved() && target.level() == player.level()
                && target.distanceToSqr(player) <= MAX_DISTANCE * MAX_DISTANCE
                && CombatTargets.isEnemyOf(player, target);
    }

    /** 目から相手の胴の真ん中へ向く。 */
    public static void turn(ServerPlayer player, LivingEntity target) {
        float[] angles = angles(player.getEyePosition(), target.getBoundingBox().getCenter());
        player.setYRot(angles[0]);
        player.setXRot(angles[1]);
        player.setYHeadRot(angles[0]);
        player.setYBodyRot(angles[0]);
    }

    /** {@code from} から {@code to} を見る向き（左右・上下。度）。 */
    public static float[] angles(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
        float pitch = Mth.clamp((float) (-(Mth.atan2(dy, flat) * Mth.RAD_TO_DEG)), -90.0F, 90.0F);
        return new float[]{yaw, pitch};
    }
}
