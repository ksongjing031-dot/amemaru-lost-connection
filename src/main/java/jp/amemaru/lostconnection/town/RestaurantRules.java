package jp.amemaru.lostconnection.town;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * ペトラ・ポムの料理屋の決まり（データパックの {@code restaurant/default.json}。共同事業③「料理屋の開店：料理による一時強化」。
 * Phase 7 その4）。料理・材料・強化の数は【補完】。
 */
public final class RestaurantRules {
    public static final ResourceLocation DEFAULT_ID = AmemaruMod.id("default");
    private static final DefinitionLoader<RestaurantRules> LOADER = new DefinitionLoader<>("restaurant", RestaurantRules::parse);

    /** 材料（fishStars が 1 以上なら「★がその数以上の魚」、そうでなければ item）。 */
    public record Ingredient(int fishStars, @Nullable ResourceLocation item, int count) {
    }

    /** 料理（食べると、stat を amount だけ上げる。mode：percent か flat）。 */
    public record Dish(ResourceLocation item, StatType stat, boolean percent, double amount, long fee, List<Ingredient> ingredients) {
    }

    private final ResourceLocation flag;
    private final int minutes;
    private final List<Dish> dishes;

    public static final RestaurantRules FALLBACK = new RestaurantRules(AmemaruMod.id("project/restaurant"), 20, List.of());

    private RestaurantRules(ResourceLocation flag, int minutes, List<Dish> dishes) {
        this.flag = flag;
        this.minutes = minutes;
        this.dishes = dishes;
    }

    public static DefinitionLoader<RestaurantRules> loader() {
        return LOADER;
    }

    public static RestaurantRules current() {
        return LOADER.get(DEFAULT_ID).orElse(FALLBACK);
    }

    static RestaurantRules parse(JsonObject json) {
        List<Dish> dishes = new ArrayList<>();
        JsonArray array = Json.array(json, "dishes");
        for (int i = 0; i < array.size(); i++) {
            JsonObject d = Json.asObject(array.get(i), "dishes[" + i + "]");
            String statId = Json.string(d, "stat");
            StatType stat = StatType.byId(statId).orElseThrow(() -> new DefinitionException("「dishes[].stat」がありません：" + statId));
            String mode = Json.optionalString(d, "mode", "percent");
            if (!"percent".equals(mode) && !"flat".equals(mode)) {
                throw new DefinitionException("「dishes[].mode」は percent か flat にしてください（今は " + mode + "）");
            }
            List<Ingredient> ingredients = new ArrayList<>();
            JsonArray ing = Json.array(d, "ingredients");
            for (int k = 0; k < ing.size(); k++) {
                JsonObject one = Json.asObject(ing.get(k), "ingredients[" + k + "]");
                int stars = Json.optionalInt(one, "fish_stars", 0, 0, 5);
                ResourceLocation item = stars > 0 ? null : Json.id(one, "item");
                ingredients.add(new Ingredient(stars, item, Json.intInRange(one, "count", 1, 64)));
            }
            dishes.add(new Dish(Json.id(d, "item"), stat, "percent".equals(mode), Json.doubleInRange(d, "amount", 0.1, 1000),
                    (long) Json.optionalDouble(d, "fee", 0, 0, 1.0E9), List.copyOf(ingredients)));
        }
        return new RestaurantRules(Json.parseId(Json.string(json, "flag"), "flag"), Json.optionalInt(json, "minutes", 20, 1, 24 * 60),
                List.copyOf(dishes));
    }

    public ResourceLocation flag() {
        return flag;
    }

    public int minutes() {
        return minutes;
    }

    public List<Dish> dishes() {
        return dishes;
    }

    @Nullable
    public Dish dish(ResourceLocation item) {
        for (Dish d : dishes) {
            if (d.item().equals(item)) {
                return d;
            }
        }
        return null;
    }
}
