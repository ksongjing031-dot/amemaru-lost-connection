package jp.amemaru.lostconnection.block;

import jp.amemaru.lostconnection.job.JobService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 誓約の結晶（冒険者ギルドの職業登録の台）。
 *
 * <p>職業登録は「大結晶への誓約」（Phase 0-A 7章・13章）。大結晶から分けた小さな結晶に手を当てて誓うと、
 * 結晶がその人の糸の形に合わせて「接続技能」を授ける【補完】。右クリックで職業選択の画面が開く。
 * Phase 5でギルドの受付（リリア）と会話からも同じ画面を開けるようにする。</p>
 */
public class OathCrystalBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0),
            Block.box(4.0, 4.0, 4.0, 12.0, 7.0, 12.0),
            Block.box(5.0, 7.0, 5.0, 11.0, 16.0, 11.0));

    public OathCrystalBlock(Properties properties) {
        super(properties);
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
            JobService.openJobScreen(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
            double y = pos.getY() + 0.6 + random.nextDouble() * 0.5;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.02, 0.0);
        }
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 1.5, -0.6, (random.nextDouble() - 0.5) * 1.5);
        }
    }
}
