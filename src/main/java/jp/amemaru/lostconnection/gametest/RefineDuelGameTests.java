package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.accessory.AccessoryRefine;
import jp.amemaru.lostconnection.accessory.AccessoryRoller;
import jp.amemaru.lostconnection.accessory.AccessoryRules;
import jp.amemaru.lostconnection.accessory.RefineService;
import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.combat.CombatTargets;
import jp.amemaru.lostconnection.core.registry.ModBlocks;
import jp.amemaru.lostconnection.core.registry.ModDamageTypes;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearService;
import jp.amemaru.lostconnection.guild.GuildCounters;
import jp.amemaru.lostconnection.guild.GuildData;
import jp.amemaru.lostconnection.guild.GuildHooks;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.SkillRegistry;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.social.Commission;
import jp.amemaru.lostconnection.social.CommissionBoard;
import jp.amemaru.lostconnection.social.CommissionData;
import jp.amemaru.lostconnection.social.DuelService;
import jp.amemaru.lostconnection.workshop.WorkshopService;
import jp.amemaru.lostconnection.workshop.WorkshopType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 6 その4：アクセサリーの厳選（振り直し・固定・振り直すほど高くなる・強化・分解）、装飾師への厳選の依頼（依頼台）、決闘。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RefineDuelGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;

    private RefineDuelGameTests() {
    }

    // ================================================================ 厳選の決まり

    /** 決まり：表のとおりの費用・固定・上乗せ・強化・分解・値引き。素材の品がある。 */
    @GameTest(template = EMPTY)
    public static void refineDefinitionsLoad(GameTestHelper helper) {
        AccessoryRules.Refine refine = AccessoryRules.current().refine();
        check(helper, refine.rerollValues().knots() == 2000 && refine.rerollTypes().knots() == 4000, "振り直しのノットが2,000・4,000ではありません");
        check(helper, refine.rerollValues().items().size() == 1 && refine.rerollValues().items().get(0).count() == 3,
                "数値の振り直しの素材が残響の欠片×3ではありません");
        check(helper, refine.rerollTypes().items().size() == 2, "種類ごとの振り直しの素材が、残響の欠片と揺らぎの結晶ではありません");
        check(helper, refine.lockCounts()[0] == 1 && refine.lockCounts()[1] == 2 && refine.lockCounts()[2] == 4
                && Math.abs(refine.lockKnots()[2] - 4.0) < EPSILON, "固定の留め糸が1・2・4、ノットが×4ではありません");
        check(helper, refine.escalationStep() == 10 && refine.escalationMax() == 200, "振り直すほどの上乗せが+10%・最大+200%ではありません");
        check(helper, refine.enhanceKnots() == 500 && Math.abs(refine.mainPercent() - 5.0) < EPSILON && refine.isMilestone(3)
                && refine.isMilestone(12) && !refine.isMilestone(4), "強化の決まり（500ノット・+5%・+3/6/9/12）が違います");
        check(helper, AccessoryRefine.discountPercent(refine, 0) == 0 && AccessoryRefine.discountPercent(refine, 5) == 10
                && AccessoryRefine.discountPercent(refine, 15) == 20, "装飾師の値引き（熟練度1ごとに-2%・最大-20%）が違います");
        for (String name : new String[]{"echo_fragment", "polishing_powder", "binding_thread", "wavering_crystal", "clear_echo"}) {
            item(name);
        }
        helper.succeed();
    }

    /** 振り直し：数値だけ（種類は変わらない）・種類ごと（固定は変わらない、重ならない）・固定の数と費用・振り直すほど高くなる。 */
    @GameTest(template = EMPTY)
    public static void refineRerollAndLocks(GameTestHelper helper) {
        AccessoryRules rules = AccessoryRules.current();
        RandomSource random = RandomSource.create(2026L);
        ItemStack stack = ring(rules, random, 4);
        int day = 5;
        AccessoryRefine.Cost plain = AccessoryRefine.rerollCost(stack, rules, false, day, 1.0, 0);
        check(helper, plain.knots() == 800 && plain.items().size() == 1, "第1章の数値の振り直しが800ノット・残響の欠片×3ではありません：" + plain);
        check(helper, AccessoryRefine.rerollCost(stack, rules, true, day, 1.0, 0).knots() == 1600, "第1章の種類ごとの振り直しが1,600ノットではありません");
        check(helper, AccessoryRefine.rerollCost(stack, rules, false, day, 1.0, 10).knots() == 720, "値引き10%が効いていません");
        // 固定：3つまで（サブ4つ）
        check(helper, AccessoryRefine.toggleLock(stack, rules, 0) == AccessoryRefine.LockResult.LOCKED, "1つ目を固定できません");
        AccessoryRefine.Cost locked = AccessoryRefine.rerollCost(stack, rules, false, day, 1.0, 0);
        check(helper, locked.knots() == 1200 && locked.items().size() == 2 && locked.items().get(1).count() == 1,
                "固定1つの費用が1,200ノット・留め糸×1ではありません：" + locked);
        AccessoryRefine.toggleLock(stack, rules, 1);
        AccessoryRefine.toggleLock(stack, rules, 2);
        check(helper, AccessoryData.lockedCount(stack) == 3, "3つ固定できません");
        check(helper, AccessoryRefine.toggleLock(stack, rules, 3) == AccessoryRefine.LockResult.TOO_MANY, "4つ目まで固定できました");
        AccessoryRefine.Cost three = AccessoryRefine.rerollCost(stack, rules, false, day, 1.0, 0);
        check(helper, three.knots() == 3200 && three.items().get(1).count() == 4, "固定3つの費用が3,200ノット・留め糸×4ではありません：" + three);
        AccessoryRefine.toggleLock(stack, rules, 1);
        AccessoryRefine.toggleLock(stack, rules, 2);
        // 数値だけ：種類はそのまま、固定した行は値も変わらない
        List<AccessoryData.StatLine> before = AccessoryData.subs(stack);
        for (int i = 0; i < 30; i++) {
            AccessoryRefine.rerollValues(stack, rules, random);
            List<AccessoryData.StatLine> after = AccessoryData.subs(stack);
            check(helper, after.size() == before.size(), "数値の振り直しでサブの数が変わりました");
            for (int j = 0; j < after.size(); j++) {
                check(helper, after.get(j).type() == before.get(j).type(), "数値の振り直しで種類が変わりました");
                check(helper, after.get(j).value() >= after.get(j).min() - EPSILON && after.get(j).value() <= after.get(j).max() + EPSILON,
                        "振り直した値が幅の外です");
            }
            check(helper, Math.abs(after.get(0).value() - before.get(0).value()) < EPSILON && after.get(0).locked(), "固定した行の値が変わりました");
        }
        check(helper, AccessoryData.rerolls(stack) == 30, "再抽選の回数が数えられていません");
        // 種類ごと：固定はそのまま、ほかはメイン・固定・互いに重ならない
        StatType mainType = AccessoryData.main(stack).type();
        for (int i = 0; i < 30; i++) {
            AccessoryRefine.rerollTypes(stack, rules, random);
            List<AccessoryData.StatLine> after = AccessoryData.subs(stack);
            check(helper, after.size() == 4 && after.get(0).type() == before.get(0).type(), "種類ごとの振り直しで固定が変わったか、数が変わりました");
            Set<StatType> seen = EnumSet.of(mainType);
            for (AccessoryData.StatLine line : after) {
                check(helper, seen.add(line.type()), "種類ごとの振り直しで、サブが重なりました：" + line.type());
            }
        }
        // 振り直すほど高くなる：+10%ずつ、最大+200%、日が変わると半分
        ItemStack fresh = ring(rules, random, 3);
        for (int i = 0; i < 3; i++) {
            AccessoryData.addCostUp(fresh, day, 10, 200);
        }
        check(helper, AccessoryData.costUp(fresh, day) == 30, "3回振り直して+30%になりません");
        check(helper, AccessoryRefine.rerollCost(fresh, rules, false, day, 1.0, 0).knots() == 1040, "+30%の費用が1,040ノットではありません");
        check(helper, AccessoryData.costUp(fresh, day + 1) == 15 && AccessoryData.costUp(fresh, day + 2) == 7, "日が変わっても半分に戻りません");
        for (int i = 0; i < 40; i++) {
            AccessoryData.addCostUp(fresh, day, 10, 200);
        }
        check(helper, AccessoryData.costUp(fresh, day) == 200, "上乗せが+200%で止まりません");
        helper.succeed();
    }

    /** 自分で振り直す：素材とノットを払い、厳選の回数を数える。足りなければできない。 */
    @GameTest(template = EMPTY)
    public static void refinePaysAndCounts(GameTestHelper helper) {
        FakePlayer player = fake(helper, "refiner");
        PlayerData data = knight(player, 10);
        AccessoryRules rules = AccessoryRules.current();
        ItemStack stack = ring(rules, RandomSource.create(7L), 2);
        Wallet.set(player, data, 1000, "gametest");
        give(player, "echo_fragment", 3);
        long rerolls = data.getGuild().counter(GuildCounters.REROLLS);
        check(helper, RefineService.reroll(player, stack, false) == RefineService.Outcome.DONE, "振り直せません");
        check(helper, data.getKnots() == 200 && count(player, "echo_fragment") == 0, "費用（800ノット・残響の欠片×3）が払われていません："
                + data.getKnots());
        check(helper, data.getGuild().counter(GuildCounters.REROLLS) == rerolls + 1, "厳選の回数が数えられていません");
        check(helper, AccessoryData.costUp(stack, ProgressionData.currentDay(player.server)) == 10, "振り直した品に+10%が残りません");
        check(helper, RefineService.reroll(player, stack, false) == RefineService.Outcome.NEED_MATERIALS, "素材がないのに振り直せました");
        give(player, "echo_fragment", 3);
        check(helper, RefineService.reroll(player, stack, false) == RefineService.Outcome.NEED_KNOTS, "ノットが足りないのに振り直せました");
        helper.succeed();
    }

    /** 強化：+1ずつ失敗なし。メインが+5%ずつ。+3でサブが増え、4つあれば+12でどれかが1回分伸びる。費用は研磨粉×段階・500×段階×章。 */
    @GameTest(template = EMPTY)
    public static void refineEnhanceMilestones(GameTestHelper helper) {
        AccessoryRules rules = AccessoryRules.current();
        RandomSource random = RandomSource.create(99L);
        ItemStack stack = ring(rules, random, 2);
        AccessoryRefine.Cost first = AccessoryRefine.enhanceCost(stack, rules, 1.0, 0);
        check(helper, first != null && first.knots() == 200 && first.items().get(0).count() == 1, "+1の費用が200ノット・研磨粉×1ではありません");
        AccessoryData.StatLine main = AccessoryData.main(stack);
        for (int level = 1; level <= 3; level++) {
            AccessoryRefine.EnhanceResult result = AccessoryRefine.enhance(stack, rules, random);
            check(helper, result != null && result.level() == level, "強化できません：+" + level);
        }
        check(helper, AccessoryData.subs(stack).size() == 3, "+3でサブが増えません");
        double expected = Math.round(main.value() * 1.15);
        check(helper, Math.abs(AccessoryData.effectiveMain(stack, main) - expected) < EPSILON, "+3のメインが+15%になりません："
                + AccessoryData.effectiveMain(stack, main) + "／" + expected);
        for (int level = 4; level <= 12; level++) {
            AccessoryRefine.enhance(stack, rules, random);
        }
        check(helper, AccessoryData.enhance(stack) == 12 && AccessoryRefine.enhanceCost(stack, rules, 1.0, 0) == null
                && AccessoryRefine.enhance(stack, rules, random) == null, "+12で止まりません");
        List<AccessoryData.StatLine> subs = AccessoryData.subs(stack);
        int rolls = 0;
        for (AccessoryData.StatLine line : subs) {
            rolls += line.rolls();
            check(helper, line.value() >= line.min() - EPSILON && line.value() <= line.max() + EPSILON, "伸びたサブが幅の外です");
        }
        // サブ2つから：+3・+6 で増えて4つ、+9・+12 で2回伸びる
        check(helper, subs.size() == 4 && rolls == 6, "+12までのサブの数・伸びた回数が違います：" + subs.size() + "・" + rolls);
        ItemStack twelve = stack.copy();
        AccessoryData.setEnhance(twelve, 11);
        AccessoryRefine.Cost last = AccessoryRefine.enhanceCost(twelve, rules, 1.0, 0);
        check(helper, last != null && last.knots() == 2400 && last.items().get(0).count() == 12, "+12の費用が2,400ノット・研磨粉×12ではありません");
        helper.succeed();
    }

    /** 分解：★と強化段階が高いほど多く戻り、★4以上は澄んだ残響も出る。 */
    @GameTest(template = EMPTY)
    public static void refineDismantleYield(GameTestHelper helper) {
        AccessoryRules rules = AccessoryRules.current();
        RandomSource random = RandomSource.create(5L);
        ItemStack low = ring(rules, random, 1);
        AccessoryRoller.forceRatio(low, rules, 0.5);
        List<ItemStack> lowYield = AccessoryRefine.dismantleYield(low, rules);
        check(helper, total(lowYield, "echo_fragment") == 1 + AccessoryData.quality(low, rules) && total(lowYield, "clear_echo") == 0,
                "★の低い品の分解が違います：" + lowYield);
        ItemStack perfect = ring(rules, random, 4);
        AccessoryRoller.forceRatio(perfect, rules, 1.0);
        AccessoryData.setEnhance(perfect, 6);
        List<ItemStack> top = AccessoryRefine.dismantleYield(perfect, rules);
        check(helper, total(top, "echo_fragment") == 1 + 5 + 3 && total(top, "polishing_powder") == 10 && total(top, "clear_echo") == 3,
                "完璧・+6の分解（残響の欠片9・研磨粉10・澄んだ残響3）が違います：" + top);
        helper.succeed();
    }

    // ================================================================ 依頼台

    /** 依頼：預ける（品・素材・ノット）→ 装飾師が受けて厳選 → 回数の上限で終わり、品と残りは持ち主へ、手数料は装飾師へ。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void commissionFlow(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos bench = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(bench, ModBlocks.ACCESSORY_WORKBENCH.get().defaultBlockState());
        FakePlayer owner = fake(helper, "cm_owner");
        FakePlayer crafter = fake(helper, "cm_crafter");
        PlayerData ownerData = knight(owner, 10);
        PlayerData crafterData = knight(crafter, 10);
        for (ServerPlayer player : List.of(owner, crafter)) {
            player.setPos(bench.getX() + 0.5, bench.getY() + 1.0, bench.getZ() + 1.5);
            CommissionBoard.setBenchForTest(player.getUUID(), bench);
        }
        check(helper, WorkshopService.chooseJob(crafter, WorkshopType.ACCESSORY) && CommissionBoard.isCrafter(crafter), "装飾師になれません");
        AccessoryRules rules = AccessoryRules.current();
        ItemStack ring = ring(rules, RandomSource.create(31L), 3);
        owner.getInventory().items.set(0, ring);
        give(owner, "echo_fragment", 9);
        give(owner, "binding_thread", 3);
        Wallet.set(owner, ownerData, 5000, "gametest");
        CompoundTag args = new CompoundTag();
        args.putInt("slot", 0);
        args.putString("kind", "VALUES");
        args.put("locks", new IntArrayTag(new int[]{0}));
        args.putString("target", "NONE");
        args.putInt("attempts", 3);
        args.putLong("fee", 500);
        check(helper, CommissionBoard.create(owner, args), "依頼を出せません");
        // 預かり：1,200＋1,320＋1,440（固定1つ・+10%ずつ）＝3,960 と手数料500
        check(helper, owner.getInventory().items.get(0).isEmpty() && count(owner, "echo_fragment") == 0 && count(owner, "binding_thread") == 0
                && ownerData.getKnots() == 5000 - 3960 - 500, "品・素材・ノット（3,960＋500）を預かっていません：" + ownerData.getKnots());
        Commission c = null;
        for (Commission one : CommissionData.get(server).all()) {
            if (one.owner().equals(owner.getUUID()) && one.state() == Commission.State.OPEN) {
                c = one;
            }
        }
        check(helper, c != null && c.escrowKnots() == 3960 && c.maxAttempts() == 3, "依頼の預かりが違います");
        int id = c.id();
        check(helper, !CommissionBoard.accept(owner, id), "自分の依頼を受けられました");
        check(helper, CommissionBoard.accept(crafter, id) && c.state() == Commission.State.WORKING, "装飾師が受けられません");
        long rerolls = crafterData.getGuild().counter(GuildCounters.REROLLS);
        check(helper, CommissionBoard.attempt(crafter, id, 5), "依頼で厳選できません");
        check(helper, c.state() == Commission.State.DONE && c.attempts() == 3 && c.endReason() == Commission.EndReason.LIMIT,
                "回数の上限で終わりません：" + c.state() + "・" + c.attempts());
        check(helper, crafterData.getGuild().counter(GuildCounters.REROLLS) == rerolls + 3, "装飾師の厳選の回数が数えられていません");
        // 熟練度1（-2%）：1,176＋1,294＋1,411＝3,881。残り79は持ち主へ
        CommissionData.Payout ownerPayout = CommissionData.get(server).peekPayout(owner.getUUID());
        check(helper, ownerPayout != null && ownerPayout.knots() == 3960 - 3881 && ownerPayout.items().size() == 1
                && AccessoryData.subs(ownerPayout.items().get(0)).get(0).locked(), "持ち主への戻り（品と79ノット）が違います："
                + (ownerPayout == null ? "なし" : ownerPayout.knots()));
        CommissionData.Payout crafterPayout = CommissionData.get(server).peekPayout(crafter.getUUID());
        check(helper, crafterPayout != null && crafterPayout.knots() == 500, "手数料が装飾師に渡りません");
        CommissionBoard.claimPayout(owner);
        check(helper, ownerData.getKnots() == 5000 - 3881 - 500 && count(owner, "moss_iron_battle_ring") == 1, "戻りを受け取れません："
                + ownerData.getKnots());
        GuildHooks.applyPending(crafter, crafterData);
        GuildData.Connection link = crafterData.getGuild().connection(owner.getUUID());
        check(helper, crafterData.getGuild().counter(GuildCounters.HELPED) >= 1 && link != null && link.commissions() >= 1,
                "依頼実績か縁帳が残りません");
        // 受付中のうちに取り下げると、手数料も戻る
        owner.getInventory().items.set(1, ring(rules, RandomSource.create(32L), 2));
        give(owner, "echo_fragment", 3);
        Wallet.set(owner, ownerData, 5000, "gametest");
        long knots = ownerData.getKnots();
        CompoundTag second = args.copy();
        second.putInt("slot", 1);
        second.putIntArray("locks", new int[0]);
        second.putInt("attempts", 1);
        second.putLong("fee", 100);
        check(helper, CommissionBoard.create(owner, second), "2つ目の依頼を出せません");
        Commission open = null;
        for (Commission one : CommissionData.get(server).all()) {
            if (one.owner().equals(owner.getUUID()) && one.state() == Commission.State.OPEN) {
                open = one;
            }
        }
        check(helper, open != null && CommissionBoard.stop(owner, open.id()) && open.endReason() == Commission.EndReason.CANCELLED,
                "受付中の依頼を取り下げられません");
        CommissionBoard.claimPayout(owner);
        check(helper, ownerData.getKnots() == knots && count(owner, "echo_fragment") == 3, "取り下げで、ノット・手数料・素材が戻りません");
        helper.succeed();
    }

    // ================================================================ 決闘

    /** 決闘：相手どうしだけ攻撃が届き、ほかの人とは届かない。耐久が減らない。倒れる一撃はHPを1残して決着（ダウンしない）。 */
    @GameTest(template = EMPTY)
    public static void duelRules(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer a = fake(helper, "duel_a");
        FakePlayer b = fake(helper, "duel_b");
        FakePlayer c = fake(helper, "duel_c");
        knight(a, 10);
        PlayerData bData = knight(b, 10);
        knight(c, 10);
        DuelService.setTestMode(true);
        DuelService.testPlayers(a, b, c);
        try {
            check(helper, DuelService.challenge(a, b), "申し込めません");
            check(helper, DuelService.accept(b, a.getUUID()), "申し込みを受けられません");
            DuelService.Duel duel = DuelService.duelForTest(a);
            check(helper, duel != null && duel.phase() == DuelService.Phase.COUNTDOWN, "決闘が始まりません");
            check(helper, !DuelService.areOpponents(a, b), "数えている間に攻撃が届きます");
            check(helper, Math.abs(bData.getHp() - bData.getStats().maxHp()) < EPSILON, "決闘の始めにHPが満タンになりません");
            DuelService.finishForTest(server, duel);
            check(helper, DuelService.duelForTest(a) == null, "決闘が片づきません");

            duel = DuelService.startForTest(a, b);
            check(helper, DuelService.areOpponents(a, b) && DuelService.areOpponents(b, a), "相手どうしになりません");
            check(helper, CombatTargets.isEnemyOf(a, b) && !CombatTargets.isEnemyOf(c, b) && !CombatTargets.isEnemy(b),
                    "決闘の相手だけが敵になっていません");
            check(helper, !CombatTargets.isAlly(c, b, false) && !CombatTargets.isAlly(b, c, false), "決闘している人に、回復・強化が届きます");
            check(helper, !b.hurt(helper.getLevel().damageSources().playerAttack(c), 4.0F), "決闘していない人の攻撃が届きました");
            // 耐久が減らない
            EquipmentDef swordDef = EquipmentDefs.server().get(AmemaruMod.id("ancient_stone_sword"));
            ItemStack sword = new ItemStack(item("ancient_stone_sword"));
            a.setItemSlot(EquipmentSlot.MAINHAND, sword);
            int durability = GearData.durability(sword, swordDef);
            GearService.wearWeapon(a, false);
            check(helper, GearData.durability(a.getMainHandItem(), swordDef) == durability, "決闘の間に武器の耐久が減りました");
            // 倒れるほどの一撃：HPを1残して決着
            double dealt = -1;
            for (int i = 0; i < 40 && dealt < 0 && duel.phase() == DuelService.Phase.FIGHT; i++) {
                dealt = CombatService.applyDamage(b, bData.getStats().maxHp() * 10.0, false, ModDamageTypes.skill(helper.getLevel(), a, a));
            }
            check(helper, duel.phase() == DuelService.Phase.END && a.getUUID().equals(duel.winner()), "倒れる一撃で決着しません："
                    + duel.phase());
            check(helper, b.isAlive() && !DownHandler.isDown(b), "決闘で負けた人が倒れました");
            DuelService.finishForTest(server, duel);
            check(helper, !DuelService.isolated(a) && !DuelService.isolated(b), "決闘のあとも、決闘している扱いのままです");
            check(helper, Math.abs(bData.getHp() - bData.getStats().maxHp()) < EPSILON, "決闘の前のHP（満タン）に戻りません");
            check(helper, bData.getGuild().counter("duel_losses") >= 1 && data(a).getGuild().counter("duel_wins") >= 1, "戦績が残りません");
        } finally {
            DuelService.setTestMode(false);
        }
        helper.succeed();
    }

    // ================================================================ 道具

    /** 第1章の闘環（サブの数を決めて抽選）。 */
    private static ItemStack ring(AccessoryRules rules, RandomSource random, int subs) {
        ItemStack stack = new ItemStack(item("moss_iron_battle_ring"));
        AccessoryDef def = AccessoryDefs.get(item("moss_iron_battle_ring"));
        if (def == null) {
            throw new GameTestAssertException("アクセサリーの定義が見つかりません");
        }
        AccessoryRoller.roll(stack, def, rules, random, false, null, subs);
        return stack;
    }

    private static int total(List<ItemStack> stacks, String path) {
        int count = 0;
        Item item = item(path);
        for (ItemStack stack : stacks) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(AmemaruMod.id(path));
        if (item == null || !AmemaruMod.id(path).equals(ForgeRegistries.ITEMS.getKey(item))) {
            throw new GameTestAssertException("アイテムが見つかりません：" + path);
        }
        return item;
    }

    private static void give(ServerPlayer player, String path, int count) {
        player.getInventory().add(new ItemStack(item(path), count));
    }

    private static int count(ServerPlayer player, String path) {
        return WorkshopService.count(player, item(path));
    }

    private static PlayerData knight(ServerPlayer player, int level) {
        PlayerData data = data(player);
        LevelService.setLevel(player, data, level);
        JobDef job = SkillRegistry.get().job(AmemaruMod.id("knight"));
        if (job == null) {
            throw new GameTestAssertException("職業が見つかりません：knight");
        }
        JobService.setJobForced(player, data, job);
        player.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        LevelService.recompute(player, data);
        data.setHp(data.getStats().maxHp());
        data.setMp(data.getStats().maxMp());
        return data;
    }

    private static PlayerData data(ServerPlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "amt_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, profileName));
    }

}
