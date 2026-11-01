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
 */
public record PendingLine(UUID id, List<BlockPos> nodes) {
    public PendingLine {
        nodes = List.copyOf(nodes);
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        ListTag list = new ListTag();
        for (BlockPos p : nodes) list.add(NbtUtils.writeBlockPos(p));
        tag.put("Nodes", list);
        return tag;
    }

    public static PendingLine read(CompoundTag tag) {
        List<BlockPos> nodes = new ArrayList<>();
        for (Tag t : tag.getList("Nodes", Tag.TAG_COMPOUND)) nodes.add(NbtUtils.readBlockPos((CompoundTag) t));
        return new PendingLine(tag.getUUID("Id"), nodes);
    }
}
