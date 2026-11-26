package jp.amemaru.lostconnection.gacha;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 祈願（ガチャ）の決まり（データパックの {@code gacha/default.json}。11月27日のご指示「ガチャ限定の武器や装備を追加する。ガチャを回すときは
 * ボスを二回目以上から倒すと出る専用アイテムを使う。専用アイテムは各層のボスから各層ごとの専用アイテムを出す仕組みにして、ガチャも
 * 各層ごとのガチャにする。ガチャからはガチャ限のユニーク武器も作る」）。数は【補完】。
 *
 * <ul>
 *   <li>{@code boss_tokens}：その階のボスを2回目以降に倒すと、その階の祈願札をいくつもらえるか（参加した一人ずつ）</li>
 *   <li>{@code ten}：10回まとめて引くと、★4以上が1つは出る（{@code guarantee} の星より下だけだったら、最後の1つを上げる）</li>
 *   <li>{@code npc_range}：祈願所の巫女から、何ブロックまで離れても引けるか</li>
 *   <li>{@code banners}：階ごとの祈願。{@code token}（引くのに使う祈願札）・{@code rates}（★5・★4の確率。%。のこりは★3）・
 *       {@code pity}（★5が出ないまま何回目で必ず★5か。天井）・{@code pool}（星ごとの中身。{@code weight} の重さで選ぶ。
 *       {@code count} は数の幅）</li>
 * </ul>
 */
public final class GachaRules {
    public static final ResourceLocation DEFAULT_ID = AmemaruMod.id("default");
    private static final DefinitionLoader<GachaRules> LOADER = new DefinitionLoader<>("gacha", GachaRules::parse);
    public static final GachaRules FALLBACK = new GachaRules(5, 10, 4, 8, List.of());

    /** 祈願の中身のひとつ。 */
    public record Entry(ResourceLocation item, int minCount, int maxCount, int weight) {
    }

    /** 階ごとの祈願。 */
    public record Banner(String id, int floor, String name, String text, ResourceLocation token, int color, double rateFive,
                         double rateFour, int pity, List<Entry> five, List<Entry> four, List<Entry> three) {
        public List<Entry> pool(int stars) {
            return switch (stars) {
                case 5 -> five;
                case 4 -> four;
                default -> three;
            };
        }
    }

    private final int bossTokens;
    private final int tenCount;
    private final int tenGuarantee;
    private final int npcRange;
    private final List<Banner> banners;

    private GachaRules(int bossTokens, int tenCount, int tenGuarantee, int npcRange, List<Banner> banners) {
        this.bossTokens = bossTokens;
        this.tenCount = tenCount;
        this.tenGuarantee = tenGuarantee;
        this.npcRange = npcRange;
        this.banners = banners;
    }

    public static DefinitionLoader<GachaRules> loader() {
        return LOADER;
    }

    public static GachaRules current() {
        return LOADER.get(DEFAULT_ID).orElse(FALLBACK);
    }

    static GachaRules parse(JsonObject json) {
        int bossTokens = Json.optionalInt(json, "boss_tokens", 5, 0, 1000);
        JsonObject ten = Json.optionalObject(json, "ten");
        int tenCount = ten == null ? 10 : Json.optionalInt(ten, "count", 10, 2, 50);
        int tenGuarantee = ten == null ? 4 : Json.optionalInt(ten, "guarantee", 4, 3, 5);
        int npcRange = Json.optionalInt(json, "npc_range", 8, 1, 64);
        List<Banner> banners = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Integer> floors = new HashSet<>();
        JsonArray array = Json.array(json, "banners");
        for (int i = 0; i < array.size(); i++) {
            String where = "banners[" + i + "]";
            JsonObject one = Json.asObject(array.get(i), where);
            String id = Json.string(one, "id");
            if (!ids.add(id)) {
                throw new DefinitionException("祈願「" + id + "」が2つあります");
            }
            int floor = Json.optionalInt(one, "floor", 1, 1, 100);
            if (!floors.add(floor)) {
                throw new DefinitionException(floor + "階の祈願が2つあります");
            }
            JsonObject rates = Json.object(one, "rates");
            double five = Json.doubleInRange(rates, "5", 0.0, 100.0);
            double four = Json.doubleInRange(rates, "4", 0.0, 100.0);
            if (five + four > 100.0) {
                throw new DefinitionException("「" + where + ".rates」の★5と★4の合計が100%を超えています");
            }
            JsonObject pool = Json.object(one, "pool");
            List<Entry> fives = entries(pool, "5", where);
            List<Entry> fours = entries(pool, "4", where);
            List<Entry> threes = entries(pool, "3", where);
            if (fives.isEmpty() || fours.isEmpty() || threes.isEmpty()) {
                throw new DefinitionException("「" + where + ".pool」の★5・★4・★3に、それぞれ1つ以上入れてください");
            }
            banners.add(new Banner(id, floor, Json.string(one, "name"), Json.optionalString(one, "text", ""), Json.id(one, "token"),
                    Json.optionalColor(one, "color", 0xFFD27A), five, four, Json.optionalInt(one, "pity", 60, 1, 1000), List.copyOf(fives),
                    List.copyOf(fours), List.copyOf(threes)));
        }
        banners.sort(java.util.Comparator.comparingInt(Banner::floor));
        return new GachaRules(bossTokens, tenCount, tenGuarantee, npcRange, List.copyOf(banners));
    }

    private static List<Entry> entries(JsonObject pool, String key, String where) {
        List<Entry> list = new ArrayList<>();
        JsonArray array = Json.optionalArray(pool, key);
        for (int i = 0; i < array.size(); i++) {
            String at = where + ".pool." + key + "[" + i + "]";
            JsonObject one = Json.asObject(array.get(i), at);
            int min = 1;
            int max = 1;
            if (one.has("count")) {
                if (one.get("count").isJsonArray()) {
                    JsonArray range = one.getAsJsonArray("count");
                    if (range.size() != 2) {
                        throw new DefinitionException("「" + at + ".count」は [少ない数, 多い数] で書いてください");
                    }
                    min = range.get(0).getAsInt();
                    max = range.get(1).getAsInt();
                } else {
                    min = max = one.get("count").getAsInt();
                }
            }
            if (min < 1 || max < min || max > 64) {
                throw new DefinitionException("「" + at + ".count」は 1〜64 で、少ない数 ≦ 多い数 にしてください");
            }
            list.add(new Entry(Json.id(one, "item"), min, max, Json.optionalInt(one, "weight", 1, 1, 10000)));
        }
        return list;
    }

    public int bossTokens() {
        return bossTokens;
    }

    public int tenCount() {
        return tenCount;
    }

    public int tenGuarantee() {
        return tenGuarantee;
    }

    public int npcRange() {
        return npcRange;
    }

    public List<Banner> banners() {
        return banners;
    }

    @Nullable
    public Banner banner(String id) {
        for (Banner banner : banners) {
            if (banner.id().equals(id)) {
                return banner;
            }
        }
        return null;
    }

    @Nullable
    public Banner bannerOfFloor(int floor) {
        for (Banner banner : banners) {
            if (banner.floor() == floor) {
                return banner;
            }
        }
        return null;
    }
}
