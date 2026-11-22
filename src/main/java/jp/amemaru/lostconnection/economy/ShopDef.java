package jp.amemaru.lostconnection.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 店の定義（{@code data/<名前空間>/amemaru/shops/<店>.json}。Phase 6）：店の名前・店主・売る品・買い取る品。
 *
 * <pre>{
 *   "name": "雑貨屋", "keeper": "マルタ・ポム",
 *   "sells": [{"item": "amemaru:healing_potion_small", "price": 100}],
 *   "buys":  [{"item": "amemaru:moss_iron", "ratio": 0.5}, {"item": "minecraft:bread", "price": 7}]
 * }</pre>
 *
 * <p>買い取りの値段は {@code price}（ノット）か、{@code ratio}（相場の基準価格 {@link MarketPrices} に対する割合）で書く。</p>
 */
public record ShopDef(ResourceLocation id, String name, String keeper, List<Entry> sells, List<Entry> buys) {
    /** 1つの品：値段（ノット）か、基準価格に対する割合 */
    public record Entry(ResourceLocation item, long price, double ratio) {
        /** 値段（割合なら基準価格から。1以上）。 */
        public long resolvedPrice() {
            if (price > 0) {
                return price;
            }
            long base = MarketPrices.base(item);
            return base <= 0 ? 0L : Math.max(1L, Math.round(base * ratio));
        }

        void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(item);
            buf.writeVarLong(Math.max(0L, resolvedPrice()));
        }

        static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readResourceLocation(), buf.readVarLong(), 0.0);
        }
    }

    public static ShopDef parse(ResourceLocation id, JsonObject json) {
        String name = Json.string(json, "name");
        String keeper = Json.optionalString(json, "keeper", "");
        return new ShopDef(id, name, keeper, entries(json, "sells", false), entries(json, "buys", true));
    }

    private static List<Entry> entries(JsonObject json, String key, boolean allowRatio) {
        List<Entry> list = new ArrayList<>();
        JsonArray array = Json.optionalArray(json, key);
        if (array == null) {
            return list;
        }
        for (JsonElement element : array) {
            JsonObject entry = Json.asObject(element, key);
            ResourceLocation item = Json.id(entry, "item");
            long price = entry.has("price") ? Math.round(Json.number(entry, "price")) : 0L;
            double ratio = allowRatio && entry.has("ratio") ? Json.doubleInRange(entry, "ratio", 0.0, 10.0) : 0.0;
            if (price <= 0 && ratio <= 0) {
                throw new DefinitionException(key + " の " + item + "：price（1以上）か ratio を書いてください");
            }
            list.add(new Entry(item, price, ratio));
        }
        return list;
    }

    /** 画面に送る形（値段は決めた額にして送る）。 */
    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeUtf(name);
        buf.writeUtf(keeper);
        buf.writeVarInt(sells.size());
        for (Entry entry : sells) {
            entry.write(buf);
        }
        buf.writeVarInt(buys.size());
        for (Entry entry : buys) {
            entry.write(buf);
        }
    }

    public static ShopDef read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        String name = buf.readUtf();
        String keeper = buf.readUtf();
        int sellCount = Math.min(buf.readVarInt(), 256);
        List<Entry> sells = new ArrayList<>();
        for (int i = 0; i < sellCount; i++) {
            sells.add(Entry.read(buf));
        }
        int buyCount = Math.min(buf.readVarInt(), 256);
        List<Entry> buys = new ArrayList<>();
        for (int i = 0; i < buyCount; i++) {
            buys.add(Entry.read(buf));
        }
        return new ShopDef(id, name, keeper, sells, buys);
    }
}
