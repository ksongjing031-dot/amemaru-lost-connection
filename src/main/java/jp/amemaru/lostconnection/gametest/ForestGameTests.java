package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.combat.PoisonService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.enemy.boss.OldWard;
import jp.amemaru.lostconnection.enemy.boss.OldWardKnot;
import jp.amemaru.lostconnection.enemy.forest.HakageKitsune;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import jp.amemaru.lostconnection.guild.RecordDef;
import jp.amemaru.lostconnection.guild.RecordDefs;
import jp.amemaru.lostconnection.story.StoryEventDef;
import jp.amemaru.lostconnection.story.StoryEventManager;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.TowerService;
import jp.amemaru.lostconnection.tower.forest.LostWoodsService;
import jp.amemaru.lostconnection.tower.forest.WaypointService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Phase 8 その1（2階「忘れられた森」）の自動テスト。
 *
 * <ul>
 *   <li>2階の形：迷いの森3つ（正しい出口は1つずつ。鐘・灯台・入口は箱の中）・中継の灯5つ・道しるべの石・霧・物語の場所・
 *       宝箱の守り手・森番の日誌（古代記録と同じ名前）・ボスの部屋</li>
 *   <li>迷いの森：つなぐと封印が開く（3つとも）。日が変わるとほどける。保存して読み直しても同じ</li>
 *   <li>中継の灯：ともした中で、いちばん先の灯へ行く</li>
 *   <li>毒と解毒薬：解毒薬のあとは毒にならない</li>
 *   <li>森番の古樹：光る節が3つ。3つとも壊すと怯み、守りがなくなる。HP30%の暗い森で、森番の灯が2つ落ちる</li>
 *   <li>白い木立の葉影ギツネは、湧く所の輪を歩く。2階の敵とボスの定義・古樹の枝杖（魔法が当たると回復）</li>
 * </ul>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForestGameTests {
    private static final String EMPTY = "empty3x3x3";

    private ForestGameTests() {
    }

    // ---------------------------------------------------------------- 2階の形

    @GameTest(template = EMPTY)
    public static void floor2Layout(GameTestHelper helper) {
        FloorDef def = FloorDefs.byNumber(2);
        check(helper, def != null && def.bossRoom() != null, "2階の定義がありません");
        check(helper, def.sizeX() > 384 && def.sizeZ() > 384, "2階が1階より広くありません（" + def.sizeX() + "×" + def.sizeZ() + "）");
        FloorDef one = FloorDefs.byNumber(1);
        check(helper, one != null && !def.contains(one.originVec()) && !one.contains(def.originVec()), "1階と2階が重なっています");
        Vec3 o = def.originVec();
        FloorDef.BossRoomDef boss = def.bossRoom();
        check(helper, AmemaruMod.id("old_ward").equals(boss.boss()), "2階のボスが森番の古樹ではありません：" + boss.boss());
        Vec3 spawn = o.add(boss.spawn().pos());
        check(helper, boss.inside(spawn, o) && boss.inside(o.add(boss.exit().center()), o), "ボスの出る所か出口の円が部屋の外です");
        check(helper, !boss.inside(o.add(def.entrance().pos()), o), "入口がボスの部屋の中です");
        for (FloorDef.SpawnZone zone : def.spawns()) {
            Vec3 at = o.add(zone.center());
            check(helper, def.contains(at) && !boss.inside(at, o), "湧く所 " + zone.id() + " が階の外か、ボスの部屋の中です");
        }
        FloorFeatures f = def.features();
        check(helper, f.environment() != null && f.environment().fogEnd() > f.environment().fogStart(), "2階に霧がありません");
        check(helper, FloorFeatures.SEAL_LOST_WOODS.equals(f.sealRequires()), "2階の封印が迷いの森で開きません：" + f.sealRequires());
        check(helper, f.lostWoods().size() == 3, "迷いの森が3つではありません：" + f.lostWoods().size());
        int exits = 0;
        int decoys = 0;
        for (FloorFeatures.LostWoods woods : f.lostWoods()) {
            AABB box = woods.box();
            check(helper, woods.correctExit() != null, woods.id() + " に正しい出口がありません");
            check(helper, box.contains(Vec3.atCenterOf(woods.bell())), woods.id() + " の鐘が箱の外です");
            check(helper, box.contains(woods.entry().pos()), woods.id() + " の戻される所が箱の外です");
            for (FloorFeatures.Exit exit : woods.exits()) {
                check(helper, box.contains(Vec3.atCenterOf(exit.post())), exit.id() + " の灯台が箱の外です");
                check(helper, exit.box().intersects(box), exit.id() + " の出口が箱のふちにありません");
                check(helper, !exit.box().contains(woods.entry().pos()), exit.id() + " の出口の中に、戻される所があります（戻ってもすぐ戻される）");
            }
            exits += woods.exits().size();
            decoys += woods.decoys();
        }
        // 奥の森ほど出口が多く、にせのこだまも多い（西2・東3・奥4、にせ0・1・2）
        check(helper, exits == 9 && decoys == 3, "迷いの森の出口とにせのこだまの数が違います：" + exits + "・" + decoys);
        check(helper, f.waypoints().size() == 5 && f.travelStone() != null, "中継の灯が5つ、道しるべの石が1つではありません");
        for (FloorFeatures.Waypoint point : f.waypoints()) {
            check(helper, def.contains(o.add(point.arrive().pos())) && !boss.inside(o.add(point.arrive().pos()), o),
                    "中継の灯「" + point.name() + "」の着く所が階の外か、ボスの部屋の中です");
        }
        check(helper, f.storyZones().stream().anyMatch(z -> z.id().equals("noa_sighting")), "霧の奥の少女の場所がありません");
        // 宝箱の守り手（樹皮兵長・葉隠れの長）
        check(helper, def.extras().guardians().size() == 2, "守り手が2体ではありません");
        for (FloorDef.Guardian guardian : def.extras().guardians()) {
            check(helper, guardian.pillar() == -1, guardian.id() + " が石柱の守り手になっています（宝箱の守り手のはず）");
            check(helper, guardian.variant() != null && EnemyDefs.get(guardian.variant()) != null, guardian.id() + " の個体の定義がありません");
        }
        long guarded = def.extras().chests().stream().filter(c -> !c.guardian().isEmpty()).count();
        check(helper, def.extras().chests().size() >= 12 && guarded == 2, "2階の宝箱が足りないか、守られた宝箱が2つではありません");
        // 森番の日誌：石板の名前と同じ古代記録が2階にある
        check(helper, def.tablet() != null && "森番の日誌".equals(def.tablet().title()), "森番の日誌の石板がありません");
        boolean record = false;
        for (RecordDef r : RecordDefs.sorted()) {
            record |= r.floor() == 2 && r.title().equals(def.tablet().title());
        }
        check(helper, record, "森番の日誌の古代記録がありません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 迷いの森

    @GameTest(template = EMPTY)
    public static void lostWoodsTieAndReset(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FloorDef def = FloorDefs.byNumber(2);
        check(helper, def != null, "2階の定義がありません");
        FakePlayer player = fake(helper, "woods");
        LostWoodsService.onDayChanged(server);
        check(helper, !LostWoodsService.allTied(server, def) && !TowerService.pillarsComplete(player, 2),
                "日が変わったのに、迷いの森がほどけていません");
        List<FloorFeatures.LostWoods> woods = def.features().lostWoods();
        for (int i = 0; i < woods.size(); i++) {
            check(helper, LostWoodsService.adminTie(server, 2, woods.get(i).id()), woods.get(i).id() + " をつなげません");
            check(helper, LostWoodsService.isTied(server, 2, woods.get(i).id()), woods.get(i).id() + " がつながっていません");
            boolean last = i == woods.size() - 1;
            check(helper, LostWoodsService.allTied(server, def) == last, "3つつなぐ前に封印が開く・3つつないでも開かない");
        }
        check(helper, LostWoodsService.tieBits(server, def) == 0b111, "つないだ印が3つ立っていません：" + LostWoodsService.tieBits(server, def));
        check(helper, TowerService.pillarsComplete(player, 2), "3つともつないだのに、封印が開きません");
        // 保存して読み直しても同じ
        LostWoodsService.Data copy = LostWoodsService.Data.load(LostWoodsService.Data.get(server).save(new net.minecraft.nbt.CompoundTag()));
        check(helper, copy.isTied(2, woods.get(0).id()), "保存して読み直すと、つないだ印が消えます");
        check(helper, !LostWoodsService.adminTie(server, 2, "nowhere"), "ない迷いの森をつなげました");
        // 結び目の灯台の見た目は、読み込まれたときに今の結び目に合わせる（読み込まれていない間に日が変わっても、灯ったまま残らない）
        FloorFeatures.LostWoods north = def.features().lostWoods("north");
        int correct = north.exits().indexOf(north.correctExit());
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos at = helper.absolutePos(rel);
        helper.setBlock(rel, jp.amemaru.lostconnection.core.registry.ModBlocks.KNOT_POST.get().defaultBlockState());
        jp.amemaru.lostconnection.tower.FloorThingBlockEntity post =
                (jp.amemaru.lostconnection.tower.FloorThingBlockEntity) helper.getLevel().getBlockEntity(at);
        check(helper, post != null, "結び目の灯台に、仕掛けの中身がありません");
        post.set(2, correct, "north");
        LostWoodsService.syncPost(helper.getLevel(), at);
        check(helper, helper.getBlockState(rel).getValue(jp.amemaru.lostconnection.tower.forest.ForestBlocks.LIT), "結ばれた森の正しい灯台が、読み込んでも灯りません");
        post.set(2, (correct + 1) % north.exits().size(), "north");
        LostWoodsService.syncPost(helper.getLevel(), at);
        check(helper, !helper.getBlockState(rel).getValue(jp.amemaru.lostconnection.tower.forest.ForestBlocks.LIT), "間違いの出口の灯台が灯っています");
        post.set(2, correct, "north");
        LostWoodsService.syncPost(helper.getLevel(), at);
        LostWoodsService.onDayChanged(server);
        LostWoodsService.syncPost(helper.getLevel(), at);
        check(helper, !helper.getBlockState(rel).getValue(jp.amemaru.lostconnection.tower.forest.ForestBlocks.LIT), "日が変わってほどけたのに、灯台が灯ったままです");
        LostWoodsService.onDayChanged(server);
        check(helper, LostWoodsService.tieBits(server, def) == 0, "日が変わっても、迷いの森がほどけません");
        // 方角：北が上、東が右（ことばの鍵）
        check(helper, LostWoodsService.direction(Vec3.ZERO, new Vec3(0, 0, -10)).getString().equals(
                net.minecraft.network.chat.Component.translatable("direction.amemaru.north").getString()), "北の方角が違います");
        check(helper, LostWoodsService.direction(Vec3.ZERO, new Vec3(10, 0, 0)).getString().equals(
                net.minecraft.network.chat.Component.translatable("direction.amemaru.east").getString()), "東の方角が違います");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 中継の灯

    @GameTest(template = EMPTY)
    public static void waypointsTakeYouFurthest(GameTestHelper helper) {
        FakePlayer player = fake(helper, "waypoint");
        MinecraftServer server = helper.getLevel().getServer();
        FloorDef def = FloorDefs.byNumber(2);
        WaypointService.Data data = WaypointService.Data.get(server);
        data.clear(player.getUUID());
        check(helper, WaypointService.furthest(player, def) == null, "ともしていないのに、中継の灯へ行けます");
        data.add(player.getUUID(), 2, 0);
        data.add(player.getUUID(), 2, 2);
        FloorFeatures.Waypoint best = WaypointService.furthest(player, def);
        check(helper, best != null && best.index() == 2, "いちばん先の中継の灯へ行きません：" + (best == null ? "なし" : best.name()));
        check(helper, data.has(player.getUUID(), 2, 0) && !data.has(player.getUUID(), 2, 1), "ともした灯の覚え方が違います");
        data.clear(player.getUUID());
        helper.succeed();
    }

    // ---------------------------------------------------------------- 毒と解毒薬

    @GameTest(template = EMPTY)
    public static void poisonAndAntidote(GameTestHelper helper) {
        FakePlayer player = fake(helper, "poison");
        player.removeAllEffects();
        check(helper, PoisonService.poison(player, 6, 0), "毒になりません");
        check(helper, PoisonService.poisoned(player) && player.hasEffect(ModEffects.POISON.get()), "毒のしるしが付きません");
        PoisonService.cure(player, 30);
        check(helper, !PoisonService.poisoned(player) && !player.hasEffect(ModEffects.POISON.get()), "解毒薬で毒が消えません");
        check(helper, player.hasEffect(ModEffects.ANTIDOTE.get()), "解毒薬のあとの毒よけが付きません");
        check(helper, !PoisonService.poison(player, 6, 0) && !PoisonService.poisoned(player), "毒よけの間に、毒になりました");
        player.removeAllEffects();
        // 薬屋で売っている
        Item antidote = ModItems.MANIFEST.get("antidote").get();
        boolean sold = jp.amemaru.lostconnection.economy.ShopDefs.all().values().stream()
                .anyMatch(shop -> shop.sells().stream().anyMatch(e -> e.item().equals(AmemaruMod.id("antidote"))));
        check(helper, sold && antidote != null, "解毒薬が店にありません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 森番の古樹

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void oldWardKnotsStunAndDark(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OldWard ward = ModEntities.OLD_WARD.get().create(level);
        check(helper, ward != null, "森番の古樹を作れません");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        ward.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(ward);
        ward.onFightStarted(level, List.of());
        helper.runAfterDelay(2, () -> {
            check(helper, ward.knotCount() == 3, "光る節が3つ生えていません：" + ward.knotCount());
            double armored = ward.defense();
            List<OldWardKnot> knots = level.getEntitiesOfClass(OldWardKnot.class, ward.getBoundingBox().inflate(4.0));
            check(helper, knots.size() == 3, "光る節が幹のまわりにありません：" + knots.size());
            for (OldWardKnot knot : knots) {
                Vec3 p = knot.position().subtract(ward.position());
                double r = Math.sqrt(p.x * p.x + p.z * p.z);
                check(helper, r > 1.0 && r < 2.0 && p.y > 0.5 && p.y < 3.6, "光る節が幹の面にありません：" + p);
            }
            EnemyDef knotDef = EnemyDefs.get(AmemaruMod.id("old_ward_knot"));
            check(helper, knotDef != null && !knotDef.bestiary() && knotDef.exp() == 0, "光る節が図鑑に載るか、経験値があります");
            for (OldWardKnot knot : knots) {
                CombatService.applyDamage(knot, 1.0E7, false, level.damageSources().generic());
            }
            helper.runAfterDelay(2, () -> {
                check(helper, ward.knotCount() == 0 && ward.stunned(), "節を3つとも壊しても、怯みません");
                check(helper, ward.defense() == 0.0 && armored > 0.0, "怯んでいる間に、守りが残っています");
                check(helper, ward.forceSkill("dark") && ward.isDark(), "森が暗くなりません");
                List<ItemEntity> lights = level.getEntitiesOfClass(ItemEntity.class, ward.getBoundingBox().inflate(12.0),
                        e -> e.getItem().is(ModItems.MANIFEST.get("warden_light").get()));
                check(helper, lights.size() == 2, "森番の灯が2つ落ちません：" + lights.size());
                for (ItemEntity light : lights) {
                    light.discard();
                }
                ward.discard();
                helper.succeed();
            });
        });
    }

    // ---------------------------------------------------------------- 敵・ボス・レア装備の定義

    @GameTest(template = EMPTY)
    public static void forestEnemiesAndRare(GameTestHelper helper) {
        for (String id : List.of("mayoi_bi", "juhihei", "pokodake", "hakage_kitsune", "old_ward")) {
            EnemyDef def = EnemyDefs.get(AmemaruMod.id(id));
            check(helper, def != null && def.floor() == 2, id + " が2階の敵として定義されていません");
            check(helper, def.lootTable() != null, id + " にドロップ表がありません");
        }
        // 名のある魔物は、元の敵の見た目を借りた大きな個体
        for (String[] pair : new String[][]{{"juhihei_captain", "juhihei"}, {"hakage_kitsune_chief", "hakage_kitsune"}}) {
            EnemyDef def = EnemyDefs.get(AmemaruMod.id(pair[0]));
            check(helper, def != null && AmemaruMod.id(pair[1]).equals(def.entity()) && def.behavior("scale", 1.0) > 1.0,
                    pair[0] + " が " + pair[1] + " の見た目を借りる大きな個体ではありません");
        }
        // ボスのレア：古樹の枝杖（6%。魔法が当たると少し回復）
        EnemyDef ward = EnemyDefs.get(AmemaruMod.id("old_ward"));
        boolean staff = ward.rare().stream().anyMatch(r -> r.item().equals(AmemaruMod.id("elder_branch_staff")) && r.chance() == 6.0);
        check(helper, staff && ward.pity(), "森番の古樹のレアに、古樹の枝杖（6%）がありません");
        EquipmentDef gear = EquipmentDefs.server().get(ModItems.MANIFEST.get("elder_branch_staff").get());
        check(helper, gear != null && gear.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.HEAL_ON_HIT
                && e.source() == SpecialEffect.Source.SKILL), "古樹の枝杖に「魔法が当たると回復」がありません");
        // 白い木立の葉影ギツネ：輪を歩く
        HakageKitsune fox = ModEntities.HAKAGE_KITSUNE.get().create(helper.getLevel());
        check(helper, fox != null, "葉影ギツネを作れません");
        fox.setPatrol(List.of(BlockPos.ZERO, new BlockPos(4, 0, 0), new BlockPos(4, 0, 4)));
        check(helper, fox.loop().size() == 3, "葉影ギツネが輪を覚えません");
        fox.discard();
        // 物語：霧の奥の少女（初めてのとき全員に知らせる）
        StoryEventDef noa = StoryEventManager.get(AmemaruMod.id("floor2/noa_sighting"));
        check(helper, noa != null && noa.triggerType().equals("floor_zone") && noa.triggerZone().equals("noa_sighting")
                && noa.once() && !noa.announce().isEmpty(), "霧の奥の少女のイベントがありません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "amt_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, profileName));
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}
