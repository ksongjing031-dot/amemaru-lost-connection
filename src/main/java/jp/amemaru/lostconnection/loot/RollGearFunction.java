package jp.amemaru.lostconnection.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.Rarity;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.EnumMap;
import java.util.Map;

/**
 * ドロップ表の関数 {@code amemaru:roll_gear}：装備のレアリティとステータスを抽選する（Phase 0-B 6章「ドロップは
 * バニラの loot_tables と、レアリティやランダムな数値を足す独自の関数」）。
 *
 * <pre>{"function": "amemaru:roll_gear", "rarity": {"common": 70, "uncommon": 25, "rare": 5}}</pre>
 *
 * <p>{@code rarity} を省くと、装備の定義のレアリティ。装備でない品には何もしない。</p>
 */
public class RollGearFunction extends LootItemConditionalFunction {
    private final Map<Rarity, Integer> weights;

    protected RollGearFunction(LootItemCondition[] conditions, Map<Rarity, Integer> weights) {
        super(conditions);
        this.weights = weights;
    }

    @Override
    public LootItemFunctionType getType() {
        return ModLootFunctions.ROLL_GEAR.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        EquipmentDef def = snapshot.get(stack);
        if (def == null) {
            return stack;
        }
        GearData.roll(stack, def, snapshot.rules(), context.getRandom(), pick(context.getRandom()), false, null);
        return stack;
    }

    private Rarity pick(RandomSource random) {
        if (weights.isEmpty()) {
            return null;
        }
        int total = 0;
        for (int weight : weights.values()) {
            total += weight;
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Map.Entry<Rarity, Integer> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<RollGearFunction> {
        @Override
        public void serialize(JsonObject json, RollGearFunction function, JsonSerializationContext context) {
            super.serialize(json, function, context);
            if (!function.weights.isEmpty()) {
                JsonObject rarity = new JsonObject();
                for (Map.Entry<Rarity, Integer> entry : function.weights.entrySet()) {
                    rarity.addProperty(entry.getKey().id(), entry.getValue());
                }
                json.add("rarity", rarity);
            }
        }

        @Override
        public RollGearFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            Map<Rarity, Integer> weights = new EnumMap<>(Rarity.class);
            if (json.has("rarity")) {
                JsonObject rarity = GsonHelper.getAsJsonObject(json, "rarity");
                for (Map.Entry<String, JsonElement> entry : rarity.entrySet()) {
                    Rarity type = Rarity.byId(entry.getKey());
                    if (type == null) {
                        throw new com.google.gson.JsonSyntaxException("amemaru:roll_gear：レアリティ「" + entry.getKey() + "」はありません");
                    }
                    weights.put(type, Math.max(0, entry.getValue().getAsInt()));
                }
            }
            return new RollGearFunction(conditions, weights);
        }
    }
}
