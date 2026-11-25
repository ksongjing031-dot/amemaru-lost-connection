package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.combat.LostService;
import jp.amemaru.lostconnection.core.registry.ModBlocks;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.enemy.boss.Mune;
import jp.amemaru.lostconnection.enemy.lake.SilentFish;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import jp.amemaru.lostconnection.guild.QuestDefs;
import jp.amemaru.lostconnection.guild.RecordDefs;
import jp.amemaru.lostconnection.story.DialogueDefs;
import jp.amemaru.lostconnection.story.StoryEventManager;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.TowerService;
import jp.amemaru.lostconnection.tower.lake.BellService;
import jp.amemaru.lostconnection.tower.lake.LakeBlocks;
import jp.amemaru.lostconnection.tower.lake.ThinIceService;
import jp.amemaru.lostconnection.workshop.ModRecipes;
import jp.amemaru.lostconnection.workshop.WorkshopRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
 * 11月25日（8階「沈黙の湖」）の自動テスト。
 *
 * <ul>
 *   <li>8階の形：入江の鐘（3つ。石橋が浮かぶ）・湖の鐘（5つ。音が戻り、祠のふたが開く）・湖心の鳴り石4つ・音の消えた湖・
 *       名のある魔物2体・封印は「鐘＋名のある魔物」・湖底の祠の石板と最後の歌い手の氷像</li>
 *   <li>鐘：今日の歌（その日の順番）のとおりに鳴らすと解ける。違う鐘で、はじめから。湖の鐘で音が戻る。日が変わると戻る</li>
 *   <li>薄氷：ひとりでは割れない。2人が近くに乗ると、ひびが入って割れ、水の穴になり、凍り直す。水に落ちた人は引き上げられる</li>
 *   <li>ロスト状態「断線」「静寂」（静寂の強さ1はボスの帳）。無音の大魚は氷の下では叩けない。声喰いの静寂の届く所</li>
 *   <li>ムーネ：氷の下では叩けない。静寂の帳は、違う2つの鳴り石で破れ、動けない間は受けるダメージが増える</li>
 *   <li>ボスのレア「湖主の髭弓」（予兆の耳）・8階のアクセサリー・セレナの「湖底の祠の石板」</li>
 * </ul>
 * <p>歌の碑と鐘を右クリックして解く道と、祠の石板・歌い手の氷像は、8階を本当に建てて {@link FloorRealPlayGameTests} で確かめる。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LakeGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;

    private LakeGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void floor8Layout(GameTestHelper helper) {
        FloorDef def = FloorDefs.byNumber(8);
        check(helper, def != null && def.bossRoom() != null, "8階の定義がありません");
        check(helper, def.chapter() == 2, "8階が第2章になっていません：" + def.chapter());
        for (int n = 1; n <= 7; n++) {
            FloorDef other = FloorDefs.byNumber(n);
            check(helper, other != null && !def.contains(other.originVec()) && !other.contains(def.originVec()), n + "階と8階が重なっています");
        }
        check(helper, AmemaruMod.id("mune").equals(def.bossRoom().boss()), "8階のボスがムーネではありません：" + def.bossRoom().boss());
        check(helper, def.bossRoom().next() != null, "8階のボスの部屋に、次の階への円がありません");
        FloorFeatures fx = def.features();
        check(helper, fx.requires(FloorFeatures.SEAL_BELLS) && fx.requires(FloorFeatures.SEAL_GUARDIANS),
                "8階の封印の条件が「鐘＋名のある魔物」ではありません");
        FloorFeatures.BellGroup cove = fx.bellGroup("cove");
        FloorFeatures.BellGroup main = fx.bellGroup("main");
        check(helper, fx.bells().size() == 2 && cove != null && main != null, "鐘の組が「入江」と「湖」の2つではありません：" + fx.bells().size());
        check(helper, cove.bells().size() == 3 && !cove.sound() && !cove.raises().isEmpty(), "入江の鐘が3つでないか、石橋が浮かびません");
        check(helper, main.bells().size() == 5 && main.sound() && !main.opens().isEmpty(), "湖の鐘が5つでないか、音が戻らないか、祠のふたが開きません");
        check(helper, main.seconds() > cove.seconds(), "湖の鐘の時間が、入江の鐘より短い");
        for (FloorFeatures.BellGroup group : fx.bells()) {
            List<String> marks = new ArrayList<>();
            for (FloorFeatures.Bell bell : group.bells()) {
                check(helper, !marks.contains(bell.mark()), group.name() + "に、同じ印の鐘が2つあります：" + bell.mark());
                marks.add(bell.mark());
                check(helper, bell.name().endsWith("の鐘") && !BellService.word(bell).isEmpty(), group.name() + "の鐘の名前がおかしい：" + bell.name());
            }
        }
        check(helper, fx.bossMarks("arena_bells").size() == 4, "湖心の鳴り石が4つではありません：" + fx.bossMarks("arena_bells").size());
        check(helper, fx.lake() != null && fx.lake().rescueSeconds() >= 2 && fx.lake().rescueSeconds() <= 10,
                "8階の湖がないか、水から引き上げる秒がおかしい");
        check(helper, fx.environment() != null && fx.environment().silent(), "8階が、音の消えた湖になっていません");
        check(helper, TowerService.namedGuardians(def).size() == 2, "名のある魔物（霜冠・千声喰い）が2体ではありません");
        check(helper, fx.waypoints().size() == 4 && fx.travelStone() != null, "中継の灯が4つ・道しるべの石がありません");
        check(helper, def.extras().chests().size() >= 15, "8階の宝箱が少なすぎます：" + def.extras().chests().size());
        for (String zone : new String[]{"gorge", "cove", "shrine", "fishers"}) {
            check(helper, fx.storyZones().stream().anyMatch(z -> z.id().equals(zone)), "物語の場所 " + zone + " がありません");
            check(helper, StoryEventManager.get(AmemaruMod.id("floor8/" + zone)) != null, "物語 floor8/" + zone + " がありません");
        }
        for (String event : new String[]{"enter", "first_kill", "tablet", "memory_singer", "sound_returns"}) {
            check(helper, StoryEventManager.get(AmemaruMod.id("floor8/" + event)) != null, "物語 floor8/" + event + " がありません");
        }
        check(helper, fx.hearths().size() == 1 && fx.memoryStatues().size() == 1 && "singer".equals(fx.memoryStatues().get(0).id()),
                "祠の灯籠と、最後の歌い手の氷像がありません");
        check(helper, fx.relics().size() == 1 && fx.relics().get(0).items().contains(AmemaruMod.id("lake_shrine_tablet")),
                "湖底の祠の石板（受け取る品）がありません");
        check(helper, RecordDefs.get(AmemaruMod.id("f08_lake_shrine")) != null && def.tablet() != null
                        && def.tablet().title().equals(RecordDefs.get(AmemaruMod.id("f08_lake_shrine")).title()),
                "古代記録「湖底の祠の石板」か、その石板がありません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 鐘

    /** 今日の歌：その日のうちは同じ順番。違う鐘から鳴らすと、はじめから。順番どおりに鳴らすと解ける。湖の鐘で音が戻り、日が変わると戻る。 */
    @GameTest(template = EMPTY, batch = "lake_bells")
    public static void bellsFollowTodaysSong(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FloorDef def = FloorDefs.byNumber(8);
        check(helper, def != null, "8階の定義がありません");
        BellService.Data data = BellService.Data.get(server);
        data.clear();
        try {
            FloorFeatures.BellGroup cove = def.features().bellGroup("cove");
            FloorFeatures.BellGroup main = def.features().bellGroup("main");
            List<Integer> song = BellService.song(server, def, cove);
            check(helper, song.equals(BellService.song(server, def, cove)), "同じ日なのに、今日の歌が変わります");
            check(helper, song.size() == cove.bells().size() && song.stream().distinct().count() == song.size(),
                    "今日の歌が、入江の鐘をひとつずつ使う順番になっていません：" + song);
            check(helper, BellService.songText(server, def, cove).split(" → ").length == song.size(), "今日の歌の文がおかしい："
                    + BellService.songText(server, def, cove));
            FakePlayer player = fake(helper, "bells");
            // 1つ目を正しく、2つ目を間違える → はじめから
            BellService.ring(player, 8, cove.id(), def.world(cove.bells().get(song.get(0)).pos()));
            check(helper, data.progress(8, cove.id()) == 1, "今日の歌の1つ目を鳴らしても、進みません");
            BellService.ring(player, 8, cove.id(), def.world(cove.bells().get(song.get(2)).pos()));
            check(helper, data.progress(8, cove.id()) == 0 && !BellService.solved(server, 8, cove.id()), "違う鐘を鳴らしても、はじめからになりません");
            // 順番どおり
            for (int index : song) {
                BellService.ring(player, 8, cove.id(), def.world(cove.bells().get(index).pos()));
            }
            check(helper, BellService.solved(server, 8, cove.id()), "今日の歌のとおりに鳴らしても、入江の鐘が解けません");
            check(helper, !BellService.allSolved(server, def) && !BellService.soundRestored(server, def), "入江の鐘だけで、音が戻りました");
            int[] count = BellService.count(server, def);
            check(helper, count[0] == 1 && count[1] == 2, "解けた鐘の組の数が 1/2 ではありません：" + count[0] + "/" + count[1]);
            BellService.solve(server, def, main, null);
            check(helper, BellService.allSolved(server, def) && BellService.soundRestored(server, def), "湖の鐘が解けても、音が戻りません");
            check(helper, BellService.groupBits(server, def) == 3, "解けた組の印が 0b11 ではありません：" + BellService.groupBits(server, def));
            // 解けた組は、鳴らしても変わらない
            BellService.ring(player, 8, cove.id(), def.world(cove.bells().get(song.get(1)).pos()));
            check(helper, BellService.solved(server, 8, cove.id()), "解けたあとに鐘を鳴らすと、解けていないことになりました");
            BellService.onDayChanged(server);
            check(helper, !BellService.solved(server, 8, cove.id()) && !BellService.soundRestored(server, def), "日が変わっても、鐘と音が戻りません");
        } finally {
            data.clear();
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 薄氷

    /** 薄氷：ひとりでは割れない。2人が3ブロックのうちにいると、ひびが入り、3段のあとに割れて水の穴になる。凍り直すと、薄氷に戻る。 */
    @GameTest(template = EMPTY, batch = "lake_ice")
    public static void thinIceCracksUnderTwo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), ModBlocks.THIN_ICE.get());
            }
        }
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        FakePlayer a = fake(helper, "ice_a");
        FakePlayer b = fake(helper, "ice_b");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        a.setPos(at.x, at.y, at.z);
        b.setPos(at.x + 1.0, at.y, at.z);
        check(helper, center.equals(ThinIceService.thinUnder(level, a)), "薄氷の上に立っているのに、足もとの薄氷が見つかりません");
        long now = level.getGameTime();
        ThinIceService.tickCrowd(level, List.of(a), now);
        check(helper, crack(level, center) == 0, "ひとりで乗っただけで、薄氷にひびが入りました");
        for (int k = 1; k <= 3; k++) {
            ThinIceService.tickCrowd(level, List.of(a, b), now);
            check(helper, crack(level, center) == k, "2人が近くに乗っても、ひびが " + k + " 段になりません：" + crack(level, center));
        }
        int before = ThinIceService.holes(level.getServer());
        ThinIceService.tickCrowd(level, List.of(a, b), now);
        check(helper, level.getBlockState(center).is(Blocks.WATER), "ひびが3段のあとも集まっているのに、割れません");
        check(helper, ThinIceService.holes(level.getServer()) >= before + 9, "割れた所が、凍り直す穴として覚えられていません");
        ThinIceService.refreeze(level, true);
        BlockState back = level.getBlockState(center);
        check(helper, back.is(ModBlocks.THIN_ICE.get()) && back.getValue(LakeBlocks.CRACK) == 0, "凍り直しても、ひびのない薄氷に戻りません："
                + back);
        // 湖の氷も、敵が割ると穴になる
        helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.LAKE_ICE.get());
        int broke = ThinIceService.breakIce(level, Vec3.atCenterOf(center), 0.5, 40);
        check(helper, broke == 1 && level.getBlockState(center).is(Blocks.WATER), "湖の氷を割れません");
        ThinIceService.refreeze(level, true);
        check(helper, level.getBlockState(center).is(ModBlocks.LAKE_ICE.get()), "割れた湖の氷が、凍り直しません");
        helper.succeed();
    }

    /** 水に落ちた人は、rescue_seconds 秒で、最後に立っていた所へ引き上げられる（その所を覚える・秒を数える）。 */
    @GameTest(template = EMPTY, batch = "lake_ice")
    public static void fallenIntoWaterAreRescued(GameTestHelper helper) {
        FloorDef def = FloorDefs.byNumber(8);
        check(helper, def != null && def.features().lake() != null, "8階の湖がありません");
        FloorFeatures.Lake lake = def.features().lake();
        FakePlayer player = fake(helper, "rescue");
        Vec3 safe = helper.absoluteVec(new Vec3(0.5, 2.0, 0.5));
        player.setPos(safe.x, safe.y, safe.z);
        player.setOnGround(true);
        check(helper, !ThinIceService.rescue(player, lake, def, false), "水の外なのに、引き上げられました");
        check(helper, ThinIceService.safeSpot(player) != null && ThinIceService.safeSpot(player).distanceTo(safe) < 0.01,
                "地面に立っても、その所が覚えられません");
        Vec3 wet = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
        player.setPos(wet.x, wet.y, wet.z);
        player.setOnGround(false);
        int steps = lake.rescueSeconds() * 2;
        for (int i = 1; i < steps; i++) {
            check(helper, !ThinIceService.rescue(player, lake, def, true), (i * 0.5) + " 秒で、もう引き上げられました（" + lake.rescueSeconds() + " 秒のはず）");
        }
        check(helper, ThinIceService.rescue(player, lake, def, true), lake.rescueSeconds() + " 秒たっても、水から引き上げられません");
        // 引き上げたら、数えなおし（にせのプレイヤーは通信がないので、動いた先はここでは見ない。遊んで確かめる）
        check(helper, !ThinIceService.rescue(player, lake, def, true), "引き上げたあと、すぐにまた引き上げられました");
        player.removeAllEffects();
        helper.succeed();
    }

    // ---------------------------------------------------------------- ロスト状態

    /** 断線・静寂はロスト状態。静寂は強い方（帳の強さ1）を残す。結び紐と同じく、まとめてほどける。 */
    @GameTest(template = EMPTY, batch = "lake_lost")
    public static void severAndSilenceStates(GameTestHelper helper) {
        FakePlayer player = fake(helper, "lost8");
        player.removeAllEffects();
        check(helper, LostService.isLost(ModEffects.LOST_SEVER.get()) && LostService.isLost(ModEffects.LOST_SILENCE.get()),
                "断線・静寂がロスト状態になっていません");
        LostService.inflict(player, ModEffects.LOST_SILENCE.get(), 200);
        check(helper, player.getEffect(ModEffects.LOST_SILENCE.get()).getAmplifier() == 0, "声喰いの静寂の強さが0ではありません");
        LostService.inflict(player, ModEffects.LOST_SILENCE.get(), 100, 1);
        check(helper, player.getEffect(ModEffects.LOST_SILENCE.get()).getAmplifier() == 1
                && player.getEffect(ModEffects.LOST_SILENCE.get()).getDuration() >= 190, "静寂の帳（強さ1）にならないか、残りが短くなりました");
        LostService.inflict(player, ModEffects.LOST_SILENCE.get(), 400, 0);
        check(helper, player.getEffect(ModEffects.LOST_SILENCE.get()).getAmplifier() == 1, "弱い静寂で、帳（強さ1）が弱くなりました");
        LostService.inflict(player, ModEffects.LOST_SEVER.get(), 300);
        check(helper, player.hasEffect(ModEffects.LOST_SEVER.get()) && LostService.anyLost(player), "断線になりません");
        check(helper, LostService.clear(player) == 2 && !LostService.anyLost(player), "断線と静寂が、まとめてほどけません");
        // 声喰い（静寂の届く所）・名のある千声喰いはもっと広い。氷霧の亡霊は断線
        EnemyDef eater = EnemyDefs.get(AmemaruMod.id("voice_eater"));
        EnemyDef elder = EnemyDefs.get(AmemaruMod.id("voice_eater_elder"));
        check(helper, eater != null && elder != null && eater.floor() == 8 && eater.behavior("silence_range", 0) > 3
                && elder.behavior("silence_range", 0) > eater.behavior("silence_range", 0), "声喰い・千声喰いの静寂の届く所がおかしい");
        EnemyDef wraith = EnemyDefs.get(AmemaruMod.id("fog_wraith"));
        check(helper, wraith != null && wraith.behavior("sever_seconds", 0) > 0, "氷霧の亡霊が、断線にしません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 無音の大魚

    /** 氷の下では叩けない。跳び上がると氷の上でもがき、その間だけ叩ける。 */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void silentFishOnlyHittableWhenBeached(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SilentFish fish = ModEntities.SILENT_FISH.get().create(level);
        check(helper, fish != null, "無音の大魚を作れません");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        fish.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(fish);
        EnemyDef def = EnemyDefs.forType(ModEntities.SILENT_FISH.get());
        check(helper, def != null && def.floor() == 8, "無音の大魚の定義がありません");
        int windup = def.skill("lunge", 1.5, 1.3, 7.0, 18.0).windupTicks();
        helper.runAfterDelay(2, () -> {
            check(helper, fish.submerged() && Math.abs(fish.damageTakenMultiplier(null)) < EPSILON && !fish.isPickable(),
                    "氷の下にいる無音の大魚を叩けます");
            fish.testStartRise(at);
            check(helper, "RISE".equals(fish.testState()), "跳び上がる準備に入りません：" + fish.testState());
        });
        helper.runAfterDelay(windup + 6, () -> {
            check(helper, "SURFACED".equals(fish.testState()) && !fish.submerged(), "跳び上がったあと、氷の上に出ません：" + fish.testState());
            check(helper, Math.abs(fish.damageTakenMultiplier(null) - 1.0) < EPSILON, "氷の上でもがいている間に、叩けません");
            fish.discard();
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- ムーネ

    /** 氷の下では叩けない。静寂の帳は、同じ鳴り石では破れず、違う2つで破れる。破れると氷の上に出て動けず、受けるダメージが増える。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void muneVeilBrokenByTwoBells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = fake(helper, "mune");
        Mune boss = ModEntities.MUNE.get().create(level);
        check(helper, boss != null, "ムーネを作れません");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        boss.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        EnemyDef def = EnemyDefs.forType(ModEntities.MUNE.get());
        check(helper, def != null && def.floor() == 8 && def.pity(), "ムーネの定義がないか、天井がありません");
        check(helper, def.rare().stream().anyMatch(r -> r.item().equals(AmemaruMod.id("lake_lord_whisker_bow")) && Math.abs(r.chance() - 4.0) < EPSILON),
                "ムーネのレアに「湖主の髭弓」4% がありません");
        check(helper, boss.skillNames().containsAll(List.of("rise", "sweep", "tail", "veil", "wave")), "ムーネの技がそろっていません：" + boss.skillNames());
        double broken = def.skill("veil", 0, 2, 70, 0).param("broken", 1.3);
        helper.runAfterDelay(2, () -> {
            check(helper, boss.submerged() && Math.abs(boss.damageTakenMultiplier(null)) < EPSILON, "氷の下のムーネを叩けます");
            BlockPos bellA = helper.absolutePos(new BlockPos(0, 1, 0));
            BlockPos bellB = helper.absolutePos(new BlockPos(2, 1, 2));
            check(helper, !boss.onBell(player, bellA), "静寂の帳がないのに、鳴り石で何かが起きました");
            boss.castVeil(level);
            check(helper, boss.veiled() && "veil".equals(boss.status()), "静寂の帳が広がりません：" + boss.status());
            check(helper, !boss.onBell(player, bellA) && !boss.onBell(player, bellA) && boss.veiled(), "同じ鳴り石を2回鳴らしただけで、帳が破れました");
            check(helper, boss.onBell(player, bellB), "違う2つの鳴り石を続けて鳴らしても、帳が破れません");
            check(helper, !boss.veiled() && boss.dazed() && !boss.submerged(), "帳が破れても、ムーネが氷の上で動けなくなりません");
            check(helper, Math.abs(boss.damageTakenMultiplier(null) - broken) < EPSILON, "動けない間、受けるダメージが " + broken + " 倍になりません："
                    + boss.damageTakenMultiplier(null));
            check(helper, "dazed".equals(boss.status()), "攻めどきの知らせ（dazed）が出ません：" + boss.status());
            boss.startWaveNow(level);
            check(helper, boss.testWaveRadius() > 0, "断ち波が出ません");
            boss.discard();
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- 装備・アクセサリー・セレナ

    @GameTest(template = EMPTY)
    public static void floor8GearAndSerena(GameTestHelper helper) {
        EquipmentDef bow = EquipmentDefs.server().get(AmemaruMod.id("lake_lord_whisker_bow"));
        check(helper, bow != null && bow.rare(), "湖主の髭弓の定義がありません");
        check(helper, bow.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.WARNING_EAR), "湖主の髭弓の効き目が「予兆の耳」ではありません");
        check(helper, bow.jobs().contains(AmemaruMod.id("archer")), "湖主の髭弓を、アーチャーが使えません");
        int floor8 = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            AccessoryDef def = AccessoryDefs.get(item);
            if (def != null && def.floor() == 8) {
                floor8++;
            }
        }
        check(helper, floor8 == 7, "8階のアクセサリーが7つ（作る5つ・レアのお守り2つ）ではありません：" + floor8);
        AccessoryDef plume = AccessoryDefs.get(ModItems.MANIFEST.get("frost_crown_plume").get());
        check(helper, plume != null && plume.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.REGEN), "霜冠の冠羽の効き目が「HPが戻る」ではありません");
        AccessoryDef stone = AccessoryDefs.get(ModItems.MANIFEST.get("thousand_voice_stone").get());
        check(helper, stone != null && stone.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.ALLY_POWER),
                "千の声の喉石の効き目が「仲間の数で力」ではありません");
        String[][] named = {{"frost_crane_lord", "frost_crown_plume"}, {"voice_eater_elder", "thousand_voice_stone"}};
        for (String[] pair : named) {
            EnemyDef def = EnemyDefs.get(AmemaruMod.id(pair[0]));
            check(helper, def != null && def.rare().stream().anyMatch(r -> r.item().getPath().equals(pair[1])),
                    pair[0] + "（名のある魔物）が、レアのお守り " + pair[1] + " を落としません");
        }
        List<WorkshopRecipe> recipes = helper.getLevel().getServer().getRecipeManager().getAllRecipesFor(ModRecipes.WORKSHOP_TYPE.get());
        for (String name : new String[]{"silent_ice_battle_ring", "silent_ice_wisdom_ring", "fish_scale_necklace", "frost_crane_bracelet",
                "bell_charm"}) {
            WorkshopRecipe recipe = recipes.stream().filter(r -> r.getId().getPath().endsWith(name)).findFirst().orElse(null);
            check(helper, recipe != null && recipe.ingredients().stream().anyMatch(c -> c.item().equals(AmemaruMod.id("pedestal_cloth"))),
                    name + " のレシピがないか、防具職人の台座布を使いません");
        }
        check(helper, DialogueDefs.get(AmemaruMod.id("serena/lake_tablet")) != null, "セレナの「湖底の祠の石板」の会話がありません");
        check(helper, QuestDefs.get(AmemaruMod.id("lake_tablet")) != null, "頼みごと「湖底の祠の石板」がありません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    private static int crack(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.THIN_ICE.get()) ? state.getValue(LakeBlocks.CRACK) : -1;
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "aml_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-f8:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}
