package jp.amemaru.lostconnection.tower.village;

import jp.amemaru.lostconnection.tower.FloorThingBlockEntity;
import jp.amemaru.lostconnection.tower.forest.ForestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 6階「凍結した村」のブロック（11月25日。Phase 0-A 9章の6階「記憶の解凍。熱の結晶を運んで氷像を溶かすと、その日の村の短い
 * 場面が見える。運ぶ人は足が遅くなるので、ほかの人が護衛する」）。
 *
 * <ul>
 *   <li>{@link Hearth} 熱の炉（宿屋の暖炉・パン屋のかまど・鍛冶屋の炉）：右クリックで熱の結晶をもらう（{@link VillageService#takeHeat}）</li>
 *   <li>{@link Statue} 凍った村人の氷像：見た目は {@link VillageStatueBlockEntity} が持ち、{@link VillageStatueRenderer} が描く。
 *       記憶の氷像は、熱の結晶を持って右クリックすると溶け始める。溶けると {@code thawed}（見えない・通れる）。朝にまた凍る</li>
 * </ul>
 */
public final class VillageBlocks {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty THAWED = BooleanProperty.create("thawed");

    private VillageBlocks() {
    }

    /** 熱の炉。 */
    public static class Hearth extends ForestBlocks.Thing {
        public Hearth(Properties properties) {
            super(properties, Block.box(0, 0, 0, 16, 16, 16));
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
            VillageService.takeHeat(player, thing.floor(), thing.group(), pos);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            Direction face = state.getValue(FACING);
            double x = pos.getX() + 0.5 + face.getStepX() * 0.52;
            double z = pos.getZ() + 0.5 + face.getStepZ() * 0.52;
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.3 + random.nextDouble() * 0.3,
                        z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.01, 0.0);
            }
            if (random.nextInt(5) == 0) {
                level.addParticle(ParticleTypes.SMALL_FLAME, x, pos.getY() + 0.6, z, 0.0, 0.02, 0.0);
            }
        }
    }

    /** 凍った村人の氷像（人の形はブロックの実体が描く。ブロックそのものは見えない）。 */
    public static class Statue extends BaseEntityBlock {
        private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 24, 12);
        private static final VoxelShape OUTLINE = Block.box(3, 0, 3, 13, 16, 13);

        public Statue(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(THAWED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(THAWED);
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.ENTITYBLOCK_ANIMATED;
        }

        @SuppressWarnings("deprecation")
        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(THAWED) ? Shapes.empty() : OUTLINE;
        }

        @SuppressWarnings("deprecation")
        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(THAWED) ? Shapes.empty() : SHAPE;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new VillageStatueBlockEntity(pos, state);
        }

        @SuppressWarnings("deprecation")
        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND || state.getValue(THAWED)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                    && level.getBlockEntity(pos) instanceof VillageStatueBlockEntity statue) {
                VillageService.touchStatue(serverPlayer, pos, statue);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(THAWED) || !(level.getBlockEntity(pos) instanceof VillageStatueBlockEntity statue)) {
                return;
            }
            // 記憶の氷像：まわりに、淡い光の粒（溶かせる像の目じるし）
            if (statue.isMemory() && random.nextInt(3) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + Math.cos(a) * 0.8, pos.getY() + 0.2 + random.nextDouble() * 1.8,
                        pos.getZ() + 0.5 + Math.sin(a) * 0.8, 0.0, 0.01, 0.0);
            } else if (random.nextInt(14) == 0) {
                level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 1.8,
                        pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0.0, -0.01, 0.0);
            }
        }

        /** 溶けた・凍った（サービスから）。 */
        public static void setThawed(ServerLevel level, BlockPos pos, boolean thawed) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof Statue && state.getValue(THAWED) != thawed) {
                level.setBlock(pos, state.setValue(THAWED, thawed), 3);
            }
        }
    }
}
