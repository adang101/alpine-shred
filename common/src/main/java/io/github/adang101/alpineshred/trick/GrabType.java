package io.github.adang101.alpineshred.trick;

/**
 * Grabs (design doc section 8). Ordinal is the 4-bit wire value; append only.
 * The factor multiplies grab points.
 */
public enum GrabType {
    NONE(0.0),
    INDY(1.0),
    MUTE(1.1),
    NOSE(1.2),
    TAIL(1.2),
    METHOD(1.5);

    private static final GrabType[] VALUES = values();

    private final double scoreFactor;

    GrabType(double scoreFactor) {
        this.scoreFactor = scoreFactor;
    }

    public double scoreFactor() {
        return scoreFactor;
    }

    public static GrabType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
