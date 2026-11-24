package jp.amemaru.lostconnection.tower.temple;

import jp.amemaru.lostconnection.tower.FloorThingBlockEntity;
import jp.amemaru.lostconnection.tower.forest.ForestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 4階「崩壊した神殿」の仕掛けのブロック（11月24日。Phase 0-A 9章の4階のギミック「壁画パズル」と「音の順番」）。
 *
 * <ul>
 *   <li>{@link MuralPanel}：壁画のパネル（組の名前は {@link FloorThingBlockEntity#group}、組の中の番号は {@code index}）。
 *       右クリックで90度回る（{@link TempleService#turnPanel}）。{@link #PIECE} は絵のどの部分か、{@link #TURN} は今の向き。
 *       「一枚の絵」のパネルは回らず、すべての壁画がそろうと {@link #LIT}（絵が出る。それまでは白く消えている）</li>
 *   <li>{@link SoundPlate}：音の床（{@link #NOTE} が音。0 白・1 青・2 金・3 紅）。踏むと鳴る（{@link TempleService#step}）。
 *       正しく踏まれた間は {@link #LIT}</li>
 *   <li>{@link ChimeStone}：音の順番を鳴らす石（右クリック）</li>
 *   <li>{@link KnightRelic}：結晶騎士団の遺品（冑と認識票。右クリックで受け取る）</li>
 * </ul>
 */
public final class TempleBlocks {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty TURN = IntegerProperty.create("turn", 0, 3);
    public static final IntegerProperty PIECE = IntegerProperty.create("piece", 0, 15);
    public static final IntegerProperty NOTE = IntegerProperty.create("note", 0, 3);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private TempleBlocks() {
    }

    /** 壁画のパネル（四角いブロック。表の面に絵）。 */
    public static class MuralPanel extends ForestBlocks.Thing {
        public MuralPanel(Properties properties) {
            super(properties, Block.box(0, 0, 0, 16, 16, 16));
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TURN, 0).setValue(PIECE, 0)
                    .setValue(LIT, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, TURN, PIECE, LIT);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            TempleService.turnPanel(player, thing.floor(), thing.group(), thing.index(), pos);
        }
    }

    /** 音の床（床から2ピクセルの、平たい石の板）。 */
    public static class SoundPlate extends Block {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

        public SoundPlate(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(NOTE, 0).setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(NOTE, LIT);
        }

        @SuppressWarnings("deprecation")
        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        /*
         * 11月24日の直し：前は stepOn で受けていたが、stepOn は「足もとから0.2下のブロック」にしか呼ばれない（Entity#getOnPosLegacy）。
         * この床は高さ2ピクセルなので、乗っても下の石の stepOn が呼ばれ、音の床は一度も反応しなかった（祈りの間の音の順番も、
         * カンターレの選律の歌も）。感圧板と同じく、体がこのマスに重なって動くたびに呼ばれる entityInside で受ける。体の幅で隣の床にも
         * 重なるので、足の真ん中がこの床のマスにあって、足が床の上（半ブロックまで）にあるときだけ数える（同じ床に乗っている間は1回。
         * TempleService#step）。pos は動く入れ物なので、固定してから渡す。
         */
        @SuppressWarnings("deprecation")
        @Override
        public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            if (!level.isClientSide() && entity instanceof ServerPlayer player && TempleService.standingOn(player, pos)) {
                TempleService.step(player, pos.immutable(), state.getValue(NOTE));
            }
            super.entityInside(state, level, pos, entity);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(LIT) && random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.NOTE, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.4,
                        pos.getZ() + 0.2 + random.nextDouble() * 0.6, state.getValue(NOTE) / 4.0, 0.0, 0.0);
            }
        }
    }

    /** 音の順番を鳴らす石（白い石の台に、歌声の結晶の鈴）。 */
    public static class ChimeStone extends ForestBlocks.Thing {
        public ChimeStone(Properties properties) {
            super(properties, Block.box(3, 0, 3, 13, 15, 13));
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            TempleService.ringChime(player, thing.floor(), thing.group(), pos);
        }
    }

    /** 結晶騎士団の遺品（折れた槍の台に、冑と認識票）。 */
    public static class KnightRelic extends ForestBlocks.Thing {
        public KnightRelic(Properties properties) {
            super(properties, Block.box(2, 0, 2, 14, 15, 14));
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
            TempleService.takeRelic(player, thing.floor(), thing.group(), pos);
        }
    }
}
