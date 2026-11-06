package io.github.adang101.alpineshred.lift;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared properties and shape helpers for the lift blocks. */
public final class LiftBlocks {
    private LiftBlocks() {
    }

    /** Steel parts: pickaxe to drop, and a piston can neither push nor pull them. */
    public static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    /**
     * A box given in pixels (0..16) for a model that faces north, turned to face {@code facing}
     * (a horizontal direction), the way a blockstate {@code y} rotation turns the model.
     */
    public static VoxelShape box(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        for (int i = 0; i < turns; i++) {
            // A quarter turn clockwise seen from above: (x, z) -> (16 - z, x).
            double nx1 = 16 - z2, nx2 = 16 - z1, nz1 = x1, nz2 = x2;
            x1 = nx1;
            x2 = nx2;
            z1 = nz1;
            z2 = nz2;
        }
        return Shapes.box(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }
}
