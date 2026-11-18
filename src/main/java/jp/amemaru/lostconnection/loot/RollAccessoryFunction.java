package jp.amemaru.lostconnection.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.accessory.AccessoryRoller;
import jp.amemaru.lostconnection.accessory.AccessoryRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * ドロップ表の関数 {@code amemaru:roll_accessory}：アクセサリーのメインとサブを抽選する（Phase 0-A 21章：
 * 作ったとき・ドロップしたときのサブの数は、1個30%・2個40%・3個25%・4個5%）。アクセサリーでない品には何もしない。
 *
 * <pre>{"function": "amemaru:roll_accessory"}</pre>
 */
public class RollAccessoryFunction extends LootItemConditionalFunction {
    protected RollAccessoryFunction(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType getType() {
        return ModLootFunctions.ROLL_ACCESSORY.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        AccessoryDef def = AccessoryDefs.get(stack);
        if (def != null) {
            AccessoryRoller.roll(stack, def, AccessoryRules.current(), context.getRandom(), false, null, 0);
        }
        return stack;
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<RollAccessoryFunction> {
        @Override
        public void serialize(JsonObject json, RollAccessoryFunction function, JsonSerializationContext context) {
            super.serialize(json, function, context);
        }

        @Override
        public RollAccessoryFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            return new RollAccessoryFunction(conditions);
        }
    }
}
