package jp.amemaru.lostconnection.accessory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * アクセサリーの定義（{@code data/<名前空間>/amemaru/accessories/<名前>.json}。IDはアイテムのIDと同じ）。
 *
 * <ul>
 *   <li>{@code slot}：枠（ring・necklace・bracelet・charm・clasp）。アイテムの枠と同じにする</li>
 *   <li>{@code generation}：世代（章。1〜4）。数値の幅に、章ごとの倍率がかかる（{@code accessory_rules}）</li>
 *   <li>{@code rarity}：レアリティ（説明文と名前の色）</li>
 *   <li>{@code main}：メインステータスになれる種類（省くと枠の決まりのとおり。お守りなら5種類からランダム）</li>
 *   <li>{@code effects}：固有の特殊効果（ボスのレアアクセサリーなど。例：草原の角笛「移動速度+5%」）</li>
 * </ul>
 */
public record AccessoryDef(ResourceLocation id, AccessorySlot slot, int generation, Rarity rarity, List<StatType> main,
                           List<SpecialEffect> effects) {

    public static AccessoryDef parse(ResourceLocation id, JsonObject json) {
        AccessorySlot slot = AccessorySlot.byId(Json.string(json, "slot"));
        if (slot == null) {
            throw new DefinitionException("「slot」は ring・necklace・bracelet・charm・clasp のどれかにしてください");
        }
        int generation = Json.optionalInt(json, "generation", 1, 1, 4);
        Rarity rarity = Rarity.byId(Json.optionalString(json, "rarity", "uncommon"));
        if (rarity == null) {
            throw new DefinitionException("「rarity」は common・uncommon・rare・epic・legendary・unique のどれかにしてください");
        }
        List<StatType> main = new ArrayList<>();
        JsonArray mainArray = Json.optionalArray(json, "main");
        for (int i = 0; i < mainArray.size(); i++) {
            String name = Json.asString(mainArray.get(i), "main[" + i + "]");
            StatType type = StatType.byId(name).orElseThrow(() ->
                    new DefinitionException("「main」に、ない種類のステータス「" + name + "」があります"));
            if (!slot.mainStats().contains(type)) {
                throw new DefinitionException("「main」の「" + name + "」は、この枠（" + slot.id() + "）のメインステータスになれません");
            }
            main.add(type);
        }
        if (main.isEmpty()) {
            main.addAll(slot.mainStats());
        }
        List<SpecialEffect> effects = new ArrayList<>();
        JsonArray effectArray = Json.optionalArray(json, "effects");
        for (int i = 0; i < effectArray.size(); i++) {
            String where = "effects[" + i + "]";
            effects.add(SpecialEffect.parse(Json.asObject(effectArray.get(i), where), where));
        }
        return new AccessoryDef(id, slot, generation, rarity, List.copyOf(main), List.copyOf(effects));
    }
}
