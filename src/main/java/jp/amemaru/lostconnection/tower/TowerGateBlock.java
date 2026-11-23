package jp.amemaru.lostconnection.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 古代の格子（11月22日【補完】。1階の東の遺跡の奥の中庭の門）。閉じている間は通れない光る格子で、仕掛け石（{@link TowerSwitchBlock}）を
 * そろえて押すと、しばらくひらく（{@link #OPEN}。見えなくなって通れる）。ひらき・閉じは {@link FloorGates} が決める。
 */
public class TowerGateBlock extends Block {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public TowerGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean skipRendering(BlockState state, BlockState neighbor, net.minecraft.core.Direction direction) {
        // となりも閉じた格子なら、間の面を描かない（ガラスと同じ）
        return neighbor.is(this) && neighbor.getValue(OPEN) == state.getValue(OPEN) || super.skipRendering(state, neighbor, direction);
    }
}
