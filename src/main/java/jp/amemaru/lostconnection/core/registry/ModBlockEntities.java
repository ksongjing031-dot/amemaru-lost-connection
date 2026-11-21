package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.tower.FloorSealBlockEntity;
import jp.amemaru.lostconnection.tower.TowerPillarBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** ブロックエンティティの登録（Phase 5：塔の石柱の核・ボスの部屋の封印）。 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,
            AmemaruMod.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TowerPillarBlockEntity>> TOWER_PILLAR = BLOCK_ENTITIES.register("tower_pillar",
            () -> BlockEntityType.Builder.of(TowerPillarBlockEntity::new, ModBlocks.TOWER_PILLAR.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<FloorSealBlockEntity>> FLOOR_SEAL = BLOCK_ENTITIES.register("floor_seal",
            () -> BlockEntityType.Builder.of(FloorSealBlockEntity::new, ModBlocks.FLOOR_SEAL.get()).build(null));

    private ModBlockEntities() {
    }
}
