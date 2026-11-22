package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.workshop.WorkshopBlock;
import jp.amemaru.lostconnection.workshop.WorkshopType;
import jp.amemaru.lostconnection.block.OathCrystalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** ブロックの登録。 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AmemaruMod.MOD_ID);

    /** 誓約の結晶（職業選択の台）。サバイバル・アドベンチャーでは壊せない。 */
    public static final RegistryObject<Block> OATH_CRYSTAL = BLOCKS.register("oath_crystal", () -> new OathCrystalBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 11)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    /** 武器工房の作業台（Phase 3）。 */
    public static final RegistryObject<Block> WEAPON_WORKBENCH = BLOCKS.register("weapon_workbench", () ->
            new WorkshopBlock(WorkshopType.WEAPON, workbench(MapColor.COLOR_ORANGE)));
    /** 防具工房の作業台（Phase 3）。 */
    public static final RegistryObject<Block> ARMOR_WORKBENCH = BLOCKS.register("armor_workbench", () ->
            new WorkshopBlock(WorkshopType.ARMOR, workbench(MapColor.COLOR_GRAY)));
    /** 装飾工房の作業台（Phase 3）。 */
    public static final RegistryObject<Block> ACCESSORY_WORKBENCH = BLOCKS.register("accessory_workbench", () ->
            new WorkshopBlock(WorkshopType.ACCESSORY, workbench(MapColor.COLOR_PURPLE)));

    // ---- Phase 5：塔の階・大結晶・街の家

    /** 起動する石柱の核（1階の3本の石柱。Phase 0-A 9章）。 */
    public static final RegistryObject<Block> TOWER_PILLAR = BLOCKS.register("tower_pillar", () -> new jp.amemaru.lostconnection.tower.TowerPillarBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.EMERALD)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    /** ボスの部屋の入口の封印（光の幕。石柱をすべて起動した人だけ通れる）。 */
    public static final RegistryObject<Block> FLOOR_SEAL = BLOCKS.register("floor_seal", () -> new jp.amemaru.lostconnection.tower.FloorSealBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 8)
                    .noOcclusion()
                    .noLootTable()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.BLOCK)));

    /** 古代の仕掛け石（11月22日。1階の東の遺跡。組の石をそろえて押すと門がひらく）。 */
    public static final RegistryObject<Block> TOWER_SWITCH = BLOCKS.register("tower_switch", () -> new jp.amemaru.lostconnection.tower.TowerSwitchBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(jp.amemaru.lostconnection.tower.TowerSwitchBlock.LIT) ? 9 : 2)
                    .pushReaction(PushReaction.BLOCK)));

    /** 古代の格子（11月22日。仕掛け石でひらく門）。 */
    public static final RegistryObject<Block> TOWER_GATE = BLOCKS.register("tower_gate", () -> new jp.amemaru.lostconnection.tower.TowerGateBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> state.getValue(jp.amemaru.lostconnection.tower.TowerGateBlock.OPEN) ? 0 : 6)
                    .noOcclusion()
                    .noLootTable()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.BLOCK)));

    /** 古代の宝箱（11月22日。人それぞれ1度だけ開けられる）。 */
    public static final RegistryObject<Block> TOWER_CHEST = BLOCKS.register("tower_chest", () -> new jp.amemaru.lostconnection.tower.TowerChestBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 3)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    /** ロストの「欠け」（白く抜け落ちた所。Phase 0-A 3章）。 */
    public static final RegistryObject<Block> LOST_BLANK = BLOCKS.register("lost_blank", () -> new jp.amemaru.lostconnection.block.LostBlankBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK)));

    /** 大結晶の記憶の石（記憶の再生。Phase 0-A 14章）。 */
    public static final RegistryObject<Block> MEMORY_STONE = BLOCKS.register("memory_stone", () -> new jp.amemaru.lostconnection.block.MemoryStoneBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 10)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    /**
     * 街の家の扉（トウヒ・ダークオークと同じ見た目で、手では開かない）。民間の家は中に入らないので、内装を作らない
     * （11月21日のご指示）。
     */
    public static final RegistryObject<Block> HOUSE_DOOR_SPRUCE = BLOCKS.register("house_door_spruce", () -> new net.minecraft.world.level.block.DoorBlock(
            BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.SPRUCE_DOOR)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable(), net.minecraft.world.level.block.state.properties.BlockSetType.IRON));
    public static final RegistryObject<Block> HOUSE_DOOR_DARK_OAK = BLOCKS.register("house_door_dark_oak", () -> new net.minecraft.world.level.block.DoorBlock(
            BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.DARK_OAK_DOOR)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable(), net.minecraft.world.level.block.state.properties.BlockSetType.IRON));

    /**
     * 個人倉庫の窓口（Phase 6）：地下の小部屋の呼び出しの封印箱（box）と、1階のカウンターの「契約・拡張」（contract）・
     * 「受け取り口」（receive）。右クリックで倉庫の画面を開く（11月22日に決定：施設はGUIで使う）。
     */
    public static final RegistryObject<Block> STORAGE_ACCESS = BLOCKS.register("storage_access", () -> new jp.amemaru.lostconnection.block.StorageAccessBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.LODESTONE)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    /** 店のカウンターの呼び鈴（Phase 6）：右クリックで、その店の「買う・売る」の画面を開く。どの店かはブロックエンティティが持つ。 */
    public static final RegistryObject<Block> SHOP_COUNTER = BLOCKS.register("shop_counter", () -> new jp.amemaru.lostconnection.block.ShopCounterBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    /** 工房の作業台（サバイバル・アドベンチャーでは壊せない。街の施設のため）。 */
    private static BlockBehaviour.Properties workbench(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.WOOD)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    private ModBlocks() {
    }
}
