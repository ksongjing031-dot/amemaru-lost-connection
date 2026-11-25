package jp.amemaru.lostconnection.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.item.MaterialItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 相場の基準価格（Phase 0-A 20章「初期相場表の考え方【提案】」。Phase 6）。
 * {@code data/<名前空間>/amemaru/market_prices/*.json} に、品ごとの基準価格（ノット）を書く。
 *
 * <ul>
 *   <li>店で買える物：店の値段の80%</li>
 *   <li>素材：1個を手に入れるまでの平均時間（分）× その章の1分あたりの稼ぎ（第1章は約33ノット）</li>
 *   <li>作った装備：材料の基準価格の合計 × 1.2</li>
 *   <li>アクセサリー：種類ごとの基準価格 × 品質の倍率（★1＝0.6、★2＝0.8、★3＝1.0、★4＝1.6、★5＝3.0。{@code accessory_quality}）</li>
 *   <li>表にない物：レアリティごとの基準額（{@code rarity_base}）×（1＋0.08×装備レベル（{@code level_step}））</li>
 * </ul>
 *
 * <p>フリーマーケットの値段の幅（基準価格の50〜200%）と、店の買い取り値（{@code ratio}）に使う。{@code untradeable} は
 * フリーマーケットに出せない品（クエストの品など）。
 * ファイルが複数あるときは、名前の順にあとのファイルが上書きする。</p>
 */
public final class MarketPrices {
    /** 1つのファイル */
    public record Table(Map<ResourceLocation, Long> prices, Map<String, Long> rarityBase, @Nullable List<Double> quality,
                        double levelStep, List<ResourceLocation> untradeable) {
    }

    /** アクセサリーの品質（★1〜5）の倍率の初期値（Phase 0-A 20章） */
    public static final List<Double> DEFAULT_QUALITY = List.of(0.6, 0.8, 1.0, 1.6, 3.0);
    /** 表にない物の、装備レベル1つあたりの上がり幅の初期値（Phase 0-A 20章） */
    public static final double DEFAULT_LEVEL_STEP = 0.08;

    private static final DefinitionLoader<Table> LOADER = new DefinitionLoader<>("market_prices", MarketPrices::parse);

    private MarketPrices() {
    }

    public static DefinitionLoader<Table> loader() {
        return LOADER;
    }

    static Table parse(ResourceLocation id, JsonObject json) {
        Map<ResourceLocation, Long> prices = new LinkedHashMap<>();
        JsonObject list = Json.optionalObject(json, "prices");
        if (list != null) {
            for (Map.Entry<String, JsonElement> entry : list.entrySet()) {
                ResourceLocation item = Json.parseId(entry.getKey(), "prices");
                long price = Math.round(Json.asNumber(entry.getValue(), entry.getKey()));
                if (price > 0) {
                    prices.put(item, price);
                }
            }
        }
        Map<String, Long> rarity = new HashMap<>();
        JsonObject base = Json.optionalObject(json, "rarity_base");
        if (base != null) {
            for (Map.Entry<String, JsonElement> entry : base.entrySet()) {
                if (Rarity.byId(entry.getKey()) == null) {
                    throw new DefinitionException("rarity_base の「" + entry.getKey()
                            + "」はレアリティの名前ではありません（common・uncommon・rare・epic・legendary・unique）");
                }
                rarity.put(entry.getKey(), Math.round(Json.asNumber(entry.getValue(), entry.getKey())));
            }
        }
        List<Double> quality = null;
        if (json.has("accessory_quality")) {
            JsonArray array = Json.array(json, "accessory_quality");
            if (array.size() != 5) {
                throw new DefinitionException("accessory_quality は★1〜5の5つの数にしてください");
            }
            quality = new ArrayList<>();
            for (JsonElement element : array) {
                quality.add(Json.asNumber(element, "accessory_quality"));
            }
        }
        double step = Json.optionalDouble(json, "level_step", -1.0, -1.0, 10.0);
        List<ResourceLocation> untradeable = Json.idList(json, "untradeable");
        return new Table(prices, rarity, quality == null ? null : List.copyOf(quality), step, List.copyOf(untradeable));
    }

    /** 品の基準価格（表になければ0）。 */
    public static long base(ResourceLocation item) {
        long found = 0;
        for (Table table : LOADER.all().values()) {
            Long price = table.prices().get(item);
            if (price != null) {
                found = price;
            }
        }
        return found;
    }

    /**
     * 品の基準価格（1個あたり）。表にあればその値（アクセサリーは品質の倍率をかける）。なければレアリティごとの基準額 ×
     * （1＋0.08×装備レベル）。どちらもなければ0（フリーマーケットでは値段の幅を決められないので、出品できない）。
     */
    public static long base(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        double price = base(id);
        if (price <= 0) {
            Long rarityBase = null;
            String rarity = rarityOf(stack).id();
            for (Table table : LOADER.all().values()) {
                Long value = table.rarityBase().get(rarity);
                if (value != null) {
                    rarityBase = value;
                }
            }
            if (rarityBase == null || rarityBase <= 0) {
                return 0;
            }
            price = rarityBase * (1.0 + levelStep() * level(stack));
        }
        if (stack.getItem() instanceof AccessoryItem) {
            int stars = AccessoryData.storedQuality(stack);
            if (stars >= 1 && stars <= 5) {
                price *= quality().get(stars - 1);
            }
        }
        return Math.max(1L, Math.round(price));
    }

    /** フリーマーケットに出せない品か（クエストの品など。{@code untradeable}）。 */
    public static boolean untradeable(ResourceLocation item) {
        for (Table table : LOADER.all().values()) {
            if (table.untradeable().contains(item)) {
                return true;
            }
        }
        return false;
    }

    /** アクセサリーの品質の倍率（★1〜5）。 */
    public static List<Double> quality() {
        List<Double> found = DEFAULT_QUALITY;
        for (Table table : LOADER.all().values()) {
            if (table.quality() != null) {
                found = table.quality();
            }
        }
        return found;
    }

    /** 表にない物の、装備レベル1つあたりの上がり幅。 */
    public static double levelStep() {
        double found = DEFAULT_LEVEL_STEP;
        for (Table table : LOADER.all().values()) {
            if (table.levelStep() >= 0) {
                found = table.levelStep();
            }
        }
        return found;
    }

    /** 装備レベル（装備は必要なレベル。ほかは0）。 */
    private static int level(ItemStack stack) {
        EquipmentDef def = EquipmentDefs.server().get(stack);
        return def == null ? 0 : def.requiredLevel();
    }

    /** 品のレアリティ（装備・アクセサリーはその品の、素材は素材の、ほかはふつう）。 */
    public static Rarity rarityOf(ItemStack stack) {
        EquipmentDef def = EquipmentDefs.server().get(stack);
        if (def != null) {
            return GearData.rarity(stack, def);
        }
        if (stack.getItem() instanceof AccessoryItem) {
            return AccessoryData.rarity(stack, Rarity.UNCOMMON);
        }
        if (stack.getItem() instanceof MaterialItem material) {
            return material.amemaruRarity();
        }
        return Rarity.COMMON;
    }
}
