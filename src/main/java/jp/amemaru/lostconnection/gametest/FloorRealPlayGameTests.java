package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.tower.FloorBuild;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.FloorSealBlock;
import jp.amemaru.lostconnection.tower.FloorSealBlockEntity;
import jp.amemaru.lostconnection.tower.FloorThingBlockEntity;
import jp.amemaru.lostconnection.tower.TowerChestBlock;
import jp.amemaru.lostconnection.tower.TowerPillarBlock;
import jp.amemaru.lostconnection.tower.TowerPillarBlockEntity;
import jp.amemaru.lostconnection.tower.TowerService;
import jp.amemaru.lostconnection.tower.TowerSwitchBlock;
import jp.amemaru.lostconnection.tower.altar.AltarBlocks;
import jp.amemaru.lostconnection.tower.forest.ForestBlocks;
import jp.amemaru.lostconnection.tower.forest.LostWoodsService;
import jp.amemaru.lostconnection.tower.forest.WaypointService;
import jp.amemaru.lostconnection.tower.temple.TempleBlocks;
import jp.amemaru.lostconnection.tower.temple.TempleService;
import jp.amemaru.lostconnection.tower.waterway.FloodgateService;
import jp.amemaru.lostconnection.tower.waterway.WaterwayBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 11月24日：塔の階を本当に建てて、仕掛けを遊ぶ人と同じ道で触って確かめる（ご指示「4層の床を踏むギミックが反応しないです　別の階層も
 * しっかりと動作するのか確認してください」）。
 *
 * <p>前の自動テストとCIの写真は、仕掛けの処理（TowerService など）を直接呼んでいたので、ブロックからの呼ばれ方の間違い（4階の音の床は
 * stepOn で受けていて、薄い床では一度も呼ばれなかった）を見つけられなかった。ここでは：</p>
 * <ul>
 *   <li>階を、管理者のコマンドと同じ道（{@link FloorBuild#build}）で塔のディメンションに建てる</li>
 *   <li>階の定義のすべての仕掛けの場所に、正しいブロックと、正しい「どの階の何番目」があり、触れる面が空いているかを見る</li>
 *   <li>右クリックは、サーバーが右クリックの知らせを受けたときと同じ道（{@code ServerPlayerGameMode#useItemOn}：右クリックの
 *       イベント → ブロックの use）で、床を踏むのは体を動かして（{@code Entity#move} → ブロックの entityInside）確かめる</li>
 *   <li>階の課題をすべて触ってこなすと、封印のブロックが通れるようになる（当たり判定が空になる）</li>
 * </ul>
 * <p>次の階への円（人の位置を毎 tick 見る）は、仮の人（世界のプレイヤーの一覧にいない）では確かめられないので、CIの写真で見る。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FloorRealPlayGameTests {
    private static final String EMPTY = "empty3x3x3";

    private FloorRealPlayGameTests() {
    }

    // 階を建てる（大きい階は数秒かかる）ので、1階ずつ別の組で順に回す
    @GameTest(template = EMPTY, batch = "floor_real_1", timeoutTicks = 6000)
    public static void floor1RealPlay(GameTestHelper helper) {
        run(helper, 1);
    }

    @GameTest(template = EMPTY, batch = "floor_real_2", timeoutTicks = 6000)
    public static void floor2RealPlay(GameTestHelper helper) {
        run(helper, 2);
    }

    @GameTest(template = EMPTY, batch = "floor_real_3", timeoutTicks = 6000)
    public static void floor3RealPlay(GameTestHelper helper) {
        run(helper, 3);
    }

    @GameTest(template = EMPTY, batch = "floor_real_4", timeoutTicks = 6000)
    public static void floor4RealPlay(GameTestHelper helper) {
        run(helper, 4);
    }

    @GameTest(template = EMPTY, batch = "floor_real_5", timeoutTicks = 6000)
    public static void floor5RealPlay(GameTestHelper helper) {
        run(helper, 5);
    }

    /** 1つの階を建てて、すべての仕掛けを触る。 */
    private static void run(GameTestHelper helper, int floor) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel tower = FloorDefs.towerLevel(server);
        check(helper, tower != null, "塔のディメンションがありません");
        FloorDef def = FloorDefs.byNumber(floor);
        check(helper, def != null, floor + "階の定義がありません");
        CommandSourceStack source = server.createCommandSourceStack().withLevel(tower).withSuppressedOutput();
        FakePlayer player = fake(tower, "real_f" + floor);
        PlayerData data = PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
        int[] builds = new int[1];
        long start = helper.getLevel().getGameTime();
        GameTestSequence seq = helper.startSequence()
                .thenExecute(() -> {
                    resetFloor(server, def, player, data);
                    // 自動テストのサーバーは tick が速く進むので、建てる順番が区画の読み込みを待ちきれないことがある（5階は広い）。
                    // 範囲の区画をその場で読み込んでから建て、待ちきれずに順番が落ちたら出し直す
                    BlockPos origin = def.origin();
                    for (int cx = origin.getX() >> 4; cx <= (origin.getX() + def.sizeX() - 1) >> 4; cx++) {
                        for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + def.sizeZ() - 1) >> 4; cz++) {
                            tower.getChunk(cx, cz);
                        }
                    }
                    builds[0] = FloorBuild.builds;
                    FloorBuild.build(source, floor);
                })
                .thenWaitUntil(() -> {
                    if (FloorBuild.builds == builds[0] && !FloorBuild.pending(floor)) {
                        FloorBuild.build(source, floor);
                    }
                    check(helper, FloorBuild.builds > builds[0], floor + "階が建ちません（" + (helper.getLevel().getGameTime() - start) + " tick）");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    // 人が来たときと同じく、保存の状態に見えるものを合わせる（3階：水門と水。4階：崩れた壁画のパネル・扉）
                    if (floor == 3) {
                        FloodgateService.sync(server, def);
                    } else if (floor == 4) {
                        TempleService.reconcile(tower, def);
                    }
                    List<String> problems = structure(tower, def);
                    check(helper, problems.isEmpty(), floor + "階の仕掛けのブロックがおかしい（" + problems.size() + "）："
                            + String.join(" / ", problems.subList(0, Math.min(8, problems.size()))));
                    check(helper, !sealOpen(tower, def, player), floor + "階：まだ何もしていないのに、ボスの部屋の封印を通れます");
                });
        chests(helper, seq, tower, def, player, data);
        waypoints(helper, seq, tower, def, player);
        switch (floor) {
            case 1, 5 -> pillarsAndSwitches(helper, seq, tower, def, player, data);
            case 2 -> lostWoods(helper, seq, tower, def, player);
            case 3 -> floodgates(helper, seq, tower, def, player);
            case 4 -> temple(helper, seq, tower, def, player, data);
            default -> {
            }
        }
        seq.thenExecute(() -> {
            // 名のある魔物は倒したことにする（倒す道はほかのテストで見る）
            for (FloorDef.Guardian guardian : TowerService.namedGuardians(def)) {
                data.getStory().add(TowerService.guardianFlag(floor, guardian.id()));
            }
            check(helper, TowerService.pillarsComplete(player, floor), floor + "階：仕掛けをすべて触ってこなしたのに、封印の条件がそろいません");
            check(helper, sealOpen(tower, def, player), floor + "階：封印の条件がそろったのに、封印のブロックを通れません");
            resetFloor(server, def, player, data);
        }).thenSucceed();
    }

    // ---------------------------------------------------------------- 形：仕掛けのブロック

    /** 階の定義のすべての仕掛けの場所に、正しいブロックと「どの階の何番目」があるか。おかしいところの一覧。 */
    private static List<String> structure(ServerLevel tower, FloorDef def) {
        List<String> out = new ArrayList<>();
        int n = def.number();
        for (FloorDef.PillarDef pillar : def.pillars()) {
            BlockPos pos = def.world(pillar.pos());
            BlockState state = tower.getBlockState(pos);
            if (!(state.getBlock() instanceof TowerPillarBlock)) {
                out.add("石柱" + pillar.index() + "が " + name(state) + " " + local(pillar.pos()));
            } else if (!(tower.getBlockEntity(pos) instanceof TowerPillarBlockEntity be) || be.floor() != n || be.index() != pillar.index()) {
                out.add("石柱" + pillar.index() + "の階・番号が違う " + local(pillar.pos()));
            }
            exposed(out, tower, pos, "石柱" + pillar.index());
        }
        for (FloorDef.SwitchGroup group : def.extras().switches()) {
            for (int i = 0; i < group.stones().size(); i++) {
                thing(out, tower, def, group.stones().get(i), TowerSwitchBlock.class, i, group.id(), "仕掛け石 " + group.id() + "#" + i);
            }
        }
        for (FloorDef.ChestDef chest : def.extras().chests()) {
            thing(out, tower, def, chest.pos(), TowerChestBlock.class, chest.index(), null, "宝箱#" + chest.index());
        }
        FloorFeatures fx = def.features();
        for (FloorFeatures.LostWoods woods : fx.lostWoods()) {
            thing(out, tower, def, woods.bell(), ForestBlocks.Bell.class, -1, woods.id(), "鐘 " + woods.id());
            for (int i = 0; i < woods.exits().size(); i++) {
                thing(out, tower, def, woods.exits().get(i).post(), ForestBlocks.KnotPost.class, i, woods.id(), "灯台 " + woods.id() + "#" + i);
            }
        }
        for (int i = 0; i < fx.waypoints().size(); i++) {
            thing(out, tower, def, fx.waypoints().get(i).pos(), ForestBlocks.Waypoint.class, i, null, "中継の灯#" + i);
        }
        if (fx.travelStone() != null) {
            thing(out, tower, def, fx.travelStone(), ForestBlocks.TravelStone.class, -1, null, "道しるべの石");
        }
        for (FloorFeatures.Floodgate gate : fx.floodgates()) {
            for (int i = 0; i < gate.valves().size(); i++) {
                thing(out, tower, def, gate.valves().get(i), WaterwayBlocks.Valve.class, i, gate.id(), "ハンドル " + gate.id() + "#" + i);
            }
        }
        for (FloorFeatures.Mural mural : fx.murals()) {
            for (int i = 0; i < mural.panels().size(); i++) {
                thing(out, tower, def, mural.panels().get(i).pos(), TempleBlocks.MuralPanel.class, i, mural.id(), "壁画 " + mural.id() + "#" + i);
            }
        }
        for (FloorFeatures.Song song : fx.songs()) {
            thing(out, tower, def, song.chime(), TempleBlocks.ChimeStone.class, -1, song.id(), "鈴の石 " + song.id());
            for (int i = 0; i < song.plates().size(); i++) {
                plate(out, tower, def, song.plates().get(i), i, "音の床 " + song.id() + "#" + i);
            }
        }
        for (FloorFeatures.Relic relic : fx.relics()) {
            thing(out, tower, def, relic.pos(), TempleBlocks.KnightRelic.class, -1, relic.id(), "遺品 " + relic.id());
        }
        List<BlockPos> bossPlates = fx.bossMarks("sound_plates");
        for (int i = 0; i < bossPlates.size(); i++) {
            plate(out, tower, def, bossPlates.get(i), i, "ボスの音の床#" + i);
        }
        for (BlockPos dais : fx.bossMarks("binding_daises")) {
            BlockPos pos = def.world(dais);
            BlockState state = tower.getBlockState(pos);
            if (!(state.getBlock() instanceof AltarBlocks.BindingDais)) {
                out.add("結び台が " + name(state) + " " + local(dais));
            } else if (!tower.getBlockState(pos.above()).getCollisionShape(tower, pos.above()).isEmpty()) {
                out.add("結び台の上がふさがっている " + local(dais));
            }
        }
        if (def.bossRoom() != null) {
            BlockPos seal = def.world(def.bossRoom().sealMin());
            BlockState state = tower.getBlockState(seal);
            if (!(state.getBlock() instanceof FloorSealBlock)) {
                out.add("封印が " + name(state));
            } else if (!(tower.getBlockEntity(seal) instanceof FloorSealBlockEntity be) || be.floor() != n) {
                out.add("封印の階が違う");
            }
        }
        return out;
    }

    /** 右クリックで触る仕掛け：ブロック・階・番号（-1 は見ない）・組（null は見ない）・触れる面。 */
    private static void thing(List<String> out, ServerLevel tower, FloorDef def, BlockPos local, Class<? extends Block> type, int index,
                              String group, String what) {
        BlockPos pos = def.world(local);
        BlockState state = tower.getBlockState(pos);
        if (!type.isInstance(state.getBlock())) {
            out.add(what + "が " + name(state) + " " + local(local));
            return;
        }
        BlockEntity be = tower.getBlockEntity(pos);
        if (!(be instanceof FloorThingBlockEntity thing)) {
            out.add(what + "に中身（階・番号）がない " + local(local));
            return;
        }
        if (thing.floor() != def.number() || index >= 0 && thing.index() != index || group != null && !group.equals(thing.group())) {
            out.add(what + "の中身が違う（" + thing.floor() + "階・" + thing.index() + "・" + thing.group() + "） " + local(local));
        }
        exposed(out, tower, pos, what);
    }

    /** 音の床：ブロックと音（番号と同じ）・下が固く、上が空いていて乗れる。 */
    private static void plate(List<String> out, ServerLevel tower, FloorDef def, BlockPos local, int note, String what) {
        BlockPos pos = def.world(local);
        BlockState state = tower.getBlockState(pos);
        if (!(state.getBlock() instanceof TempleBlocks.SoundPlate)) {
            out.add(what + "が " + name(state) + " " + local(local));
            return;
        }
        if (state.getValue(TempleBlocks.NOTE) != note) {
            out.add(what + "の音が " + state.getValue(TempleBlocks.NOTE));
        }
        if (!tower.getBlockState(pos.below()).isFaceSturdy(tower, pos.below(), Direction.UP)) {
            out.add(what + "の下が固くない " + local(local));
        }
        if (!tower.getBlockState(pos.above()).getCollisionShape(tower, pos.above()).isEmpty()) {
            out.add(what + "の上がふさがっている " + local(local));
        }
    }

    /** 右クリックできるように、まわりの6面のどれかが空いているか（壁に埋まっていないか）。 */
    private static void exposed(List<String> out, ServerLevel tower, BlockPos pos, String what) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (tower.getBlockState(n).getCollisionShape(tower, n).isEmpty()) {
                return;
            }
        }
        out.add(what + "がまわりをふさがれて触れない");
    }

    // ---------------------------------------------------------------- どの階にもあるもの

    /** 宝箱：守り手のいない宝箱は開く。守り手のいる宝箱は、倒すまで開かない（倒したことにすると開く）。 */
    private static void chests(GameTestHelper helper, GameTestSequence seq, ServerLevel tower, FloorDef def, FakePlayer player, PlayerData data) {
        seq.thenExecute(() -> {
            int n = def.number();
            FloorDef.ChestDef free = null;
            FloorDef.ChestDef guarded = null;
            for (FloorDef.ChestDef chest : def.extras().chests()) {
                if (chest.guardian().isEmpty() && free == null) {
                    free = chest;
                } else if (!chest.guardian().isEmpty() && guarded == null) {
                    guarded = chest;
                }
            }
            check(helper, free != null, n + "階に、守り手のいない宝箱がありません");
            int before = data.getStory().chestCount(n);
            use(helper, tower, player, def.world(free.pos()), "宝箱#" + free.index());
            check(helper, data.getStory().chestCount(n) == before + 1, n + "階：宝箱#" + free.index() + "を右クリックしても開きません");
            if (guarded != null) {
                ResourceLocation flag = TowerService.guardianFlag(n, guarded.guardian());
                data.getStory().remove(flag);
                use(helper, tower, player, def.world(guarded.pos()), "宝箱#" + guarded.index());
                check(helper, data.getStory().chestCount(n) == before + 1, n + "階：守り手を倒していないのに、宝箱#" + guarded.index() + "が開きました");
                data.getStory().add(flag);
                use(helper, tower, player, def.world(guarded.pos()), "宝箱#" + guarded.index());
                check(helper, data.getStory().chestCount(n) == before + 2, n + "階：守り手を倒したのに、宝箱#" + guarded.index() + "が開きません");
                data.getStory().remove(flag);
            }
        });
    }

    /** 中継の灯：触るとともる。道しるべの石は、サーバーで動く（右クリックが通る）。 */
    private static void waypoints(GameTestHelper helper, GameTestSequence seq, ServerLevel tower, FloorDef def, FakePlayer player) {
        List<FloorFeatures.Waypoint> points = def.features().waypoints();
        if (points.isEmpty()) {
            return;
        }
        seq.thenExecute(() -> {
            WaypointService.Data data = WaypointService.Data.get(tower.getServer());
            for (int i = 0; i < points.size(); i++) {
                use(helper, tower, player, def.world(points.get(i).pos()), "中継の灯#" + i);
                check(helper, data.has(player.getUUID(), def.number(), i), def.number() + "階：中継の灯#" + i + "を右クリックしても、ともりません");
            }
            if (def.features().travelStone() != null) {
                use(helper, tower, player, def.world(def.features().travelStone()), "道しるべの石");
            }
        });
    }

    // ---------------------------------------------------------------- 1階・5階：石柱と仕掛け石

    private static void pillarsAndSwitches(GameTestHelper helper, GameTestSequence seq, ServerLevel tower, FloorDef def, FakePlayer player,
                                           PlayerData data) {
        int n = def.number();
        seq.thenExecute(() -> {
            for (FloorDef.PillarDef pillar : def.pillars()) {
                // 起動する前の課題（守り手・光のかけら）は終えたことにする（課題の道はほかのテストで見る）
                data.getStory().wakePillar(n, pillar.index());
                use(helper, tower, player, def.world(pillar.pos()), "石柱" + pillar.index());
                check(helper, (data.getStory().pillars(n) & (1 << pillar.index())) != 0,
                        n + "階：石柱" + (pillar.index() + 1) + "を右クリックしても、起動しません");
            }
            for (FloorDef.SwitchGroup group : def.extras().switches()) {
                for (int i = 0; i < group.stones().size(); i++) {
                    use(helper, tower, player, def.world(group.stones().get(i)), "仕掛け石 " + group.id() + "#" + i);
                }
                long now = tower.getServer().overworld().getGameTime();
                check(helper, TowerService.runtime(n) != null && TowerService.runtime(n).gates().isOpen(group.id(), now),
                        n + "階：仕掛け石（" + group.id() + "）をすべて押しても、門がひらきません");
            }
        });
    }

    // ---------------------------------------------------------------- 2階：迷いの森

    private static void lostWoods(GameTestHelper helper, GameTestSequence seq, ServerLevel tower, FloorDef def, FakePlayer player) {
        seq.thenExecute(() -> {
            MinecraftServer server = tower.getServer();
            Item lantern = ModItems.MANIFEST.get("knot_lantern").get();
            for (FloorFeatures.LostWoods woods : def.features().lostWoods()) {
                use(helper, tower, player, def.world(woods.bell()), "鐘 " + woods.id());
                FloorFeatures.Exit wrong = null;
                FloorFeatures.Exit correct = woods.correctExit();
                for (FloorFeatures.Exit exit : woods.exits()) {
                    if (!exit.correct()) {
                        wrong = exit;
                    }
                }
                check(helper, correct != null, woods.id() + "：正しい出口がありません");
                player.getInventory().add(new ItemStack(lantern, 2));
                if (wrong != null) {
                    use(helper, tower, player, def.world(wrong.post()), "まちがった灯台 " + woods.id());
                    check(helper, !LostWoodsService.isTied(server, def.number(), woods.id()), woods.id() + "：まちがった出口で、区画がつながりました");
                }
                use(helper, tower, player, def.world(correct.post()), "正しい灯台 " + woods.id());
                check(helper, LostWoodsService.isTied(server, def.number(), woods.id()),
                        woods.id() + "：正しい出口の灯台を右クリックしても、区画がつながりません");
            }
            player.getInventory().clearContent();
        });
    }

    // ---------------------------------------------------------------- 3階：水門

    private static void floodgates(GameTestHelper helper, GameTestSequence seq, ServerLevel tower, FloorDef def, FakePlayer player) {
        seq.thenExecute(() -> {
            MinecraftServer server = tower.getServer();
            List<FloorFeatures.Floodgate> left = new ArrayList<>();
            for (FloorFeatures.Floodgate gate : def.features().floodgates()) {
                if (!FloorFeatures.DRAIN.equals(gate.id())) {
                    left.add(gate);
                }
            }
            // 先の水門（after）が開いてから開く順に回す
            for (int guard = 0; !left.isEmpty() && guard < 16; guard++) {
                for (java.util.Iterator<FloorFeatures.Floodgate> it = left.iterator(); it.hasNext(); ) {
                    FloorFeatures.Floodgate gate = it.next();
                    if (!gate.after().isEmpty() && !FloodgateService.isOpen(server, def.number(), gate.after())) {
                        continue;
                    }
                    check(helper, gate.valves().size() == 2, gate.id() + "：ハンドルが2つではありません");
                    use(helper, tower, player, def.world(gate.valves().get(0)), "ハンドル " + gate.id() + "#0");
                    check(helper, !FloodgateService.isOpen(server, def.number(), gate.id()), gate.id() + "：ハンドル1つで水門が開きました");
                    use(helper, tower, player, def.world(gate.valves().get(1)), "ハンドル " + gate.id() + "#1");
                    check(helper, FloodgateService.isOpen(server, def.number(), gate.id()),
                            gate.id() + "：2つのハンドルを続けて回しても、水門が開きません");
                    it.remove();
                }
            }
            check(helper, left.isEmpty(), "開けられない水門があります（先の水門の指定）：" + left.size());
        });
    }

    // ---------------------------------------------------------------- 4階：壁画・音の順番・遺品・ボスの音の床

    private static void temple(GameTestHelper helper, GameTestSequence seq, ServerLevel tower, FloorDef def, FakePlayer player, PlayerData data) {
        int n = def.number();
        MinecraftServer server = tower.getServer();
        // 壁画：パネルを右クリックで回して、正しい向きにそろえる
        seq.thenExecute(() -> {
            for (FloorFeatures.Mural mural : def.features().murals()) {
                if (mural.display()) {
                    continue;
                }
                for (int i = 0; i < mural.panels().size(); i++) {
                    FloorFeatures.Panel panel = mural.panels().get(i);
                    BlockPos pos = def.world(panel.pos());
                    for (int turn = 0; turn < 4 && !TempleService.solved(server, n, mural.id())
                            && tower.getBlockState(pos).getValue(TempleBlocks.TURN) != panel.answer(); turn++) {
                        int was = tower.getBlockState(pos).getValue(TempleBlocks.TURN);
                        use(helper, tower, player, pos, "壁画 " + mural.id() + "#" + i);
                        check(helper, TempleService.solved(server, n, mural.id()) || tower.getBlockState(pos).getValue(TempleBlocks.TURN) == (was + 1) % 4,
                                "壁画 " + mural.id() + "#" + i + "：右クリックしても、パネルが回りません");
                    }
                }
                check(helper, TempleService.solved(server, n, mural.id()), "壁画 " + mural.id() + "：パネルをすべて正しい向きにしても、戻りません");
                if (mural.door() != null) {
                    check(helper, tower.getBlockState(def.world(mural.door().min())).getCollisionShape(tower, def.world(mural.door().min())).isEmpty(),
                            "壁画 " + mural.id() + "：戻ったのに、扉がひらきません");
                }
            }
        });
        // 音の順番：鈴の石を鳴らし、鳴り終えたら、同じ順に床へ乗る（体を動かして乗る）。11月25日のご指示「音の通りに床を踏んでも
        // 失敗になる」で見つけた3つ（鳴っている間から1音目の床で待つ・違う床を通り過ぎる・乗ったらすぐ次へ）も、遊ぶ人と同じに試す
        for (FloorFeatures.Song song : def.features().songs()) {
            int[][] notes = new int[1][];
            seq.thenExecute(() -> {
                use(helper, tower, player, def.world(song.chime()), "鈴の石 " + song.id());
                notes[0] = TempleService.songNotes(n, song.id());
                check(helper, notes[0].length == song.length(), song.id() + "：鈴の石を右クリックしても、歌が始まりません");
                // 鳴っている間から、1音目の床に乗って待つ
                stepOnto(player, def.world(song.plates().get(notes[0][0])));
            }).thenIdle(song.length() * TempleService.NOTE_TICKS + 2).thenExecute(() -> {
                check(helper, TempleService.songAnswered(n, song.id()) == 1,
                        song.id() + "：鳴っている間から1音目の床で待っていたのに、鳴り終えても1音目と数えません（"
                                + TempleService.songAnswered(n, song.id()) + "）");
                // 次の床へ行く途中で、違う床の上を止まらずに通る（数えない・外れにしない）
                int wrong = -1;
                for (int i = 0; i < song.plates().size() && wrong < 0; i++) {
                    if (i != notes[0][1] && i != notes[0][0]) {
                        wrong = i;
                    }
                }
                stepOnto(player, def.world(song.plates().get(wrong)));
            }).thenIdle(2).thenExecute(() -> {
                BlockPos away = def.world(song.chime());
                player.setPos(away.getX() + 0.5, away.getY() + 1.0, away.getZ() + 0.5);
            }).thenIdle(TempleService.DWELL_TICKS + 2).thenExecute(() ->
                    check(helper, TempleService.songAnswered(n, song.id()) == 1,
                            song.id() + "：違う床の上を通り過ぎただけで、外れ（はじめから）になりました"));
            for (int i = 1; i < song.length(); i++) {
                int k = i;
                int[] before = new int[1];
                seq.thenExecute(() -> {
                    BlockPos plate = def.world(song.plates().get(notes[0][k]));
                    int counted = TempleService.countedSteps();
                    before[0] = TempleService.confirmedSteps();
                    stepOnto(player, plate);
                    check(helper, plate.equals(TempleService.lastStep(player)) && TempleService.countedSteps() == counted + 1,
                            song.id() + "：音の床#" + notes[0][k] + "に乗っても、乗ったことになりません（" + (k + 1) + "音目）");
                    // 乗ったまま少し動いても、もう一度は数えない
                    player.move(MoverType.PLAYER, new Vec3(0.1, 0.0, 0.1));
                    check(helper, TempleService.countedSteps() == counted + 1, song.id() + "：同じ床の上で動いただけで、もう一度乗ったことになりました");
                }).thenIdle(TempleService.DWELL_TICKS + 2).thenExecute(() ->
                        check(helper, TempleService.confirmedSteps() == before[0] + 1,
                                song.id() + "：音の床#" + notes[0][k] + "に乗って止まっても、踏んだと数えません（" + (k + 1) + "音目）"));
            }
            seq.thenExecute(() -> {
                check(helper, TempleService.Data.get(server).sung(n, song.id()), song.id() + "：鳴った順に床を踏んでも、歌がそろいません");
                if (song.door() != null) {
                    BlockPos door = def.world(song.door().min());
                    check(helper, tower.getBlockState(door).getCollisionShape(tower, door).isEmpty(), song.id() + "：歌がそろったのに、扉がひらきません");
                }
            });
        }
        // 遺品：右クリックで受け取る
        seq.thenExecute(() -> {
            for (FloorFeatures.Relic relic : def.features().relics()) {
                data.getStory().remove(relic.flag());
                use(helper, tower, player, def.world(relic.pos()), "遺品 " + relic.id());
                check(helper, data.getStory().has(relic.flag()), "遺品 " + relic.id() + "：右クリックしても受け取れません");
                for (ResourceLocation id : relic.items()) {
                    Item item = ForgeRegistries.ITEMS.getValue(id);
                    check(helper, item != null && player.getInventory().countItem(item) > 0, "遺品 " + relic.id() + "：" + id + " が持ち物に入りません");
                }
                data.getStory().remove(relic.flag());
            }
            player.getInventory().clearContent();
        });
        // ボスの部屋の音の床：乗ると踏んだことになる（ボスがいないときは、鳴るだけ）。乗ったままの間は数えず、降りてまた乗ると、また数える
        List<BlockPos> bossPlates = def.features().bossMarks("sound_plates");
        if (!bossPlates.isEmpty()) {
            int[] counted = new int[1];
            seq.thenExecute(() -> {
                for (BlockPos local : bossPlates) {
                    BlockPos plate = def.world(local);
                    int before = TempleService.countedSteps();
                    stepOnto(player, plate);
                    check(helper, plate.equals(TempleService.lastStep(player)) && TempleService.countedSteps() == before + 1,
                            "ボスの音の床 " + local(local) + "：乗っても、踏んだことになりません");
                }
                stepOnto(player, def.world(bossPlates.get(0)));
                counted[0] = TempleService.countedSteps();
            }).thenIdle(3).thenExecute(() -> {
                BlockPos first = def.world(bossPlates.get(0));
                player.move(MoverType.PLAYER, new Vec3(0.1, 0.0, -0.1));
                check(helper, TempleService.countedSteps() == counted[0] && first.equals(TempleService.lastStep(player)),
                        "音の床に乗ったまま時間がたつと、また踏んだことになりました");
                // 降りる（2つとなりの床の石へ）
                player.setPos(first.getX() + 2.5, first.getY() + 0.2, first.getZ() + 0.5);
                player.move(MoverType.PLAYER, new Vec3(0.0, -0.3, 0.0));
            }).thenIdle(3).thenExecute(() -> {
                BlockPos first = def.world(bossPlates.get(0));
                check(helper, TempleService.lastStep(player) == null, "音の床から降りたのに、乗ったままになっています");
                int before = TempleService.countedSteps();
                stepOnto(player, first);
                check(helper, TempleService.countedSteps() == before + 1, "音の床から降りてまた乗っても、踏んだことになりません");
                player.setPos(first.getX() + 2.5, first.getY() + 0.2, first.getZ() + 0.5);
            });
        }
    }

    // ---------------------------------------------------------------- 封印

    /** ボスの部屋の封印を、その人が通れるか（封印のブロックの当たり判定が、その人には空か）。 */
    private static boolean sealOpen(ServerLevel tower, FloorDef def, FakePlayer player) {
        if (def.bossRoom() == null) {
            return true;
        }
        BlockPos seal = def.world(def.bossRoom().sealMin());
        player.setPos(seal.getX() + 0.5, seal.getY(), seal.getZ() + 2.5);
        return tower.getBlockState(seal).getCollisionShape(tower, seal, CollisionContext.of(player)).isEmpty();
    }

    // ---------------------------------------------------------------- 道具

    /**
     * 右クリック：サーバーが右クリックの知らせを受けたときと同じ道（右クリックのイベント → ブロックの use）。ブロックの上に立ってから。
     * サーバーの側で受け取った（CONSUME か SUCCESS）でなければ失敗。
     */
    private static void use(GameTestHelper helper, ServerLevel level, FakePlayer player, BlockPos pos, String what) {
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        InteractionResult result = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        check(helper, result.consumesAction(), what + "：右クリックが仕掛けに届きません（" + result + "）" + local(pos));
    }

    /** 床の上へ、上から降りて乗る（遊ぶ人と同じく体を動かす：Entity#move → 重なったブロックの entityInside）。 */
    private static void stepOnto(FakePlayer player, BlockPos plate) {
        player.setPos(plate.getX() + 0.5, plate.getY() + 0.7, plate.getZ() + 0.5);
        player.setOnGround(false);
        player.move(MoverType.PLAYER, new Vec3(0.0, -0.6, 0.0));
    }

    /** 階ごとの、みんなの仕掛けの状態と、この人の印を、はじめに戻す。 */
    private static void resetFloor(MinecraftServer server, FloorDef def, FakePlayer player, PlayerData data) {
        switch (def.number()) {
            case 2 -> LostWoodsService.onDayChanged(server);
            case 3 -> FloodgateService.onDayChanged(server);
            case 4 -> TempleService.onDayChanged(server);
            default -> {
            }
        }
        WaypointService.Data.get(server).clear(player.getUUID());
        for (FloorDef.Guardian guardian : TowerService.namedGuardians(def)) {
            data.getStory().remove(TowerService.guardianFlag(def.number(), guardian.id()));
        }
        player.getInventory().clearContent();
    }

    private static FakePlayer fake(ServerLevel level, String name) {
        String full = "amt_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, profileName));
    }

    private static String name(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id == null ? "?" : id.toString();
    }

    private static String local(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}
