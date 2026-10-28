package io.github.adang101.alpineshred.movement;

/**
 * Implemented on {@code Player} by {@code mixin.PlayerMixin}.
 *
 * <ul>
 *   <li>The packed {@link RideSnapshot} in a synced data accessor, so vanilla entity sync delivers it
 *       to every tracking client and to players who join later (design doc section 7).</li>
 *   <li>Local, unsynced values set by whoever runs a {@link SlideController} for this player (the
 *       client driver, or a game test): whether the controller is moving the body this tick, the
 *       step height it rides with, and whether it is fast enough to float on powder snow.</li>
 * </ul>
 */
public interface RideStateHolder {
    int alpineshred$getRideBits();

    void alpineshred$setRideBits(int bits);

    boolean alpineshred$isControlled();

    void alpineshred$setControlled(boolean controlled);

    /** Step height for controller moves; 0 for vanilla. */
    float alpineshred$rideStep();

    void alpineshred$setRideStep(float step);

    boolean alpineshred$floatsOnPowder();

    void alpineshred$setFloatsOnPowder(boolean floats);
}
