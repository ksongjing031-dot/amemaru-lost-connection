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
 * Modに同梱したJSON（{@code amemaru/item_types.json}）から、装備・アクセサリー・素材・結晶果・消耗品（Phase 6）のアイテムの種類を
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
        WEAPON, SHIELD, ARMOR, ACCESSORY, MATERIAL, FRUIT, CONSUMABLE
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
            manifest.add(register, Category.ACCESSORY, id, () -> new AccessoryItem(slot, visible, new Item.Properties()));
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
