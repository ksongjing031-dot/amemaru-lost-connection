package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.enemy.boss.Hydram;
import jp.amemaru.lostconnection.enemy.waterway.KibaNamazu;
import jp.amemaru.lostconnection.enemy.waterway.Soujikihei;
import jp.amemaru.lostconnection.enemy.waterway.SuimonNezumi;
import jp.amemaru.lostconnection.enemy.waterway.YodomiSlime;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearService;
import jp.amemaru.lostconnection.equipment.GearSlot;
import jp.amemaru.lostconnection.guild.ErnestoArrival;
import jp.amemaru.lostconnection.guild.RecordDef;
import jp.amemaru.lostconnection.guild.RecordDefs;
import jp.amemaru.lostconnection.item.AmemaruArmorItem;
import jp.amemaru.lostconnection.item.ItemManifest;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.story.StoryEventDef;
import jp.amemaru.lostconnection.story.StoryEventManager;
import jp.amemaru.lostconnection.story.StoryState;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.TowerService;
import jp.amemaru.lostconnection.tower.waterway.FloodgateService;
import jp.amemaru.lostconnection.workshop.ModRecipes;
import jp.amemaru.lostconnection.workshop.WorkshopRecipe;
import jp.amemaru.lostconnection.workshop.WorkshopType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 8 その2（3階「古代の水路」）の自動テスト。
 *
 * <ul>
 *   <li>3階の形：2階より広い。水門3組と排水（あとの水門は先の水門のあとでないと回らない。水門②は扉つき）・水の区画・
 *       中継の灯5つ・名のある魔物3体・白い流れ（物語の場所）・水路管理局の掲示（古代記録と同じ名前）・ボスの部屋</li>
 *   <li>水門：ハンドルを1つ回しただけでは開かない。2つを決まった秒数のうちに回すと開く。3組とも開き、名のある魔物を3体とも
 *       倒すと封印が開く。日が変わると閉じる。保存して読み直しても同じ</li>
 *   <li>敵の守り：掃除機兵は前から半分・ナマズは水の中で半分・番人は水の鎧で1/5、炉心がむき出しで2倍</li>
 *   <li>澱みスライムは倒すと2匹に分かれる。ネズミの親分はHPが減ると子分を呼ぶ</li>
 *   <li>エルネスト：3階の記録を持って街へ戻ると来る</li>
 * </ul>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterwayGameTests {
    private static final String EMPTY = "empty3x3x3";

    private WaterwayGameTests() {
    }

    // ---------------------------------------------------------------- 3階の形

    @GameTest(template = EMPTY)
    public static void floor3Layout(GameTestHelper helper) {
        FloorDef def = FloorDefs.byNumber(3);
        check(helper, def != null && def.bossRoom() != null, "3階の定義がありません");
        FloorDef two = FloorDefs.byNumber(2);
        check(helper, two != null && def.sizeX() > two.sizeX() && def.sizeZ() > two.sizeZ(),
                "3階が2階より広くありません（" + def.sizeX() + "×" + def.sizeZ() + "）");
        for (int n = 1; n <= 2; n++) {
            FloorDef other = FloorDefs.byNumber(n);
            check(helper, other != null && !def.contains(other.originVec()) && !other.contains(def.originVec()), n + "階と3階が重なっています");
        }
        FloorDef.BossRoomDef boss = def.bossRoom();
        check(helper, AmemaruMod.id("hydram").equals(boss.boss()), "3階のボスが大水門の番人ではありません：" + boss.boss());
        check(helper, boss.scaling().hpMinPlayers() >= 2 && boss.scaling().enrageSeconds() > 0, "3階のボスが、ひとりでも2人ぶんのHPになっていません");
        FloorFeatures fx = def.features();
        check(helper, fx.requires(FloorFeatures.SEAL_FLOODGATES) && fx.requires(FloorFeatures.SEAL_GUARDIANS),
                "3階の封印の条件が、水門と名のある魔物になっていません：" + fx.sealRequires());
        check(helper, fx.floodgates().size() == 4 && fx.floodgate(FloorFeatures.DRAIN) != null, "水門が3組と排水になっていません");
        FloorFeatures.Floodgate first = fx.floodgate("first");
        FloorFeatures.Floodgate deep = fx.floodgate("deep");
        FloorFeatures.Floodgate moat = fx.floodgate("moat");
        check(helper, first != null && deep != null && moat != null, "水門の組の名前が違います");
        check(helper, first.after().isEmpty() && "first".equals(deep.after()) && "deep".equals(moat.after()), "水門の順番が違います");
        check(helper, deep.doors().size() == 2, "水門②の扉が2つではありません：" + deep.doors().size());
        for (FloorFeatures.Floodgate gate : fx.floodgates()) {
            check(helper, gate.valves().size() == 2 && !gate.drains().isEmpty(), gate.id() + " のハンドルか水の区画が足りません");
            for (BlockPos valve : gate.valves()) {
                check(helper, def.contains(Vec3.atBottomCenterOf(def.world(valve))), gate.id() + " のハンドルが階の外です");
            }
            for (String section : gate.drains()) {
                check(helper, fx.waterSection(section) != null, gate.id() + " の水の区画 " + section + " がありません");
            }
            double apart = gate.valves().get(0).distSqr(gate.valves().get(1));
            check(helper, apart > 40.0 * 40.0, gate.id() + " の2つのハンドルが近すぎます（ひとりで回せてしまう）");
        }
        check(helper, fx.waterSections().size() == 5, "水の区画が5つではありません：" + fx.waterSections().size());
        check(helper, fx.waypoints().size() == 5 && fx.travelStone() != null, "中継の灯が5つ・道しるべの石がありません");
        check(helper, fx.environment() != null, "3階の霧がありません");
        check(helper, fx.storyZones().stream().anyMatch(z -> z.id().equals("white_flow")), "白い流れの物語の場所がありません");
        check(helper, TowerService.namedGuardians(def).size() == 3, "名のある魔物が3体ではありません：" + TowerService.namedGuardians(def).size());
        check(helper, def.tablet() != null && "水路管理局の掲示".equals(def.tablet().title()), "水路管理局の掲示がありません");
        boolean record = false;
        for (RecordDef r : RecordDefs.sorted()) {
            record |= r.floor() == 3 && r.title().equals(def.tablet().title());
        }
        check(helper, record, "水路管理局の掲示と同じ名前の古代記録がありません");
        check(helper, def.spawns().stream().anyMatch(z -> z.inWater() && z.enemy().equals(AmemaruMod.id("kiba_namazu"))),
                "牙ナマズの、水の中に湧く所がありません");
        check(helper, def.extras().chests().size() >= 12, "3階の宝箱が少なすぎます：" + def.extras().chests().size());
        helper.succeed();
    }

    // ---------------------------------------------------------------- 水門

    @GameTest(template = EMPTY)
    public static void floodgateNeedsTwoHands(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FloorDef def = FloorDefs.byNumber(3);
        check(helper, def != null, "3階の定義がありません");
        FloodgateService.onDayChanged(server);
        FakePlayer a = fake(helper, "gate_a");
        FakePlayer b = fake(helper, "gate_b");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        // 先の水門が閉じているうちは、あとの水門は回らない
        check(helper, !FloodgateService.turn(a, 3, "deep", 0, pos) && !FloodgateService.turn(b, 3, "deep", 1, pos)
                && !FloodgateService.isOpen(server, 3, "deep"), "水門①を開ける前に、水門②が開きました");
        // ひとりでは開かない（同じハンドルを何度回しても）
        check(helper, !FloodgateService.turn(a, 3, "first", 0, pos) && !FloodgateService.turn(a, 3, "first", 0, pos)
                && !FloodgateService.isOpen(server, 3, "first"), "ハンドルを1つ回しただけで、水門が開きました");
        // もう片方を、決まった秒数のうちに回すと開く
        check(helper, FloodgateService.turn(b, 3, "first", 1, pos) && FloodgateService.isOpen(server, 3, "first"),
                "2つのハンドルを同時に回しても、水門が開きません");
        int[] count = FloodgateService.count(server, def);
        check(helper, count[0] == 1 && count[1] == 3 && !FloodgateService.allOpen(server, def), "開いた水門の数が違います："
                + count[0] + "/" + count[1]);
        // 封印：水門がそろわないうちは開かず、知らせは「水門」
        PlayerData data = PlayerDataCapability.get(a).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
        for (FloorDef.Guardian guardian : TowerService.namedGuardians(def)) {
            data.getStory().add(TowerService.guardianFlag(3, guardian.id()));
        }
        net.minecraft.network.chat.Component locked = TowerService.sealLockedMessage(a, def);
        check(helper, !TowerService.pillarsComplete(a, 3) && locked != null
                && locked.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals("message.amemaru.seal.floodgates"), "水門がそろわないのに封印が開くか、知らせが違います");
        for (FloorFeatures.Floodgate gate : def.features().floodgates()) {
            FloodgateService.open(server, def, gate, null);
        }
        check(helper, FloodgateService.allOpen(server, def) && !FloodgateService.isOpen(server, 3, FloorFeatures.DRAIN),
                "水門を3組とも開けられないか、排水が開いたままになりました");
        check(helper, TowerService.pillarsComplete(a, 3), "水門を3組とも開き、名のある魔物を3体とも倒したのに、封印が開きません");
        // 保存して読み直しても同じ
        FloodgateService.Data copy = FloodgateService.Data.load(FloodgateService.Data.get(server).save(new CompoundTag()));
        check(helper, copy.isOpen(3, "first") && copy.isOpen(3, "moat"), "保存して読み直すと、開いた水門が閉じます");
        // 日が変わると閉じる
        FloodgateService.onDayChanged(server);
        check(helper, !FloodgateService.isOpen(server, 3, "first") && !TowerService.pillarsComplete(a, 3), "日が変わっても、水門が閉じません");
        // 排水のハンドル：ボスと戦っていないときは回らない
        check(helper, !FloodgateService.turn(a, 3, FloorFeatures.DRAIN, 0, pos) & !FloodgateService.turn(b, 3, FloorFeatures.DRAIN, 1, pos),
                "ボスと戦っていないのに、排水が始まりました");
        for (FloorDef.Guardian guardian : TowerService.namedGuardians(def)) {
            data.getStory().remove(TowerService.guardianFlag(3, guardian.id()));
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 敵の守り

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void waterwayEnemyDefenses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        // 掃除機兵：前（南を向いている）から打つと半分、後ろからはそのまま
        Soujikihei bot = ModEntities.SOUJIKIHEI.get().create(level);
        check(helper, bot != null, "水路掃除機兵を作れません");
        bot.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        bot.setYRot(0.0F);
        level.addFreshEntity(bot);
        FakePlayer front = fake(helper, "armor_front");
        front.moveTo(at.x, at.y, at.z + 3.0, 180.0F, 0.0F);
        FakePlayer back = fake(helper, "armor_back");
        back.moveTo(at.x, at.y, at.z - 3.0, 0.0F, 0.0F);
        check(helper, Math.abs(bot.damageTakenMultiplier(front) - 0.5) < 1.0E-6, "掃除機兵の正面の装甲が効いていません：" + bot.damageTakenMultiplier(front));
        check(helper, Math.abs(bot.damageTakenMultiplier(back) - 1.0) < 1.0E-6, "掃除機兵の背中まで硬くなっています");
        bot.discard();
        // 番人：水の鎧（1/5）・むき出しの炉心（2倍）。水の外套は、水が引くと隠れる
        Hydram hydram = ModEntities.HYDRAM.get().create(level);
        check(helper, hydram != null, "大水門の番人を作れません");
        check(helper, Math.abs(hydram.damageTakenMultiplier(null) - 0.2) < 1.0E-6 && Boolean.TRUE.equals(hydram.boneToggles().get("mantle")),
                "水が満ちている番人の守りが 1/5 ではないか、水の外套が見えていません");
        hydram.testDrained(true);
        check(helper, Math.abs(hydram.damageTakenMultiplier(null) - 2.0) < 1.0E-6 && Boolean.FALSE.equals(hydram.boneToggles().get("mantle")),
                "炉心がむき出しなのに、2倍にならないか、水の外套が残っています");
        hydram.discard();
        // ナマズ：水の中では半分。陸ではそのまま
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.WATER.defaultBlockState());
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.WATER.defaultBlockState());
        KibaNamazu fish = ModEntities.KIBA_NAMAZU.get().create(level);
        check(helper, fish != null, "牙ナマズを作れません");
        fish.moveTo(at.x, at.y + 0.1, at.z, 0.0F, 0.0F);
        level.addFreshEntity(fish);
        helper.runAfterDelay(3, () -> {
            check(helper, fish.isInWater() && Math.abs(fish.damageTakenMultiplier(null) - 0.5) < 1.0E-6,
                    "水の中の牙ナマズの守りが半分になっていません");
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR.defaultBlockState());
            helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                check(helper, !fish.isInWater() && Math.abs(fish.damageTakenMultiplier(null) - 1.0) < 1.0E-6,
                        "陸の牙ナマズまで硬くなっています");
                check(helper, fish.canBreatheUnderwater(), "牙ナマズが水の中で息ができません");
                fish.discard();
                helper.succeed();
            });
        });
    }

    // ---------------------------------------------------------------- 分かれる・呼ぶ

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void slimeSplitsAndRatsCall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        YodomiSlime slime = ModEntities.YODOMI_SLIME.get().create(level);
        check(helper, slime != null, "澱みスライムを作れません");
        slime.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(slime);
        SuimonNezumi boss = ModEntities.SUIMON_NEZUMI.get().create(level);
        check(helper, boss != null, "水門ネズミを作れません");
        boss.setVariant(AmemaruMod.id("suimon_nezumi_boss"));
        boss.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        helper.runAfterDelay(2, () -> {
            slime.die(level.damageSources().generic());
            boss.setEnemyHp(boss.getEnemyMaxHp() * 0.6);
            boss.testSummon();
            helper.runAfterDelay(2, () -> {
                List<YodomiSlime> small = level.getEntitiesOfClass(YodomiSlime.class, slime.getBoundingBox().inflate(4.0),
                        s -> s != slime && YodomiSlime.SMALL.equals(s.variantId()));
                check(helper, small.size() == 2, "澱みスライムが2匹に分かれません：" + small.size());
                List<SuimonNezumi> pups = level.getEntitiesOfClass(SuimonNezumi.class, boss.getBoundingBox().inflate(6.0), r -> r != boss);
                check(helper, boss.summonStage() == 1 && pups.size() == 3, "ネズミの親分が、HP60%で子分を3匹呼びません："
                        + boss.summonStage() + "・" + pups.size());
                for (YodomiSlime s : small) {
                    s.discard();
                }
                for (SuimonNezumi r : pups) {
                    r.discard();
                }
                slime.discard();
                boss.discard();
                helper.succeed();
            });
        });
    }

    // ---------------------------------------------------------------- エルネスト

    @GameTest(template = EMPTY)
    public static void ernestoComesWithTheRecord(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StoryState state = StoryState.get(server);
        boolean had = state.has(ErnestoArrival.ARRIVED);
        state.remove(ErnestoArrival.ARRIVED);
        FakePlayer player = fake(helper, "ernesto_record");
        PlayerData data = PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
        data.getGuild().forgetRecord(ErnestoArrival.RECORD);
        check(helper, !ErnestoArrival.check(player) && !state.has(ErnestoArrival.ARRIVED), "3階の記録を持っていないのに、エルネストが来ました");
        check(helper, !NpcDefs.get(AmemaruMod.id("ernesto")).appears().testServer(server), "記録を持ち帰る前から、エルネストがいます");
        data.getGuild().findRecord(ErnestoArrival.RECORD, 3);
        check(helper, ErnestoArrival.check(player) && state.has(ErnestoArrival.ARRIVED), "3階の記録を持ち帰っても、エルネストが来ません");
        check(helper, NpcDefs.get(AmemaruMod.id("ernesto")).appears().testServer(server), "エルネストが来たのに、現れる条件に合いません");
        check(helper, !ErnestoArrival.check(player), "エルネストが二度来ました");
        data.getGuild().forgetRecord(ErnestoArrival.RECORD);
        if (!had) {
            state.remove(ErnestoArrival.ARRIVED);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 定義

    @GameTest(template = EMPTY)
    public static void waterwayDefinitions(GameTestHelper helper) {
        for (String id : List.of("suimon_nezumi", "yodomi_slime", "yodomi_slime_small", "soujikihei", "kiba_namazu", "hydram")) {
            EnemyDef def = EnemyDefs.get(AmemaruMod.id(id));
            check(helper, def != null && def.floor() == 3, id + " が3階の敵として定義されていません");
            check(helper, def.lootTable() != null, id + " にドロップ表がありません");
        }
        for (String[] pair : new String[][]{{"soujikihei_overseer", "soujikihei"}, {"kiba_namazu_lord", "kiba_namazu"},
                {"suimon_nezumi_boss", "suimon_nezumi"}}) {
            EnemyDef def = EnemyDefs.get(AmemaruMod.id(pair[0]));
            check(helper, def != null && AmemaruMod.id(pair[1]).equals(def.entity()) && def.behavior("scale", 1.0) > 1.0,
                    pair[0] + " が " + pair[1] + " の見た目を借りる大きな個体ではありません");
        }
        EnemyDef small = EnemyDefs.get(YodomiSlime.SMALL);
        check(helper, small != null && small.behavior("scale", 1.0) < 1.0 && small.behavior("split", 0.0) == 0.0,
                "小さな澱みスライムが、小さくないか、また分かれます");
        EnemyDef hydram = EnemyDefs.get(AmemaruMod.id("hydram"));
        for (String skill : List.of("cannon", "sweep", "whirl", "call", "drain", "share")) {
            check(helper, hydram.skills().containsKey(skill), "大水門の番人に技 " + skill + " がありません");
        }
        boolean shield = hydram.rare().stream().anyMatch(r -> r.item().equals(AmemaruMod.id("floodgate_key_shield")) && r.chance() == 6.0);
        check(helper, shield && hydram.pity(), "大水門の番人のレアに、水門の鍵盾（6%）がありません");
        for (String item : List.of("gnawed_cog", "murky_gel", "waterway_bronze", "catfish_whisker", "flow_crystal")) {
            check(helper, ModItems.MANIFEST.get(item) != null, "3階の素材 " + item + " がありません");
        }
        check(helper, ModItems.FLOODGATE_VALVE.get() != null, "水門のハンドルの品がありません");
        for (String id : List.of("floor3/enter", "floor3/first_kill", "floor3/white_flow")) {
            check(helper, StoryEventManager.get(AmemaruMod.id(id)) != null, "3階の物語 " + id + " がありません");
        }
        StoryEventDef white = StoryEventManager.get(AmemaruMod.id("floor3/white_flow"));
        check(helper, white.triggerType().equals("floor_zone") && white.triggerZone().equals("white_flow"), "白い流れの物語が、物語の場所で流れません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 3階からの職業ごとの装備（流紋シリーズ）

    /** 3階の素材（流紋シリーズの材料には、どれかが入る）。 */
    private static final Set<String> FLOOR3_MATERIALS = Set.of("gnawed_cog", "murky_gel", "waterway_bronze", "catfish_whisker",
            "flow_crystal");

    /**
     * 11月23日の決定：3階からは職業ごとの固有防具と武器。6職業それぞれに、その職業だけが着られる頭・胴・脚・足（見た目の
     * シリーズも職業ごと）と武器があり、工房で3階の素材から作れる。古代石より強く、3階のレアの盾よりは弱い。
     */
    @GameTest(template = EMPTY)
    public static void ryumonJobGear(GameTestHelper helper) {
        EquipmentDefs.Snapshot gear = EquipmentDefs.server();
        Map<ResourceLocation, WorkshopRecipe> recipes = new HashMap<>();
        for (WorkshopRecipe recipe : helper.getLevel().getServer().getRecipeManager().getAllRecipesFor(ModRecipes.WORKSHOP_TYPE.get())) {
            recipes.put(ForgeRegistries.ITEMS.getKey(recipe.result().getItem()), recipe);
        }
        String[] jobs = {"knight", "wizard", "healer", "archer", "rogue", "monk"};
        for (String job : jobs) {
            ResourceLocation jobId = AmemaruMod.id(job);
            Set<GearSlot> slots = new HashSet<>();
            for (RegistryObject<Item> item : ModItems.MANIFEST.category(ItemManifest.Category.ARMOR)) {
                if (!(item.get() instanceof AmemaruArmorItem armor) || !armor.series().equals("ryumon_" + job)) {
                    continue;
                }
                EquipmentDef def = gear.get(item.get());
                check(helper, def != null, item.getId() + " に装備の定義がありません");
                check(helper, def.jobs().equals(List.of(jobId)), item.getId() + " が " + job + " だけの防具ではありません：" + def.jobs());
                // 11月24日：レベルの上限を上げ、必要レベルも合わせた（Lv7 → Lv15）
                check(helper, def.requiredLevel() == 15 && def.series().startsWith("流紋シリーズ"),
                        item.getId() + " が Lv15 の流紋シリーズではありません：Lv" + def.requiredLevel());
                check(helper, slots.add(def.slot()), item.getId() + " と同じ場所の防具が、ほかにもあります");
                checkRecipe(helper, recipes, item.getId(), WorkshopType.ARMOR);
            }
            check(helper, slots.equals(Set.of(GearSlot.HEAD, GearSlot.CHEST, GearSlot.LEGS, GearSlot.FEET)),
                    job + " の流紋シリーズの防具が、頭・胴・脚・足の4つそろっていません：" + slots);
        }
        // 武器：その職業だけが持て、その職業の武器のタグに入り、古代石の武器より強い
        String[][] weapons = {{"knight", "ryumon_sword", "ancient_stone_sword"}, {"knight", "ryumon_greatsword", "ancient_stone_greatsword"},
                {"wizard", "ryumon_staff", "ancient_stone_staff"}, {"healer", "ryumon_bell_staff", "ancient_stone_bell_staff"},
                {"archer", "ryumon_bow", "ancient_stone_bow"}, {"rogue", "ryumon_daggers", "ancient_stone_daggers"},
                {"monk", "ryumon_gauntlets", "ancient_stone_gauntlets"}};
        for (String[] w : weapons) {
            ResourceLocation id = AmemaruMod.id(w[1]);
            EquipmentDef def = gear.get(id);
            EquipmentDef old = gear.get(AmemaruMod.id(w[2]));
            check(helper, def != null && def.slot() == GearSlot.WEAPON && def.jobs().equals(List.of(AmemaruMod.id(w[0]))),
                    w[1] + " が " + w[0] + " だけの武器ではありません");
            JobDef job = SkillRegistry.get().job(AmemaruMod.id(w[0]));
            check(helper, job != null && job.weapons() != null && new ItemStack(ForgeRegistries.ITEMS.getValue(id)).is(job.weapons()),
                    w[1] + " が " + w[0] + " の武器のタグに入っていません");
            StatType main = old.stat(StatType.MAGIC) > old.stat(StatType.ATTACK) ? StatType.MAGIC : StatType.ATTACK;
            check(helper, def.stat(main) > old.stat(main), w[1] + " が " + w[2] + " より弱いです");
            checkRecipe(helper, recipes, id, WorkshopType.WEAPON);
        }
        EquipmentDef shield = gear.get(AmemaruMod.id("ryumon_shield"));
        EquipmentDef rare = gear.get(AmemaruMod.id("floodgate_key_shield"));
        check(helper, shield != null && shield.slot() == GearSlot.OFFHAND && shield.jobs().equals(List.of(AmemaruMod.id("knight")))
                        && shield.stat(StatType.DEFENSE) > gear.get(AmemaruMod.id("ancient_stone_shield")).stat(StatType.DEFENSE)
                        && shield.stat(StatType.DEFENSE) < rare.stat(StatType.DEFENSE),
                "流紋の盾が、古代石の盾より強く水門の鍵盾より弱いナイトの盾になっていません");
        checkRecipe(helper, recipes, AmemaruMod.id("ryumon_shield"), WorkshopType.ARMOR);
        // 着られるかどうか：ナイトの兜は、Lv15のナイトだけ
        EquipmentDef helm = gear.get(AmemaruMod.id("ryumon_knight_helm"));
        PlayerData data = new PlayerData();
        data.setLevel(15);
        data.setCombatJob(AmemaruMod.id("wizard"));
        check(helper, !GearService.usable(data, helm), "ウィザードが、ナイトの流紋の兜を着られます");
        data.setCombatJob(AmemaruMod.id("knight"));
        check(helper, GearService.usable(data, helm), "Lv15のナイトが、流紋の兜を着られません");
        data.setLevel(14);
        check(helper, !GearService.usable(data, helm), "Lv14で流紋の兜を着られます");
        helper.succeed();
    }

    private static void checkRecipe(GameTestHelper helper, Map<ResourceLocation, WorkshopRecipe> recipes, ResourceLocation item,
                                    WorkshopType bench) {
        WorkshopRecipe recipe = recipes.get(item);
        check(helper, recipe != null && recipe.workshop() == bench, item + " を作るレシピが " + bench.id() + " の工房にありません");
        // 第1章の熟練度の上限は3（production_rules の chapter_caps）
        check(helper, recipe.mastery() >= 2 && recipe.mastery() <= 3, item + " のレシピの熟練度が2〜3ではありません：" + recipe.mastery());
        check(helper, recipe.ingredients().stream().anyMatch(cost -> cost.item().getNamespace().equals(AmemaruMod.MOD_ID)
                && FLOOR3_MATERIALS.contains(cost.item().getPath())), item + " の材料に3階の素材がありません");
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
