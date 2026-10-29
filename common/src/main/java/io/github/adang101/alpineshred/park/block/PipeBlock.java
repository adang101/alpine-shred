package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.movement.RideStateHolder;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.park.Facing;
import io.github.adang101.alpineshred.park.PipeArc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code quarter_pipe} (design doc section 11, "Pipe pieces"): one block of a quarter pipe of radius
 * 3 or 5, its {@code index} counting the blocks along the arc from the transition. The item places a
 * whole pipe (sneak for radius 5) and breaking any block removes the pipe.
 *
 * <p>Riders do not collide with the pipe's 1-pixel columns: the slide controller rides the arc
 * itself, analytically, and the server's replay of that ride must not snag on the columns
 * (vanilla resolves vertical movement first, so a rider coming down the arc would land on the
 * column under where it was). Everyone else walks on the columns.
 */
public class PipeBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public PipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(ParkStates.PIPE_RADIUS, ParkStates.PipeRadius.R3).setValue(ParkStates.PIPE_INDEX, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ParkStates.PIPE_RADIUS, ParkStates.PIPE_INDEX);
    }

    public static int radius(BlockState state) {
        return state.getValue(ParkStates.PIPE_RADIUS).radius;
    }

    public static int index(BlockState state) {
        return Math.min(state.getValue(ParkStates.PIPE_INDEX), PipeArc.cellCount(radius(state)) - 1);
    }

    public static Facing facing(BlockState state) {
        return ParkStates.facing(state.getValue(FACING));
    }

    /** The block of the pipe at the transition (index 0), from any block of it. */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        PipeArc.Cell c = PipeArc.cell(radius(state), index(state));
        Direction f = state.getValue(FACING);
        return pos.relative(f, -c.i()).below(c.j());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ParkShapes.pipe(radius(state), index(state), facing(state));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ec && ridesThrough(ec.getEntity())) return Shapes.empty();
        return getShape(state, level, pos, context);
    }

    /**
     * Riders the controller moves ride the arc, not the columns: where the controller runs (the
     * rider's own client, a game test) its flag says so at once; elsewhere (the server, other
     * clients) the synced state does, for any ridden state but standing still.
     */
    public static boolean ridesThrough(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        if (((RideStateHolder) player).alpineshred$isControlled()) return true;
        var state = Riders.state(player);
        return state.isControllerDriven() && state != io.github.adang101.alpineshred.movement.RideState.IDLE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /** Every block position of the pipe this block belongs to. */
    public static java.util.List<BlockPos> blocks(BlockState state, BlockPos pos) {
        BlockPos origin = origin(state, pos);
        Direction f = state.getValue(FACING);
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (PipeArc.Cell c : PipeArc.cells(radius(state))) out.add(origin.relative(f, c.i()).above(c.j()));
        return out;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(level, pos, state, player);
        if (level.isClientSide) return;
        for (BlockPos p : blocks(state, pos)) {
            if (p.equals(pos)) continue;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() == this && origin(s, p).equals(origin(state, pos))) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (!player.getAbilities().instabuild) Block.popResource(level, pos, new ItemStack(asItem()));
    }
}
