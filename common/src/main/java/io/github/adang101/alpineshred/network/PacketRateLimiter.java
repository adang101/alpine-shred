package io.github.adang101.alpineshred.network;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A per-player token bucket for one client-to-server packet type (design doc section 15, "Rate
 * limiting"). Excess packets are dropped; nothing disconnects a player. The phase 2 validator will
 * also count drops toward its violation threshold.
 */
public final class PacketRateLimiter {
    private final double perSecond;
    private final double capacity;
    private final Map<Player, double[]> buckets = new WeakHashMap<>();

    /**
     * @param perSecond refill rate in packets per second
     * @param capacity  burst size
     */
    public PacketRateLimiter(double perSecond, double capacity) {
        this.perSecond = perSecond;
        this.capacity = capacity;
    }

    /** Uses game ticks (20 per second) so the limiter follows server time, including at low TPS. */
    public synchronized boolean tryAcquire(Player player) {
        double now = player.level().getGameTime();
        double[] bucket = buckets.computeIfAbsent(player, p -> new double[]{capacity, now});
        double elapsedSeconds = Math.max(0, (now - bucket[1]) / 20.0);
        bucket[0] = Math.min(capacity, bucket[0] + elapsedSeconds * perSecond);
        bucket[1] = now;
        if (bucket[0] >= 1) {
            bucket[0] -= 1;
            return true;
        }
        return false;
    }
}
