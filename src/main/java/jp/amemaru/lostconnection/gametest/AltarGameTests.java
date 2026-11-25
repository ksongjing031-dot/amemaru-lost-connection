package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.enemy.altar.RepairDrone;
import jp.amemaru.lostconnection.enemy.altar.Sentinel;
import jp.amemaru.lostconnection.enemy.boss.AncientGuardian;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearSlot;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import jp.amemaru.lostconnection.guild.RecordDefs;
import jp.amemaru.lostconnection.loot.DropService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.TowerService;
import jp.amemaru.lostconnection.tower.altar.AltarService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.Map;
import java.util.UUID;

/**
 * 11月24日（5階「古代守護者の祭壇」）の自動テスト。
 *
 * <ul>
 *   <li>5階の形：4階より広い。接続の柱4本（監視の大眼・防衛隊長・ドローンのぜんまい3つ・仕掛け石の祠）・奈落・中継の灯4つ・
 *       祭壇の結び台4つと中央と結界の柱4本・次の階への円</li>
 *   <li>警戒：上がり、3つまで</li>
 *   <li>防衛兵の盾は前だけ。修復ドローンは傷ついた仲間を直す</li>
 *   <li>古代守護者：結び台の人数（戦う人が少なければその人数）・接続判定のつながり・第七の門鍵を渡す人</li>
 *   <li>守護者シリーズは職業ごと（自分の職業の品だけを引く）。第七の門鍵はユニークで、取引できない</li>
 * </ul>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AltarGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final String[] JOBS = {"knight", "wizard", "healer", "archer", "rogue", "monk"};

    private AltarGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void floor5Layout(GameTestHelper helper) {
        FloorDef def = FloorDefs.byNumber(5);
        check(helper, def != null && def.bossRoom() != null, "5階の定義がありません");
        FloorDef four = FloorDefs.byNumber(4);
        check(helper, four != null && def.sizeX() > four.sizeX() && def.sizeZ() > four.sizeZ(),
                "5階が4階より広くありません（" + def.sizeX() + "×" + def.sizeZ() + "）");
        for (int n = 1; n <= 4; n++) {
            FloorDef other = FloorDefs.byNumber(n);
            check(helper, other != null && !def.contains(other.originVec()) && !other.contains(def.originVec()), n + "階と5階が重なっています");
        }
        FloorDef.BossRoomDef boss = def.bossRoom();
        check(helper, AmemaruMod.id("ancient_guardian").equals(boss.boss()), "5階のボスが古代守護者ではありません：" + boss.boss());
        check(helper, boss.scaling().hpMinPlayers() >= 2 && boss.next() != null, "5階のボスの人数補正か、次の階への円がありません");
        check(helper, def.pillars().size() == 4, "接続の柱が4本ではありません：" + def.pillars().size());
        check(helper, def.pillars().get(0).requires() != null && "great_eye".equals(def.pillars().get(0).requires().guardian())
                && def.pillars().get(1).requires() != null && "captain".equals(def.pillars().get(1).requires().guardian())
                && def.pillars().get(2).requires() != null && AmemaruMod.id("drone_spring").equals(def.pillars().get(2).requires().item()),
                "接続の柱の課題（監視の大眼・防衛隊長・ドローンのぜんまい）が違います");
        check(helper, def.extras().switches().size() == 1, "前庭の祠の仕掛け石がありません");
        FloorFeatures fx = def.features();
        check(helper, fx.abyssY() != FloorFeatures.NO_ABYSS, "5階の奈落（戻される高さ）がありません");
        check(helper, fx.bossMarks(AncientGuardian.DAISES).size() == 4 && fx.bossMarks(AncientGuardian.CENTER).size() == 1
                && fx.bossMarks(AncientGuardian.PILLARS).size() == 4, "祭壇の結び台・中央・結界の柱が足りません");
        check(helper, fx.waypoints().size() == 4 && fx.travelStone() != null, "中継の灯が4つ・道しるべの石がありません");
        // 5階の名のある魔物（監視の大眼・防衛隊長）は、どちらも接続の柱の守り手（封印は柱で開く。guardians の数には入らない）
        check(helper, def.extras().guardians().size() == 2 && def.extras().guardians().stream().allMatch(g -> g.pillar() >= 0)
                && TowerService.namedGuardians(def).isEmpty(), "名のある魔物が、2体とも接続の柱の守り手になっていません");
        check(helper, fx.storyZones().stream().anyMatch(z -> z.id().equals("lattice_sky")), "格子の空の物語の場所がありません");
        check(helper, def.extras().chests().size() >= 15, "5階の宝箱が少なすぎます：" + def.extras().chests().size());
        check(helper, RecordDefs.get(AmemaruMod.id("f05_guardian_core")) != null, "古代記録「守護者の記録核」がありません");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void watchersRaiseTheAlert(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = fake(helper, "alert");
        AltarService.clearAlert(5);
        check(helper, AltarService.alert(level, 5) == 0, "はじめの警戒が0ではありません");
        AltarService.raiseAlert(level, 5, player, 1);
        check(helper, AltarService.alert(level, 5) == 1, "見つかっても警戒が上がりません");
        AltarService.raiseAlert(level, 5, player, 2);
        AltarService.raiseAlert(level, 5, player, 2);
        check(helper, AltarService.alert(level, 5) == AltarService.MAX_ALERT, "警戒が " + AltarService.MAX_ALERT + " で止まりません");
        AltarService.clearAlert(5);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void shieldsAndRepairs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        Sentinel guard = ModEntities.SENTINEL.get().create(level);
        check(helper, guard != null, "防衛兵を作れません");
        guard.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        guard.setYRot(0.0F);
        level.addFreshEntity(guard);
        FakePlayer front = fake(helper, "shield_front");
        front.moveTo(at.x, at.y, at.z + 3.0, 180.0F, 0.0F);
        FakePlayer back = fake(helper, "shield_back");
        back.moveTo(at.x, at.y, at.z - 3.0, 0.0F, 0.0F);
        check(helper, Math.abs(guard.damageTakenMultiplier(front) - 0.25) < 1.0E-6, "防衛兵の盾が前で効いていません");
        check(helper, Math.abs(guard.damageTakenMultiplier(back) - 1.0) < 1.0E-6, "防衛兵の背中まで盾が効いています");
        RepairDrone drone = ModEntities.REPAIR_DRONE.get().create(level);
        check(helper, drone != null, "修復ドローンを作れません");
        drone.moveTo(at.x + 2.0, at.y + 1.0, at.z, 0.0F, 0.0F);
        level.addFreshEntity(drone);
        helper.runAfterDelay(2, () -> {
            double max = guard.getEnemyMaxHp();
            guard.setEnemyHp(max * 0.4);
            double healed = drone.testRepair();
            check(helper, healed > 0 && guard.getEnemyHp() > max * 0.4, "修復ドローンが、傷ついた防衛兵を直しません");
            guard.discard();
            drone.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void guardianTestsTheConnection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AncientGuardian boss = ModEntities.ANCIENT_GUARDIAN.get().create(level);
        check(helper, boss != null, "古代守護者を作れません");
        check(helper, boss.requiredDaises(6) == 4 && boss.requiredDaises(3) == 3 && boss.requiredDaises(1) == 1,
                "結び台の人数が、min(4, 戦う人) になっていません");
        check(helper, !boss.barrier() && Math.abs(boss.damageTakenMultiplier(null) - 1.0) < 1.0E-6
                && Boolean.FALSE.equals(boss.boneToggles().get("barrier")), "結界のない守護者が、攻撃を受けません");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        FakePlayer a = fake(helper, "link_a");
        FakePlayer b = fake(helper, "link_b");
        FakePlayer c = fake(helper, "link_c");
        a.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        b.moveTo(at.x + 4.0, at.y, at.z, 0.0F, 0.0F);
        c.moveTo(at.x + 20.0, at.y, at.z, 0.0F, 0.0F);
        List<ServerPlayer> all = List.of(a, b, c);
        check(helper, AncientGuardian.linked(a, all, 6.0) && AncientGuardian.linked(b, all, 6.0) && !AncientGuardian.linked(c, all, 6.0),
                "接続判定の「6ブロック以内の仲間」の数え方が違います");
        ServerPlayer holder = AncientGuardian.keyHolder(all, Map.of(a.getUUID(), 40, b.getUUID(), 90, c.getUUID(), 10));
        check(helper, holder == b, "第七の門鍵が、結び台に一番長く乗っていた人へ渡りません");
        boss.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void guardianGearIsPerJob(GameTestHelper helper) {
        EnemyDef boss = EnemyDefs.get(AmemaruMod.id("ancient_guardian"));
        check(helper, boss != null, "古代守護者の定義がありません");
        long matched = boss.rare().stream().filter(EnemyDef.RareDrop::jobMatch).count();
        // 11月25日「防具は一式全部揃えた形に」：職業ごとに、武器1つと防具一式（頭・胴・脚・足）
        check(helper, matched == 30, "守護者シリーズ（職業ごとの武器1と防具一式4）が30ではありません：" + matched);
        for (String job : JOBS) {
            int weapons = 0;
            int shells = 0;
            java.util.Set<GearSlot> armor = java.util.EnumSet.noneOf(GearSlot.class);
            for (EnemyDef.RareDrop rare : boss.rare()) {
                EquipmentDef gear = EquipmentDefs.server().get(rare.item());
                if (!rare.jobMatch() || gear == null || !gear.jobs().contains(AmemaruMod.id(job))) {
                    continue;
                }
                if (gear.slot() == GearSlot.WEAPON) {
                    check(helper, Math.abs(rare.chance() - 4.0) < 1.0E-6, rare.item() + " の確率が4%ではありません");
                    weapons += gear.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.ALLY_POWER) ? 1 : 0;
                    continue;
                }
                check(helper, Math.abs(rare.chance() - 3.0) < 1.0E-6, rare.item() + " の確率が3%ではありません");
                check(helper, gear.setEffects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.DAMAGE_REDUCTION),
                        rare.item() + " に、一式の効果（被ダメージ減）がありません");
                check(helper, ForgeRegistries.ITEMS.getValue(rare.item()) instanceof jp.amemaru.lostconnection.item.AmemaruArmorItem piece
                        && piece.series().equals("guardian_" + job), rare.item() + " が、守護者シリーズの防具として登録されていません");
                armor.add(gear.slot());
                if (gear.slot() == GearSlot.CHEST && gear.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.DAMAGE_REDUCTION)) {
                    shells++;
                }
            }
            check(helper, weapons == 1 && shells == 1 && armor.size() == 4,
                    job + " の守護者シリーズが、武器1（味方が近いほど攻撃+）と防具一式（頭・胴〈被ダメージ減〉・脚・足）ではありません");
        }
        FakePlayer player = fake(helper, "gear_job");
        PlayerData data = PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
        ResourceLocation before = data.getCombatJob();
        data.setCombatJob(AmemaruMod.id("knight"));
        check(helper, DropService.forJob(AmemaruMod.id("guardian_greatsword"), data) && !DropService.forJob(AmemaruMod.id("guardian_staff"), data),
                "ナイトが、ナイトの守護者シリーズだけを引くようになっていません");
        data.setCombatJob(before);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void seventhGateKeyIsUnique(GameTestHelper helper) {
        Item item = ForgeRegistries.ITEMS.getValue(AncientGuardian.GATE_KEY);
        check(helper, item != null, "第七の門鍵の品がありません");
        AccessoryDef def = AccessoryDefs.get(item);
        check(helper, def != null && def.rarity() == Rarity.UNIQUE
                && def.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.PARTY_WARD && e.threshold() == 8.0 && e.percent() == 5.0),
                "第七の門鍵が、ユニークの「認証（8ブロック・5%）」になっていません");
        FakePlayer player = fake(helper, "gate_key");
        player.getInventory().clearContent();
        AncientGuardian.giveGateKey(helper.getLevel(), player);
        ItemStack stack = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                stack = player.getInventory().getItem(i);
            }
        }
        check(helper, !stack.isEmpty() && GearData.isBound(stack), "第七の門鍵が、取引できない品として渡されません");
        player.getInventory().clearContent();
        helper.succeed();
    }

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
