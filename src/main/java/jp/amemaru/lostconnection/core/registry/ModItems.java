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

    // ---- Phase 5：塔の階・大結晶・街の家のブロック（管理者・建築係が置く）
    public static final RegistryObject<Item> TOWER_PILLAR = ITEMS.register("tower_pillar", () ->
            new BlockItem(ModBlocks.TOWER_PILLAR.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> FLOOR_SEAL = ITEMS.register("floor_seal", () ->
            new BlockItem(ModBlocks.FLOOR_SEAL.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> TOWER_SWITCH = ITEMS.register("tower_switch", () ->
            new BlockItem(ModBlocks.TOWER_SWITCH.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> TOWER_GATE = ITEMS.register("tower_gate", () ->
            new BlockItem(ModBlocks.TOWER_GATE.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> TOWER_CHEST = ITEMS.register("tower_chest", () ->
            new BlockItem(ModBlocks.TOWER_CHEST.get(), new Item.Properties().rarity(Rarity.RARE)));

    // Phase 8 その1：2階の仕掛けのブロック（管理者が置き直すとき用）
    public static final RegistryObject<Item> FOREST_BELL = ITEMS.register("forest_bell", () ->
            new BlockItem(ModBlocks.FOREST_BELL.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> KNOT_POST = ITEMS.register("knot_post", () ->
            new BlockItem(ModBlocks.KNOT_POST.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> WAYPOINT = ITEMS.register("waypoint", () ->
            new BlockItem(ModBlocks.WAYPOINT.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> TRAVEL_STONE = ITEMS.register("travel_stone", () ->
            new BlockItem(ModBlocks.TRAVEL_STONE.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> STORAGE_ACCESS = ITEMS.register("storage_access", () ->
            new BlockItem(ModBlocks.STORAGE_ACCESS.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SHOP_COUNTER = ITEMS.register("shop_counter", () ->
            new BlockItem(ModBlocks.SHOP_COUNTER.get(), new Item.Properties().rarity(Rarity.RARE)));
    /** 市場の手帳（Phase 6）：右クリックで、街のどこからでも取引所の画面を開く。ギルドの受付（リリア）でもらえる */
    public static final RegistryObject<Item> MARKET_NOTEBOOK = ITEMS.register("market_notebook", () ->
            new jp.amemaru.lostconnection.item.MarketNotebookItem(new Item.Properties().stacksTo(1)));
    /** 記録帳（Phase 6 その3）：見つけた古代記録と、階ごとの収集率。エルネストから受け取る */
    public static final RegistryObject<Item> RECORD_BOOK = ITEMS.register("record_book", () ->
            new jp.amemaru.lostconnection.item.RecordBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    /** NPCゆかりの小物（Phase 6 その3。親密度4「親友」で受け取る。見た目だけ） */
    public static final RegistryObject<Item> KEEPSAKE_LILIA = keepsake("keepsake_lilia");
    public static final RegistryObject<Item> KEEPSAKE_GARD = keepsake("keepsake_gard");
    public static final RegistryObject<Item> KEEPSAKE_DORAN = keepsake("keepsake_doran");
    public static final RegistryObject<Item> KEEPSAKE_SERENA = keepsake("keepsake_serena");
    public static final RegistryObject<Item> KEEPSAKE_ERNESTO = keepsake("keepsake_ernesto");
    public static final RegistryObject<Item> KEEPSAKE_NOA = keepsake("keepsake_noa");
    public static final RegistryObject<Item> LOST_BLANK = ITEMS.register("lost_blank", () ->
            new BlockItem(ModBlocks.LOST_BLANK.get(), new Item.Properties()));
    public static final RegistryObject<Item> MEMORY_STONE = ITEMS.register("memory_stone", () ->
            new BlockItem(ModBlocks.MEMORY_STONE.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> HOUSE_DOOR_SPRUCE = ITEMS.register("house_door_spruce", () ->
            new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.HOUSE_DOOR_SPRUCE.get(), new Item.Properties()));
    public static final RegistryObject<Item> HOUSE_DOOR_DARK_OAK = ITEMS.register("house_door_dark_oak", () ->
            new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.HOUSE_DOOR_DARK_OAK.get(), new Item.Properties()));

    // ---- 敵を呼ぶ卵（管理者・建築係が置く。Phase 3）
    public static final RegistryObject<Item> TSUNOMOFU_SPAWN_EGG = ITEMS.register("tsunomofu_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.TSUNOMOFU, 0xF3EBDD, 0xB58A5C, new Item.Properties()));
    public static final RegistryObject<Item> ISHIKURE_SOLDIER_SPAWN_EGG = ITEMS.register("ishikure_soldier_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.ISHIKURE_SOLDIER, 0x8C8A7E, 0x6FB3A8, new Item.Properties()));
    public static final RegistryObject<Item> CRYSTAL_BEE_SPAWN_EGG = ITEMS.register("crystal_bee_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CRYSTAL_BEE, 0x8FD3FF, 0xE8C15A, new Item.Properties()));
    public static final RegistryObject<Item> KAKEKIBA_WOLF_SPAWN_EGG = ITEMS.register("kakekiba_wolf_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.KAKEKIBA_WOLF, 0x5B6270, 0xF4F7FB, new Item.Properties()));
    // Phase 8 その1：2階の敵
    public static final RegistryObject<Item> MAYOI_BI_SPAWN_EGG = ITEMS.register("mayoi_bi_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.MAYOI_BI, 0xFFF2B0, 0x9FE0C8, new Item.Properties()));
    public static final RegistryObject<Item> JUHIHEI_SPAWN_EGG = ITEMS.register("juhihei_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.JUHIHEI, 0x6B4E32, 0x6E8E3A, new Item.Properties()));
    public static final RegistryObject<Item> POKODAKE_SPAWN_EGG = ITEMS.register("pokodake_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.POKODAKE, 0xB0503A, 0xE8E0C8, new Item.Properties()));
    public static final RegistryObject<Item> HAKAGE_KITSUNE_SPAWN_EGG = ITEMS.register("hakage_kitsune_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.HAKAGE_KITSUNE, 0x3E5A2E, 0xC86E2E, new Item.Properties()));

    public static final RegistryObject<Item> VELDHORN_SPAWN_EGG = ITEMS.register("veldhorn_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.VELDHORN, 0x6B5A3E, 0x7FD98A, new Item.Properties()));
    public static final RegistryObject<Item> OLD_WARD_SPAWN_EGG = ITEMS.register("old_ward_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.OLD_WARD, 0x4A3A28, 0xE8F0A8, new Item.Properties()));
    public static final RegistryObject<Item> NPC_SPAWN_EGG = ITEMS.register("npc_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.NPC, 0x2E5E3A, 0xF3E6C8, new Item.Properties()));

    private ModItems() {
    }

    private static RegistryObject<Item> keepsake(String name) {
        return ITEMS.register(name, () -> new jp.amemaru.lostconnection.item.KeepsakeItem(name,
                new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    }
}
