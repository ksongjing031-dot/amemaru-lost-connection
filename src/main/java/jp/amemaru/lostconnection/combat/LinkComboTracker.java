package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.job.def.LinkType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 連携技の判定（Phase 0-A 13章）。敵ごとに「つないだプレイヤーの列」と「最後につないだ時刻」を持つ。
 *
 * <ul>
 *   <li>「起点」が当たると、列をその人だけにして始め直す。</li>
 *   <li>「追撃」が、まだ列にいない人から時間内（初期値4秒）に当たると、列に足して「連携！」（+30%）。
 *       列が3人以上なら「三連携」（+50%）。つなぐたびに時間は数え直す。</li>
 *   <li>同じ人の追撃や、時間切れの追撃は連携にならない。</li>
 * </ul>
 */
public final class LinkComboTracker {
    public enum Outcome {
        NONE,
        STARTED,
        LINK,
        LINK3
    }

    /**
     * @param outcome    何が起きたか
     * @param multiplier ダメージにかける倍率
     * @param players    つないだプレイヤー（連携のときだけ）
     */
    public record Result(Outcome outcome, double multiplier, List<UUID> players) {
        static final Result NONE = new Result(Outcome.NONE, 1.0, List.of());
    }

    private static final class Chain {
        final List<UUID> players = new ArrayList<>();
        long lastTick;
    }

    private static final Map<UUID, Chain> CHAINS = new HashMap<>();

    private LinkComboTracker() {
    }

    /** スキルが敵に当たったときに呼ぶ。 */
    public static Result onSkillHit(LivingEntity target, ServerPlayer caster, LinkType link, long now) {
        return onSkillHit(target.getUUID(), caster.getUUID(), link, now);
    }

    public static Result onSkillHit(UUID target, UUID caster, LinkType link, long now) {
        long window = windowTicks();
        switch (link) {
            case STARTER -> {
                Chain chain = new Chain();
                chain.players.add(caster);
                chain.lastTick = now;
                CHAINS.put(target, chain);
                return new Result(Outcome.STARTED, 1.0, List.of(caster));
            }
            case FINISHER -> {
                Chain chain = CHAINS.get(target);
                if (chain == null || now - chain.lastTick > window || chain.players.contains(caster)) {
                    return Result.NONE;
                }
                chain.players.add(caster);
                chain.lastTick = now;
                boolean three = chain.players.size() >= 3;
                double bonus = three ? ServerConfig.LINK3_BONUS.get() : ServerConfig.LINK_BONUS.get();
                return new Result(three ? Outcome.LINK3 : Outcome.LINK, 1.0 + bonus / 100.0, List.copyOf(chain.players));
            }
            default -> {
                return Result.NONE;
            }
        }
    }

    /** 連携・支援の時間（tick）。 */
    public static long windowTicks() {
        return Math.round(ServerConfig.LINK_WINDOW_SECONDS.get() * 20.0);
    }

    /** 時間切れの列を消す（1秒ごと）。 */
    public static void cleanup(long now) {
        long window = windowTicks();
        CHAINS.values().removeIf(chain -> now - chain.lastTick > window * 2);
    }

    /** サーバーが止まったときなどに、すべて消す。 */
    public static void clear() {
        CHAINS.clear();
    }
}
