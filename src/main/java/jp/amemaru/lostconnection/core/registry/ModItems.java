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
    // Phase 8 その2：3階の仕掛けのブロック
    public static final RegistryObject<Item> FLOODGATE_VALVE = ITEMS.register("floodgate_valve", () ->
            new BlockItem(ModBlocks.FLOODGATE_VALVE.get(), new Item.Properties().rarity(Rarity.RARE)));
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

    // Phase 8 その2：3階の敵
    public static final RegistryObject<Item> SUIMON_NEZUMI_SPAWN_EGG = ITEMS.register("suimon_nezumi_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.SUIMON_NEZUMI, 0x6E6A66, 0xC9A27E, new Item.Properties()));
    public static final RegistryObject<Item> YODOMI_SLIME_SPAWN_EGG = ITEMS.register("yodomi_slime_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.YODOMI_SLIME, 0x3E5A55, 0x7FB3A6, new Item.Properties()));
    public static final RegistryObject<Item> SOUJIKIHEI_SPAWN_EGG = ITEMS.register("soujikihei_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.SOUJIKIHEI, 0x8A6A3A, 0x4FA3B8, new Item.Properties()));
    public static final RegistryObject<Item> KIBA_NAMAZU_SPAWN_EGG = ITEMS.register("kiba_namazu_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.KIBA_NAMAZU, 0x3B4A3E, 0xE8E0C8, new Item.Properties()));
    public static final RegistryObject<Item> HYDRAM_SPAWN_EGG = ITEMS.register("hydram_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.HYDRAM, 0x6B7A86, 0x48C8FF, new Item.Properties()));

    // 11月24日：4階の敵
    public static final RegistryObject<Item> SILENT_STATUE_SPAWN_EGG = ITEMS.register("silent_statue_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.SILENT_STATUE, 0xD8D3C4, 0x7E786C, new Item.Properties()));
    public static final RegistryObject<Item> PRAYER_ECHO_SPAWN_EGG = ITEMS.register("prayer_echo_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.PRAYER_ECHO, 0xF4EEDC, 0xDCBC64, new Item.Properties()));
    public static final RegistryObject<Item> CRYSTAL_BEETLE_SPAWN_EGG = ITEMS.register("crystal_beetle_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CRYSTAL_BEETLE, 0x2A3442, 0x97C3C9, new Item.Properties()));
    public static final RegistryObject<Item> BROKEN_BEAST_SPAWN_EGG = ITEMS.register("broken_beast_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.BROKEN_BEAST, 0xE0DACB, 0xDCBC64, new Item.Properties()));
    public static final RegistryObject<Item> CANTARE_SPAWN_EGG = ITEMS.register("cantare_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CANTARE, 0xF4EEDC, 0x8478C0, new Item.Properties()));
    public static final RegistryObject<Item> WATCHER_SPAWN_EGG = ITEMS.register("watcher_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.WATCHER, 0xE8D9A8, 0xC8342C, new Item.Properties()));
    public static final RegistryObject<Item> SENTINEL_SPAWN_EGG = ITEMS.register("sentinel_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.SENTINEL, 0xD8D4C8, 0x3E8C6E, new Item.Properties()));
    public static final RegistryObject<Item> REPAIR_DRONE_SPAWN_EGG = ITEMS.register("repair_drone_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.REPAIR_DRONE, 0xA07A3C, 0x5CE0A4, new Item.Properties()));
    public static final RegistryObject<Item> ANCIENT_GUARDIAN_SPAWN_EGG = ITEMS.register("ancient_guardian_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.ANCIENT_GUARDIAN, 0xE6E2D6, 0x4CD69A, new Item.Properties()));
    // 11月25日：6階「凍結した村」
    public static final RegistryObject<Item> FROST_WOLF_SPAWN_EGG = ITEMS.register("frost_wolf_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.FROST_WOLF, 0xE6F0F8, 0x7A8EA6, new Item.Properties()));
    public static final RegistryObject<Item> STATUE_ECHO_SPAWN_EGG = ITEMS.register("statue_echo_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.STATUE_ECHO, 0xD6E8F6, 0x9CC8E8, new Item.Properties()));
    public static final RegistryObject<Item> FROST_BIRD_SPAWN_EGG = ITEMS.register("frost_bird_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.FROST_BIRD, 0xF2F7FC, 0x5A8EBA, new Item.Properties()));
    public static final RegistryObject<Item> FROST_BEAR_SPAWN_EGG = ITEMS.register("frost_bear_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.FROST_BEAR, 0xD0E0EE, 0x2A344A, new Item.Properties()));
    public static final RegistryObject<Item> ORDO_SPAWN_EGG = ITEMS.register("ordo_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.ORDO, 0x2A3A62, 0x9FD8FF, new Item.Properties()));
    // 11月25日：7階
    public static final RegistryObject<Item> MIMIC_SPAWN_EGG = ITEMS.register("mimic_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.MIMIC, 0xE8EEF5, 0x8C9AAE, new Item.Properties()));
    public static final RegistryObject<Item> ICE_STAG_SPAWN_EGG = ITEMS.register("ice_stag_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.ICE_STAG, 0xB6CADC, 0x9CC8E8, new Item.Properties()));
    public static final RegistryObject<Item> ICICLE_BAT_SPAWN_EGG = ITEMS.register("icicle_bat_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.ICICLE_BAT, 0x2A344A, 0xBEDDF4, new Item.Properties()));
    public static final RegistryObject<Item> BLIZZARD_SPIRIT_SPAWN_EGG = ITEMS.register("blizzard_spirit_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.BLIZZARD_SPIRIT, 0xF2F7FC, 0xB6A0E0, new Item.Properties()));
    public static final RegistryObject<Item> REFLECTA_SPAWN_EGG = ITEMS.register("reflecta_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.REFLECTA, 0xDDEEFA, 0xC7A6F0, new Item.Properties()));
    // 11月25日：8階
    public static final RegistryObject<Item> SILENT_FISH_SPAWN_EGG = ITEMS.register("silent_fish_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.SILENT_FISH, 0xE6ECF2, 0x2C3A52, new Item.Properties()));
    public static final RegistryObject<Item> VOICE_EATER_SPAWN_EGG = ITEMS.register("voice_eater_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.VOICE_EATER, 0xD8DEE8, 0x3FA7B8, new Item.Properties()));
    public static final RegistryObject<Item> FROST_CRANE_SPAWN_EGG = ITEMS.register("frost_crane_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.FROST_CRANE, 0xF4F6FA, 0x20242E, new Item.Properties()));
    public static final RegistryObject<Item> FOG_WRAITH_SPAWN_EGG = ITEMS.register("fog_wraith_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.FOG_WRAITH, 0xB9C3CF, 0x6D7A8C, new Item.Properties()));
    public static final RegistryObject<Item> MUNE_SPAWN_EGG = ITEMS.register("mune_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.MUNE, 0xEEF2F6, 0x8FD8E8, new Item.Properties()));
    // 11月28日：9階「氷結した王城」
    public static final RegistryObject<Item> FROZEN_GUARD_SPAWN_EGG = ITEMS.register("frozen_guard_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.FROZEN_GUARD, 0xB8D4EC, 0x2E4A86, new Item.Properties()));
    public static final RegistryObject<Item> ICE_CAVALRY_SPAWN_EGG = ITEMS.register("ice_cavalry_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.ICE_CAVALRY, 0xD6E6F4, 0x3A5C9A, new Item.Properties()));
    public static final RegistryObject<Item> SHELF_WRAITH_SPAWN_EGG = ITEMS.register("shelf_wraith_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.SHELF_WRAITH, 0xE8E2D0, 0x6C86B8, new Item.Properties()));
    public static final RegistryObject<Item> FROST_HOUND_SPAWN_EGG = ITEMS.register("frost_hound_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.FROST_HOUND, 0xDCE4EC, 0x2C4C8C, new Item.Properties()));
    public static final RegistryObject<Item> FORGOTTEN_MAID_SPAWN_EGG = ITEMS.register("forgotten_maid_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.FORGOTTEN_MAID, 0xF4F2EE, 0x2A2E3E, new Item.Properties()));
    public static final RegistryObject<Item> VALGRIM_SPAWN_EGG = ITEMS.register("valgrim_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.VALGRIM, 0xC4DCF2, 0x1E3670, new Item.Properties()));
    // 11月24日：5階の結び台
    public static final RegistryObject<Item> BINDING_DAIS = ITEMS.register("binding_dais", () ->
            new BlockItem(ModBlocks.BINDING_DAIS.get(), new Item.Properties().rarity(Rarity.RARE)));
    // 11月24日：4階の仕掛けのブロック
    // 11月25日：6階の熱の炉と、凍った村人の氷像
    public static final RegistryObject<Item> VILLAGE_HEARTH = ITEMS.register("village_hearth", () ->
            new BlockItem(ModBlocks.VILLAGE_HEARTH.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> VILLAGE_STATUE = ITEMS.register("village_statue", () ->
            new BlockItem(ModBlocks.VILLAGE_STATUE.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> LIGHT_SPRING = ITEMS.register("light_spring", () ->
            new BlockItem(ModBlocks.LIGHT_SPRING.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> ICE_MIRROR = ITEMS.register("ice_mirror", () ->
            new BlockItem(ModBlocks.ICE_MIRROR.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> LIGHT_TARGET = ITEMS.register("light_target", () ->
            new BlockItem(ModBlocks.LIGHT_TARGET.get(), new Item.Properties().rarity(Rarity.RARE)));
    // 11月25日：8階
    public static final RegistryObject<Item> LAKE_ICE = ITEMS.register("lake_ice", () ->
            new BlockItem(ModBlocks.LAKE_ICE.get(), new Item.Properties()));
    public static final RegistryObject<Item> THIN_ICE = ITEMS.register("thin_ice", () ->
            new BlockItem(ModBlocks.THIN_ICE.get(), new Item.Properties()));
    public static final RegistryObject<Item> LAKE_BELL = ITEMS.register("lake_bell", () ->
            new BlockItem(ModBlocks.LAKE_BELL.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SONG_STELE = ITEMS.register("song_stele", () ->
            new BlockItem(ModBlocks.SONG_STELE.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> LAKE_TABLET = ITEMS.register("lake_tablet", () ->
            new BlockItem(ModBlocks.LAKE_TABLET.get(), new Item.Properties().rarity(Rarity.RARE)));
    // 11月28日：9階
    public static final RegistryObject<Item> ROYAL_PORTRAIT = ITEMS.register("royal_portrait", () ->
            new BlockItem(ModBlocks.ROYAL_PORTRAIT.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> NAME_PLATE_STAND = ITEMS.register("name_plate_stand", () ->
            new BlockItem(ModBlocks.NAME_PLATE_STAND.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> LINEAGE_STELE = ITEMS.register("lineage_stele", () ->
            new BlockItem(ModBlocks.LINEAGE_STELE.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> LINEAGE_BOOK = ITEMS.register("lineage_book", () ->
            new BlockItem(ModBlocks.LINEAGE_BOOK.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> QUEEN_LETTER = ITEMS.register("queen_letter", () ->
            new BlockItem(ModBlocks.QUEEN_LETTER.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> STUDY_LATCH = ITEMS.register("study_latch", () ->
            new BlockItem(ModBlocks.STUDY_LATCH.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> MURAL_PANEL = ITEMS.register("mural_panel", () ->
            new BlockItem(ModBlocks.MURAL_PANEL.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SOUND_PLATE = ITEMS.register("sound_plate", () ->
            new BlockItem(ModBlocks.SOUND_PLATE.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> CHIME_STONE = ITEMS.register("chime_stone", () ->
            new BlockItem(ModBlocks.CHIME_STONE.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> KNIGHT_RELIC = ITEMS.register("knight_relic", () ->
            new BlockItem(ModBlocks.KNIGHT_RELIC.get(), new Item.Properties().rarity(Rarity.RARE)));

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
