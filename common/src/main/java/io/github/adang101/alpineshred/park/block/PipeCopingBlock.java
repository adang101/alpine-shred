package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.park.Facing;
import io.github.adang101.alpineshred.park.GrindMaterial;
import io.github.adang101.alpineshred.park.GrindPath;
import io.github.adang101.alpineshred.park.RailPath;
import io.github.adang101.alpineshred.park.V3;
import io.github.adang101.alpineshred.movement.TuningData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * {@code pipe_coping} (design doc section 11): the lip block at the top of a pipe wall, a full deck
 * block with a metal edge. It is grindable along that edge: facing the way the pipe's wall rises,
 * so the edge is on the side opposite the facing.
 */
public class PipeCopingBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The grind path's inset from the pipe-side face. */
    private static final double EDGE_INSET = 1.0 / 16;

    public PipeCopingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    /** Facing north: the edge runs east-west along the south side, on top. */
    public static GrindPath grindPath(BlockState state, BlockPos pos) {
        Facing f = ParkStates.facing(state.getValue(FACING));
        V3 a = f.rotateLocal(new V3(0.0, 1.0, 1.0 - EDGE_INSET)).add(pos.getX(), pos.getY(), pos.getZ());
        V3 b = f.rotateLocal(new V3(1.0, 1.0, 1.0 - EDGE_INSET)).add(pos.getX(), pos.getY(), pos.getZ());
        return new GrindPath(pos.asLong(), RailPath.straight(a, b), GrindMaterial.METAL, TuningData.tuning().railFriction);
    }
}
