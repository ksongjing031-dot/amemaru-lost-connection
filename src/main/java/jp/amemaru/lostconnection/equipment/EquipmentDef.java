package jp.amemaru.lostconnection.equipment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 装備（武器・盾・防具）の定義（{@code data/<名前空間>/amemaru/equipment/<名前>.json}）。数値はデータパックで変えられる。
 *
 * <p>IDはアイテムのIDと同じ（{@code equipment/ancient_stone_sword.json} → {@code amemaru:ancient_stone_sword}）。
 * アイテムの種類（IDと見た目）は、Modに同梱した {@code amemaru/item_types.json} から起動時に登録する（Phase 0-B 4.3）。</p>
 *
 * <ul>
 *   <li>{@code slot}：weapon・offhand・head・chest・legs・feet</li>
 *   <li>{@code generation}：世代（章。1〜4）</li>
 *   <li>{@code series}：シリーズの名前（「古代石シリーズ」など。説明文に出す）</li>
 *   <li>{@code rarity}：作ったとき・落ちたときのレアリティ（ドロップでは、ドロップ表の側で上げられる）</li>
 *   <li>{@code required_level}・{@code jobs}：使える条件（満たさないと、持っていてもステータスが上がらない）</li>
 *   <li>{@code stats}：コモンで抽選が100%のときのステータス（ここにレアリティの倍率・抽選の割合・強化の倍率がかかる）</li>
 *   <li>{@code fixed}：抽選しない（いつも100%。見習い装備）</li>
 *   <li>{@code durability}：耐久の最大（0は壊れない）。0になると壊れた状態になり、修理するまでステータスが上がらない（消えない）</li>
 *   <li>{@code binding}：取引の決まり。bound（取引できない）・on_equip（一度装備するまで取引できる。ボスのレア装備）・none</li>
 *   <li>{@code effects}：固有の特殊効果（{@link SpecialEffect}）</li>
 *   <li>{@code rare}：工房では作れないレア装備か（説明文に出す）</li>
 *   <li>{@code repair}：耐久が0から最大まで戻すときの素材（減った分の割合で少なくなる）</li>
 *   <li>{@code description}：説明文の最後に出す、ひとこと</li>
 * </ul>
 */
