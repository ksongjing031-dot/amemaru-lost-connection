package jp.amemaru.lostconnection.workshop;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 工房のレシピの種類の登録（{@code amemaru:workshop}）。 */
public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, AmemaruMod.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, AmemaruMod.MOD_ID);

    public static final RegistryObject<RecipeType<WorkshopRecipe>> WORKSHOP_TYPE = TYPES.register("workshop", () ->
            RecipeType.simple(AmemaruMod.id("workshop")));
    public static final RegistryObject<RecipeSerializer<WorkshopRecipe>> WORKSHOP_SERIALIZER = SERIALIZERS.register("workshop",
            WorkshopRecipe.Serializer::new);

    private ModRecipes() {
    }
}
