package jp.amemaru.lostconnection.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会話（データパックの {@code dialogues/}。Phase 0-B 6章「会話」）。1つのファイルが、NPCの一つの会話。
 * 条件に合う会話のうち、{@code priority} の高いものが選ばれる。
 *
 * <pre>
 * {
 *   "npc": "amemaru:lilia", "priority": 50, "conditions": { … },
 *   "start": "greet",
 *   "nodes": {
 *     "greet":  { "speaker": "amemaru:lilia", "expression": "smile", "text": "…", "next": "choice" },
 *     "choice": { "choices": [ { "text": "…", "actions": [ … ], "next": "end" } ] }
 *   }
 * }
 * </pre>
 *
 * <ul>
 *   <li>台詞は1吹き出し全角40字以内（最大60字）、1回の会話は8吹き出しまで（Phase 0-A 14章「文章のルール」。読み込むときに確かめる）</li>
 *   <li>{@code {player}} は話している人の名前に置き換える</li>
 *   <li>{@code speaker} は NPC のID、{@code player}（本人）、{@code narration}（地の文）のどれか。省くと会話のNPC</li>
 *   <li>選択肢は最大4つ。正解・不正解を作らず、どれを選んでも物語は進む（Phase 0-A 14章）</li>
 *   <li>{@code next} が {@code end} か、ないと会話が終わる</li>
 * </ul>
 */
public record DialogueDef(ResourceLocation id, ResourceLocation npc, int priority, Conditions conditions, String start,
                          Map<String, Node> nodes, List<StoryAction> endActions) {
    /** 台詞の長さの上限（全角60字。40字をこえたら読み込むときに知らせる）。 */
    public static final int TEXT_MAX = 60;
    public static final int TEXT_RECOMMENDED = 40;
    public static final int BUBBLES_MAX = 8;
    public static final int CHOICES_MAX = 4;

    /** 1つの場面。{@code text} が空で {@code choices} だけの場面もある。 */
    public record Node(String key, @Nullable ResourceLocation speaker, String speakerKind, String expression, String text,
                       @Nullable String next, List<Choice> choices, List<StoryAction> actions) {
        public boolean hasChoices() {
            return !choices.isEmpty();
        }
    }

    /** 選択肢。 */
    public record Choice(String text, Conditions conditions, List<StoryAction> actions, @Nullable String next) {
    }

    @Nullable
    public Node node(@Nullable String key) {
        return key == null ? null : nodes.get(key);
    }

    public static DialogueDef parse(ResourceLocation id, JsonObject json) {
        ResourceLocation npc = Json.id(json, "npc");
        int priority = Json.optionalInt(json, "priority", 0, -1000, 1000);
        Conditions conditions = Conditions.parse(Json.optionalObject(json, "conditions"));
        String start = Json.string(json, "start");
        JsonObject nodesJson = Json.object(json, "nodes");
        Map<String, Node> nodes = new LinkedHashMap<>();
        int bubbles = 0;
        for (String key : nodesJson.keySet()) {
            JsonObject n = Json.asObject(nodesJson.get(key), "nodes." + key);
            String text = Json.optionalString(n, "text", "");
            if (length(text) > TEXT_MAX) {
                throw new DefinitionException("「nodes." + key + ".text」が長すぎます（" + length(text) + "字。1吹き出しは" + TEXT_MAX
                        + "字まで、目安は" + TEXT_RECOMMENDED + "字）");
            }
            if (!text.isEmpty()) {
                bubbles++;
            }
            String speakerText = Json.optionalString(n, "speaker", "");
            ResourceLocation speaker = null;
            String kind;
            if (speakerText.isEmpty()) {
                kind = "npc";
                speaker = npc;
            } else if ("player".equals(speakerText) || "narration".equals(speakerText) || "core".equals(speakerText)) {
                kind = speakerText;
            } else {
                kind = "npc";
                speaker = Json.parseId(speakerText, "nodes." + key + ".speaker");
            }
            List<Choice> choices = new ArrayList<>();
            JsonArray choiceArray = Json.optionalArray(n, "choices");
            if (choiceArray.size() > CHOICES_MAX) {
                throw new DefinitionException("「nodes." + key + ".choices」は" + CHOICES_MAX + "つまでにしてください");
            }
            for (int i = 0; i < choiceArray.size(); i++) {
                JsonObject c = Json.asObject(choiceArray.get(i), "choices");
                String choiceText = Json.string(c, "text");
                if (length(choiceText) > TEXT_RECOMMENDED) {
                    throw new DefinitionException("「nodes." + key + ".choices」の文が長すぎます（" + TEXT_RECOMMENDED + "字まで）");
                }
                choices.add(new Choice(choiceText, Conditions.parse(Json.optionalObject(c, "conditions")),
                        StoryAction.parseList(Json.optionalArray(c, "actions")), nextOf(c)));
            }
            nodes.put(key, new Node(key, speaker, kind, Json.optionalString(n, "expression", ""), text, nextOf(n), List.copyOf(choices),
                    StoryAction.parseList(Json.optionalArray(n, "actions"))));
        }
        if (!nodes.containsKey(start)) {
            throw new DefinitionException("「start」の「" + start + "」が「nodes」にありません");
        }
        for (Node node : nodes.values()) {
            check(nodes, node.next(), "nodes." + node.key() + ".next");
            for (Choice choice : node.choices()) {
                check(nodes, choice.next(), "nodes." + node.key() + ".choices.next");
            }
        }
        if (bubbles > BUBBLES_MAX * 2) {
            // 選択肢で分かれる会話は、1回に通る吹き出しが8つまでなら長くてよい（ここでは大きく外れたものだけ知らせる）
            throw new DefinitionException("吹き出しが多すぎます（" + bubbles + "。1回の会話は" + BUBBLES_MAX + "吹き出しまで）");
        }
        return new DialogueDef(id, npc, priority, conditions, start, Map.copyOf(nodes),
                StoryAction.parseList(Json.optionalArray(json, "end_actions")));
    }

    @Nullable
    private static String nextOf(JsonObject json) {
        String next = Json.optionalString(json, "next", "");
        return next.isEmpty() || "end".equals(next) ? null : next;
    }

    private static void check(Map<String, Node> nodes, @Nullable String next, String where) {
        if (next != null && !nodes.containsKey(next)) {
            throw new DefinitionException("「" + where + "」の「" + next + "」が「nodes」にありません");
        }
    }

    /** 文字数（{player} は数えない）。 */
    static int length(String text) {
        return text.replace("{player}", "").codePointCount(0, text.replace("{player}", "").length());
    }
}
