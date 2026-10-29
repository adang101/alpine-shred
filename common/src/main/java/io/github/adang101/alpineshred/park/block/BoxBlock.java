package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.park.GrindMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A park box (design doc section 11, {@code BoxBlock}): a slideable surface along its long axis,
 * low (8 px) or full height, with bevelled ends where no box continues the run (visual only).
 * Sneaking while placing makes a low box. Honeycomb waxes it, an axe scrapes the wax off.
 */
public class BoxBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    private final GrindMaterial material;

    public BoxBlock(Properties properties, GrindMaterial material) {
        super(properties);
        this.material = material;
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(ParkStates.BOX_HALF, ParkStates.BoxHalf.FULL)
                .setValue(ParkStates.END_A, true).setValue(ParkStates.END_B, true).setValue(ParkStates.WAXED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, ParkStates.BOX_HALF, ParkStates.END_A, ParkStates.END_B, ParkStates.WAXED);
    }

    public GrindMaterial material() {
        return material;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ParkStates.BOX_HALF) == ParkStates.BoxHalf.LOW ? ParkShapes.BOX_LOW : ParkShapes.BOX_FULL;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getHorizontalDirection().getAxis();
        boolean low = context.getPlayer() != null && context.getPlayer().isSecondaryUseActive();
        BlockState state = defaultBlockState().setValue(AXIS, axis)
                .setValue(ParkStates.BOX_HALF, low ? ParkStates.BoxHalf.LOW : ParkStates.BoxHalf.FULL);
        return ends(context.getLevel(), context.getClickedPos(), state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction.getAxis() == state.getValue(AXIS) ? ends(level, pos, state) : state;
    }

    /** {@code end_a} is the negative end of the axis (north or west), {@code end_b} the positive one. */
    static BlockState ends(BlockGetter level, BlockPos pos, BlockState state) {
        Direction.Axis axis = state.getValue(AXIS);
        Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        return state.setValue(ParkStates.END_A, !continues(level.getBlockState(pos.relative(negative)), axis))
                .setValue(ParkStates.END_B, !continues(level.getBlockState(pos.relative(negative.getOpposite())), axis));
    }

    private static boolean continues(BlockState neighbour, Direction.Axis axis) {
        return neighbour.getBlock() instanceof BoxBlock && neighbour.getValue(AXIS) == axis;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        boolean waxed = state.getValue(ParkStates.WAXED);
        if (held.is(Items.HONEYCOMB) && !waxed) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(ParkStates.WAXED, true), Block.UPDATE_ALL_IMMEDIATE);
                level.levelEvent(player, 3003, pos, 0);
                if (!player.getAbilities().instabuild) held.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.getItem() instanceof AxeItem && waxed) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(ParkStates.WAXED, false), Block.UPDATE_ALL_IMMEDIATE);
                level.levelEvent(player, 3004, pos, 0);
                held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
