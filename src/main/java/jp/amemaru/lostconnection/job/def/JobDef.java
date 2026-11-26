package jp.amemaru.lostconnection.job.def;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 職業の定義（{@code data/<名前空間>/amemaru/jobs/<名前>.json}。Phase 0-B 6章）。
 *
 * <p>職業は「大結晶への誓約」で授かる戦い方（Phase 0-A 13章）。ここには、役割・説明・使う武器・ステータスの伸び方・
 * 専用ゲージ・スキルツリーの流派・最初から使えるスキル・見習い装備・上位職を書く。スキルそのものは skills/ に置く。</p>
 */
public final class JobDef {
    public enum Kind {
        /** 戦闘職。 */
        COMBAT("combat"),
        /** 生産職（Phase 3で工房と一緒に使い始める）。 */
        PRODUCTION("production");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            throw new DefinitionException("「kind」は combat か production にしてください（今は " + id + "）");
        }
    }

    /** スキルツリーの流派。 */
    public record SchoolDef(String id, String name, String description, int color) {
    }

    /**
     * 専用ゲージ。{@code type} が働き（たまり方と使われ方）をコードの中から選び、数値は {@code params} で調整する。
     */
    public record GaugeDef(String type, String name, String description, int max, int color, Map<String, Double> params) {
        public double param(String key, double fallback) {
            Double value = params.get(key);
            return value == null ? fallback : value;
        }
    }

    /** 上位職（Phase 0-A 19章：11日目から、Lv60以上（11月24日の2回目に30から変えた）で昇格クエストを終えると昇格）。 */
    public record UpperDef(String name, String description, int minLevel, int minDay) {
    }

    /**
     * 連撃（モンク。Phase 6 その2【提案】）。違うスキルを {@code window} 秒以内に続けると連撃が1つずつ増え（{@code max} まで）、
     * 1つにつきダメージ +{@code perStep}%。【極】の技は連撃を使い、1つにつき さらに +{@code finisherPerStep}%。
     * 連撃が増えるたびに、専用ゲージが {@code gaugePerStep} たまる。
     */
    public record ComboDef(double window, int max, double perStep, double finisherPerStep, double gaugePerStep) {
        public int windowTicks() {
            return (int) Math.round(window * 20.0);
        }
    }

    /** 職業を選んだときに渡す見習い装備の1つ。 */
    public record KitEntry(ResourceLocation item, int count) {
    }

    /**
     * 攻撃のあとの硬直（11月26日のご指示「遠距離職は攻撃の際にスキルなどは硬直やほぼほぼ動けないスロウ効果などをつけましょう。
     * 動きながら打てるのが強すぎる」【補完】）。段階（{@code level}）は、1つで足が30%遅くなる（3でほぼ止まり、ジャンプとダッシュも
     * できない）。0の所は硬直なし。
     *
     * <ul>
     *   <li>攻撃のスキル（ダメージを与えるスキル。移動の技〈瞬間移動・跳躍・突進・後ろ跳び〉は除く）を使ったあと、
     *       {@code skillBase + skillPerCooldown × クールダウンの秒}（{@code skillMin}〜{@code skillMax} 秒）の間、{@code skillLevel}。
     *       {@code exemptSupport} なら、回復・強化・障壁などで仲間も支えるスキルは除く（ヒーラーの回復の鈴は動ける）</li>
     *   <li>攻撃のスキルの詠唱の間は {@code chantLevel}、溜めの間は {@code chargeLevel}</li>
     *   <li>通常の射撃（弓）を射るたびに、{@code basicSeconds} 秒の間 {@code basicLevel}</li>
     * </ul>
     * スキルの定義の {@code lock}（秒。0で硬直なし）があれば、そのスキルは計算の代わりにその秒。
     */
    public record AttackLock(double skillBase, double skillPerCooldown, double skillMin, double skillMax, int skillLevel,
                             boolean exemptSupport, int chantLevel, int chargeLevel, double basicSeconds, int basicLevel) {
        /** 足の遅さの段階の上限（3で −90%）。 */
        public static final int MAX_LEVEL = 3;

        /** 攻撃のスキルの硬直の秒（クールダウンの秒から）。 */
        public double skillSeconds(double cooldownSeconds) {
            return Math.max(skillMin, Math.min(skillMax, skillBase + skillPerCooldown * Math.max(0.0, cooldownSeconds)));
        }
    }

    private final ResourceLocation id;
    private final Kind kind;
    private final int order;
    private final String name;
    private final String role;
    private final String description;
    private final int color;
    private final ResourceLocation icon;
    @Nullable
    private final TagKey<Item> weapons;
    private final String weaponText;
    private final Set<String> tags;
    private final double[] statPercent;
    private final double[] statFlat;
    @Nullable
    private final GaugeDef gauge;
    private final List<SchoolDef> schools;
    private final List<ResourceLocation> startingSkills;
    private final List<KitEntry> starterKit;
    @Nullable
    private final UpperDef upper;
    /** 生産職の工房（weapon・armor・accessory。戦闘職は空）。 */
    private String workshop = "";
    /**
     * スキルの消費MPを、覚えられるレベルに合わせて増やす割合（1レベルにつき。11月23日のご指示「近接職のスキルの消費MPが、使える
     * ようになるレベルに対して少ない。レベルが上がるとMP切れが起こらない」）。消費MP ×（1 ＋ この値 ×（覚えられるレベル − 1））。
     */
    private double skillMpPerLevel;
    /** 連撃（ない職業は null）。 */
    @Nullable
    private ComboDef combo;
    /** 攻撃のあとの硬直（ない職業は null。11月26日）。 */
    @Nullable
    private AttackLock attackLock;

    private JobDef(ResourceLocation id, Kind kind, int order, String name, String role, String description, int color,
                   ResourceLocation icon, @Nullable TagKey<Item> weapons, String weaponText, Set<String> tags,
                   double[] statPercent, double[] statFlat, @Nullable GaugeDef gauge, List<SchoolDef> schools,
                   List<ResourceLocation> startingSkills, List<KitEntry> starterKit, @Nullable UpperDef upper) {
        this.id = id;
        this.kind = kind;
        this.order = order;
        this.name = name;
        this.role = role;
        this.description = description;
        this.color = color;
        this.icon = icon;
        this.weapons = weapons;
        this.weaponText = weaponText;
        this.tags = tags;
        this.statPercent = statPercent;
        this.statFlat = statFlat;
        this.gauge = gauge;
        this.schools = schools;
        this.startingSkills = startingSkills;
        this.starterKit = starterKit;
        this.upper = upper;
    }

    public static JobDef parse(ResourceLocation id, JsonObject json) {
        Kind kind = Kind.byId(Json.optionalString(json, "kind", "combat"));
        int order = Json.optionalInt(json, "order", 100, 0, 10000);
        String name = Json.string(json, "name");
        String role = Json.optionalString(json, "role", "");
        String description = Json.string(json, "description");
        int color = Json.optionalColor(json, "color", 0x8FD3FF);
        ResourceLocation icon = Json.optionalId(json, "icon");
        if (icon == null) {
            icon = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/gui/job/" + id.getPath() + ".png");
        }
        ResourceLocation weaponTag = Json.optionalId(json, "weapons");
        TagKey<Item> weapons = weaponTag == null ? null : TagKey.create(Registries.ITEM, weaponTag);
        String weaponText = Json.optionalString(json, "weapon_text", "");

        Set<String> tags = new HashSet<>();
        JsonArray tagArray = Json.optionalArray(json, "tags");
        for (int i = 0; i < tagArray.size(); i++) {
            tags.add(Json.asString(tagArray.get(i), "tags"));
        }

        double[] statPercent = new double[StatType.COUNT];
        double[] statFlat = new double[StatType.COUNT];
        JsonObject stats = Json.optionalObject(json, "stats");
        if (stats != null) {
            readStats(Json.optionalObject(stats, "percent"), statPercent, "stats.percent", -90.0, 500.0);
            readStats(Json.optionalObject(stats, "flat"), statFlat, "stats.flat", -10000.0, 10000.0);
        }

        GaugeDef gauge = null;
        JsonObject gaugeJson = Json.optionalObject(json, "gauge");
        if (gaugeJson != null) {
            Map<String, Double> params = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : gaugeJson.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                    params.put(entry.getKey(), value.getAsDouble());
                }
            }
            gauge = new GaugeDef(
                    Json.string(gaugeJson, "type"),
                    Json.string(gaugeJson, "name"),
                    Json.optionalString(gaugeJson, "description", ""),
                    Json.optionalInt(gaugeJson, "max", 100, 1, 10000),
                    Json.optionalColor(gaugeJson, "color", 0xFFC94D),
                    Collections.unmodifiableMap(params));
        }

        List<SchoolDef> schools = new ArrayList<>();
        JsonArray schoolArray = Json.optionalArray(json, "schools");
        Set<String> schoolIds = new HashSet<>();
        for (int i = 0; i < schoolArray.size(); i++) {
            JsonObject entry = Json.asObject(schoolArray.get(i), "schools[" + i + "]");
            String schoolId = Json.string(entry, "id");
            if (!schoolIds.add(schoolId)) {
                throw new DefinitionException("流派「" + schoolId + "」が2回あります");
            }
            schools.add(new SchoolDef(schoolId, Json.string(entry, "name"), Json.optionalString(entry, "description", ""),
                    Json.optionalColor(entry, "color", color)));
        }
        if (kind == Kind.COMBAT && schools.isEmpty()) {
            throw new DefinitionException("戦闘職には「schools」（スキルツリーの流派）が1つ以上要ります");
        }
        if (schools.size() > 3) {
            throw new DefinitionException("流派は3つまでにしてください（スキルツリーの画面に入りきらないため）");
        }

        List<ResourceLocation> startingSkills = Json.idList(json, "starting_skills");
        if (startingSkills.size() > 6) {
            throw new DefinitionException("「starting_skills」は6つまでにしてください（スキル枠が6つのため）");
        }

        List<KitEntry> kit = new ArrayList<>();
        JsonArray kitArray = Json.optionalArray(json, "starter_kit");
        for (int i = 0; i < kitArray.size(); i++) {
            JsonObject entry = Json.asObject(kitArray.get(i), "starter_kit[" + i + "]");
            kit.add(new KitEntry(Json.id(entry, "item"), Json.optionalInt(entry, "count", 1, 1, 64)));
        }

        UpperDef upper = null;
        JsonObject upperJson = Json.optionalObject(json, "upper");
        if (upperJson != null) {
            upper = new UpperDef(Json.string(upperJson, "name"), Json.optionalString(upperJson, "description", ""),
                    Json.optionalInt(upperJson, "min_level", 30, 1, 200), Json.optionalInt(upperJson, "min_day", 11, 1, 365));
        }

        JobDef def = new JobDef(id, kind, order, name, role, description, color, icon, weapons, weaponText,
                Collections.unmodifiableSet(tags), statPercent, statFlat, gauge, Collections.unmodifiableList(schools),
                Collections.unmodifiableList(startingSkills), Collections.unmodifiableList(kit), upper);
        def.workshop = Json.optionalString(json, "workshop", "");
        def.skillMpPerLevel = Json.optionalDouble(json, "skill_mp_per_level", 0.0, 0.0, 1.0);
        JsonObject comboJson = Json.optionalObject(json, "combo");
        if (comboJson != null) {
            def.combo = new ComboDef(
                    Json.optionalDouble(comboJson, "window", 3.0, 0.5, 30.0),
                    Json.optionalInt(comboJson, "max", 5, 1, 20),
                    Json.optionalDouble(comboJson, "per_step", 6.0, 0.0, 100.0),
                    Json.optionalDouble(comboJson, "finisher_per_step", 15.0, 0.0, 200.0),
                    Json.optionalDouble(comboJson, "ki_per_step", 5.0, 0.0, 1000.0));
        }
        JsonObject lockJson = Json.optionalObject(json, "attack_lock");
        if (lockJson != null) {
            JsonObject skill = Json.optionalObject(lockJson, "skill");
            JsonObject basic = Json.optionalObject(lockJson, "basic");
            double min = skill == null ? 0.0 : Json.optionalDouble(skill, "min", 0.0, 0.0, 5.0);
            double max = skill == null ? 0.0 : Json.optionalDouble(skill, "max", 0.0, 0.0, 5.0);
            if (max < min) {
                throw new DefinitionException("「attack_lock.skill」の max は min 以上にしてください");
            }
            def.attackLock = new AttackLock(
                    skill == null ? 0.0 : Json.optionalDouble(skill, "base", 0.0, 0.0, 5.0),
                    skill == null ? 0.0 : Json.optionalDouble(skill, "per_cooldown", 0.0, 0.0, 1.0),
                    min, max,
                    skill == null ? 0 : Json.optionalInt(skill, "level", 0, 0, AttackLock.MAX_LEVEL),
                    skill != null && Json.optionalBoolean(skill, "exempt_support", false),
                    Json.optionalInt(lockJson, "chant_level", 0, 0, AttackLock.MAX_LEVEL),
                    Json.optionalInt(lockJson, "charge_level", 0, 0, AttackLock.MAX_LEVEL),
                    basic == null ? 0.0 : Json.optionalDouble(basic, "seconds", 0.0, 0.0, 5.0),
                    basic == null ? 0 : Json.optionalInt(basic, "level", 0, 0, AttackLock.MAX_LEVEL));
        }
        return def;
    }

    private static void readStats(@Nullable JsonObject object, double[] target, String where, double min, double max) {
        if (object == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            StatType type = StatType.byId(entry.getKey()).orElseThrow(() ->
                    new DefinitionException("「" + where + "」に、ない種類のステータス「" + entry.getKey() + "」があります"));
            double value = Json.asNumber(entry.getValue(), where + "." + entry.getKey());
            if (value < min || value > max) {
                throw new DefinitionException("「" + where + "." + entry.getKey() + "」は " + min + "〜" + max + " の範囲にしてください");
            }
            target[type.ordinal()] = value;
        }
    }

    public ResourceLocation id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    /** 職業の一覧で並べる順（小さいほど先）。 */
    public int order() {
        return order;
    }

    public String name() {
        return name;
    }

    /** 役割（例：タンク）。 */
    public String role() {
        return role;
    }

    public String description() {
        return description;
    }

    public int color() {
        return color;
    }

    public ResourceLocation icon() {
        return icon;
    }

    /** スキルを使うときに手に持つ武器のタグ（ないときは何を持っていてもよい）。 */
    @Nullable
    public TagKey<Item> weapons() {
        return weapons;
    }

    /** 画面に出す、使う武器の説明（例：片手剣と盾・両手剣）。 */
    public String weaponText() {
        return weaponText;
    }

    /** 職業の性質（例：{@code magic} … 魔法を使う職業。魔力の共鳴などで使う）。 */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /** ステータスを割合で上げ下げする量（%）。 */
    public double statPercent(StatType type) {
        return statPercent[type.ordinal()];
    }

    /** ステータスにそのまま足す量。 */
    public double statFlat(StatType type) {
        return statFlat[type.ordinal()];
    }

    @Nullable
    public GaugeDef gauge() {
        return gauge;
    }

    /** 連撃（ない職業は null）。 */
    @Nullable
    public ComboDef combo() {
        return combo;
    }

    /** 攻撃のあとの硬直（ない職業は null。11月26日）。 */
    @Nullable
    public AttackLock attackLock() {
        return attackLock;
    }

    public List<SchoolDef> schools() {
        return schools;
    }

    public int schoolIndex(String schoolId) {
        for (int i = 0; i < schools.size(); i++) {
            if (schools.get(i).id().equals(schoolId)) {
                return i;
            }
        }
        return -1;
    }

    /** 職業を選んだときに、1段目を無料で覚えるスキル（スキル枠にも最初から入る）。 */
    public List<ResourceLocation> startingSkills() {
        return startingSkills;
    }

    public List<KitEntry> starterKit() {
        return starterKit;
    }

    /** 生産職の工房（weapon・armor・accessory。戦闘職は空）。 */
    public String workshop() {
        return workshop;
    }

    /** スキルの消費MPを、覚えられるレベルに合わせて増やす割合（1レベルにつき）。 */
    public double skillMpPerLevel() {
        return skillMpPerLevel;
    }

    @Nullable
    public UpperDef upper() {
        return upper;
    }
}
