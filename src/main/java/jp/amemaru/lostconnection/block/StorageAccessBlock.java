package jp.amemaru.lostconnection.block;

import jp.amemaru.lostconnection.economy.StorageService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 個人倉庫の窓口（Phase 6。11月22日に決定：施設は右クリックでGUIを開いて使う）。
 *
 * <ul>
 *   <li>{@code box}：地下の小部屋の「呼び出しの封印箱」。中身を出し入れする（契約していなければ契約の案内）</li>
 *   <li>{@code contract}：1階のカウンターの「契約・拡張」の窓口（天板の上の呼び鈴）</li>
 *   <li>{@code receive}：1階のカウンターの「受け取り口」（天板の上の包み。市場で買った品などが届く受け取り箱）</li>
 * </ul>
 */
public class StorageAccessBlock extends HorizontalDirectionalBlock {
    /** 窓口の種類 */
    public enum Mode implements StringRepresentable {
        BOX("box", StorageService.Access.BOX),
        CONTRACT("contract", StorageService.Access.CONTRACT),
        RECEIVE("receive", StorageService.Access.RECEIVE);

        private final String name;
        private final StorageService.Access access;

        Mode(String name, StorageService.Access access) {
            this.name = name;
            this.access = access;
        }

        public StorageService.Access access() {
            return access;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);

    /** カウンターの窓口：天板の上に置いた呼び鈴・包み（窓口の口はふさがない） */
    private static final VoxelShape COUNTER = Block.box(4.0, 0.0, 4.0, 12.0, 6.0, 12.0);

    public StorageAccessBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MODE, Mode.BOX));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(MODE) == Mode.BOX ? Shapes.block() : COUNTER;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            StorageService.open(serverPlayer, pos, state.getValue(MODE).access());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
