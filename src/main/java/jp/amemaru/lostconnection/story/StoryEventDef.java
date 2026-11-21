package jp.amemaru.lostconnection.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ストーリーイベント（データパックの {@code story_events/}。Phase 0-B 6章）。きっかけ（{@code trigger}）が起きると、
 * 手順（{@code steps}）を上から順に行う。
 *
 * <ul>
 *   <li>{@code trigger.type}：{@code first_login}（初めてのログイン）・{@code floor_first_kill}（その階のボスの初撃破。{@code floor}）・
 *       {@code floor_enter}（その階に初めて入った）・{@code admin}（管理者が起こす）</li>
 *   <li>{@code scope}：見る人（{@code player}＝本人、{@code party}＝きっかけを作ったパーティ、{@code all}＝全員）。
 *       どの演出も、見る人それぞれの画面で、その人の速さで流す（同じ時刻にそろえない）</li>
 *   <li>{@code once}：一度だけ起きるか。{@code replayable}：大結晶の「記憶の再生」で見直せるか</li>
 *   <li>手順のうち、画面の演出（暗転・字幕・章題・音・カメラ・役者・粒・HUD）はクライアントが流し、
 *       物語の処理（フラグ・会話・手引き）は、見終えたときにサーバーが行う</li>
 * </ul>
 */
public record StoryEventDef(ResourceLocation id, String title, String triggerType, int triggerFloor, String scope, boolean once,
                            boolean replayable, Conditions conditions, List<Step> steps) {
    /** 画面の演出の手順（クライアントが流す）。 */
    public static final Set<String> CLIENT_STEPS = Set.of("amemaru:fade", "amemaru:wait", "amemaru:subtitle", "amemaru:title",
            "amemaru:sound", "amemaru:camera", "amemaru:actor", "amemaru:actor_move", "amemaru:actor_look", "amemaru:actor_remove",
            "amemaru:particles", "amemaru:hud");
    /** 物語の処理の手順（見終えたときにサーバーが行う）。 */
    public static final Set<String> SERVER_STEPS = Set.of("amemaru:set_flags", "amemaru:open_dialogue", "amemaru:guide");
    private static final Set<String> TRIGGERS = Set.of("first_login", "floor_first_kill", "floor_enter", "admin");
    private static final Set<String> SCOPES = Set.of("player", "party", "all");

    /** 手順1つ（{@code json} はそのまま。位置の「anchor」はサーバーが世界の座標に直してから送る）。 */
    public record Step(String type, JsonObject json) {
        public boolean client() {
            return CLIENT_STEPS.contains(type);
        }
    }

    public static StoryEventDef parse(ResourceLocation id, JsonObject json) {
        JsonObject trigger = Json.object(json, "trigger");
        String type = Json.parseId(Json.string(trigger, "type"), "trigger.type").getPath();
        if (!TRIGGERS.contains(type)) {
            throw new DefinitionException("「trigger.type」の「" + type + "」は知らないきっかけです（" + String.join("・", TRIGGERS) + "）");
        }
        String scope = Json.optionalString(json, "scope", "player");
        if (!SCOPES.contains(scope)) {
            throw new DefinitionException("「scope」は player・party・all のどれかにしてください");
        }
        List<Step> steps = new ArrayList<>();
        JsonArray array = Json.array(json, "steps");
        for (int i = 0; i < array.size(); i++) {
            JsonObject step = Json.asObject(array.get(i), "steps");
            String stepType = Json.parseId(Json.string(step, "type"), "steps.type").toString();
            if (!CLIENT_STEPS.contains(stepType) && !SERVER_STEPS.contains(stepType)) {
                throw new DefinitionException("「steps」の「" + stepType + "」は知らない手順です");
            }
            if (stepType.equals("amemaru:subtitle")) {
                String text = Json.string(step, "text");
                if (DialogueDef.length(text) > DialogueDef.TEXT_MAX) {
                    throw new DefinitionException("「steps」の字幕が長すぎます（" + DialogueDef.TEXT_MAX + "字まで）");
                }
            }
            steps.add(new Step(stepType, step));
        }
        return new StoryEventDef(id, Json.optionalString(json, "title", id.getPath()), type,
                Json.optionalInt(trigger, "floor", 0, 0, 99), scope, Json.optionalBoolean(json, "once", false),
                Json.optionalBoolean(json, "replayable", true), Conditions.parse(Json.optionalObject(json, "conditions")),
                List.copyOf(steps));
    }

    /** 画面の演出の長さのめやす（tick。見終えた知らせが来ないときに、ここから待つ時間を決める）。 */
    public int estimatedTicks() {
        int total = 0;
        for (Step step : steps) {
            if (step.client()) {
                total += Json.optionalInt(step.json(), "ticks", 0, 0, 20 * 600);
            }
        }
        return total;
    }
}
