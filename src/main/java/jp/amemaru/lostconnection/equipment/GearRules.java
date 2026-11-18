package jp.amemaru.lostconnection.equipment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 装備の共通の決まり（{@code data/amemaru/amemaru/gear_rules/default.json}）。数値はデータパックで変えられる。
 *
 * <ul>
 *   <li>レアリティごとのステータスの倍率【補完】（コモン1.00・アンコモン1.08・レア1.16・エピック1.25・レジェンダリー1.35・ユニーク1.40）</li>
 *   <li>ステータスの抽選の幅【補完】（定義の数値の70〜100%）と、工房の「大成功」で上がる分（+10%。品質が一段上がる）</li>
 *   <li>品質の段階（平均の割合が60%未満で★1、60〜70%で★2、70〜80%で★3、80〜90%で★4、90%以上で★5。アクセサリーと同じ）</li>
 *   <li>強化（+0〜+10、失敗なし【確定】。1段ごとにステータス+4%【補完】。+4・+7・+10で光り方が一段ずつ増える【提案】）</li>
 *   <li>強化の素材（世代ごと）。ノットはPhase 6（経済）で足す</li>
 *   <li>修理（生産職でない人は素材が2倍【補完】。NPCの修理はPhase 6でノットを使う形にする）</li>
 *   <li>耐久の減り方（武器は攻撃が当たるたび・スキルを使うたびに1、防具は敵の攻撃を受けるたびに1）</li>
 * </ul>
 */
public final class GearRules {
    /** 使う定義のID。 */
    public static final ResourceLocation DEFAULT_ID = AmemaruMod.id("default");

    /** 強化の素材の1種類（{@code from} 段目から、段の数 × {@code perLevel} 個。{@code from} が4なら+4で1個、+5で2個…）。 */
    public record EnhanceCost(ResourceLocation item, int perLevel, int from) {
        /** その段（1〜）に上げるときの数。 */
        public int countFor(int targetLevel) {
            if (targetLevel < from) {
                return 0;
            }
            return perLevel * (targetLevel - from + 1);
        }
    }

    private final double[] rarityMultiplier;
    private final double rollMin;
    private final double rollMax;
    private final double greatSuccessBonus;
    private final int[] qualityThresholds;
    private final int maxEnhance;
    private final double enhancePercent;
    private final int[] glowLevels;
    private final Map<Integer, List<EnhanceCost>> enhanceCosts;
    private final double nonCrafterRepair;
    private final int weaponHitLoss;
    private final int skillUseLoss;
    private final int armorHitLoss;

    private GearRules(double[] rarityMultiplier, double rollMin, double rollMax, double greatSuccessBonus,
                      int[] qualityThresholds, int maxEnhance, double enhancePercent, int[] glowLevels,
                      Map<Integer, List<EnhanceCost>> enhanceCosts, double nonCrafterRepair, int weaponHitLoss,
                      int skillUseLoss, int armorHitLoss) {
        this.rarityMultiplier = rarityMultiplier;
        this.rollMin = rollMin;
        this.rollMax = rollMax;
        this.greatSuccessBonus = greatSuccessBonus;
        this.qualityThresholds = qualityThresholds;
        this.maxEnhance = maxEnhance;
        this.enhancePercent = enhancePercent;
        this.glowLevels = glowLevels;
        this.enhanceCosts = enhanceCosts;
        this.nonCrafterRepair = nonCrafterRepair;
        this.weaponHitLoss = weaponHitLoss;
        this.skillUseLoss = skillUseLoss;
        this.armorHitLoss = armorHitLoss;
    }

    // ---------------------------------------------------------------- 値

    public double rarityMultiplier(Rarity rarity) {
        return rarityMultiplier[rarity.ordinal()];
    }

    /** 抽選の下限（割合。0.7 など）。 */
    public double rollMin() {
        return rollMin;
    }

    /** 抽選の上限（割合。1.0 など）。 */
    public double rollMax() {
        return rollMax;
    }

    /** 大成功で上がる割合（0.1 など）。 */
    public double greatSuccessBonus() {
        return greatSuccessBonus;
    }

    /** 平均の割合（0〜1）から品質（★1〜5）を決める。 */
    public int quality(double averageRatio) {
        double percent = averageRatio * 100.0 + 1.0E-6;
        int stars = 1;
        for (int threshold : qualityThresholds) {
            if (percent >= threshold) {
                stars++;
            }
        }
        return Math.min(5, stars);
    }

    public int maxEnhance() {
        return maxEnhance;
    }

    /** 強化の段の数から、ステータスにかける倍率。 */
    public double enhanceMultiplier(int enhance) {
        return 1.0 + Math.max(0, enhance) * enhancePercent / 100.0;
    }

