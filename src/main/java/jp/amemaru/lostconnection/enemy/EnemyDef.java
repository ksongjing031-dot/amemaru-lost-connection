package jp.amemaru.lostconnection.enemy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 敵の定義（{@code data/<名前空間>/amemaru/enemies/<名前>.json}。Phase 0-B 6章：モデル、レベル、ステータス、行動の型、
 * 使う技、ドロップ表、与えるロスト状態）。IDは敵のエンティティのIDと同じ（{@code enemies/tsunomofu.json} →
 * {@code amemaru:tsunomofu}）。
 *
 * <ul>
 *   <li>{@code name}・{@code description}：名前と図鑑の説明</li>
 *   <li>{@code floor}・{@code level}：出る階とレベル</li>
 *   <li>{@code stats}：hp・attack・defense（このModのステータス）、move_speed（バニラの移動速度）、
 *       follow_range（追いかける距離）、aggro_range（自分から気づく距離。0なら叩かれるまで気づかない）</li>
 *   <li>{@code exp}：倒した人たちに分ける経験値</li>
 *   <li>{@code skills}：技ごとの数値（power：攻撃の倍率、windup：予兆の秒、cooldown：間の秒、range：届く距離など）</li>
 *   <li>{@code behavior}：行動の型ごとの数値（群れで反撃する範囲、仲間を呼ぶ範囲など）</li>
 *   <li>{@code drops}：loot_table（ふつうのドロップ表）、rare（レアドロップ。確率は%）、pity（天井。ボスだけ）</li>
 *   <li>{@code lost}：ロストを見せる敵か（体の一部が白く欠けた見た目。ロスト状態を与える仕組みはPhase 8）</li>
 * </ul>
 */
