package io.github.adang101.alpineshred.compat.create;

/**
 * Remembers the last command a kinetic drive gave its station, so the drive only pushes a refresh when
 * the command changes (README 78). Create can drop rotation without telling the block entity (its own
 * validation just zeroes the speed), so the drive compares every tick; this keeps that comparison free
 * of allocation and of work.
 *
 * <p>A fresh drive starts as "stopped": placing it already refreshed the station through the
 * neighbour update, and the first rotation then arrives as a change. A drive loaded from disk
 * calls {@link #reset} with what it read, so a reload never looks like a change (a false emergency
 * stop of a running lift).
 */
public final class KineticDriveWatch {
    private boolean enabled;
    private double target;

    /** Takes {@code enabled} and {@code target} as the baseline without reporting a change. */
    public void reset(boolean enabled, double target) {
        this.enabled = enabled;
        this.target = target;
    }

    /** Stores the command and returns whether it differs from the stored one. */
    public boolean update(boolean enabled, double target) {
        if (this.enabled == enabled && this.target == target) return false;
        this.enabled = enabled;
        this.target = target;
        return true;
    }
}
