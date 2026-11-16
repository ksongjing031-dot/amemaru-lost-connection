package jp.amemaru.lostconnection.job.def;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * スキルの定義（{@code data/<名前空間>/amemaru/skills/<職業>/<名前>.json}。Phase 0-B 6章）。
 *
 * <p>スキルはどれも「接続技能」（仲間の糸から力を借りる技。Phase 0-A 19章）。職業ごとのスキルツリーの、
 * どの流派の何段目に置くか（{@code school}・{@code row}・{@code column}）もここで決める。</p>
 *
 * <p>段階（{@code rank}）は1から {@code max_rank} まで。{@code upper_rank} 以上の段階は、上位職にならないと覚えられない
 * （Phase 0-A 19章「上位職になると第3段と奥義が開く」）。奥義（{@code ultimate}）は上位職だけが覚えられる。</p>
 */
public final class SkillDef {
    /** 覚える前に要るスキルと段階。 */
    public record Requirement(ResourceLocation skill, int rank) {
    }

    private final ResourceLocation id;
    private final ResourceLocation job;
    private final String school;
    private final int row;
    private final int column;
    private final SkillKind kind;
    private final boolean ultimate;
    private final String name;
    private final String description;
    private final ResourceLocation icon;
    private final int maxRank;
    private final int[] rankCosts;
    private final int upperRank;
    private final int minLevel;
    private final List<Requirement> requirements;
    // アクティブだけ
    private final Ranked mpCost;
    private final Ranked gaugeCost;
    private final Ranked cooldown;
    private final Ranked cast;
    private final double castSupportMultiplier;
    private final LinkType link;
    private final DeliveryDef delivery;
    private final List<EffectDef> effects;
    // パッシブだけ
    @Nullable
    private final PassiveDef passive;
    // 演出
    private final String animation;
    private final String castAnimation;
    private final String fx;
    @Nullable
    private final ResourceLocation sound;
    @Nullable
    private final ResourceLocation castSound;
    @Nullable
    private final ResourceLocation impactSound;

    private SkillDef(Builder b) {
        this.id = b.id;
        this.job = b.job;
        this.school = b.school;
        this.row = b.row;
        this.column = b.column;
        this.kind = b.kind;
        this.ultimate = b.ultimate;
        this.name = b.name;
        this.description = b.description;
        this.icon = b.icon;
        this.maxRank = b.maxRank;
        this.rankCosts = b.rankCosts;
        this.upperRank = b.upperRank;
        this.minLevel = b.minLevel;
        this.requirements = b.requirements;
        this.mpCost = b.mpCost;
        this.gaugeCost = b.gaugeCost;
        this.cooldown = b.cooldown;
        this.cast = b.cast;
        this.castSupportMultiplier = b.castSupportMultiplier;
        this.link = b.link;
        this.delivery = b.delivery;
        this.effects = b.effects;
        this.passive = b.passive;
        this.animation = b.animation;
        this.castAnimation = b.castAnimation;
        this.fx = b.fx;
        this.sound = b.sound;
        this.castSound = b.castSound;
        this.impactSound = b.impactSound;
    }

    // ---------------------------------------------------------------- 読み込み

