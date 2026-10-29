package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.park.Facing;
import io.github.adang101.alpineshred.park.GrindPhysics;
import io.github.adang101.alpineshred.park.ParkView;
import io.github.adang101.alpineshred.park.PipeSection;
import io.github.adang101.alpineshred.park.V3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** {@link ParkView} over a real level: rail and fence paths, quarter pipes, lip ramps. */
public final class LevelParkView implements ParkView {
    private static final int MAX_PIPE_WIDTH = 32;

    private final BlockGetter level;

    public LevelParkView(BlockGetter level) {
        this.level = level;
    }

    @Override
    public GrindPhysics.Lookup grinds() {
        return GrindPaths.lookup(level);
    }

    @Override
    @Nullable
    public PipeSection pipeNear(V3 feet) {
        BlockPos base = BlockPos.containing(feet.x(), feet.y(), feet.z());
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos p = base.above(dy);
            PipeSection s = sectionAt(p);
            if (s != null) return s;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                s = sectionAt(p.relative(d));
                if (s != null) return s;
            }
        }
        return null;
    }

    @Nullable
    private PipeSection sectionAt(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeBlock)) return null;
        Facing facing = PipeBlock.facing(state);
        int radius = PipeBlock.radius(state);
        BlockPos origin = PipeBlock.origin(state, pos);
        Direction lateral = ParkStates.direction(facing.clockwise());
        int min = 0, max = 0;
        while (min > -MAX_PIPE_WIDTH && sameTransition(origin.relative(lateral, min - 1), facing, radius)) min--;
        while (max < MAX_PIPE_WIDTH && sameTransition(origin.relative(lateral, max + 1), facing, radius)) max++;
        double along = lowEdge(origin, facing);
        double acrossMin = lowEdge(origin.relative(lateral, min), facing.clockwise());
        double acrossMax = lowEdge(origin.relative(lateral, max), facing.clockwise()) + 1;
        return new PipeSection(facing, radius, along, origin.getY(), acrossMin, acrossMax);
    }

    private boolean sameTransition(BlockPos pos, Facing facing, int radius) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof PipeBlock && PipeBlock.facing(s) == facing && PipeBlock.radius(s) == radius
                && PipeBlock.index(s) == 0;
    }

    /** The lowest coordinate of a block measured along a direction (its back face). */
    static double lowEdge(BlockPos pos, Facing f) {
        if (f.dx > 0) return pos.getX();
        if (f.dx < 0) return -(pos.getX() + 1);
        if (f.dz > 0) return pos.getZ();
        return -(pos.getZ() + 1);
    }

    @Override
    public boolean lipNear(V3 feet) {
        BlockPos base = BlockPos.containing(feet.x(), feet.y(), feet.z());
        for (BlockPos p : new BlockPos[]{base, base.below()}) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof RampBlock && s.getValue(ParkStates.LIP)) return true;
        }
        return false;
    }
}
