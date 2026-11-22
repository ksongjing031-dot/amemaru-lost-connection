package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** クエストの定義の置き場（{@code data/<名前空間>/amemaru/quests/*.json}。Phase 6 その3）。 */
public final class QuestDefs {
    private static final DefinitionLoader<QuestDef> LOADER = new DefinitionLoader<>("quests", QuestDef::parse);

    private QuestDefs() {
    }

    public static DefinitionLoader<QuestDef> loader() {
        return LOADER;
    }

    @Nullable
    public static QuestDef get(@Nullable ResourceLocation id) {
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    public static Map<ResourceLocation, QuestDef> all() {
        return LOADER.all();
    }

    /** 種類 → order → ID の順。 */
    public static List<QuestDef> sorted() {
        List<QuestDef> list = new ArrayList<>(LOADER.all().values());
        list.sort(Comparator.comparingInt((QuestDef def) -> def.kind().ordinal()).thenComparingInt(QuestDef::order)
                .thenComparing(def -> def.id().toString()));
        return list;
    }
}
