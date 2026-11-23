package jp.amemaru.lostconnection.enemy;

import net.minecraft.core.BlockPos;

import java.util.List;

/** 決まった道を巡回する敵（石くれ兵・樹皮兵）。塔の階の湧く所の {@code route} を受け取る（Phase 8 その1 で分けた）。 */
public interface Patroller {
    void setPatrol(List<BlockPos> points);
}
