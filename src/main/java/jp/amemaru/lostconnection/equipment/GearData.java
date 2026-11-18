package jp.amemaru.lostconnection.equipment;

import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * 装備の一つひとつの品のデータ（アイテムのNBT {@code AmemaruGear}。Phase 0-B 4.3）。
 *
 * <ul>
 *   <li>{@code rarity}：レアリティ</li>
 *   <li>{@code gen}：世代</li>
 *   <li>{@code roll}：ステータスごとの抽選の割合（0〜1）。数値そのものではなく割合を持つので、
 *       データパックで定義の数値を変えると、もう持っている品にも効く</li>
 *   <li>{@code enhance}：強化値（+0〜+10）</li>
 *   <li>{@code great}：工房の「大成功」で作られた品か</li>
 *   <li>{@code crafter}・{@code crafterId}：作り手（名前とUUID。説明文の「〇〇作」）</li>
 *   <li>{@code dur}：今の耐久（ないときは最大）</li>
 *   <li>{@code rareDrop}：レアドロップで手に入れた品か</li>
 * </ul>
 *
 * <p>取引の決まりはアイテムのNBTの一番上に置く（{@code AmemaruBound}：取引できない、{@code AmemaruBindOnEquip}：
 * 装備すると取引できなくなる）。Phase 2の見習い装備と同じ印で、Phase 6のフリマ・取引で見る。</p>
 */
public final class GearData {
    public static final String TAG = "AmemaruGear";
    /** 取引できない（Phase 2の見習い装備と同じ印）。 */
    public static final String BOUND = "AmemaruBound";
    /** 一度装備すると取引できなくなる（ボスのレア装備など）。 */
    public static final String BIND_ON_EQUIP = "AmemaruBindOnEquip";

    private GearData() {
    }

    // ---------------------------------------------------------------- 抽選

    /** もう抽選が済んでいるか。 */
    public static boolean isRolled(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.contains("rarity");
    }

    /**
     * ステータスを抽選して書き込む（前の抽選の結果・強化・作り手は消える。耐久は最大に戻る）。
     *
     * @param rarity  レアリティ（null なら定義のレアリティ）
     * @param great   工房の「大成功」（割合を一段上げる）
     * @param crafter 作り手（null なら書かない）
     */
    public static void roll(ItemStack stack, EquipmentDef def, GearRules rules, RandomSource random, @Nullable Rarity rarity,
                            boolean great, @Nullable Player crafter) {
        CompoundTag tag = new CompoundTag();
        tag.putString("rarity", (rarity == null ? def.rarity() : rarity).id());
        tag.putInt("gen", def.generation());
        CompoundTag roll = new CompoundTag();
        for (StatType type : def.stats().keySet()) {
            double ratio;
            if (def.fixed()) {
                ratio = 1.0;
            } else {
                ratio = rules.rollMin() + random.nextDouble() * (rules.rollMax() - rules.rollMin());
                if (great) {
                    ratio = Math.min(rules.rollMax(), ratio + rules.greatSuccessBonus());
                }
            }
            roll.putFloat(type.id(), (float) ratio);
        }
        tag.put("roll", roll);
        tag.putInt("enhance", 0);
        if (great) {
            tag.putBoolean("great", true);
        }
        if (crafter != null) {
            tag.putString("crafter", crafter.getGameProfile().getName());
            tag.putUUID("crafterId", crafter.getUUID());
        }
        stack.getOrCreateTag().put(TAG, tag);
        applyBinding(stack, def);
    }

    /** まだ抽選していなければ抽選する（コマンドで出した品・古い品など）。 */
    public static boolean ensureRolled(ItemStack stack, EquipmentDef def, GearRules rules, RandomSource random) {
        if (isRolled(stack)) {
            return false;
        }
        roll(stack, def, rules, random, null, false, null);
        return true;
    }

