package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.compat.create.CreateCompat;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

/**
 * The standalone drive station: its line and the speed level it is set to (an index into
 * {@code lift.speedLevels}; 1 is "normal"). As a {@link LiftDriveSource} it is enabled by a redstone
 * signal, or always when {@code lift.driveNeedsRedstone} is off. A Create kinetic drive next to it replaces
 * both (see {@link #kineticSource}).
 *
 * <p>{@link #refresh} pushes the drive's command into the saved line ({@code powered} and
 * {@code targetSpeed}), so an unloaded drive keeps its last command. It runs on a redstone neighbour
 * change ({@link LiftStationBlock}), when the speed level changes, and when the
 * block entity gets its line id ({@link #setLineId}, which the linker calls), so a new line starts in
 * the state the drive is in. The previous state is read from the line itself ({@code powered}), so it
 * survives a reload. Nothing runs on load: the saved line state stands (a signal across an unloaded chunk border reads false on the first tick, which would be a false emergency stop).
 */
public class LiftDriveStationBlockEntity extends LiftNodeBlockEntity implements LiftDriveSource {
    public static final int DEFAULT_SPEED_LEVEL = 1;

    private int speedLevel = DEFAULT_SPEED_LEVEL;

    public LiftDriveStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIFT_DRIVE_STATION.get(), pos, state);
    }

    public int speedLevel() {
        return speedLevel;
    }

    public void setSpeedLevel(int speedLevel) {
        this.speedLevel = speedLevel;
        setChanged();
    }

    /**
     * The kinetic drive attached to this station, or null (phase 9 decision 2, README 78). While there is one it
     * is the source and redstone is ignored; without Create the bridge answers null at no cost.
     */
    public LiftDriveSource kineticSource() {
        return level == null ? null : CreateCompat.bridge().kineticDriveNear(level, worldPosition);
    }

    @Override
    public boolean enabled() {
        LiftDriveSource kinetic = kineticSource();
        if (kinetic != null) return kinetic.enabled();
        if (!AlpineConfig.LIFT_DRIVE_NEEDS_REDSTONE.get()) return true;
        return level != null && level.hasNeighborSignal(worldPosition);
    }

    @Override
    public double targetSpeed() {
        LiftDriveSource kinetic = kineticSource();
        if (kinetic != null) return kinetic.targetSpeed();
        List<Double> levels = AlpineConfig.LIFT_SPEED_LEVELS.get();
        return levels.get(Math.max(0, Math.min(speedLevel, levels.size() - 1)));
    }

    /** Moves to the next speed level, wrapping, and returns the new target speed. */
    public double cycleSpeedLevel() {
        int count = AlpineConfig.LIFT_SPEED_LEVELS.get().size();
        setSpeedLevel((Math.max(0, Math.min(speedLevel, count - 1)) + 1) % count);
        return targetSpeed();
    }

    /**
     * A right-click on the drive. A line in emergency that redstone allows to run is reset; otherwise the
     * speed level moves on and the new speed goes to the player's action bar.
     */
    public void use(ServerLevel level, Player player) {
        LiftLine line = lineId == null ? null : LiftManager.get(level, lineId);
        if (line != null && line.emergency && enabled()) {
            LiftManager.clearEmergency(level, lineId);
            refresh(level);
            player.displayClientMessage(Component.translatable("message.alpineshred.lift.emergency_cleared"), true);
            return;
        }
        if (kineticSource() != null) {
            // The rotation sets the speed: there is no level to cycle.
            player.displayClientMessage(Component.translatable("message.alpineshred.lift.speed", String.format("%.1f", targetSpeed())), true);
            return;
        }
        double speed = cycleSpeedLevel();
        refresh(level);
        player.displayClientMessage(Component.translatable("message.alpineshred.lift.speed", String.format("%.1f", speed)), true);
    }

    /** Linking hook: a new line id means the line must be told what the drive is doing. */
    @Override
    public void setLineId(UUID lineId) {
        super.setLineId(lineId);
        if (lineId != null && level instanceof ServerLevel serverLevel) refresh(serverLevel);
    }

    /**
     * Writes the drive's command into its line.
     * <ul>
     *   <li>Not enabled (redstone lost): a running line (powered and moving) is emergency-stopped; the
     *       line is marked not powered either way.</li>
     *   <li>Enabled while the line is not powered (redstone back, or first link): the emergency is
     *       cleared and the line is powered.</li>
     *   <li>Enabled while already powered: only the target speed is updated, so a stop button's
     *       emergency is not undone by an unrelated neighbour update.</li>
     * </ul>
     */
    public void refresh(ServerLevel level) {
        if (lineId == null) return;
        LiftLine line = LiftManager.get(level, lineId);
        if (line == null) return;
        double target = targetSpeed();
        if (!enabled()) {
            if (line.powered && line.speed > 0) LiftManager.emergencyStop(level, lineId);
            LiftManager.setDrive(level, lineId, false, target);
        } else {
            if (!line.powered) LiftManager.clearEmergency(level, lineId);
            LiftManager.setDrive(level, lineId, true, target);
        }
    }

    @Override
    protected String nodeName() {
        return "drive station";
    }

    @Override
    protected String mismatch(LiftLine line) {
        return line.drive.equals(worldPosition) ? null : "not the drive station of the line";
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("SpeedLevel", speedLevel);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        speedLevel = tag.contains("SpeedLevel") ? tag.getInt("SpeedLevel") : DEFAULT_SPEED_LEVEL;
    }
}
