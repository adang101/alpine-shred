package io.github.adang101.alpineshred.park;

/**
 * Park ramp steepness (design doc section 11, "Ramps and kickers"): rise per block, and how many
 * blocks a full run of that ramp takes to climb one block.
 */
public enum RampSlope {
    QUARTER(0.25, 4), HALF(0.5, 2), FULL(1.0, 1);

    /** Collision steps per block, each 2 pixels long. */
    public static final int STEPS = 8;

    public final double rise;
    /** Blocks in one run: the valid {@code step} values are 0 to {@code blocks - 1}. */
    public final int blocks;

    RampSlope(double rise, int blocks) {
        this.rise = rise;
        this.blocks = blocks;
    }

    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public int maxStep() {
        return blocks - 1;
    }

    /** Degrees of the ramp's surface. */
    public double degrees() {
        return Math.toDegrees(Math.atan(rise));
    }

    /**
     * Top of collision column {@code column} (0 to 7, from the low edge toward the facing) in a
     * ramp block at segment {@code step}, in blocks above the block's base. A whole run climbs one
     * block, so every segment sits on the same ground and its last column meets the next one's first.
     */
    public double columnTop(int step, int column) {
        int s = Math.max(0, Math.min(step, maxStep()));
        return Math.min(1.0, rise * (s + (column + 1) / (double) STEPS));
    }

    public static RampSlope byName(String name) {
        for (RampSlope s : values()) {
            if (s.serializedName().equals(name)) return s;
        }
        return null;
    }
}
