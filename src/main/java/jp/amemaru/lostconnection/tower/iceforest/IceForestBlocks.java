package jp.amemaru.lostconnection.tower.iceforest;

import jp.amemaru.lostconnection.tower.FloorThingBlockEntity;
import jp.amemaru.lostconnection.tower.forest.ForestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * 7階「氷の森林」の光の迷路のブロック（11月25日。Phase 0-A 9章の7階「氷の鏡を動かして光を導く迷路」）。
 * <ul>
 *   <li>{@link Spring}：光の泉。{@code FACING} の向きへ光を出す（光の道は {@link LightMazeService} が粒で見せる）</li>
 *   <li>{@link Mirror}：氷の鏡。右クリックで「／」と「＼」が入れ替わる（{@code SLASH}）</li>
 *   <li>{@link Target}：光の受け石。光が届くと灯る（{@code LIT}）</li>
 * </ul>
 * どれも、どの階のどの迷路か（{@link FloorThingBlockEntity} の階・番号・組）を持つ。
 */
public final class IceForestBlocks {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** 鏡の向きが「／」か（上から見て、南西から北東へ）。でなければ「＼」。 */
    public static final BooleanProperty SLASH = BooleanProperty.create("slash");
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    private IceForestBlocks() {
    }

    /** 光の泉。 */
    public static class Spring extends ForestBlocks.Thing {
        public Spring(Properties properties) {
            super(properties, Block.box(2, 0, 2, 14, 14, 14));
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            LightMazeService.showPath(player, thing.floor(), thing.group());
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            Direction face = state.getValue(FACING);
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + face.getStepX() * 0.45, pos.getY() + 0.6,
                    pos.getZ() + 0.5 + face.getStepZ() * 0.45, face.getStepX() * 0.02, 0.0, face.getStepZ() * 0.02);
        }
    }

    /** 氷の鏡。 */
    public static class Mirror extends ForestBlocks.Thing {
        public Mirror(Properties properties) {
            super(properties, Block.box(1, 0, 1, 15, 16, 15));
            registerDefaultState(stateDefinition.any().setValue(SLASH, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SLASH);
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            LightMazeService.turn(player, thing.floor(), thing.group(), pos);
        }
    }

    /** 光の受け石。 */
    public static class Target extends ForestBlocks.Thing {
        public Target(Properties properties) {
            super(properties, Block.box(0, 0, 0, 16, 16, 16));
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            LightMazeService.showPath(player, thing.floor(), thing.group());
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(LIT) && random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
                        0.0, 0.03, 0.0);
            }
        }
    }
}
