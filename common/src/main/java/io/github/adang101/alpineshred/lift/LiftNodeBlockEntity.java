package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * What every lift node (tower head, drive station, return station) remembers: the id of its line and,
 * for blocks a structure placed, the {@link PendingLine} that has not been adopted yet (phase 8 plan,
 * decision 5).
 *
 * <p><b>When the load check runs.</b> The check runs once, on the block entity's first server tick
 * ({@link #serverTick}), not in {@code setLevel} or Forge's {@code onLoad}. {@code onLoad} does not exist
 * on Fabric, and {@code setLevel} runs before a structure template applies the block entity's NBT, so a
 * generated head would still be empty when the check looked at it. By the first tick, both a chunk
 * loaded from disk and a block a structure placed hold their data. The check does not run in chunks
 * that never tick, which is fine: such a block cannot affect anything until its chunk is active.
 */
public abstract class LiftNodeBlockEntity extends BlockEntity {
    protected UUID lineId;
    protected PendingLine pending;
    private boolean checked;

    protected LiftNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public UUID lineId() {
        return lineId;
    }

    public void setLineId(UUID lineId) {
        this.lineId = lineId;
        setChanged();
    }

    public PendingLine pending() {
        return pending;
    }

    public void setPending(PendingLine pending) {
        this.pending = pending;
        setChanged();
    }

    /** Forgets the line; used when a line is removed. */
    public void clearLine() {
        this.lineId = null;
        this.pending = null;
        setChanged();
    }

    /** What this node is called in a fault message, e.g. "head". */
    protected abstract String nodeName();

    /**
     * A description of how this block entity's data disagrees with {@code line}, or null if it agrees.
     * The base check is that this position is a node of the line.
     */
    protected String mismatch(LiftLine line) {
        return line.nodePositions().contains(worldPosition) ? null : "not a node of the line";
    }

    /** Called after a pending line was adopted, to copy what the line knows into this block entity. */
    protected void adopted(LiftLine line) {
    }

    /** Runs the load check on the first server tick. */
    public void serverTick(Level level) {
        if (checked || !(level instanceof ServerLevel serverLevel)) return;
        checked = true;
        loadCheck(serverLevel);
    }

    /**
     * Adopts a pending line (the manager creates it only if its id is new), then compares the stored
     * data with the saved line. A mismatch faults the line; a missing line just clears the stale id.
     */
    public void loadCheck(ServerLevel level) {
        if (pending != null) {
            LiftLine adoptedLine = LiftManager.adoptPending(level, pending);
            if (adoptedLine == null) {
                AlpineShred.LOGGER.warn("Lift block at {} could not adopt its pending line {}", worldPosition, pending.id());
                return;
            }
            this.lineId = adoptedLine.id;
            this.pending = null;
            adopted(adoptedLine);
            setChanged();
        }
        if (lineId == null) return;
        LiftLine line = LiftManager.get(level, lineId);
        if (line == null) {
            this.lineId = null;
            setChanged();
            return;
        }
        String problem = mismatch(line);
        if (problem != null) {
            LiftManager.fault(level, lineId, nodeName() + " at " + describe(worldPosition) + ": " + problem);
        }
    }

    /** The block was removed (broken, burnt, exploded): its line cannot run any more. */
    public void onBlockRemoved(ServerLevel level) {
        if (lineId != null) {
            LiftManager.fault(level, lineId, nodeName() + " broken at " + describe(worldPosition));
        }
    }

    protected static String describe(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (lineId != null) tag.putUUID("LineId", lineId);
        if (pending != null) tag.put("Pending", pending.write());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        lineId = tag.hasUUID("LineId") ? tag.getUUID("LineId") : null;
        pending = tag.contains("Pending", 10) ? PendingLine.read(tag.getCompound("Pending")) : null;
    }
}
