package io.github.adang101.alpineshred.network;

/**
 * Action bytes of {@code c2s/ride_action} (design doc section 15). Each carries one VarInt value.
 * Grab start and end join in phase 5. Ids are wire values: append only.
 */
public enum RideAction {
    /** A pop; value = charge ticks. */
    POP,
    /** A ramp launch; value = upward velocity in thousandths of a block per tick. */
    LAUNCH,
    /** A ski pole push; value unused. */
    POLE_PUSH;

    private static final RideAction[] VALUES = values();

    public int id() {
        return ordinal();
    }

    /** Null for unknown ids, which the server ignores. */
    public static RideAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
