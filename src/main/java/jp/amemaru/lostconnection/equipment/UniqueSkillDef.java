package jp.amemaru.lostconnection.equipment;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.job.def.SkillDef;
import net.minecraft.resources.ResourceLocation;

/**
 * ユニーク武器の固有スキル（{@code data/<名前空間>/amemaru/unique_skills/<名前>.json}。11月28日のご指示「ユニーク武器や装備には
 * 特殊効果や特殊スキルをつけましょう」【補完】）。
 *
 * <p>その武器を手に持っているときだけ、固有スキルのキー（初期値：Y）で使える。職業のスキルツリーには入らず、段階もない（1段だけ）。
 * 中身（届け方・効果・MP・クールダウン・モーション・音）は職業のスキルと同じ書き方で、{@link SkillDef} として読む。
 * 詠唱と溜めは使えない（押した瞬間に出る）。{@code item} は固有スキルを持つ武器、{@code color} は演出の色、{@code fx} は演出の名前
 * （{@code unique_} で始める。{@code client.UniqueSkillFx}）。</p>
 *
 * @param id    定義のID（ファイルの名前）
 * @param item  固有スキルを持つ武器
 * @param color 演出の色（0xRRGGBB）
 * @param skill スキルとしての中身（IDは {@code amemaru:unique/<名前>}）
 */
public record UniqueSkillDef(ResourceLocation id, ResourceLocation item, int color, SkillDef skill) {
    /** 固有スキルの「職業」（どの職業のツリーにも入らない印）。 */
    public static final ResourceLocation PSEUDO_JOB = AmemaruMod.id("unique");

    public static UniqueSkillDef parse(ResourceLocation id, JsonObject json) {
        ResourceLocation item = Json.id(json, "item");
        int color = Json.optionalColor(json, "color", 0xFFE08A);
        for (String key : new String[]{"job", "school", "row", "column", "ultimate", "max_rank", "rank_costs", "upper_rank", "requires",
                "charge", "passive"}) {
            if (json.has(key)) {
                throw new DefinitionException("固有スキルに「" + key + "」は書けません（ツリーにも段階にも入らないため）");
            }
        }
        String fx = Json.optionalString(json, "fx", "unique_" + id.getPath());
        if (!fx.startsWith("unique_")) {
            throw new DefinitionException("固有スキルの「fx」は unique_ で始めてください（今は " + fx + "）");
        }
        JsonObject copy = json.deepCopy();
        copy.remove("item");
        copy.remove("color");
        copy.add("job", new JsonPrimitive(PSEUDO_JOB.toString()));
        copy.add("school", new JsonPrimitive("unique"));
        copy.add("row", new JsonPrimitive(0));
        copy.add("type", new JsonPrimitive("active"));
        copy.add("max_rank", new JsonPrimitive(1));
        copy.add("fx", new JsonPrimitive(fx));
        SkillDef skill = SkillDef.parse(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "unique/" + id.getPath()), copy);
        if (skill.cast(1) > 0) {
            throw new DefinitionException("固有スキルに詠唱（cast）は付けられません（押した瞬間に出す）");
        }
        if (skill.cooldown(1) < 5) {
            throw new DefinitionException("固有スキルのクールダウン（cooldown）は5秒以上にしてください");
        }
        return new UniqueSkillDef(id, item, color & 0xFFFFFF, skill);
    }

    /** 名前（固有スキルの名前）。 */
    public String name() {
        return skill.name();
    }
}
