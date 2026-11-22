package jp.amemaru.lostconnection.accessory;

import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * アクセサリーの厳選（Phase 6 その4。Phase 0-A 21章「厳選の操作とコスト」）。費用の計算と、品への書き込みだけを行う
 * （払う・知らせる・数えるは {@link RefineService}）。乱数はサーバーだけで使う。
 *
 * <ul>
 *   <li>数値だけ振り直す：サブの種類はそのまま、数値だけ引き直す（残響の欠片×3・2,000ノット）</li>
 *   <li>種類ごと振り直す：サブの種類と数値を引き直す。数は変わらない（残響の欠片×6・揺らぎの結晶×1・4,000ノット）</li>
 *   <li>固定：選んだサブを固定して、残りだけ振り直す（留め糸×1／2／4。ノットが×1.5／×2.5／×4）</li>
 *   <li>強化：+1段（最大+12）。失敗なし。研磨粉×段階の数・500ノット×段階の数。1段ごとにメイン+5%。+3・+6・+9・+12 で、
 *       サブが4つ未満なら新しいサブが一つ加わり、4つあればランダムな一つが「1回分」伸びる</li>
 *   <li>分解：素材に戻す。★と強化段階が高いほど多く戻り、★4以上は澄んだ残響も出る</li>
 *   <li>振り直すほど高くなる：同じアクセサリーを振り直すたびに +10%（最大 +200%）。日付が変わると、増えた分が半分に戻る</li>
 * </ul>
 *
 * <p>ノットは第3章の目安なので、章（世代）の倍率をかける【補完】。振り直すほどの上乗せ・固定の倍率・装飾師の値引きはノットに
 * かけ、素材の数は変えない【補完】。config の倍率（{@code refine.costMultiplier}）は、素材とノットの両方にかける。</p>
 */
public final class AccessoryRefine {
    private AccessoryRefine() {
    }

    /** 厳選の操作。 */
    public enum Op {
        REROLL_VALUES, REROLL_TYPES, ENHANCE, DISMANTLE
    }

    /** 費用（素材とノット）。 */
    public record Cost(long knots, List<EquipmentDef.Cost> items) {
    }

    /** 強化でサブに起きたこと。 */
    public record EnhanceResult(int level, @Nullable StatType added, @Nullable StatType grown) {
    }

    // ---------------------------------------------------------------- 費用

    /** 装飾師の熟練度の値引き（%）。熟練度1ごとに −2%、最大 −20%（Phase 0-A 21章）。 */
    public static int discountPercent(AccessoryRules.Refine refine, int mastery) {
        if (mastery <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(refine.discountMax(), mastery * refine.discountPerMastery()));
    }

    /** 振り直しの費用（今の固定の数と、振り直すほどの上乗せを入れて）。 */
    public static Cost rerollCost(ItemStack stack, AccessoryRules rules, boolean types, int today, double configMultiplier,
                                  int discountPercent) {
        AccessoryRules.Refine refine = rules.refine();
        AccessoryRules.OpCost base = types ? refine.rerollTypes() : refine.rerollValues();
        double knots = base.knots() * refine.knotsFactor(AccessoryData.generation(stack));
        List<EquipmentDef.Cost> items = scaled(base.items(), configMultiplier);
        int locks = Math.min(AccessoryData.lockedCount(stack), refine.maxLocks());
        if (locks > 0) {
            knots *= refine.lockKnots()[locks - 1];
            items = with(items, new EquipmentDef.Cost(refine.lockItem(), scaleCount(refine.lockCounts()[locks - 1], configMultiplier)));
        }
        knots *= 1.0 + AccessoryData.costUp(stack, today) / 100.0;
        return new Cost(knots(knots, configMultiplier, discountPercent), items);
    }

    /** 強化の費用（次の段に上げるとき。もう上げられなければ null）。 */
    @Nullable
    public static Cost enhanceCost(ItemStack stack, AccessoryRules rules, double configMultiplier, int discountPercent) {
        int target = AccessoryData.enhance(stack) + 1;
        if (target > rules.maxEnhance()) {
            return null;
        }
        AccessoryRules.Refine refine = rules.refine();
        double knots = (double) refine.enhanceKnots() * target * refine.knotsFactor(AccessoryData.generation(stack));
        int count = scaleCount(refine.enhanceItems() * target, configMultiplier);
        List<EquipmentDef.Cost> items = count > 0 ? List.of(new EquipmentDef.Cost(refine.enhanceItem(), count)) : List.of();
        return new Cost(knots(knots, configMultiplier, discountPercent), items);
    }

