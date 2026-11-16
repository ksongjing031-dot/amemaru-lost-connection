package jp.amemaru.lostconnection.core.world;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * このModに合わせたゲームルールにする（Phase 0-B 2章「プレイヤーの遊ばせ方」）。
 *
 * <p>configの {@code world.applyGameRules} がオンのとき、サーバーの起動時に呼ばれる。
 * すでに同じ値なら何もしない。</p>
 */
public final class GameRuleSetup {
    /** ルールの名前と、するべき値。 */
    private static final Map<String, Boolean> DESIRED = new LinkedHashMap<>();

    static {
        // HPは独自に回復するため、バニラの自然回復を止める
        DESIRED.put("naturalRegeneration", false);
        // 敵は塔の仕組みで出すため、自然な発生を止める
        DESIRED.put("doMobSpawning", false);
        DESIRED.put("doPatrolSpawning", false);
        DESIRED.put("doTraderSpawning", false);
        DESIRED.put("doInsomnia", false);
        // 街を守る
        DESIRED.put("doFireTick", false);
        DESIRED.put("mobGriefing", false);
        // 死亡で持ち物を失わない（デスペナルティは所持金。Phase 4）
        DESIRED.put("keepInventory", true);
    }

    private GameRuleSetup() {
    }

    public static void apply(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        List<String> changed = new ArrayList<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                Boolean wanted = DESIRED.get(key.getId());
                if (wanted == null) {
                    return;
                }
                GameRules.BooleanValue value = rules.getRule(key);
                if (value.get() != wanted) {
                    value.set(wanted, server);
                    changed.add(key.getId() + "=" + wanted);
                }
            }
        });
        if (!changed.isEmpty()) {
            AmemaruMod.LOGGER.info("[amemaru] Game rules changed for this mod: {}", String.join(", ", changed));
        }
    }
}
