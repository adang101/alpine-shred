package io.github.adang101.alpineshred.trick;

/** Angle helpers for tricks: half-turns, whole flips, and what is left over (section 8). */
public final class TrickAngles {
    private TrickAngles() {
    }

    /** Whole half-turns in a spin, to the nearest: 170 degrees is a 180, 260 is a 180, 280 a 360. */
    public static int halfTurns(double yawAccum) {
        return (int) Math.round(Math.abs(yawAccum) / 180.0);
    }

    /** Whole flips, to the nearest. */
    public static int flips(double pitchAccum) {
        return (int) Math.round(Math.abs(pitchAccum) / 360.0);
    }

    /** Degrees from the nearest multiple of {@code period}: 0 to period / 2. */
    public static double residual(double angle, double period) {
        double r = Math.abs(angle) % period;
        return Math.min(r, period - r);
    }

    /**
     * The landing heading error for a spin of {@code yawAccum} from a takeoff where the board was
     * {@code takeoffRelative} degrees off the direction of travel: a switch landing counts as aligned.
     */
    public static double headingError(double takeoffRelative, double yawAccum) {
        return residual(takeoffRelative + yawAccum, 180.0);
    }
}
