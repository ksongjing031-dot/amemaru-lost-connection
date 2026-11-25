package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.tower.FloorSealBlockEntity;
import jp.amemaru.lostconnection.tower.TowerPillarBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** ブロックエンティティの登録（Phase 5：塔の石柱の核・ボスの部屋の封印。Phase 6：店のカウンター）。 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,
            AmemaruMod.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TowerPillarBlockEntity>> TOWER_PILLAR = BLOCK_ENTITIES.register("tower_pillar",
            () -> BlockEntityType.Builder.of(TowerPillarBlockEntity::new, ModBlocks.TOWER_PILLAR.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<FloorSealBlockEntity>> FLOOR_SEAL = BLOCK_ENTITIES.register("floor_seal",
            () -> BlockEntityType.Builder.of(FloorSealBlockEntity::new, ModBlocks.FLOOR_SEAL.get()).build(null));

    /** 古代の仕掛け石・古代の宝箱（どの階の何番目か。11月22日）。 */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<jp.amemaru.lostconnection.tower.FloorThingBlockEntity>> FLOOR_THING =
            BLOCK_ENTITIES.register("floor_thing", () -> BlockEntityType.Builder.of(jp.amemaru.lostconnection.tower.FloorThingBlockEntity::new,
                    ModBlocks.TOWER_SWITCH.get(), ModBlocks.TOWER_CHEST.get(), ModBlocks.FOREST_BELL.get(), ModBlocks.KNOT_POST.get(),
                    ModBlocks.WAYPOINT.get(), ModBlocks.TRAVEL_STONE.get(), ModBlocks.FLOODGATE_VALVE.get(),
                    // 11月24日：4階の壁画のパネル・鈴の石・騎士団の遺品
                    ModBlocks.MURAL_PANEL.get(), ModBlocks.CHIME_STONE.get(), ModBlocks.KNIGHT_RELIC.get(),
                    // 11月25日：6階の熱の炉・7階の光の泉・氷の鏡・光の受け石
                    ModBlocks.VILLAGE_HEARTH.get(), ModBlocks.LIGHT_SPRING.get(), ModBlocks.ICE_MIRROR.get(),
                    ModBlocks.LIGHT_TARGET.get(),
                    // 11月25日：8階の湖の鐘・歌の碑・湖底の祠の石板
                    ModBlocks.LAKE_BELL.get(), ModBlocks.SONG_STELE.get(), ModBlocks.LAKE_TABLET.get()).build(null));

    /** 6階の凍った村人の氷像（どの姿・格好・向き・記憶か。11月25日）。 */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<jp.amemaru.lostconnection.tower.village.VillageStatueBlockEntity>> VILLAGE_STATUE =
            BLOCK_ENTITIES.register("village_statue", () -> BlockEntityType.Builder.of(
                    jp.amemaru.lostconnection.tower.village.VillageStatueBlockEntity::new, ModBlocks.VILLAGE_STATUE.get()).build(null));

    /** 店のカウンターの呼び鈴（どの店か。Phase 6）。 */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<jp.amemaru.lostconnection.block.ShopCounterBlockEntity>> SHOP_COUNTER =
            BLOCK_ENTITIES.register("shop_counter", () -> BlockEntityType.Builder.of(jp.amemaru.lostconnection.block.ShopCounterBlockEntity::new,
                    ModBlocks.SHOP_COUNTER.get()).build(null));

    private ModBlockEntities() {
    }
}
