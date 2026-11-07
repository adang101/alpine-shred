package io.github.adang101.alpineshred.forge.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.adang101.alpineshred.compat.create.KineticDriveLinks;
import io.github.adang101.alpineshred.compat.create.KineticDriveMath;
import io.github.adang101.alpineshred.compat.create.KineticDriveWatch;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.lift.LiftDriveSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The kinetic lift drive (phase 9 plan, decision 2; README 78): Create's rotation as a {@link LiftDriveSource}.
 * The station next to it asks {@link #enabled()} and {@link #targetSpeed()} when it refreshes; this block entity
 * tells the station to refresh when the command changes, and the line then ramps and stops through
 * {@code LiftMotion} like any other drive (losing rotation or overstress is an emergency stop, as losing redstone is).
 *
 * <p>Create's {@code getSpeed()} already reads 0 while the network is overstressed, so one reading covers both.
 * The check also runs every tick, because Create zeroes an unsourced block's speed without calling
 * {@code onSpeedChanged}; the tick's own work is two float reads and a compare, with no allocation.
 */
public class KineticLiftDriveBE extends KineticBlockEntity implements LiftDriveSource {
    private final KineticDriveWatch watch = new KineticDriveWatch();

    public KineticLiftDriveBE(BlockPos pos, BlockState state) {
        super(ForgeKineticDrive.BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public boolean enabled() {
        return KineticDriveMath.running(getSpeed());
    }

    @Override
    public double targetSpeed() {
        return KineticDriveMath.targetSpeed(getSpeed(), AlpineConfig.LIFT_KINETIC_MAX_SPEED.value());
    }

    /** The flat impact per rpm; Create multiplies it by the speed (README 78). */
    @Override
    public float calculateStressApplied() {
        float impact = (float) AlpineConfig.LIFT_KINETIC_STRESS.value();
        this.lastStressApplied = impact;
        return impact;
    }

    @Override
    public void tick() {
        super.tick();
        syncStation();
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        syncStation();
    }

    /** A drive read from disk takes its saved rotation as the baseline, so a reload is never a change. */
    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        if (!clientPacket) watch.reset(enabled(), targetSpeed());
    }

    private void syncStation() {
        Level current = level;
        if (current == null || current.isClientSide) return;
        if (watch.update(enabled(), targetSpeed())) KineticDriveLinks.refreshStations(current, worldPosition);
    }
}
