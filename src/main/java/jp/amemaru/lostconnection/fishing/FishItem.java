package jp.amemaru.lostconnection.fishing;

import jp.amemaru.lostconnection.equipment.Rarity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * 魚（Phase 7 その2。Phase 0-A 15章「使い道：売却、料理屋の材料、留め糸との交換」）。
 *
 * <p>ふつうの魚は追加の情報（NBT）を持たず、同じ魚どうしで重なる（持ち物があふれないように【補完】）。大物（大きさの割合が
 * {@code big_ratio} 以上）だけ、大きさ・釣った人・日を持ち、ほかと重ならず、買い取りが高い。大きさの記録は、図鑑と大会に残る。</p>
 */
public class FishItem extends Item {
    /** 大物の情報（{@code AmemaruFish}：size（cm）・by（釣った人）・day（進行日））。 */
    public static final String TAG = "AmemaruFish";

    private final int stars;

    public FishItem(int stars, Properties properties) {
        super(properties);
        this.stars = Math.max(1, Math.min(5, stars));
    }

    /** ★の数（1〜5。4は記憶魚、5は湖の主）。 */
    public int stars() {
        return stars;
    }

    public Rarity amemaruRarity() {
        return Rarity.byIndex(stars - 1);
    }

    @Override
    public Component getName(ItemStack stack) {
        return stars <= 1 ? super.getName(stack) : amemaruRarity().paint(super.getName(stack).copy());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stars >= 4 || super.isFoil(stack);
    }

    /** 大物か（大きさを持っているか）。 */
    public static boolean isBig(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.contains("size");
    }

    /** 大物の大きさ（cm。大物でなければ 0）。 */
    public static double size(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? 0.0 : tag.getDouble("size");
    }

    /** 大物の印をつける。 */
    public static void markBig(ItemStack stack, double size, String by, int day) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        tag.putDouble("size", Math.round(size * 10.0) / 10.0);
        tag.putString("by", by);
        tag.putInt("day", day);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.fish", "★".repeat(stars)).withStyle(ChatFormatting.DARK_AQUA));
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag != null && tag.contains("size")) {
            lines.add(Component.translatable("tooltip.amemaru.fish.big", String.format(Locale.ROOT, "%.1f", tag.getDouble("size")))
                    .withStyle(ChatFormatting.GOLD));
            if (!tag.getString("by").isEmpty()) {
                lines.add(Component.translatable("tooltip.amemaru.fish.by", tag.getString("by"), tag.getInt("day"))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (stars == 4) {
            lines.add(Component.translatable("tooltip.amemaru.fish.memory").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (stars == 5) {
            lines.add(Component.translatable("tooltip.amemaru.fish.lord").withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("tooltip.amemaru.fish.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
