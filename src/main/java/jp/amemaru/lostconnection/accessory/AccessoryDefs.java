package jp.amemaru.lostconnection.accessory;

import com.google.gson.JsonElement;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * アクセサリーの定義の置き場（サーバー。データパックの {@code accessories/}）。説明文はNBTだけで書けるので、
 * クライアントには送らない。
 */
public final class AccessoryDefs {
    private static final DefinitionLoader<AccessoryDef> LOADER = new Loader();

    private AccessoryDefs() {
    }

    public static DefinitionLoader<AccessoryDef> loader() {
        return LOADER;
    }

    @Nullable
    public static AccessoryDef get(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    @Nullable
    public static AccessoryDef get(ItemStack stack) {
        return stack.isEmpty() ? null : get(stack.getItem());
    }

    public static Map<ResourceLocation, AccessoryDef> all() {
        return LOADER.all();
    }

    private static final class Loader extends DefinitionLoader<AccessoryDef> {
        Loader() {
            super("accessories", AccessoryDef::parse);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
            super.apply(jsons, resourceManager, profiler);
            for (AccessoryDef def : all().values()) {
                Item item = ForgeRegistries.ITEMS.getValue(def.id());
                if (item == null || !def.id().equals(ForgeRegistries.ITEMS.getKey(item))) {
                    addError(def.id() + "：同じIDのアイテムがありません（アイテムの種類は amemaru/item_types.json で登録します）");
                } else if (!(item instanceof AccessoryItem accessory)) {
                    addError(def.id() + "：このアイテムはアクセサリーではありません");
                } else if (accessory.slot() != def.slot()) {
                    addError(def.id() + "：「slot」が " + def.slot().id() + " ですが、アイテムは " + accessory.slot().id() + " のアクセサリーです");
                }
            }
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                if (item instanceof AccessoryItem && get(ForgeRegistries.ITEMS.getKey(item)).isEmpty()) {
                    addError(ForgeRegistries.ITEMS.getKey(item) + "：アクセサリーのアイテムですが、accessories の定義がありません");
                }
            }
        }
    }
}
