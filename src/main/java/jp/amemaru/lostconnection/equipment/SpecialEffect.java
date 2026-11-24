package jp.amemaru.lostconnection.equipment;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * レア装備などの固有の特殊効果（Phase 0-A 20章「レア装備：固有の特殊効果つき」。例は「攻撃時に一定確率で追加ダメージ、
 * HP自動回復、特定のボスに強い など」）。種類ごとの処理は {@code GearEffects}、数値はJSON。
 *
 * <ul>
 *   <li>{@code extra_damage}：攻撃が当たると {@code chance}% の確率で、与えたダメージの {@code percent}% の追加ダメージ</li>
 *   <li>{@code execute}：HPが {@code threshold}% 以下の敵に与えるダメージ +{@code percent}%</li>
 *   <li>{@code heal_on_hit}：攻撃が当たると {@code chance}% の確率で、与えたダメージの {@code percent}% だけHPが回復する</li>
 *   <li>{@code damage_reduction}：敵から受けるダメージ −{@code percent}%</li>
 *   <li>{@code regen}：5秒ごとに、最大HPの {@code percent}% だけHPが回復する</li>
 *   <li>{@code move_speed}：移動の速さ +{@code percent}%</li>
 *   <li>{@code heal_ward}：スキルで味方を回復すると、その味方の防御 +{@code percent}%（5秒。4階のレア「祈り手の鈴」。11月24日）</li>
 *   <li>{@code ally_power}：{@code threshold} ブロック以内の味方1人ごとに、与えるダメージ +{@code percent}%（5人まで。
 *       5階のレア「守護者の核剣」など）</li>
 *   <li>{@code party_ward}：{@code threshold} ブロック以内に味方が3人以上いると、自分とその味方の、敵から受けるダメージ
 *       −{@code percent}%（5階のユニーク「第七の門鍵」）</li>
 * </ul>
 *
 * <p>{@code source} は効く攻撃の種類（{@code any}：すべて、{@code melee}：近接攻撃だけ、{@code skill}：スキルだけ）。</p>
 */
public record SpecialEffect(Type type, double chance, double percent, double threshold, Source source) {
    public enum Type {
        EXTRA_DAMAGE("extra_damage"),
        EXECUTE("execute"),
        HEAL_ON_HIT("heal_on_hit"),
        DAMAGE_REDUCTION("damage_reduction"),
        REGEN("regen"),
        MOVE_SPEED("move_speed"),
        HEAL_WARD("heal_ward"),
        ALLY_POWER("ally_power"),
        PARTY_WARD("party_ward");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static Type byId(String id) {
            for (Type type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            throw new DefinitionException("特殊効果の種類「" + id + "」はありません（extra_damage・execute・heal_on_hit・"
                    + "damage_reduction・regen・move_speed・heal_ward・ally_power・party_ward のどれかにしてください）");
        }
    }

    /** 効く攻撃の種類。 */
    public enum Source {
        ANY("any"),
        MELEE("melee"),
        SKILL("skill");

        private final String id;

        Source(String id) {
            this.id = id;
        }

        static Source byId(String id) {
            for (Source source : values()) {
                if (source.id.equals(id)) {
                    return source;
                }
            }
            throw new DefinitionException("「source」は any・melee・skill のどれかにしてください（今は " + id + "）");
        }

        /** その攻撃に効くか。 */
        public boolean matches(boolean skill) {
            return this == ANY || (this == SKILL) == skill;
        }
    }

    public static SpecialEffect parse(JsonObject json, String where) {
        Type type = Type.byId(Json.string(json, "type"));
        double chance = Json.optionalDouble(json, "chance", 100.0, 0.0, 100.0);
        double percent = Json.optionalDouble(json, "percent", 0.0, 0.0, 1000.0);
        double threshold = Json.optionalDouble(json, "threshold", 0.0, 0.0, 100.0);
        Source source = Source.byId(Json.optionalString(json, "source", "any"));
        if (percent <= 0) {
            throw new DefinitionException(where + "：「percent」を0より大きくしてください");
        }
        if (type == Type.EXECUTE && threshold <= 0) {
            throw new DefinitionException(where + "：execute には「threshold」（HPの割合）が要ります");
        }
        if ((type == Type.ALLY_POWER || type == Type.PARTY_WARD) && threshold <= 0) {
            throw new DefinitionException(where + "：" + type.id() + " には「threshold」（味方を数える距離）が要ります");
        }
        return new SpecialEffect(type, chance, percent, threshold, source);
    }

    /** 説明文の1行。 */
    public MutableComponent describe() {
        String key = "effect.amemaru." + type.id();
        if (type == Type.EXTRA_DAMAGE || type == Type.HEAL_ON_HIT) {
            return Component.translatable(key + (source == Source.ANY ? "" : "." + source.id), number(chance), number(percent));
        }
        if (type == Type.EXECUTE || type == Type.ALLY_POWER || type == Type.PARTY_WARD) {
            return Component.translatable(key, number(threshold), number(percent));
        }
        return Component.translatable(key, number(percent));
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(type.ordinal());
        buf.writeFloat((float) chance);
        buf.writeFloat((float) percent);
        buf.writeFloat((float) threshold);
        buf.writeByte(source.ordinal());
    }

    public static SpecialEffect read(FriendlyByteBuf buf) {
        Type[] types = Type.values();
        Source[] sources = Source.values();
        int type = buf.readByte();
        double chance = buf.readFloat();
        double percent = buf.readFloat();
        double threshold = buf.readFloat();
        int source = buf.readByte();
        return new SpecialEffect(types[Math.max(0, Math.min(types.length - 1, type))], chance, percent, threshold,
                sources[Math.max(0, Math.min(sources.length - 1, source))]);
    }
}