    public static SkillDef parse(ResourceLocation id, JsonObject json) {
        Builder b = new Builder();
        b.id = id;
        b.job = Json.id(json, "job");
        b.school = Json.string(json, "school");
        b.row = Json.intInRange(json, "row", 0, 5);
        b.column = Json.optionalInt(json, "column", 0, 0, 3);
        b.kind = SkillKind.byId(Json.optionalString(json, "type", "active"));
        b.ultimate = Json.optionalBoolean(json, "ultimate", false);
        b.name = Json.string(json, "name");
        b.description = Json.string(json, "description");
        ResourceLocation icon = Json.optionalId(json, "icon");
        b.icon = icon != null ? icon : ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/gui/skill/" + id.getPath() + ".png");

        b.maxRank = Json.optionalInt(json, "max_rank", b.ultimate ? 1 : 3, 1, 5);
        b.rankCosts = readCosts(json, b.maxRank);
        b.upperRank = Json.optionalInt(json, "upper_rank", b.ultimate ? 1 : b.maxRank, 1, b.maxRank + 1);
        if (b.ultimate && b.upperRank != 1) {
            throw new DefinitionException("奥義（ultimate）は「upper_rank」を1にしてください（上位職だけが覚えられるため）");
        }
        b.minLevel = Json.optionalInt(json, "min_level", 1, 1, 200);
        b.requirements = readRequirements(json);

        if (b.kind == SkillKind.ACTIVE) {
            JsonObject cost = Json.optionalObject(json, "cost");
            b.mpCost = cost == null ? Ranked.ZERO : Ranked.readOptional(cost, "mp", 0.0, 0.0, 10000.0);
            b.gaugeCost = cost == null ? Ranked.ZERO : Ranked.readOptional(cost, "gauge", 0.0, 0.0, 1000.0);
            b.mpCost.checkRange("cost.mp", b.maxRank, 0.0, 10000.0);
            b.gaugeCost.checkRange("cost.gauge", b.maxRank, 0.0, 1000.0);
            b.cooldown = Ranked.read(json, "cooldown", 0.0, 3600.0);
            b.cooldown.checkRange("cooldown", b.maxRank, 0.0, 3600.0);
            b.cast = Ranked.readOptional(json, "cast", 0.0, 0.0, 30.0);
            b.cast.checkRange("cast", b.maxRank, 0.0, 30.0);
            b.castSupportMultiplier = Json.optionalDouble(json, "cast_support_multiplier", 1.0, 0.05, 1.0);
            b.link = LinkType.byId(Json.optionalString(json, "link", "none"));
            b.delivery = DeliveryDef.parse(Json.object(json, "delivery"), b.maxRank);
            b.effects = EffectDef.parseList(Json.optionalArray(json, "effects"), b.maxRank, false);
            if (b.effects.isEmpty() && b.delivery.type() != DeliveryDef.Type.BLINK && b.delivery.type() != DeliveryDef.Type.DASH) {
                throw new DefinitionException("「effects」が空です（瞬間移動・踏み込みのスキル以外は、効果が1つ以上要ります）");
            }
            if (json.has("passive")) {
                throw new DefinitionException("アクティブのスキルに「passive」は書けません");
            }
            b.passive = null;
        } else {
            if (b.ultimate) {
                throw new DefinitionException("パッシブのスキルは奥義（ultimate）にできません");
            }
            b.mpCost = Ranked.ZERO;
            b.gaugeCost = Ranked.ZERO;
            b.cooldown = Ranked.ZERO;
            b.cast = Ranked.ZERO;
            b.castSupportMultiplier = 1.0;
            b.link = LinkType.NONE;
            b.delivery = DeliveryDef.NONE;
            b.effects = List.of();
            b.passive = PassiveDef.parse(Json.object(json, "passive"), b.maxRank);
        }

        String defaultFx = id.getPath().replace('/', '_');
        b.animation = Json.optionalString(json, "animation", b.kind == SkillKind.ACTIVE ? defaultFx : "");
        b.castAnimation = Json.optionalString(json, "cast_animation", "");
        b.fx = Json.optionalString(json, "fx", defaultFx);
        b.sound = Json.optionalId(json, "sound");
        b.castSound = Json.optionalId(json, "cast_sound");
        b.impactSound = Json.optionalId(json, "impact_sound");
        return new SkillDef(b);
    }

    private static int[] readCosts(JsonObject json, int maxRank) {
        int[] costs = new int[maxRank];
        if (!json.has("rank_costs")) {
            for (int i = 0; i < maxRank; i++) {
                costs[i] = i + 1;
            }
            return costs;
        }
        JsonArray array = Json.array(json, "rank_costs");
        if (array.size() != maxRank) {
            throw new DefinitionException("「rank_costs」の数（" + array.size() + "）を「max_rank」（" + maxRank + "）と同じにしてください");
        }
        for (int i = 0; i < maxRank; i++) {
            double value = Json.asNumber(array.get(i), "rank_costs[" + i + "]");
            if (value != Math.floor(value) || value < 0 || value > 20) {
                throw new DefinitionException("「rank_costs」は0〜20の整数にしてください");
            }
            costs[i] = (int) value;
        }
        return costs;
    }

    private static List<Requirement> readRequirements(JsonObject json) {
        List<Requirement> list = new ArrayList<>();
        JsonArray array = Json.optionalArray(json, "requires");
        for (int i = 0; i < array.size(); i++) {
            JsonObject entry = Json.asObject(array.get(i), "requires[" + i + "]");
            list.add(new Requirement(Json.id(entry, "skill"), Json.optionalInt(entry, "rank", 1, 1, 5)));
        }
        return Collections.unmodifiableList(list);
    }

