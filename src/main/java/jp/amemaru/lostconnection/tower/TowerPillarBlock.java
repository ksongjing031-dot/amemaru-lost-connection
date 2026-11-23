package jp.amemaru.lostconnection.tower;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 起動する石柱の「核」（Phase 0-A 9章 1階「草原に散らばる3本の石柱を探して起動すると、ボスへの道が開く。石柱に触れるたびに
 * 古代文字が一行ずつ表示される」）。石柱の胴はふつうのブロックで、この核（翠の結晶をはめた石）を右クリックすると起動する。
 *
 * <p>起動は人ごと（同じパーティのメンバーにも数える）【補完】。光り方は、見ている人が起動したかで変わる（{@code TowerPillarRenderer}）。
 * どの階の何本目かは、ブロックエンティティ（{@link TowerPillarBlockEntity}）が持つ。</p>
 */
public class TowerPillarBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0, 0.0, 1.0, 15.0, 4.0, 15.0),
            Block.box(3.0, 4.0, 3.0, 13.0, 14.0, 13.0),
            Block.box(1.0, 14.0, 1.0, 15.0, 16.0, 15.0));

    public TowerPillarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TowerPillarBlockEntity(pos, state);
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TowerPillarBlockEntity pillar) {
            TowerService.activatePillar(serverPlayer, pillar.floor(), pillar.index(), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 1.2, -0.5, (random.nextDouble() - 0.5) * 1.2);
        }
    }
}
