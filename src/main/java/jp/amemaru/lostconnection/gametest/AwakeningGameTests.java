package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.job.AwakeningService;
import jp.amemaru.lostconnection.job.ComboService;
import jp.amemaru.lostconnection.job.GaugeService;
import jp.amemaru.lostconnection.job.HealService;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.SkillContext;
import jp.amemaru.lostconnection.job.SkillService;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 覚醒（11月27日のご指示「どの役職もどこかのタイミングで強くなるタイミングを（パワースパイク）」「モンクは序盤は弱めでいいが、
 * 後半で爆発的な攻撃力を」【補完】）。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AwakeningGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;
    private static final List<String> COMBAT = List.of("knight", "wizard", "healer", "archer", "rogue", "monk");

    private AwakeningGameTests() {
    }

    /** 戦う6職業は、どれも2つ以上の覚醒を持ち、時期が違う。モンクの最後の覚醒は、いちばん遅い時期のひとつ。 */
    @GameTest(template = EMPTY)
    public static void everyCombatJobAwakens(GameTestHelper helper) {
        int monkLast = 0;
        int latestOther = 0;
        Set<String> names = new HashSet<>();
        for (String name : COMBAT) {
            JobDef job = job(name);
            List<JobDef.Awakening> list = job.awakenings();
            check(helper, list.size() >= 2, name + " の覚醒が2つ未満です：" + list.size());
            int before = 0;
            for (JobDef.Awakening awakening : list) {
                check(helper, awakening.level() >= before, name + " の覚醒がレベルの順に並んでいません");
                check(helper, !awakening.description().isEmpty(), name + " の覚醒「" + awakening.name() + "」に説明がありません");
                check(helper, names.add(awakening.name()), "覚醒の名前「" + awakening.name() + "」が重なっています");
                before = awakening.level();
            }
            int last = list.get(list.size() - 1).level();
            if (name.equals("monk")) {
                monkLast = last;
            } else {
                latestOther = Math.max(latestOther, last);
            }
        }
        check(helper, monkLast >= latestOther, "モンクの最後の覚醒（Lv" + monkLast + "）が、ほかの職業（Lv" + latestOther + "）より早いです");
        for (String name : List.of("weaponsmith", "armorsmith", "jeweler")) {
            check(helper, job(name).awakenings().isEmpty(), name + "（生産職）に覚醒があります");
        }
        helper.succeed();
    }

    /** モンク：練気はLv10からレベルとともに伸び、気の器（Lv20）で気と連撃の最大が増える。 */
    @GameTest(template = EMPTY)
    public static void monkGrowsLate(GameTestHelper helper) {
        FakePlayer player = fake(helper, "monk");
        PlayerData data = setup(player, "monk", 10);
        check(helper, Math.abs(AwakeningService.value(data, "damage")) < EPSILON, "Lv10のモンクで、もう練気が伸びています");
        check(helper, GaugeService.max(data) == 100, "Lv10の気の最大が100ではありません：" + GaugeService.max(data));
        JobDef.ComboDef combo = job("monk").combo();
        check(helper, combo != null && ComboService.max(data, combo) == 5, "Lv10の連撃の最大が5ではありません");
        LevelService.setLevel(player, data, 20);
        check(helper, Math.abs(AwakeningService.value(data, "damage") - 7.5) < EPSILON,
                "Lv20の練気が+7.5%ではありません：" + AwakeningService.value(data, "damage"));
        check(helper, GaugeService.max(data) == 150, "気の器で、気の最大が150になっていません：" + GaugeService.max(data));
        check(helper, ComboService.max(data, combo) == 7, "気の器で、連撃の最大が7になっていません：" + ComboService.max(data, combo));
        LevelService.setLevel(player, data, 80);
        check(helper, Math.abs(AwakeningService.value(data, "damage") - 30.0) < EPSILON,
                "練気が+30%で止まっていません：" + AwakeningService.value(data, "damage"));
        helper.succeed();
    }

    /** ウィザード：星の巡り（Lv16）でクールダウンが短くなる（奥義は変わらない）。 */
    @GameTest(template = EMPTY)
    public static void wizardCooldownSpike(GameTestHelper helper) {
        FakePlayer player = fake(helper, "wizard_cd");
        PlayerData data = setup(player, "wizard", 15);
        SkillDef comet = skill("wizard/comet");
        SkillDef starfall = skill("wizard/starfall");
        int before = SkillService.cooldownTicks(data, comet, 1);
        int ultimateBefore = SkillService.cooldownTicks(data, starfall, 1);
        LevelService.setLevel(player, data, 16);
        int after = SkillService.cooldownTicks(data, comet, 1);
        check(helper, Math.abs(after - before * 0.85) <= 1.0, "星の巡りで、彗星のクールダウンが−15%になっていません：" + before + " → " + after);
        check(helper, SkillService.cooldownTicks(data, starfall, 1) == ultimateBefore, "奥義のクールダウンまで短くなりました");
        helper.succeed();
    }

    /** 「もう一度」は、条件を満たしたスキルにだけつく（モンクの崩天・ウィザードの大魔導）。 */
    @GameTest(template = EMPTY)
    public static void echoNeedsItsCondition(GameTestHelper helper) {
        FakePlayer monk = fake(helper, "echo_monk");
        PlayerData data = setup(monk, "monk", 31);
        SkillDef hakkei = skill("monk/hakkei");
        SkillContext early = new SkillContext(monk, data.getStats(), hakkei, 0, 1);
        SkillService.armEcho(early, data, hakkei, 1, true, 0.0);
        check(helper, early.awakenEcho == 0.0, "Lv31（崩天の前）で余震がつきました");
        LevelService.setLevel(monk, data, 32);
        SkillContext full = new SkillContext(monk, data.getStats(), hakkei, 0, 1);
        SkillService.armEcho(full, data, hakkei, 1, true, 0.0);
        check(helper, Math.abs(full.awakenEcho - 100.0) < EPSILON, "連撃が最大の【極】に、余震（100%）がつきません：" + full.awakenEcho);
        SkillContext partial = new SkillContext(monk, data.getStats(), hakkei, 0, 1);
        SkillService.armEcho(partial, data, hakkei, 1, false, 0.0);
        check(helper, partial.awakenEcho == 0.0, "連撃が最大でない【極】に、余震がつきました");

        FakePlayer wizard = fake(helper, "echo_wizard");
        PlayerData wizardData = setup(wizard, "wizard", 30);
        SkillDef comet = skill("wizard/comet");
        SkillDef bolt = skill("wizard/lamp_bolt");
        SkillContext cast = new SkillContext(wizard, wizardData.getStats(), comet, 0, 1);
        SkillService.armEcho(cast, wizardData, comet, 1, false, 0.0);
        check(helper, Math.abs(cast.awakenEcho - 50.0) < EPSILON, "大魔導で、詠唱のある彗星に二重詠唱（50%）がつきません：" + cast.awakenEcho);
        SkillContext instant = new SkillContext(wizard, wizardData.getStats(), bolt, 0, 1);
        SkillService.armEcho(instant, wizardData, bolt, 1, false, 0.0);
        check(helper, instant.awakenEcho == 0.0, "詠唱のない灯火弾に、二重詠唱がつきました");
        helper.succeed();
    }

    /** ヒーラーの祝祭（Lv30）：回復した仲間の与えるダメージが上がる。Lv29では上がらない。 */
    @GameTest(template = EMPTY)
    public static void healerFestivalPowersAllies(GameTestHelper helper) {
        FakePlayer healer = fake(helper, "festival");
        PlayerData data = setup(healer, "healer", 29);
        FakePlayer ally = fake(helper, "festival_ally");
        PlayerData allyData = setup(ally, "knight", 15);
        allyData.setHp(allyData.getStats().maxHp() * 0.5);
        long now = helper.getLevel().getGameTime();
        HealService.healBySkill(healer, ally, data.getStats().get(StatType.MAGIC) * 2.0);
        check(helper, allyData.getBuffs().powerUp(now) == 0.0, "Lv29のヒーラーの回復で、仲間の与えるダメージが上がりました");
        LevelService.setLevel(healer, data, 30);
        allyData.setHp(allyData.getStats().maxHp() * 0.5);
        HealService.healBySkill(healer, ally, data.getStats().get(StatType.MAGIC) * 2.0);
        check(helper, Math.abs(allyData.getBuffs().powerUp(now) - 12.0) < EPSILON,
                "祝祭で、回復した仲間の与えるダメージが+12%になっていません：" + allyData.getBuffs().powerUp(now));
        check(helper, HealService.healMultiplier(healer, data) >= 1.3 - EPSILON, "祝福の調べと祝祭の回復の量（+30%）が入っていません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "awk_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-awaken:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static PlayerData setup(ServerPlayer player, String jobName, int level) {
        PlayerData data = PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
        JobService.setJobForced(player, data, job(jobName));
        LevelService.setLevel(player, data, level);
        LevelService.recompute(player, data);
        data.setHp(data.getStats().maxHp());
        data.setMp(data.getStats().maxMp());
        data.setGauge(0.0);
        data.setCombo(0, 0L);
        return data;
    }

    private static JobDef job(String name) {
        JobDef job = SkillRegistry.get().job(AmemaruMod.id(name));
        if (job == null) {
            throw new GameTestAssertException("職業がありません：" + name);
        }
        return job;
    }

    private static SkillDef skill(String id) {
        SkillDef skill = SkillRegistry.get().skill(AmemaruMod.id(id));
        if (skill == null) {
            throw new GameTestAssertException("スキルがありません：" + id);
        }
        return skill;
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}
