package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 古代記録の定義の置き場（{@code data/<名前空間>/amemaru/records/*.json}。Phase 6 その3）。 */
public final class RecordDefs {
    private static final DefinitionLoader<RecordDef> LOADER = new DefinitionLoader<>("records", RecordDef::parse);

    private RecordDefs() {
    }

    public static DefinitionLoader<RecordDef> loader() {
        return LOADER;
    }

    @Nullable
    public static RecordDef get(@Nullable ResourceLocation id) {
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    public static Map<ResourceLocation, RecordDef> all() {
        return LOADER.all();
    }

    /** 階 → order → ID の順。 */
    public static List<RecordDef> sorted() {
        List<RecordDef> list = new ArrayList<>(LOADER.all().values());
        list.sort(Comparator.comparingInt(RecordDef::floor).thenComparingInt(RecordDef::order).thenComparing(def -> def.id().toString()));
        return list;
    }
}
