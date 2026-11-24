package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.item.ItemManifest;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * クリエイティブのタブ（管理者・建築係が使う）。
 *
 * <ul>
 *   <li>「AMEMARU」：施設・やり直しのアイテム・敵を呼ぶ卵</li>
 *   <li>「AMEMARU：装備」：武器・盾・防具・アクセサリー（取り出すと、持ち物に入ったときに抽選される）</li>
 *   <li>「AMEMARU：素材」：素材・結晶果・消耗品（Phase 6）</li>
 * </ul>
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AmemaruMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.amemaru.main"))
            .icon(() -> new ItemStack(ModItems.OATH_CRYSTAL.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.OATH_CRYSTAL.get());
                output.accept(ModItems.WEAPON_WORKBENCH.get());
                output.accept(ModItems.ARMOR_WORKBENCH.get());
                output.accept(ModItems.ACCESSORY_WORKBENCH.get());
                output.accept(ModItems.TRAINING_DUMMY.get());
                output.accept(ModItems.FORGET_WATER.get());
                output.accept(ModItems.BLANK_OATH.get());
                output.accept(ModItems.JOB_CHANGE_TOKEN.get());
                output.accept(ModItems.MARKET_NOTEBOOK.get());
                output.accept(ModItems.RECORD_BOOK.get());
                output.accept(ModItems.KEEPSAKE_LILIA.get());
                output.accept(ModItems.KEEPSAKE_GARD.get());
                output.accept(ModItems.KEEPSAKE_DORAN.get());
                output.accept(ModItems.KEEPSAKE_SERENA.get());
                output.accept(ModItems.KEEPSAKE_ERNESTO.get());
                output.accept(ModItems.KEEPSAKE_NOA.get());
                output.accept(ModItems.TSUNOMOFU_SPAWN_EGG.get());
                output.accept(ModItems.ISHIKURE_SOLDIER_SPAWN_EGG.get());
                output.accept(ModItems.CRYSTAL_BEE_SPAWN_EGG.get());
                output.accept(ModItems.KAKEKIBA_WOLF_SPAWN_EGG.get());
                output.accept(ModItems.MAYOI_BI_SPAWN_EGG.get());
                output.accept(ModItems.JUHIHEI_SPAWN_EGG.get());
                output.accept(ModItems.POKODAKE_SPAWN_EGG.get());
                output.accept(ModItems.HAKAGE_KITSUNE_SPAWN_EGG.get());
                // Phase 8 その2：3階の敵
                output.accept(ModItems.SUIMON_NEZUMI_SPAWN_EGG.get());
                output.accept(ModItems.YODOMI_SLIME_SPAWN_EGG.get());
                output.accept(ModItems.SOUJIKIHEI_SPAWN_EGG.get());
                output.accept(ModItems.KIBA_NAMAZU_SPAWN_EGG.get());
                // 11月24日：4階の敵
                output.accept(ModItems.SILENT_STATUE_SPAWN_EGG.get());
                output.accept(ModItems.PRAYER_ECHO_SPAWN_EGG.get());
                output.accept(ModItems.CRYSTAL_BEETLE_SPAWN_EGG.get());
                output.accept(ModItems.BROKEN_BEAST_SPAWN_EGG.get());
            })
            .build());

    public static final RegistryObject<CreativeModeTab> GEAR = TABS.register("gear", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.amemaru.gear"))
            .icon(() -> new ItemStack(ModItems.MANIFEST.get("ancient_stone_sword").get()))
            .withTabsBefore(MAIN.getKey())
            .displayItems((parameters, output) -> {
                for (ItemManifest.Category category : new ItemManifest.Category[]{ItemManifest.Category.WEAPON,
                        ItemManifest.Category.SHIELD, ItemManifest.Category.ARMOR, ItemManifest.Category.ACCESSORY}) {
                    for (RegistryObject<Item> item : ModItems.MANIFEST.category(category)) {
                        output.accept(item.get());
                    }
                }
            })
            .build());

    public static final RegistryObject<CreativeModeTab> MATERIALS = TABS.register("materials", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.amemaru.materials"))
            .icon(() -> new ItemStack(ModItems.CRYSTAL_SHARD.get()))
            .withTabsBefore(GEAR.getKey())
            .displayItems((parameters, output) -> {
                for (ItemManifest.Category category : new ItemManifest.Category[]{ItemManifest.Category.MATERIAL,
                        ItemManifest.Category.FRUIT, ItemManifest.Category.CONSUMABLE, ItemManifest.Category.FISH}) {
                    for (RegistryObject<Item> item : ModItems.MANIFEST.category(category)) {
                        output.accept(item.get());
                    }
                }
            })
            .build());

    private ModCreativeTabs() {
    }
}
