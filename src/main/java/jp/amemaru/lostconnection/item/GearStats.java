package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 装備が上げるステータス（Phase 2の見習い装備だけ。Phase 3でレアリティ・強化などを持つ装備の仕組みに置きかえる）。
 */
public final class GearStats {
    /** 「取引できない」の印（Phase 0-A 20章：見習い装備・クエスト品は取引不可。Phase 6のフリマ・取引で見る）。 */
    public static final String BOUND_TAG = "AmemaruBound";

    private final Map<StatType, Double> values;

    private GearStats(Map<StatType, Double> values) {
        this.values = values;
    }

    public static GearStats of(Object... pairs) {
        Map<StatType, Double> map = new EnumMap<>(StatType.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((StatType) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return new GearStats(map);
    }

    public Map<StatType, Double> values() {
        return values;
    }

    /** 説明文に「攻撃 +6」などを足す。 */
    public void appendTooltip(List<Component> lines) {
        for (Map.Entry<StatType, Double> entry : values.entrySet()) {
            StatType type = entry.getKey();
            double value = entry.getValue();
            String number = (value >= 0 ? "+" : "") + type.format(value);
            lines.add(Component.translatable("tooltip.amemaru.gear.stat", Component.translatable(type.translationKey()), number)
                    .withStyle(ChatFormatting.BLUE));
        }
    }

    /** アイテムに「取引できない」の印をつける。 */
    public static ItemStack bind(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(BOUND_TAG, true);
        return stack;
    }

    public static boolean isBound(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(BOUND_TAG);
    }
}
