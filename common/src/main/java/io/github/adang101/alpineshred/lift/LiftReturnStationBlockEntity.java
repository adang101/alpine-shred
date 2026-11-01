package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The return station's data: its line. */
public class LiftReturnStationBlockEntity extends LiftNodeBlockEntity {
    public LiftReturnStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIFT_RETURN_STATION.get(), pos, state);
    }

    @Override
    protected String nodeName() {
        return "return station";
    }

    @Override
    protected String mismatch(LiftLine line) {
        return line.returnStation.equals(worldPosition) ? null : "not the return station of the line";
    }
}
