package jp.amemaru.lostconnection.accessory;

import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * アクセサリーの一つひとつの品のデータ（アイテムのNBT {@code AmemaruAccessory}。Phase 0-B 4.3）。
 *
 * <p>種類（枠）・メインステータス・サブステータス1〜4個（数値と固定の有無）・品質（★1〜5）・強化値（+0〜+12）・
 * 再抽選の回数を持つ。乱数はすべてサーバーで決め、結果をNBTに保存する（倉庫・フリマ・ログアウトでも正しく引き継ぐ。
 * Phase 0-A 21章【確定】）。数値の幅（下限と上限）も抽選したときの値を一緒に持つので、説明文はNBTだけで書ける。</p>
 */
public final class AccessoryData {
    public static final String TAG = "AmemaruAccessory";

    /**
     * ステータスの1行（今の値・幅の下限・上限・固定の有無・何回分か）。
     *
     * <p>{@code rolls} は「1回分」の数（Phase 6 その4）。強化の +3・+6・+9・+12 でサブが4つあるときは、ランダムな一つが
     * 「1回分」伸びる（Phase 0-A 21章）。そのとき下限と上限も1回分ずつ足すので、割合（上限に対する値）は 50〜100% のまま。</p>
     */
    public record StatLine(StatType type, double value, double min, double max, boolean locked, int rolls) {
        public StatLine(StatType type, double value, double min, double max, boolean locked) {
            this(type, value, min, max, locked, 1);
        }

