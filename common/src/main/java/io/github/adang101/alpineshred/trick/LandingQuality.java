package io.github.adang101.alpineshred.trick;

/**
 * Landing quality (design doc section 8, "Landing evaluation"): the score factor and what the
 * landing does to the rider. Ordinal is the wire value; append only.
 */
public enum LandingQuality {
    PERFECT(1.25, 15, 15, 0.0),
    CLEAN(1.0, 35, 30, 0.0),
    SKETCHY(0.5, 60, 45, 0.4),
    BAIL(0.0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 1.0);

    private static final LandingQuality[] VALUES = values();
    /** PERFECT also needs the rider's descent within this many degrees of the surface it lands on. */
    public static final double PERFECT_SLOPE_WINDOW = 10;

    public final double factor;
    /** Heading error window, degrees, before gear forgiveness. */
    public final double headingWindow;
    /** Flip residual window, degrees, before gear forgiveness. */
    public final double flipWindow;
    /** Share of speed lost on landing (BAIL wipes out). */
    public final double speedLoss;

    LandingQuality(double factor, double headingWindow, double flipWindow, double speedLoss) {
        this.factor = factor;
        this.headingWindow = headingWindow;
        this.flipWindow = flipWindow;
        this.speedLoss = speedLoss;
    }

    /**
     * @param headingError degrees between the board and the direction of travel, folded so a
     *                     switch (backward) landing counts as aligned: 0 to 90
     * @param flipResidual degrees from the nearest whole flip: 0 to 180
     * @param slopeError   degrees between the rider's descent and the surface landed on
     * @param forgiveness  the gear's landing forgiveness multiplier; it widens every window
     */
    public static LandingQuality evaluate(double headingError, double flipResidual, double slopeError, double forgiveness) {
        double f = Math.max(0.25, forgiveness);
        for (LandingQuality q : new LandingQuality[]{PERFECT, CLEAN, SKETCHY}) {
            boolean slopeOk = q != PERFECT || slopeError <= PERFECT_SLOPE_WINDOW * f;
            if (headingError <= q.headingWindow * f && flipResidual <= q.flipWindow * f && slopeOk) return q;
        }
        return BAIL;
    }

    public static LandingQuality byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : CLEAN;
    }

    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