    /** 1段あたりの上がり方（%）。 */
    public double enhancePercent() {
        return enhancePercent;
    }

    /** 強化の段の数から、光り方の段階（0〜3）。 */
    public int glowTier(int enhance) {
        int tier = 0;
        for (int level : glowLevels) {
            if (enhance >= level) {
                tier++;
            }
        }
        return tier;
    }

    /** その世代の、ある段に上げるときの素材（世代の定義がなければ、いちばん近い下の世代）。 */
    public List<EnhanceCost> enhanceCosts(int generation) {
        for (int g = generation; g >= 1; g--) {
            List<EnhanceCost> costs = enhanceCosts.get(g);
            if (costs != null) {
                return costs;
            }
        }
        return List.of();
    }

    /** 生産職でない人の修理の素材の倍率。 */
    public double nonCrafterRepair() {
        return nonCrafterRepair;
    }

    public int weaponHitLoss() {
        return weaponHitLoss;
    }

    public int skillUseLoss() {
        return skillUseLoss;
    }

    public int armorHitLoss() {
        return armorHitLoss;
    }

    // ---------------------------------------------------------------- 通信（説明文の計算に要るため、クライアントにも送る）

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(rarityMultiplier.length);
        for (double value : rarityMultiplier) {
            buf.writeDouble(value);
        }
        buf.writeDouble(rollMin);
        buf.writeDouble(rollMax);
        buf.writeDouble(greatSuccessBonus);
        for (int threshold : qualityThresholds) {
            buf.writeVarInt(threshold);
        }
        buf.writeVarInt(maxEnhance);
        buf.writeDouble(enhancePercent);
        buf.writeVarInt(glowLevels.length);
        for (int level : glowLevels) {
            buf.writeVarInt(level);
        }
        buf.writeVarInt(enhanceCosts.size());
        for (Map.Entry<Integer, List<EnhanceCost>> entry : enhanceCosts.entrySet()) {
            buf.writeVarInt(entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (EnhanceCost cost : entry.getValue()) {
                buf.writeResourceLocation(cost.item());
                buf.writeVarInt(cost.perLevel());
                buf.writeVarInt(cost.from());
            }
        }
        buf.writeDouble(nonCrafterRepair);
        buf.writeVarInt(weaponHitLoss);
        buf.writeVarInt(skillUseLoss);
        buf.writeVarInt(armorHitLoss);
    }

