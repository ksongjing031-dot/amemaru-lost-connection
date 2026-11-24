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
        for (String id : List.of("veldhorn", "old_ward", "hydram", "cantare", "ancient_guardian")) {
            EnemyDef def = EnemyDefs.get(AmemaruMod.id(id));
            check(helper, def != null, id + " の定義がありません");
            check(helper, !def.raid() && !def.skills().containsKey("share"), id + " に、みんなで受ける一撃が残っています（レイドのボスだけ）");
            for (var entry : def.skills().entrySet()) {
                EnemyDef.Skill skill = entry.getValue();
                if (skill.power() <= 0.0 || skill.windup() <= 0.0 || entry.getKey().equals("song")) {
                    continue;
                }
                check(helper, skill.windup() >= 1.0, id + " の技 " + entry.getKey() + " の予兆が1秒より短いです：" + skill.windup());
                check(helper, skill.param("anim_windup", skill.windup()) <= skill.windup(),
                        id + " の技 " + entry.getKey() + " の anim_windup が予兆より長いです");
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
