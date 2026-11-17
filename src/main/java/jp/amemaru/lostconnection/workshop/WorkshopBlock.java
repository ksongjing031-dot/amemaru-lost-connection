package jp.amemaru.lostconnection.workshop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * 工房の作業台（武器工房・防具工房・装飾工房。Phase 0-A 9章）。右クリックで工房の画面が開く：
 * 生産職になる・作る（レシピ）・強化・修理。Phase 5で、工房の担当NPCとの会話からも開けるようにする。
 */
public class WorkshopBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0.0, 12.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(1.0, 0.0, 1.0, 4.0, 12.0, 4.0),
            Block.box(12.0, 0.0, 1.0, 15.0, 12.0, 4.0),
            Block.box(1.0, 0.0, 12.0, 4.0, 12.0, 15.0),
            Block.box(12.0, 0.0, 12.0, 15.0, 12.0, 15.0));

    private final WorkshopType type;

    public WorkshopBlock(WorkshopType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public WorkshopType type() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inventory, opener) -> new WorkshopMenu(id, inventory, type, pos),
                    Component.translatable(type.translationKey())), buf -> {
                buf.writeByte(type.ordinal());
                buf.writeBlockPos(pos);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
