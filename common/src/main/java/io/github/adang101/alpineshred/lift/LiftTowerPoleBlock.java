package io.github.adang101.alpineshred.lift;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** {@code lift_tower_pole}: one block of tower shaft. It has no data and no behaviour. */
public class LiftTowerPoleBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.box(6 / 16.0, 0, 6 / 16.0, 10 / 16.0, 1, 10 / 16.0);

    public LiftTowerPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
