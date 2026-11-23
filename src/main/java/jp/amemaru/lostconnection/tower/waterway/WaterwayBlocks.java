package jp.amemaru.lostconnection.tower.waterway;

import jp.amemaru.lostconnection.tower.FloorThingBlockEntity;
import jp.amemaru.lostconnection.tower.forest.ForestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * 3階「古代の水路」の仕掛けのブロック（Phase 8 その2）。
 *
 * <ul>
 *   <li>{@link Valve}：水門のハンドル（組の名前は {@link FloorThingBlockEntity#group}、組の中のどちらかは {@code index}）。
 *       右クリックで回す（{@link FloodgateService#turn}）。水門が開くと {@link #OPEN}</li>
 * </ul>
 */
public final class WaterwayBlocks {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private WaterwayBlocks() {
    }

    /** 水門のハンドル。 */
    public static class Valve extends ForestBlocks.Thing {
        public Valve(Properties properties) {
            super(properties, Block.box(1, 0, 1, 15, 16, 15));
            registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(OPEN, FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            FloodgateService.turn(player, thing.floor(), thing.group(), thing.index(), pos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(OPEN) && random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.DRIPPING_WATER, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.9,
                        pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.0, 0.0);
            }
        }
    }

    /** ハンドルの形を、開いた・閉じたに（読み込まれていれば）。 */
    public static void setValveOpen(ServerLevel level, BlockPos pos, boolean open) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(OPEN) && state.getValue(OPEN) != open) {
            level.setBlock(pos, state.setValue(OPEN, open), 3);
        }
    }
}
