package jp.amemaru.lostconnection.workshop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.Rarity;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 工房のレシピ（独自のレシピの種類 {@code amemaru:workshop}。Phase 0-B 6章「工房のレシピは独自のレシピの種類を
 * recipes/ に置く」）。バニラの作業台では使えない。
 *
 * <pre>{
 *   "type": "amemaru:workshop",
 *   "workshop": "weapon",
 *   "mastery": 1,
 *   "mastery_exp": 10,
 *   "ingredients": [{"item": "amemaru:moss_iron", "count": 5}, {"item": "amemaru:crystal_shard", "count": 2}],
 *   "result": {"item": "amemaru:ancient_stone_sword"}
 * }</pre>
 *
 * <p>作れるのは、その工房の生産職の人だけ（熟練度がレシピの {@code mastery} 以上）。作った品は抽選して、説明文に
 * 「〇〇作」と入る。</p>
 */
public class WorkshopRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final WorkshopType workshop;
    private final int mastery;
    private final int masteryExp;
    private final List<EquipmentDef.Cost> ingredients;
    private final ItemStack result;
    @Nullable
    private final Rarity rarity;

    public WorkshopRecipe(ResourceLocation id, WorkshopType workshop, int mastery, int masteryExp, List<EquipmentDef.Cost> ingredients,
                          ItemStack result, @Nullable Rarity rarity) {
        this.id = id;
        this.workshop = workshop;
        this.mastery = mastery;
        this.masteryExp = masteryExp;
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
        this.rarity = rarity;
    }

    public WorkshopType workshop() {
        return workshop;
    }

    public int mastery() {
        return mastery;
    }

    public int masteryExp() {
        return masteryExp;
    }

    public List<EquipmentDef.Cost> ingredients() {
        return ingredients;
    }

    /** できる品（数を含む。抽選の前）。 */
    public ItemStack result() {
        return result.copy();
    }

    @Nullable
    public Rarity rarity() {
        return rarity;
    }

    @Override
    public boolean matches(Container container, Level level) {
        // バニラの作業台では使わない
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return result.copy();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.WORKSHOP_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.WORKSHOP_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        // レシピ本には出さない
        return true;
    }

    /** JSONと通信の読み書き。 */
    public static class Serializer implements RecipeSerializer<WorkshopRecipe> {
        @Override
        public WorkshopRecipe fromJson(ResourceLocation id, JsonObject json) {
            WorkshopType workshop = WorkshopType.byId(GsonHelper.getAsString(json, "workshop"));
            if (workshop == null) {
                throw new JsonSyntaxException(id + "：「workshop」は weapon・armor・accessory のどれかにしてください");
            }
            int mastery = GsonHelper.getAsInt(json, "mastery", 1);
            int exp = GsonHelper.getAsInt(json, "mastery_exp", 10);
            JsonArray array = GsonHelper.getAsJsonArray(json, "ingredients");
            List<EquipmentDef.Cost> ingredients = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JsonObject one = GsonHelper.convertToJsonObject(array.get(i), "ingredients[" + i + "]");
                ResourceLocation item = new ResourceLocation(GsonHelper.getAsString(one, "item"));
                if (!ForgeRegistries.ITEMS.containsKey(item)) {
                    throw new JsonSyntaxException(id + "：素材「" + item + "」がありません");
                }
                ingredients.add(new EquipmentDef.Cost(item, Math.max(1, GsonHelper.getAsInt(one, "count", 1))));
            }
            JsonObject resultJson = GsonHelper.getAsJsonObject(json, "result");
            ResourceLocation resultId = new ResourceLocation(GsonHelper.getAsString(resultJson, "item"));
            Item item = ForgeRegistries.ITEMS.getValue(resultId);
            if (item == null || !resultId.equals(ForgeRegistries.ITEMS.getKey(item))) {
                throw new JsonSyntaxException(id + "：できる品「" + resultId + "」がありません");
            }
            ItemStack result = new ItemStack(item, Math.max(1, GsonHelper.getAsInt(resultJson, "count", 1)));
            Rarity rarity = json.has("rarity") ? Rarity.byId(GsonHelper.getAsString(json, "rarity")) : null;
            return new WorkshopRecipe(id, workshop, Math.max(1, mastery), Math.max(0, exp), ingredients, result, rarity);
        }

        @Nullable
        @Override
        public WorkshopRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            WorkshopType workshop = WorkshopType.byIndex(buf.readByte());
            int mastery = buf.readVarInt();
            int exp = buf.readVarInt();
            int count = Math.min(buf.readVarInt(), 64);
            List<EquipmentDef.Cost> ingredients = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ingredients.add(EquipmentDef.Cost.read(buf));
            }
            ItemStack result = buf.readItem();
            int rarityIndex = buf.readByte();
            Rarity rarity = rarityIndex < 0 ? null : Rarity.byIndex(rarityIndex);
            return new WorkshopRecipe(id, workshop, mastery, exp, ingredients, result, rarity);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, WorkshopRecipe recipe) {
            buf.writeByte(recipe.workshop.ordinal());
            buf.writeVarInt(recipe.mastery);
            buf.writeVarInt(recipe.masteryExp);
            buf.writeVarInt(recipe.ingredients.size());
            for (EquipmentDef.Cost cost : recipe.ingredients) {
                cost.write(buf);
            }
            buf.writeItem(recipe.result);
            buf.writeByte(recipe.rarity == null ? -1 : recipe.rarity.ordinal());
        }
    }
}
