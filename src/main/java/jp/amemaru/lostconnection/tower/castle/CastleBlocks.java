package jp.amemaru.lostconnection.tower.castle;

import jp.amemaru.lostconnection.tower.FloorThingBlockEntity;
import jp.amemaru.lostconnection.tower.forest.ForestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 9階「氷結した王城」のブロック（11月28日。Phase 0-A 9章の9階）【補完】。どれも壊せない。
 * <ul>
 *   <li>{@link Portrait}：王家の肖像画（顔は空白）。ブロックは壁ぎわの名札の台座で、絵はその上の壁に3×4で描く
 *       （{@code client.render.PortraitRenderer}）。名前の札を持って右クリックではめる（{@link LineageService}）。戻ると
 *       {@code RESTORED}（顔と名前が描かれる）。女王の肖像（組 {@code queen}）は札がない</li>
 *   <li>{@link PlateStand}：名前の札の台。右クリックで、その札をもらう</li>
 *   <li>{@link Clue}：建国の碑・系譜の書。触れると、王家の人の名前と、肖像画に描かれた物が分かる</li>
 *   <li>{@link Letter}：女王の書簡（物語の品。4階の騎士団の遺品と同じ）</li>
 *   <li>{@link Latch}：女王の書斎の内がわの留め金（隠し扉を内から開ける）</li>
 * </ul>
 */
public final class CastleBlocks {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty RESTORED = BooleanProperty.create("restored");

    private CastleBlocks() {
    }

    /** 壁ぎわの薄い板（facing の反対がわが壁）。 */
    static VoxelShape plate(Direction facing, double low, double high, double depth) {
        return switch (facing) {
            case EAST -> Block.box(0, low, 2, depth, high, 14);
            case WEST -> Block.box(16 - depth, low, 2, 16, high, 14);
            case SOUTH -> Block.box(2, low, 0, 14, high, depth);
            default -> Block.box(2, low, 16 - depth, 14, high, 16);
        };
    }

    /** 向きを持つ、王城の仕掛け。 */
    abstract static class Facing extends ForestBlocks.Thing {
        Facing(Properties properties, VoxelShape shape) {
            super(properties, shape);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }
    }

    /** 王家の肖像画（の名札の台座）。 */
    public static class Portrait extends ForestBlocks.Thing {
        public Portrait(Properties properties) {
            super(properties, Block.box(0, 0, 0, 16, 16, 16));
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(RESTORED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, RESTORED);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @SuppressWarnings("deprecation")
        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return plate(state.getValue(FACING), 2, 12, 4);
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            LineageService.usePortrait(player, thing.floor(), thing.group(), pos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(RESTORED) && random.nextInt(4) == 0) {
                Direction facing = state.getValue(FACING);
                double x = pos.getX() + 0.5 + facing.getStepX() * 0.1 + (random.nextDouble() - 0.5) * 2.4 * Math.abs(facing.getStepZ());
                double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.1 + (random.nextDouble() - 0.5) * 2.4 * Math.abs(facing.getStepX());
                level.addParticle(ParticleTypes.END_ROD, x, pos.getY() + 1.2 + random.nextDouble() * 3.4, z, 0.0, 0.01, 0.0);
            }
        }
    }

    /** 名前の札の台。 */
    public static class PlateStand extends Facing {
        public PlateStand(Properties properties) {
            super(properties, Block.box(3, 0, 3, 13, 10, 13));
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            LineageService.takePlate(player, thing.floor(), thing.group(), pos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(5) == 0) {
                level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.8,
                        pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.01, 0.0);
            }
        }
    }

    /** 建国の碑・系譜の書。 */
    public static class Clue extends Facing {
        public Clue(Properties properties, VoxelShape shape) {
            super(properties, shape);
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            LineageService.showClue(player, thing.floor(), thing.group(), pos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.ENCHANT, pos.getX() + random.nextDouble(), pos.getY() + 1.3, pos.getZ() + random.nextDouble(),
                        0.0, -0.2, 0.0);
            }
        }
    }

    /** 女王の書簡（物語の品）。 */
    public static class Letter extends Facing {
        public Letter(Properties properties) {
            super(properties, Block.box(3, 0, 4, 13, 2, 12));
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            jp.amemaru.lostconnection.tower.temple.TempleService.takeRelic(player, thing.floor(), thing.group(), pos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 0.0, 0.02, 0.0);
            }
        }
    }

    /** 女王の書斎の、内がわの留め金。 */
    public static class Latch extends Facing {
        public Latch(Properties properties) {
            super(properties, Block.box(4, 4, 4, 12, 12, 12));
        }

        @SuppressWarnings("deprecation")
        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return plate(state.getValue(FACING), 3, 13, 3);
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            LineageService.useLatch(player, thing.floor(), pos);
        }
    }
}
