package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.park.Facing;
import io.github.adang101.alpineshred.park.RampSlope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * {@code park_ramp} (design doc section 11, "Ramps and kickers"): rises toward its facing at a
 * quarter, half or full slope; {@code step} is its segment in a longer run, so ramps placed one
 * after another climb smoothly. Launching is the generic ramp-launch rule (section 7); a ramp the
 * wrench marks as a {@code lip} also gives the lip assist on a pop.
 */
public class RampBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public RampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(ParkStates.RAMP_SLOPE, ParkStates.RampSlopeProp.HALF).setValue(ParkStates.RAMP_STEP, 0)
                .setValue(ParkStates.LIP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ParkStates.RAMP_SLOPE, ParkStates.RAMP_STEP, ParkStates.LIP);
    }

    public static RampSlope slope(BlockState state) {
        return state.getValue(ParkStates.RAMP_SLOPE).slope;
    }

    public static int step(BlockState state) {
        return Math.min(state.getValue(ParkStates.RAMP_STEP), slope(state).maxStep());
    }

    public static Facing facing(BlockState state) {
        return ParkStates.facing(state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ParkShapes.ramp(slope(state), step(state), facing(state));
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /**
     * Facing where the player looks. Placed in line after a ramp of the same facing and slope that
     * has not topped out, it continues that run one segment higher.
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        BlockState behind = context.getLevel().getBlockState(context.getClickedPos().relative(facing.getOpposite()));
        if (behind.getBlock() instanceof RampBlock && behind.getValue(FACING) == facing) {
            RampSlope s = slope(behind);
            int step = step(behind);
            state = state.setValue(ParkStates.RAMP_SLOPE, behind.getValue(ParkStates.RAMP_SLOPE));
            if (step < s.maxStep()) state = state.setValue(ParkStates.RAMP_STEP, step + 1);
        }
        return state;
    }

    /** The wrench's slope cycle: quarter, half, full, keeping the step valid. */
    public static BlockState nextSlope(BlockState state) {
        ParkStates.RampSlopeProp next = state.getValue(ParkStates.RAMP_SLOPE).next();
        return state.setValue(ParkStates.RAMP_SLOPE, next).setValue(ParkStates.RAMP_STEP, Math.min(state.getValue(ParkStates.RAMP_STEP), next.slope.maxStep()));
    }
}