    /** 抽選の割合を、すべて決まった値にする（管理者のコマンドとテスト用）。 */
    public static void setAllRatios(ItemStack stack, EquipmentDef def, double ratio) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        CompoundTag roll = new CompoundTag();
        for (StatType type : def.stats().keySet()) {
            roll.putFloat(type.id(), (float) (def.fixed() ? 1.0 : Math.max(0.0, Math.min(1.0, ratio))));
        }
        tag.put("roll", roll);
    }

    /** 定義の取引の決まりを、品に印としてつける。 */
    public static void applyBinding(ItemStack stack, EquipmentDef def) {
        CompoundTag root = stack.getOrCreateTag();
        if (def.binding() == EquipmentDef.Binding.BOUND) {
            root.putBoolean(BOUND, true);
        } else if (def.binding() == EquipmentDef.Binding.ON_EQUIP && !root.getBoolean(BOUND)) {
            root.putBoolean(BIND_ON_EQUIP, true);
        }
    }

    // ---------------------------------------------------------------- 読み取り

    /** レアリティ（抽選していなければ定義のレアリティ）。 */
    public static Rarity rarity(ItemStack stack, EquipmentDef def) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null || !tag.contains("rarity")) {
            return def.rarity();
        }
        Rarity rarity = Rarity.byId(tag.getString("rarity"));
        return rarity == null ? def.rarity() : rarity;
    }

    public static void setRarity(ItemStack stack, Rarity rarity) {
        stack.getOrCreateTagElement(TAG).putString("rarity", rarity.id());
    }

    public static int enhance(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? 0 : Math.max(0, tag.getInt("enhance"));
    }

    public static void setEnhance(ItemStack stack, int enhance) {
        stack.getOrCreateTagElement(TAG).putInt("enhance", Math.max(0, enhance));
    }

    /** そのステータスの抽選の割合（0〜1）。抽選していなければ、幅の真ん中。 */
    public static double ratio(ItemStack stack, StatType type, EquipmentDef def, GearRules rules) {
        if (def.fixed()) {
            return 1.0;
        }
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null || !tag.getCompound("roll").contains(type.id())) {
            return (rules.rollMin() + rules.rollMax()) / 2.0;
        }
        return Math.max(0.0, Math.min(1.0, tag.getCompound("roll").getFloat(type.id())));
    }

    /** そのステータスの今の値（レアリティ・抽選・強化の倍率をかけたもの）。 */
    public static double value(ItemStack stack, StatType type, EquipmentDef def, GearRules rules) {
        double base = def.stat(type);
        if (base <= 0) {
            return 0.0;
        }
        double raw = base * rules.rarityMultiplier(rarity(stack, def)) * ratio(stack, type, def, rules)
                * rules.enhanceMultiplier(enhance(stack));
        return round(type, raw);
    }

    /** 幅の下限（抽選が一番悪いとき）。 */
    public static double minValue(ItemStack stack, StatType type, EquipmentDef def, GearRules rules) {
        double ratio = def.fixed() ? 1.0 : rules.rollMin();
        return round(type, def.stat(type) * rules.rarityMultiplier(rarity(stack, def)) * ratio
                * rules.enhanceMultiplier(enhance(stack)));
    }

    /** 幅の上限（抽選が一番良いとき）。 */
    public static double maxValue(ItemStack stack, StatType type, EquipmentDef def, GearRules rules) {
        double ratio = def.fixed() ? 1.0 : rules.rollMax();
        return round(type, def.stat(type) * rules.rarityMultiplier(rarity(stack, def)) * ratio
                * rules.enhanceMultiplier(enhance(stack)));
    }

    /** 数値の丸め方（%のステータスは小数1けた、ほかは整数。元が0より大きければ1以上）。 */
    public static double round(StatType type, double raw) {
        if (raw <= 0) {
            return 0.0;
        }
        if (type.isPercent()) {
            return Math.max(0.1, Math.round(raw * 10.0) / 10.0);
        }
        return Math.max(1.0, Math.round(raw));
    }

    /** すべてのステータスの割合の平均（品質の元）。 */
    public static double averageRatio(ItemStack stack, EquipmentDef def, GearRules rules) {
        if (def.stats().isEmpty()) {
            return 1.0;
        }
        double sum = 0.0;
        for (StatType type : def.stats().keySet()) {
            sum += ratio(stack, type, def, rules);
        }
        return sum / def.stats().size();
    }

    /** 品質（★1〜5）。 */
    public static int quality(ItemStack stack, EquipmentDef def, GearRules rules) {
        return rules.quality(averageRatio(stack, def, rules));
    }

    /** 「大成功」で作られた品か。 */
    public static boolean isGreat(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.getBoolean("great");
    }

    /** 作り手の名前（なければ null）。 */
    @Nullable
    public static String crafterName(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.contains("crafter") ? tag.getString("crafter") : null;
    }

    /** 作り手のUUID（なければ null）。 */
    @Nullable
    public static UUID crafterId(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.hasUUID("crafterId") ? tag.getUUID("crafterId") : null;
    }

    /** レアドロップで手に入れた品か。 */
    public static boolean isRareDrop(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.getBoolean("rareDrop");
    }

    public static void markRareDrop(ItemStack stack) {
        stack.getOrCreateTagElement(TAG).putBoolean("rareDrop", true);
    }

    // ---------------------------------------------------------------- 耐久

    /** 今の耐久（壊れない装備は最大を返す）。 */
    public static int durability(ItemStack stack, EquipmentDef def) {
        if (def.durability() <= 0) {
            return 0;
        }
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null || !tag.contains("dur")) {
            return def.durability();
        }
        return Math.max(0, Math.min(def.durability(), tag.getInt("dur")));
    }

    public static void setDurability(ItemStack stack, EquipmentDef def, int value) {
        if (def.durability() <= 0) {
            return;
        }
        stack.getOrCreateTagElement(TAG).putInt("dur", Math.max(0, Math.min(def.durability(), value)));
    }

    /** 壊れているか（耐久が0。ステータスが上がらない）。 */
    public static boolean isBroken(ItemStack stack, EquipmentDef def) {
        return def.durability() > 0 && durability(stack, def) <= 0;
    }

    /**
     * 耐久を減らす。
     *
     * @return 今回で壊れたら true
     */
    public static boolean wear(ItemStack stack, EquipmentDef def, int amount) {
        if (def.durability() <= 0 || amount <= 0) {
            return false;
        }
        int before = durability(stack, def);
        if (before <= 0) {
            return false;
        }
        setDurability(stack, def, before - amount);
        return before - amount <= 0;
    }

    // ---------------------------------------------------------------- 取引

    /** 取引できない品か。 */
    public static boolean isBound(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(BOUND);
    }

    /** 装備すると取引できなくなる品か（まだ装備していない）。 */
    public static boolean bindsOnEquip(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(BIND_ON_EQUIP) && !tag.getBoolean(BOUND);
    }

    /** 取引できない印をつける。 */
    public static ItemStack bind(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(BOUND, true);
        tag.remove(BIND_ON_EQUIP);
        return stack;
    }

    /** 抽選の割合の一覧（説明文用）。 */
    public static Map<StatType, Double> stats(EquipmentDef def) {
        return def.stats();
    }
}