    // ---------------------------------------------------------------- 中身

    public ResourceLocation id() {
        return id;
    }

    public ResourceLocation job() {
        return job;
    }

    /** 流派のID（職業の定義の schools のどれか）。 */
    public String school() {
        return school;
    }

    /** スキルツリーの何段目か（0から）。 */
    public int row() {
        return row;
    }

    /** スキルツリーの流派の中の列（0：アクティブの列、1：パッシブの列）。 */
    public int column() {
        return column;
    }

    public SkillKind kind() {
        return kind;
    }

    public boolean isActive() {
        return kind == SkillKind.ACTIVE;
    }

    /** 奥義（上位職だけが覚えられる）か。 */
    public boolean isUltimate() {
        return ultimate;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ResourceLocation icon() {
        return icon;
    }

    public int maxRank() {
        return maxRank;
    }

    /** その段階に上げるのに要るスキルポイント（1から）。 */
    public int rankCost(int rank) {
        if (rank < 1 || rank > maxRank) {
            return 0;
        }
        return rankCosts[rank - 1];
    }

    /** 1段目からその段階までに使うスキルポイントの合計。 */
    public int totalCost(int rank) {
        int total = 0;
        for (int r = 1; r <= Math.min(rank, maxRank); r++) {
            total += rankCost(r);
        }
        return total;
    }

    /** この段階以上は上位職だけ（{@link #maxRank()}+1 なら、上位職でなくても全部覚えられる）。 */
    public int upperRank() {
        return upperRank;
    }

    /** 1段目を覚えられるレベル。 */
    public int minLevel() {
        return minLevel;
    }

    public List<Requirement> requirements() {
        return requirements;
    }

    public int mpCost(int rank) {
        return (int) Math.round(Math.max(0, mpCost.at(rank)));
    }

    public int gaugeCost(int rank) {
        return (int) Math.round(Math.max(0, gaugeCost.at(rank)));
    }

    /** クールダウン（秒。クールダウン短縮をかける前）。 */
    public double cooldown(int rank) {
        return Math.max(0, cooldown.at(rank));
    }

    /** 詠唱の時間（秒。詠唱短縮をかける前）。 */
    public double cast(int rank) {
        return Math.max(0, cast.at(rank));
    }

    /** 味方の支援を受けているときの詠唱の倍率（1なら変わらない）。 */
    public double castSupportMultiplier() {
        return castSupportMultiplier;
    }

    public LinkType link() {
        return link;
    }

    public DeliveryDef delivery() {
        return delivery;
    }

    public List<EffectDef> effects() {
        return effects;
    }

    @Nullable
    public PassiveDef passive() {
        return passive;
    }

    /** 使ったときのモーションの名前（{@code assets/<名前空間>/player_animation/} のアニメーション名）。空ならなし。 */
    public String animation() {
        return animation;
    }

    /** 詠唱中のモーションの名前。空ならなし。 */
    public String castAnimation() {
        return castAnimation;
    }

    /** 演出（パーティクルの出し方）の名前。 */
    public String fx() {
        return fx;
    }

    @Nullable
    public ResourceLocation sound() {
        return sound;
    }

    @Nullable
    public ResourceLocation castSound() {
        return castSound;
    }

    @Nullable
    public ResourceLocation impactSound() {
        return impactSound;
    }

    private static final class Builder {
        ResourceLocation id;
        ResourceLocation job;
        String school;
        int row;
        int column;
        SkillKind kind;
        boolean ultimate;
        String name;
        String description;
        ResourceLocation icon;
        int maxRank;
        int[] rankCosts;
        int upperRank;
        int minLevel;
        List<Requirement> requirements;
        Ranked mpCost;
        Ranked gaugeCost;
        Ranked cooldown;
        Ranked cast;
        double castSupportMultiplier;
        LinkType link;
        DeliveryDef delivery;
        List<EffectDef> effects;
        PassiveDef passive;
        String animation;
        String castAnimation;
        String fx;
        ResourceLocation sound;
        ResourceLocation castSound;
        ResourceLocation impactSound;
    }
}
