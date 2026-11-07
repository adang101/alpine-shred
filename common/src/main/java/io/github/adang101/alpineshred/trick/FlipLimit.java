package io.github.adang101.alpineshred.trick;

/**
 * The most a rider may flip in an air session of a given length. One rule, used by the server's
 * {@link TrickValidator} to reject a report and by the client's {@link AirRotation} to stop turning,
 * so an honest client can never send a flip the server rejects (README clarification 82). It holds
 * two bounds: the flip rate (with the validator's slack), and whole flips needing 16 ticks each,
 * where {@link TrickAngles#flips} rounds to the nearest, so a flip past 180 degrees already counts as one.
 */
public final class FlipLimit {
    /** Air ticks one whole flip needs. */
    public static final int TICKS_PER_FLIP = 16;
    private static final double RATE_SLACK_FACTOR = 1.1;
    private static final double RATE_SLACK_DEGREES = 30;

    private FlipLimit() {
    }

    /** The largest |pitch| in degrees that passes both bounds after {@code airTicks} of air. */
    public static double maxPitch(int airTicks, double flipMax) {
        double byRate = airTicks * flipMax * RATE_SLACK_FACTOR + RATE_SLACK_DEGREES;
        // flips(p) <= k  <=>  |p| < (k + 0.5) * 360
        double byCount = Math.nextDown((airTicks / TICKS_PER_FLIP + 0.5) * 360.0);
        return Math.min(byRate, byCount);
    }

    /** Whether a report flipping {@code pitchAccum} degrees in {@code airTicks} of air is within the limit. */
    public static boolean allows(double pitchAccum, int airTicks, double flipMax) {
        return Math.abs(pitchAccum) <= maxPitch(airTicks, flipMax);
    }
}
