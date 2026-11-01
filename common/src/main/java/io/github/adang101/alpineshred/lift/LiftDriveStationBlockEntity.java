package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The drive station's data: its line and the speed level the drive is set to (an index into
 * {@code lift.speedLevels}; 1 is "normal"). Nothing acts on the level yet (work package 7).
 */
public class LiftDriveStationBlockEntity extends LiftNodeBlockEntity {
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