        /** 上限に対する割合（0〜1）。 */
        public double ratio() {
            return max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, value / max));
        }

        /** 1回分の下限。 */
        public double minPerRoll() {
            return min / Math.max(1, rolls);
        }

        /** 1回分の上限。 */
        public double maxPerRoll() {
            return max / Math.max(1, rolls);
        }

        public StatLine withValue(double newValue) {
            return new StatLine(type, newValue, min, max, locked, rolls);
        }

        public StatLine withLocked(boolean newLocked) {
            return new StatLine(type, value, min, max, newLocked, rolls);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("stat", type.id());
            tag.putDouble("value", value);
            tag.putDouble("min", min);
            tag.putDouble("max", max);
            if (locked) {
                tag.putBoolean("locked", true);
            }
            if (rolls > 1) {
                tag.putInt("rolls", rolls);
            }
            return tag;
        }

        @Nullable
        static StatLine load(CompoundTag tag) {
            StatType type = StatType.byId(tag.getString("stat")).orElse(null);
            if (type == null) {
                return null;
            }
            return new StatLine(type, tag.getDouble("value"), tag.getDouble("min"), tag.getDouble("max"), tag.getBoolean("locked"),
                    Math.max(1, tag.getInt("rolls")));
        }
    }

    private AccessoryData() {
    }

    /** もう抽選が済んでいるか。 */
    public static boolean isRolled(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.contains("main");
    }

    @Nullable
    public static StatLine main(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null || !tag.contains("main") ? null : StatLine.load(tag.getCompound("main"));
    }

    public static List<StatLine> subs(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null) {
            return List.of();
        }
        List<StatLine> list = new ArrayList<>();
        ListTag subs = tag.getList("subs", Tag.TAG_COMPOUND);
        for (int i = 0; i < subs.size(); i++) {
            StatLine line = StatLine.load(subs.getCompound(i));
            if (line != null) {
                list.add(line);
            }
        }
        return Collections.unmodifiableList(list);
    }

    /** メインとサブのすべての行（メインが先）。 */
    public static List<StatLine> allLines(ItemStack stack) {
        List<StatLine> list = new ArrayList<>();
        StatLine main = main(stack);
        if (main != null) {
            list.add(main);
        }
        list.addAll(subs(stack));
        return list;
    }

    /** 抽選の結果を書き込む。 */
    public static void write(ItemStack stack, AccessorySlot slot, int generation, Rarity rarity, StatLine main,
                             List<StatLine> subs, boolean great, @Nullable String crafter, @Nullable UUID crafterId) {
        CompoundTag tag = new CompoundTag();
        tag.putString("slot", slot.id());
        tag.putInt("gen", generation);
        tag.putString("rarity", rarity.id());
        tag.put("main", main.save());
        ListTag list = new ListTag();
        for (StatLine sub : subs) {
            list.add(sub.save());
        }
        tag.put("subs", list);
        tag.putInt("enhance", 0);
        tag.putInt("rerolls", 0);
        if (great) {
            tag.putBoolean("great", true);
        }
        if (crafter != null && crafterId != null) {
            tag.putString("crafter", crafter);
            tag.putUUID("crafterId", crafterId);
        }
        stack.getOrCreateTag().put(TAG, tag);
    }

    public static int generation(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? 1 : Math.max(1, tag.getInt("gen"));
    }

    public static Rarity rarity(ItemStack stack, Rarity fallback) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null) {
            return fallback;
        }
        Rarity rarity = Rarity.byId(tag.getString("rarity"));
        return rarity == null ? fallback : rarity;
    }

    public static int enhance(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? 0 : tag.getInt("enhance");
    }

    public static void setEnhance(ItemStack stack, int value) {
        stack.getOrCreateTagElement(TAG).putInt("enhance", Math.max(0, value));
    }

    public static int rerolls(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? 0 : tag.getInt("rerolls");
    }

    public static void setRerolls(ItemStack stack, int value) {
        stack.getOrCreateTagElement(TAG).putInt("rerolls", Math.max(0, value));
    }

    /**
     * メインとサブの行を入れかえる（厳選。Phase 6 その4）。枠・世代・強化値・再抽選の回数・作り手などは、そのまま残す。
     */
    public static void replaceLines(ItemStack stack, StatLine main, List<StatLine> subs) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        tag.put("main", main.save());
        ListTag list = new ListTag();
        for (StatLine sub : subs) {
            list.add(sub.save());
        }
        tag.put("subs", list);
    }

    /** 固定しているサブの数。 */
    public static int lockedCount(ItemStack stack) {
        int count = 0;
        for (StatLine sub : subs(stack)) {
            if (sub.locked()) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- 振り直すほど高くなる（Phase 0-A 21章）

    /**
     * 振り直しの費用の上乗せ（%）。同じアクセサリーを振り直すたびに +10%（最大 +200%）。日付（進行日）が変わると、
     * 増えた分が半分に戻る（1日ごとに半分。切り捨て）。
     */
    public static int costUp(ItemStack stack, int today) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null) {
            return 0;
        }
        int up = tag.getInt("costUp");
        int day = tag.getInt("costDay");
        if (up <= 0) {
            return 0;
        }
        int days = today - day;
        if (days <= 0) {
            return up;
        }
        return days >= 31 ? 0 : up >> days;
    }

    /** 振り直したので、上乗せを足す（その日の分に直してから）。 */
    public static void addCostUp(ItemStack stack, int today, int step, int max) {
        int now = costUp(stack, today);
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        tag.putInt("costUp", Math.max(0, Math.min(max, now + step)));
        tag.putInt("costDay", today);
    }

    /** 強化1段ごとのメインの伸び（%。強化したときの決まりを品に残す。強化していなければ0）。 */
    public static double mainPercent(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.contains("mainPct") ? tag.getDouble("mainPct") : 0.0;
    }

    public static void setMainPercent(ItemStack stack, double percent) {
        stack.getOrCreateTagElement(TAG).putDouble("mainPct", Math.max(0.0, percent));
    }

    /** メインの、強化を入れた値（品に残した伸びで計算する。クライアントの説明文でも同じ値になる）。 */
    public static double effectiveMain(ItemStack stack, StatLine main) {
        return effectiveValue(stack, main, true, mainPercent(stack));
    }

    /**
     * 強化を入れた、ステータスとして足す値（メインは強化1段ごとに +{@code mainPercent}%。%で表すステータスは小数1けた、
     * ほかは整数に丸める。サブはそのまま）。
     */
    public static double effectiveValue(ItemStack stack, StatLine line, boolean isMain, double mainPercent) {
        if (!isMain) {
            return line.value();
        }
        int enhance = enhance(stack);
        if (enhance <= 0 || mainPercent <= 0) {
            return line.value();
        }
        double raw = line.value() * (1.0 + enhance * mainPercent / 100.0);
        return line.type().isPercent() ? Math.round(raw * 10.0) / 10.0 : Math.round(raw);
    }

    public static boolean isGreat(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.getBoolean("great");
    }

    @Nullable
    public static String crafterName(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.contains("crafter") ? tag.getString("crafter") : null;
    }

    /** すべての行の割合の平均（品質の元）。抽選していなければ0。 */
    public static double averageRatio(ItemStack stack) {
        List<StatLine> lines = allLines(stack);
        if (lines.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (StatLine line : lines) {
            sum += line.ratio();
        }
        return sum / lines.size();
    }

    /** 品質（★1〜5。抽選していなければ0）。 */
    public static int quality(ItemStack stack, AccessoryRules rules) {
        return isRolled(stack) ? rules.quality(averageRatio(stack)) : 0;
    }

    /** 完璧なアクセサリーか（サブ4つで、メインを含むすべての割合が95%以上）。 */
    public static boolean isPerfect(ItemStack stack, AccessoryRules rules) {
        List<StatLine> subs = subs(stack);
        if (subs.size() < rules.perfectSubs() || !isRolled(stack)) {
            return false;
        }
        for (StatLine line : allLines(stack)) {
            if (line.ratio() + 1.0E-9 < rules.perfectRatio()) {
                return false;
            }
        }
        return true;
    }

    /** 品質の★を、NBTに書いた値から読む（説明文・アイコンの光り方用。抽選したときに書く）。 */
    public static int storedQuality(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? 0 : tag.getInt("quality");
    }

    public static void storeQuality(ItemStack stack, int quality, boolean perfect) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        tag.putInt("quality", quality);
        if (perfect) {
            tag.putBoolean("perfect", true);
        } else {
            tag.remove("perfect");
        }
    }

    /** 完璧の印（NBTに書いた値）。 */
    public static boolean storedPerfect(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.getBoolean("perfect");
    }
}
