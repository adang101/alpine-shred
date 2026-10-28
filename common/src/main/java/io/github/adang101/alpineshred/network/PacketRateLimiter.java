package io.github.adang101.alpineshred.network;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

/**
 * A per-player token bucket for one client-to-server packet type (design doc section 15, "Rate
 * limiting"). Excess packets are dropped; nothing disconnects a player.
 *
 * <p>Wall-clock time, not game time: clients send at their own 20 ticks per second, so a server
 * running at 10 TPS would otherwise see every client as flooding. Drops count toward the ride
 * validator at most once per second per player ({@link #dropShouldCount}), so a burst of delayed
 * packets after a lag spike costs one violation, not dozens.
 */
public final class PacketRateLimiter {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final double perSecond;
    private final double capacity;
    private final LongSupplier clock;
    private final Map<Player, long[]> lastCountedDrop = new WeakHashMap<>();
    private final Map<Player, double[]> buckets = new WeakHashMap<>();

    /**
     * @param perSecond refill rate in packets per second
     * @param capacity  burst size
     */
    public PacketRateLimiter(double perSecond, double capacity) {
        this(perSecond, capacity, System::nanoTime);
    }

    PacketRateLimiter(double perSecond, double capacity, LongSupplier nanoClock) {
        this.perSecond = perSecond;
        this.capacity = capacity;
        this.clock = nanoClock;
    }

    public synchronized boolean tryAcquire(Player player) {
        long now = clock.getAsLong();
        double[] bucket = buckets.computeIfAbsent(player, p -> new double[]{capacity, now});
        double elapsedSeconds = Math.max(0, (now - (long) bucket[1]) / (double) NANOS_PER_SECOND);
        bucket[0] = Math.min(capacity, bucket[0] + elapsedSeconds * perSecond);
        bucket[1] = now;
        if (bucket[0] >= 1) {
            bucket[0] -= 1;
            return true;
        }
        return false;
    }

    /** After a drop: true at most once per second per player. */
    public synchronized boolean dropShouldCount(Player player) {
        long now = clock.getAsLong();
        long[] last = lastCountedDrop.computeIfAbsent(player, p -> new long[]{now - NANOS_PER_SECOND});
        if (now - last[0] < NANOS_PER_SECOND) return false;
        last[0] = now;
        return true;
    }
}
