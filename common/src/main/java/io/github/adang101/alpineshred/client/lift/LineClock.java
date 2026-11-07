package io.github.adang101.alpineshred.client.lift;

import java.util.Collection;

/**
 * Keeps client lines on the client level's game time (README clarification 73). The time of the last
 * point the lines were brought to is remembered; {@link #follow} moves every line by the game time that has
 * passed since. It must also run just before a sync is applied: the server sends its time packet and the
 * 100-tick resend in the same tick, so both arrive between two client ticks, and a sync applied to a clock
 * that has already jumped, with the jump still unshifted, would be shifted by the jump again at the next tick.
 * Pure (no client classes), so a unit test drives it.
 */
final class LineClock {
    private static final long NONE = Long.MIN_VALUE;
    private long last = NONE;

    void reset() {
        last = NONE;
    }

    /**
     * Brings {@code lines} to game time {@code now}. The first call after a reset has nothing to measure from
     * and moves the lines {@code firstDelta} ticks: one for a client tick (the level ticked once since the
     * lines were built), none before a sync. Returns the ticks moved.
     */
    long follow(Collection<ClientLine> lines, long now, long firstDelta) {
        long delta = last == NONE ? firstDelta : now - last;
        last = now;
        if (delta == 0) return 0;
        for (ClientLine line : lines) {
            if (delta == 1) line.tick();
            else line.shift(delta);
        }
        return delta;
    }
}
