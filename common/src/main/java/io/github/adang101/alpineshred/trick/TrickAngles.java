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

    /**
     * Degrees between two lines in one vertical plane, each given as a direction (along the plane's
     * horizontal axis, then up): 0 to 90, a direction and its reverse being the same line. The slope
     * error of a landing is the angle between the rider's velocity and the surface it lands on.
     */
    public static double lineAngle(double along1, double up1, double along2, double up2) {
        return residual(Math.toDegrees(Math.atan2(up1, along1) - Math.atan2(up2, along2)), 180.0);
    }

    /**
     * The slope error landing on a quarter pipe's arc at {@code theta} (0 at the transition, pi / 2
     * at the vertical) with velocity {@code along} the pipe's facing and {@code vy} up.
     */
    public static double pipeSlopeError(double along, double vy, double theta) {
        return lineAngle(along, vy, Math.cos(theta), Math.sin(theta));
    }
}
