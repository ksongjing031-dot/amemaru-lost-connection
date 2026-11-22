package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.util.thread.EffectiveSide;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/** 消耗品の数値の置き場（サーバーはデータパックから、クライアントはサーバーから送られた分。Phase 6）。 */
public final class ConsumableDefs {
    private static final DefinitionLoader<ConsumableDef> LOADER = new DefinitionLoader<>("consumables", ConsumableDef::parse);
    private static volatile Map<ResourceLocation, ConsumableDef> client = Map.of();

    private ConsumableDefs() {
    }

    public static DefinitionLoader<ConsumableDef> loader() {
        return LOADER;
    }

    /** そのアイテムの数値（なければ null）。 */
    @Nullable
    public static ConsumableDef get(ResourceLocation item) {
        if (EffectiveSide.get().isClient()) {
            return client.get(item);
        }
        return LOADER.get(item).orElse(null);
    }

    public static List<ConsumableDef> server() {
        return List.copyOf(LOADER.all().values());
    }

    public static void setClient(List<ConsumableDef> defs) {
        java.util.Map<ResourceLocation, ConsumableDef> map = new java.util.LinkedHashMap<>();
        for (ConsumableDef def : defs) {
            map.put(def.id(), def);
        }
        client = Map.copyOf(map);
    }

    /** 同じ group の消耗品（クールダウンを一緒にかける）。 */
    public static List<ConsumableDef> group(String group) {
        Map<ResourceLocation, ConsumableDef> all = EffectiveSide.get().isClient() ? client : LOADER.all();
        return all.values().stream().filter(def -> def.group().equals(group)).toList();
    }
}
