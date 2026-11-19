package jp.amemaru.lostconnection.equipment;

import com.google.gson.JsonElement;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * 装備の定義の置き場。サーバーはデータパックから読み、クライアントはサーバーから受け取った写しを持つ
 * （説明文の数値をクライアントで計算するため。{@code SyncContentS2C}）。
 */
public final class EquipmentDefs {
    private static final DefinitionLoader<EquipmentDef> LOADER = new Loader();
    private static volatile Snapshot client = new Snapshot(Map.of(), GearRules.current());

    /** 定義と決まりの組。 */
    public record Snapshot(Map<ResourceLocation, EquipmentDef> equipment, GearRules rules) {
        @Nullable
        public EquipmentDef get(@Nullable ResourceLocation id) {
            return id == null ? null : equipment.get(id);
        }

        @Nullable
        public EquipmentDef get(Item item) {
            return get(ForgeRegistries.ITEMS.getKey(item));
        }

        @Nullable
        public EquipmentDef get(ItemStack stack) {
            return stack.isEmpty() ? null : get(stack.getItem());
        }
    }

    private EquipmentDefs() {
    }

    /** データパックの読み込み処理。 */
    public static DefinitionLoader<EquipmentDef> loader() {
        return LOADER;
    }

    /** サーバーの定義（データパック）。 */
    public static Snapshot server() {
        return new Snapshot(LOADER.all(), GearRules.current());
    }

    /** クライアントの写し。 */
    public static Snapshot client() {
        return client;
    }

    public static void setClient(Snapshot snapshot) {
        client = snapshot;
    }

    /** どちらの側の定義を使うか。 */
    public static Snapshot of(boolean clientSide) {
        return clientSide ? client : server();
    }

    private static final class Loader extends DefinitionLoader<EquipmentDef> {
        Loader() {
            super("equipment", EquipmentDef::parse);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
            super.apply(jsons, resourceManager, profiler);
            // 定義とアイテムが合っているかを確かめる（アイテムがない・装備のアイテムでない・場所が違う）
            for (EquipmentDef def : all().values()) {
                Item item = ForgeRegistries.ITEMS.getValue(def.id());
                if (item == null || ForgeRegistries.ITEMS.getKey(item) == null
                        || !def.id().equals(ForgeRegistries.ITEMS.getKey(item))) {
                    addError(def.id() + "：同じIDのアイテムがありません（アイテムの種類は amemaru/item_types.json で登録します）");
                    continue;
                }
                if (!(item instanceof GearItem gear)) {
                    addError(def.id() + "：このアイテムは装備ではありません");
                    continue;
                }
                if (gear.gearSlot() != def.slot()) {
                    addError(def.id() + "：「slot」が " + def.slot().id() + " ですが、アイテムは " + gear.gearSlot().id() + " の装備です");
                }
            }
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                if (item instanceof GearItem gear && get(gear.gearId()).isEmpty()) {
                    addError(gear.gearId() + "：装備のアイテムですが、equipment の定義がありません（ステータスが上がりません）");
                }
            }
        }
    }
}
