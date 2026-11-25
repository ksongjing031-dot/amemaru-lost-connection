package jp.amemaru.lostconnection.block;

import jp.amemaru.lostconnection.economy.ShopService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 店のカウンターの呼び鈴（Phase 6。11月22日に決定：施設は右クリックでGUIを開いて使う）。店の人の前の、カウンターの天板の上に置く
 * 小さな真鍮の呼び鈴（個人倉庫の窓口の呼び鈴と同じ形）。右クリックで、その店の「買う・売る」の画面が開く。
 *
 * <p>{@code slab=true} は、天板（ダークオークの下付きの半ブロック）ごと置く形（天板の1枚を、呼び鈴の付いた天板に替える）。
 * 11月21日の直しでカウンターを1段低くした店は、天板の上のマスに {@code slab=false} を置く。</p>
 */
public class ShopCounterBlock extends BaseEntityBlock {
    /** 天板（半ブロック）ごと置く形か */
    public static final BooleanProperty SLAB = BooleanProperty.create("slab");
    private static final VoxelShape BELL = Block.box(4.0, 0.0, 4.0, 12.0, 6.0, 12.0);
    private static final VoxelShape ON_SLAB = Shapes.or(Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0),
            Block.box(4.0, 8.0, 4.0, 12.0, 14.0, 12.0));

    public ShopCounterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SLAB, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SLAB);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShopCounterBlockEntity(pos, state);
    }

    @SuppressWarnings("deprecation")
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SLAB) ? ON_SLAB : BELL;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof ShopCounterBlockEntity counter && counter.shop() != null) {
                ShopService.open(serverPlayer, pos, counter.shop());
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("message.amemaru.shop.closed").withStyle(ChatFormatting.GRAY));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
