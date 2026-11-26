package jp.amemaru.lostconnection.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.accessory.AccessorySlot;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modに同梱したJSON（{@code amemaru/item_types.json}）から、装備・アクセサリー・素材・結晶果・消耗品（Phase 6）・魚（Phase 7）のアイテムの種類を
 * 起動時に登録する（Phase 0-B 4.3「アイテムの種類（IDと見た目）は起動時に登録する必要があるため、Modに同梱したJSONから
 * 起動時に登録します。攻撃力やドロップ率などの数値は、データパックで後から変えられます」）。
 *
 * <p>このファイルはデータパックでは変えられない（アイテムの種類はゲームの起動時に決まるため）。足すときは、
 * 言語ファイル・モデル・絵と一緒に足す。数値は {@code data/amemaru/amemaru/equipment/}・{@code accessories/} に書く。</p>
 */
public final class ItemManifest {
    /** 同梱のJSONの場所（Modのjarの中）。 */
    public static final String PATH = "/amemaru/item_types.json";

    /** 登録したアイテム（IDの名前 → 登録の印）。 */
    private final Map<String, RegistryObject<Item>> items = new LinkedHashMap<>();
    /** 種類ごとの一覧（クリエイティブのタブとクライアントの登録に使う）。 */
    private final Map<Category, List<RegistryObject<Item>>> byCategory = new LinkedHashMap<>();

    /** アイテムの分類。 */
    public enum Category {
        WEAPON, SHIELD, ARMOR, ACCESSORY, MATERIAL, FRUIT, CONSUMABLE, FISH
    }

    private ItemManifest() {
        for (Category category : Category.values()) {
            byCategory.put(category, new ArrayList<>());
        }
    }

