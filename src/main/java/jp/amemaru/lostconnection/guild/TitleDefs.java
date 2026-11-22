package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 称号の定義の置き場（{@code data/<名前空間>/amemaru/titles/*.json}。Phase 6 その3）。 */
public final class TitleDefs {
    private static final DefinitionLoader<TitleDef> LOADER = new DefinitionLoader<>("titles", TitleDef::parse);

    private TitleDefs() {
    }

    public static DefinitionLoader<TitleDef> loader() {
        return LOADER;
    }

    @Nullable
    public static TitleDef get(@Nullable ResourceLocation id) {
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    public static Map<ResourceLocation, TitleDef> all() {
        return LOADER.all();
    }

    /** 並べる順（分野 → order → ID）。 */
    public static List<TitleDef> sorted() {
        List<TitleDef> list = new ArrayList<>(LOADER.all().values());
        list.sort(Comparator.comparing((TitleDef def) -> def.field().ordinal()).thenComparingInt(TitleDef::order)
                .thenComparing(def -> def.id().toString()));
        return list;
    }
}