    /** 分解で戻る素材（★と強化段階が高いほど多い。★4以上は澄んだ残響も）。 */
    public static List<ItemStack> dismantleYield(ItemStack stack, AccessoryRules rules) {
        AccessoryRules.Refine refine = rules.refine();
        int stars = Math.max(0, Math.min(5, AccessoryData.quality(stack, rules)));
        int enhance = AccessoryData.enhance(stack);
        List<ItemStack> list = new ArrayList<>();
        int echo = refine.dismantleEchoBase() + refine.dismantleEchoPerStar() * stars
                + (refine.dismantleEnhancePerEcho() > 0 ? enhance / refine.dismantleEnhancePerEcho() : 0);
        add(list, refine.echoItem(), echo);
        long spent = (long) refine.enhanceItems() * enhance * (enhance + 1) / 2;
        add(list, refine.enhanceItem(), (int) (spent * refine.dismantlePowderPercent() / 100));
        int clear = refine.dismantleClearEcho()[stars] + (AccessoryData.isPerfect(stack, rules) ? refine.dismantleClearPerfect() : 0);
        if (refine.dismantleClearEcho()[stars] <= 0) {
            clear = 0;
        }
        add(list, refine.clearEchoItem(), clear);
        return list;
    }

    private static void add(List<ItemStack> list, ResourceLocation id, int count) {
        if (count <= 0) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return;
        }
        while (count > 0) {
            int size = Math.min(count, item.getMaxStackSize());
            list.add(new ItemStack(item, size));
            count -= size;
        }
    }

    private static long knots(double raw, double configMultiplier, int discountPercent) {
        double value = raw * Math.max(0.0, configMultiplier) * (1.0 - Math.max(0, Math.min(100, discountPercent)) / 100.0);
        return Math.max(0L, Math.round(value));
    }

    private static List<EquipmentDef.Cost> scaled(List<EquipmentDef.Cost> costs, double multiplier) {
        List<EquipmentDef.Cost> list = new ArrayList<>();
        for (EquipmentDef.Cost cost : costs) {
            int count = scaleCount(cost.count(), multiplier);
            if (count > 0) {
                list.add(new EquipmentDef.Cost(cost.item(), count));
            }
        }
        return list;
    }

    private static List<EquipmentDef.Cost> with(List<EquipmentDef.Cost> costs, EquipmentDef.Cost extra) {
        List<EquipmentDef.Cost> list = new ArrayList<>(costs);
        if (extra.count() > 0) {
            list.add(extra);
        }
        return list;
    }

    /** 素材の数に config の倍率をかける（切り上げ。もとが1以上なら1以上）。 */
    static int scaleCount(int count, double multiplier) {
        if (count <= 0 || multiplier <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(count * multiplier - 1.0E-6));
    }

    // ---------------------------------------------------------------- 振り直す

    /** 振り直せるか（抽選済みで、固定していないサブが1つ以上ある）。 */
    public static boolean canReroll(ItemStack stack) {
        if (!AccessoryData.isRolled(stack)) {
            return false;
        }
        for (AccessoryData.StatLine sub : AccessoryData.subs(stack)) {
            if (!sub.locked()) {
                return true;
            }
        }
        return false;
    }

    /** 数値だけ振り直す（固定したサブはそのまま）。 */
    public static void rerollValues(ItemStack stack, AccessoryRules rules, RandomSource random) {
        AccessoryData.StatLine main = AccessoryData.main(stack);
        if (main == null) {
            return;
        }
        List<AccessoryData.StatLine> out = new ArrayList<>();
        for (AccessoryData.StatLine sub : AccessoryData.subs(stack)) {
            out.add(sub.locked() ? sub : sub.withValue(AccessoryRoller.rerollValue(sub, random)));
        }
        AccessoryData.replaceLines(stack, main, out);
        afterReroll(stack, rules);
    }

    /**
     * 種類ごと振り直す（数は変わらない。固定したサブはそのまま）。新しい種類は、メインと固定したサブと、ほかの新しいサブに重ならない。
     * 強化で「1回分」伸びていた枠は、その回数分のまま新しい種類になる【補完】。
     */
    public static void rerollTypes(ItemStack stack, AccessoryRules rules, RandomSource random) {
        AccessoryData.StatLine main = AccessoryData.main(stack);
        if (main == null) {
            return;
        }
        List<AccessoryData.StatLine> subs = AccessoryData.subs(stack);
        List<StatType> used = new ArrayList<>();
        used.add(main.type());
        for (AccessoryData.StatLine sub : subs) {
            if (sub.locked()) {
                used.add(sub.type());
            }
        }
        int generation = AccessoryData.generation(stack);
        List<AccessoryData.StatLine> out = new ArrayList<>();
        for (AccessoryData.StatLine sub : subs) {
            if (sub.locked()) {
                out.add(sub);
                continue;
            }
            StatType type = AccessoryRoller.pickSubType(rules, used, random);
            if (type == null) {
                out.add(sub);
                continue;
            }
            used.add(type);
            out.add(AccessoryRoller.rollSub(type, rules, generation, sub.rolls(), random));
        }
        AccessoryData.replaceLines(stack, main, out);
        afterReroll(stack, rules);
    }

    private static void afterReroll(ItemStack stack, AccessoryRules rules) {
        AccessoryData.setRerolls(stack, AccessoryData.rerolls(stack) + 1);
        AccessoryData.storeQuality(stack, AccessoryData.quality(stack, rules), AccessoryData.isPerfect(stack, rules));
    }

    // ---------------------------------------------------------------- 固定

    /** 固定できる数（表のとおり3つまで。サブが4つ未満なら、サブの数−1まで。振り直すサブが残るように）。 */
    public static int maxLocks(ItemStack stack, AccessoryRules rules) {
        int subs = AccessoryData.subs(stack).size();
        return Math.max(0, Math.min(rules.refine().maxLocks(), subs - 1));
    }

    /** 固定の結果。 */
    public enum LockResult {
        LOCKED, UNLOCKED, TOO_MANY, NO_SUB
    }

    /** 何番目のサブの固定を切りかえる（固定は品に残り、振り直すたびに留め糸を使う）。 */
    public static LockResult toggleLock(ItemStack stack, AccessoryRules rules, int index) {
        AccessoryData.StatLine main = AccessoryData.main(stack);
        List<AccessoryData.StatLine> subs = new ArrayList<>(AccessoryData.subs(stack));
        if (main == null || index < 0 || index >= subs.size()) {
            return LockResult.NO_SUB;
        }
        AccessoryData.StatLine line = subs.get(index);
        if (!line.locked() && AccessoryData.lockedCount(stack) >= maxLocks(stack, rules)) {
            return LockResult.TOO_MANY;
        }
        subs.set(index, line.withLocked(!line.locked()));
        AccessoryData.replaceLines(stack, main, subs);
        return line.locked() ? LockResult.UNLOCKED : LockResult.LOCKED;
    }

    // ---------------------------------------------------------------- 強化

    /** 1段強化する（失敗なし）。+3・+6・+9・+12 でサブが増える・伸びる。もう上げられなければ null。 */
    @Nullable
    public static EnhanceResult enhance(ItemStack stack, AccessoryRules rules, RandomSource random) {
        int level = AccessoryData.enhance(stack) + 1;
        AccessoryData.StatLine main = AccessoryData.main(stack);
        if (main == null || level > rules.maxEnhance()) {
            return null;
        }
        AccessoryData.setEnhance(stack, level);
        AccessoryData.setMainPercent(stack, rules.refine().mainPercent());
        StatType added = null;
        StatType grown = null;
        if (rules.refine().isMilestone(level)) {
            List<AccessoryData.StatLine> subs = new ArrayList<>(AccessoryData.subs(stack));
            if (subs.size() < 4) {
                List<StatType> used = new ArrayList<>();
                used.add(main.type());
                for (AccessoryData.StatLine sub : subs) {
                    used.add(sub.type());
                }
                StatType type = AccessoryRoller.pickSubType(rules, used, random);
                if (type != null) {
                    subs.add(AccessoryRoller.rollSub(type, rules, AccessoryData.generation(stack), 1, random));
                    added = type;
                }
            } else {
                int index = random.nextInt(subs.size());
                AccessoryData.StatLine line = subs.get(index);
                subs.set(index, AccessoryRoller.growOnce(line, random));
                grown = line.type();
            }
            AccessoryData.replaceLines(stack, main, subs);
        }
        AccessoryData.storeQuality(stack, AccessoryData.quality(stack, rules), AccessoryData.isPerfect(stack, rules));
        return new EnhanceResult(level, added, grown);
    }
}
