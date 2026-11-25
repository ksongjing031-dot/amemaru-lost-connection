package jp.amemaru.lostconnection.item;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

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
 * </ul>
 */
public record ConsumableDef(ResourceLocation id, Restore restore, double percent, double flat, double cooldown, String group,
                            boolean eat) {
    public enum Restore {
        HP, MP;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static ConsumableDef parse(ResourceLocation id, JsonObject json) {
        String restoreText = Json.string(json, "restore");
        Restore restore = switch (restoreText) {
            case "hp" -> Restore.HP;
            case "mp" -> Restore.MP;
            default -> throw new DefinitionException("restore は hp か mp にしてください（「" + restoreText + "」）");
        };
        double percent = Json.optionalDouble(json, "percent", 0.0, 0.0, 100.0);
        double flat = Json.optionalDouble(json, "flat", 0.0, 0.0, 100_000.0);
        if (percent <= 0 && flat <= 0) {
            throw new DefinitionException("percent か flat のどちらかを0より大きくしてください");
        }
        double cooldown = Json.optionalDouble(json, "cooldown", 10.0, 0.0, 600.0);
        String group = Json.optionalString(json, "group", restore.id());
        String kind = Json.optionalString(json, "kind", "drink");
        if (!kind.equals("drink") && !kind.equals("eat")) {
            throw new DefinitionException("kind は drink か eat にしてください（「" + kind + "」）");
        }
        return new ConsumableDef(id, restore, percent, flat, cooldown, group, kind.equals("eat"));
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
        buf.writeEnum(restore);
        buf.writeDouble(percent);
        buf.writeDouble(flat);
        buf.writeDouble(cooldown);
        buf.writeUtf(group);
        buf.writeBoolean(eat);
    }

    public static ConsumableDef read(FriendlyByteBuf buf) {
        return new ConsumableDef(buf.readResourceLocation(), buf.readEnum(Restore.class), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readUtf(), buf.readBoolean());
    }
}
