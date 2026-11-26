package jp.amemaru.lostconnection.casino.parlor;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * 遊技機の機種（データパックの {@code casino_machines/<機種>.json}。11月27日【補完】）。パチンコ（{@link PachinkoSpec}）か
 * スロット（{@link SlotSpec}）。画面の色（{@code theme}）も持つ。画面を開くときは、機種の JSON をそのまま送る（リールの並び・図柄・
 * 色。設定そのものは台にあって、送らない）。
 */
public final class ParlorModels {
    public enum Kind { PACHINKO, SLOT }

    public record Model(String id, Kind kind, String name, String specName, @Nullable SlotSpec slot,
                        @Nullable PachinkoSpec pachinko, String json) {
        public boolean isSlot() {
            return kind == Kind.SLOT;
        }
    }

    private static final DefinitionLoader<Model> LOADER = new DefinitionLoader<>("casino_machines", ParlorModels::parse);

    private ParlorModels() {
    }

    public static DefinitionLoader<Model> loader() {
        return LOADER;
    }

    @Nullable
    public static Model get(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        ResourceLocation key = id.contains(":") ? ResourceLocation.tryParse(id) : AmemaruMod.id(id);
        return key == null ? null : LOADER.get(key).orElse(null);
    }

    static Model parse(ResourceLocation id, JsonObject json) {
        String kind = ParlorJson.string(json, "kind");
        String name = ParlorJson.string(json, "name");
        String spec = ParlorJson.optString(json, "spec", "");
        return switch (kind) {
            case "slot" -> new Model(id.getPath(), Kind.SLOT, name, spec, SlotSpec.parse(id.getPath(), json), null, json.toString());
            case "pachinko" -> new Model(id.getPath(), Kind.PACHINKO, name, spec, null, PachinkoSpec.parse(id.getPath(), json),
                    json.toString());
            default -> throw new DefinitionException("「kind」は pachinko か slot です（今は " + kind + "）");
        };
    }
}
