package io.github.adang101.alpineshred.lift;

/** Whether the block at a position is solid; the link validator's only view of the world. */
@FunctionalInterface
public interface BlockSolid {
    boolean solid(int x, int y, int z);
}
