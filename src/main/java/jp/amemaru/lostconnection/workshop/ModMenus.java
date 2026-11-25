package jp.amemaru.lostconnection.workshop;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 画面の中身の種類の登録（工房・アクセサリーの枠・個人倉庫）。 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, AmemaruMod.MOD_ID);

    public static final RegistryObject<MenuType<WorkshopMenu>> WORKSHOP = MENUS.register("workshop", () ->
            IForgeMenuType.create(WorkshopMenu::fromNetwork));
    public static final RegistryObject<MenuType<AccessoryMenu>> ACCESSORY = MENUS.register("accessory", () ->
            IForgeMenuType.create(AccessoryMenu::fromNetwork));
    /** 個人倉庫（Phase 6） */
    public static final RegistryObject<MenuType<jp.amemaru.lostconnection.economy.StorageMenu>> STORAGE = MENUS.register("storage", () ->
            IForgeMenuType.create(jp.amemaru.lostconnection.economy.StorageMenu::fromNetwork));

    private ModMenus() {
    }
}
