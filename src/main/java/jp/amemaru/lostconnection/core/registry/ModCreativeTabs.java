package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.item.ItemManifest;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * クリエイティブのタブ（管理者・建築係が使う）。11月29日のご指示「クリエイティブのアイテム一覧をもっと細かくしましょう」で、
 * 3つ（AMEMARU・装備・素材）を6つに分けた：
 *
 * <ol>
 *   <li>「武器」：武器（取り出すと、持ち物に入ったときに抽選される）</li>
 *   <li>「防具＆アクセサリー」：盾・防具・アクセサリー</li>
 *   <li>「建築に使うもの」：このModのブロック（大結晶・工房・塔の仕掛け・家の扉・湖の氷・王城の肖像画など）と訓練人形</li>
 *   <li>「素材」：素材（敵の落とし物・鉱石など）と魚</li>
 *   <li>「使用アイテム」：回復薬・キャンディー・料理・結晶果・道具・やり直しの品・手帳・記録帳・小物と、物語・仕掛けの品
 *       （騎士団の遺品・名前の札・女王の書簡・祈願の札など）</li>
 *   <li>「スポーンエッグ」：敵・ボス・NPCを呼ぶ卵</li>
 * </ol>
 *
 * <p>前は、9階までのボスの卵や、2階から9階の仕掛けのブロックの多くが、どのタブにも入っていなかった。今は、このModのすべての
 * アイテムを、登録の順のままどれか1つのタブに入れる（{@link #tabOf}）。新しい品も、種類から自動で入る。</p>
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AmemaruMod.MOD_ID);

    /** タブ。 */
    public enum Tab {
        WEAPONS, ARMOR, BLOCKS, MATERIALS, USABLES, SPAWN_EGGS
    }

    /** 素材の表（item_types.json の materials）にあるが、物語・仕掛けの品として「使用アイテム」に入れるもの。 */
    private static final Set<String> KEY_ITEMS = Set.of("knight_helm_relic", "harold_tag", "lake_shrine_tablet");
    private static final String[] KEY_PREFIXES = {"name_plate_", "queen_letter_", "wish_tag_"};

    public static final RegistryObject<CreativeModeTab> WEAPONS = tab("weapons", Tab.WEAPONS,
            () -> ModItems.MANIFEST.get("ancient_stone_sword").get(), null);
    public static final RegistryObject<CreativeModeTab> ARMOR = tab("armor", Tab.ARMOR,
            () -> ModItems.MANIFEST.get("ancient_stone_cuirass").get(), WEAPONS.getKey());
    public static final RegistryObject<CreativeModeTab> BLOCKS = tab("blocks", Tab.BLOCKS,
            () -> ModItems.OATH_CRYSTAL.get(), ARMOR.getKey());
    public static final RegistryObject<CreativeModeTab> MATERIALS = tab("materials", Tab.MATERIALS,
            () -> ModItems.CRYSTAL_SHARD.get(), BLOCKS.getKey());
    public static final RegistryObject<CreativeModeTab> USABLES = tab("usables", Tab.USABLES,
            () -> ModItems.MANIFEST.get("candy_strength").get(), MATERIALS.getKey());
    public static final RegistryObject<CreativeModeTab> SPAWN_EGGS = tab("spawn_eggs", Tab.SPAWN_EGGS,
            () -> ModItems.TSUNOMOFU_SPAWN_EGG.get(), USABLES.getKey());

    private ModCreativeTabs() {
    }

    /** そのタブの登録（CI の確かめの画面で使う）。 */
    public static RegistryObject<CreativeModeTab> registered(Tab tab) {
        return switch (tab) {
            case WEAPONS -> WEAPONS;
            case ARMOR -> ARMOR;
            case BLOCKS -> BLOCKS;
            case MATERIALS -> MATERIALS;
            case USABLES -> USABLES;
            case SPAWN_EGGS -> SPAWN_EGGS;
        };
    }

    private static RegistryObject<CreativeModeTab> tab(String name, Tab tab, Supplier<Item> icon, ResourceKey<CreativeModeTab> before) {
        return TABS.register(name, () -> {
            CreativeModeTab.Builder builder = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.amemaru." + name))
                    .icon(() -> new ItemStack(icon.get()))
                    .displayItems((parameters, output) -> {
                        for (Item item : items(tab)) {
                            output.accept(item);
                        }
                    });
            if (before != null) {
                builder.withTabsBefore(before);
            }
            return builder.build();
        });
    }

    /** そのタブに入る品（登録の順）。自動テストでも使う。 */
    public static List<Item> items(Tab tab) {
        List<Item> list = new ArrayList<>();
        if (tab == Tab.USABLES) {
            // 使用アイテムは、使う品（回復薬・キャンディー・料理・結晶果・道具）を先に、物語・仕掛けの品を後に
            List<Item> keys = new ArrayList<>();
            for (RegistryObject<Item> entry : ModItems.ITEMS.getEntries()) {
                if (tabOf(entry) == tab) {
                    (isKeyItem(entry.getId().getPath()) ? keys : list).add(entry.get());
                }
            }
            list.addAll(keys);
            return list;
        }
        for (RegistryObject<Item> entry : ModItems.ITEMS.getEntries()) {
            if (tabOf(entry) == tab) {
                list.add(entry.get());
            }
        }
        return list;
    }

    /** その品が入るタブ（どの品も、どれか1つ）。 */
    public static Tab tabOf(RegistryObject<Item> entry) {
        ItemManifest manifest = ModItems.MANIFEST;
        if (manifest.category(ItemManifest.Category.WEAPON).contains(entry)) {
            return Tab.WEAPONS;
        }
        if (manifest.category(ItemManifest.Category.SHIELD).contains(entry) || manifest.category(ItemManifest.Category.ARMOR).contains(entry)
                || manifest.category(ItemManifest.Category.ACCESSORY).contains(entry)) {
            return Tab.ARMOR;
        }
        Item item = entry.get();
        if (item instanceof SpawnEggItem) {
            return Tab.SPAWN_EGGS;
        }
        if (item instanceof BlockItem || entry == ModItems.TRAINING_DUMMY) {
            return Tab.BLOCKS;
        }
        String path = entry.getId().getPath();
        if ((manifest.category(ItemManifest.Category.MATERIAL).contains(entry) && !isKeyItem(path))
                || manifest.category(ItemManifest.Category.FISH).contains(entry)) {
            return Tab.MATERIALS;
        }
        return Tab.USABLES;
    }

    private static boolean isKeyItem(String path) {
        if (KEY_ITEMS.contains(path)) {
            return true;
        }
        for (String prefix : KEY_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
