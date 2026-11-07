package io.github.adang101.alpineshred.compat.create;

import io.github.adang101.alpineshred.lift.LiftDriveSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * What common code may know about Create (phase 9 plan, decision 1): no Create type appears here, so the mod
 * loads without Create. Each loader implements it in its own {@code compat.create} package, and that class is
 * loaded only behind {@code PlatformHooks.isCreatePresent()}; the kinetic drive and wrench support grow this
 * interface in later work packages.
 */
public interface CreateBridge {
    /** True when Create's own registry content (its blocks) is present: proves Create loaded, not just that its jar is listed. */
    boolean live();

    /** The installed Create version string, for logs and the load test. */
    String version();

    /**
     * The kinetic lift drive next to the drive station at {@code stationPos}, as a {@link LiftDriveSource}, or null
     * when there is none (phase 9 plan, decision 2). The station asks on every refresh, not every tick.
     */
    @Nullable
    default LiftDriveSource kineticDriveNear(Level level, BlockPos stationPos) {
        return null;
    }

    /** Offers this bridge's own items (the kinetic drive) to the creative tab. */
    default void addCreativeTabItems(Consumer<ItemLike> out) {
    }

    // ---- fixtures for the Create game-test column only (KineticDriveGameTests); nothing else calls them ----

    /** The kinetic drive's block state with its shafts along {@code axis}. */
    default BlockState testKineticDriveState(Direction.Axis axis) {
        throw new UnsupportedOperationException("no Create");
    }

    /** Places Create's creative motor at {@code pos}, its shaft toward {@code facing}, turning at {@code rpm}. */
    default void testPlaceMotor(ServerLevel level, BlockPos pos, Direction facing, int rpm) {
        throw new UnsupportedOperationException("no Create");
    }

    /** Forces or clears Create's overstress on the kinetic block entity at {@code pos}, as its network would. */
    default void testSetOverstressed(ServerLevel level, BlockPos pos, boolean overstressed) {
        throw new UnsupportedOperationException("no Create");
    }
}
