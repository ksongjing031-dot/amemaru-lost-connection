package jp.amemaru.lostconnection.loot;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** ドロップ表の独自の関数の登録（{@code amemaru:roll_gear}・{@code amemaru:roll_accessory}）。 */
public final class ModLootFunctions {
    public static final DeferredRegister<LootItemFunctionType> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, AmemaruMod.MOD_ID);

    public static final RegistryObject<LootItemFunctionType> ROLL_GEAR =
            FUNCTIONS.register("roll_gear", () -> new LootItemFunctionType(new RollGearFunction.Serializer()));
    public static final RegistryObject<LootItemFunctionType> ROLL_ACCESSORY =
            FUNCTIONS.register("roll_accessory", () -> new LootItemFunctionType(new RollAccessoryFunction.Serializer()));

    private ModLootFunctions() {
    }
}
