package jp.amemaru.lostconnection.accessory;

import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * アクセサリーの抽選（Phase 0-A 21章）。乱数はサーバーだけで使う。
 *
 * <ol>
 *   <li>メイン：定義の候補から1つ（お守り・留め具はランダム、指輪は素材で決まる）。数値は「その章の上限値 × 70〜100%」</li>
 *   <li>サブの数：1個30%・2個40%・3個25%・4個5%（大成功なら一つ多い。最大4）</li>
 *   <li>サブの種類：出やすさの重みで選ぶ。メインと同じ種類は出ない。サブ同士も重ならない</li>
 *   <li>サブの数値：「1回分の上限 × 章の倍率」の半分〜上限</li>
 * </ol>
 *
 * <p>%で表すステータスは小数1けた、ほかは整数に丸める（上限が1より小さくなるときは1）。</p>
 */
public final class AccessoryRoller {
    private AccessoryRoller() {
    }

    /**
     * 抽選して書き込む。
     *
     * @param great   装飾師の「大成功」（サブが一つ多くつく）
     * @param crafter 作った人（null なら作り手を書かない）
     * @param subCount サブの数を決めて抽選する（管理者・テスト用。0以下なら抽選で決める）
     */
    public static void roll(ItemStack stack, AccessoryDef def, AccessoryRules rules, RandomSource random, boolean great,
                            @Nullable Player crafter, int subCount) {
        StatType mainType = def.main().get(random.nextInt(def.main().size()));
        AccessoryData.StatLine main = rollLine(mainType, rules.mainCap(mainType, def.generation()), rules.mainMinRatio(), random);

        int count = subCount > 0 ? Math.min(4, subCount) : pickCount(rules.subCountWeights(), random) + (great ? 1 : 0);
        count = Math.min(4, Math.max(1, count));
        List<AccessoryData.StatLine> subs = new ArrayList<>();
        List<StatType> used = new ArrayList<>();
        used.add(mainType);
        for (int i = 0; i < count; i++) {
            StatType type = pickSub(rules, used, random);
            if (type == null) {
                break;
            }
            used.add(type);
            subs.add(rollLine(type, rules.subCap(type, def.generation()), rules.subMinRatio(), random));
        }
        AccessoryData.write(stack, def.slot(), def.generation(), def.rarity(), main, subs, great,
                crafter == null ? null : crafter.getGameProfile().getName(), crafter == null ? null : crafter.getUUID());
        AccessoryData.storeQuality(stack, AccessoryData.quality(stack, rules), AccessoryData.isPerfect(stack, rules));
    }

    /** すべての行を、決まった割合にする（管理者・テスト用。割合は0〜1）。 */
    public static void forceRatio(ItemStack stack, AccessoryRules rules, double ratio) {
        AccessoryData.StatLine main = AccessoryData.main(stack);
        if (main == null) {
            return;
        }
        List<AccessoryData.StatLine> subs = new ArrayList<>();
        for (AccessoryData.StatLine sub : AccessoryData.subs(stack)) {
            subs.add(withRatio(sub, ratio));
        }
        AccessorySlot slot = stack.getItem() instanceof AccessoryItem item ? item.slot() : AccessorySlot.RING;
        AccessoryData.write(stack, slot, AccessoryData.generation(stack),
                AccessoryData.rarity(stack, jp.amemaru.lostconnection.equipment.Rarity.UNCOMMON), withRatio(main, ratio), subs,
                AccessoryData.isGreat(stack), null, null);
        AccessoryData.storeQuality(stack, AccessoryData.quality(stack, rules), AccessoryData.isPerfect(stack, rules));
    }

    private static AccessoryData.StatLine withRatio(AccessoryData.StatLine line, double ratio) {
        double value = round(line.type(), Math.max(line.min(), Math.min(line.max(), line.max() * ratio)));
        return new AccessoryData.StatLine(line.type(), value, line.min(), line.max(), line.locked());
    }

    private static AccessoryData.StatLine rollLine(StatType type, double cap, double minRatio, RandomSource random) {
        double max = roundCap(type, cap);
        double min = Math.min(max, roundCap(type, max * minRatio));
        return new AccessoryData.StatLine(type, draw(type, min, max, random), min, max, false);
    }

