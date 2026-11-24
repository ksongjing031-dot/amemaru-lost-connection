package jp.amemaru.lostconnection.enemy.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * ボスの技で少しの間だけ置くブロック（11月25日：3階のボスの「青銅の堤」）。置いた所の前のブロックを覚え、{@link #restore} で戻す。
 * 空気と水の所にだけ置く（部屋の床・壁・仕掛けは変えない）。保存しない（区画が閉じる前に、ボスが戻す。戻しそこねても、
 * 階を建て直せば元どおり）。
 */
public final class TempBlocks {
    private record Placed(BlockPos pos, BlockState before) {
    }

    private final List<Placed> placed = new ArrayList<>();

    /** 置く（空気か水の所だけ）。置けたら true。 */
    public boolean place(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState before = level.getBlockState(pos);
        if (!before.isAir() && !before.is(net.minecraft.world.level.block.Blocks.WATER)) {
            return false;
        }
        placed.add(new Placed(pos.immutable(), before));
        level.setBlock(pos, state, 3);
        return true;
    }

    /** 置いたブロックを、前のブロックに戻す（崩れる粒を出す）。 */
    public void restore(ServerLevel level) {
        for (int i = placed.size() - 1; i >= 0; i--) {
            Placed p = placed.get(i);
            if (level.isLoaded(p.pos())) {
                BlockState now = level.getBlockState(p.pos());
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, now), p.pos().getX() + 0.5, p.pos().getY() + 0.5,
                        p.pos().getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
                level.setBlock(p.pos(), p.before(), 3);
            }
        }
        placed.clear();
    }

    public boolean isEmpty() {
        return placed.isEmpty();
    }

    public int size() {
        return placed.size();
    }
}
