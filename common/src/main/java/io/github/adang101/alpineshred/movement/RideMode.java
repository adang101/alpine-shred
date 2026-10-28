package io.github.adang101.alpineshred.movement;

/** What the rider is strapped to. Ordinal is the 2-bit wire value; append only. */
public enum RideMode {
    NONE,
    BOARD,
    SKIS;

    private static final RideMode[] VALUES = values();

    public static RideMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