    /** 同梱のJSONを読み、すべてのアイテムを登録の予約に入れる。読めなければ起動を止める（ファイルの壊れはModの不具合のため）。 */
    public static ItemManifest load(DeferredRegister<Item> register) {
        JsonObject json = read();
        ItemManifest manifest = new ItemManifest();

        for (JsonObject entry : objects(json, "weapons")) {
            String id = string(entry, "id");
            WeaponKind kind = WeaponKind.byId(string(entry, "kind"));
            if (kind == null) {
                throw new IllegalStateException(PATH + "：武器 " + id + " の kind が分かりません");
            }
            boolean geo = "geckolib".equals(optional(entry, "model", "json"));
            manifest.add(register, Category.WEAPON, id, () -> geo
                    ? new GeoWeaponItem(kind, new Item.Properties())
                    : new AmemaruWeaponItem(kind, new Item.Properties()));
        }
        for (JsonObject entry : objects(json, "shields")) {
            String id = string(entry, "id");
            manifest.add(register, Category.SHIELD, id, () -> new AmemaruShieldItem(new Item.Properties()));
        }
        for (JsonObject entry : objects(json, "armor")) {
            String series = string(entry, "series");
            JsonObject pieces = entry.getAsJsonObject("pieces");
            for (Map.Entry<String, JsonElement> piece : pieces.entrySet()) {
                ArmorItem.Type type = switch (piece.getKey()) {
                    case "head" -> ArmorItem.Type.HELMET;
                    case "chest" -> ArmorItem.Type.CHESTPLATE;
                    case "legs" -> ArmorItem.Type.LEGGINGS;
                    case "feet" -> ArmorItem.Type.BOOTS;
                    default -> throw new IllegalStateException(PATH + "：防具の場所 " + piece.getKey() + " が分かりません");
                };
                String id = piece.getValue().getAsString();
                manifest.add(register, Category.ARMOR, id, () -> new AmemaruArmorItem(series, type, new Item.Properties()));
            }
        }
        for (JsonObject entry : objects(json, "accessories")) {
            String id = string(entry, "id");
            AccessorySlot slot = AccessorySlot.byId(string(entry, "slot"));
            if (slot == null) {
                throw new IllegalStateException(PATH + "：アクセサリー " + id + " の slot が分かりません");
            }
            boolean visible = "geckolib".equals(optional(entry, "model", "none"));
            boolean ear = entry.has("warning_ear") && entry.get("warning_ear").getAsBoolean();
            manifest.add(register, Category.ACCESSORY, id, () -> new AccessoryItem(slot, visible, ear, new Item.Properties()));
        }
        for (JsonObject entry : objects(json, "materials")) {
            String id = string(entry, "id");
            Rarity rarity = Rarity.byId(optional(entry, "rarity", "common"));
            Rarity resolved = rarity == null ? Rarity.COMMON : rarity;
            manifest.add(register, Category.MATERIAL, id, () -> new MaterialItem(resolved, new Item.Properties()));
        }
        for (JsonObject entry : objects(json, "fruits")) {
            String id = string(entry, "id");
            StatType stat = StatType.byId(string(entry, "stat"))
                    .orElseThrow(() -> new IllegalStateException(PATH + "：結晶果 " + id + " の stat が分かりません"));
            manifest.add(register, Category.FRUIT, id, () -> new CrystalFruitItem(stat, new Item.Properties().stacksTo(16)));
        }
        for (JsonObject entry : objects(json, "consumables")) {
            String id = string(entry, "id");
            int stack = entry.has("stack") ? entry.get("stack").getAsInt() : 16;
            int size = Math.max(1, Math.min(64, stack));
            manifest.add(register, Category.CONSUMABLE, id, () -> new ConsumableItem(new Item.Properties().stacksTo(size)));
        }
        // 11月23日の直し（店の品を実用的なものに）：店で売る道具。kind で品の働きを決める
        for (JsonObject entry : objects(json, "tools")) {
            String id = string(entry, "id");
            String kind = string(entry, "kind");
            int stack = entry.has("stack") ? entry.get("stack").getAsInt() : 16;
            int size = Math.max(1, Math.min(64, stack));
            java.util.function.Supplier<Item> factory = switch (kind) {
                case "return" -> () -> new ReturnFeatherItem(new Item.Properties().stacksTo(size));
                case "repair" -> () -> new RepairKitItem(new Item.Properties().stacksTo(size));
                case "lantern" -> () -> new HandLanternItem(new Item.Properties().stacksTo(size));
                case "vial" -> () -> new LanternVialItem(new Item.Properties().stacksTo(size));
                // Phase 8 その1：毒消しの薬（薬屋）と、2階の結び目の灯（迷いの森の鐘でもらう）
                case "antidote" -> () -> new AntidoteItem(new Item.Properties().stacksTo(size));
                case "knot_cord" -> () -> new KnotCordItem(new Item.Properties().stacksTo(size));
                case "knot" -> () -> new jp.amemaru.lostconnection.tower.forest.KnotLanternItem(new Item.Properties().stacksTo(size));
                case "warden" -> () -> new jp.amemaru.lostconnection.tower.forest.WardenLightItem(new Item.Properties().stacksTo(size));
                // 11月25日：6階の熱の結晶（熱の炉でもらう）と、記憶の結晶（ボスとの戦いの間に、広場のまわりの家に現れる）
                case "heat" -> () -> new jp.amemaru.lostconnection.tower.village.HeatCrystalItem(false, new Item.Properties().stacksTo(size));
                case "memory" -> () -> new jp.amemaru.lostconnection.tower.village.HeatCrystalItem(true, new Item.Properties().stacksTo(size));
                default -> throw new IllegalStateException(PATH + "：道具 " + id + " の kind が分かりません（" + kind + "）");
            };
            manifest.add(register, Category.CONSUMABLE, id, factory);
        }
        // Phase 7 その2：魚（★の数は rarity。数値はデータパックの fish/）
        for (JsonObject entry : objects(json, "fish")) {
            String id = string(entry, "id");
            int stars = entry.has("rarity") ? entry.get("rarity").getAsInt() : 1;
            manifest.add(register, Category.FISH, id, () -> new jp.amemaru.lostconnection.fishing.FishItem(stars, new Item.Properties()));
        }
        // Phase 7 その4：ペトラ・ポムの料理屋の料理（食べると、しばらく強くなる）
        for (JsonObject entry : objects(json, "dishes")) {
            String id = string(entry, "id");
            manifest.add(register, Category.CONSUMABLE, id, () -> new jp.amemaru.lostconnection.town.DishItem(new Item.Properties().stacksTo(16)
                    .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationMod(0.6F).alwaysEat().build())));
        }
        AmemaruMod.LOGGER.info("[amemaru] Registered {} item type(s) from {}", manifest.items.size(), PATH);
        return manifest;
    }

    private void add(DeferredRegister<Item> register, Category category, String id, java.util.function.Supplier<Item> factory) {
        if (items.containsKey(id)) {
            throw new IllegalStateException(PATH + "：アイテム " + id + " が2回あります");
        }
        RegistryObject<Item> object = register.register(id, factory);
        items.put(id, object);
        byCategory.get(category).add(object);
    }

    /** IDの名前から、登録の印を返す。なければ起動を止める（コードとJSONが合っていない）。 */
    public RegistryObject<Item> get(String id) {
        RegistryObject<Item> object = items.get(id);
        if (object == null) {
            throw new IllegalStateException(PATH + " に " + id + " がありません");
        }
        return object;
    }

    public List<RegistryObject<Item>> category(Category category) {
        return Collections.unmodifiableList(byCategory.get(category));
    }

    public Map<String, RegistryObject<Item>> all() {
        return Collections.unmodifiableMap(items);
    }

    // ---------------------------------------------------------------- JSON

    private static JsonObject read() {
        try (InputStream stream = ItemManifest.class.getResourceAsStream(PATH)) {
            if (stream == null) {
                throw new IllegalStateException(PATH + " がModの中にありません");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new IllegalStateException(PATH + " を読めませんでした", e);
        }
    }

    private static List<JsonObject> objects(JsonObject json, String key) {
        List<JsonObject> list = new ArrayList<>();
        if (!json.has(key)) {
            return list;
        }
        JsonArray array = json.getAsJsonArray(key);
        for (JsonElement element : array) {
            list.add(element.getAsJsonObject());
        }
        return list;
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new IllegalStateException(PATH + "：「" + key + "」がありません（" + json + "）");
        }
        return json.get(key).getAsString();
    }

    private static String optional(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }
}
