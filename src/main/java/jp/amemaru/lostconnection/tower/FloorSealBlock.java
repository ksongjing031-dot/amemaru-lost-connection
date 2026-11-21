package jp.amemaru.lostconnection.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * ボスの部屋の入口の封印（光の幕）。
 *
 * <ul>
 *   <li>その階の石柱をすべて起動した人は通れる（ほかの人には壁）。「ボスへの道が開く」を人ごとにした【補完】</li>
 *   <li>戦いが始まると閉じて、だれも通れない（{@link #SEALED}。戦いが終わると開く）</li>
 *   <li>魔物は通れない</li>
 * </ul>
 * クライアントでも同じに決められるように、本人の起動した石柱をクライアントへ送っている（{@code SyncStoryS2C}）。
 * 見た目は {@code FloorSealRenderer}（通れる人には薄い金、通れない人には白、閉じているときは赤）。
 */
public class FloorSealBlock extends BaseEntityBlock {
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    /** クライアントで、本人が通れるか（{@code ClientStory} が入れる。サーバーでは使わない）。 */
    @Nullable
    public static volatile BiPredicate<Player, Integer> clientPass;

    public FloorSealBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SEALED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEALED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloorSealBlockEntity(pos, state);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(SEALED)) {
            return Shapes.block();
        }
        Entity entity = entityOf(context);
        if (entity instanceof Player player && canPass(player, floorAt(level, pos))) {
            return Shapes.empty();
        }
        return Shapes.block();
    }

    /** 当たりの判定の相手（1.20.1 では null か Optional のどちらでも読めるようにする）。 */
    @Nullable
    private static Entity entityOf(CollisionContext context) {
        if (!(context instanceof EntityCollisionContext ecc)) {
            return null;
        }
        Object found = ecc.getEntity();
        if (found instanceof Optional<?> optional) {
            found = optional.orElse(null);
        }
        return found instanceof Entity entity ? entity : null;
    }

    private static int floorAt(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof FloorSealBlockEntity seal ? seal.floor() : 1;
    }

    /** その人が、その階の封印を通れるか。 */
    public static boolean canPass(Player player, int floor) {
        if (player.isSpectator() || player.isCreative()) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return TowerService.pillarsComplete(serverPlayer, floor);
        }
        BiPredicate<Player, Integer> pass = clientPass;
        return pass != null && pass.test(player, floor);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            level.addParticle(state.getValue(SEALED) ? ParticleTypes.FLAME : ParticleTypes.END_ROD,
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
        }
    }
}
