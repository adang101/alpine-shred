package io.github.adang101.alpineshred.movement;

/**
 * Which state changes a client may propose with {@code c2s/ride_state} (design doc section 7,
 * validation rule 3). Strapping in and out goes through {@code c2s/strap_toggle}. Grinds and box
 * slides are allowed here and checked against the world by the server ({@link #needsWorldCheck}):
 * {@code GRIND} needs a grindable block in the capture zone, {@code SLIDE} a slideable block. Lift
 * seating (phase 8) is refused until lifts exist.
 */
public final class RideTransitions {
    private RideTransitions() {
    }

    public static boolean clientMayRequest(RideState from, RideState to) {
        if (!from.isStrapped() || from == RideState.LIFT) return false;
        return switch (to) {
            case IDLE, GLIDE, CARVE, BRAKE, POWDER, AIR, WIPEOUT, GRIND, SLIDE -> true;
            case OFF, LIFT -> false;
        };
    }

    /** States the server accepts only when the world backs them: a rail for GRIND, a box for SLIDE. */
    public static boolean needsWorldCheck(RideState to) {
        return to == RideState.GRIND || to == RideState.SLIDE;
    }
}
