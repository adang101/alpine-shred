package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.park.GrindPath;
import io.github.adang101.alpineshred.park.GrindPhysics;
import io.github.adang101.alpineshred.park.RailPath;
import io.github.adang101.alpineshred.park.V3;
import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The server's world checks for park claims (design doc section 7, validation rule 3): {@code GRIND}
 * needs a grindable block in the capture zone, {@code SLIDE} a slideable block under the feet. Each
 * allows a little more than the client's own capture, for a tick of packet lag.
 */
public final class ParkChecks {
    private static final double SLACK = 0.25;

    private ParkChecks() {
    }

    public static boolean supports(Player player, RideState state) {
        return switch (state) {
            case GRIND -> grindableNear(player);
            case SLIDE -> slideableUnder(player);
            default -> true;
        };
    }

    public static boolean grindableNear(Player player) {
        V3 feet = new V3(player.getX(), player.getY(), player.getZ());
        for (GrindPath p : GrindPaths.around(player.level(), feet)) {
            RailPath.Closest c = p.path().closest(feet);
            if (c.horizontal() <= GrindPhysics.ASSIST_RADIUS + SLACK
                    && c.above() >= -GrindPhysics.BELOW - SLACK && c.above() <= GrindPhysics.ABOVE + SLACK) {
                return true;
            }
        }
        return false;
    }

    public static boolean slideableUnder(Player player) {
        Level level = player.level();
        for (double dy : new double[]{0.01, -0.1, -0.6}) {
            if (level.getBlockState(BlockPos.containing(player.getX(), player.getY() + dy, player.getZ())).is(ModTags.Blocks.SLIDE_BOXES)) {
                return true;
            }
        }
        return false;
    }

    /** A quarter pipe within two blocks of the feet: the pipe ride's rise allowance applies. */
    public static boolean nearPipe(Player player) {
        BlockPos feet = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-2, -2, -2), feet.offset(2, 1, 2))) {
            if (player.level().getBlockState(p).getBlock() instanceof PipeBlock) return true;
        }
        return false;
    }
}
