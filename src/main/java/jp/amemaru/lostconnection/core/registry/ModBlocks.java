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
