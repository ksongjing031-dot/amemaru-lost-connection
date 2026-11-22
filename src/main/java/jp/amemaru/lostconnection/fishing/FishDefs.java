package jp.amemaru.lostconnection.fishing;

import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 魚の定義の置き場（{@code data/<名前空間>/amemaru/fishing/*.json}。Phase 0-B 6章のフォルダの一覧「fishing/：魚の表」。Phase 7 その2）。 */
public final class FishDefs {
    private static final DefinitionLoader<FishDef> LOADER = new DefinitionLoader<>("fishing", FishDef::parse);

    private FishDefs() {
    }

    public static DefinitionLoader<FishDef> loader() {
        return LOADER;
    }

    @Nullable
    public static FishDef get(@Nullable ResourceLocation id) {
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    public static Map<ResourceLocation, FishDef> all() {
        return LOADER.all();
    }

    /** 図鑑の順（order、同じなら名前）。 */
    public static List<FishDef> ordered() {
        List<FishDef> list = new ArrayList<>(LOADER.all().values());
        list.sort(Comparator.comparingInt(FishDef::order).thenComparing(d -> d.id().toString()));
        return list;
    }

    /** アイテムから魚の定義（魚のアイテムでなければ null）。 */
    @Nullable
    public static FishDef of(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof FishItem)) {
            return null;
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = item.getPath();
        if (!path.startsWith("fish_")) {
            return null;
        }
        return get(new ResourceLocation(item.getNamespace(), path.substring("fish_".length())));
    }
}
