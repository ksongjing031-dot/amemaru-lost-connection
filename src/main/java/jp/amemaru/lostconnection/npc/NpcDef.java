package jp.amemaru.lostconnection.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * NPCの定義（データパックの {@code npcs/}。Phase 0-B 4.4「NPC（NpcEntity）：倒せない、押されない、その場から動かない。
 * 定義IDから、名前・見た目・会話・役割をJSONで読む」）。
 *
 * <ul>
 *   <li>{@code skin}：見た目（プレイヤーと同じ形のスキン。{@code textures/entity/npc/<名前>.png}。Phase 0-B 12章）。
 *       表情の差分は同じ名前の後ろに {@code _<表情>} を付けた絵（{@code lilia_smile.png} など）</li>
 *   <li>{@code slim}：腕の細いスキンか</li>
 *   <li>{@code home}：立つ所。{@code place} が {@code conelia} なら、中央広場の真ん中（大結晶の場所。道の高さ）からの位置。
 *       街を建てたときに、ここに置く</li>
 *   <li>{@code roles}：役割（{@code job_registration}＝職業の登録の窓口、{@code tower_gate}＝塔の門番 など）</li>
 * </ul>
 */
public record NpcDef(ResourceLocation id, String name, String title, ResourceLocation skin, boolean slim, List<String> expressions,
                     @Nullable Home home, List<String> roles, double talkRange) {
    /** 立つ所。 */
    public record Home(String place, Vec3 pos, float yaw) {
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /** 表情の絵（ない表情なら、ふつうの絵）。 */
    public ResourceLocation skinFor(@Nullable String expression) {
        if (expression == null || expression.isEmpty() || "normal".equals(expression) || !expressions.contains(expression)) {
            return skin;
        }
        String path = skin.getPath();
        int dot = path.lastIndexOf('.');
        String base = dot > 0 ? path.substring(0, dot) : path;
        String ext = dot > 0 ? path.substring(dot) : "";
        return ResourceLocation.fromNamespaceAndPath(skin.getNamespace(), base + "_" + expression + ext);
    }

    public static NpcDef parse(ResourceLocation id, JsonObject json) {
        String name = Json.string(json, "name");
        String title = Json.optionalString(json, "title", "");
        ResourceLocation skin = Json.parseId(Json.string(json, "skin"), "skin");
        boolean slim = Json.optionalBoolean(json, "slim", false);
        List<String> expressions = new ArrayList<>();
        JsonArray exp = Json.optionalArray(json, "expressions");
        for (int i = 0; i < exp.size(); i++) {
            expressions.add(Json.asString(exp.get(i), "expressions"));
        }
        Home home = null;
        JsonObject homeJson = Json.optionalObject(json, "home");
        if (homeJson != null) {
            JsonArray pos = Json.array(homeJson, "pos");
            if (pos.size() != 3) {
                throw new DefinitionException("「home.pos」は [x, y, z] の3つの数にしてください");
            }
            home = new Home(Json.optionalString(homeJson, "place", "conelia"),
                    new Vec3(Json.asNumber(pos.get(0), "home.pos"), Json.asNumber(pos.get(1), "home.pos"), Json.asNumber(pos.get(2), "home.pos")),
                    (float) Json.optionalDouble(homeJson, "yaw", 0.0, -360.0, 360.0));
        }
        List<String> roles = new ArrayList<>();
        JsonArray roleArray = Json.optionalArray(json, "roles");
        for (int i = 0; i < roleArray.size(); i++) {
            roles.add(Json.asString(roleArray.get(i), "roles"));
        }
        return new NpcDef(id, name, title, skin, slim, List.copyOf(expressions), home, List.copyOf(roles),
                Json.optionalDouble(json, "talk_range", 5.0, 1.0, 32.0));
    }

    // ---------------------------------------------------------------- クライアントへ（見た目に要る分だけ）

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeUtf(name, 64);
        buf.writeUtf(title, 64);
        buf.writeResourceLocation(skin);
        buf.writeBoolean(slim);
        buf.writeVarInt(expressions.size());
        for (String e : expressions) {
            buf.writeUtf(e, 32);
        }
    }

    public static NpcDef read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        String name = buf.readUtf(64);
        String title = buf.readUtf(64);
        ResourceLocation skin = buf.readResourceLocation();
        boolean slim = buf.readBoolean();
        int n = Math.min(16, buf.readVarInt());
        List<String> expressions = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            expressions.add(buf.readUtf(32));
        }
        return new NpcDef(id, name, title, skin, slim, List.copyOf(expressions), null, List.of(), 5.0);
    }
}
