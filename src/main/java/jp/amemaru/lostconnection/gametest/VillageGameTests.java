package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.enemy.boss.Ordo;
import jp.amemaru.lostconnection.enemy.village.FrostWolf;
import jp.amemaru.lostconnection.enemy.village.StatueEcho;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearService;
import jp.amemaru.lostconnection.equipment.GearSlot;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import jp.amemaru.lostconnection.guild.RecordDefs;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.npc.NpcMemoryService;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.story.StoryState;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.TowerService;
import jp.amemaru.lostconnection.tower.village.VillageService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 11月25日（6階「凍結した村」・第2章のはじめ）の自動テスト。
 *
 * <ul>
 *   <li>6階の形：熱の炉3つ・記憶の氷像4つ・オルドの結晶の台3つ・封印は「氷像＋名のある魔物」・オーロラの空と雪</li>
 *   <li>熱の結晶：運ぶ人は遅くなり、まわりの敵の敵視がたまる。冷えると消え、村の外では砕ける</li>
 *   <li>寒さに強い：村長の毛皮の一式・霜熊の氷の棘で、凍えが短くなる</li>
 *   <li>オルド：記憶の結晶を受け取るとよろめき（受けるダメージ増）、3つ目で思い出して消える</li>
 *   <li>霜狼の群れ長：HPが半分を切ると仲間を呼ぶ。氷像の残響：あの日の村人の姿</li>
 *   <li>街の人：ロストが及んでいる間、バルド・ジゼル・ペトラは忘れ、思い出してもらうと戻る</li>
 * </ul>
 * <p>熱の結晶で氷像を溶かす道（右クリック → そばで待つ → 溶ける → 封印が開く）は、6階を本当に建てて
 * {@link FloorRealPlayGameTests} で確かめる。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;

    private VillageGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void floor6Layout(GameTestHelper helper) {
        FloorDef def = FloorDefs.byNumber(6);
        check(helper, def != null && def.bossRoom() != null, "6階の定義がありません");
        check(helper, def.chapter() == 2, "6階が第2章になっていません：" + def.chapter());
        for (int n = 1; n <= 5; n++) {
            FloorDef other = FloorDefs.byNumber(n);
            check(helper, other != null && !def.contains(other.originVec()) && !other.contains(def.originVec()), n + "階と6階が重なっています");
        }
        check(helper, AmemaruMod.id("ordo").equals(def.bossRoom().boss()), "6階のボスがオルドではありません：" + def.bossRoom().boss());
        check(helper, def.bossRoom().next() != null, "6階のボスの部屋に、次の階への円がありません");
        FloorFeatures fx = def.features();
        check(helper, fx.hearths().size() == 3, "熱の炉が3つではありません：" + fx.hearths().size());
        check(helper, fx.memoryStatues().size() == 4, "記憶の氷像が4つではありません：" + fx.memoryStatues().size());
        for (FloorFeatures.MemoryStatue statue : fx.memoryStatues()) {
            check(helper, jp.amemaru.lostconnection.story.StoryEventManager.get(statue.event()) != null,
                    "記憶の氷像「" + statue.name() + "」の場面（" + statue.event() + "）がありません");
        }
        check(helper, fx.requires(FloorFeatures.SEAL_STATUES) && fx.requires(FloorFeatures.SEAL_GUARDIANS),
                "6階の封印の条件が「記憶の氷像＋名のある魔物」ではありません");
        check(helper, TowerService.namedGuardians(def).size() == 2, "名のある魔物（霜狼の群れ長・霜熊の主）が2体ではありません");
        check(helper, fx.bossMarks(Ordo.SPOTS).size() == Ordo.CRYSTALS, "オルドの記憶の結晶の台が " + Ordo.CRYSTALS + " つではありません");
        check(helper, fx.environment() != null && "aurora".equals(fx.environment().sky()) && "snow".equals(fx.environment().weather()),
                "6階の空がオーロラ・天気が雪ではありません");
        check(helper, fx.waypoints().size() == 4, "中継の灯が4つではありません：" + fx.waypoints().size());
        check(helper, def.extras().chests().size() >= 15, "6階の宝箱が少なすぎます：" + def.extras().chests().size());
        check(helper, fx.storyZones().stream().anyMatch(z -> z.id().equals("frozen_market")), "凍った市場の物語の場所がありません");
        check(helper, RecordDefs.get(AmemaruMod.id("f06_child_diary")) != null && def.tablet() != null,
                "古代記録「村の子どもの日記」か、その石板がありません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 熱の結晶

    @GameTest(template = EMPTY, batch = "village_carry", timeoutTicks = 60)
    public static void heatCrystalSlowsAndDrawsEnemies(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel tower = FloorDefs.towerLevel(server);
        check(helper, tower != null, "塔のディメンションがありません");
        FloorDef def = FloorDefs.byNumber(6);
        check(helper, def != null && def.entrance() != null, "6階の入口がありません");
        BlockPos entry = BlockPos.containing(def.world(def.entrance().pos()));
        tower.getChunk(entry.getX() >> 4, entry.getZ() >> 4);
        FakePlayer player = FakePlayerFactory.get(tower, new GameProfile(uuid("carry"), "amt_carry"));
        player.getInventory().clearContent();
        player.setPos(entry.getX() + 0.5, entry.getY() + 0.1, entry.getZ() + 0.5);
        long now = tower.getGameTime();

        // 持っていなければ、何も起きない
        VillageService.carry(player, tower, now, true);
        check(helper, !VillageService.slowed(player), "結晶を持っていないのに、足が遅くなっています");

        // 熱の結晶を持つと：遅くなり、走れない。そばの敵は運ぶ人をねらう
        FrostWolf wolf = ModEntities.FROST_WOLF.get().create(tower);
        check(helper, wolf != null, "霜狼を作れません");
        wolf.moveTo(entry.getX() + 3.5, entry.getY() + 0.1, entry.getZ() + 0.5, 0.0F, 0.0F);
        tower.addFreshEntity(wolf);
        player.getInventory().add(VillageService.heatCrystal(now));
        check(helper, VillageService.holds(player, false) && VillageService.carrying(player), "熱の結晶を持っていることになりません");
        player.setSprinting(true);
        VillageService.carry(player, tower, now, true);
        check(helper, VillageService.slowed(player), "熱の結晶を運んでいるのに、足が遅くなりません");
        check(helper, !player.isSprinting(), "熱の結晶を運んでいるのに、走れます");
        check(helper, wolf.threatOf(player.getUUID()) >= VillageService.CARRY_THREAT - EPSILON,
                "熱の結晶を運ぶ人に、まわりの敵の敵視がたまりません：" + wolf.threatOf(player.getUUID()));

        // 冷えると消える（もらってから CRYSTAL_SECONDS 秒）
        VillageService.carry(player, tower, now + VillageService.CRYSTAL_SECONDS * 20L + 1, false);
        check(helper, !VillageService.holds(player, false), "冷えた熱の結晶が消えません");
        VillageService.carry(player, tower, now, false);
        check(helper, !VillageService.slowed(player), "結晶がなくなったのに、足が遅いままです");

        // 村の階の外へ持ち出すと砕ける（記憶の結晶も）
        player.getInventory().add(VillageService.heatCrystal(now));
        player.getInventory().add(new ItemStack(VillageService.memoryItem()));
        FakePlayer outside = FakePlayerFactory.get(helper.getLevel(), new GameProfile(uuid("carry_out"), "amt_carry_out"));
        outside.getInventory().clearContent();
        outside.getInventory().add(VillageService.heatCrystal(now));
        outside.getInventory().add(new ItemStack(VillageService.memoryItem()));
        VillageService.carry(outside, tower, now, false);
        check(helper, !VillageService.carrying(outside), "村の階の外へ持ち出した結晶が砕けません");
        VillageService.carry(player, tower, now, false);
        check(helper, VillageService.holds(player, false) && VillageService.holds(player, true), "村の中なのに、結晶が砕けました");
        player.getInventory().clearContent();
        VillageService.carry(player, tower, now, false);
        wolf.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "village_statues")
    public static void statuesOpenTheSealAndFreezeAgain(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FloorDef def = FloorDefs.byNumber(6);
        check(helper, def != null, "6階の定義がありません");
        FakePlayer player = fake(helper, "statues");
        PlayerData data = data(player);
        VillageService.onDayChanged(server);
        for (FloorDef.Guardian guardian : TowerService.namedGuardians(def)) {
            data.getStory().add(TowerService.guardianFlag(6, guardian.id()));
        }
        check(helper, VillageService.count(server, def)[0] == 0 && !VillageService.allThawed(server, def), "朝なのに、溶けた氷像があります");
        check(helper, !TowerService.pillarsComplete(player, 6), "氷像が凍ったままなのに、封印の条件がそろいました");
        List<FloorFeatures.MemoryStatue> statues = def.features().memoryStatues();
        for (int i = 0; i < statues.size(); i++) {
            VillageService.thaw(server, def, statues.get(i), null);
            check(helper, VillageService.thawed(server, 6, statues.get(i).id()) && (VillageService.statueBits(server, def) & (1 << i)) != 0,
                    "氷像「" + statues.get(i).name() + "」が溶けたことになりません");
            check(helper, i == statues.size() - 1 || !TowerService.pillarsComplete(player, 6), "氷像がまだ残っているのに、封印の条件がそろいました");
        }
        check(helper, VillageService.allThawed(server, def) && TowerService.pillarsComplete(player, 6),
                "氷像をすべて溶かし、名のある魔物も倒したのに、封印の条件がそろいません");
        for (FloorDef.Guardian guardian : TowerService.namedGuardians(def)) {
            data.getStory().remove(TowerService.guardianFlag(6, guardian.id()));
        }
        check(helper, !TowerService.pillarsComplete(player, 6), "名のある魔物を倒していないのに、封印の条件がそろいました");
        VillageService.onDayChanged(server);
        check(helper, !VillageService.allThawed(server, def), "進行日が変わっても、氷像がまた凍りません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 寒さ

    @GameTest(template = EMPTY)
    public static void coldResistShortensChill(GameTestHelper helper) {
        FakePlayer player = fake(helper, "cold");
        PlayerData data = data(player);
        LevelService.setLevel(player, data, 30);
        JobService.setJobForced(player, data, SkillRegistry.get().job(AmemaruMod.id("knight")));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.removeAllEffects();
        LevelService.recompute(player, data);
        check(helper, PlayerColdService.resist(player) < EPSILON, "何も着ていないのに、寒さに強くなっています");
        PlayerColdService.chill(player, 2, 200);
        int bare = player.getEffect(jp.amemaru.lostconnection.core.registry.ModEffects.FROST.get()).getDuration();
        check(helper, PlayerColdService.chillOf(player) == 2 && bare >= 190, "凍えが2段・10秒かかりません");
        PlayerColdService.chill(player, 5, 20);
        check(helper, PlayerColdService.chillOf(player) == PlayerColdService.MAX_CHILL, "凍えが " + PlayerColdService.MAX_CHILL + " 段で止まりません");
        player.removeAllEffects();

        // 村長の毛皮：胴に「寒さに強い 20%」、4つそろえると一式の効果「寒さに強い 30%」
        String[] pieces = {"mayor_fur_hat", "mayor_fur_coat", "mayor_fur_leggings", "mayor_fur_boots"};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < pieces.length; i++) {
            player.setItemSlot(slots[i], rolled(pieces[i]));
            if (i == 2) {
                check(helper, GearService.setEffects(player, data).isEmpty(), "村長の毛皮を3つだけで、一式の効果が効いています");
            }
        }
        check(helper, GearService.seriesWorn(player, "mayor_fur") == 4, "村長の毛皮を4つ着ていることになっていません");
        double resist = PlayerColdService.resist(player);
        check(helper, Math.abs(resist - 50.0) < EPSILON, "村長の毛皮の一式で、寒さに強い 50%（胴20＋一式30）になりません：" + resist);
        PlayerColdService.chill(player, 1, 200);
        int warm = player.getEffect(jp.amemaru.lostconnection.core.registry.ModEffects.FROST.get()).getDuration();
        check(helper, warm <= 105 && warm >= 95, "寒さに強い 50% で、凍えの時間が半分になりません：" + warm);
        player.removeAllEffects();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- オルド

    @GameTest(template = EMPTY, timeoutTicks = 260)
    public static void ordoRemembersWithThreeCrystals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Ordo boss = ModEntities.ORDO.get().create(level);
        check(helper, boss != null, "オルドを作れません");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        boss.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        FakePlayer player = fake(helper, "ordo");
        player.getInventory().clearContent();
        helper.runAfterDelay(2, () -> {
            check(helper, boss.given() == 0 && !boss.crystalOut() && Math.abs(boss.damageTakenMultiplier(null) - 1.0) < EPSILON,
                    "はじめのオルドが、結晶を受け取ったことになっているか、受けるダメージが変わっています");
            boss.testSpawnCrystal();
            check(helper, boss.crystalOut(), "記憶の結晶が出ません");
            List<ItemEntity> crystals = level.getEntitiesOfClass(ItemEntity.class, boss.getBoundingBox().inflate(12.0),
                    e -> e.getItem().is(VillageService.memoryItem()));
            check(helper, crystals.size() == 1 && crystals.get(0).isNoGravity(), "記憶の結晶が、浮いた品として1つ出ません：" + crystals.size());
            crystals.forEach(e -> e.discard());
            boss.testReceive(player);
            EnemyDef def = EnemyDefs.get(AmemaruMod.id("ordo"));
            double broken = def == null ? 1.6 : def.skill("memory", 0.0, 0.0, 0.0, 3.5).param("broken", 1.6);
            check(helper, boss.given() == 1 && !boss.crystalOut() && Math.abs(boss.damageTakenMultiplier(null) - broken) < EPSILON,
                    "記憶の結晶を受け取っても、よろめきません（受けるダメージ ×" + broken + "）");
            check(helper, !VillageService.holds(player, true), "渡した記憶の結晶が、手もとに残っています");
            boss.testReceive(player);
            check(helper, boss.given() == 2 && !boss.remembering(), "2つ目で、もう思い出しています");
            boss.testReceive(player);
            check(helper, boss.given() == Ordo.CRYSTALS && boss.remembering() && boss.damageTakenMultiplier(null) < EPSILON,
                    "3つ目の結晶で、オルドが思い出しません");
        });
        helper.runAfterDelay(140, () -> {
            check(helper, !boss.isAlive(), "思い出したオルドが、ひざをついたあと消えません");
            boss.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void ordoDropsTheMayorFurSet(GameTestHelper helper) {
        EnemyDef boss = EnemyDefs.get(AmemaruMod.id("ordo"));
        check(helper, boss != null, "オルドの定義がありません");
        java.util.Set<GearSlot> slots = java.util.EnumSet.noneOf(GearSlot.class);
        for (EnemyDef.RareDrop rare : boss.rare()) {
            EquipmentDef gear = EquipmentDefs.server().get(rare.item());
            if (gear == null || !rare.item().getPath().startsWith("mayor_fur_")) {
                continue;
            }
            slots.add(gear.slot());
            check(helper, gear.setEffects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.COLD_RESIST),
                    rare.item() + " に、一式の効果（寒さに強い）がありません");
            check(helper, ForgeRegistries.ITEMS.getValue(rare.item()) instanceof jp.amemaru.lostconnection.item.AmemaruArmorItem piece
                    && piece.series().equals("mayor_fur"), rare.item() + " が、村長の毛皮の防具として登録されていません");
        }
        check(helper, slots.size() == 4, "村長の毛皮が一式（頭・胴・脚・足）になっていません：" + slots);
        for (String[] pair : new String[][]{{"frost_wolf_leader", "white_fang_charm"}, {"frost_bear_elder", "bear_frost_charm"}}) {
            EnemyDef named = EnemyDefs.get(AmemaruMod.id(pair[0]));
            check(helper, named != null && named.rare().stream().anyMatch(r -> r.item().equals(AmemaruMod.id(pair[1]))),
                    pair[0] + " が " + pair[1] + " を落としません");
            Item item = ForgeRegistries.ITEMS.getValue(AmemaruMod.id(pair[1]));
            AccessoryDef acc = item == null ? null : AccessoryDefs.get(item);
            check(helper, acc != null && acc.rarity() == Rarity.RARE, pair[1] + " が、レアのアクセサリーになっていません");
        }
        AccessoryDef thorn = AccessoryDefs.get(ForgeRegistries.ITEMS.getValue(AmemaruMod.id("bear_frost_charm")));
        check(helper, thorn.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.COLD_RESIST && Math.abs(e.percent() - 25.0) < EPSILON),
                "霜熊の氷の棘が「寒さに強い 25%」ではありません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 雑魚

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void packLeaderCallsAndEchoesWearTheVillage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        FrostWolf leader = ModEntities.FROST_WOLF.get().create(level);
        check(helper, leader != null, "霜狼を作れません");
        leader.setVariant(AmemaruMod.id("frost_wolf_leader"));
        leader.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(leader);
        StatueEcho echo = ModEntities.STATUE_ECHO.get().create(level);
        check(helper, echo != null, "氷像の残響を作れません");
        echo.moveTo(at.x + 1.0, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(echo);
        helper.runAfterDelay(2, () -> {
            check(helper, java.util.Arrays.asList(StatueEcho.SKINS).contains(echo.skin()), "氷像の残響の姿が、村人の姿ではありません：" + echo.skin());
            leader.onDamagedBy(null, 1.0);
            check(helper, !leader.testCalled(), "HPが半分より多いのに、群れ長が仲間を呼びました");
            leader.setEnemyHp(leader.getEnemyMaxHp() * 0.4);
            leader.onDamagedBy(null, 1.0);
            helper.runAfterDelay(2, () -> {
                List<FrostWolf> pack = level.getEntitiesOfClass(FrostWolf.class, leader.getBoundingBox().inflate(8.0), w -> w != leader);
                check(helper, leader.testCalled() && pack.size() == 3, "霜狼の群れ長が、HP40%で仲間を3匹呼びません：" + pack.size());
                pack.forEach(w -> w.discard());
                leader.discard();
                echo.discard();
                helper.succeed();
            });
        });
    }

    // ---------------------------------------------------------------- 街の人の記憶（街ぜんたいの印を変えるので、ひとりで回す）

    @GameTest(template = EMPTY, batch = "village_memory")
    public static void townForgetsUntilReminded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StoryState state = StoryState.get(server);
        boolean hadLost = state.has(NpcMemoryService.LOST_IN_TOWN);
        boolean hadQueen = state.has(NpcMemoryService.QUEEN_DOWN);
        FakePlayer player = fake(helper, "memory");
        PlayerData data = data(player);
        ResourceLocation bard = AmemaruMod.id("bard");
        ResourceLocation lilia = AmemaruMod.id("lilia");
        try {
            state.remove(NpcMemoryService.LOST_IN_TOWN);
            state.remove(NpcMemoryService.QUEEN_DOWN);
            data.getStory().remove(NpcMemoryService.memoryFlag(bard));
            check(helper, !NpcMemoryService.townLost(server) && NpcMemoryService.remembers(player, bard), "6階の前から、街の人が忘れています");
            state.add(NpcMemoryService.LOST_IN_TOWN);
            check(helper, NpcMemoryService.townLost(server) && !NpcMemoryService.remembers(player, bard),
                    "6階に人が来たのに、バルドがプレイヤーを忘れません");
            check(helper, NpcMemoryService.remembers(player, lilia), "リリアまで忘れています（忘れるのはバルド・ジゼル・ペトラ）");
            for (ResourceLocation npc : NpcMemoryService.FORGET_DAY6) {
                check(helper, NpcMemoryService.forgets(server, npc), npc + " が忘れません");
            }
            data.getStory().add(NpcMemoryService.memoryFlag(bard));
            check(helper, NpcMemoryService.remembers(player, bard), "思い出してもらったのに、バルドがまだ忘れています");
            data.getStory().remove(NpcMemoryService.memoryFlag(bard));
            state.add(NpcMemoryService.QUEEN_DOWN);
            check(helper, !NpcMemoryService.townLost(server) && NpcMemoryService.remembers(player, bard), "10階の女王を倒しても、街の記憶が戻りません");
            jp.amemaru.lostconnection.fishing.FishDef fish = jp.amemaru.lostconnection.fishing.FishDefs.all().values().iterator().next();
            state.remove(NpcMemoryService.QUEEN_DOWN);
            check(helper, NpcMemoryService.fishForgotten(server, fish) || NpcMemoryService.Data.get(server).remembered(fish.id()),
                    "ロストが及んでいるのに、ポッカ爺が魚の名前を覚えています");
        } finally {
            if (!hadLost) {
                state.remove(NpcMemoryService.LOST_IN_TOWN);
            }
            if (hadQueen) {
                state.add(NpcMemoryService.QUEEN_DOWN);
            } else {
                state.remove(NpcMemoryService.QUEEN_DOWN);
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    private static ItemStack rolled(String path) {
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        EquipmentDef def = snapshot.get(AmemaruMod.id(path));
        if (def == null) {
            throw new GameTestAssertException("装備の定義が見つかりません：" + path);
        }
        ItemStack stack = new ItemStack(ModItems.MANIFEST.get(path).get());
        GearData.roll(stack, def, snapshot.rules(), RandomSource.create(1L), Rarity.EPIC, false, null);
        GearData.setAllRatios(stack, def, 1.0);
        return stack;
    }

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(("amemaru-gametest-village:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "amv_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(uuid(name), profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static PlayerData data(FakePlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}
