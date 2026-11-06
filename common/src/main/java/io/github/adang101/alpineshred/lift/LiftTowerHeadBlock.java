package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;

/** {@code lift_tower_head}: the top of a tower, where the cable passes (design doc section 13). */
public class LiftTowerHeadBlock extends LiftNodeBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Shapes.box(6 / 16.0, 0, 6 / 16.0, 10 / 16.0, 10 / 16.0, 10 / 16.0),
            Shapes.box(0, 10 / 16.0, 5 / 16.0, 1, 13 / 16.0, 11 / 16.0),
            Shapes.box(2 / 16.0, 13 / 16.0, 3 / 16.0, 14 / 16.0, 1, 13 / 16.0));

    public LiftTowerHeadBlock(Properties properties) {
        super(properties, ModBlockEntities.LIFT_TOWER_HEAD::get);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
