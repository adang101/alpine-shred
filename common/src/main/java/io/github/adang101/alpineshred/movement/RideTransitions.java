package io.github.adang101.alpineshred.movement;

/**
 * Which state changes a client may propose with {@code c2s/ride_state} (design doc section 7,
 * validation rule 3). Strapping in and out goes through {@code c2s/strap_toggle}; grinds and box
 * slides (phase 4) and lift seating (phase 8) need world checks that arrive with those systems, so
 * until then the server refuses them.
 */
public final class RideTransitions {
    private RideTransitions() {
    }

    public static boolean clientMayRequest(RideState from, RideState to) {
        if (!from.isStrapped() || from == RideState.LIFT) return false;
        return switch (to) {
            case IDLE, GLIDE, CARVE, BRAKE, POWDER, AIR, WIPEOUT -> true;
            case OFF, GRIND, SLIDE, LIFT -> false;
        };
    }
}
