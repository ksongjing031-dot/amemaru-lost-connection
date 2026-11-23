package jp.amemaru.lostconnection.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 見た目だけの服（11月23日の直し。布と仕立て・革と鞄の店で売る）。カジノの景品の見た目の服（Phase 7 その3）と同じ作り：
 * 守りと強さを外し（AttributeModifiers を空に）、壊れず、色と名前と「見た目の品」の一行を付ける。
 */
public final class Looks {
    private Looks() {
    }

    /** その品を見た目の服にする（nameKey は名前の言語キー、color は革の色。0 は色を付けない）。 */
    public static void dress(ItemStack stack, String nameKey, int color) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag display = tag.getCompound("display");
        if (color != 0) {
            display.putInt("color", color);
        }
        display.putString("Name", Component.Serializer.toJson(Component.translatable(nameKey)
                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.WHITE))));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("shop.amemaru.look_lore")
                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))));
        display.put("Lore", lore);
        tag.put("display", display);
        tag.put("AttributeModifiers", new ListTag());
        tag.putBoolean("Unbreakable", true);
        tag.putInt("HideFlags", 6);
        tag.putBoolean("AmemaruLook", true);
    }

    /** 見た目の服か。 */
    public static boolean isLook(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean("AmemaruLook");
    }
}