public record EquipmentDef(ResourceLocation id, GearSlot slot, int generation, String series, Rarity rarity,
                           int requiredLevel, List<ResourceLocation> jobs, Map<StatType, Double> stats, boolean fixed,
                           int durability, Binding binding, List<SpecialEffect> effects, boolean rare,
                           List<Cost> repair, String description) {

    /** 取引の決まり。 */
    public enum Binding {
        NONE("none"),
        BOUND("bound"),
        ON_EQUIP("on_equip");

        private final String id;

        Binding(String id) {
            this.id = id;
        }

        static Binding byId(String id) {
            for (Binding binding : values()) {
                if (binding.id.equals(id)) {
                    return binding;
                }
            }
            throw new DefinitionException("「binding」は none・bound・on_equip のどれかにしてください（今は " + id + "）");
        }
    }

    /** 素材の数。 */
    public record Cost(ResourceLocation item, int count) {
        public static Cost parse(JsonObject json, String where) {
            return new Cost(Json.id(json, "item"), Json.optionalInt(json, "count", 1, 1, 999));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(item);
            buf.writeVarInt(count);
        }

        public static Cost read(FriendlyByteBuf buf) {
            return new Cost(buf.readResourceLocation(), buf.readVarInt());
        }
    }

    /** そのステータスの、コモン・100%のときの値（なければ0）。 */
    public double stat(StatType type) {
        Double value = stats.get(type);
        return value == null ? 0.0 : value;
    }

    /** 職業の条件があるか。 */
    public boolean hasJobRequirement() {
        return !jobs.isEmpty();
    }

    public static EquipmentDef parse(ResourceLocation id, JsonObject json) {
        GearSlot slot = GearSlot.byId(Json.string(json, "slot"));
        if (slot == null) {
            throw new DefinitionException("「slot」は weapon・offhand・head・chest・legs・feet のどれかにしてください");
        }
        int generation = Json.optionalInt(json, "generation", 1, 1, 4);
        String series = Json.optionalString(json, "series", "");
        Rarity rarity = Rarity.byId(Json.optionalString(json, "rarity", "common"));
        if (rarity == null) {
            throw new DefinitionException("「rarity」は common・uncommon・rare・epic・legendary・unique のどれかにしてください");
        }
        int requiredLevel = Json.optionalInt(json, "required_level", 1, 1, 200);
        List<ResourceLocation> jobs = Json.idList(json, "jobs");

        Map<StatType, Double> stats = new EnumMap<>(StatType.class);
        JsonObject statJson = Json.object(json, "stats");
        for (Map.Entry<String, JsonElement> entry : statJson.entrySet()) {
            StatType type = StatType.byId(entry.getKey()).orElseThrow(() ->
                    new DefinitionException("「stats」に、ない種類のステータス「" + entry.getKey() + "」があります"));
            double value = Json.asNumber(entry.getValue(), "stats." + entry.getKey());
            if (value < 0 || value > 100000) {
                throw new DefinitionException("「stats." + entry.getKey() + "」は 0〜100000 の範囲にしてください");
            }
            stats.put(type, value);
        }
        if (stats.isEmpty()) {
            throw new DefinitionException("「stats」に1つ以上のステータスを書いてください");
        }

        boolean fixed = Json.optionalBoolean(json, "fixed", false);
        int durability = Json.optionalInt(json, "durability", 0, 0, 100000);
        Binding binding = Binding.byId(Json.optionalString(json, "binding", "none"));

        List<SpecialEffect> effects = new ArrayList<>();
        JsonArray effectArray = Json.optionalArray(json, "effects");
        for (int i = 0; i < effectArray.size(); i++) {
            String where = "effects[" + i + "]";
            effects.add(SpecialEffect.parse(Json.asObject(effectArray.get(i), where), where));
        }
        boolean rare = Json.optionalBoolean(json, "rare", false);

        List<Cost> repair = new ArrayList<>();
        JsonArray repairArray = Json.optionalArray(json, "repair");
        for (int i = 0; i < repairArray.size(); i++) {
            String where = "repair[" + i + "]";
            repair.add(Cost.parse(Json.asObject(repairArray.get(i), where), where));
        }
        if (durability > 0 && repair.isEmpty()) {
            throw new DefinitionException("耐久のある装備には「repair」（修理の素材）を書いてください");
        }
        String description = Json.optionalString(json, "description", "");
        return new EquipmentDef(id, slot, generation, series, rarity, requiredLevel, List.copyOf(jobs),
                Collections.unmodifiableMap(stats), fixed, durability, binding, List.copyOf(effects), rare,
                List.copyOf(repair), description);
    }

    // ---------------------------------------------------------------- 通信（説明文に要る分をクライアントへ）

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeByte(slot.ordinal());
        buf.writeByte(generation);
        buf.writeUtf(series);
        buf.writeByte(rarity.ordinal());
        buf.writeVarInt(requiredLevel);
        buf.writeVarInt(jobs.size());
        for (ResourceLocation job : jobs) {
            buf.writeResourceLocation(job);
        }
        buf.writeVarInt(stats.size());
        for (Map.Entry<StatType, Double> entry : stats.entrySet()) {
            buf.writeByte(entry.getKey().ordinal());
            buf.writeDouble(entry.getValue());
        }
        buf.writeBoolean(fixed);
        buf.writeVarInt(durability);
        buf.writeByte(binding.ordinal());
        buf.writeVarInt(effects.size());
        for (SpecialEffect effect : effects) {
            effect.write(buf);
        }
        buf.writeBoolean(rare);
        buf.writeVarInt(repair.size());
        for (Cost cost : repair) {
            cost.write(buf);
        }
        buf.writeUtf(description);
    }

    public static EquipmentDef read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        GearSlot slot = GearSlot.byIndex(buf.readByte());
        int generation = buf.readByte();
        String series = buf.readUtf();
        Rarity rarity = Rarity.byIndex(buf.readByte());
        int requiredLevel = buf.readVarInt();
        int jobCount = Math.min(buf.readVarInt(), 64);
        List<ResourceLocation> jobs = new ArrayList<>();
        for (int i = 0; i < jobCount; i++) {
            jobs.add(buf.readResourceLocation());
        }
        int statCount = Math.min(buf.readVarInt(), 64);
        Map<StatType, Double> stats = new EnumMap<>(StatType.class);
        for (int i = 0; i < statCount; i++) {
            StatType type = StatType.byIndex(buf.readByte());
            double value = buf.readDouble();
            if (type != null) {
                stats.put(type, value);
            }
        }
        boolean fixed = buf.readBoolean();
        int durability = buf.readVarInt();
        Binding[] bindings = Binding.values();
        Binding binding = bindings[Math.max(0, Math.min(bindings.length - 1, buf.readByte()))];
        int effectCount = Math.min(buf.readVarInt(), 64);
        List<SpecialEffect> effects = new ArrayList<>();
        for (int i = 0; i < effectCount; i++) {
            effects.add(SpecialEffect.read(buf));
        }
        boolean rare = buf.readBoolean();
        int repairCount = Math.min(buf.readVarInt(), 64);
        List<Cost> repair = new ArrayList<>();
        for (int i = 0; i < repairCount; i++) {
            repair.add(Cost.read(buf));
        }
        String description = buf.readUtf();
        return new EquipmentDef(id, slot, generation, series, rarity, requiredLevel, List.copyOf(jobs),
                Collections.unmodifiableMap(stats), fixed, durability, binding, List.copyOf(effects), rare,
                List.copyOf(repair), description);
    }
}
