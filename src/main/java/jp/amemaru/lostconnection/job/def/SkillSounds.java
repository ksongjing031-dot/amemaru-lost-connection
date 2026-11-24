package jp.amemaru.lostconnection.job.def;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.job.SkillScheduler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * スキルに重ねて鳴らす音（11月24日のご指示「スキルのサウンドがすごく寂しいのでもっとつけてください」）。
 *
 * <p>{@code data/amemaru/amemaru/skill_sounds/<職業>.json}（tools/datagen/build_skill_sounds.py が書き出す）。スキルの定義の
 * {@code sound}・{@code cast_sound}・{@code impact_sound} は1つずつしか鳴らないため、その上に、技の種類ごとの音を重ねる。</p>
 *
 * <ul>
 *   <li>{@code cast}：詠唱・溜めを始めたとき</li>
 *   <li>{@code release}：放ったとき</li>
 *   <li>{@code impact}：当たったとき（1回の届きにつき1度。当たる相手がいなければ鳴らない）</li>
 * </ul>
 *
 * <p>1つの音は {@code {"sound": 音の名前（バニラの音も使える）, "volume", "pitch", "spread"（高さのゆらぎ）, "delay"（tick）}}。</p>
 */
public final class SkillSounds {
    /** 鳴らすとき。 */
    public enum Phase {
        CAST,
        RELEASE,
        IMPACT
    }

    /** 1つの音。 */
    public record Layer(ResourceLocation sound, float volume, float pitch, float spread, int delay) {
    }

    /** 1つのスキルの音（とき別）。 */
    public record Bundle(List<Layer> cast, List<Layer> release, List<Layer> impact) {
        public static final Bundle NONE = new Bundle(List.of(), List.of(), List.of());

        public List<Layer> of(Phase phase) {
            return switch (phase) {
                case CAST -> cast;
                case RELEASE -> release;
                case IMPACT -> impact;
            };
        }

        public boolean isEmpty() {
            return cast.isEmpty() && release.isEmpty() && impact.isEmpty();
        }
    }

    /** 1つのファイル（職業ごと）：スキルの名前 → 音。 */
    public record File(Map<ResourceLocation, Bundle> skills) {
        public static File parse(ResourceLocation fileId, JsonObject json) {
            JsonObject skills = Json.optionalObject(json, "skills");
            if (skills == null) {
                throw new DefinitionException("「skills」がありません");
            }
            Map<ResourceLocation, Bundle> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : skills.entrySet()) {
                if (entry.getKey().startsWith("_")) {
                    continue;
                }
                if (!entry.getValue().isJsonObject()) {
                    throw new DefinitionException("skills の「" + entry.getKey() + "」は { } にしてください");
                }
                ResourceLocation skill = entry.getKey().contains(":") ? new ResourceLocation(entry.getKey())
                        : new ResourceLocation(fileId.getNamespace(), entry.getKey());
                JsonObject one = entry.getValue().getAsJsonObject();
                String where = "skills の「" + entry.getKey() + "」：";
                out.put(skill, new Bundle(layers(one, "cast", where), layers(one, "release", where), layers(one, "impact", where)));
            }
            return new File(Collections.unmodifiableMap(out));
        }

        private static List<Layer> layers(JsonObject json, String key, String where) {
            JsonArray array = Json.optionalArray(json, key);
            if (array == null) {
                return List.of();
            }
            if (array.size() > MAX_LAYERS) {
                throw new DefinitionException(where + key + " の音が多すぎます（" + MAX_LAYERS + "まで）");
            }
            List<Layer> list = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                if (!array.get(i).isJsonObject()) {
                    throw new DefinitionException(where + key + "[" + i + "] は { } にしてください");
                }
                JsonObject o = array.get(i).getAsJsonObject();
                list.add(new Layer(Json.id(o, "sound"), (float) Json.optionalDouble(o, "volume", 1.0, 0.0, 4.0),
                        (float) Json.optionalDouble(o, "pitch", 1.0, 0.5, 2.0), (float) Json.optionalDouble(o, "spread", 0.0, 0.0, 0.5),
                        Json.optionalInt(o, "delay", 0, 0, 40)));
            }
            return List.copyOf(list);
        }
    }

    /** 1つのときに重ねる音の上限（うるさくなりすぎないように）。 */
    public static final int MAX_LAYERS = 6;

    private static final DefinitionLoader<File> LOADER = new DefinitionLoader<>("skill_sounds", File::parse);
    private static Map<ResourceLocation, Bundle> built = Map.of();
    private static int builtGeneration = -1;

    private SkillSounds() {
    }

    public static DefinitionLoader<File> loader() {
        return LOADER;
    }

    /** そのスキルの重ねる音（なければ空）。 */
    public static synchronized Bundle of(ResourceLocation skill) {
        if (LOADER.generation() != builtGeneration) {
            builtGeneration = LOADER.generation();
            built = build(LOADER.all());
        }
        return built.getOrDefault(skill, Bundle.NONE);
    }

    /** ファイルを合わせ、ない音・ないスキルを問題に足す。 */
    private static Map<ResourceLocation, Bundle> build(Map<ResourceLocation, File> files) {
        Map<ResourceLocation, Bundle> out = new HashMap<>();
        SkillRegistry.Snapshot skills = SkillRegistry.get();
        for (Map.Entry<ResourceLocation, File> file : files.entrySet()) {
            for (Map.Entry<ResourceLocation, Bundle> entry : file.getValue().skills().entrySet()) {
                String where = file.getKey() + "：" + entry.getKey() + "：";
                if (skills.skill(entry.getKey()) == null) {
                    LOADER.addError(where + "スキルがありません");
                    continue;
                }
                for (Phase phase : Phase.values()) {
                    for (Layer layer : entry.getValue().of(phase)) {
                        if (!ForgeRegistries.SOUND_EVENTS.containsKey(layer.sound())) {
                            LOADER.addError(where + "音「" + layer.sound() + "」がありません（鳴りません）");
                        }
                    }
                }
                out.put(entry.getKey(), entry.getValue());
            }
        }
        AmemaruMod.LOGGER.info("[amemaru] Skill sound layers: {} skill(s)", out.size());
        return Map.copyOf(out);
    }

    /** そのときの音を、その場所で鳴らす（遅れて鳴る音は、あとで）。 */
    public static void play(ServerLevel level, Vec3 at, SkillDef skill, Phase phase) {
        List<Layer> layers = of(skill.id()).of(phase);
        if (layers.isEmpty()) {
            return;
        }
        for (Layer layer : layers) {
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(layer.sound());
            if (sound == null) {
                continue;
            }
            float pitch = layer.pitch();
            if (layer.spread() > 0.0F) {
                pitch += (level.random.nextFloat() * 2.0F - 1.0F) * layer.spread();
            }
            float finalPitch = Math.max(0.5F, Math.min(2.0F, pitch));
            if (layer.delay() <= 0) {
                level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, layer.volume(), finalPitch);
            } else {
                SkillScheduler.schedule(level, layer.delay(),
                        () -> level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, layer.volume(), finalPitch));
            }
        }
    }
}
