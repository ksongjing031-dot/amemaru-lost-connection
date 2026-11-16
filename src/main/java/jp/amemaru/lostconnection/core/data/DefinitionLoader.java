package jp.amemaru.lostconnection.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * データパックの定義（{@code data/<名前空間>/amemaru/<種類>/*.json}）を読み込む。
 *
 * <p>サーバーの起動時と {@code /reload} のたびに読み直す。読めなかったファイルは飛ばして、
 * 問題の一覧を {@link #errors()} に残す（{@link DataReport} が管理者に知らせる）。</p>
 *
 * <p>定義のIDは「名前空間:種類のフォルダからの相対パス（.jsonを除く）」になる。
 * 例：{@code data/amemaru/amemaru/growth/default.json} → {@code amemaru:default}</p>
 *
 * @param <T> 読み込んだ定義の型
 */
public class DefinitionLoader<T> extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private final String kind;
    private final Function<JsonObject, T> parser;
    private volatile Map<ResourceLocation, T> entries = Map.of();
    private volatile List<String> errors = List.of();

    /**
     * @param kind   種類（フォルダの名前。例：{@code growth}）
     * @param parser JSONを定義に変える処理。問題があれば {@link DefinitionException} を投げる
     */
    public DefinitionLoader(String kind, Function<JsonObject, T> parser) {
        super(GSON, AmemaruMod.MOD_ID + "/" + kind);
        this.kind = kind;
        this.parser = parser;
        DataReport.register(this);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, T> loaded = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();

        List<ResourceLocation> ids = new ArrayList<>(jsons.keySet());
        Collections.sort(ids);
        for (ResourceLocation id : ids) {
            JsonElement element = jsons.get(id);
            String where = kind + "/" + id;
            try {
                if (element == null || !element.isJsonObject()) {
                    throw new DefinitionException("ファイル全体を { } で囲んだ形にしてください");
                }
                loaded.put(id, parser.apply(element.getAsJsonObject()));
            } catch (DefinitionException e) {
                problems.add(where + "：" + e.getMessage());
            } catch (RuntimeException e) {
                problems.add(where + "：読み込めませんでした（" + e.getClass().getSimpleName() + "：" + e.getMessage() + "）");
            }
        }

        this.entries = Collections.unmodifiableMap(loaded);
        this.errors = List.copyOf(problems);

        AmemaruMod.LOGGER.info("[amemaru] Loaded {} definition(s) of kind '{}' ({} problem(s))", loaded.size(), kind, problems.size());
        for (String problem : problems) {
            AmemaruMod.LOGGER.error("[amemaru] {}", problem);
        }
    }

    /** 種類の名前（フォルダの名前）。 */
    public String kind() {
        return kind;
    }

    /** IDから定義を探す。 */
    public Optional<T> get(ResourceLocation id) {
        return Optional.ofNullable(entries.get(id));
    }

    /** 読み込んだすべての定義（読み込んだ順）。 */
    public Map<ResourceLocation, T> all() {
        return entries;
    }

    /** 直前の読み込みで見つかった問題。 */
    public List<String> errors() {
        return errors;
    }

    /** 定義が見つからないなど、読み込み以外で見つかった問題を足す。 */
    public void addError(String problem) {
        List<String> next = new ArrayList<>(errors);
        next.add(kind + "：" + problem);
        this.errors = List.copyOf(next);
        AmemaruMod.LOGGER.error("[amemaru] {}：{}", kind, problem);
    }
}
