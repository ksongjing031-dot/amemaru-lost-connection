package jp.amemaru.lostconnection.guild;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 古代記録（データパックの {@code records/}。Phase 0-B 6章「古代記録」、Phase 0-A 14章「古代記録と記録帳」）。
 *
 * <pre>
 * {
 *   "title": "道標の石板・一",
 *   "pages": ["ひとりで来たのなら、引き返せ。この先は、手をつなぐ者のための道"],
 *   "category": "history",                 // history・link・lost・null・people・unknown（伏線の記録は unknown＝「？？？」）
 *   "floor": 1, "hint": "1階、ボスの広間へ続く道の石板",
 *   "foreshadow": null,                    // Phase 0-A 18章の伏線の番号（関わらないものは null）
 *   "comment": "エルネストのひと言",
 *   "title_hint": "amemaru:lost_guide",    // この記録がほのめかす隠し称号（歴史の語り部の記録帳に出す）
 *   "order": 1
 * }
 * </pre>
 */
public record RecordDef(ResourceLocation id, String title, List<String> pages, Category category, int floor, String hint,
                        @Nullable Integer foreshadow, String comment, @Nullable ResourceLocation titleHint, int order) {
    /** 1ページの長さの上限（記録帳の1ページに収まる量）。 */
    public static final int PAGE_MAX = 200;

    /** 記録帳の分類（歴史／リンク／ロスト／NULL／人々／？？？）。 */
    public enum Category {
        HISTORY, LINK, LOST, NULL, PEOPLE, UNKNOWN;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Category parse(String text) {
            for (Category c : values()) {
                if (c.key().equals(text)) {
                    return c;
                }
            }
            throw new DefinitionException("「category」は history・link・lost・null・people・unknown のどれかにしてください（今は " + text + "）");
        }
    }

    public static RecordDef parse(ResourceLocation id, JsonObject json) {
        String title = Json.string(json, "title");
        List<String> pages = new ArrayList<>();
        JsonArray array = Json.array(json, "pages");
        for (int i = 0; i < array.size(); i++) {
            String page = Json.asString(array.get(i), "pages");
            if (page.length() > PAGE_MAX) {
                throw new DefinitionException("「pages」の" + (i + 1) + "ページ目が長すぎます（" + page.length() + "字。" + PAGE_MAX + "字まで）");
            }
            pages.add(page);
        }
        if (pages.isEmpty()) {
            throw new DefinitionException("「pages」が空です");
        }
        Integer foreshadow = json.has("foreshadow") && !json.get("foreshadow").isJsonNull()
                ? Json.intInRange(json, "foreshadow", 1, 99) : null;
        return new RecordDef(id, title, List.copyOf(pages), Category.parse(Json.optionalString(json, "category", "history")),
                Json.intInRange(json, "floor", 0, 20), Json.optionalString(json, "hint", ""), foreshadow,
                Json.optionalString(json, "comment", ""), Json.optionalId(json, "title_hint"), Json.optionalInt(json, "order", 0, 0, 100000));
    }
}
