package jp.amemaru.lostconnection.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.combat.DamageCalculator;
import jp.amemaru.lostconnection.combat.LinkComboTracker;
import jp.amemaru.lostconnection.combat.TargetVitals;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModDamageTypes;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import jp.amemaru.lostconnection.item.GearStats;
import jp.amemaru.lostconnection.job.GaugeService;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.PassiveService;
import jp.amemaru.lostconnection.job.SkillExecutor;
import jp.amemaru.lostconnection.job.SkillService;
import jp.amemaru.lostconnection.job.SkillTreeService;
import jp.amemaru.lostconnection.job.def.BuffKind;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.LinkType;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillKind;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.CastState;
import jp.amemaru.lostconnection.player.GrowthRules;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.SkillCooldown;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 職業とスキルの自動テスト（Phase 2。GitHub Actions で {@code gradlew runGameTestServer} として毎回動かす）。
 *
 * <p>数値は同梱の {@code jobs/*.json}・{@code skills/**}・{@code growth/default.json} と、config の初期値が前提。</p>
 *
 * <p>テストのプレイヤーは Forge の「偽のプレイヤー」。偽のプレイヤーはサーバーのプレイヤー一覧に入らないので、
 * 「使った人」を一覧から探す処理（盾結びの肩代わり・魔力の糸・連携の記録・遅れて落ちる流星）はここでは確かめられない。
 * それらは docs/phase2.md の「ゲーム内での確認」で、LANの2人で確かめる。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JobSkillGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;

    private JobSkillGameTests() {
    }

    // ================================================================ 職業の選択

    /** 職業を選ぶと、見習い装備（1回だけ）・最初のスキル・スキル枠・職業の伸び方・武器の数値が入る。 */
    @GameTest(template = EMPTY)
    public static void jobSelectionGivesKitSkillsAndStats(GameTestHelper helper) {
        FakePlayer player = fake(helper, "job_select");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        JobDef knight = job("knight");
        JobDef wizard = job("wizard");

        JobService.setJobForced(player, data, knight);
        check(helper, knight.id().equals(data.getCombatJob()), "職業がナイトになっていません：" + data.getCombatJob());
        expectCount(helper, player, ModItems.APPRENTICE_SWORD.get(), 1);
        expectCount(helper, player, ModItems.APPRENTICE_SHIELD.get(), 1);
        expectCount(helper, player, ModItems.APPRENTICE_GREATSWORD.get(), 1);
        expectCount(helper, player, ModItems.FORGET_WATER.get(), 1);
        expectCount(helper, player, ModItems.BLANK_OATH.get(), 1);
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                check(helper, GearStats.isBound(stack), "受け取った物が「取引できない」になっていません：" + stack);
            }
        }
        expectRank(helper, data, "knight/call", 1);
        expectRank(helper, data, "knight/charge_slash", 1);
        check(helper, data.getLearnedSkills().size() == 2, "最初のスキルが2つではありません：" + data.getLearnedSkills());
        expectLoadout(helper, data, "knight/call", "knight/charge_slash", null, null, null, null);
        check(helper, SkillTreeService.spentPoints(data) == 0, "最初のスキルにポイントが使われています");
        int points = LevelService.availableSkillPoints(data, GrowthRules.current());
        check(helper, points == 11, "レベル10のスキルポイントが11ではありません：" + points);
        check(helper, Math.abs(data.getHp() - data.getStats().maxHp()) < EPSILON, "職業を選んだ後のHPが満タンではありません");
        check(helper, Math.abs(data.getMp() - data.getStats().maxMp()) < EPSILON, "職業を選んだ後のMPが満タンではありません");

        // 何も持たないナイト：HP+20%・MP-10%・魔力-20%・防御+30%
        equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        expectStat(helper, data, StatType.HP, 672);
        expectStat(helper, data, StatType.MP, 171);
        expectStat(helper, data, StatType.ATTACK, 28);
        expectStat(helper, data, StatType.MAGIC, 22);
        expectStat(helper, data, StatType.DEFENSE, 18);
        // 片手剣と盾：攻撃+6、防御+4（防御は職業の+30%の前に足す）
        equip(player, data, new ItemStack(ModItems.APPRENTICE_SWORD.get()), new ItemStack(ModItems.APPRENTICE_SHIELD.get()));
        expectStat(helper, data, StatType.ATTACK, 34);
        expectStat(helper, data, StatType.DEFENSE, 23);
        // 両手剣：攻撃+10。もう片方の手の盾は効かない
        equip(player, data, new ItemStack(ModItems.APPRENTICE_GREATSWORD.get()), new ItemStack(ModItems.APPRENTICE_SHIELD.get()));
        expectStat(helper, data, StatType.ATTACK, 38);
        expectStat(helper, data, StatType.DEFENSE, 18);

        // ウィザードに変える：杖は渡す。やり直しのアイテムは2回目は渡さない
        player.getInventory().clearContent();
        JobService.setJobForced(player, data, wizard);
        expectCount(helper, player, ModItems.APPRENTICE_STAFF.get(), 1);
        expectCount(helper, player, ModItems.FORGET_WATER.get(), 0);
        expectRank(helper, data, "wizard/lamp_bolt", 1);
        expectRank(helper, data, "wizard/frost_circle", 1);
        expectRank(helper, data, "knight/call", 0);
        expectLoadout(helper, data, "wizard/lamp_bolt", "wizard/frost_circle", null, null, null, null);
        // 杖を持つウィザード：MP+50%・魔力+20%・HP-10%・防御-10%・攻撃-20%、杖は魔力+8・MP+20
        equip(player, data, new ItemStack(ModItems.APPRENTICE_STAFF.get()), ItemStack.EMPTY);
        expectStat(helper, data, StatType.HP, 504);
        expectStat(helper, data, StatType.MP, 315);
        expectStat(helper, data, StatType.ATTACK, 22);
        expectStat(helper, data, StatType.MAGIC, 43);
        expectStat(helper, data, StatType.DEFENSE, 12);

        // ナイトに戻す：見習い装備は2回目は渡さない
        player.getInventory().clearContent();
        JobService.setJobForced(player, data, knight);
        expectCount(helper, player, ModItems.APPRENTICE_SWORD.get(), 0);
        helper.succeed();
    }

    /** 3日目までは無料で変えられ、その後は「転職の証」が要る。 */
    @GameTest(template = EMPTY)
    public static void jobChangeCostsTokenAfterFreeDays(GameTestHelper helper) {
        FakePlayer player = fake(helper, "job_change");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 5);
        JobDef knight = job("knight");
        JobDef wizard = job("wizard");
        ProgressionData progression = ProgressionData.get(helper.getLevel().getServer());
        int originalDay = progression.day();
        try {
            progression.setDay(2);
            check(helper, JobService.changeJob(player, data, knight), "最初の職業を選べません");
            check(helper, data.getJobChanges() == 0, "最初の選択が転職の回数に入っています");
            check(helper, JobService.changeRule(player, data).free(), "2日目の転職が無料ではありません");
            check(helper, JobService.changeJob(player, data, wizard), "2日目に転職できません");
            check(helper, data.getJobChanges() == 1, "転職の回数が1ではありません：" + data.getJobChanges());
            check(helper, !JobService.changeJob(player, data, wizard), "同じ職業への転職が通りました");

            progression.setDay(ServerConfig.JOB_CHANGE_FREE_UNTIL_DAY.get() + 1);
            JobService.ChangeRule rule = JobService.changeRule(player, data);
            check(helper, !rule.free() && rule.tokensNeeded() == ServerConfig.JOB_CHANGE_TOKENS.get() && !rule.allowed(),
                    "無料の期間の後なのに、証なしで転職できる決まりになっています：" + rule);
            check(helper, !JobService.changeJob(player, data, knight), "証を持たずに転職できました");
            check(helper, wizard.id().equals(data.getCombatJob()), "転職できなかったのに職業が変わっています");

            player.getInventory().add(new ItemStack(ModItems.JOB_CHANGE_TOKEN.get(), 2));
            check(helper, JobService.changeJob(player, data, knight), "証を持っているのに転職できません");
            expectCount(helper, player, ModItems.JOB_CHANGE_TOKEN.get(), 2 - ServerConfig.JOB_CHANGE_TOKENS.get());
            check(helper, data.getJobChanges() == 2, "転職の回数が2ではありません：" + data.getJobChanges());
        } finally {
            progression.setDay(originalDay);
        }
        helper.succeed();
    }

    // ================================================================ 習得

    /** レベル・前提・上位職・ポイントの決まりどおりに覚えられ、やり直しと上位職を外すことができる。 */
    @GameTest(template = EMPTY)
    public static void learningFollowsTreeRules(GameTestHelper helper) {
        FakePlayer player = fake(helper, "learn_rules");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 1);
        JobService.setJobForced(player, data, job("knight"));

        expectBlocker(helper, data, "knight/shield_bond", SkillTreeService.Blocker.LEVEL);
        expectBlocker(helper, data, "knight/call", SkillTreeService.Blocker.POINTS);
        expectBlocker(helper, data, "wizard/lamp_bolt", SkillTreeService.Blocker.OTHER_JOB);

        // レベル5：ポイント5
        LevelService.setLevel(player, data, 5);
        expectPoints(helper, data, 5);
        expectBlocker(helper, data, "knight/rampart", SkillTreeService.Blocker.LEVEL);
        expectBlocker(helper, data, "knight/shield_bond", SkillTreeService.Blocker.NONE);
        learn(helper, player, data, "knight/shield_bond");
        expectRank(helper, data, "knight/shield_bond", 1);
        expectLoadout(helper, data, "knight/call", "knight/charge_slash", "knight/shield_bond", null, null, null);
        expectPoints(helper, data, 4);
        learn(helper, player, data, "knight/call");
        expectRank(helper, data, "knight/call", 2);
        expectPoints(helper, data, 2);
        // 第3段は上位職から
        expectBlocker(helper, data, "knight/call", SkillTreeService.Blocker.UPPER);
        check(helper, JobService.promote(player, data, true), "上位職に昇格できません");
        check(helper, data.isPromoted() && data.hasPromotedOnce(), "昇格が記録されていません");
        expectBlocker(helper, data, "knight/call", SkillTreeService.Blocker.POINTS);

        // レベル10：ポイントは11 − 使った3
        LevelService.setLevel(player, data, 10);
        expectPoints(helper, data, 8);
        learn(helper, player, data, "knight/call");
        expectRank(helper, data, "knight/call", 3);
        expectPoints(helper, data, 5);
        expectBlocker(helper, data, "knight/call", SkillTreeService.Blocker.MAX_RANK);
        expectBlocker(helper, data, "knight/unfallen_castle", SkillTreeService.Blocker.LEVEL);

        // レベル30：奥義は「城壁の構え」が前提
        LevelService.setLevel(player, data, 30);
        expectBlocker(helper, data, "knight/unfallen_castle", SkillTreeService.Blocker.REQUIREMENT);
        learn(helper, player, data, "knight/rampart");
        expectBlocker(helper, data, "knight/unfallen_castle", SkillTreeService.Blocker.NONE);
        int before = LevelService.availableSkillPoints(data, GrowthRules.current());
        learn(helper, player, data, "knight/unfallen_castle");
        expectPoints(helper, data, before - skill("knight/unfallen_castle").rankCost(1));
        expectLoadout(helper, data, "knight/call", "knight/charge_slash", "knight/shield_bond", "knight/rampart",
                "knight/unfallen_castle", null);

        // スキル枠：習得していないスキルは入れられない。入れ替えると前の枠は空く
        check(helper, !SkillTreeService.setLoadout(player, data, 5, id("knight/whirlwind")), "習得していないスキルが枠に入りました");
        check(helper, SkillTreeService.setLoadout(player, data, 5, id("knight/call")), "スキル枠に入れられません");
        expectLoadout(helper, data, null, "knight/charge_slash", "knight/shield_bond", "knight/rampart", "knight/unfallen_castle",
                "knight/call");

        // 上位職を外す：第3段は第2段に、奥義は消える
        JobService.demote(player, data);
        expectRank(helper, data, "knight/call", 2);
        expectRank(helper, data, "knight/unfallen_castle", 0);
        check(helper, data.loadoutSlotOf(id("knight/unfallen_castle")) < 0, "外した奥義がスキル枠に残っています");

        // 白紙の誓約書と同じやり直し：最初のスキルだけが1段目で残る
        SkillTreeService.resetSkills(player, data);
        check(helper, data.getLearnedSkills().size() == 2, "やり直した後のスキルが2つではありません：" + data.getLearnedSkills());
        expectLoadout(helper, data, "knight/call", "knight/charge_slash", null, null, null, null);
        expectPoints(helper, data, GrowthRules.current().totalSkillPoints(30));
        helper.succeed();
    }

    // ================================================================ 使う

    /** スキルを使うとMPが減り、クールダウンが始まる。MP不足・武器なし・空の枠は断られる。 */
    @GameTest(template = EMPTY)
    public static void skillUsesMpAndCooldown(GameTestHelper helper) {
        FakePlayer player = fake(helper, "skill_mp_cd");
        PlayerData data = data(helper, player);
        place(helper, player);
        LevelService.setLevel(player, data, 10);
        JobService.setJobForced(player, data, job("knight"));
        equip(player, data, new ItemStack(ModItems.APPRENTICE_SWORD.get()), new ItemStack(ModItems.APPRENTICE_SHIELD.get()));
        data.setMp(data.getStats().maxMp());
        SkillDef call = skill("knight/call");

        long now = helper.getLevel().getGameTime();
        double mpBefore = data.getMp();
        SkillService.Result first = SkillService.tryUse(player, data, 0, -1, null);
        check(helper, first.ok(), "呼び声を使えません：" + first.failure());
        check(helper, Math.abs(data.getMp() - (mpBefore - call.mpCost(1))) < EPSILON,
                "MPが " + call.mpCost(1) + " 減っていません：" + mpBefore + " → " + data.getMp());
        int cooldownTicks = SkillService.cooldownTicks(data, call, 1);
        check(helper, cooldownTicks == 240, "呼び声のクールダウンが12秒ではありません：" + cooldownTicks + "tick");
        SkillCooldown cooldown = data.getCooldown(call.id());
        check(helper, cooldown != null && cooldown.endTick() == now + cooldownTicks, "クールダウンが始まっていません：" + cooldown);

        double mpAfterFirst = data.getMp();
        expectFailure(helper, SkillService.tryUse(player, data, 0, -1, null), SkillService.Failure.COOLDOWN);
        check(helper, Math.abs(data.getMp() - mpAfterFirst) < EPSILON, "クールダウン中なのにMPが減りました");

        data.clearCooldowns();
        data.setMp(call.mpCost(1) - 1);
        expectFailure(helper, SkillService.tryUse(player, data, 0, -1, null), SkillService.Failure.NO_MP);

        data.setMp(data.getStats().maxMp());
        equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        expectFailure(helper, SkillService.tryUse(player, data, 0, -1, null), SkillService.Failure.NO_WEAPON);

        equip(player, data, new ItemStack(ModItems.APPRENTICE_SWORD.get()), ItemStack.EMPTY);
        expectFailure(helper, SkillService.tryUse(player, data, 5, -1, null), SkillService.Failure.EMPTY_SLOT);
        helper.succeed();
    }

    /** 詠唱のあるスキルは詠唱を始め（MPはまだ減らない）、同じキーでやめられる。詠唱短縮と連携支援で短くなる。 */
    @GameTest(template = EMPTY)
    public static void castStartsAndCancels(GameTestHelper helper) {
        FakePlayer player = fake(helper, "cast_cancel");
        PlayerData data = data(helper, player);
        place(helper, player);
        LevelService.setLevel(player, data, 10);
        JobService.setJobForced(player, data, job("wizard"));
        equip(player, data, new ItemStack(ModItems.APPRENTICE_STAFF.get()), ItemStack.EMPTY);
        SkillDef meteor = skill("wizard/meteor_shower");
        SkillTreeService.forceRank(player, data, meteor, 1);
        int slot = data.loadoutSlotOf(meteor.id());
        check(helper, slot == 2, "流星群が3番目の枠に入っていません：" + slot);

        long now = helper.getLevel().getGameTime();
        check(helper, SkillService.castTicks(data, meteor, 1, now) == 40, "流星群の詠唱が2秒ではありません");
        data.setMp(data.getStats().maxMp());
        double mpBefore = data.getMp();
        Vec3 aim = player.position().add(player.getLookAngle().scale(4.0));
        SkillService.Result started = SkillService.tryUse(player, data, slot, -1, aim);
        check(helper, started.ok(), "流星群の詠唱を始められません：" + started.failure());
        CastState casting = data.getCasting();
        check(helper, casting != null && meteor.id().equals(casting.skill()) && casting.endTick() == now + 40,
                "詠唱の記録が正しくありません：" + casting);
        check(helper, Math.abs(data.getMp() - mpBefore) < EPSILON, "詠唱を始めただけでMPが減りました");
        check(helper, player.hasEffect(ModEffects.CHANTING.get()), "詠唱中の効果（移動が遅くなる）が付いていません");

        expectFailure(helper, SkillService.tryUse(player, data, slot, -1, aim), SkillService.Failure.CANCELLED);
        check(helper, data.getCasting() == null, "詠唱をやめられません");
        check(helper, !player.hasEffect(ModEffects.CHANTING.get()), "詠唱をやめても、詠唱中の効果が残っています");
        check(helper, data.getCooldown(meteor.id()) == null, "詠唱をやめたのにクールダウンが始まりました");

        // 詠唱短縮（1段目：10%）
        SkillTreeService.forceRank(player, data, skill("wizard/swift_chant"), 1);
        check(helper, Math.abs(PassiveService.castMultiplier(data) - 0.9) < EPSILON, "詠唱短縮の倍率が0.9ではありません");
        check(helper, SkillService.castTicks(data, meteor, 1, now) == 36, "詠唱短縮で36tickになっていません");
        // 星落とし：連携支援を受けていると、詠唱がさらに半分
        SkillDef starfall = skill("wizard/starfall");
        check(helper, SkillService.castTicks(data, starfall, 1, now) == 72, "星落としの詠唱が72tickではありません");
        data.getBuffs().supportFrom = UUID.randomUUID();
        data.getBuffs().supportUntil = now + 80;
        check(helper, SkillService.castTicks(data, starfall, 1, now) == 36, "連携支援で星落としの詠唱が半分になっていません");
        data.getBuffs().consumeSupport();
        helper.succeed();
    }

    /** 連携支援を受けて使ったスキルは効果が上がり、支援はその1回で使い切る（城壁の構えで確かめる）。 */
    @GameTest(template = EMPTY)
    public static void supportBoostsNextSkillOnce(GameTestHelper helper) {
        FakePlayer player = fake(helper, "support_once");
        PlayerData data = data(helper, player);
        place(helper, player);
        LevelService.setLevel(player, data, 10);
        JobService.setJobForced(player, data, job("knight"));
        equip(player, data, new ItemStack(ModItems.APPRENTICE_SWORD.get()), new ItemStack(ModItems.APPRENTICE_SHIELD.get()));
        SkillDef rampart = skill("knight/rampart");
        SkillTreeService.forceRank(player, data, rampart, 1);
        int slot = data.loadoutSlotOf(rampart.id());
        data.setMp(data.getStats().maxMp());

        long now = helper.getLevel().getGameTime();
        data.getBuffs().supportFrom = UUID.randomUUID();
        data.getBuffs().supportUntil = now + 80;
        data.getBuffs().supportBonus = ServerConfig.SUPPORT_BONUS.get();
        SkillService.Result result = SkillService.tryUse(player, data, slot, -1, null);
        check(helper, result.ok(), "城壁の構えを使えません：" + result.failure());
        double boosted = 50.0 * (1.0 + ServerConfig.SUPPORT_BONUS.get() / 100.0);
        check(helper, data.getBuffs().rampart(now) && Math.abs(data.getBuffs().rampartReduction - boosted) < EPSILON,
                "連携支援つきの城壁の構えが " + boosted + "% ではありません：" + data.getBuffs().rampartReduction);
        check(helper, data.getBuffs().rampartUntil == now + 120, "城壁の構えが6秒ではありません");
        check(helper, !data.getBuffs().supported(now), "連携支援が使い切られていません");
        check(helper, player.hasEffect(ModEffects.RAMPART.get()), "城壁の構えのアイコン（効果）が付いていません");

        data.clearCooldowns();
        SkillService.Result again = SkillService.tryUse(player, data, slot, -1, null);
        check(helper, again.ok(), "2回目の城壁の構えを使えません：" + again.failure());
        check(helper, Math.abs(data.getBuffs().rampartReduction - 50.0) < EPSILON,
                "支援なしの城壁の構えが50%ではありません：" + data.getBuffs().rampartReduction);
        helper.succeed();
    }

    // ================================================================ ゲージ

    /** 誓気：最大値で止まり、足りなければ使えない。「誓気の循環」で使った分のMPが戻る。 */
    @GameTest(template = EMPTY)
    public static void oathGaugeAndRefund(GameTestHelper helper) {
        FakePlayer player = fake(helper, "oath_gauge");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        JobService.setJobForced(player, data, job("knight"));
        check(helper, GaugeService.max(data) == 100, "誓気の最大が100ではありません");

        GaugeService.add(data, 30);
        check(helper, Math.abs(data.getGauge() - 30) < EPSILON, "誓気が30になっていません：" + data.getGauge());
        GaugeService.add(data, 500);
        check(helper, Math.abs(data.getGauge() - 100) < EPSILON, "誓気が最大の100で止まっていません：" + data.getGauge());

        data.setGauge(40);
        data.setMp(100);
        check(helper, GaugeService.consume(player, data, 30), "誓気を30使えません");
        check(helper, Math.abs(data.getGauge() - 10) < EPSILON && Math.abs(data.getMp() - 100) < EPSILON,
                "誓気の循環なしで、誓気・MPが正しくありません");
        check(helper, !GaugeService.consume(player, data, 30), "誓気が足りないのに使えました");
        check(helper, Math.abs(data.getGauge() - 10) < EPSILON, "使えなかったのに誓気が減りました");

        // 誓気の循環：1段目50%、2段目60%
        SkillTreeService.forceRank(player, data, skill("knight/oath_cycle"), 1);
        data.setGauge(40);
        data.setMp(100);
        GaugeService.consume(player, data, 30);
        check(helper, Math.abs(data.getMp() - 115) < EPSILON, "誓気の循環（1段目）でMPが15戻っていません：" + data.getMp());
        SkillTreeService.forceRank(player, data, skill("knight/oath_cycle"), 2);
        data.setGauge(30);
        data.setMp(100);
        GaugeService.consume(player, data, 30);
        check(helper, Math.abs(data.getMp() - 118) < EPSILON, "誓気の循環（2段目）でMPが18戻っていません：" + data.getMp());

        // ウィザードには専用ゲージがない
        JobService.setJobForced(player, data, job("wizard"));
        GaugeService.add(data, 30);
        check(helper, data.getGauge() == 0, "ウィザードにゲージがたまりました");
        helper.succeed();
    }

    // ================================================================ ダメージ

    /** 訓練人形：決まった量の独自のダメージが、そのまま独自のHPから引かれる。防御・会心・ばらつきの計算。 */
    @GameTest(template = EMPTY)
    public static void dummyTakesExactSkillDamage(GameTestHelper helper) {
        FakePlayer player = fake(helper, "dummy_damage");
        place(helper, player);
        TrainingDummy dummy = helper.spawn(ModEntities.TRAINING_DUMMY.get(), new BlockPos(1, 1, 1));
        try {
            dummy.setDummyDefense(100);
            check(helper, Math.abs(TargetVitals.defense(dummy) - 100) < EPSILON, "訓練人形の防御が100ではありません");
            float before = dummy.getDummyHp();
            double dealt = CombatService.applyDamage(dummy, 150.0, false, ModDamageTypes.skill(helper.getLevel(), player, player));
            check(helper, Math.abs(dealt - 150.0) < EPSILON, "与えたダメージが150ではありません：" + dealt);
            check(helper, Math.abs(dummy.getDummyHp() - (before - 150.0F)) < 0.01F,
                    "訓練人形のHPが150減っていません：" + before + " → " + dummy.getDummyHp());
            check(helper, Math.abs(dummy.getHealth() - dummy.getMaxHealth()) < 0.01F, "訓練人形のバニラの体力が減っています");
            // 持ち主のいないダメージ（プレイヤー以外）は、訓練人形を傷つけない
            double none = CombatService.applyDamage(dummy, 150.0, false, ModDamageTypes.skill(helper.getLevel(), null, null));
            check(helper, none < 0, "プレイヤー以外のダメージで訓練人形が傷つきました");
        } finally {
            dummy.discard();
        }

        // 計算式：ダメージ ＝ 威力 × ばらつき × 会心 × 100 ÷（100 ＋ 防御）
        RandomSource random = RandomSource.create(20260417L);
        DamageCalculator.Result plain = DamageCalculator.compute(200, 0, 50, 100, 0, random);
        check(helper, plain.amount() == 100 && !plain.crit(), "威力200・防御100のダメージが100ではありません：" + plain);
        DamageCalculator.Result crit = DamageCalculator.compute(200, 100, 50, 100, 0, random);
        check(helper, crit.amount() == 150 && crit.crit(), "会心（会心ダメージ50%）のダメージが150ではありません：" + crit);
        for (int i = 0; i < 50; i++) {
            DamageCalculator.Result spread = DamageCalculator.compute(200, 0, 50, 0, 5, random);
            check(helper, spread.amount() >= 190 && spread.amount() <= 210, "±5%のばらつきを超えました：" + spread.amount());
        }
        check(helper, Math.abs(DamageCalculator.mitigation(0) - 1.0) < EPSILON
                && Math.abs(DamageCalculator.mitigation(300) - 0.25) < EPSILON, "防御の軽減の倍率が正しくありません");
        check(helper, DamageCalculator.compute(0.2, 0, 50, 0, 0, random).amount() == 1, "ダメージの最低が1ではありません");
        helper.succeed();
    }

    /** 魔物から受けるダメージ：防御で減り、城壁の構え（正面だけ）・不落の城で減り、不屈の誓いでHPが1残る。 */
    @GameTest(template = EMPTY)
    public static void incomingDamageReductions(GameTestHelper helper) {
        FakePlayer player = fake(helper, "damage_taken");
        PlayerData data = data(helper, player);
        place(helper, player);
        LevelService.setLevel(player, data, 10);
        JobService.setJobForced(player, data, job("knight"));
        equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        data.setHp(data.getStats().maxHp());

        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
        Zombie front = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie back = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        try {
            front.moveTo(player.getX() + flat.x * 1.5, player.getY(), player.getZ() + flat.z * 1.5, 0.0F, 0.0F);
            back.moveTo(player.getX() - flat.x * 1.5, player.getY(), player.getZ() - flat.z * 1.5, 0.0F, 0.0F);
            check(helper, CombatService.isInFront(player, front) && !CombatService.isInFront(player, back), "正面・背面の判定が逆です");

            double base = 4.0 * ServerConfig.VANILLA_MOB_DAMAGE_MULTIPLIER.get()
                    * DamageCalculator.mitigation(data.getStats().get(StatType.DEFENSE));
            expectTaken(helper, player, data, front, 4.0F, base, "強化なし");

            long now = helper.getLevel().getGameTime();
            SkillExecutor.applyBuff(player, BuffKind.RAMPART, 100, 50.0, now, null);
            data.setGauge(0);
            expectTaken(helper, player, data, front, 4.0F, base * 0.5, "城壁の構え（正面）");
            double gain = base * 0.5 / data.getStats().maxHp() * 100.0 * 2.0;
            check(helper, Math.abs(data.getGauge() - gain) < 0.01, "城壁の構えの間、誓気が2倍たまっていません：" + data.getGauge() + "（" + gain + "）");
            expectTaken(helper, player, data, back, 4.0F, base, "城壁の構え（背面は減らない）");

            SkillExecutor.applyBuff(player, BuffKind.FORTRESS, 100, 40.0, now, null);
            expectTaken(helper, player, data, back, 4.0F, base * 0.6, "不落の城");
            // 弱い不落の城を重ねても、強い方の割合が残る
            SkillExecutor.applyBuff(player, BuffKind.FORTRESS, 100, 10.0, now, null);
            check(helper, Math.abs(data.getBuffs().fortressReduction - 40.0) < EPSILON, "不落の城の重ねがけで割合が下がりました");

            SkillExecutor.applyBuff(player, BuffKind.UNYIELDING, 100, 0.0, now, null);
            data.setHp(50);
            expectTaken(helper, player, data, back, 1000.0F, 49.0, "不屈の誓い（HPが1残る）");
        } finally {
            front.discard();
            back.discard();
        }
        helper.succeed();
    }

    // ================================================================ 連携

    /** 連携：起点 → 別の人の追撃（4秒以内）で連携、3人目で三連携。同じ人・時間切れ・起点なしは連携にならない。 */
    @GameTest(template = EMPTY)
    public static void linkComboWindow(GameTestHelper helper) {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        long window = LinkComboTracker.windowTicks();
        check(helper, window == 80, "連携の時間が4秒ではありません：" + window);
        double link = 1.0 + ServerConfig.LINK_BONUS.get() / 100.0;
        double link3 = 1.0 + ServerConfig.LINK3_BONUS.get() / 100.0;

        UUID target = UUID.randomUUID();
        long t = 1000;
        expectLink(helper, LinkComboTracker.onSkillHit(target, a, LinkType.STARTER, t), LinkComboTracker.Outcome.STARTED, 1.0);
        expectLink(helper, LinkComboTracker.onSkillHit(target, a, LinkType.FINISHER, t + 10), LinkComboTracker.Outcome.NONE, 1.0);
        LinkComboTracker.Result two = LinkComboTracker.onSkillHit(target, b, LinkType.FINISHER, t + 40);
        expectLink(helper, two, LinkComboTracker.Outcome.LINK, link);
        check(helper, two.players().equals(List.of(a, b)), "連携した人の列が正しくありません：" + two.players());
        // つなぐたびに時間は数え直す（最初から100tickでも、前の連携から60tickなら間に合う）
        LinkComboTracker.Result three = LinkComboTracker.onSkillHit(target, c, LinkType.FINISHER, t + 100);
        expectLink(helper, three, LinkComboTracker.Outcome.LINK3, link3);
        check(helper, three.players().size() == 3, "三連携の列が3人ではありません");
        expectLink(helper, LinkComboTracker.onSkillHit(target, b, LinkType.FINISHER, t + 110), LinkComboTracker.Outcome.NONE, 1.0);

        UUID late = UUID.randomUUID();
        LinkComboTracker.onSkillHit(late, a, LinkType.STARTER, 2000);
        expectLink(helper, LinkComboTracker.onSkillHit(late, b, LinkType.FINISHER, 2000 + window + 1),
                LinkComboTracker.Outcome.NONE, 1.0);
        expectLink(helper, LinkComboTracker.onSkillHit(UUID.randomUUID(), b, LinkType.FINISHER, 3000),
                LinkComboTracker.Outcome.NONE, 1.0);
        expectLink(helper, LinkComboTracker.onSkillHit(target, c, LinkType.SUPPORT, t + 120), LinkComboTracker.Outcome.NONE, 1.0);
        helper.succeed();
    }

    // ================================================================ 保存

    /** 職業・上位職・習得・スキル枠・クールダウンが、保存と読み直し、死んで復活しても残る。 */
    @GameTest(template = EMPTY)
    public static void jobAndSkillsSurviveSaveAndRespawn(GameTestHelper helper) {
        FakePlayer oldBody = fake(helper, "jobsave_old");
        PlayerData data = data(helper, oldBody);
        LevelService.setLevel(oldBody, data, 10);
        JobService.setJobForced(oldBody, data, job("knight"));
        learn(helper, oldBody, data, "knight/shield_bond");
        learn(helper, oldBody, data, "knight/call");
        JobService.promote(oldBody, data, true);
        data.countJobChange();
        long now = helper.getLevel().getGameTime();
        data.setCooldown(id("knight/call"), new SkillCooldown(now + 200, 240));
        data.setGauge(50);

        CompoundTag saved = data.save();
        PlayerData loaded = new PlayerData();
        loaded.load(saved);
        expectSameJob(helper, data, loaded, "読み直した");
        SkillCooldown cooldown = loaded.getCooldown(id("knight/call"));
        check(helper, cooldown != null && cooldown.endTick() == now + 200 && cooldown.totalTicks() == 240,
                "読み直したクールダウンが残っていません：" + cooldown);
        check(helper, loaded.getGauge() == 0, "読み直したゲージが0ではありません（ゲージは保存しない）");

        FakePlayer newBody = fake(helper, "jobsave_new");
        oldBody.invalidateCaps();
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(newBody, oldBody, true));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(newBody, false));
        PlayerData after = data(helper, newBody);
        expectSameJob(helper, data, after, "復活後の");
        check(helper, after.getCooldown(id("knight/call")) != null, "復活後にクールダウンが消えました");
        check(helper, after.getGauge() == 0 && after.getCasting() == null, "復活後にゲージ・詠唱が残っています");
        helper.succeed();
    }

    // ================================================================ 定義

    /**
     * 画面の文字：英語の設定の人にも日本語が出るよう、en_us.json は ja_jp.json と同じ中身にしている。
     * 片方にだけ足すと、英語の設定の人には「combat.amemaru.frozen」のような名前がそのまま出てしまうため、ずれを見つける。
     */
    @GameTest(template = EMPTY)
    public static void languageFilesMatch(GameTestHelper helper) {
        JsonObject japanese = readLanguage(helper, "ja_jp");
        JsonObject english = readLanguage(helper, "en_us");
        List<String> missing = new ArrayList<>();
        List<String> different = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : japanese.entrySet()) {
            JsonElement other = english.get(entry.getKey());
            if (other == null) {
                missing.add(entry.getKey());
            } else if (!other.equals(entry.getValue())) {
                different.add(entry.getKey());
            }
        }
        List<String> extra = new ArrayList<>();
        for (String key : english.keySet()) {
            if (!japanese.has(key)) {
                extra.add(key);
            }
        }
        check(helper, missing.isEmpty(), "en_us.json にない文字があります：" + missing);
        check(helper, different.isEmpty(), "en_us.json と ja_jp.json で中身が違う文字があります：" + different);
        check(helper, extra.isEmpty(), "en_us.json にだけある文字があります：" + extra);
        helper.succeed();
    }

    private static JsonObject readLanguage(GameTestHelper helper, String code) {
        String path = "/assets/" + AmemaruMod.MOD_ID + "/lang/" + code + ".json";
        try (InputStream stream = JobSkillGameTests.class.getResourceAsStream(path)) {
            check(helper, stream != null, path + " が見つかりません");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new GameTestAssertException(path + " を読めません：" + e.getMessage());
        }
    }

    /** 同梱の職業とスキルの定義に問題がなく、決めた数がそろっている。 */
    @GameTest(template = EMPTY)
    public static void skillDefinitionsAreValid(GameTestHelper helper) {
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        check(helper, snapshot.problems().isEmpty(), "職業・スキルの定義に問題があります：" + snapshot.problems());
        for (String name : new String[]{"knight", "wizard"}) {
            JobDef job = job(name);
            List<SkillDef> skills = snapshot.skillsOf(job.id());
            long actives = skills.stream().filter(skill -> skill.isActive() && !skill.isUltimate()).count();
            long passives = skills.stream().filter(skill -> skill.kind() == SkillKind.PASSIVE).count();
            long ultimates = skills.stream().filter(SkillDef::isUltimate).count();
            // 仕様S2は4〜6個。11月17日のご指示（スキルが単調）で、流派ごとに6つ（計12）に増やした（Phase 0-A 19章）
            check(helper, actives == 12, name + " のアクティブスキルが12個ではありません：" + actives);
            check(helper, passives == 6, name + " のパッシブスキルが6個ではありません：" + passives);
            // 奥義は流派ごとに1つ（計2つ）から1つを選ぶ（11月17日のご指示。Phase 0-A 19章）
            check(helper, ultimates == 2, name + " の奥義が2つではありません：" + ultimates);
            check(helper, job.startingSkills().size() == 2, name + " の最初のスキルが2つではありません");
            check(helper, job.upper() != null, name + " に上位職がありません");
            for (ResourceLocation start : job.startingSkills()) {
                SkillDef skill = snapshot.skill(start);
                check(helper, skill != null && skill.isActive() && !skill.isUltimate() && skill.minLevel() <= 1,
                        name + " の最初のスキルが、レベル1で使えるアクティブスキルではありません：" + start);
            }
            for (SkillDef skill : skills) {
                if (skill.isActive()) {
                    check(helper, !skill.animation().isEmpty(), skill.id() + " のモーションの名前がありません");
                    check(helper, skill.cooldown(1) > 0, skill.id() + " のクールダウンが0です");
                    check(helper, skill.mpCost(1) > 0 || skill.gaugeCost(1) > 0 || skill.id().getPath().endsWith("mana_thread"),
                            skill.id() + " の消費がありません");
                }
            }
        }
        helper.succeed();
    }

    // ================================================================ 共通

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "amj_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-job:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, profileName));
    }

    private static PlayerData data(GameTestHelper helper, ServerPlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
    }

    /** 構造物の真ん中に、南を向いて立たせる。 */
    private static void place(GameTestHelper helper, ServerPlayer player) {
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
    }

    /** 手に持つ物を決めて、ステータスを計算し直す（偽のプレイヤーは毎 tick の持ちかえの検知が動かないため）。 */
    private static void equip(ServerPlayer player, PlayerData data, ItemStack main, ItemStack off) {
        player.setItemSlot(EquipmentSlot.MAINHAND, main);
        player.setItemSlot(EquipmentSlot.OFFHAND, off);
        LevelService.recompute(player, data);
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

    private static void learn(GameTestHelper helper, ServerPlayer player, PlayerData data, String path) {
        SkillDef skill = skill(path);
        SkillTreeService.Blocker blocker = SkillTreeService.canLearn(data, skill);
        check(helper, SkillTreeService.learn(player, data, skill.id()), path + " を覚えられません：" + blocker);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    private static void expectCount(GameTestHelper helper, ServerPlayer player, Item item, int expected) {
        int actual = player.getInventory().countItem(item);
        check(helper, actual == expected, item + " の数が " + expected + " ではありません：" + actual);
    }

    private static void expectRank(GameTestHelper helper, PlayerData data, String path, int expected) {
        int actual = data.getSkillRank(id(path));
        check(helper, actual == expected, path + " の段階が " + expected + " ではありません：" + actual);
    }

    private static void expectLoadout(GameTestHelper helper, PlayerData data, String... paths) {
        for (int slot = 0; slot < PlayerData.LOADOUT_SIZE; slot++) {
            ResourceLocation expected = slot < paths.length && paths[slot] != null ? id(paths[slot]) : null;
            ResourceLocation actual = data.getLoadout(slot);
            check(helper, expected == null ? actual == null : expected.equals(actual),
                    "スキル枠" + (slot + 1) + " が " + expected + " ではありません：" + actual);
        }
    }

    private static void expectPoints(GameTestHelper helper, PlayerData data, int expected) {
        int actual = LevelService.availableSkillPoints(data, GrowthRules.current());
        check(helper, actual == expected, "残りのスキルポイントが " + expected + " ではありません：" + actual);
    }

    private static void expectBlocker(GameTestHelper helper, PlayerData data, String path, SkillTreeService.Blocker expected) {
        SkillTreeService.Blocker actual = SkillTreeService.canLearn(data, skill(path));
        check(helper, actual == expected, path + " を覚えられるかの判定が " + expected + " ではありません：" + actual);
    }

    private static void expectStat(GameTestHelper helper, PlayerData data, StatType type, double expected) {
        double actual = data.getStats().get(type);
        check(helper, Math.abs(actual - expected) < EPSILON, type.id() + " が " + expected + " ではありません：" + actual);
    }

    private static void expectFailure(GameTestHelper helper, SkillService.Result result, SkillService.Failure expected) {
        check(helper, result.failure() == expected, "スキルが " + expected + " で断られていません：" + result.failure());
    }

    private static void expectLink(GameTestHelper helper, LinkComboTracker.Result result, LinkComboTracker.Outcome outcome,
                                   double multiplier) {
        check(helper, result.outcome() == outcome && Math.abs(result.multiplier() - multiplier) < EPSILON,
                "連携の判定が " + outcome + "（×" + multiplier + "）ではありません：" + result);
    }

    /** 魔物の攻撃（バニラの量）を受けたときに引かれる、独自のHPの量。 */
    private static void expectTaken(GameTestHelper helper, ServerPlayer player, PlayerData data, LivingEntity attacker,
                                    float vanillaAmount, double expected, String label) {
        LivingDamageEvent event = new LivingDamageEvent(player, player.damageSources().mobAttack(attacker), vanillaAmount);
        CombatService.onLivingDamage(event);
        double custom = event.getAmount() / player.getMaxHealth() * data.getStats().maxHp();
        check(helper, Math.abs(custom - expected) < 0.01, label + "：受けるダメージが " + expected + " ではありません：" + custom);
    }

    private static void expectSameJob(GameTestHelper helper, PlayerData expected, PlayerData actual, String label) {
        check(helper, expected.getCombatJob().equals(actual.getCombatJob()), label + "職業が違います");
        check(helper, actual.isPromoted() && actual.hasPromotedOnce(), label + "上位職が外れています");
        check(helper, expected.getLearnedSkills().equals(actual.getLearnedSkills()),
                label + "習得が違います：" + actual.getLearnedSkills());
        for (int slot = 0; slot < PlayerData.LOADOUT_SIZE; slot++) {
            ResourceLocation a = expected.getLoadout(slot);
            ResourceLocation b = actual.getLoadout(slot);
            check(helper, a == null ? b == null : a.equals(b), label + "スキル枠" + (slot + 1) + "が違います");
        }
        check(helper, actual.hasReceivedKit(expected.getCombatJob()) && actual.hasFreeResetItems(), label + "受け取りの記録が消えました");
        check(helper, actual.getJobChanges() == expected.getJobChanges(), label + "転職の回数が違います");
    }
}
