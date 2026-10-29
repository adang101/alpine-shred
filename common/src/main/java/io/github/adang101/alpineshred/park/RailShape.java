package io.github.adang101.alpineshred.park;

/**
 * Rail piece geometry (design doc section 10, "Shapes and paths"). Paths are authored facing north,
 * in block-local coordinates (0 to 1 on each axis): end A on the south edge, end B on the north edge
 * (for a curve, the east edge).
 */
public enum RailShape {
    /** Straight and level at height 0.5. */
    FLAT,
    /** First half of a rise: 0.5 at end A up to 1.0 at end B, inside the lower block. */
    RISE_A,
    /** Second half, one block higher and one block along: 0.0 up to 0.5. */
    RISE_B,
    /** Quarter arc of radius 0.5 at height 0.5, from the south edge to the east edge. */
    CURVE;

    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static RailShape byName(String name) {
        for (RailShape s : values()) {
            if (s.serializedName().equals(name)) return s;
        }
        return null;
    }
}