public record EnemyDef(ResourceLocation id, String name, String description, int floor, int level, double hp, double attack,
                       double defense, double moveSpeed, double followRange, double aggroRange, int exp,
                       Map<String, Skill> skills, Map<String, Double> behavior, @Nullable ResourceLocation lootTable,
                       List<RareDrop> rare, boolean pity, boolean lost) {

    /** 技の数値。 */
    public record Skill(double power, double windup, double cooldown, double range, Map<String, Double> params) {
        public int windupTicks() {
            return (int) Math.round(windup * 20.0);
        }

        public int cooldownTicks() {
            return (int) Math.round(cooldown * 20.0);
        }

        public double param(String key, double fallback) {
            Double value = params.get(key);
            return value == null ? fallback : value;
        }
    }

    /** レアドロップ（{@code chance} は%。{@code rarity} は装備のレアリティを上げるとき）。 */
    public record RareDrop(ResourceLocation item, double chance, int count, @Nullable String rarity) {
    }

    /** 技の数値（定義になければ、渡した値で作る）。 */
    public Skill skill(String name, double power, double windup, double cooldown, double range) {
        Skill skill = skills.get(name);
        return skill != null ? skill : new Skill(power, windup, cooldown, range, Map.of());
    }

    /** 行動の数値（なければ渡した値）。 */
    public double behavior(String key, double fallback) {
        Double value = behavior.get(key);
        return value == null ? fallback : value;
    }

    public static EnemyDef parse(ResourceLocation id, JsonObject json) {
        String name = Json.string(json, "name");
        String description = Json.optionalString(json, "description", "");
        int floor = Json.optionalInt(json, "floor", 1, 0, 100);
        int level = Json.optionalInt(json, "level", 1, 1, 200);

        JsonObject stats = Json.object(json, "stats");
        double hp = Json.doubleInRange(stats, "hp", 1.0, 1.0e9);
        double attack = Json.doubleInRange(stats, "attack", 0.0, 1.0e7);
        double defense = Json.optionalDouble(stats, "defense", 0.0, 0.0, 1.0e7);
        double moveSpeed = Json.optionalDouble(stats, "move_speed", 0.25, 0.0, 2.0);
        double followRange = Json.optionalDouble(stats, "follow_range", 24.0, 1.0, 128.0);
        double aggroRange = Json.optionalDouble(stats, "aggro_range", 0.0, 0.0, 64.0);
        int exp = Json.optionalInt(json, "exp", 0, 0, 10_000_000);

        Map<String, Skill> skills = new LinkedHashMap<>();
        JsonObject skillJson = Json.optionalObject(json, "skills");
        if (skillJson != null) {
            for (Map.Entry<String, JsonElement> entry : skillJson.entrySet()) {
                JsonObject one = Json.asObject(entry.getValue(), "skills." + entry.getKey());
                Map<String, Double> params = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> param : one.entrySet()) {
                    if (param.getValue().isJsonPrimitive() && param.getValue().getAsJsonPrimitive().isNumber()) {
                        params.put(param.getKey(), param.getValue().getAsDouble());
                    }
                }
                skills.put(entry.getKey(), new Skill(
                        Json.optionalDouble(one, "power", 1.0, 0.0, 1000.0),
                        Json.optionalDouble(one, "windup", 0.5, 0.0, 60.0),
                        Json.optionalDouble(one, "cooldown", 2.0, 0.0, 600.0),
                        Json.optionalDouble(one, "range", 2.5, 0.0, 128.0),
                        Collections.unmodifiableMap(params)));
            }
        }

        Map<String, Double> behavior = new LinkedHashMap<>();
        JsonObject behaviorJson = Json.optionalObject(json, "behavior");
        if (behaviorJson != null) {
            for (Map.Entry<String, JsonElement> entry : behaviorJson.entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                    behavior.put(entry.getKey(), entry.getValue().getAsDouble());
                }
            }
        }

        ResourceLocation lootTable = null;
        List<RareDrop> rare = new ArrayList<>();
        boolean pity = false;
        JsonObject drops = Json.optionalObject(json, "drops");
        if (drops != null) {
            lootTable = Json.optionalId(drops, "loot_table");
            pity = Json.optionalBoolean(drops, "pity", false);
            JsonArray rareArray = Json.optionalArray(drops, "rare");
            for (int i = 0; i < rareArray.size(); i++) {
                JsonObject one = Json.asObject(rareArray.get(i), "drops.rare[" + i + "]");
                String rarity = Json.optionalString(one, "rarity", "");
                rare.add(new RareDrop(Json.id(one, "item"), Json.doubleInRange(one, "chance", 0.0, 100.0),
                        Json.optionalInt(one, "count", 1, 1, 64), rarity.isEmpty() ? null : rarity));
            }
        }
        if (attack <= 0 && !skills.isEmpty()) {
            throw new DefinitionException("技を持つ敵の「stats.attack」は0より大きくしてください");
        }
        boolean lost = Json.optionalBoolean(json, "lost", false);
        return new EnemyDef(id, name, description, floor, level, hp, attack, defense, moveSpeed, followRange, aggroRange, exp,
                Collections.unmodifiableMap(skills), Collections.unmodifiableMap(behavior), lootTable, List.copyOf(rare), pity, lost);
    }

    // ---------------------------------------------------------------- 図鑑に要る分をクライアントへ

    /** 図鑑の1行（名前・説明・階・レベル・ふつうのドロップの品・レアドロップの品と確率）。 */
    public record Info(ResourceLocation id, String name, String description, int floor, int level, boolean lost,
                       List<ResourceLocation> drops, List<RareDrop> rare) {
        public void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(id);
            buf.writeUtf(name);
            buf.writeUtf(description);
            buf.writeVarInt(floor);
            buf.writeVarInt(level);
            buf.writeBoolean(lost);
            buf.writeVarInt(drops.size());
            for (ResourceLocation drop : drops) {
                buf.writeResourceLocation(drop);
            }
            buf.writeVarInt(rare.size());
            for (RareDrop drop : rare) {
                buf.writeResourceLocation(drop.item());
                buf.writeDouble(drop.chance());
                buf.writeVarInt(drop.count());
                buf.writeUtf(drop.rarity() == null ? "" : drop.rarity());
            }
        }

        public static Info read(FriendlyByteBuf buf) {
            ResourceLocation id = buf.readResourceLocation();
            String name = buf.readUtf();
            String description = buf.readUtf();
            int floor = buf.readVarInt();
            int level = buf.readVarInt();
            boolean lost = buf.readBoolean();
            int dropCount = Math.min(buf.readVarInt(), 256);
            List<ResourceLocation> drops = new ArrayList<>();
            for (int i = 0; i < dropCount; i++) {
                drops.add(buf.readResourceLocation());
            }
            int rareCount = Math.min(buf.readVarInt(), 256);
            List<RareDrop> rare = new ArrayList<>();
            for (int i = 0; i < rareCount; i++) {
                ResourceLocation item = buf.readResourceLocation();
                double chance = buf.readDouble();
                int count = buf.readVarInt();
                String rarity = buf.readUtf();
                rare.add(new RareDrop(item, chance, count, rarity.isEmpty() ? null : rarity));
            }
            return new Info(id, name, description, floor, level, lost, List.copyOf(drops), List.copyOf(rare));
        }
    }
}
