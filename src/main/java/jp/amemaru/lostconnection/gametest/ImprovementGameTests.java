package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.LockOnFacing;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.GrowthRules;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.TowerService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 11月24日の改善点の自動テスト。
 *
 * <ul>
 *   <li>ロックオン：ロックした敵のほうへ向く（扇・線・飛び道具がその敵に向かう）。味方・遠すぎる相手には向かない。</li>
 *   <li>次の階への円：どの階のボスの部屋にもあり、部屋の中で、出口の円と重ならない。次の階が開いていなければ進めない。</li>
 *   <li>転職：振ったステータスのポイントが全部戻り、一から振り直せる。</li>
 * </ul>
 * <p>照準の印・画面の表示・円の光は、CIの写真（phase8_lock_*・phase8_next_circle_*）で見る。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ImprovementGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;

    private ImprovementGameTests() {
    }

    // ---------------------------------------------------------------- ロックオン

    /** ロックした敵のほうへ向く：南を向いていても、東の敵をロックして使えば東を向く。 */
    @GameTest(template = EMPTY)
    public static void lockOnTurnsTowardTheEnemy(GameTestHelper helper) {
        FakePlayer player = fake(helper, "lock_turn");
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        if (zombie == null) {
            throw new GameTestAssertException("ゾンビを作れません");
        }
        zombie.moveTo(at.x + 6.0, at.y, at.z, 0.0F, 0.0F);
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);
        try {
            check(helper, LockOnFacing.face(player, zombie.getId()), "ロックした敵のほうへ向けません");
            Vec3 look = player.getLookAngle();
            check(helper, look.x > 0.95, "東の敵のほうを向いていません：" + look);
            check(helper, LockOnFacing.lockedId(player) == zombie.getId(), "向いた相手を覚えていません");
            // 詠唱のあいだに向きを変えても、もう一度向き直せる
            player.setYRot(180.0F);
            LockOnFacing.refresh(player);
            check(helper, player.getLookAngle().x > 0.95, "詠唱の終わりに、ロックした敵へ向き直りません：" + player.getLookAngle());
            // 遠すぎる相手には向かない（覚えていた相手も忘れる）
            zombie.moveTo(at.x + LockOnFacing.MAX_DISTANCE + 8.0, at.y, at.z, 0.0F, 0.0F);
            player.setYRot(0.0F);
            check(helper, !LockOnFacing.face(player, zombie.getId()), "遠すぎる敵に向きました");
            check(helper, Math.abs(player.getYRot()) < EPSILON, "向けないのに向きが変わりました");
            check(helper, LockOnFacing.lockedId(player) < 0, "向けない相手を覚えたままです");
        } finally {
            zombie.discard();
        }
        helper.succeed();
    }

    /** 味方（ほかのプレイヤー）には向かない。ロックせずに使えば、覚えていた相手を忘れる。 */
    @GameTest(template = EMPTY)
    public static void lockOnIgnoresAllies(GameTestHelper helper) {
        FakePlayer player = fake(helper, "lock_ally_a");
        FakePlayer ally = fake(helper, "lock_ally_b");
        ally.moveTo(player.getX() + 4.0, player.getY(), player.getZ(), 0.0F, 0.0F);
        player.setYRot(0.0F);
        check(helper, !LockOnFacing.face(player, ally.getId()), "味方のほうへ向きました");
        check(helper, Math.abs(player.getYRot()) < EPSILON, "味方なのに向きが変わりました");
        check(helper, !LockOnFacing.face(player, -1), "相手がいないのに向いたことになりました");
        LockOnFacing.forget(player);
        check(helper, LockOnFacing.lockedId(player) < 0, "忘れたはずの相手を覚えています");
        float[] angles = LockOnFacing.angles(Vec3.ZERO, new Vec3(0.0, 0.0, 5.0));
        check(helper, Math.abs(angles[0]) < 1.0E-3 && Math.abs(angles[1]) < 1.0E-3, "南（+z）の向きが 0 度になりません");
        angles = LockOnFacing.angles(Vec3.ZERO, new Vec3(-5.0, 0.0, 0.0));
        check(helper, Math.abs(angles[0] - 90.0F) < 1.0E-3, "西（−x）の向きが 90 度になりません：" + angles[0]);
        helper.succeed();
    }

    // ---------------------------------------------------------------- 次の階への円

    /** 作ってあるどの階のボスの部屋にも、次の階への円がある。部屋の中で、出口の円と重ならない。 */
    @GameTest(template = EMPTY)
    public static void bossRoomsHaveNextCircle(GameTestHelper helper) {
        check(helper, FloorDefs.all().size() >= 3, "階の定義が3つより少ないです");
        for (FloorDef def : FloorDefs.all()) {
            int n = def.number();
            check(helper, def.bossRoom() != null, n + "階にボスの部屋がありません");
            FloorDef.BossRoomDef boss = def.bossRoom();
            check(helper, boss.next() != null, n + "階のボスの部屋に、次の階への円がありません");
            Vec3 o = def.originVec();
            Vec3 next = o.add(boss.next().center());
            Vec3 exit = o.add(boss.exit().center());
            check(helper, boss.inside(next, o), n + "階の次の階への円が、ボスの部屋の外です");
            double gap = Math.sqrt(Math.pow(next.x - exit.x, 2) + Math.pow(next.z - exit.z, 2)) - boss.next().radius() - boss.exit().radius();
            check(helper, gap >= 1.0, n + "階の次の階への円が、出口の円に近すぎます（すきま " + gap + "）");
        }
        helper.succeed();
    }

    /** 次の階がひらいていなければ進めない（理由を出す）。ひらいていれば、理由はない（建ててあるときだけ進める）。 */
    @GameTest(template = EMPTY)
    public static void nextFloorNeedsTheFloorOpen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ProgressionData progress = ProgressionData.get(level.getServer());
        boolean was = progress.isUnlocked(2);
        try {
            progress.lock(2);
            check(helper, TowerService.nextFloorBlocked(level.getServer(), 1) != null, "2階がひらいていないのに、1階から進めます");
            progress.unlock(2);
            var blocked = TowerService.nextFloorBlocked(level.getServer(), 1);
            // テストの世界では塔の階を建てていないので、「まだできていない」になる（ひらいていない、ではない）
            check(helper, blocked == null || blocked.getString().contains("できていない"),
                    "2階がひらいているのに、ひらいていないことになっています：" + (blocked == null ? "" : blocked.getString()));
            check(helper, TowerService.nextFloorBlocked(level.getServer(), FloorDefs.FLOOR_COUNT) != null,
                    "いちばん上の階から、さらに上へ進めることになっています");
        } finally {
            if (was) {
                progress.unlock(2);
            } else {
                progress.lock(2);
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 転職と振り直し

    /** 転職すると、振ったステータスのポイントが全部戻る（一から振り直せる）。 */
    @GameTest(template = EMPTY)
    public static void jobChangeRefundsStatPoints(GameTestHelper helper) {
        FakePlayer player = fake(helper, "job_refund");
        PlayerData data = data(player);
        ProgressionData progression = ProgressionData.get(helper.getLevel().getServer());
        int originalDay = progression.day();
        try {
            progression.setDay(1);
            LevelService.setLevel(player, data, 10);
            check(helper, JobService.changeJob(player, data, job("knight")), "ナイトを選べません");
            LevelService.resetAllocation(player, data);
            int before = LevelService.availableStatPoints(data, GrowthRules.current());
            int used = LevelService.allocate(player, data, StatType.ATTACK, 4) + LevelService.allocate(player, data, StatType.HP, 3);
            check(helper, used == 7 && data.totalAllocated() == 7, "7ポイント振れません：" + used);
            check(helper, JobService.changeJob(player, data, job("wizard")), "ウィザードに変えられません");
            check(helper, data.totalAllocated() == 0, "転職したのに、振ったポイントが残っています：" + data.totalAllocated());
            check(helper, data.getAllocated(StatType.ATTACK) == 0 && data.getAllocated(StatType.HP) == 0, "攻撃・HPの振り分けが残っています");
            int after = LevelService.availableStatPoints(data, GrowthRules.current());
            check(helper, after == before, "戻ったポイントの数が合いません：" + after + "（" + before + "）");
            check(helper, LevelService.allocate(player, data, StatType.MAGIC, 5) == 5, "振り直せません");
        } finally {
            LevelService.resetAllocation(player, data);
            progression.setDay(originalDay);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 階ごとのアクセサリー

    /**
     * 2〜5階に、その階の素材で作る5つ（闘環・智環・首飾り・腕輪・お守り）と、名のある魔物が落とすレアのお守り1つがある。
     * 奥の階の品ほど、メインステータスの上限が高い（同じ章の中で1階ごとに +5%）。
     */
    @GameTest(template = EMPTY)
    public static void everyFloorHasAccessories(GameTestHelper helper) {
        java.util.Map<Integer, java.util.List<jp.amemaru.lostconnection.accessory.AccessoryDef>> byFloor = new java.util.HashMap<>();
        for (jp.amemaru.lostconnection.accessory.AccessoryDef def : jp.amemaru.lostconnection.accessory.AccessoryDefs.all().values()) {
            byFloor.computeIfAbsent(def.floor(), k -> new java.util.ArrayList<>()).add(def);
        }
        double lastFactor = 0.0;
        for (int floor = 1; floor <= 5; floor++) {
            java.util.List<jp.amemaru.lostconnection.accessory.AccessoryDef> list = byFloor.getOrDefault(floor, java.util.List.of());
            long crafted = list.stream().filter(d -> d.rarity() == jp.amemaru.lostconnection.equipment.Rarity.UNCOMMON).count();
            long rare = list.stream().filter(d -> d.rarity() == jp.amemaru.lostconnection.equipment.Rarity.RARE && !d.effects().isEmpty()).count();
            check(helper, crafted == 5, floor + "階のアクセサリー（アンコモン）が5つではありません：" + crafted);
            check(helper, rare == 1, floor + "階のレアのお守り（特殊効果つき）が1つではありません：" + rare);
            java.util.Set<jp.amemaru.lostconnection.accessory.AccessorySlot> slots = new java.util.HashSet<>();
            for (jp.amemaru.lostconnection.accessory.AccessoryDef def : list) {
                slots.add(def.slot());
                check(helper, def.generation() == 1, def.id() + " が第1章（世代1）の品になっていません");
            }
            check(helper, slots.size() == 4, floor + "階に、指輪・首飾り・腕輪・お守りがそろっていません：" + slots);
            double factor = list.get(0).mainFactor();
            check(helper, factor > lastFactor, floor + "階の品のメインの上限が、前の階より高くありません：" + factor);
            lastFactor = factor;
        }
        check(helper, Math.abs(lastFactor - 1.2) < 1.0E-9, "5階の品のメインの上限の倍率が 1.2 ではありません：" + lastFactor);
        // 名のある魔物が、その階のレアのお守りを落とす
        String[][] named = {{"hakage_kitsune_chief", "shade_tail_charm"}, {"juhihei_captain", "shade_tail_charm"},
                {"soujikihei_overseer", "water_mirror_charm"}, {"kiba_namazu_lord", "water_mirror_charm"},
                {"suimon_nezumi_boss", "water_mirror_charm"}};
        for (String[] pair : named) {
            jp.amemaru.lostconnection.enemy.EnemyDef def = jp.amemaru.lostconnection.enemy.EnemyDefs.get(AmemaruMod.id(pair[0]));
            check(helper, def != null && def.rare().stream().anyMatch(r -> r.item().equals(AmemaruMod.id(pair[1]))),
                    pair[0] + " が " + pair[1] + " を落としません");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "amx_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-improvements:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static PlayerData data(FakePlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
    }

    private static JobDef job(String name) {
        JobDef job = SkillRegistry.get().job(AmemaruMod.id(name));
        if (job == null) {
            throw new GameTestAssertException("職業 " + name + " がありません");
        }
        return job;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
