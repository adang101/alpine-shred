package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.registry.ModBlockEntities;
import io.github.adang101.alpineshred.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code lift_drive_station} and {@code lift_return_station}: the two ends of a line. Placing one
 * with solid blocks in the 5x5x5 around it (the bullwheel and the chairs' swing need room) warns the
 * player in the action bar; the block is placed anyway.
 *
 * <p>The drive station also answers redstone (a neighbour change re-reads the signal) and right-clicks
 * (cycle the speed level, or clear an emergency). With a {@code lift_linker} in either hand its
 * {@code use} returns PASS: vanilla calls the block's {@code use} before the item's {@code useOn}, and
 * the drive must not swallow linker clicks.
 */
public class LiftStationBlock extends LiftNodeBlock {
    /** Blocks out from the station that must be clear: 2 gives the 5x5x5 volume. */
    public static final int CLEARANCE = 2;

    private final boolean drive;

    public LiftStationBlock(Properties properties, boolean drive) {
        super(properties, drive ? ModBlockEntities.LIFT_DRIVE_STATION::get : ModBlockEntities.LIFT_RETURN_STATION::get);
        this.drive = drive;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moved) {
        super.neighborChanged(state, level, pos, block, fromPos, moved);
        if (drive && level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof LiftDriveStationBlockEntity be) {
            be.refresh(serverLevel);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!drive) return InteractionResult.PASS;
        for (InteractionHand h : InteractionHand.values()) {
            if (player.getItemInHand(h).is(ModItems.LIFT_LINKER.get())) return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof LiftDriveStationBlockEntity be) {
            be.use(serverLevel, player);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer player && hasSolidNeighbours(level, pos)) {
            player.displayClientMessage(Component.translatable("message.alpineshred.lift.station_clearance"), true);
        }
    }

    /** Whether any block in the 5x5x5 volume centred on {@code pos}, other than {@code pos}, has a collision shape. */
    public static boolean hasSolidNeighbours(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-CLEARANCE, -CLEARANCE, -CLEARANCE), pos.offset(CLEARANCE, CLEARANCE, CLEARANCE))) {
            if (p.equals(pos)) continue;
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return true;
        }
        return false;
    }
}
