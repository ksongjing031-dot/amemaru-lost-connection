package jp.amemaru.lostconnection.gametest;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Locale;

/**
 * 街の直しの書き出しの自動テスト（GameTest。11月21日）。
 *
 * <p>街をまるごと建て（{@code /amemaru admin city build}）、何も直さないうちは「直した所」が1つもないこと
 * （自然に変わる所・置き方の違いを直しに数えない）を確かめる。そのあと、ギルドの上の空に金のブロックを置いて、
 * 書き出す前は建て直しが止まり、書き出すと直しのファイルができ、建て直しても金のブロックが残り、空気に戻して書き出すと
 * ファイルが消えることを、通しで確かめる。違いが見つかると、ログに違いの見本（建てた形と今の形）が出る。</p>
 *
 * <p>街は、テストの場所の130上に建てる（テストの場所のまわりは、もう作られた区画なので、すぐ建てられる。地面は道の13下から
 * 作るので、テストの場所には届かない）。建てる前に、範囲の区画をその場で読み込む（遠くの区画を作るのを待つと長くかかるため）。
 * 自動の確認のクライアントの世界では、すべての写真を撮り終えてからテストが回る。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityEditsGameTests {
    private static final String EMPTY = "empty3x3x3";

    private CityEditsGameTests() {
    }

    @GameTest(template = EMPTY, batch = "city_edits", timeoutTicks = 6000)
    public static void cityEditsRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        BlockPos test = helper.absolutePos(BlockPos.ZERO);
        // 道の高さ：テストの場所の130上（ワールドの高さに収まるように。上の片づけは64、下の地面は13）
        int y = Mth.clamp(test.getY() + 130, level.getMinBuildHeight() + 14, level.getMaxBuildHeight() - 66);
        BlockPos origin = new BlockPos(test.getX(), y, test.getZ());
        // ギルドの構造物の上の空（構造物の中の (1, 37, 1)。はじめは空気。CIの写真と同じ所）
        BlockPos guildSky = origin.offset(6 + 1, 37, -100 + 1);
        String at = String.format(Locale.ROOT, "%d %d %d", origin.getX(), origin.getY(), origin.getZ());
        int[] builds = new int[1];
        long start = level.getGameTime();
        helper.startSequence()
                // 範囲の区画をその場で読み込んでから建てる（前に別の所に建てた街があっても、その直しは見ない）
                .thenExecute(() -> {
                    for (int cx = (origin.getX() - 160) >> 4; cx <= (origin.getX() + 150) >> 4; cx++) {
                        for (int cz = (origin.getZ() - 200) >> 4; cz <= (origin.getZ() + 140) >> 4; cz++) {
                            level.getChunk(cx, cz);
                        }
                    }
                    builds[0] = CityCommands.builds;
                    step(helper, "build sent (builds " + builds[0] + ")");
                    run(server, source, "amemaru admin city build at " + at + " force");
                })
                .thenWaitUntil(() -> {
                    if ((level.getGameTime() - start) % 100 == 0) {
                        step(helper, "waiting for the build (builds " + CityCommands.builds + ")");
                    }
                    check(helper, CityCommands.builds > builds[0], "街が建ちません（" + (level.getGameTime() - start) + " tick）");
                })
                .thenExecute(() -> step(helper, "built"))
                // 落ちる砂利・水の流れ・形のつながりなどが落ち着くまで待つ
                .thenIdle(60)
                .thenExecute(() -> clean(helper, level, "建てた直後"))
                // 直す：ギルドの上の空に金のブロック
                .thenExecute(() -> level.setBlock(guildSky, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL))
                .thenExecute(() -> {
                    List<String> unsaved = CityCommands.unsavedFacilities(level);
                    check(helper, unsaved.equals(List.of("guild")), "ギルドに金のブロックを置いたのに、書き出していない直しが " + unsaved);
                    builds[0] = CityCommands.builds;
                    run(server, source, "amemaru admin city build at " + at);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    check(helper, CityCommands.builds == builds[0], "書き出していない直しがあるのに、建て直しました");
                    check(helper, level.getBlockState(guildSky).is(Blocks.GOLD_BLOCK), "建て直しを止めたのに、金のブロックが消えました");
                    step(helper, "rebuild refused; export");
                    run(server, source, "amemaru admin city export");
                })
                .thenWaitUntil(() -> check(helper, CityCommands.hasEditFile(level, "guild_hall"), "書き出しても、ギルドの直しのファイルができません"))
                .thenExecute(() -> {
                    clean(helper, level, "書き出した直後");
                    check(helper, !CityCommands.hasEditFile(level, "personal_storage"), "ギルドしか直していないのに、個人倉庫の直しのファイルができました");
                    builds[0] = CityCommands.builds;
                    run(server, source, "amemaru admin city build at " + at);
                })
                .thenWaitUntil(() -> check(helper, CityCommands.builds > builds[0], "書き出したのに、建て直せません（" + (level.getGameTime() - start) + " tick）"))
                .thenExecute(() -> step(helper, "rebuilt with the export"))
                .thenIdle(20)
                .thenExecute(() -> {
                    check(helper, level.getBlockState(guildSky).is(Blocks.GOLD_BLOCK), "書き出した直し（金のブロック）が、建て直すと消えました");
                    clean(helper, level, "直しを重ねて建て直した直後");
                    // 元に戻して書き出すと、Modの形と同じになった直しが外れ、ファイルが消える
                    level.setBlock(guildSky, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    run(server, source, "amemaru admin city export");
                })
                .thenWaitUntil(() -> check(helper, !CityCommands.hasEditFile(level, "guild_hall"), "元に戻して書き出しても、ギルドの直しのファイルが消えません"))
                .thenExecute(() -> clean(helper, level, "元に戻して書き出した直後"))
                .thenSucceed();
    }

    private static void run(MinecraftServer server, CommandSourceStack source, String command) {
        server.getCommands().performPrefixedCommand(source, command);
    }

    /** 進み具合をログに書く（ビルドのジョブのログに、テストの何 tick 目かと一緒に出る） */
    private static void step(GameTestHelper helper, String what) {
        AmemaruMod.LOGGER.info("[amemaru][city-test] game time {}: {}", helper.getLevel().getGameTime(), what);
    }

    private static void clean(GameTestHelper helper, ServerLevel level, String when) {
        List<String> unsaved = CityCommands.unsavedFacilities(level);
        check(helper, unsaved.isEmpty(), when + "なのに、直した所に見える施設があります " + unsaved + "（違いの見本はログの [amemaru][city]）");
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}
