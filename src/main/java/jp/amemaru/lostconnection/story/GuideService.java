package jp.amemaru.lostconnection.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * はじめの手引き（データパックの {@code guides/first_steps.json}）：目覚めてから1階のボスまでの道しるべ（Phase 5 の縦切り版の確認の目安
 * 「初めてのログインから1階のボスまで、通して遊べる」に合わせて足した【補完】）。画面の右上に、今することと、行き先の向きと距離を出す。
 *
 * <p>段の終わり（{@code done}）：{@code registered}（リリアに名前を伝えて、名簿に書いてもらった）・{@code job_selected}（職業を選んだ）・
 * {@code tower_gate}（塔の門の転移の円に立った）・
 * {@code floor_entered:N}（N階に入った）・{@code pillars:N}（N階の石柱をすべて起動した）・{@code boss:N}（N階のボスを倒した）。
 * もう済んでいる段は飛ばす（途中から始めた人・後から参加した人も、今いる段から始まる）。Phase 6 のクエストの仕組みができたら、
 * 「街めぐりクエスト」などと一緒にそちらへ移す。</p>
 */
public final class GuideService {
    /** 1つの段。{@code place} は {@code conelia}（中央広場の真ん中から）か {@code floorN}（N階の原点から）。 */
    public record Step(String id, String text, @Nullable String place, @Nullable Vec3 target, String done) {
    }

    /** 右上に出す今の段（{@code target} は世界の座標。行き先がなければ null）。 */
    public record View(int index, int total, String text, @Nullable ResourceKey<Level> dimension, @Nullable Vec3 target) {
    }

    private static final DefinitionLoader<List<Step>> LOADER = new DefinitionLoader<>("guides", GuideService::parse);
    private static final ResourceLocation FIRST_STEPS = AmemaruMod.id("first_steps");
    /** 塔の門の転移の円に立った。 */
    public static final ResourceLocation FLAG_TOWER_GATE = AmemaruMod.id("guide/tower_gate");

    private GuideService() {
    }

    public static DefinitionLoader<List<Step>> loader() {
        return LOADER;
    }

    private static List<Step> steps() {
        return LOADER.get(FIRST_STEPS).orElse(List.of());
    }

    private static List<Step> parse(JsonObject json) {
        List<Step> list = new ArrayList<>();
        JsonArray array = Json.array(json, "steps");
        for (int i = 0; i < array.size(); i++) {
            JsonObject s = Json.asObject(array.get(i), "steps");
            Vec3 target = null;
            String place = null;
            JsonObject t = Json.optionalObject(s, "target");
            if (t != null) {
                place = Json.string(t, "place");
                JsonArray pos = Json.array(t, "pos");
                if (pos.size() != 3) {
                    throw new DefinitionException("「target.pos」は [x, y, z] の3つの数にしてください");
                }
                target = new Vec3(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble());
            }
            list.add(new Step(Json.string(s, "id"), Json.string(s, "text"), place, target, Json.string(s, "done")));
        }
        return List.copyOf(list);
    }

    // ---------------------------------------------------------------- 進める

    /** 知らせ（{@code tower_gate} など）を受けて、段を進める。 */
    public static void event(ServerPlayer player, String key) {
        PlayerDataCapability.get(player).ifPresent(data -> {
            if ("tower_gate".equals(key)) {
                data.getStory().add(FLAG_TOWER_GATE);
            }
            refresh(player, data);
        });
    }

    /** 済んだ段を飛ばして、今の段を決め直す（変わったら本人に送る）。 */
    public static void refresh(ServerPlayer player, PlayerData data) {
        // 目覚める前（オープニングの演出を見る前）は出さない。目覚めた後に会話を閉じた人には、リリアの所を示す
        if (data.getStory().opening() < PlayerStory.OPENING_AWAKENED) {
            data.markDirty(PlayerData.SYNC_STORY);
            return;
        }
        List<Step> steps = steps();
        int index = data.getStory().guide();
        int before = index;
        while (index < steps.size() && done(player, data, steps.get(index).done())) {
            index++;
        }
        if (index != before) {
            data.getStory().setGuide(index);
            if (index >= steps.size() && before < steps.size()) {
                player.sendSystemMessage(Component.translatable("message.amemaru.guide.finished").withStyle(ChatFormatting.AQUA));
            } else if (index < steps.size()) {
                player.sendSystemMessage(Component.translatable("message.amemaru.guide.next", steps.get(index).text().replace("{n}", "")
                        .replace("{total}", "")).withStyle(ChatFormatting.AQUA));
            }
        }
        data.markDirty(PlayerData.SYNC_STORY);
    }

    private static boolean done(ServerPlayer player, PlayerData data, String key) {
        int colon = key.indexOf(':');
        String kind = colon < 0 ? key : key.substring(0, colon);
        int floor = 0;
        if (colon >= 0) {
            try {
                floor = Integer.parseInt(key.substring(colon + 1));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        PlayerStory story = data.getStory();
        return switch (kind) {
            case "registered" -> story.opening() >= PlayerStory.OPENING_DONE;
            case "job_selected" -> data.getCombatJob() != null;
            case "tower_gate" -> story.has(FLAG_TOWER_GATE) || story.visited(1);
            case "floor_entered" -> story.visited(floor);
            case "pillars" -> {
                FloorDef def = FloorDefs.byNumber(floor);
                int all = def == null ? 0 : (1 << def.pillars().size()) - 1;
                yield def != null && (story.pillars(floor) & all) == all;
            }
            case "boss" -> story.bossKills(floor) > 0;
            default -> false;
        };
    }

    /** 今の段（目覚める前・手引きを終えたあとは null）。 */
    @Nullable
    public static View view(ServerPlayer player, PlayerData data) {
        if (data.getStory().opening() < PlayerStory.OPENING_AWAKENED) {
            return null;
        }
        List<Step> steps = steps();
        int index = data.getStory().guide();
        if (index >= steps.size()) {
            return null;
        }
        Step step = steps.get(index);
        String text = step.text();
        if (step.done().startsWith("pillars:")) {
            int floor = Integer.parseInt(step.done().substring("pillars:".length()));
            FloorDef def = FloorDefs.byNumber(floor);
            int total = def == null ? 0 : def.pillars().size();
            text = text.replace("{n}", Integer.toString(Integer.bitCount(data.getStory().pillars(floor)))).replace("{total}",
                    Integer.toString(total));
        }
        ResourceKey<Level> dimension = null;
        Vec3 target = null;
        if (step.place() != null && step.target() != null) {
            if ("conelia".equals(step.place())) {
                Optional<BlockPos> origin = CityCommands.builtOrigin(player.server.overworld());
                if (origin.isPresent()) {
                    dimension = Level.OVERWORLD;
                    target = step.target().add(origin.get().getX(), origin.get().getY(), origin.get().getZ());
                }
            } else if (step.place().startsWith("floor")) {
                try {
                    FloorDef def = FloorDefs.byNumber(Integer.parseInt(step.place().substring("floor".length())));
                    if (def != null) {
                        dimension = FloorDefs.TOWER;
                        target = def.world(step.target());
                    }
                } catch (NumberFormatException ignored) {
                    // 行き先なし
                }
            }
        }
        return new View(index, steps.size(), text, dimension, target);
    }
}
