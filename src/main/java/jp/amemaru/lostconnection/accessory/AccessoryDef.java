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
 *   <li>{@code floor}：作れる・手に入る階（11月24日のご指示「階層ごとにアクセサリーも追加しましょう」。省くと 0）。
 *       同じ章（世代）の中で、奥の階の品ほどメインステータスの上限が少し高い（章の1つ目の階を1倍として、1階ごとに
 *       +{@value #FLOOR_STEP_PERCENT}%。{@link #mainFactor()}）【補完】。サブの幅と、厳選・強化の決まりは章のまま</li>
 * </ul>
 */
public record AccessoryDef(ResourceLocation id, AccessorySlot slot, int generation, Rarity rarity, List<StatType> main,
                           List<SpecialEffect> effects, int floor) {
    /** 同じ章の中で、1階奥へ進むごとに、メインステータスの上限を何%上げるか。 */
    public static final int FLOOR_STEP_PERCENT = 5;

    /** メインステータスの上限の倍率（章の1つ目の階と、階を決めていない品は 1）。 */
    public double mainFactor() {
        if (floor <= 0) {
            return 1.0;
        }
        return 1.0 + FLOOR_STEP_PERCENT / 100.0 * ((floor - 1) % 5);
    }

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
        int floor = Json.optionalInt(json, "floor", 0, 0, 20);
        if (floor > 0 && (floor - 1) / 5 + 1 != generation) {
            throw new DefinitionException("「floor」（" + floor + "階）と「generation」（第" + generation + "章）が合いません");
        }
        return new AccessoryDef(id, slot, generation, rarity, List.copyOf(main), List.copyOf(effects), floor);
    }
}
