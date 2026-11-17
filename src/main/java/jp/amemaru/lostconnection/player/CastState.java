package jp.amemaru.lostconnection.player;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 詠唱中・溜め中のスキル（保存しない）。詠唱が終わった時点（溜めはキーを離した時点）で、もう一度確かめてから効果を出す。
 *
 * @param skill        詠唱しているスキル
 * @param rank         そのスキルの段階
 * @param slot         スキル枠の番号
 * @param startTick    詠唱を始めた時刻
 * @param endTick      詠唱が終わる時刻
 * @param targetEntity 狙った相手のエンティティ番号（ないときは -1）
 * @param targetPoint  狙った地点（ないときは null）
 * @param charging     溜め（キーを押している間）か。溜めのときは {@code endTick} が溜めきる時刻
 */
public record CastState(ResourceLocation skill, int rank, int slot, long startTick, long endTick, int targetEntity,
                        @Nullable Vec3 targetPoint, boolean charging) {
    /** 溜めの割合（0〜1）。 */
    public double chargeRatio(long now) {
        return Math.max(0.0, Math.min(1.0, (now - startTick) / (double) totalTicks()));
    }

    public int totalTicks() {
        return (int) Math.max(1L, endTick - startTick);
    }

    public int remaining(long now) {
        return (int) Math.max(0L, endTick - now);
    }
}
