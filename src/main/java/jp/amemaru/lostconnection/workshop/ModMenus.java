package jp.amemaru.lostconnection.workshop;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 画面の中身の種類の登録（工房・アクセサリーの枠）。 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, AmemaruMod.MOD_ID);

    public static final RegistryObject<MenuType<WorkshopMenu>> WORKSHOP = MENUS.register("workshop", () ->
            IForgeMenuType.create(WorkshopMenu::fromNetwork));
    public static final RegistryObject<MenuType<AccessoryMenu>> ACCESSORY = MENUS.register("accessory", () ->
            IForgeMenuType.create(AccessoryMenu::fromNetwork));

    private ModMenus() {
    }
}
