package io.github.adang101.alpineshred.movement;

/**
 * Implemented on {@code Player} by {@code mixin.PlayerMixin}: the packed {@link RideSnapshot} in a
 * synced data accessor, so vanilla entity sync delivers it to every tracking client and to players
 * who join later (design doc section 7, "Rider state").
 */
public interface RideStateHolder {
    int alpineshred$getRideBits();

    void alpineshred$setRideBits(int bits);
}
