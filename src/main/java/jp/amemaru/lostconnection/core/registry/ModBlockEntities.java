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
                    ModBlocks.WAYPOINT.get(), ModBlocks.TRAVEL_STONE.get(), ModBlocks.FLOODGATE_VALVE.get()).build(null));

    /** 店のカウンターの呼び鈴（どの店か。Phase 6）。 */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<jp.amemaru.lostconnection.block.ShopCounterBlockEntity>> SHOP_COUNTER =
            BLOCK_ENTITIES.register("shop_counter", () -> BlockEntityType.Builder.of(jp.amemaru.lostconnection.block.ShopCounterBlockEntity::new,
                    ModBlocks.SHOP_COUNTER.get()).build(null));

    private ModBlockEntities() {
    }
}
