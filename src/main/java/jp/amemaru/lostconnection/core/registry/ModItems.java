package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.item.ApprenticeShieldItem;
import jp.amemaru.lostconnection.item.ApprenticeWeaponItem;
import jp.amemaru.lostconnection.item.GearStats;
import jp.amemaru.lostconnection.item.ResetItem;
import jp.amemaru.lostconnection.item.TrainingDummyItem;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * アイテムの登録。Phase 2では見習い装備・やり直しのアイテム・転職の証・訓練人形・誓約の結晶。
 * 装備のレアリティ・強化などの仕組みは、Phase 3で装備の定義と一緒に作る。
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AmemaruMod.MOD_ID);

    // ---- 見習い装備（ナイト）
    public static final RegistryObject<Item> APPRENTICE_SWORD = ITEMS.register("apprentice_sword", () ->
            new ApprenticeWeaponItem(ApprenticeWeaponItem.Kind.SWORD, GearStats.of(StatType.ATTACK, 6),
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> APPRENTICE_GREATSWORD = ITEMS.register("apprentice_greatsword", () ->
            new ApprenticeWeaponItem(ApprenticeWeaponItem.Kind.GREATSWORD, GearStats.of(StatType.ATTACK, 10),
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> APPRENTICE_SHIELD = ITEMS.register("apprentice_shield", () ->
            new ApprenticeShieldItem(GearStats.of(StatType.DEFENSE, 4), new Item.Properties().durability(500)));

    // ---- 見習い装備（ウィザード）
    public static final RegistryObject<Item> APPRENTICE_STAFF = ITEMS.register("apprentice_staff", () ->
            new ApprenticeWeaponItem(ApprenticeWeaponItem.Kind.STAFF, GearStats.of(StatType.MAGIC, 8, StatType.MP, 20),
                    new Item.Properties().stacksTo(1)));

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

    private ModItems() {
    }
}
