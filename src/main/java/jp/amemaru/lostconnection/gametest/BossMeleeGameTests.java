package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.MeleeGuard;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.enemy.boss.AmemaruBoss;
import jp.amemaru.lostconnection.enemy.boss.Veldhorn;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearSlot;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.SkillService;
import jp.amemaru.lostconnection.job.SkillTreeService;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
 * 11月25日のご指示（修正点8つ）の自動テスト：使えなかった武器・近接職の守り・ボスの技の間・みんなで受ける一撃はレイドだけ。
 * 音の床は {@link FloorRealPlayGameTests}（本当に建てた4階で、遊ぶ人と同じ踏み方）。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BossMeleeGameTests {
    private static final String EMPTY = "empty3x3x3";

    private BossMeleeGameTests() {
    }

    // ---------------------------------------------------------------- 武器

    /**
     * どの武器も、定義（equipment の jobs）に書いた職業で持てばスキルが使える（職業の武器のタグにもある）。ほかの職業では使えない。
     * 11月25日のご指示「ウィザード系の武器でウィザードにしても使えないものがある。他の武器でもある可能性があるので確認」。
     */
    @GameTest(template = EMPTY)
    public static void everyWeaponWorksForItsJob(GameTestHelper helper) {
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        FakePlayer player = fake(helper, "weapons");
        PlayerData data = data(player);
        int checked = 0;
        for (EquipmentDef def : EquipmentDefs.server().equipment().values()) {
            if (def.slot() != GearSlot.WEAPON) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(def.id());
            check(helper, item != null, def.id() + " の品がありません");
            ItemStack stack = new ItemStack(item);
            check(helper, !def.jobs().isEmpty(), def.id() + " に、使う職業（jobs）がありません");
            for (ResourceLocation jobId : def.jobs()) {
                JobDef job = snapshot.job(jobId);
                check(helper, job != null && job.weapons() != null, def.id() + " の職業 " + jobId + " がないか、武器のタグがありません");
                check(helper, stack.is(job.weapons()), def.id() + " が、" + jobId + " の武器のタグ（" + job.weapons().location() + "）にありません");
                JobService.setJobForced(player, data, job);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
                check(helper, SkillService.hasWeapon(player, job), def.id() + " を " + jobId + " で持っても、スキルが使えません");
                checked++;
            }
            // ほかの職業では使えない（何でも通してしまっていないか）
            for (JobDef other : snapshot.jobs()) {
                if (!def.jobs().contains(other.id()) && other.weapons() != null) {
                    check(helper, !SkillService.isJobWeapon(stack, other), def.id() + " が、" + other.id() + " の武器になっています");
                }
            }
        }
        check(helper, checked >= 32, "確かめた武器が少なすぎます：" + checked);
        // 2階・4階・5階の武器（前はタグに抜けていた）
        for (String[] pair : new String[][]{{"elder_branch_staff", "wizard"}, {"guardian_staff", "wizard"}, {"prayer_bell_staff", "healer"},
                {"guardian_bell_staff", "healer"}, {"guardian_bow", "archer"}, {"guardian_daggers", "rogue"},
                {"guardian_gauntlets", "monk"}, {"guardian_greatsword", "knight"}}) {
            JobDef job = snapshot.job(AmemaruMod.id(pair[1]));
            Item item = ForgeRegistries.ITEMS.getValue(AmemaruMod.id(pair[0]));
            check(helper, job != null && item != null && new ItemStack(item).is(job.weapons()), pair[0] + " が " + pair[1] + " の武器のタグにありません");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }

    // ---------------------------------------------------------------- 近接職の守り

    /** 不屈の再起：HPが3割を切ると、すぐに大きく戻り、戻り続ける。90秒に1回。 */
    @GameTest(template = EMPTY)
    public static void knightLastStandHealsWhenLow(GameTestHelper helper) {
        FakePlayer player = fake(helper, "last_stand");
        PlayerData data = withSkill(player, "knight", "knight/last_stand", 1);
        SkillDef skill = SkillRegistry.get().skill(AmemaruMod.id("knight/last_stand"));
        check(helper, skill != null && skill.passive() != null && skill.passive().type() == PassiveDef.Type.LAST_STAND
                && skill.minLevel() >= 21, "不屈の再起が、Lv21からのナイトのパッシブではありません");
        MeleeGuard.clear();
        double max = data.getStats().maxHp();
        data.setHp(max * 0.2);
        long now = player.level().getGameTime();
        check(helper, MeleeGuard.onLowHp(player, data, now), "HPが3割を切っても、不屈の再起が働きません");
        double value = skill.passive().value(1);
        check(helper, data.getHp() >= max * (0.2 + value / 100.0) - 1.0, "不屈の再起で、すぐに最大HPの" + value + "%が戻りません："
                + data.getHp() + " / " + max);
        check(helper, data.getBuffs().regenerating(now), "不屈の再起のあと、HPが戻り続けていません");
        check(helper, !MeleeGuard.onLowHp(player, data, now + 20), "不屈の再起が、90秒たたないうちにまた働きました");
        check(helper, MeleeGuard.lastStandWait(player, now) == MeleeGuard.LAST_STAND_COOLDOWN, "不屈の再起の待ち時間が90秒ではありません");
        // ほかの職業（パッシブなし）では働かない
        FakePlayer other = fake(helper, "no_stand");
        PlayerData otherData = withSkill(other, "wizard", null, 0);
        otherData.setHp(otherData.getStats().maxHp() * 0.2);
        check(helper, !MeleeGuard.onLowHp(other, otherData, now), "不屈の再起のない人で働きました");
        MeleeGuard.clear();
        helper.succeed();
    }

    /** 影吸い（与えたダメージの一部で癒す。1回で最大HPの3%まで）と、金剛身（受けるダメージが減る。気が50以上で2倍）。 */
    @GameTest(template = EMPTY)
    public static void rogueDrainAndMonkAdamant(GameTestHelper helper) {
        FakePlayer rogue = fake(helper, "drain");
        PlayerData rogueData = withSkill(rogue, "rogue", "rogue/shadow_drain", 1);
        double max = rogueData.getStats().maxHp();
        rogueData.setHp(max * 0.5);
        double value = SkillRegistry.get().skill(AmemaruMod.id("rogue/shadow_drain")).passive().value(1);
        MeleeGuard.drain(rogue, rogueData, 100.0);
        check(helper, Math.abs(rogueData.getHp() - (max * 0.5 + Math.min(100.0 * value / 100.0, max * MeleeGuard.DRAIN_CAP))) < 0.01,
                "影吸いで戻るHPが違います：" + rogueData.getHp());
        double before = rogueData.getHp();
        MeleeGuard.drain(rogue, rogueData, 1.0E6);
        check(helper, Math.abs(rogueData.getHp() - before - max * MeleeGuard.DRAIN_CAP) < 0.01, "影吸いが、最大HPの3%を超えて戻りました");

        FakePlayer monk = fake(helper, "adamant");
        PlayerData monkData = withSkill(monk, "monk", "monk/adamant_body", 1);
        double adamant = SkillRegistry.get().skill(AmemaruMod.id("monk/adamant_body")).passive().value(1);
        monkData.setGauge(0.0);
        check(helper, Math.abs(MeleeGuard.adamantReduction(monkData) - adamant) < 1.0E-6, "金剛身で減る割合が違います");
        monkData.setGauge(60.0);
        check(helper, Math.abs(MeleeGuard.adamantReduction(monkData) - adamant * 2.0) < 1.0E-6, "気が50以上のとき、金剛身がさらに減らしません");
        monkData.setGauge(0.0);
        // 職業の守り：ローグとモンクの防御・HPを上げた（11月25日）
        JobDef rogueJob = SkillRegistry.get().job(AmemaruMod.id("rogue"));
        JobDef monkJob = SkillRegistry.get().job(AmemaruMod.id("monk"));
        check(helper, rogueJob.statPercent(jp.amemaru.lostconnection.player.StatType.DEFENSE) >= -5.0
                && monkJob.statPercent(jp.amemaru.lostconnection.player.StatType.DEFENSE) >= 15.0, "ローグ・モンクの防御が上がっていません");
        helper.succeed();
    }

    /** スキルツリーの形が変わって、なくなった節は、ログインのときに外れる（ポイントが戻る）。 */
    @GameTest(template = EMPTY)
    public static void removedTreeNodesAreDropped(GameTestHelper helper) {
        FakePlayer player = fake(helper, "tree_nodes");
        PlayerData data = withSkill(player, "knight", null, 0);
        LevelService.setLevel(player, data, 30);
        data.addTreeNode("g:guardian:99:9");
        int spent = SkillTreeService.spentPoints(data);
        check(helper, SkillTreeService.dropRemovedNodes(player, data) == 1 && !data.hasTreeNode("g:guardian:99:9"),
                "なくなった節が外れません");
        check(helper, SkillTreeService.spentPoints(data) == spent - 1, "なくなった節のポイントが戻りません");
        data.clearTreeNodes();
        helper.succeed();
    }

    // ---------------------------------------------------------------- ボス

    /**
     * ボスの技の間（11月25日「ボスのモーションが早く近接職が辛い」）：予兆を長くし、技のあとに息つぎ。長くした予兆の分だけ、
     * 技の動きをゆっくり流す。みんなで受ける一撃は、レイドのボスだけ。
     */
    @GameTest(template = EMPTY)
    public static void bossesAreSlowerAndHaveNoSharedBlow(GameTestHelper helper) {
        // 予兆を長くした技（打つ・よける技）：予兆は1秒以上で、アニメーションを作ったときの予兆（anim_windup）より長い
        java.util.Map<String, List<String>> attacks = java.util.Map.of(
                "veldhorn", List.of("sweep", "upthrust", "kick", "charge", "stomp", "leap", "roots"),
                "old_ward", List.of("sweep", "crack", "cage"),
                "hydram", List.of("cannon", "sweep", "whirl"),
                "cantare", List.of("wave", "pillars"),
                "ancient_guardian", List.of("sweep", "beam", "slam", "judge"));
        for (var bossEntry : attacks.entrySet()) {
            String id = bossEntry.getKey();
            EnemyDef def = EnemyDefs.get(AmemaruMod.id(id));
            check(helper, def != null, id + " の定義がありません");
            check(helper, !def.raid() && !def.skills().containsKey("share"), id + " に、みんなで受ける一撃が残っています（レイドのボスだけ）");
            for (String name : bossEntry.getValue()) {
                EnemyDef.Skill skill = def.skills().get(name);
                check(helper, skill != null, id + " に技 " + name + " がありません");
                check(helper, skill.windup() >= 1.0, id + " の技 " + name + " の予兆が1秒より短いです：" + skill.windup());
                double designed = skill.param("anim_windup", -1.0);
                check(helper, designed > 0.0 && designed < skill.windup(), id + " の技 " + name + " の予兆が、前（anim_windup）より長くなっていません");
            }
        }
        EnemyDef cantare = EnemyDefs.get(AmemaruMod.id("cantare"));
        check(helper, cantare.skills().get("song").param("answer", 0.0) >= 20.0, "カンターレの歌の答えの時間が20秒より短いです");
        // 息つぎ：ひとりで1.4秒
        ServerLevel level = helper.getLevel();
        Veldhorn boss = ModEntities.VELDHORN.get().create(level);
        check(helper, boss != null, "ヴェルドホーンを作れません");
        check(helper, boss.breatheTicks() == (int) Math.round(AmemaruBoss.BREATHE_SECONDS * 20.0), "ボスの息つぎが1.4秒ではありません："
                + boss.breatheTicks());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        boss.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        helper.runAfterDelay(2, () -> {
            // 角の薙ぎ払い：0.8秒で作ったアニメーションを、1.3秒の予兆に合わせてゆっくり流す
            boss.playAction("attack");
            EnemyDef.Skill sweep = boss.def().skills().get("sweep");
            float expected = (float) (sweep.param("anim_windup", sweep.windup()) / sweep.windup());
            check(helper, expected < 0.99F && Math.abs(boss.actionSpeed() - expected) < 0.01F,
                    "角の薙ぎ払いのアニメーションの速さが " + expected + " ではありません：" + boss.actionSpeed());
            boss.playAction("skill_roar");
            check(helper, Math.abs(boss.actionSpeed() - 1.0F) < 0.01F, "予兆のない動き（咆哮）の速さが1ではありません");
            boss.discard();
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- ボスの固有のギミック（11月25日）

    /** 1階：大地の咆哮。構えの間に最大HPの2.5%を削ると角が折れて倒れ、しばらく受けるダメージが1.5倍。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void veldhornEarthRoarCanBeBroken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = fake(helper, "earth_roar");
        withSkill(player, "knight", null, 0);
        Veldhorn boss = ModEntities.VELDHORN.get().create(level);
        check(helper, boss != null, "ヴェルドホーンを作れません");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        boss.moveTo(at.x + 4.0, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        helper.runAfterDelay(2, () -> {
            check(helper, boss.forceSkill("earth_roar") && boss.earthRoaring(), "大地の咆哮の構えに入りません");
            double needed = boss.earthRoarNeeded();
            check(helper, Math.abs(needed - boss.getEnemyMaxHp() * 0.025) < 1.0, "止めるのに要る量が最大HPの2.5%ではありません：" + needed);
            boss.onDamagedBy(player, needed + 1.0);
        });
        helper.runAfterDelay(6, () -> {
            check(helper, !boss.earthRoaring() && boss.weakTicks() > 0, "削っても、角が折れません");
            check(helper, Math.abs(boss.damageTakenMultiplier(player) - 1.5) < 1.0E-6, "角が折れても、受けるダメージが1.5倍になりません");
            boss.discard();
            helper.succeed();
        });
    }

    /** 2階：芽吹きの種。根づいた種は、壊せば消え、育つと古樹の傷が最大HPの4%ふさがる。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void oldWardSeedsSproutAndHeal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = fake(helper, "seeds");
        withSkill(player, "knight", null, 0);
        jp.amemaru.lostconnection.enemy.boss.OldWard boss = ModEntities.OLD_WARD.get().create(level);
        check(helper, boss != null, "オールドウォードを作れません");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        boss.moveTo(at.x + 6.0, at.y, at.z + 6.0, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        double[] before = new double[1];
        int[] grown = new int[1];
        helper.runAfterDelay(2, () -> check(helper, boss.forceSkill("seeds"), "芽吹きの種をまけません"));
        helper.runAfterDelay(45, () -> {
            List<jp.amemaru.lostconnection.enemy.boss.OldWardKnot> seeds = boss.seedEntities();
            check(helper, seeds.size() >= 2 && seeds.stream().allMatch(jp.amemaru.lostconnection.enemy.boss.OldWardKnot::seed),
                    "芽吹きの種が根づきません：" + seeds.size());
            jp.amemaru.lostconnection.combat.CombatService.applyDamage(seeds.get(0), 1.0E7, false, level.damageSources().generic());
            // HP70%（2段階目に入らない所）から、育った種のぶんだけ傷がふさがるかを見る
            boss.setEnemyHp(boss.getEnemyMaxHp() * 0.7);
            before[0] = boss.getEnemyHp();
            for (int i = 1; i < seeds.size(); i++) {
                seeds.get(i).setGrowTicks(1);
            }
            grown[0] = seeds.size() - 1;
        });
        helper.runAfterDelay(50, () -> {
            check(helper, boss.seedCount() == 0, "壊した種・育った種が残っています：" + boss.seedCount());
            double expected = before[0] + boss.getEnemyMaxHp() * 0.04 * grown[0];
            check(helper, boss.getEnemyHp() >= expected - 1.0, "種が育っても、古樹の傷がふさがりません：" + boss.getEnemyHp() + " / " + expected);
            boss.discard();
            helper.succeed();
        });
    }

    /** 3階：大放水。番人との間にブロックがあれば（堤のかげ）無事、なければ当たる。仮のブロックは、空気と水の所にだけ置き、元に戻る。 */
    @GameTest(template = EMPTY)
    public static void hydramFloodHidesBehindDikes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = fake(helper, "flood");
        net.minecraft.core.BlockPos wall = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        player.moveTo(wall.getX() + 0.5, wall.getY(), wall.getZ() + 1.5, 0.0F, 0.0F);
        Vec3 from = new Vec3(wall.getX() + 0.5, wall.getY() + 1.0, wall.getZ() - 1.5);
        check(helper, !jp.amemaru.lostconnection.enemy.boss.Hydram.behindDike(level, from, player), "何もないのに、堤のかげと判定しました");
        jp.amemaru.lostconnection.enemy.boss.TempBlocks dikes = new jp.amemaru.lostconnection.enemy.boss.TempBlocks();
        for (int h = 0; h < 3; h++) {
            dikes.place(level, wall.above(h), net.minecraft.world.level.block.Blocks.WAXED_CUT_COPPER.defaultBlockState());
        }
        check(helper, dikes.size() == 3, "堤のブロックを置けません");
        check(helper, jp.amemaru.lostconnection.enemy.boss.Hydram.behindDike(level, from, player), "堤のかげにいるのに、見えていると判定しました");
        // 床や仕掛け（空気や水でない所）には置かない。水の所には置き、戻すと水に戻る
        net.minecraft.core.BlockPos solidRel = new net.minecraft.core.BlockPos(2, 1, 2);
        net.minecraft.core.BlockPos wetRel = new net.minecraft.core.BlockPos(0, 1, 0);
        helper.setBlock(solidRel, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        helper.setBlock(wetRel, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        net.minecraft.core.BlockPos solid = helper.absolutePos(solidRel);
        net.minecraft.core.BlockPos wet = helper.absolutePos(wetRel);
        check(helper, !dikes.place(level, solid, net.minecraft.world.level.block.Blocks.WAXED_CUT_COPPER.defaultBlockState())
                && level.getBlockState(solid).is(net.minecraft.world.level.block.Blocks.STONE), "床（空気や水でない所）に堤を置きました");
        check(helper, dikes.place(level, wet, net.minecraft.world.level.block.Blocks.WAXED_CUT_COPPER.defaultBlockState())
                && level.getBlockState(wet).is(net.minecraft.world.level.block.Blocks.WAXED_CUT_COPPER), "水の所に堤を置けません");
        dikes.restore(level);
        check(helper, level.getBlockState(wall).isAir() && level.getBlockState(wall.above(2)).isAir() && dikes.isEmpty(),
                "堤のブロックが元に戻りません");
        check(helper, level.getBlockState(wet).is(net.minecraft.world.level.block.Blocks.WATER), "水の所に置いた堤が、水に戻りません");
        helper.setBlock(solidRel, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.setBlock(wetRel, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    /** 4階：残響の色（自分の色の床のそば3ブロックなら無事）と、5階：切断判定（4ブロック以内にほかの人がいると打たれる）。 */
    @GameTest(template = EMPTY)
    public static void echoAndSeverJudgePositions(GameTestHelper helper) {
        FakePlayer a = fake(helper, "echo_a");
        FakePlayer b = fake(helper, "echo_b");
        net.minecraft.core.BlockPos plate = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        a.moveTo(plate.getX() + 0.5 + 3.0, plate.getY(), plate.getZ() + 0.5, 0.0F, 0.0F);
        check(helper, jp.amemaru.lostconnection.enemy.boss.Cantare.echoSafe(a, plate, 3.5), "色の床のそば（3ブロック）にいるのに、外と判定しました");
        a.moveTo(plate.getX() + 0.5 + 5.0, plate.getY(), plate.getZ() + 0.5, 0.0F, 0.0F);
        check(helper, !jp.amemaru.lostconnection.enemy.boss.Cantare.echoSafe(a, plate, 3.5), "色の床から5ブロック離れているのに、そばと判定しました");
        b.moveTo(a.getX() + 3.0, a.getY(), a.getZ(), 0.0F, 0.0F);
        check(helper, jp.amemaru.lostconnection.enemy.boss.AncientGuardian.linked(a, List.<net.minecraft.server.level.ServerPlayer>of(a, b), 4.0), "3ブロックの2人が、切断判定で近すぎると判定されません");
        b.moveTo(a.getX() + 5.0, a.getY(), a.getZ(), 0.0F, 0.0F);
        check(helper, !jp.amemaru.lostconnection.enemy.boss.AncientGuardian.linked(a, List.<net.minecraft.server.level.ServerPlayer>of(a, b), 4.0), "5ブロック離れた2人が、近すぎると判定されました");
        EnemyDef guardian = EnemyDefs.get(AmemaruMod.id("ancient_guardian"));
        EnemyDef cantare = EnemyDefs.get(AmemaruMod.id("cantare"));
        EnemyDef hydram = EnemyDefs.get(AmemaruMod.id("hydram"));
        EnemyDef oldWard = EnemyDefs.get(AmemaruMod.id("old_ward"));
        EnemyDef veldhorn = EnemyDefs.get(AmemaruMod.id("veldhorn"));
        check(helper, guardian.skills().containsKey("sever") && cantare.skills().containsKey("echo") && hydram.skills().containsKey("flood")
                && oldWard.skills().containsKey("seeds") && veldhorn.skills().containsKey("earth_roar"), "ボスの固有のギミックの定義がそろっていません");
        // 11月26日：大地の咆哮を止められなかったときの衝撃は、ほかのどの技（突進 2.2倍）よりも強い（3.2倍）
        double roarPower = veldhorn.skills().get("earth_roar").power();
        check(helper, roarPower >= 3.2 - 1.0E-6, "大地の咆哮の衝撃が弱いままです：" + roarPower);
        for (java.util.Map.Entry<String, EnemyDef.Skill> entry : veldhorn.skills().entrySet()) {
            check(helper, entry.getKey().equals("earth_roar") || entry.getValue().power() < roarPower,
                    "大地の咆哮の衝撃が、ほかの技（" + entry.getKey() + "）より弱いです");
        }
        helper.succeed();
    }

    /**
     * 11月27日：ボスごとのHPの帯。1階は1本、強くなるにつれて増える（5本まで）。HPは前の1.5倍（2人ぶんで1階24000以上）。
     */
    @GameTest(template = EMPTY)
    public static void bossHpBarsGrowWithFloors(GameTestHelper helper) {
        String[] bosses = {"veldhorn", "old_ward", "hydram", "cantare", "ancient_guardian", "ordo", "reflecta", "mune"};
        int before = 0;
        for (int i = 0; i < bosses.length; i++) {
            EnemyDef def = EnemyDefs.get(AmemaruMod.id(bosses[i]));
            check(helper, def != null, bosses[i] + " の定義がありません");
            int bars = jp.amemaru.lostconnection.enemy.boss.AmemaruBoss.hpBars(def);
            check(helper, i > 0 || bars == 1, "1階のボスのHPの帯が1本ではありません：" + bars);
            check(helper, bars >= before && bars <= 5, bosses[i] + " のHPの帯が、前の階より少ないか5本より多い：" + bars);
            before = bars;
        }
        check(helper, before >= 4, "8階のボスのHPの帯が少なすぎます：" + before);
        EnemyDef veldhorn = EnemyDefs.get(AmemaruMod.id("veldhorn"));
        check(helper, veldhorn.hp() >= 24000, "1階のボスのHPが1.5倍になっていません：" + veldhorn.hp());
        helper.succeed();
    }

    // ---------------------------------------------------------------- ボス戦の曲と演出（11月25日）

    /**
     * 1〜8階のボスに、それぞれの曲（sounds.json の {@code music.boss.<ボス>} と、Mod に入った Ogg のファイル）があり、HPの帯に
     * 「曲を流す」印が立っている。戦いの様子と段階は、クライアントへ送る値に写る（部屋のないボスは「戦っていない」）。
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void bossMusicAndFightSync(GameTestHelper helper) {
        for (int floor = 1; floor <= 8; floor++) {
            jp.amemaru.lostconnection.tower.FloorDef def = jp.amemaru.lostconnection.tower.FloorDefs.byNumber(floor);
            check(helper, def != null && def.bossRoom() != null, floor + "階のボスの部屋がありません");
            String id = def.bossRoom().boss().getPath();
            ResourceLocation sound = AmemaruMod.id("music.boss." + id);
            check(helper, ForgeRegistries.SOUND_EVENTS.containsKey(sound), floor + "階のボスの曲（" + sound + "）が登録されていません");
            try (java.io.InputStream in = AmemaruMod.class.getResourceAsStream("/assets/amemaru/sounds/music/boss_" + id + ".ogg")) {
                check(helper, in != null, floor + "階のボスの曲のファイル（boss_" + id + ".ogg）がありません");
                byte[] head = in.readNBytes(4);
                long size = head.length + in.transferTo(java.io.OutputStream.nullOutputStream());
                check(helper, "OggS".equals(new String(head, StandardCharsets.US_ASCII)) && size > 200_000L,
                        floor + "階のボスの曲が Ogg でないか、短すぎます（" + size + " バイト）");
            } catch (java.io.IOException e) {
                throw new GameTestAssertException(floor + "階のボスの曲を読めません：" + e);
            }
        }
        ServerLevel level = helper.getLevel();
        Veldhorn boss = ModEntities.VELDHORN.get().create(level);
        check(helper, boss != null, "ヴェルドホーンを作れません");
        check(helper, boss.barPlaysMusic(), "ボスのHPの帯に、曲を流す印が立っていません");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        boss.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        helper.runAfterDelay(2, () -> {
            check(helper, boss.fightState() == AmemaruBoss.FIGHT_NONE && boss.shownPhase() == 1,
                    "部屋のないボスが、戦っていることになっているか、段階が1ではありません");
            boss.setEnemyHp(boss.getEnemyMaxHp() * 0.45);
        });
        helper.runAfterDelay(5, () -> {
            check(helper, boss.phase() == 2 && boss.shownPhase() == 2, "HP45%で、クライアントへ送る段階が2になりません：" + boss.shownPhase());
            boss.discard();
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- 道具

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "am25_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-1125:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static PlayerData data(FakePlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
    }

    /** その職業の Lv30 にして、スキルを1つ（{@code skill} が null なら、なし）覚えさせる。 */
    private static PlayerData withSkill(FakePlayer player, String jobName, String skill, int rank) {
        PlayerData data = data(player);
        JobDef job = SkillRegistry.get().job(AmemaruMod.id(jobName));
        if (job == null) {
            throw new GameTestAssertException("職業がありません：" + jobName);
        }
        JobService.setJobForced(player, data, job);
        LevelService.setLevel(player, data, 30);
        if (skill != null) {
            data.setSkillRank(AmemaruMod.id(skill), rank);
        }
        LevelService.recompute(player, data);
        return data;
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}
