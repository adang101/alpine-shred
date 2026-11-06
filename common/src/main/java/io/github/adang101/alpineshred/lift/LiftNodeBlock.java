package io.github.adang101.alpineshred.lift;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * A lift block with a block entity (tower head or station). Removing it, by any means, faults its
 * line; its first server tick runs the {@linkplain LiftNodeBlockEntity load check}. The properties
 * should make it immovable ({@link #nodeProperties}) so a piston cannot take it apart.
 */
public abstract class LiftNodeBlock extends BaseEntityBlock {
    private final Supplier<? extends BlockEntityType<? extends LiftNodeBlockEntity>> type;

    protected LiftNodeBlock(Properties properties, Supplier<? extends BlockEntityType<? extends LiftNodeBlockEntity>> type) {
        super(properties);
        this.type = type;
    }

    /** Steel-like properties, immovable by pistons. */
    public static Properties nodeProperties() {
        return LiftBlocks.steel();
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> given) {
        if (level.isClientSide || given != type.get()) return null;
        return (l, p, s, be) -> {
            if (be instanceof LiftNodeBlockEntity node) node.serverTick(l);
        };
    }

    /** Runs before the block entity is removed, so the line id can still be read. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof LiftNodeBlockEntity node) {
            node.onBlockRemoved(serverLevel);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
