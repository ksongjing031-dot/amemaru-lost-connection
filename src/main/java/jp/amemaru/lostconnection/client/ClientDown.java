package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.network.s2c.DownStateS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * ダウンしている人の様子（クライアント。Phase 4）。サーバーから {@link DownStateS2C} で受け取り、残り時間と起こした量は
 * 受け取ってからの tick で進める（受け取るのは、変わったときと1秒ごと）。
 */
public final class ClientDown {
    /** ある人のダウンの様子（受け取った時のクライアントの tick も覚える）。 */
    public record Info(int ticksLeft, int length, int progress, int needed, int reviverId, long receivedAt) {
        public int ticksLeftNow() {
            return (int) Math.max(0L, ticksLeft - (ticks - receivedAt));
        }

        public int secondsLeftNow() {
            return (ticksLeftNow() + 19) / 20;
        }

        /** 起こしている量（0〜1。起こしている人がいなければ0）。 */
        public float progressNow(float partialTick) {
            if (reviverId < 0 || needed <= 0) {
                return 0.0F;
            }
            return Math.min(1.0F, (progress + (ticks - receivedAt) + partialTick) / needed);
        }

        /** ダウンの残りの割合（1→0）。 */
        public float remainingNow() {
            return length <= 0 ? 0.0F : Math.min(1.0F, ticksLeftNow() / (float) length);
        }
    }

    private static final Map<Integer, Info> INFO = new HashMap<>();
    private static long ticks;

    private ClientDown() {
    }

    public static void apply(DownStateS2C message) {
        if (message.down()) {
            INFO.put(message.entityId(), new Info(message.ticksLeft(), message.length(), message.progress(), message.needed(),
                    message.reviverId(), ticks));
        } else {
            INFO.remove(message.entityId());
        }
    }

    @Nullable
    public static Info get(int entityId) {
        return INFO.get(entityId);
    }

    public static boolean isDown(Entity entity) {
        return INFO.containsKey(entity.getId());
    }

    /** 自分がダウンしているか。 */
    public static boolean localDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && INFO.containsKey(minecraft.player.getId());
    }

    public static Map<Integer, Info> all() {
        return INFO;
    }

    public static long now() {
        return ticks;
    }

    /** 毎 tick：時計を進め、いなくなった人と、残りが尽きてしばらくたった人を消す。 */
    static void tick(Minecraft minecraft) {
        ticks++;
        if (ticks % 20 != 0) {
            return;
        }
        for (Iterator<Map.Entry<Integer, Info>> it = INFO.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Info> entry = it.next();
            boolean gone = minecraft.level == null || minecraft.level.getEntity(entry.getKey()) == null;
            boolean stale = entry.getValue().ticksLeftNow() == 0 && ticks - entry.getValue().receivedAt() > entry.getValue().ticksLeft() + 200;
            if (gone || stale) {
                it.remove();
            }
        }
    }

    public static void reset() {
        INFO.clear();
    }
}
