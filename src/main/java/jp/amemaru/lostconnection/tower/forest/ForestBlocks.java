package jp.amemaru.lostconnection.tower.forest;

import jp.amemaru.lostconnection.tower.FloorThingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 2階「忘れられた森」の仕掛けのブロック（Phase 8 その1）。どれも、どの階の何番目かを {@link FloorThingBlockEntity} が持つ
 * （迷いの森の区画の名前は group）。壊せない（強さ −1）。
 *
 * <ul>
 *   <li>{@link Bell}：迷いの森の鐘（風鈴のような古代の装置）。鳴らすと、少しおいて正しい出口からこだまが返る</li>
 *   <li>{@link KnotPost}：結び目の灯台（出口ごとに1つ）。結び目の灯を置くと灯る（{@link #LIT}）</li>
 *   <li>{@link Waypoint}：中継の灯。触れた人は、次から入口の道しるべの石からここへ来られる</li>
 *   <li>{@link TravelStone}：入口の道しるべの石。いちばん先の、ともした中継の灯へ連れていく</li>
 * </ul>
 */
public final class ForestBlocks {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private ForestBlocks() {
    }

    /** 仕掛けのブロックの土台（右クリックで {@link #act} を呼ぶ）。 */
    public abstract static class Thing extends BaseEntityBlock {
        private final VoxelShape shape;

        protected Thing(Properties properties, VoxelShape shape) {
            super(properties);
            this.shape = shape;
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @SuppressWarnings("deprecation")
        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shape;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new FloorThingBlockEntity(pos, state);
        }

        @SuppressWarnings("deprecation")
        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                    && level.getBlockEntity(pos) instanceof FloorThingBlockEntity thing) {
                act(serverPlayer, thing, pos, state);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        protected abstract void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state);
    }

    /** 迷いの森の鐘。 */
    public static class Bell extends Thing {
        public Bell(Properties properties) {
            super(properties, Block.box(3, 0, 3, 13, 16, 13));
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            LostWoodsService.ringBell(player, thing.floor(), thing.group(), pos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, random.nextDouble(), 0.0, 0.0);
            }
        }
    }

    /** 結び目の灯台。 */
    public static class KnotPost extends Thing {
        public KnotPost(Properties properties) {
            super(properties, Block.box(5, 0, 5, 11, 16, 11));
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            LostWoodsService.placeKnot(player, thing.floor(), thing.group(), thing.index(), pos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(LIT) && random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.9,
                        pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.02, 0.0);
            }
        }
    }

    /** 中継の灯。 */
    public static class Waypoint extends Thing {
        public Waypoint(Properties properties) {
            super(properties, Block.box(2, 0, 2, 14, 16, 14));
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            WaypointService.light(player, thing.floor(), thing.index(), pos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.GLOW, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.8,
                        pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.03, 0.0);
            }
        }
    }

    /** 入口の道しるべの石。 */
    public static class TravelStone extends Thing {
        public TravelStone(Properties properties) {
            super(properties, Block.box(3, 0, 3, 13, 14, 13));
        }

        @Override
        protected void act(ServerPlayer player, FloorThingBlockEntity thing, BlockPos pos, BlockState state) {
            WaypointService.travel(player, thing.floor(), pos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                        (random.nextDouble() - 0.5) * 0.6, 0.2, (random.nextDouble() - 0.5) * 0.6);
            }
        }
    }
}
