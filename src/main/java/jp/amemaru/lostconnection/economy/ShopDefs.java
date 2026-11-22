package jp.amemaru.lostconnection.economy;

import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;

/** 店の定義の置き場（{@code data/<名前空間>/amemaru/shops/*.json}。Phase 6）。 */
public final class ShopDefs {
    private static final DefinitionLoader<ShopDef> LOADER = new DefinitionLoader<>("shops", ShopDef::parse);

    private ShopDefs() {
    }

    public static DefinitionLoader<ShopDef> loader() {
        return LOADER;
    }

    @Nullable
    public static ShopDef get(@Nullable ResourceLocation id) {
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    public static Map<ResourceLocation, ShopDef> all() {
        return LOADER.all();
    }
}
