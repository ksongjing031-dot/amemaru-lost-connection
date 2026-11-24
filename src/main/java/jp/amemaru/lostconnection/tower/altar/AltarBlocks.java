package jp.amemaru.lostconnection.tower.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 5階「古代守護者の祭壇」のブロック（11月24日【補完】）。
 *
 * <ul>
 *   <li>{@link BindingDais} 結び台（Phase 0-A 9章・10章の古代守護者「4つの結び台に4人が同時に乗る」）：白い石の円い台に、
 *       金の結び目の模様。守護者が結界を張っている間は光り（{@code lit}）、人が乗ると結び目が明るくなる。乗っている人は
 *       守護者が数える（{@link jp.amemaru.lostconnection.enemy.boss.AncientGuardian}）</li>
 * </ul>
 */
public final class AltarBlocks {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private AltarBlocks() {
    }

    /** 結び台（床から3ピクセルの台）。 */
    public static class BindingDais extends Block {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 3, 16);

        public BindingDais(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }

        @SuppressWarnings("deprecation")
        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(LIT) && random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + 0.3,
                        pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0.0, 0.03, 0.0);
            }
        }

        /** 光らせる・消す（守護者から）。 */
        public static void setLit(ServerLevel level, BlockPos pos, boolean lit) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BindingDais && state.getValue(LIT) != lit) {
                level.setBlock(pos, state.setValue(LIT, lit), 3);
            }
        }
    }
}
