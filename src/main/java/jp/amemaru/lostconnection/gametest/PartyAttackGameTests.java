package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.enemy.boss.PartyAttacks;
import jp.amemaru.lostconnection.enemy.boss.Veldhorn;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 11月28日のご指示「ボスが一人をターゲットしてその人にしか攻撃をしないので、ボス参加している人全員をターゲットにする攻撃を
 * 追加して他のプレイヤーが一方的に攻撃できる状態を減らす」の自動テスト：1〜8階のどのボスにも一斉の印と大技があり、印は全員の
 * 足もとに出て、遅れて今いる所を追いかける。大技は形どおりの所を打つ。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartyAttackGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final List<String> BOSSES = List.of("veldhorn", "old_ward", "hydram", "cantare", "ancient_guardian", "ordo", "reflecta",
            "mune");
    /** 固有のギミックをしくじったときの一撃（大技はこれより弱い。11月27日の決まり）。 */
    private static final Map<String, List<String>> FAILURE = Map.of("veldhorn", List.of("earth_roar"), "hydram", List.of("flood"),
            "cantare", List.of("echo"), "ancient_guardian", List.of("judge", "sever"), "ordo", List.of("gaze"));

    private PartyAttackGameTests() {
    }

    /** どのボスにも一斉の印（volley）と大技（nova）があり、大技は固有のギミックをしくじったときの一撃より弱く、印より強い。 */
    @GameTest(template = EMPTY)
    public static void everyBossHasPartyAttacks(GameTestHelper helper) {
        for (String name : BOSSES) {
            EnemyDef def = EnemyDefs.get(AmemaruMod.id(name));
            check(helper, def != null, name + " の定義がありません");
            EnemyDef.Skill volley = def.skills().get("volley");
            EnemyDef.Skill nova = def.skills().get("nova");
            check(helper, volley != null && nova != null, name + " に一斉の印（volley）か大技（nova）がありません");
            check(helper, volley.power() > 0.0 && volley.range() >= 2.0 && volley.param("waves", 0.0) >= 1.0 && volley.windup() >= 1.2,
                    name + " の一斉の印の数値がおかしいです");
            int kinds = (int) Math.round(nova.param("kinds", 0.0));
            check(helper, kinds > 0 && (kinds & ~7) == 0, name + " の大技の形（kinds）がおかしいです：" + kinds);
            check(helper, nova.power() > volley.power(), name + " の大技が、一斉の印より弱いです");
            check(helper, nova.windup() >= 2.5, name + " の大技の予兆が短すぎます：" + nova.windup());
            for (String failure : FAILURE.getOrDefault(name, List.of())) {
                EnemyDef.Skill strike = def.skills().get(failure);
                check(helper, strike != null && nova.power() < strike.power(),
                        name + " の大技が、固有のギミックをしくじったときの一撃（" + failure + "）より強いです");
            }
        }
        helper.succeed();
    }

    /** 一斉の印は、狙った全員の足もとに出る。少し遅れて、そのとき立っている所に次の印が出る（追いかける）。 */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void volleyMarksEveryoneAndChases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Veldhorn boss = ModEntities.VELDHORN.get().create(level);
        check(helper, boss != null, "ヴェルドホーンを作れません");
        Vec3 base = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        boss.moveTo(base.x + 6.0, base.y, base.z, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        FakePlayer a = fake(helper, "volley_a", base);
        FakePlayer b = fake(helper, "volley_b", base.add(0.0, 0.0, 4.0));
        FakePlayer c = fake(helper, "volley_c", base.add(-4.0, 0.0, 2.0));
        List<ServerPlayer> targets = List.of(a, b, c);
        PartyAttacks party = boss.partyAttacks();
        helper.runAfterDelay(2, () -> {
            check(helper, party.startVolley(level, targets), "一斉の印を出せません");
            party.tickAttacks(level, targets);
            List<Vec3> spots = party.pendingSpots();
            check(helper, spots.size() == 3, "印が全員の足もとに出ません：" + spots.size());
            for (ServerPlayer player : targets) {
                check(helper, spots.stream().anyMatch(s -> Math.hypot(s.x - player.getX(), s.z - player.getZ()) < 0.5),
                        player.getGameProfile().getName() + " の足もとに印がありません");
            }
            // ひとりだけ動く。次の印は、動いた先に出る
            a.moveTo(a.getX() + 5.0, a.getY(), a.getZ(), 0.0F, 0.0F);
            Vec3 moved = a.position();
            boolean chased = false;
            for (int i = 0; i < 25 && !chased; i++) {
                party.tickAttacks(level, targets);
                chased = party.pendingSpots().stream().anyMatch(s -> Math.hypot(s.x - moved.x, s.z - moved.z) < 0.5);
            }
            check(helper, chased, "次の印が、動いた人の今いる所に出ません");
            boss.discard();
            helper.succeed();
        });
    }

    /** 大技の当たる所：ボスのまわりの形は円の中だけ、金の円の形は金の円の外ぜんぶ。高さが離れていれば当たらない。 */
    @GameTest(template = EMPTY)
    public static void novaShapesHitTheRightPlaces(GameTestHelper helper) {
        Vec3 center = new Vec3(0.0, 64.0, 0.0);
        check(helper, PartyAttacks.hitBy(PartyAttacks.NOVA_OUT, center, 13.0, List.of(), 0.0, new Vec3(8.0, 64.0, 0.0)),
                "ボスのまわりの大技が、円の中に当たりません");
        check(helper, !PartyAttacks.hitBy(PartyAttacks.NOVA_OUT, center, 13.0, List.of(), 0.0, new Vec3(15.0, 64.0, 0.0)),
                "ボスのまわりの大技が、円の外に当たりました");
        List<Vec3> safes = List.of(new Vec3(10.0, 64.0, 0.0), new Vec3(-10.0, 64.0, 0.0));
        check(helper, !PartyAttacks.hitBy(PartyAttacks.NOVA_SAFE, center, 40.0, safes, 4.5, new Vec3(12.0, 64.0, 2.0)),
                "金の円の中にいるのに、大技に当たりました");
        check(helper, PartyAttacks.hitBy(PartyAttacks.NOVA_SAFE, center, 40.0, safes, 4.5, new Vec3(0.0, 64.0, 20.0)),
                "金の円の外にいるのに、大技に当たりません");
        check(helper, PartyAttacks.hitBy(PartyAttacks.NOVA_IN, center, 40.0, List.of(new Vec3(3.0, 64.0, 3.0)), 7.0,
                new Vec3(25.0, 64.0, 0.0)), "ボスのそばの金の円から遠いのに、大技に当たりません");
        check(helper, !PartyAttacks.hitBy(PartyAttacks.NOVA_SAFE, center, 40.0, safes, 4.5, new Vec3(0.0, 80.0, 20.0)),
                "高さが離れているのに、大技に当たりました");
        helper.succeed();
    }

    /** 戦いの外（部屋のないボス）では、全員を狙う技を自分では出さない。 */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void noPartyAttackOutsideFights(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Veldhorn boss = ModEntities.VELDHORN.get().create(level);
        check(helper, boss != null, "ヴェルドホーンを作れません");
        Vec3 base = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        boss.moveTo(base.x + 3.0, base.y, base.z, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        fake(helper, "party_idle", base);
        helper.runAfterDelay(80, () -> {
            check(helper, !boss.partyAttacks().active(), "戦いの外で、全員を狙う技を出しました");
            boss.discard();
            helper.succeed();
        });
    }

    private static FakePlayer fake(GameTestHelper helper, String name, Vec3 at) {
        ServerLevel level = helper.getLevel();
        String full = "am28_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-1128:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}
