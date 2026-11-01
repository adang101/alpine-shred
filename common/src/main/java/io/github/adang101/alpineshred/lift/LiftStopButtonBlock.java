package io.github.adang101.alpineshred.lift;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code lift_stop_button} (design doc section 13): right-click emergency-stops the line whose drive or
 * return station is nearest within {@link #RANGE} blocks (phase 8 plan, decision 3, "beside a station").
 * With no station in range it says so in the action bar and does nothing else. The button has no state
 * and gives no redstone output.
 */
public class LiftStopButtonBlock extends LiftFacingBlock {
    public static final double RANGE = 8;

    public LiftStopButtonBlock(Properties properties, double... box) {
        super(properties, box);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        LiftLine line = nearestLine(serverLevel, pos);
        if (line == null) {
            player.displayClientMessage(Component.translatable("message.alpineshred.lift.stop_button.no_station"), true);
            return InteractionResult.CONSUME;
        }
        // TODO(WP8): play lift.e_stop here; a vanilla button click stands in until the sound exists.
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 0.6f);
        LiftManager.emergencyStop(serverLevel, line.id);
        player.displayClientMessage(Component.translatable("message.alpineshred.lift.stop_button.stopped"), true);
        return InteractionResult.CONSUME;
    }

    /**
     * The line with a drive or return station closest to {@code pos}, no farther than {@link #RANGE}
     * blocks, or null. Ties go to the line that comes first in the manager's order.
     */
    public static LiftLine nearestLine(ServerLevel level, BlockPos pos) {
        LiftLine best = null;
        double bestSqr = RANGE * RANGE;
        for (LiftLine line : LiftManager.lines(level)) {
            double d = Math.min(line.drive.distSqr(pos), line.returnStation.distSqr(pos));
            if (d <= bestSqr && (best == null || d < bestSqr)) {
                best = line;
                bestSqr = d;
            }
        }
        return best;
    }
}
