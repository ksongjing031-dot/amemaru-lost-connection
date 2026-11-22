package jp.amemaru.lostconnection.town;

import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 共同事業の定義の置き場（{@code data/<名前空間>/amemaru/projects/*.json}。Phase 7 その4）。 */
public final class ProjectDefs {
    private static final DefinitionLoader<ProjectDef> LOADER = new DefinitionLoader<>("projects", ProjectDef::parse);

    private ProjectDefs() {
    }

    public static DefinitionLoader<ProjectDef> loader() {
        return LOADER;
    }

    @Nullable
    public static ProjectDef get(@Nullable ResourceLocation id) {
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    public static Map<ResourceLocation, ProjectDef> all() {
        return LOADER.all();
    }

    /** 順（order）。 */
    public static List<ProjectDef> ordered() {
        List<ProjectDef> list = new ArrayList<>(LOADER.all().values());
        list.sort(Comparator.comparingInt(ProjectDef::order).thenComparing(d -> d.id().toString()));
        return list;
    }
}
