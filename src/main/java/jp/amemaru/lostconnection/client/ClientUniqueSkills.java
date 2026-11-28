package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.equipment.UniqueSkills;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import jp.amemaru.lostconnection.network.s2c.SyncUniqueSkillsS2C;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * クライアントが持つ、ユニーク武器の固有スキルの一覧（11月28日【補完】。サーバーの {@code SyncUniqueSkillsS2C} から）。
 */
public final class ClientUniqueSkills {
    private static List<SyncUniqueSkillsS2C.Entry> entries = List.of();
    private static Map<ResourceLocation, SyncUniqueSkillsS2C.Entry> byItem = Map.of();

    private ClientUniqueSkills() {
    }

    public static void apply(SyncUniqueSkillsS2C message) {
        entries = message.entries();
        Map<ResourceLocation, SyncUniqueSkillsS2C.Entry> map = new HashMap<>();
        for (SyncUniqueSkillsS2C.Entry entry : entries) {
            map.put(entry.item(), entry);
        }
        byItem = Map.copyOf(map);
    }

    public static void reset() {
        entries = List.of();
        byItem = Map.of();
    }

    /** 番号（{@link UniqueSkills#INDEX_BASE} から）の固有スキル（なければ null）。 */
    @Nullable
    public static SyncDefinitionsS2C.SkillInfo skill(int index) {
        int local = index - UniqueSkills.INDEX_BASE;
        return local >= 0 && local < entries.size() ? entries.get(local).skill() : null;
    }

    /** 番号の固有スキルの、武器と色（なければ null）。 */
    @Nullable
    public static SyncUniqueSkillsS2C.Entry entry(int index) {
        int local = index - UniqueSkills.INDEX_BASE;
        return local >= 0 && local < entries.size() ? entries.get(local) : null;
    }

    /** その品の固有スキル（なければ null）。 */
    @Nullable
    public static SyncUniqueSkillsS2C.Entry forStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : byItem.get(id);
    }

    public static List<SyncUniqueSkillsS2C.Entry> all() {
        return entries;
    }
}
