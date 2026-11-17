package jp.amemaru.lostconnection.enemy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 敵の定義の置き場。サーバーはデータパックから読み、図鑑に要る分（{@link EnemyDef.Info}）をクライアントへ送る。
 *
 * <p>図鑑の「ドロップ一覧」は、敵の定義が指すドロップ表（{@code loot_tables/}）のJSONから、出る品を読み取って作る
 * （ドロップ表を書きかえると、図鑑の一覧も変わる）。</p>
 */
public final class EnemyDefs {
    private static final Loader LOADER = new Loader();
    private static volatile Map<ResourceLocation, EnemyDef.Info> clientInfo = Map.of();

    private EnemyDefs() {
    }

    public static DefinitionLoader<EnemyDef> loader() {
        return LOADER;
    }

    @Nullable
    public static EnemyDef get(@Nullable ResourceLocation id) {
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    @Nullable
    public static EnemyDef forType(EntityType<?> type) {
        return get(ForgeRegistries.ENTITY_TYPES.getKey(type));
    }

    public static Map<ResourceLocation, EnemyDef> all() {
        return LOADER.all();
    }

    /** 図鑑に要る分（サーバーで作る）。 */
    public static List<EnemyDef.Info> infos() {
        List<EnemyDef.Info> list = new ArrayList<>();
        for (EnemyDef def : LOADER.all().values()) {
            List<ResourceLocation> drops = new ArrayList<>(LOADER.dropsOf(def.id()));
            list.add(new EnemyDef.Info(def.id(), def.name(), def.description(), def.floor(), def.level(), def.lost(),
                    List.copyOf(drops), def.rare()));
        }
        return list;
    }

    /** クライアントの写し（図鑑）。 */
    public static Map<ResourceLocation, EnemyDef.Info> clientInfo() {
        return clientInfo;
    }

    public static void setClientInfo(List<EnemyDef.Info> infos) {
        Map<ResourceLocation, EnemyDef.Info> map = new LinkedHashMap<>();
        for (EnemyDef.Info info : infos) {
            map.put(info.id(), info);
        }
        clientInfo = Map.copyOf(map);
    }

    private static final class Loader extends DefinitionLoader<EnemyDef> {
        private volatile Map<ResourceLocation, List<ResourceLocation>> drops = Map.of();

        Loader() {
            super("enemies", EnemyDef::parse);
        }

        List<ResourceLocation> dropsOf(ResourceLocation enemy) {
            return drops.getOrDefault(enemy, List.of());
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
            super.apply(jsons, resourceManager, profiler);
            Map<ResourceLocation, List<ResourceLocation>> found = new LinkedHashMap<>();
            for (EnemyDef def : all().values()) {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(def.id());
                if (type == null || !def.id().equals(ForgeRegistries.ENTITY_TYPES.getKey(type))) {
                    addError(def.id() + "：同じIDの敵（エンティティ）がありません");
                }
                for (EnemyDef.RareDrop rare : def.rare()) {
                    if (!ForgeRegistries.ITEMS.containsKey(rare.item())) {
                        addError(def.id() + "：レアドロップの品「" + rare.item() + "」がありません");
                    }
                }
                Set<ResourceLocation> items = new LinkedHashSet<>();
                if (def.lootTable() != null) {
                    ResourceLocation file = ResourceLocation.fromNamespaceAndPath(def.lootTable().getNamespace(),
                            "loot_tables/" + def.lootTable().getPath() + ".json");
                    Optional<Resource> resource = resourceManager.getResource(file);
                    if (resource.isEmpty()) {
                        addError(def.id() + "：ドロップ表「" + def.lootTable() + "」がありません");
                    } else {
                        try (Reader reader = resource.get().openAsReader()) {
                            collectItems(JsonParser.parseReader(reader), items);
                        } catch (Exception e) {
                            addError(def.id() + "：ドロップ表「" + def.lootTable() + "」を読めませんでした（" + e.getMessage() + "）");
                        }
                    }
                }
                found.put(def.id(), List.copyOf(items));
            }
            drops = Map.copyOf(found);
        }

        /** ドロップ表のJSONから、{@code "type": "minecraft:item"} の品の名前を集める。 */
        private static void collectItems(JsonElement element, Set<ResourceLocation> items) {
            if (element.isJsonArray()) {
                for (JsonElement child : element.getAsJsonArray()) {
                    collectItems(child, items);
                }
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("type") && object.get("type").isJsonPrimitive()
                        && "minecraft:item".equals(object.get("type").getAsString()) && object.has("name")) {
                    ResourceLocation id = ResourceLocation.tryParse(object.get("name").getAsString());
                    if (id != null) {
                        items.add(id);
                    }
                }
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    collectItems(entry.getValue(), items);
                }
            }
        }
    }
}
