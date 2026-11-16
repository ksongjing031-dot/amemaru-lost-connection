package jp.amemaru.lostconnection.player;

import com.google.gson.JsonElement;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * 今使っている成長の定義を返す。
 *
 * <p>データパックに {@code amemaru:default} がないとき・読めなかったときは、同じ数値を持つ
 * {@link GrowthDef#FALLBACK} を使い、管理者に知らせる。</p>
 */
public final class GrowthRules {
    private static final DefinitionLoader<GrowthDef> LOADER = new GrowthLoader();

    private GrowthRules() {
    }

    /** データパックの読み込み処理（{@code AddReloadListenerEvent} で登録する）。 */
    public static DefinitionLoader<GrowthDef> loader() {
        return LOADER;
    }

    /** 今の成長の定義。 */
    public static GrowthDef current() {
        return LOADER.get(GrowthDef.DEFAULT_ID).orElse(GrowthDef.FALLBACK);
    }

    private static final class GrowthLoader extends DefinitionLoader<GrowthDef> {
        GrowthLoader() {
            super("growth", GrowthDef::parse);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
            super.apply(jsons, resourceManager, profiler);
            if (get(GrowthDef.DEFAULT_ID).isEmpty()) {
                addError("「" + GrowthDef.DEFAULT_ID + "」が見つからないため、Modに入っている初期値を使います");
            }
        }
    }
}
