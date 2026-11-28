package jp.amemaru.lostconnection.equipment;

import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ユニーク武器の固有スキルの一覧（11月28日【補完】。{@link UniqueSkillDef}）。
 *
 * <p>演出（{@code SkillFxS2C}）は職業のスキルと同じ道を通る。スキルの番号は {@link #INDEX_BASE} + 一覧の番号にして、
 * 職業のスキルの番号と重ならないようにする（クライアントの {@code ClientDefinitions.skill} が、この番号から固有スキルを返す）。</p>
 */
public final class UniqueSkills {
    /** 固有スキルの番号の始まり（職業のスキルの番号と重ならない大きさ）。 */
    public static final int INDEX_BASE = 100000;

    private static final DefinitionLoader<UniqueSkillDef> LOADER = new DefinitionLoader<>("unique_skills", UniqueSkillDef::parse);

    private static int builtGeneration = -1;
    private static List<UniqueSkillDef> list = List.of();
    private static Map<ResourceLocation, UniqueSkillDef> byItem = Map.of();
    private static Map<ResourceLocation, Integer> indexById = Map.of();

    private UniqueSkills() {
    }

    public static DefinitionLoader<UniqueSkillDef> loader() {
        return LOADER;
    }

    private static synchronized void ensure() {
        if (LOADER.generation() == builtGeneration) {
            return;
        }
        builtGeneration = LOADER.generation();
        List<UniqueSkillDef> next = new ArrayList<>();
        Map<ResourceLocation, UniqueSkillDef> items = new HashMap<>();
        Map<ResourceLocation, Integer> indices = new HashMap<>();
        for (UniqueSkillDef def : LOADER.all().values()) {
            String where = def.id() + "：";
            if (!ForgeRegistries.ITEMS.containsKey(def.item())) {
                LOADER.addError(where + "武器「" + def.item() + "」がありません");
                continue;
            }
            if (items.containsKey(def.item())) {
                LOADER.addError(where + "武器「" + def.item() + "」には、もう固有スキル「" + items.get(def.item()).id() + "」があります");
                continue;
            }
            List<String> problems = new ArrayList<>();
            if (!SkillRegistry.checkEffects(def.skill().effects(), where, problems)) {
                problems.forEach(LOADER::addError);
                continue;
            }
            SkillRegistry.checkSound(def.skill().sound(), where, problems);
            SkillRegistry.checkSound(def.skill().impactSound(), where, problems);
            problems.forEach(LOADER::addError);
            indices.put(def.skill().id(), next.size());
            items.put(def.item(), def);
            next.add(def);
        }
        list = Collections.unmodifiableList(next);
        byItem = Map.copyOf(items);
        indexById = Map.copyOf(indices);
    }

    /** 読み込んだ固有スキル（番号の順）。 */
    public static List<UniqueSkillDef> all() {
        ensure();
        return list;
    }

    /** その品（手に持っている武器）の固有スキル（なければ null）。 */
    @Nullable
    public static UniqueSkillDef forStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : forItem(id);
    }

    @Nullable
    public static UniqueSkillDef forItem(ResourceLocation item) {
        ensure();
        return byItem.get(item);
    }

    /** 固有スキルの番号（演出に使う。{@link #INDEX_BASE} から。なければ -1）。 */
    public static int index(UniqueSkillDef def) {
        ensure();
        Integer index = indexById.get(def.skill().id());
        return index == null ? -1 : INDEX_BASE + index;
    }

    /** スキルID（{@code amemaru:unique/...}）から番号（なければ -1）。 */
    public static int indexOfSkill(ResourceLocation skillId) {
        ensure();
        Integer index = indexById.get(skillId);
        return index == null ? -1 : INDEX_BASE + index;
    }

    /** 番号から（{@link #INDEX_BASE} からの番号。なければ null）。 */
    @Nullable
    public static UniqueSkillDef byIndex(int index) {
        ensure();
        int local = index - INDEX_BASE;
        return local >= 0 && local < list.size() ? list.get(local) : null;
    }

    /** 固有スキルのスキルID（{@code amemaru:unique/...}）か。 */
    public static boolean isUniqueSkillId(@Nullable ResourceLocation id) {
        return id != null && id.getPath().startsWith("unique/");
    }
}
