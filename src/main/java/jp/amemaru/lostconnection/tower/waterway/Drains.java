package jp.amemaru.lostconnection.tower.waterway;

import jp.amemaru.lostconnection.enemy.boss.AmemaruBoss;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorRuntime;
import jp.amemaru.lostconnection.tower.TowerService;
import net.minecraft.server.level.ServerLevel;

/**
 * ボスの部屋の排水のハンドル（Phase 8 その2：3階のボス「大水門の番人ハイドラム」。Phase 0-A 9章「水位を下げると胸の炉心が露出して
 * 弱点になる」）。2つを同時に回すと、その階のボスに知らせる。
 */
public final class Drains {
    private Drains() {
    }

    /** 排水を聞くボス。 */
    public interface Listener {
        /** 2つの排水のハンドルが同時に回された。水を引けたら true。 */
        boolean onDrain(ServerLevel level);
    }

    static boolean both(ServerLevel level, FloorDef def) {
        FloorRuntime runtime = TowerService.runtime(def.number());
        AmemaruBoss boss = runtime == null || runtime.bossRoom() == null ? null : runtime.bossRoom().boss();
        return boss instanceof Listener listener && boss.isAlive() && listener.onDrain(level);
    }
}
