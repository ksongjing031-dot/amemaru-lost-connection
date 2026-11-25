package jp.amemaru.lostconnection.tower.lake;

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
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 8階「沈黙の湖」のブロック（11月25日。Phase 0-A 9章の8階）【補完】。
 * <ul>
 *   <li>{@link LakeIce}：湖の氷。透きとおっていて、下の水と影が見える。すべらない（ふつうの地面と同じ）。壊せない</li>
 *   <li>{@link ThinIce}：薄氷。2人以上が近くに乗ると、ひびが入り（{@code CRACK} 0〜3）、割れる（{@link ThinIceService}）</li>
 *   <li>{@link Bell}：湖の鐘・鳴り石。右クリックで鳴らす（{@link BellService}）。正しく鳴った鐘は灯る（{@code LIT}）</li>
 *   <li>{@link Stele}：歌の碑。触れると、今日の歌（鐘を鳴らす順番）が浮かぶ</li>
 *   <li>{@link Tablet}：湖底の祠の石板。右クリックで受け取る（4階の騎士団の遺品と同じ「物語の品」）</li>
 * </ul>
 */
public final class LakeBlocks {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** 薄氷のひび（0：ない 〜 3：今にも割れる）。 */
    public static final IntegerProperty CRACK = IntegerProperty.create("crack", 0, 3);

    private LakeBlocks() {
    }

    /** 湖の氷。 */
    public static class LakeIce extends HalfTransparentBlock {
        public LakeIce(Properties properties) {
            super(properties);
        }
    }

    /** 薄氷。 */
    public static class ThinIce extends HalfTransparentBlock {
        public ThinIce(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(CRACK, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(CRACK);
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
            return adjacent.getBlock() instanceof ThinIce || super.skipRendering(state, adjacent, direction);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            int crack = state.getValue(CRACK);
            if (crack >= 2 && random.nextInt(4 - crack + 1) == 0) {
                level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(),
                        0.0, 0.02, 0.0);
            }
        }
    }

    /** 湖の鐘・鳴り石。 */
    public static class Bell extends ForestBlocks.Thing {
        public Bell(Properties properties) {
            super(properties, Block.box(1, 0, 1, 15, 16, 15));
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, LIT);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            BellService.ring(player, thing.floor(), thing.group(), pos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(LIT) && random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.9,
                        pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.03, 0.0);
            }
        }
    }

    /** 歌の碑。 */
    public static class Stele extends ForestBlocks.Thing {
        public Stele(Properties properties) {
            super(properties, Block.box(0, 0, 0, 16, 16, 16));
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

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            BellService.showSong(player, thing.floor(), thing.group());
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(5) == 0) {
                level.addParticle(ParticleTypes.ENCHANT, pos.getX() + random.nextDouble(), pos.getY() + 1.2, pos.getZ() + random.nextDouble(),
                        0.0, -0.2, 0.0);
            }
        }
    }

    /** 湖底の祠の石板（物語の品）。 */
    public static class Tablet extends ForestBlocks.Thing {
        public Tablet(Properties properties) {
            super(properties, Block.box(1, 0, 3, 15, 14, 13));
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

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            jp.amemaru.lostconnection.tower.temple.TempleService.takeRelic(player, thing.floor(), thing.group(), pos);
        }
    }
}
