package jp.amemaru.lostconnection.connection;

import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;

/** CPが増える行動の定義の置き場（{@code data/<名前空間>/amemaru/connection_actions/*.json}。Phase 7 その1）。 */
public final class ConnectionActionDefs {
    private static final DefinitionLoader<ConnectionActionDef> LOADER =
            new DefinitionLoader<>("connection_actions", ConnectionActionDef::parse);

    private ConnectionActionDefs() {
    }

    public static DefinitionLoader<ConnectionActionDef> loader() {
        return LOADER;
    }

    @Nullable
    public static ConnectionActionDef get(@Nullable ResourceLocation id) {
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    public static Map<ResourceLocation, ConnectionActionDef> all() {
        return LOADER.all();
    }
}