    /** 下限〜上限から1つ引く（%は小数1けた、ほかは整数）。 */
    private static double draw(StatType type, double min, double max, RandomSource random) {
        if (type.isPercent()) {
            int lo = (int) Math.round(min * 10.0);
            int hi = (int) Math.round(max * 10.0);
            return (lo + random.nextInt(Math.max(1, hi - lo + 1))) / 10.0;
        }
        int lo = (int) Math.round(min);
        int hi = (int) Math.round(max);
        return lo + random.nextInt(Math.max(1, hi - lo + 1));
    }

    // ---------------------------------------------------------------- 厳選（Phase 6 その4）

    /**
     * サブの1行を新しく引く（{@code rolls} 回分。下限と上限も {@code rolls} 倍）。厳選の「種類ごと振り直す」と、強化でサブが増えるとき。
     */
    public static AccessoryData.StatLine rollSub(StatType type, AccessoryRules rules, int generation, int rolls, RandomSource random) {
        int count = Math.max(1, rolls);
        double max = roundCap(type, rules.subCap(type, generation));
        double min = Math.min(max, roundCap(type, max * rules.subMinRatio()));
        double value = 0.0;
        for (int i = 0; i < count; i++) {
            value += draw(type, min, max, random);
        }
        return new AccessoryData.StatLine(type, round(type, value), round(type, min * count), round(type, max * count), false, count);
    }

    /** 同じ種類・同じ回数分のまま、数値だけ引き直す（「数値だけ振り直す」）。 */
    public static double rerollValue(AccessoryData.StatLine line, RandomSource random) {
        int count = Math.max(1, line.rolls());
        double min = line.minPerRoll();
        double max = line.maxPerRoll();
        double value = 0.0;
        for (int i = 0; i < count; i++) {
            value += draw(line.type(), min, max, random);
        }
        return round(line.type(), value);
    }

    /** 「1回分」伸ばす（強化の +3・+6・+9・+12 で、サブが4つあるとき）。 */
    public static AccessoryData.StatLine growOnce(AccessoryData.StatLine line, RandomSource random) {
        double min = line.minPerRoll();
        double max = line.maxPerRoll();
        double add = draw(line.type(), min, max, random);
        return new AccessoryData.StatLine(line.type(), round(line.type(), line.value() + add), round(line.type(), line.min() + min),
                round(line.type(), line.max() + max), line.locked(), line.rolls() + 1);
    }

    /** サブの種類を出やすさの重みで選ぶ（{@code used} にある種類は出ない。選べなければ null）。 */
    @Nullable
    public static StatType pickSubType(AccessoryRules rules, List<StatType> used, RandomSource random) {
        return pickSub(rules, used, random);
    }

    /** 上限と下限の丸め（%は小数1けたで0.1以上、ほかは整数で1以上）。 */
    private static double roundCap(StatType type, double raw) {
        if (type.isPercent()) {
            return Math.max(0.1, Math.round(raw * 10.0) / 10.0);
        }
        return Math.max(1.0, Math.round(raw));
    }

    private static double round(StatType type, double raw) {
        return type.isPercent() ? Math.round(raw * 10.0) / 10.0 : Math.round(raw);
    }

    private static int pickCount(int[] weights, RandomSource random) {
        int total = 0;
        for (int weight : weights) {
            total += Math.max(0, weight);
        }
        if (total <= 0) {
            return 1;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= Math.max(0, weights[i]);
            if (roll < 0) {
                return i + 1;
            }
        }
        return 1;
    }

    @Nullable
    private static StatType pickSub(AccessoryRules rules, List<StatType> used, RandomSource random) {
        int total = 0;
        for (Map.Entry<StatType, AccessoryRules.SubEntry> entry : rules.subs().entrySet()) {
            if (!used.contains(entry.getKey())) {
                total += Math.max(0, entry.getValue().weight());
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Map.Entry<StatType, AccessoryRules.SubEntry> entry : rules.subs().entrySet()) {
            if (used.contains(entry.getKey())) {
                continue;
            }
            roll -= Math.max(0, entry.getValue().weight());
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return null;
    }
}
