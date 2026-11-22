package jp.amemaru.lostconnection.guild;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * 称号（データパックの {@code titles/}。Phase 0-A 22章、Phase 0-B 6章「titles/：名前、説明、種類（通常・隠し・限定・ユニーク）、色、条件」）。
 *
 * <pre>
 * {
 *   "name": "見習い討伐者", "description": "敵を500体倒した",
 *   "kind": "normal",                       // normal・rare・hidden・limited・unique
 *   "field": "combat",                      // combat・craft・trade・social・leisure・story
 *   "series": "slayer", "tier": 1, "tiers": 3,   // 段階つき（いちばん上の段は金）
 *   "condition": { "type": "counter", "counter": "kills", "at_least": 500 },
 *   "hint": "ガルドの昔話",                  // 隠し称号：手がかりのある所（歴史の語り部の記録帳に出る）
 *   "effect": { "type": "listing_bonus", "amount": 5 },   // 段階の最上位だけ、暮らしの便利さを少し（22章【提案】）
 *   "order": 10
 * }
 * </pre>
 *
 * <p>条件の種類：{@code counter}（数が at_least 以上）・{@code affinity}（NPCの親密度の段階）・{@code boss}（ボスを倒す）・
 * {@code boss_no_down}（一度もダウンせずに章ボス）・{@code boss_guard}（章ボス戦で一番多く受け止めて勝つ）・{@code event}（できごと）・
 * {@code first}（サーバーで最初。限定）・{@code flag}（物語フラグ）・{@code zero_earn}（0ノットからの稼ぎ）・{@code admin}（管理者だけ）。
 * まだない仕組みの条件（釣り・カジノ・結び札など）は、その仕組みを作るPhaseで数え始める。</p>
 */
