package jp.amemaru.lostconnection.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * ロストの「欠け」（Phase 0-A 3章「ロストの見た目：漂白されたような白と、切れ目の黒。風景や体の一部が、四角く、または不定形に白く
 * 抜け落ちる。縁には砂嵐のようなノイズ」、Minecraftでの表現例「白い半透明ブロックとノイズのパーティクル」）。
 * 1階では、草むらの一部が白く抜け落ちている所に使う（Phase 0-A 9章 1階「ロストの侵食」）。
 */
public class LostBlankBlock extends Block {
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 0.7F);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05F, 0.05F, 0.07F), 0.6F);

    public LostBlankBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0 || !level.getBlockState(pos.above()).isAir()) {
            return;
        }
        level.addParticle(random.nextInt(3) == 0 ? BLACK : WHITE, pos.getX() + random.nextDouble(), pos.getY() + 1.05 + random.nextDouble() * 0.4,
                pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
    }
}
