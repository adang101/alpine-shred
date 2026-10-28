package io.github.adang101.alpineshred.movement;

/**
 * Rider states (design doc section 7). The ordinal is the 4-bit wire value inside
 * {@link RideSnapshot}, so new states may only be appended, never reordered.
 */
public enum RideState {
    /** Not strapped in. Vanilla movement. */
    OFF,
    /** Strapped in and nearly still, or strapped on a surface that cannot be ridden. */
    IDLE,
    GLIDE,
    /** Steer input held while gliding. */
    CARVE,
    BRAKE,
    POWDER,
    AIR,
    /** On a rail. */
    GRIND,
    /** On a box. */
    SLIDE,
    WIPEOUT,
    /** Seated on a lift chair. */
    LIFT;

    private static final RideState[] VALUES = values();

    public int id() {
        return ordinal();
    }

    /** Unknown ids decode to {@link #OFF} so a newer client cannot crash an older one. */
    public static RideState byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : OFF;
    }

    public boolean isStrapped() {
        return this != OFF;
    }

    /** The five ground sub-states shown as one hub in the state machine diagram. */
    public boolean isGroundRiding() {
        return this == IDLE || this == GLIDE || this == CARVE || this == BRAKE || this == POWDER;
    }

    public boolean isOnFeature() {
        return this == GRIND || this == SLIDE;
    }

    /** States in which the slide controller, rather than vanilla, moves the rider. */
    public boolean isControllerDriven() {
        return isGroundRiding() || this == AIR || isOnFeature() || this == WIPEOUT;
    }
}
