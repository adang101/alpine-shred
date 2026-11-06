package io.github.adang101.alpineshred.lift;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The payload of {@code s2c/lift_line_sync} (design doc section 15): one line's nodes and motion
 * state at a server game time. A {@code removed} sync carries only the id. Speeds and travel are
 * doubles, so a client that catches up with {@link LiftMotion#advance} matches the server exactly.
 */
public record LiftLineSync(UUID id, boolean removed, List<BlockPos> nodes, int spacing, double travel, double speed,
                           double targetSpeed, long gameTime, boolean faulted, boolean powered, boolean emergency) {
    /** Most nodes a packet may carry; a line is limited far below this. */
    public static final int MAX_NODES = 1024;

    public static LiftLineSync removed(UUID id) {
        return new LiftLineSync(id, true, List.of(), 0, 0, 0, 0, 0, false, false, false);
    }

    /** The line's state as the server holds it at {@code gameTime}. */
    public static LiftLineSync of(LiftLine line, long gameTime) {
        return new LiftLineSync(line.id, false, line.nodePositions(), line.spacing(), line.travel, line.speed,
                line.targetSpeed, gameTime, line.faulted, line.powered, line.emergency);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeBoolean(removed);
        if (removed) return;
        buf.writeVarInt(nodes.size());
        for (BlockPos p : nodes) buf.writeBlockPos(p);
        buf.writeVarInt(spacing);
        buf.writeDouble(travel);
        buf.writeDouble(speed);
        buf.writeDouble(targetSpeed);
        buf.writeLong(gameTime);
        buf.writeByte((faulted ? 1 : 0) | (powered ? 2 : 0) | (emergency ? 4 : 0));
    }

    /** Reads a sync, or returns null if it is malformed. */
    public static LiftLineSync read(FriendlyByteBuf buf) {
        try {
            UUID id = buf.readUUID();
            if (buf.readBoolean()) return removed(id);
            int n = buf.readVarInt();
            if (n < 2 || n > MAX_NODES) return null;
            List<BlockPos> nodes = new ArrayList<>(n);
            for (int i = 0; i < n; i++) nodes.add(buf.readBlockPos());
            int spacing = buf.readVarInt();
            double travel = buf.readDouble();
            double speed = buf.readDouble();
            double target = buf.readDouble();
            long time = buf.readLong();
            int flags = buf.readUnsignedByte();
            return new LiftLineSync(id, false, nodes, spacing, travel, speed, target, time,
                    (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
