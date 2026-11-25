package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.item.ConsumableDef;
import jp.amemaru.lostconnection.item.ConsumableDefs;
import jp.amemaru.lostconnection.item.ConsumableItem;
import jp.amemaru.lostconnection.item.Looks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 品の「使い道」（11月23日の直し：「色々な店があるがどこで使うかわからないものが多いので実用的なものを置く」）。
 *
 * <ul>
 *   <li>使い道は言語ファイルの {@code itemuse.<名前空間>.<名前>}（{@code tools/datagen/build_shops.py} の USES から書き出す）。
 *       見た目の服は {@code itemuse.amemaru.look}。店の画面の2行目と、説明文の「使い道：」の行に出す</li>
 *   <li>バニラの食べ物など、このModの消耗品ではない品に数値があるとき（{@code data/minecraft/amemaru/consumables/}）、
 *       説明文に「HPが○%戻る」の行を足す</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class ItemUseTooltips {
    private ItemUseTooltips() {
    }

    /** その品の使い道（なければ null）。 */
    @Nullable
    public static Component use(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (Looks.isLook(stack)) {
            return Component.translatable("itemuse.amemaru.look");
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return null;
        }
        String key = "itemuse." + id.getNamespace() + "." + id.getPath();
        return I18n.exists(key) ? Component.translatable(key) : null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> extra = new ArrayList<>();
        if (!(stack.getItem() instanceof ConsumableItem)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            ConsumableDef def = id == null ? null : ConsumableDefs.get(id);
            if (def != null) {
                ConsumableItem.restoreLines(def, extra);
            }
        }
        Component use = use(stack);
        if (use != null) {
            extra.add(Component.translatable("tooltip.amemaru.item_use", use).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (!extra.isEmpty()) {
            List<Component> tooltip = event.getToolTip();
            tooltip.addAll(Math.min(1, tooltip.size()), extra);
        }
    }
}
