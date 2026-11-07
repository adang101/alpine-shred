package io.github.adang101.alpineshred.compat.create;

import io.github.adang101.alpineshred.lift.LiftDriveStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Who a kinetic drive is attached to: the drive stations on its six sides (README 78). Common code
 * (no Create type), shared by both loaders' block entities.
 */
public final class KineticDriveLinks {
    private static final Direction[] SIDES = Direction.values();

    private KineticDriveLinks() {
    }

    /** Tells every drive station next to {@code drivePos} to re-read its source. Runs on a change only, never per tick. */
    public static void refreshStations(Level level, BlockPos drivePos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (Direction side : SIDES) {
            BlockEntity be = level.getBlockEntity(drivePos.relative(side));
            if (be instanceof LiftDriveStationBlockEntity station) station.refresh(serverLevel);
        }
    }
}
