package jp.amemaru.lostconnection.story;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 会話・ストーリーイベント・手引きに共通の条件（Phase 0-B 6章「共通の決まり」の {@code conditions}）。
 *
 * <ul>
 *   <li>{@code flags_all}・{@code flags_any}・{@code flags_none}：物語フラグ（サーバー全体のフラグと、本人のフラグのどちらかで立っていればよい）</li>
 *   <li>{@code day_min}・{@code day_max}：進行日の範囲。{@code chapter}：章</li>
 *   <li>{@code has_job}：戦闘職を選んでいるか（Phase 5で足した）。{@code job}：その戦闘職か</li>
 *   <li>{@code opening}：オープニングの進み（0・1・2。Phase 5で足した）</li>
 *   <li>{@code affinity_min}・{@code title}・{@code remembered}：親密度・称号・NPCの記憶。Phase 6・8で働く（いまは読み取るだけ。
 *       親密度と称号はまだないので「足りている」、記憶はまだ失われないので「覚えている」とみなす）</li>
 * </ul>
 */
public record Conditions(List<ResourceLocation> flagsAll, List<ResourceLocation> flagsAny, List<ResourceLocation> flagsNone,
                         int dayMin, int dayMax, int chapter, @Nullable Boolean hasJob, @Nullable ResourceLocation job,
                         int opening, int affinityMin, @Nullable ResourceLocation title, @Nullable Boolean remembered) {
    public static final Conditions NONE = new Conditions(List.of(), List.of(), List.of(), 0, Integer.MAX_VALUE, 0, null, null, -1, 0,
            null, null);

    public static Conditions parse(@Nullable JsonObject json) {
        if (json == null) {
            return NONE;
        }
        Boolean hasJob = json.has("has_job") ? Json.optionalBoolean(json, "has_job", false) : null;
        Boolean remembered = json.has("remembered") ? Json.optionalBoolean(json, "remembered", true) : null;
        return new Conditions(Json.idList(json, "flags_all"), Json.idList(json, "flags_any"), Json.idList(json, "flags_none"),
                Json.optionalInt(json, "day_min", 0, 0, ProgressionData.MAX_DAY),
                Json.optionalInt(json, "day_max", Integer.MAX_VALUE, 0, Integer.MAX_VALUE),
                Json.optionalInt(json, "chapter", 0, 0, 9), hasJob, Json.optionalId(json, "job"),
                Json.optionalInt(json, "opening", -1, -1, PlayerStory.OPENING_DONE),
                Json.optionalInt(json, "affinity_min", 0, 0, 5), Json.optionalId(json, "title"), remembered);
    }

    /** その人について、条件がすべて合っているか。 */
    public boolean test(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        StoryState state = StoryState.get(player.server);
        ProgressionData progress = ProgressionData.get(player.server);
        for (ResourceLocation flag : flagsAll) {
            if (!flag(state, data, flag)) {
                return false;
            }
        }
        if (!flagsAny.isEmpty()) {
            boolean any = false;
            for (ResourceLocation flag : flagsAny) {
                if (flag(state, data, flag)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        for (ResourceLocation flag : flagsNone) {
            if (flag(state, data, flag)) {
                return false;
            }
        }
        int day = progress.day();
        if (day < dayMin || day > dayMax) {
            return false;
        }
        if (chapter > 0 && progress.chapter() != chapter) {
            return false;
        }
        if (hasJob != null && (data == null || (data.getCombatJob() != null) != hasJob)) {
            return false;
        }
        if (job != null && (data == null || !job.equals(data.getCombatJob()))) {
            return false;
        }
        if (opening >= 0 && (data == null || data.getStory().opening() != opening)) {
            return false;
        }
        // 親密度・称号（Phase 6）・NPCの記憶（Phase 8）はまだないので、いまは条件に合っているとみなす
        return remembered == null || remembered;
    }

    private static boolean flag(StoryState state, @Nullable PlayerData data, ResourceLocation flag) {
        return state.has(flag) || (data != null && data.getStory().has(flag));
    }
}
