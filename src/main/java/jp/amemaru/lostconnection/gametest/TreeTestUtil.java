package jp.amemaru.lostconnection.gametest;

import jp.amemaru.lostconnection.job.SkillTreeService;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.job.def.SkillTreeDef;
import jp.amemaru.lostconnection.player.PlayerData;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * スキルツリーの自動テストの道具（11月24日）：節を探す・取れている節から道をたどって取る。
 */
final class TreeTestUtil {
    private TreeTestUtil() {
    }

    /** 職業のスキルツリー（なければテストを落とす）。 */
    static SkillTreeDef tree(ResourceLocation job) {
        SkillTreeDef tree = SkillRegistry.get().tree(job);
        if (tree == null) {
            throw new GameTestAssertException("スキルツリーがありません：" + job);
        }
        return tree;
    }

    /** スキルの節の名前（なければテストを落とす）。 */
    static String nodeOf(SkillTreeDef tree, ResourceLocation skill) {
        SkillTreeDef.Node node = tree.nodeOf(skill);
        if (node == null) {
            throw new GameTestAssertException("スキルツリーに、スキルの節がありません：" + skill);
        }
        return node.id();
    }

    /** 2つの節の、両方のとなりにある節（なければテストを落とす）。 */
    static String between(SkillTreeDef tree, String a, String b) {
        for (String neighbor : tree.neighbors(a)) {
            if (tree.neighbors(b).contains(neighbor)) {
                return neighbor;
            }
        }
        throw new GameTestAssertException("2つの節の間に節がありません：" + a + "・" + b);
    }

    /** 核のとなりの、ステータスの節（なければテストを落とす）。 */
    static String statNextToCore(SkillTreeDef tree) {
        for (String neighbor : tree.neighbors(tree.core().id())) {
            SkillTreeDef.Node node = tree.node(neighbor);
            if (node != null && node.isStatNode()) {
                return neighbor;
            }
        }
        throw new GameTestAssertException("核のとなりにステータスの節がありません：" + tree.job());
    }

    /** 取れている節から、その節までの道のうち、まだ取っていない節（近い順。最後がその節）。 */
    static List<String> path(SkillTreeDef tree, PlayerData data, String target) {
        Deque<String> queue = new ArrayDeque<>();
        Map<String, String> previous = new HashMap<>();
        for (SkillTreeDef.Node node : tree.nodes()) {
            if (SkillTreeService.taken(data, tree, node.id())) {
                previous.put(node.id(), null);
                queue.add(node.id());
            }
        }
        while (!queue.isEmpty()) {
            String at = queue.poll();
            if (at.equals(target)) {
                break;
            }
            for (String next : tree.neighbors(at)) {
                if (!previous.containsKey(next)) {
                    previous.put(next, at);
                    queue.add(next);
                }
            }
        }
        if (!previous.containsKey(target)) {
            throw new GameTestAssertException("取れている節から、" + target + " への道がありません");
        }
        List<String> path = new ArrayList<>();
        for (String at = target; at != null && !SkillTreeService.taken(data, tree, at); at = previous.get(at)) {
            path.add(0, at);
        }
        return path;
    }

    /** 道の節を順に取る（スキルの節は1段目を覚える。最後がその節）。取れなければテストを落とす。 */
    static void takePath(GameTestHelper helper, ServerPlayer player, PlayerData data, SkillTreeDef tree, String target) {
        for (String id : path(tree, data, target)) {
            take(helper, player, data, tree, id);
        }
    }

    /** 節を1つ取る（スキルの節は次の段階を覚える）。取れなければテストを落とす。 */
    static void take(GameTestHelper helper, ServerPlayer player, PlayerData data, SkillTreeDef tree, String id) {
        SkillTreeDef.Node node = tree.node(id);
        if (node == null) {
            throw new GameTestAssertException("スキルツリーに節がありません：" + id);
        }
        String reason;
        if (node.kind() == SkillTreeDef.Kind.SKILL && node.skill() != null) {
            SkillDef skill = SkillRegistry.get().skill(node.skill());
            reason = skill == null ? "スキルがない" : SkillTreeService.canLearn(data, skill).name();
        } else {
            reason = SkillTreeService.canTakeNode(data, tree, id).name();
        }
        helper.assertTrue(SkillTreeService.takeNode(player, data, id), id + " を取れません：" + reason);
    }
}
