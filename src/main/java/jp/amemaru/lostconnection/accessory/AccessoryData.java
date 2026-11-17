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

    /** ステータスの1行（今の値・幅の下限・上限・固定の有無）。 */
    public record StatLine(StatType type, double value, double min, double max, boolean locked) {
        /** 上限に対する割合（0〜1）。 */
        public double ratio() {
            return max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, value / max));
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
            return tag;
        }

        @Nullable
        static StatLine load(CompoundTag tag) {
            StatType type = StatType.byId(tag.getString("stat")).orElse(null);
            if (type == null) {
                return null;
            }
            return new StatLine(type, tag.getDouble("value"), tag.getDouble("min"), tag.getDouble("max"), tag.getBoolean("locked"));
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

    public static int rerolls(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? 0 : tag.getInt("rerolls");
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