public record TitleDef(ResourceLocation id, String name, String description, Kind kind, Field field, String series, int tier,
                       int tiers, Condition condition, String hint, @Nullable Effect effect, int order, int color, boolean rainbow) {

    /** 種類（Phase 0-A 22章「種類と表示の色」）。 */
    public enum Kind {
        /** 通常：条件を満たせば誰でも。白（段階の最上位は金）。 */
        NORMAL(0xFFFFFF),
        /** レア：難しい条件の通常称号。青。 */
        RARE(0x5DA9FF),
        /** 隠し：条件を公開しない。手に入れるまで一覧では「？？？」。紫。 */
        HIDDEN(0xC285FF),
        /** 限定：サーバーで最初の1人（または1パーティ）だけ。赤金、ゆっくり光る。 */
        LIMITED(0xE8A04A),
        /** ユニーク（運営専用）：管理者コマンドでしかもらえない。虹色、色が変わり続ける。 */
        UNIQUE(0xFF7AD9);

        private final int color;

        Kind(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind parse(String text) {
            for (Kind kind : values()) {
                if (kind.key().equals(text)) {
                    return kind;
                }
            }
            throw new DefinitionException("「kind」は normal・rare・hidden・limited・unique のどれかにしてください（今は " + text + "）");
        }
    }

    /** 分野（22章の表の「分野」。一覧のタブ）。 */
    public enum Field {
        COMBAT, CRAFT, TRADE, SOCIAL, LEISURE, STORY;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Field parse(String text) {
            for (Field field : values()) {
                if (field.key().equals(text)) {
                    return field;
                }
            }
            throw new DefinitionException("「field」は combat・craft・trade・social・leisure・story のどれかにしてください（今は " + text + "）");
        }
    }

    /** 条件の種類。 */
    public static final List<String> CONDITION_TYPES = List.of("counter", "affinity", "boss", "boss_no_down", "boss_guard", "event",
            "first", "flag", "zero_earn", "admin");

    /** 条件。{@code key} は数の名前・できごとの名前、{@code target} はNPC・ボス・フラグのID、{@code amount} は数・段階・額。 */
    public record Condition(String type, String key, @Nullable ResourceLocation target, long amount) {
        public static Condition parse(JsonObject json) {
            String type = Json.string(json, "type");
            if (!CONDITION_TYPES.contains(type)) {
                throw new DefinitionException("条件の「type」は " + String.join("・", CONDITION_TYPES) + " のどれかにしてください（今は " + type + "）");
            }
            return switch (type) {
                case "counter" -> {
                    String counter = Json.string(json, "counter");
                    if (!GuildCounters.ALL.contains(counter)) {
                        throw new DefinitionException("条件の「counter」は " + String.join("・", GuildCounters.ALL) + " のどれかにしてください（今は "
                                + counter + "）");
                    }
                    yield new Condition(type, counter, null, (long) Json.doubleInRange(json, "at_least", 1, 1.0e12));
                }
                case "affinity" -> new Condition(type, "", Json.id(json, "npc"), Json.intInRange(json, "stage", 1, BondService.MAX_STAGE));
                case "boss", "boss_no_down", "boss_guard" -> new Condition(type, "", Json.optionalId(json, "boss"),
                        Json.optionalBoolean(json, "chapter_boss", false) ? 1 : 0);
                case "event", "first" -> new Condition(type, Json.string(json, "event"), null, 0);
                case "flag" -> new Condition(type, "", Json.id(json, "flag"), 0);
                case "zero_earn" -> new Condition(type, "", null, (long) Json.doubleInRange(json, "amount", 1, 1.0e12));
                default -> new Condition(type, "", null, 0);
            };
        }
    }

    /** 効果（22章「効果についての提案」：戦闘力には一切影響させない。段階の最上位だけ、暮らしの便利さを少し）。 */
    public record Effect(String type, int amount) {
        public static final List<String> TYPES = List.of("listing_bonus", "fishing_gauge", "record_hints");

        public static Effect parse(JsonObject json) {
            String type = Json.string(json, "type");
            if (!TYPES.contains(type)) {
                throw new DefinitionException("効果の「type」は " + String.join("・", TYPES) + " のどれかにしてください（今は " + type + "）");
            }
            return new Effect(type, Json.optionalInt(json, "amount", 0, 0, 1000));
        }
    }

    public static TitleDef parse(ResourceLocation id, JsonObject json) {
        String name = Json.string(json, "name");
        if (name.isBlank() || name.length() > 16) {
            throw new DefinitionException("「name」は1〜16文字にしてください（今は「" + name + "」）");
        }
        Kind kind = Kind.parse(Json.optionalString(json, "kind", "normal"));
        Field field = Field.parse(Json.optionalString(json, "field", "story"));
        int tiers = Json.optionalInt(json, "tiers", 1, 1, 9);
        int tier = Json.optionalInt(json, "tier", 1, 1, tiers);
        Condition condition = Condition.parse(Json.object(json, "condition"));
        if (kind == Kind.UNIQUE && !"admin".equals(condition.type())) {
            throw new DefinitionException("ユニーク称号の条件は「admin」だけです");
        }
        if (kind == Kind.LIMITED && !"first".equals(condition.type())) {
            throw new DefinitionException("限定称号の条件は「first」（サーバーで最初）にしてください");
        }
        JsonObject effect = Json.optionalObject(json, "effect");
        int color = Json.optionalColor(json, "color", -1);
        int shown = color >= 0 ? color : (kind == Kind.NORMAL && tiers > 1 && tier == tiers ? 0xF2C94C : kind.color());
        return new TitleDef(id, name, Json.optionalString(json, "description", ""), kind, field,
                Json.optionalString(json, "series", ""), tier, tiers, condition, Json.optionalString(json, "hint", ""),
                effect == null ? null : Effect.parse(effect), Json.optionalInt(json, "order", 1000, 0, 100000), shown,
                kind == Kind.UNIQUE);
    }

    /** 段階の最上位か（白の段階つきなら金で出す）。 */
    public boolean topTier() {
        return tiers > 1 && tier == tiers;
    }
}
