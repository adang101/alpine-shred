package io.github.adang101.alpineshred.movement;

/**
 * Decides when the client sends {@code c2s/ride_state} (design doc section 15): on change, at most
 * once every {@link #MIN_INTERVAL_TICKS} ticks (10 per second). A change that arrives inside the
 * interval goes out on the next allowed tick, because the caller offers the current bits every tick.
 *
 * <p>State changes are not exempt: the server rate-limits this packet to 10 per second and counts
 * drops as violations, and a player tapping A and D can flip GLIDE and CARVE faster than that.
 * Nothing on the server needs a state change sooner than one tick late.
 */
public final class StateSendThrottle {
    public static final int MIN_INTERVAL_TICKS = 2;

    private int lastBits = -1;
    private long lastTick = Long.MIN_VALUE / 2;

    public boolean shouldSend(long tick, int bits) {
        if (bits == lastBits) return false;
        if (tick - lastTick < MIN_INTERVAL_TICKS) return false;
        lastBits = bits;
        lastTick = tick;
        return true;
    }

    public void reset() {
        lastBits = -1;
        lastTick = Long.MIN_VALUE / 2;
    }
}
