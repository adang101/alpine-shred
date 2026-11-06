package io.github.adang101.alpineshred.lift;

import dev.architectury.event.events.common.TickEvent;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.config.AlpineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Runs every level's lift lines (design doc section 13): each server level post-tick it steps each
 * line with {@link LiftMotion#step}. The target is the line's {@code targetSpeed} when it is powered,
 * not faulted and not in emergency, else 0; faulted and emergency lines slow at the emergency rate.
 * The saved data is marked dirty when a speed or flag changes and at least every
 * {@link #SAVE_INTERVAL} ticks. All calls are on the server thread.
 */
public final class LiftManager {
    public static final int SAVE_INTERVAL = 200;

    /** Told when a line's speed target or a flag changes, or when a line is made or removed. */
    public interface Listener {
        void changed(ServerLevel level, LiftLine line);

        void removed(ServerLevel level, UUID id);
    }

    private static volatile Listener listener;

    /** Sets the one listener (networking sets it; the manager itself does not depend on it). */
    public static void setListener(Listener l) {
        listener = l;
    }

    private static void changed(ServerLevel level, LiftLine line) {
        Listener l = listener;
        if (l != null) l.changed(level, line);
    }

    private LiftManager() {
    }

    public static void register() {
        TickEvent.SERVER_LEVEL_POST.register(LiftManager::tick);
    }

    /** One tick for every line of {@code level}. Public so tests can run it directly. */
    public static void tick(ServerLevel level) {
        LiftSavedData data = LiftSavedData.get(level);
        if (data.lines().isEmpty()) return;
        boolean runUnloaded = AlpineConfig.LIFT_RUN_UNLOADED.get();
        for (LiftLine line : data.lines()) {
            if (!runUnloaded && !anyNodeLoaded(level, line)) continue;
            stepLine(data, line);
        }
        LiftChairs.tick(level, data.lines());
    }

    private static boolean anyNodeLoaded(ServerLevel level, LiftLine line) {
        for (BlockPos p : line.nodePositions()) {
            if (level.hasChunkAt(p)) return true;
        }
        return false;
    }

    private static void stepLine(LiftSavedData data, LiftLine line) {
        boolean stopping = LiftMotion.isStopping(line.faulted, line.emergency);
        double target = LiftMotion.targetFor(line.powered, line.faulted, line.emergency, line.targetSpeed);
        double[] next = LiftMotion.step(line.speed, line.travel, target, stopping, line.path().loopLength());
        boolean speedChanged = next[0] != line.speed;
        line.speed = next[0];
        line.travel = next[1];
        line.age++;
        if (speedChanged || ++line.ticksSinceDirty >= SAVE_INTERVAL) {
            line.ticksSinceDirty = 0;
            data.setDirty();
        }
    }

    // ---- API (every call names the level, since lines live in one level's saved data) ----

    /**
     * Creates a line through {@code nodes} (drive, heads in order, return), or null if there are fewer
     * than two, the nodes cannot make a path, or the level is at {@code lift.maxLinesPerLevel}.
     */
    public static LiftLine create(ServerLevel level, List<BlockPos> nodes) {
        return create(level, UUID.randomUUID(), nodes);
    }

    public static LiftLine create(ServerLevel level, UUID id, List<BlockPos> nodes) {
        if (nodes.size() < 2) return null;
        LiftLine line;
        try {
            line = new LiftLine(id, nodes.get(0), nodes.subList(1, nodes.size() - 1), nodes.get(nodes.size() - 1));
        } catch (IllegalArgumentException e) {
            AlpineShred.LOGGER.warn("Cannot make a lift line: {}", e.getMessage());
            return null;
        }
        if (!LiftSavedData.get(level).add(line)) return null;
        changed(level, line);
        return line;
    }

    /** The line for a pending one, creating it only if its UUID is not yet a line. */
    public static LiftLine adoptPending(ServerLevel level, PendingLine pending) {
        LiftLine existing = LiftSavedData.get(level).get(pending.id());
        if (existing != null) return existing;
        LiftLine created = create(level, pending.id(), pending.nodes());
        if (created != null && pending.powered()) setDrive(level, created.id, true, pending.targetSpeed());
        return created;
    }

    public static boolean remove(ServerLevel level, UUID id) {
        boolean removed = LiftSavedData.get(level).remove(id) != null;
        Listener l = listener;
        if (removed && l != null) l.removed(level, id);
        return removed;
    }

    public static LiftLine get(ServerLevel level, UUID id) {
        return LiftSavedData.get(level).get(id);
    }

    public static Collection<LiftLine> lines(ServerLevel level) {
        return LiftSavedData.get(level).lines();
    }

    /** A snapshot of the lines, safe to iterate while removing. */
    public static List<LiftLine> snapshot(ServerLevel level) {
        return new ArrayList<>(LiftSavedData.get(level).lines());
    }

    /** Faults a line: it stops at the emergency rate and stays stopped until relinked. */
    public static void fault(ServerLevel level, UUID id, String reason) {
        LiftSavedData data = LiftSavedData.get(level);
        LiftLine line = data.get(id);
        if (line == null) return;
        boolean was = line.faulted;
        if (!was) AlpineShred.LOGGER.info("Lift line {} faulted: {}", id, reason);
        line.faulted = true;
        line.faultReason = reason;
        data.setDirty();
        if (!was) changed(level, line);
    }

    /** Sets the drive's command: whether it is running and the speed it wants. */
    public static void setDrive(ServerLevel level, UUID id, boolean powered, double target) {
        LiftSavedData data = LiftSavedData.get(level);
        LiftLine line = data.get(id);
        if (line == null) return;
        if (line.powered != powered || line.targetSpeed != target) {
            line.powered = powered;
            line.targetSpeed = target;
            data.setDirty();
            changed(level, line);
        }
    }

    public static void emergencyStop(ServerLevel level, UUID id) {
        LiftSavedData data = LiftSavedData.get(level);
        LiftLine line = data.get(id);
        if (line == null || line.emergency) return;
        line.emergency = true;
        data.setDirty();
        changed(level, line);
    }

    public static void clearEmergency(ServerLevel level, UUID id) {
        LiftSavedData data = LiftSavedData.get(level);
        LiftLine line = data.get(id);
        if (line == null || !line.emergency) return;
        line.emergency = false;
        data.setDirty();
        changed(level, line);
    }
}
