package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.combat.DamageCalculator;
import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.combat.TargetVitals;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.PassiveService;
import jp.amemaru.lostconnection.job.ReactiveSkills;
import jp.amemaru.lostconnection.job.SkillContext;
import jp.amemaru.lostconnection.job.SkillDescriber;
import jp.amemaru.lostconnection.job.SkillExecutor;
import jp.amemaru.lostconnection.job.SkillService;
import jp.amemaru.lostconnection.job.SkillTreeService;
import jp.amemaru.lostconnection.job.def.BuffKind;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillKind;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.CastState;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.SkillCooldown;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * スキルの拡張（11月17日）の仕組みの自動テスト：スキルの数と木の形・受け流し・障壁・凍え→凍結→砕く・星痕と星爆・防御崩し・
 * 誓気解放・溜め・誓いの円陣・凍結した魔物の攻撃・パッシブの倍率。
 *
 * <p>数値は同梱の {@code skills/**} と config の初期値が前提。偽のプレイヤーはプレイヤー一覧に入らないため、
 * 「使った人」を一覧から探す処理（吸収の回復・星の残光のMP・挑発の極意）はここでは確かめず、LANの2人で確かめる。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillMechanicsGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;

    private SkillMechanicsGameTests() {
    }

    // ================================================================ 定義

    /** 各職業：アクティブ12（流派ごとに6）・パッシブ6（流派ごとに3）・奥義1。木は4段×3列で、前提は同じ流派。説明文が作れる。 */
    @GameTest(template = EMPTY)
    public static void expandedSkillTrees(GameTestHelper helper) {
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        check(helper, snapshot.problems().isEmpty(), "職業・スキルの定義に問題があります：" + snapshot.problems());
        for (String name : new String[]{"knight", "wizard"}) {
            JobDef job = job(name);
            List<SkillDef> skills = snapshot.skillsOf(job.id());
            long actives = skills.stream().filter(skill -> skill.isActive() && !skill.isUltimate()).count();
            long passives = skills.stream().filter(skill -> skill.kind() == SkillKind.PASSIVE).count();
            long ultimates = skills.stream().filter(SkillDef::isUltimate).count();
            check(helper, actives == 12, name + " のアクティブスキルが12個ではありません：" + actives);
            check(helper, passives == 6, name + " のパッシブスキルが6個ではありません：" + passives);
            check(helper, ultimates == 2, name + " の奥義が2つではありません：" + ultimates);
            long ultimateSchools = skills.stream().filter(SkillDef::isUltimate).map(SkillDef::school).distinct().count();
            check(helper, ultimateSchools == 2, name + " の奥義が、2つの流派に1つずつになっていません");
            Map<String, int[]> perSchool = new HashMap<>();
            for (SkillDef skill : skills) {
                for (int rank = 1; rank <= skill.maxRank(); rank++) {
                    check(helper, !SkillDescriber.describe(skill, job, rank).isEmpty(), skill.id() + " の" + rank + "段目の説明が空です");
                }
                if (skill.isUltimate()) {
                    continue;
                }
                int[] count = perSchool.computeIfAbsent(skill.school(), key -> new int[2]);
                count[skill.isActive() ? 0 : 1]++;
                check(helper, skill.row() >= 0 && skill.row() <= 3, skill.id() + " の段（row）が0〜3ではありません");
                check(helper, skill.isActive() ? skill.column() <= 1 : skill.column() == 2,
                        skill.id() + " の列（column）が、アクティブは0か1、パッシブは2になっていません");
                for (SkillDef.Requirement requirement : skill.requirements()) {
                    SkillDef required = snapshot.skill(requirement.skill());
                    check(helper, required != null && required.school().equals(skill.school()),
                            skill.id() + " の前提が同じ流派にありません：" + requirement.skill());
                }
                if (skill.isCharged()) {
                    check(helper, !skill.chargeAnimation().isEmpty(), skill.id() + " に溜めのモーションがありません");
                }
            }
            check(helper, perSchool.size() == 2, name + " の流派が2つではありません：" + perSchool.keySet());
            for (Map.Entry<String, int[]> entry : perSchool.entrySet()) {
                check(helper, entry.getValue()[0] == 6 && entry.getValue()[1] == 3,
                        name + " の流派「" + entry.getKey() + "」が、アクティブ6・パッシブ3になっていません：" + entry.getValue()[0] + "・"
                                + entry.getValue()[1]);
            }
        }
        SkillDef heavy = skill("knight/heavy_slash");
        check(helper, Math.abs(heavy.chargeMultiplier(0.0) - 1.0) < EPSILON && Math.abs(heavy.chargeMultiplier(0.5) - 2.0) < EPSILON
                && Math.abs(heavy.chargeMultiplier(1.0) - 3.0) < EPSILON, "溜め斬りの倍率が1〜3倍になっていません");
        helper.succeed();
    }

    // ================================================================ ナイト

    /** 受け流し：構えの間、正面の攻撃だけを無効にし、最初の1回だけ反撃（ダメージ・よろめき・誓気+15・クールダウン半分）。 */
    @GameTest(template = EMPTY)
    public static void parryNegatesAndCounters(GameTestHelper helper) {
        FakePlayer player = fake(helper, "parry");
        PlayerData data = knight(helper, player);
        SkillDef parry = skill("knight/parry");
        SkillTreeService.forceRank(player, data, parry, 1);
        int slot = data.loadoutSlotOf(parry.id());
        data.setGauge(0);
        Vec3 flat = flatLook(player);
        Zombie front = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie back = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        try {
            front.moveTo(player.getX() + flat.x * 1.5, player.getY(), player.getZ() + flat.z * 1.5, 0.0F, 0.0F);
            back.moveTo(player.getX() - flat.x * 1.5, player.getY(), player.getZ() - flat.z * 1.5, 0.0F, 0.0F);
            long now = helper.getLevel().getGameTime();
            SkillService.Result result = SkillService.tryUse(player, data, slot, -1, null);
            check(helper, result.ok(), "受け流しを使えません：" + result.failure());
            check(helper, ReactiveSkills.parrying(player, now), "受け流しの構えになっていません");
            check(helper, player.hasEffect(ModEffects.PARRY.get()), "受け流しの構えのアイコン（効果）が付いていません");
            int cooldown = SkillService.cooldownTicks(data, parry, 1);

            check(helper, !ReactiveSkills.tryParry(player, back, now), "背中からの攻撃を受け流しました");
            float before = front.getHealth();
            check(helper, ReactiveSkills.tryParry(player, front, now), "正面からの攻撃を受け流せません");
            check(helper, front.getHealth() < before, "反撃のダメージが入っていません");
            check(helper, StatusService.staggered(front), "反撃で、よろめいていません");
            check(helper, Math.abs(data.getGauge() - 15.0) < EPSILON, "反撃で誓気が15たまっていません：" + data.getGauge());
            SkillCooldown after = data.getCooldown(parry.id());
            int expected = (int) Math.ceil(cooldown * 0.5);
            check(helper, after != null && after.endTick() == now + expected,
                    "反撃で、クールダウンの残りが半分になっていません：" + after + "（" + expected + "）");

            float again = front.getHealth();
            check(helper, ReactiveSkills.tryParry(player, front, now), "構えの間の2回目の攻撃を受け流せません");
            check(helper, Math.abs(front.getHealth() - again) < 1.0E-4F, "2回目にも反撃しました");
            check(helper, !ReactiveSkills.tryParry(player, front, now + 40), "構えが終わった後も受け流しました");
        } finally {
            front.discard();
            back.discard();
        }
        helper.succeed();
    }

    /** 誓いの円陣（ward）で受けるダメージが減り、凍結した魔物は殴れない。 */
    @GameTest(template = EMPTY)
    public static void wardAndFrozenAttackers(GameTestHelper helper) {
        FakePlayer player = fake(helper, "ward");
        PlayerData data = knight(helper, player);
        data.setHp(data.getStats().maxHp());
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        try {
            Vec3 flat = flatLook(player);
            zombie.moveTo(player.getX() + flat.x * 1.5, player.getY(), player.getZ() + flat.z * 1.5, 0.0F, 0.0F);
            double base = 4.0 * ServerConfig.VANILLA_MOB_DAMAGE_MULTIPLIER.get()
                    * DamageCalculator.mitigation(data.getStats().get(StatType.DEFENSE));
            long now = helper.getLevel().getGameTime();
            SkillExecutor.applyBuff(player, BuffKind.WARD, 20, 20.0, now, null);
            check(helper, player.hasEffect(ModEffects.WARD.get()), "誓いの円陣のアイコン（効果）が付いていません");
            expectTaken(helper, player, data, zombie, 4.0F, base * 0.8, "誓いの円陣（-20%）");

            LivingAttackEvent normal = new LivingAttackEvent(player, player.damageSources().mobAttack(zombie), 4.0F);
            CombatService.onLivingAttack(normal);
            check(helper, !normal.isCanceled(), "凍結していない魔物の攻撃が止まりました");
            StatusService.freeze(zombie, 40, null);
            check(helper, StatusService.frozen(zombie), "凍結していません");
            LivingAttackEvent frozen = new LivingAttackEvent(player, player.damageSources().mobAttack(zombie), 4.0F);
            CombatService.onLivingAttack(frozen);
            check(helper, frozen.isCanceled(), "凍結した魔物の攻撃が止まっていません");
        } finally {
            zombie.discard();
        }
        helper.succeed();
    }

    /** 誓気解放：誓気をすべて使い（コストの30と残りの70）、威力が1＋100×0.04＝5倍になる。 */
    @GameTest(template = EMPTY)
    public static void oathReleaseBurnsAllGauge(GameTestHelper helper) {
        FakePlayer player = fake(helper, "oath_release");
        PlayerData data = knight(helper, player);
        SkillDef release = skill("knight/oath_release");
        SkillTreeService.forceRank(player, data, release, 1);
        int slot = data.loadoutSlotOf(release.id());
        // 偽のプレイヤーのダメージは「持ち主なし」になり、訓練人形は傷つかないため、体力を増やしたゾンビで確かめる
        Zombie target = toughZombieInFront(helper, player, 1.6);
        try {
            data.setGauge(100);
            data.markCombat(helper.getLevel().getGameTime());
            double before = TargetVitals.hp(target);
            SkillService.Result result = SkillService.tryUse(player, data, slot, target.getId(), null);
            check(helper, result.ok(), "誓気解放を使えません：" + result.failure());
            check(helper, data.getGauge() < EPSILON, "誓気が残っています：" + data.getGauge());
            double expected = data.getStats().get(StatType.ATTACK) * 1.0 * 5.0
                    * DamageCalculator.mitigation(TargetVitals.defense(target));
            double dealt = before - TargetVitals.hp(target);
            check(helper, dealt >= expected * 0.9 && dealt <= expected * 3.2,
                    "誓気解放のダメージが、攻撃×5倍になっていません：" + dealt + "（" + expected + "）");
            data.setGauge(10);
            data.clearCooldowns();
            SkillService.Result empty = SkillService.tryUse(player, data, slot, target.getId(), null);
            check(helper, empty.failure() == SkillService.Failure.NO_GAUGE, "誓気が30ないのに使えました：" + empty.failure());
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    /** 溜め斬り：押すと溜め（MPはまだ使わない・足が遅くなる）、溜めきって離すと防御崩しとよろめき。 */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void chargeAndRelease(GameTestHelper helper) {
        FakePlayer player = fake(helper, "charge");
        PlayerData data = knight(helper, player);
        SkillDef heavy = skill("knight/heavy_slash");
        SkillTreeService.forceRank(player, data, heavy, 1);
        int slot = data.loadoutSlotOf(heavy.id());
        TrainingDummy dummy = dummyInFront(helper, player, 1.6);
        double mpBefore = data.getMp();
        SkillService.Result pressed = SkillService.tryUse(player, data, slot, -1, null);
        check(helper, pressed.ok(), "溜め斬りの溜めを始められません：" + pressed.failure());
        CastState casting = data.getCasting();
        check(helper, casting != null && casting.charging(), "溜めになっていません：" + casting);
        check(helper, Math.abs(data.getMp() - mpBefore) < EPSILON, "溜めを始めただけでMPが減りました");
        check(helper, player.hasEffect(ModEffects.CHARGING.get()), "溜めの効果（足が遅くなる）が付いていません");
        check(helper, SkillService.tryUse(player, data, slot, -1, null).failure() == SkillService.Failure.NOT_CHARGING,
                "溜めの間の押し直しが、何もしないになっていません");
        helper.runAfterDelay(32, () -> {
            try {
                // 自動の確認の場所によっては床が狭いため、放つ前に目の前へ置き直す
                placeInFront(dummy, player, 1.6);
                SkillService.Result released = SkillService.tryRelease(player, data, slot, -1, null);
                check(helper, released.ok(), "溜め斬りを放てません：" + released.failure());
                check(helper, data.getCasting() == null, "放った後も溜めが残っています");
                check(helper, !player.hasEffect(ModEffects.CHARGING.get()), "放った後も溜めの効果が残っています");
                check(helper, data.getMp() < mpBefore - EPSILON, "放ってもMPが減っていません");
                check(helper, data.getCooldown(heavy.id()) != null, "放ってもクールダウンが始まっていません");
                check(helper, StatusService.armorBroken(dummy) && StatusService.staggered(dummy),
                        "溜めきって放ったのに、防御崩し・よろめきになっていません");
                check(helper, SkillService.tryRelease(player, data, slot, -1, null).failure() == SkillService.Failure.NOT_CHARGING,
                        "溜めていないのに放てました");
            } finally {
                dummy.discard();
            }
            helper.succeed();
        });
    }

    /**
     * 奥義は2つのうち1つだけ（11月17日のご指示）。1つを覚えると、もう一方は覚えられない。上位職を外して奥義が消えたり、
     * 白紙の誓約書でやり直したりすると、もう一方を選べる。
     */
    @GameTest(template = EMPTY)
    public static void ultimateIsAChoice(GameTestHelper helper) {
        FakePlayer player = fake(helper, "ultimate");
        PlayerData data = knight(helper, player);
        LevelService.setLevel(player, data, 30);
        check(helper, JobService.promote(player, data, true), "上位職に昇格できません");
        SkillDef castle = skill("knight/unfallen_castle");
        SkillDef cleave = skill("knight/heaven_cleave");
        for (String path : new String[]{"knight/rampart", "knight/whirlwind", "knight/heavy_slash"}) {
            SkillTreeService.forceRank(player, data, skill(path), 1);
        }
        check(helper, SkillTreeService.canLearn(data, castle) == SkillTreeService.Blocker.NONE,
                "不落の城を覚えられません：" + SkillTreeService.canLearn(data, castle));
        check(helper, SkillTreeService.canLearn(data, cleave) == SkillTreeService.Blocker.NONE,
                "天断ちを覚えられません：" + SkillTreeService.canLearn(data, cleave));
        check(helper, SkillTreeService.learn(player, data, cleave.id()), "天断ちを覚えられません");
        check(helper, SkillTreeService.canLearn(data, castle) == SkillTreeService.Blocker.ULTIMATE_CHOSEN,
                "天断ちを覚えたのに、不落の城も覚えられます：" + SkillTreeService.canLearn(data, castle));
        check(helper, !SkillTreeService.learn(player, data, castle.id()), "奥義を2つ覚えられました");
        check(helper, data.getSkillRank(castle.id()) == 0, "不落の城が覚えられています");
        // 上位職を外すと奥義は消え、昇格し直すと、もう一方を選べる
        JobService.demote(player, data);
        check(helper, data.getSkillRank(cleave.id()) == 0, "上位職を外しても、天断ちが残っています");
        check(helper, JobService.promote(player, data, true), "上位職に昇格し直せません");
        check(helper, SkillTreeService.canLearn(data, castle) == SkillTreeService.Blocker.NONE,
                "奥義が消えた後に、不落の城を選べません：" + SkillTreeService.canLearn(data, castle));
        check(helper, SkillTreeService.learn(player, data, castle.id()), "不落の城を覚えられません");
        check(helper, SkillTreeService.canLearn(data, cleave) == SkillTreeService.Blocker.ULTIMATE_CHOSEN,
                "不落の城を覚えたのに、天断ちも覚えられます");
        // 白紙の誓約書と同じやり直し：どちらも選べる状態に戻る
        SkillTreeService.resetSkills(player, data);
        for (String path : new String[]{"knight/rampart", "knight/whirlwind", "knight/heavy_slash"}) {
            SkillTreeService.forceRank(player, data, skill(path), 1);
        }
        check(helper, SkillTreeService.canLearn(data, cleave) == SkillTreeService.Blocker.NONE,
                "やり直した後に、天断ちを選べません：" + SkillTreeService.canLearn(data, cleave));
        helper.succeed();
    }

    /**
     * 天断ち：1秒の構えの後、地面と平行に前へのびる線の上の敵すべてに、攻撃×7倍のダメージ・防御崩し・よろめき。
     * 下を向いていても地面で止まらない（目の向きの線なら、1ブロック先で地面に当たり、奥の的に届かない）。
     * テストの場所は見えない壁（バリア）で囲まれていて、線はその壁（前へ4.5ブロック）で止まるため、的はその内側に置く。
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void heavenCleaveCutsTheLine(GameTestHelper helper) {
        FakePlayer player = fake(helper, "cleave");
        PlayerData data = knight(helper, player);
        LevelService.setLevel(player, data, 30);
        JobService.promote(player, data, true);
        SkillDef cleave = skill("knight/heaven_cleave");
        SkillTreeService.forceRank(player, data, cleave, 1);
        data.setMp(data.getStats().maxMp());
        int slot = data.loadoutSlotOf(cleave.id());
        player.setXRot(60.0F);
        Zombie near = toughZombieInFront(helper, player, 1.6);
        Zombie far = toughZombieInFront(helper, player, 3.8);
        Zombie behind = toughZombieInFront(helper, player, -2.0);
        double nearBefore = TargetVitals.hp(near);
        double farBefore = TargetVitals.hp(far);
        double behindBefore = TargetVitals.hp(behind);
        SkillService.Result result = SkillService.tryUse(player, data, slot, -1, null);
        check(helper, result.ok(), "天断ちを使えません：" + result.failure());
        check(helper, data.getCasting() != null && !data.getCasting().charging(), "構え（詠唱）になっていません");
        helper.runAfterDelay(24, () -> {
            try {
                // 偽のプレイヤーは毎tickの処理が動かないため、構えの終わりを直接進める
                placeInFront(near, player, 1.6);
                placeInFront(far, player, 3.8);
                placeInFront(behind, player, -2.0);
                SkillService.tick(player, data);
                check(helper, data.getCasting() == null, "構えが終わっていません");
                double expected = data.getStats().get(StatType.ATTACK) * 7.0 * DamageCalculator.mitigation(TargetVitals.defense(near));
                double nearDealt = nearBefore - TargetVitals.hp(near);
                double farDealt = farBefore - TargetVitals.hp(far);
                check(helper, nearDealt >= expected * 0.9 && nearDealt <= expected * 3.2,
                        "天断ち（近く）のダメージが、攻撃×7倍になっていません：" + nearDealt + "（" + expected + "）");
                check(helper, farDealt >= expected * 0.9 && farDealt <= expected * 3.2,
                        "天断ちが、下を向いていても奥の敵（3.8ブロック先）に届いていません：" + farDealt + "（" + expected + "）");
                check(helper, Math.abs(behindBefore - TargetVitals.hp(behind)) < EPSILON, "後ろの敵に当たりました");
                check(helper, StatusService.armorBroken(far) && StatusService.staggered(far), "防御崩し・よろめきになっていません");
            } finally {
                near.discard();
                far.discard();
                behind.discard();
            }
            helper.succeed();
        });
    }

    // ================================================================ ウィザード

    /** 光の帳：障壁が先にダメージを吸い、吸いきると砕けて残りをHPで受ける。 */
    @GameTest(template = EMPTY)
    public static void barrierAbsorbsDamage(GameTestHelper helper) {
        FakePlayer player = fake(helper, "barrier");
        PlayerData data = wizard(helper, player);
        data.setHp(data.getStats().maxHp());
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        try {
            double base = 4.0 * ServerConfig.VANILLA_MOB_DAMAGE_MULTIPLIER.get()
                    * DamageCalculator.mitigation(data.getStats().get(StatType.DEFENSE));
            long now = helper.getLevel().getGameTime();
            double amount = base * 1.5;
            SkillExecutor.applyBarrier(player, amount, 100, now);
            check(helper, player.hasEffect(ModEffects.BARRIER.get()), "光の帳のアイコン（効果）が付いていません");
            expectTaken(helper, player, data, zombie, 4.0F, 0.0, "障壁がすべて吸う");
            check(helper, Math.abs(data.getBuffs().barrierAmount - (amount - base)) < 0.01, "障壁の残りが正しくありません："
                    + data.getBuffs().barrierAmount);
            expectTaken(helper, player, data, zombie, 4.0F, base - (amount - base), "障壁が砕けて、残りを受ける");
            check(helper, !data.getBuffs().barrier(now), "吸いきった障壁が残っています");
            check(helper, !player.hasEffect(ModEffects.BARRIER.get()), "障壁が砕けても、アイコンが残っています");
        } finally {
            zombie.discard();
        }
        helper.succeed();
    }

    /** 凍え：1つずつ重なり、5つで凍結（凍えは消える）。砕いた後の3秒は凍結しない（4つまで）。陣は4つまで。3つ以上ならすぐ凍結。 */
    @GameTest(template = EMPTY)
    public static void chillFreezeAndShatter(GameTestHelper helper) {
        TrainingDummy first = helper.spawn(ModEntities.TRAINING_DUMMY.get(), new BlockPos(1, 1, 1));
        TrainingDummy second = helper.spawn(ModEntities.TRAINING_DUMMY.get(), new BlockPos(1, 1, 1));
        TrainingDummy third = helper.spawn(ModEntities.TRAINING_DUMMY.get(), new BlockPos(1, 1, 1));
        try {
            for (int i = 1; i <= 4; i++) {
                check(helper, !StatusService.addChill(first, 1, 80, 5, 40, 0, null), "凍えが" + i + "つで凍結しました");
                check(helper, StatusService.chill(first) == i, "凍えが" + i + "つになっていません：" + StatusService.chill(first));
            }
            check(helper, StatusService.addChill(first, 1, 80, 5, 40, 0, null), "凍えが5つで凍結しません");
            check(helper, StatusService.frozen(first) && StatusService.chill(first) == 0, "凍結したのに凍えが残っています");
            check(helper, StatusService.shatter(first) && !StatusService.frozen(first), "凍結を砕けません");
            check(helper, !StatusService.addChill(first, 5, 80, 5, 40, 0, null), "凍結が解けてすぐに、また凍結しました");
            check(helper, StatusService.chill(first) == 4, "凍結しない間の凍えが4つになっていません：" + StatusService.chill(first));

            check(helper, !StatusService.addChill(second, 9, 80, 4, 40, 0, null), "陣の凍え（4つまで）で凍結しました");
            check(helper, StatusService.chill(second) == 4, "陣の凍えが4つで止まっていません：" + StatusService.chill(second));

            StatusService.addChill(third, 3, 80, 5, 40, 0, null);
            check(helper, StatusService.addChill(third, 2, 80, 5, 40, 3, null), "凍えが3つの敵が、凍てつく波動ですぐ凍結しません");

            third.setDummyDefense(100);
            StatusService.armorBreak(third, 100, null);
            check(helper, Math.abs(TargetVitals.defense(third) - 75.0) < EPSILON,
                    "防御崩しで防御が25%下がっていません：" + TargetVitals.defense(third));
        } finally {
            first.discard();
            second.discard();
            third.discard();
        }
        helper.succeed();
    }

    /** 星爆：星痕のない敵しかいなければ使えない（MPも減らない）。星痕を弾けさせ、星痕が多いほど強い。 */
    @GameTest(template = EMPTY)
    public static void starBurstDetonatesBrands(GameTestHelper helper) {
        FakePlayer player = fake(helper, "star_burst");
        PlayerData data = wizard(helper, player);
        SkillDef burst = skill("wizard/star_burst");
        SkillTreeService.forceRank(player, data, burst, 1);
        int slot = data.loadoutSlotOf(burst.id());
        Zombie target = toughZombieInFront(helper, player, 2.0);
        try {
            double mp = data.getMp();
            check(helper, SkillService.tryUse(player, data, slot, -1, null).failure() == SkillService.Failure.NO_BRANDED,
                    "星痕がないのに星爆を使えました");
            check(helper, Math.abs(data.getMp() - mp) < EPSILON, "使えなかったのにMPが減りました");

            StatusService.addBrand(target, 2, 160, null);
            StatusService.addBrand(target, 1, 160, null);
            check(helper, StatusService.brands(target) == 3, "星痕が3つになっていません：" + StatusService.brands(target));
            StatusService.addBrand(target, 9, 160, null);
            check(helper, StatusService.brands(target) == 5, "星痕が5つで止まっていません：" + StatusService.brands(target));
            double before = TargetVitals.hp(target);
            SkillService.Result result = SkillService.tryUse(player, data, slot, -1, null);
            check(helper, result.ok(), "星爆を使えません：" + result.failure());
            check(helper, StatusService.brands(target) == 0, "星痕が消えていません");
            double expected = data.getStats().get(StatType.MAGIC) * (0.8 + 0.6 * 5)
                    * DamageCalculator.mitigation(TargetVitals.defense(target));
            double dealt = before - TargetVitals.hp(target);
            check(helper, dealt >= expected * 0.9 && dealt <= expected * 3.2,
                    "星爆（星痕5つ）のダメージが、魔力×3.8倍になっていません：" + dealt + "（" + expected + "）");
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    /** パッシブ：砕きの心得（よろめき・防御崩しに+10%）・氷の心臓（凍え・凍結に+10%）・深冷（凍え1.5倍・凍結+0.5秒）・闘志（HP半分で攻撃+10%）。 */
    @GameTest(template = EMPTY)
    public static void passiveMultipliers(GameTestHelper helper) {
        FakePlayer player = fake(helper, "passives");
        PlayerData data = knight(helper, player);
        SkillTreeService.forceRank(player, data, skill("knight/breaker"), 1);
        TrainingDummy dummy = helper.spawn(ModEntities.TRAINING_DUMMY.get(), new BlockPos(1, 1, 1));
        try {
            SkillDef call = skill("knight/call");
            SkillContext ctx = new SkillContext(player, data.getStats(), call, 0, 1).loadPassives(data);
            check(helper, Math.abs(ctx.passiveDamageMultiplier(dummy) - 1.0) < EPSILON, "ふつうの敵に倍率がかかりました");
            StatusService.armorBreak(dummy, 100, null);
            check(helper, Math.abs(ctx.passiveDamageMultiplier(dummy) - 1.1) < EPSILON,
                    "砕きの心得：防御崩しの敵に1.1倍になっていません：" + ctx.passiveDamageMultiplier(dummy));

            double attack = data.getStats().get(StatType.ATTACK);
            SkillTreeService.forceRank(player, data, skill("knight/fighting_spirit"), 1);
            data.setHp(data.getStats().maxHp() * 0.3);
            LevelService.recompute(player, data);
            check(helper, data.getStats().get(StatType.ATTACK) > attack * 1.05,
                    "闘志：HPが半分を切っても攻撃が上がっていません：" + attack + " → " + data.getStats().get(StatType.ATTACK));

            JobService.setJobForced(player, data, job("wizard"));
            SkillTreeService.forceRank(player, data, skill("wizard/frost_heart"), 1);
            SkillTreeService.forceRank(player, data, skill("wizard/deep_chill"), 1);
            SkillContext frost = new SkillContext(player, data.getStats(), skill("wizard/icicle"), 0, 1).loadPassives(data);
            StatusService.addChill(dummy, 1, 80, 5, 40, 0, null);
            check(helper, Math.abs(frost.passiveDamageMultiplier(dummy) - 1.1) < EPSILON,
                    "氷の心臓：凍えの敵に1.1倍になっていません：" + frost.passiveDamageMultiplier(dummy));
            check(helper, Math.abs(frost.chillDurationMultiplier - PassiveService.DEEP_CHILL_DURATION) < EPSILON
                    && frost.freezeBonusTicks == 10, "深冷：凍え1.5倍・凍結+10tick になっていません");
        } finally {
            dummy.discard();
        }
        helper.succeed();
    }

    // ================================================================ 共通

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "ams_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-mechanics:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static PlayerData data(ServerPlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
    }

    /** レベル15のナイト（剣と盾）。MPは満タン。 */
    private static PlayerData knight(GameTestHelper helper, ServerPlayer player) {
        PlayerData data = data(player);
        LevelService.setLevel(player, data, 15);
        JobService.setJobForced(player, data, job("knight"));
        equip(player, data, new ItemStack(ModItems.APPRENTICE_SWORD.get()), new ItemStack(ModItems.APPRENTICE_SHIELD.get()));
        data.clearCooldowns();
        data.setMp(data.getStats().maxMp());
        return data;
    }

    /** レベル15のウィザード（杖）。MPは満タン。 */
    private static PlayerData wizard(GameTestHelper helper, ServerPlayer player) {
        PlayerData data = data(player);
        LevelService.setLevel(player, data, 15);
        JobService.setJobForced(player, data, job("wizard"));
        equip(player, data, new ItemStack(ModItems.APPRENTICE_STAFF.get()), ItemStack.EMPTY);
        data.clearCooldowns();
        data.setMp(data.getStats().maxMp());
        return data;
    }

    private static void equip(ServerPlayer player, PlayerData data, ItemStack main, ItemStack off) {
        player.setItemSlot(EquipmentSlot.MAINHAND, main);
        player.setItemSlot(EquipmentSlot.OFFHAND, off);
        LevelService.recompute(player, data);
    }

    private static Vec3 flatLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        return new Vec3(look.x, 0.0, look.z).normalize();
    }

    /** 目の前（distance ブロック）に、防御0の訓練人形を置く。 */
    private static TrainingDummy dummyInFront(GameTestHelper helper, ServerPlayer player, double distance) {
        TrainingDummy dummy = helper.spawn(ModEntities.TRAINING_DUMMY.get(), new BlockPos(1, 1, 1));
        placeInFront(dummy, player, distance);
        dummy.setDummyDefense(0);
        return dummy;
    }

    /**
     * 目の前（distance ブロック）に置き、落ちないようにする。
     * テストの床は3×3しかなく、目の前はその外になることがある（クライアントの世界で確かめるときは、下が空のこともある）。
     */
    private static void placeInFront(LivingEntity entity, ServerPlayer player, double distance) {
        Vec3 flat = flatLook(player);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.moveTo(player.getX() + flat.x * distance, player.getY(), player.getZ() + flat.z * distance, 180.0F, 0.0F);
    }

    /** 目の前（distance ブロック）に、体力を10倍にしたハスク（日に当たっても燃えないゾンビ。動かない）を置く。 */
    private static Zombie toughZombieInFront(GameTestHelper helper, ServerPlayer player, double distance) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(1, 1, 1));
        placeInFront(zombie, player, distance);
        AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(200.0);
        }
        zombie.setHealth(200.0F);
        return zombie;
    }

    private static ResourceLocation id(String path) {
        return AmemaruMod.id(path);
    }

    private static JobDef job(String name) {
        JobDef job = SkillRegistry.get().job(id(name));
        if (job == null) {
            throw new GameTestAssertException("職業が見つかりません：" + name);
        }
        return job;
    }

    private static SkillDef skill(String path) {
        SkillDef skill = SkillRegistry.get().skill(id(path));
        if (skill == null) {
            throw new GameTestAssertException("スキルが見つかりません：" + path);
        }
        return skill;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    /** 魔物の攻撃（バニラの量）を受けたときに引かれる、独自のHPの量。 */
    private static void expectTaken(GameTestHelper helper, ServerPlayer player, PlayerData data, LivingEntity attacker,
                                    float vanillaAmount, double expected, String label) {
        LivingDamageEvent event = new LivingDamageEvent(player, player.damageSources().mobAttack(attacker), vanillaAmount);
        CombatService.onLivingDamage(event);
        double custom = event.getAmount() / player.getMaxHealth() * data.getStats().maxHp();
        check(helper, Math.abs(custom - expected) < 0.01, label + "：受けるダメージが " + expected + " ではありません：" + custom);
    }
}
