package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.park.RailPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** {@link RailPlacement.World} over a real level: rail pieces, and full blocks as ground. */
public final class LevelRails implements RailPlacement.World {
    private final BlockGetter level;

    public LevelRails(BlockGetter level) {
        this.level = level;
    }

    public static RailPlacement.Cell cell(BlockPos pos) {
        return new RailPlacement.Cell(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos pos(RailPlacement.Cell c) {
        return new BlockPos(c.x(), c.y(), c.z());
    }

    @Override
    public RailPlacement.Piece railAt(RailPlacement.Cell c) {
        BlockState state = level.getBlockState(pos(c));
        return state.getBlock() instanceof RailBlock ? RailBlock.piece(state) : null;
    }

    @Override
    public boolean solid(RailPlacement.Cell c) {
        BlockPos p = pos(c);
        return level.getBlockState(p).isCollisionShapeFullBlock(level, p);
    }
}
