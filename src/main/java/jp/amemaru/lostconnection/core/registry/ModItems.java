package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.item.ItemManifest;
import jp.amemaru.lostconnection.item.ResetItem;
import jp.amemaru.lostconnection.item.TrainingDummyItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * アイテムの登録。
 *
 * <p>装備・アクセサリー・素材・結晶果は、Modに同梱した {@code amemaru/item_types.json} から起動時に登録する
 * （{@link ItemManifest}。Phase 0-B 4.3）。数値はデータパックの装備・アクセサリーの定義から読む。
 * そのほかのアイテム（やり直しのアイテム・転職の証・訓練人形・ブロック・敵を呼ぶ卵）は、ここに直接書く。</p>
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AmemaruMod.MOD_ID);

    /** 同梱のJSONから登録した装備・アクセサリー・素材・結晶果。 */
    public static final ItemManifest MANIFEST = ItemManifest.load(ITEMS);

    // ---- 見習い装備（Phase 2。今は同梱のJSONから登録する装備の一つ）
    public static final RegistryObject<Item> APPRENTICE_SWORD = MANIFEST.get("apprentice_sword");
    public static final RegistryObject<Item> APPRENTICE_GREATSWORD = MANIFEST.get("apprentice_greatsword");
    public static final RegistryObject<Item> APPRENTICE_SHIELD = MANIFEST.get("apprentice_shield");
    public static final RegistryObject<Item> APPRENTICE_STAFF = MANIFEST.get("apprentice_staff");

    // ---- コードから使う素材
    public static final RegistryObject<Item> CRYSTAL_SHARD = MANIFEST.get("crystal_shard");

    // ---- やり直し・転職
    public static final RegistryObject<Item> FORGET_WATER = ITEMS.register("forget_water", () ->
            new ResetItem(ResetItem.Kind.STATS, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BLANK_OATH = ITEMS.register("blank_oath", () ->
            new ResetItem(ResetItem.Kind.SKILLS, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> JOB_CHANGE_TOKEN = ITEMS.register("job_change_token", () ->
            new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    // ---- 訓練と施設
    public static final RegistryObject<Item> TRAINING_DUMMY = ITEMS.register("training_dummy", () ->
            new TrainingDummyItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> OATH_CRYSTAL = ITEMS.register("oath_crystal", () ->
            new BlockItem(ModBlocks.OATH_CRYSTAL.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> WEAPON_WORKBENCH = ITEMS.register("weapon_workbench", () ->
            new BlockItem(ModBlocks.WEAPON_WORKBENCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> ARMOR_WORKBENCH = ITEMS.register("armor_workbench", () ->
            new BlockItem(ModBlocks.ARMOR_WORKBENCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> ACCESSORY_WORKBENCH = ITEMS.register("accessory_workbench", () ->
            new BlockItem(ModBlocks.ACCESSORY_WORKBENCH.get(), new Item.Properties()));

    // ---- 敵を呼ぶ卵（管理者・建築係が置く。Phase 3）
    public static final RegistryObject<Item> TSUNOMOFU_SPAWN_EGG = ITEMS.register("tsunomofu_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.TSUNOMOFU, 0xF3EBDD, 0xB58A5C, new Item.Properties()));
    public static final RegistryObject<Item> ISHIKURE_SOLDIER_SPAWN_EGG = ITEMS.register("ishikure_soldier_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.ISHIKURE_SOLDIER, 0x8C8A7E, 0x6FB3A8, new Item.Properties()));
    public static final RegistryObject<Item> CRYSTAL_BEE_SPAWN_EGG = ITEMS.register("crystal_bee_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CRYSTAL_BEE, 0x8FD3FF, 0xE8C15A, new Item.Properties()));
    public static final RegistryObject<Item> KAKEKIBA_WOLF_SPAWN_EGG = ITEMS.register("kakekiba_wolf_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.KAKEKIBA_WOLF, 0x5B6270, 0xF4F7FB, new Item.Properties()));

    private ModItems() {
    }
}