    public static GearRules read(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 16);
        double[] multiplier = new double[Rarity.all().length];
        java.util.Arrays.fill(multiplier, 1.0);
        for (int i = 0; i < count; i++) {
            double value = buf.readDouble();
            if (i < multiplier.length) {
                multiplier[i] = value;
            }
        }
        double min = buf.readDouble();
        double max = buf.readDouble();
        double great = buf.readDouble();
        int[] quality = new int[4];
        for (int i = 0; i < 4; i++) {
            quality[i] = buf.readVarInt();
        }
        int maxEnhance = buf.readVarInt();
        double percent = buf.readDouble();
        int glowCount = Math.min(buf.readVarInt(), 16);
        int[] glow = new int[glowCount];
        for (int i = 0; i < glowCount; i++) {
            glow[i] = buf.readVarInt();
        }
        int generations = Math.min(buf.readVarInt(), 16);
        Map<Integer, List<EnhanceCost>> costs = new HashMap<>();
        for (int i = 0; i < generations; i++) {
            int generation = buf.readVarInt();
            int size = Math.min(buf.readVarInt(), 16);
            List<EnhanceCost> list = new ArrayList<>();
            for (int j = 0; j < size; j++) {
                list.add(new EnhanceCost(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt()));
            }
            costs.put(generation, List.copyOf(list));
        }
        double nonCrafter = buf.readDouble();
        int weaponHit = buf.readVarInt();
        int skillUse = buf.readVarInt();
        int armorHit = buf.readVarInt();
        return new GearRules(multiplier, min, max, great, quality, maxEnhance, percent, glow, costs, nonCrafter,
                weaponHit, skillUse, armorHit);
    }

    // ---------------------------------------------------------------- 読み込み

    private static final DefinitionLoader<GearRules> LOADER = new Loader();
    private static final GearRules FALLBACK = fallback();

    /** データパックの読み込み処理。 */
    public static DefinitionLoader<GearRules> loader() {
        return LOADER;
    }

    /** 今の決まり。 */
    public static GearRules current() {
        return LOADER.get(DEFAULT_ID).orElse(FALLBACK);
    }

    public static GearRules parse(JsonObject json) {
        double[] multiplier = new double[Rarity.all().length];
        JsonObject rarity = Json.object(json, "rarity_multiplier");
        for (Rarity r : Rarity.all()) {
            multiplier[r.ordinal()] = Json.doubleInRange(rarity, r.id(), 0.1, 100.0);
        }

        JsonObject roll = Json.object(json, "roll");
        double min = Json.doubleInRange(roll, "min", 1.0, 100.0) / 100.0;
        double max = Json.doubleInRange(roll, "max", 1.0, 100.0) / 100.0;
        if (min > max) {
            throw new DefinitionException("「roll」の「min」は「max」以下にしてください");
        }
        double great = Json.optionalDouble(roll, "great_success_bonus", 10.0, 0.0, 100.0) / 100.0;

        int[] quality = readThresholds(Json.array(json, "quality"));

        JsonObject enhance = Json.object(json, "enhance");
        int maxEnhance = Json.intInRange(enhance, "max", 0, 100);
        double percent = Json.doubleInRange(enhance, "percent_per_level", 0.0, 100.0);
        JsonArray glowArray = Json.optionalArray(enhance, "glow");
        int[] glow = new int[glowArray.size()];
        for (int i = 0; i < glow.length; i++) {
            glow[i] = (int) Json.asNumber(glowArray.get(i), "enhance.glow");
        }

        Map<Integer, List<EnhanceCost>> costs = new HashMap<>();
        JsonObject costJson = Json.optionalObject(json, "enhance_cost");
        if (costJson != null) {
            for (Map.Entry<String, JsonElement> entry : costJson.entrySet()) {
                int generation;
                try {
                    generation = Integer.parseInt(entry.getKey());
                } catch (NumberFormatException e) {
                    throw new DefinitionException("「enhance_cost」のキーは世代の番号（1〜4）にしてください（今は " + entry.getKey() + "）");
                }
                JsonArray array = entry.getValue().getAsJsonArray();
                List<EnhanceCost> list = new ArrayList<>();
                for (int i = 0; i < array.size(); i++) {
                    JsonObject one = Json.asObject(array.get(i), "enhance_cost." + generation + "[" + i + "]");
                    list.add(new EnhanceCost(Json.id(one, "item"), Json.intInRange(one, "per_level", 1, 64),
                            Json.optionalInt(one, "from", 1, 1, 100)));
                }
                costs.put(generation, List.copyOf(list));
            }
        }

        JsonObject repair = Json.optionalObject(json, "repair");
        double nonCrafter = repair == null ? 2.0 : Json.optionalDouble(repair, "non_crafter_multiplier", 2.0, 0.0, 100.0);

        JsonObject loss = Json.optionalObject(json, "durability_loss");
        int weaponHit = loss == null ? 1 : Json.optionalInt(loss, "weapon_hit", 1, 0, 100);
        int skillUse = loss == null ? 1 : Json.optionalInt(loss, "skill_use", 1, 0, 100);
        int armorHit = loss == null ? 1 : Json.optionalInt(loss, "armor_hit", 1, 0, 100);

        return new GearRules(multiplier, min, max, great, quality, maxEnhance, percent, glow, costs, nonCrafter,
                weaponHit, skillUse, armorHit);
    }

    /** 品質の境目（%。4つ、小さい順）。 */
    public static int[] readThresholds(JsonArray array) {
        if (array.size() != 4) {
            throw new DefinitionException("品質の境目は4つにしてください（★2・★3・★4・★5になる割合）");
        }
        int[] values = new int[4];
        for (int i = 0; i < 4; i++) {
            values[i] = (int) Json.asNumber(array.get(i), "quality");
            if (i > 0 && values[i] <= values[i - 1]) {
                throw new DefinitionException("品質の境目は小さい順にしてください");
            }
        }
        return values;
    }

    private static GearRules fallback() {
        double[] multiplier = {1.0, 1.08, 1.16, 1.25, 1.35, 1.40};
        Map<Integer, List<EnhanceCost>> costs = new HashMap<>();
        costs.put(1, List.of(new EnhanceCost(AmemaruMod.id("moss_iron"), 1, 1),
                new EnhanceCost(AmemaruMod.id("crystal_shard"), 1, 4)));
        return new GearRules(multiplier, 0.7, 1.0, 0.1, new int[]{60, 70, 80, 90}, 10, 4.0, new int[]{4, 7, 10}, costs,
                2.0, 1, 1, 1);
    }

    private static final class Loader extends DefinitionLoader<GearRules> {
        Loader() {
            super("gear_rules", GearRules::parse);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
            super.apply(jsons, resourceManager, profiler);
            if (get(DEFAULT_ID).isEmpty()) {
                addError("「" + DEFAULT_ID + "」が見つからないため、Modに入っている初期値を使います");
            }
        }
    }
}
