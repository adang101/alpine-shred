package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A tower head: the line it belongs to and its neighbours in loop order ({@code prev} and {@code next}). */
public class LiftTowerHeadBlockEntity extends LiftNodeBlockEntity {
    private BlockPos prev;
    private BlockPos next;

    public LiftTowerHeadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIFT_TOWER_HEAD.get(), pos, state);
    }

    public BlockPos prev() {
        return prev;
    }

    public BlockPos next() {
        return next;
    }

    public void setLink(UUID lineId, BlockPos prev, BlockPos next) {
        this.lineId = lineId;
        this.prev = prev == null ? null : prev.immutable();
        this.next = next == null ? null : next.immutable();
        setChanged();
    }

    @Override
    public void clearLine() {
        this.prev = null;
        this.next = null;
        super.clearLine();
    }

    @Override
    protected String nodeName() {
        return "head";
    }

    @Override
    protected String mismatch(LiftLine line) {
        List<BlockPos> nodes = line.nodePositions();
        int i = nodes.indexOf(worldPosition);
        if (i < 1 || i > nodes.size() - 2) return "not a head of the line";
        if (!Objects.equals(prev, nodes.get(i - 1))) return "previous node " + prev + " should be " + nodes.get(i - 1);
        if (!Objects.equals(next, nodes.get(i + 1))) return "next node " + next + " should be " + nodes.get(i + 1);
        return null;
    }

    @Override
    protected void adopted(LiftLine line) {
        List<BlockPos> nodes = line.nodePositions();
        int i = nodes.indexOf(worldPosition);
        if (i >= 1 && i <= nodes.size() - 2) {
            prev = nodes.get(i - 1);
            next = nodes.get(i + 1);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (prev != null) tag.put("Prev", NbtUtils.writeBlockPos(prev));
        if (next != null) tag.put("Next", NbtUtils.writeBlockPos(next));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        prev = tag.contains("Prev", 10) ? NbtUtils.readBlockPos(tag.getCompound("Prev")) : null;
        next = tag.contains("Next", 10) ? NbtUtils.readBlockPos(tag.getCompound("Next")) : null;
    }
}
