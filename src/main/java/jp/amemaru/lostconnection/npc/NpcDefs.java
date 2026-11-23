package jp.amemaru.lostconnection.npc;

import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NPCの定義の置き場。サーバーはデータパックから読み、見た目に要る分（名前・肩書き・スキン・表情）をクライアントへ送る。
 */
public final class NpcDefs {
    private static final DefinitionLoader<NpcDef> LOADER = new DefinitionLoader<>("npcs", NpcDef::parse);
    private static volatile Map<ResourceLocation, NpcDef> client = Map.of();

    private NpcDefs() {
    }

    public static DefinitionLoader<NpcDef> loader() {
        return LOADER;
    }

    @Nullable
    public static NpcDef get(@Nullable ResourceLocation id) {
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    public static Map<ResourceLocation, NpcDef> all() {
        return LOADER.all();
    }

    /** クライアントの写し（見た目に要る分）。 */
    @Nullable
    public static NpcDef client(@Nullable ResourceLocation id) {
        return id == null ? null : client.get(id);
    }

    public static void setClient(List<NpcDef> defs) {
        Map<ResourceLocation, NpcDef> map = new LinkedHashMap<>();
        for (NpcDef def : defs) {
            map.put(def.id(), def);
        }
        client = Map.copyOf(map);
    }
}
