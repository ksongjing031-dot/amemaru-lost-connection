package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModBlocks;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import jp.amemaru.lostconnection.enemy.boss.Mune;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.SoloTest;
import jp.amemaru.lostconnection.tower.lake.BellService;
import jp.amemaru.lostconnection.tower.lake.LakeBlocks;
import jp.amemaru.lostconnection.tower.lake.ThinIceService;
import jp.amemaru.lostconnection.tower.waterway.FloodgateService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * ひとり確認モード（11月26日のご指示「ボス戦までのギミックをソロでテストできるようにして動作確認できるように、おなじくボス戦での
 * ギミックの確認も」）の自動テスト。モードはサーバーじゅうの切りかえなので、ほかのテストと同時に動かないよう、ひとつの組
 * （{@code solo_mode}）に、ひとつのテストだけを置く。終わったら（失敗しても）切る。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoloModeGameTests {
    private static final String EMPTY = "empty3x3x3";

    private SoloModeGameTests() {
    }

    /**
     * モードの間だけ：水門・排水・鐘の組・鳴り石の「何秒のうちに」が延び、練習用の人形が仲間に数えられる。ひとりで水門の2つの
     * ハンドルを（3秒より長くあけて）回すと開く。人形のそばに立つと薄氷にひびが入る。帳の間に、5秒あけて違う鳴り石を鳴らすと破れる。
     * 切ると、ふだんの決まりに戻る。
     */
    @GameTest(template = EMPTY, batch = "solo_mode", timeoutTicks = 300)
    public static void soloModeLoosensTogetherGimmicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        FloorDef floor3 = FloorDefs.byNumber(3);
        FloorDef floor8 = FloorDefs.byNumber(8);
        guard(helper, floor3 != null && floor8 != null && !floor8.features().bells().isEmpty(), "3階か8階の定義がありません");
        FloorFeatures.BellGroup cove = floor8.features().bells().get(0);
        // ふだん
        SoloTest.set(false);
        guard(helper, FloodgateService.windowTicks("first") == FloodgateService.WINDOW_TICKS
                && FloodgateService.windowTicks(FloorFeatures.DRAIN) == FloodgateService.WINDOW_TICKS
                && BellService.limitSeconds(cove) == cove.seconds(), "モードを切っているのに、水門・排水・鐘の時間が延びています");
        // 薄氷：ひとりと人形では、ふだんは割れない
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), ModBlocks.THIN_ICE.get());
            }
        }
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        FakePlayer player = fake(helper, "solo");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        player.setPos(at.x, at.y, at.z);
        TrainingDummy dummy = ModEntities.TRAINING_DUMMY.get().create(level);
        guard(helper, dummy != null, "練習用の人形を作れません");
        dummy.moveTo(at.x + 1.0, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(dummy);
        guard(helper, SoloTest.standIns(level, at, 3.0, 1.5) == 0, "モードを切っているのに、人形が仲間に数えられました");
        ThinIceService.tickCrowd(level, List.of(player), level.getGameTime());
        guard(helper, crack(level, center) == 0, "モードを切っているのに、ひとりと人形で薄氷にひびが入りました");
        // 入れる
        SoloTest.set(true);
        guard(helper, FloodgateService.windowTicks("first") == SoloTest.FLOODGATE_SECONDS * 20
                && FloodgateService.windowTicks(FloorFeatures.DRAIN) == SoloTest.DRAIN_SECONDS * 20, "モードの間も、水門・排水の時間が延びません");
        guard(helper, BellService.limitSeconds(cove) == cove.seconds() * SoloTest.BELL_FACTOR, "モードの間も、鐘の組の時間が延びません");
        guard(helper, SoloTest.seconds(4, SoloTest.ECHO_SECONDS) == SoloTest.ECHO_SECONDS && SoloTest.seconds(300, 10) == 300,
                "モードの間の時間が、ふだんより短くなることがあります");
        guard(helper, SoloTest.standIns(level, at, 3.0, 1.5) == 1 && SoloTest.standIns(level, at.add(10.0, 0.0, 0.0), 3.0, 1.5) == 0,
                "人形が、そばにいるときだけ仲間に数えられません");
        ThinIceService.tickCrowd(level, List.of(player), level.getGameTime());
        guard(helper, crack(level, center) == 1, "モードの間、人形のそばに立っても薄氷にひびが入りません：" + crack(level, center));
        // 水門：ひとりで片方を回し、4秒あとにもう片方を回すと開く（ふだんは3秒）
        FloodgateService.onDayChanged(server);
        BlockPos valve = helper.absolutePos(new BlockPos(0, 1, 0));
        guard(helper, !FloodgateService.turn(player, 3, "first", 0, valve), "ハンドルを1つ回しただけで、水門が開きました");
        // 帳：違う鳴り石を、5秒あけて鳴らす（ふだんは3秒）
        Mune boss = ModEntities.MUNE.get().create(level);
        guard(helper, boss != null, "ムーネを作れません");
        Vec3 bossAt = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        boss.moveTo(bossAt.x, bossAt.y, bossAt.z, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        BlockPos bellA = helper.absolutePos(new BlockPos(0, 1, 2));
        BlockPos bellB = helper.absolutePos(new BlockPos(2, 1, 0));
        helper.runAfterDelay(2, () -> {
            boss.castVeil(level);
            guard(helper, boss.veiled() && !boss.onBell(player, bellA), "帳が広がらないか、鳴り石を1つ鳴らしただけで破れました");
        });
        helper.runAfterDelay(90, () -> {
            guard(helper, FloodgateService.turn(player, 3, "first", 1, valve) && FloodgateService.isOpen(server, 3, "first"),
                    "モードの間、ひとりで（4秒あけて）2つのハンドルを回しても、水門が開きません");
            guard(helper, boss.veiled() && boss.onBell(player, bellB) && !boss.veiled(),
                    "モードの間、5秒あけて違う鳴り石を鳴らしても、帳が破れません");
            // 切ると、ふだんに戻る
            SoloTest.set(false);
            guard(helper, FloodgateService.windowTicks("first") == FloodgateService.WINDOW_TICKS
                    && SoloTest.standIns(level, at, 3.0, 1.5) == 0, "モードを切っても、ふだんの決まりに戻りません");
            FloodgateService.onDayChanged(server);
            ThinIceService.refreeze(level, true);
            boss.discard();
            dummy.discard();
            helper.succeed();
        });
    }

    /**
     * 11月27日のご指示「推奨レベルよりもかなり高めの時はソロでも攻略できるように。ソロプレイヤーだと攻略できないギミックを減らす」：
     * モードを切っていても、ひとりで挑んでいる（その階にほかの人がいない・ボス戦の参加者が1人）なら、時間の決まりはひとり用になる。
     * 2人以上なら、ふだんのまま。
     */
    @GameTest(template = EMPTY, batch = "solo_alone")
    public static void aloneGetsSoloTimes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        SoloTest.set(false);
        FloorDef floor8 = FloorDefs.byNumber(8);
        ServerLevel tower = FloorDefs.towerLevel(server);
        guard(helper, floor8 != null && !floor8.features().bells().isEmpty() && tower != null, "8階か塔の定義がありません");
        FloorFeatures.BellGroup cove = floor8.features().bells().get(0);
        guard(helper, SoloTest.seconds(3, 120, true) == 120 && SoloTest.seconds(3, 120, false) == 3
                && SoloTest.seconds(300, 10, true) == 300, "ひとりで挑むときの秒の決まりが違います");
        // 自動テストのサーバーでは、3階・8階に誰もいない＝ひとり
        guard(helper, SoloTest.aloneOnFloor(server, 3) && SoloTest.playersOnFloor(server, 3) == 0, "誰もいない階が、ひとりと判定されません");
        guard(helper, FloodgateService.windowTicks(server, 3, "first") == SoloTest.FLOODGATE_SECONDS * 20
                && FloodgateService.windowTicks(server, 3, FloorFeatures.DRAIN) == SoloTest.DRAIN_SECONDS * 20,
                "ひとりで挑んでいるのに、水門・排水の時間が延びません");
        guard(helper, BellService.limitSeconds(tower, floor8, cove) == cove.seconds() * SoloTest.BELL_FACTOR,
                "ひとりで挑んでいるのに、鐘の組の時間が延びません");
        // ボス戦：参加者が2人なら、ふだんのまま
        FakePlayer a = fake(helper, "alone_a");
        FakePlayer b = fake(helper, "alone_b");
        guard(helper, SoloTest.aloneInFight(List.of(a)) && !SoloTest.aloneInFight(List.of(a, b)), "ボス戦の参加者の数で、ひとりが判定されません");
        // モードの間の決まり（古い呼び方）は、ひとりかどうかに関係なく、モードだけで決まる
        guard(helper, FloodgateService.windowTicks("first") == FloodgateService.WINDOW_TICKS, "モードを切っているのに、水門の時間が延びています");
        helper.succeed();
    }

    /** 組が終わったら（テストが失敗しても）、モードを切る。 */
    @AfterBatch(batch = "solo_mode")
    public static void soloModeOff(ServerLevel level) {
        SoloTest.set(false);
        FloodgateService.onDayChanged(level.getServer());
    }

    private static int crack(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.THIN_ICE.get()) ? level.getBlockState(pos).getValue(LakeBlocks.CRACK) : -1;
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "ams_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-solo:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /** 確かめる。違っていたら、モードを切ってから失敗にする（ほかの組のテストに残さない）。 */
    private static void guard(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            SoloTest.set(false);
            throw new GameTestAssertException(message);
        }
    }
}
