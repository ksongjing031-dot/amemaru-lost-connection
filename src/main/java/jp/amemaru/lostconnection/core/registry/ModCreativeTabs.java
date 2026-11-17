package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** クリエイティブのタブ（管理者・建築係が使う）。 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AmemaruMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.amemaru.main"))
            .icon(() -> new ItemStack(ModItems.OATH_CRYSTAL.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.OATH_CRYSTAL.get());
                output.accept(ModItems.TRAINING_DUMMY.get());
                output.accept(ModItems.APPRENTICE_SWORD.get());
                output.accept(ModItems.APPRENTICE_GREATSWORD.get());
                output.accept(ModItems.APPRENTICE_SHIELD.get());
                output.accept(ModItems.APPRENTICE_STAFF.get());
                output.accept(ModItems.FORGET_WATER.get());
                output.accept(ModItems.BLANK_OATH.get());
                output.accept(ModItems.JOB_CHANGE_TOKEN.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
