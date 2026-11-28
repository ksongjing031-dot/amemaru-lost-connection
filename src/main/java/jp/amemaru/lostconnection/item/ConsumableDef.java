package jp.amemaru.lostconnection.item;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * 消耗品（回復薬など）の数値（{@code data/<名前空間>/amemaru/consumables/<アイテム>.json}。Phase 6）。ファイルの名前がアイテムのID。
 *
 * <pre>{"restore": "hp", "percent": 25, "flat": 0, "cooldown": 10, "group": "hp"}</pre>
 *
 * <ul>
 *   <li>restore：戻すもの（{@code hp}・{@code mp}）</li>
 *   <li>percent：最大の何%を戻すか。flat：さらに足す量</li>
 *   <li>cooldown：使ったあと、同じ group の品が使えない秒数</li>
 *   <li>kind：{@code drink}（飲む。ふつう）か {@code eat}（食べる。音と粒が変わる）。11月23日の直しで、バニラの食べ物にも
 *       数値を付けた（{@code data/minecraft/amemaru/consumables/bread.json} など。満腹度はいつも満ちているので、食べ物は
 *       これで使い道ができる）</li>
 *   <li>buff（11月29日。キャンディー）：しばらくステータスを上げる。
 *       {@code {"stat": "attack", "mode": "percent", "amount": 8, "seconds": 180}}（mode が {@code flat} なら値をそのまま足す。
 *       会心 +4 など）。buff だけの品は restore を書かない。上がるのは一度に1つ（新しく使うと入れかわる。{@link ItemBuffService}）</li>
 * </ul>
 */
public record ConsumableDef(ResourceLocation id, @Nullable Restore restore, double percent, double flat, double cooldown, String group,
                            boolean eat, @Nullable Buff buff) {
    public enum Restore {
        HP, MP;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * しばらく上がるステータス（キャンディー。11月29日のご指示「一時的なステータスバフを得るアイテム『キャンディー』」）。
     * {@code percent} が true なら割合（攻撃 +8%）、false なら値をそのまま足す（会心 +4）。
     */
    public record Buff(StatType stat, boolean percent, double amount, int seconds) {
        public int ticks() {
            return seconds * 20;
        }
    }

    public static ConsumableDef parse(ResourceLocation id, JsonObject json) {
        Restore restore = null;
        if (json.has("restore")) {
            String restoreText = Json.string(json, "restore");
            restore = switch (restoreText) {
                case "hp" -> Restore.HP;
                case "mp" -> Restore.MP;
                default -> throw new DefinitionException("restore は hp か mp にしてください（「" + restoreText + "」）");
            };
        }
        Buff buff = json.has("buff") ? parseBuff(Json.object(json, "buff")) : null;
        if (restore == null && buff == null) {
            throw new DefinitionException("restore（HP・MPを戻す）か buff（しばらく上げる）のどちらかを書いてください");
        }
        double percent = Json.optionalDouble(json, "percent", 0.0, 0.0, 100.0);
        double flat = Json.optionalDouble(json, "flat", 0.0, 0.0, 100_000.0);
        if (restore != null && percent <= 0 && flat <= 0) {
            throw new DefinitionException("percent か flat のどちらかを0より大きくしてください");
        }
        double cooldown = Json.optionalDouble(json, "cooldown", 10.0, 0.0, 600.0);
        String group = Json.optionalString(json, "group", restore != null ? restore.id() : "buff");
        String kind = Json.optionalString(json, "kind", "drink");
        if (!kind.equals("drink") && !kind.equals("eat")) {
            throw new DefinitionException("kind は drink か eat にしてください（「" + kind + "」）");
        }
        return new ConsumableDef(id, restore, percent, flat, cooldown, group, kind.equals("eat"), buff);
    }

    private static Buff parseBuff(JsonObject json) {
        String statText = Json.string(json, "stat");
        StatType stat = StatType.byId(statText)
                .orElseThrow(() -> new DefinitionException("buff.stat が分かりません（「" + statText + "」）"));
        String mode = Json.optionalString(json, "mode", "percent");
        if (!mode.equals("percent") && !mode.equals("flat")) {
            throw new DefinitionException("buff.mode は percent か flat にしてください（「" + mode + "」）");
        }
        double amount = Json.doubleInRange(json, "amount", 0.01, 1000.0);
        int seconds = Json.optionalInt(json, "seconds", 180, 1, 3600);
        return new Buff(stat, mode.equals("percent"), amount, seconds);
    }

    /** 最大値 max のときに戻す量。 */
    public double amount(double max) {
        return max * percent / 100.0 + flat;
    }

    public int cooldownTicks() {
        return (int) Math.round(cooldown * 20.0);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeBoolean(restore != null);
        if (restore != null) {
            buf.writeEnum(restore);
        }
        buf.writeDouble(percent);
        buf.writeDouble(flat);
        buf.writeDouble(cooldown);
        buf.writeUtf(group);
        buf.writeBoolean(eat);
        buf.writeBoolean(buff != null);
        if (buff != null) {
            buf.writeEnum(buff.stat());
            buf.writeBoolean(buff.percent());
            buf.writeDouble(buff.amount());
            buf.writeVarInt(buff.seconds());
        }
    }

    public static ConsumableDef read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        Restore restore = buf.readBoolean() ? buf.readEnum(Restore.class) : null;
        double percent = buf.readDouble();
        double flat = buf.readDouble();
        double cooldown = buf.readDouble();
        String group = buf.readUtf();
        boolean eat = buf.readBoolean();
        Buff buff = buf.readBoolean() ? new Buff(buf.readEnum(StatType.class), buf.readBoolean(), buf.readDouble(), buf.readVarInt()) : null;
        return new ConsumableDef(id, restore, percent, flat, cooldown, group, eat, buff);
    }
}
