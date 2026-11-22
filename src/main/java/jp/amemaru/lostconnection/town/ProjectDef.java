package jp.amemaru.lostconnection.town;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

/**
 * 共同事業（データパックの {@code projects/<名前>.json}。Phase 0-B 6章「projects/：寄付目標（25人基準）、必要な輝き段階、完成したときの効果」。
 * Phase 0-A 7章「共同事業（寄付による施設の発展）【提案】」。Phase 7 その4）。
 *
 * @param goal     寄付目標（25人基準のノット。13章の人数補正で増減する）
 * @param stage    必要な大結晶の輝き段階
 * @param startDay 寄付を受け付け始める進行日（7章の「開始日の目安」）
 * @param flag     完成したときに付く物語の印（釣り・カジノ・料理屋などが見る）
 * @param overlay  完成した次の朝に重ねる、育ったあとの形（{@code map/conelia/} の後ろの名前。なければ空）
 * @param blessing 最終決戦の「結晶の加護」を上げる段（⑦）
 * @param result   完成すると（7章の表の文）
 */
public record ProjectDef(ResourceLocation id, int order, String name, String shortName, long goal, int stage, int startDay,
                         ResourceLocation flag, String overlay, int blessing, String result, String description) {

    static ProjectDef parse(ResourceLocation id, JsonObject json) {
        String overlay = Json.optionalString(json, "overlay", "");
        if (!overlay.isEmpty() && !overlay.matches("[a-z0-9_/]+")) {
            throw new DefinitionException("「overlay」は構造物の名前（小文字・数字・_・/）にしてください（今は " + overlay + "）");
        }
        return new ProjectDef(id,
                Json.intInRange(json, "order", 1, 100),
                Json.string(json, "name"),
                Json.optionalString(json, "short", Json.string(json, "name")),
                (long) Json.doubleInRange(json, "goal", 1, 1.0E12),
                Json.intInRange(json, "stage", 0, 10),
                Json.intInRange(json, "start_day", 0, 1000),
                Json.parseId(Json.string(json, "flag"), "flag"),
                overlay,
                Json.optionalInt(json, "blessing", 0, 0, 10),
                Json.optionalString(json, "result", ""),
                Json.optionalString(json, "description", ""));
    }
}
