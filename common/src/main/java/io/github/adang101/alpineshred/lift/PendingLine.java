package io.github.adang101.alpineshred.lift;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A line a structure piece asks for during generation, when saved data cannot be touched (phase 8
 * plan, decision 5): the nodes (drive, heads in order, return) and a generated UUID. The first block
 * entity of the line to load hands it to {@link LiftManager#adoptPending}, which creates the line
 * once.
 *
 * <p>{@code powered} and {@code targetSpeed} are the drive command the new line starts with (a
 * generated lift that needs no redstone starts running); a pending line without them starts stopped.
 */
public record PendingLine(UUID id, List<BlockPos> nodes, boolean powered, double targetSpeed) {
    public PendingLine {
        nodes = List.copyOf(nodes);
    }

    public PendingLine(UUID id, List<BlockPos> nodes) {
        this(id, nodes, false, 0);
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        ListTag list = new ListTag();
        for (BlockPos p : nodes) list.add(NbtUtils.writeBlockPos(p));
        tag.put("Nodes", list);
        if (powered) {
            tag.putBoolean("Powered", true);
            tag.putDouble("Target", targetSpeed);
        }
        return tag;
    }

    public static PendingLine read(CompoundTag tag) {
        List<BlockPos> nodes = new ArrayList<>();
        for (Tag t : tag.getList("Nodes", Tag.TAG_COMPOUND)) nodes.add(NbtUtils.readBlockPos((CompoundTag) t));
        return new PendingLine(tag.getUUID("Id"), nodes, tag.getBoolean("Powered"), tag.getDouble("Target"));
    }
}
