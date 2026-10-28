package io.github.adang101.alpineshred.movement;

/**
 * Post-move ground handling (design doc section 7, "Slope sampling and ground snap"): decides,
 * after a tick's move left a gap under the rider, whether to snap down, launch off a ramp, or
 * become airborne.
 *
 * <p>Two clarifications of the doc, both covered by tests:
 * <ul>
 *   <li><b>Launch grade source.</b> The caller supplies the grade. Park ramps report their exact
 *       slope; other terrain uses the steepest per-tick climb over the last two grounded ticks.
 *       Averaging over two ticks would understate a one-block 45 degree kicker (the rider is only
 *       on it for about one tick at speed), and the section 19 apex targets assume the ramp's
 *       true grade. The grade is capped at {@link #MAX_LAUNCH_GRADE} so a single block step
 *       cannot fling a rider.</li>
 *   <li><b>Snap distance.</b> The doc's fixed 1.0 block snap leaves a rider at the 1.4 speed cap
 *       on a 45 degree voxel slope dropping up to 2 blocks in one tick (1.4 of slope plus one
 *       voxel step), which would flicker into {@code AIR}. Snap distance is
 *       {@code snapDown + speed * SNAP_GRADE}: exactly the doc's 1.0 at rest, and enough for any
 *       voxel slope up to 45 degrees at any speed.</li>
 * </ul>
 */
public final class GroundContact {
    /** Steepest slope (rise over run) the snap follows at speed. 1.0 = 45 degrees. */
    public static final double SNAP_GRADE = 1.0;
    /** Launch grade cap. 1.0 = 45 degrees, the steepest park ramp. */
    public static final double MAX_LAUNCH_GRADE = 1.0;

    private GroundContact() {
    }

    public enum Kind {
        /** Ground directly under the rider; nothing to do. */
        GROUNDED,
        /** Move down by {@link Result#snapDistance} and stay on the ground. */
        SNAP,
        /** Leave the ground with vertical velocity {@link Result#launchVy}; enter {@code AIR}. */
        LAUNCH,
        /** No ground close enough; vanilla gravity takes over; enter {@code AIR}. */
        AIRBORNE
    }

    public record Result(Kind kind, double snapDistance, double launchVy) {
        static final Result GROUNDED = new Result(Kind.GROUNDED, 0, 0);
        static final Result AIRBORNE = new Result(Kind.AIRBORNE, 0, 0);
    }

    /**
     * @param wasGrounded    the rider was on the ground last tick
     * @param popped         a pop was released this tick
     * @param verticalVel    current vertical velocity (positive up)
     * @param gapBelow       distance from feet down to the nearest ground, or {@code Double.POSITIVE_INFINITY}
     * @param recentUpGrade  steepest upward grade over the last two grounded ticks (see class doc)
     * @param speed          horizontal speed, blocks per tick
     */
    public static Result resolve(MovementTuning t, boolean wasGrounded, boolean popped, double verticalVel,
                                 double gapBelow, double recentUpGrade, double speed) {
        if (gapBelow <= 1.0e-4) return Result.GROUNDED;
        if (!wasGrounded || popped) return Result.AIRBORNE;
        if (recentUpGrade >= t.launchGrade && speed >= t.launchMinSpeed) {
            return new Result(Kind.LAUNCH, 0, launchVelocity(t, speed, recentUpGrade));
        }
        if (verticalVel <= 0 && gapBelow <= snapDistance(t, speed)) {
            return new Result(Kind.SNAP, gapBelow, 0);
        }
        return Result.AIRBORNE;
    }

    /** {@code snapDown + speed * SNAP_GRADE}. */
    public static double snapDistance(MovementTuning t, double speed) {
        return t.snapDown + Math.max(0, speed) * SNAP_GRADE;
    }

    /** {@code vy = 0.9 * s * grade}, with the grade capped at {@link #MAX_LAUNCH_GRADE}. */
    public static double launchVelocity(MovementTuning t, double speed, double grade) {
        return t.launchVyFactor * speed * Math.min(grade, MAX_LAUNCH_GRADE);
    }

    /** Step height while strapped: raised on snow at speed (section 7, "Step-up"). */
    public static double stepHeight(MovementTuning t, boolean onSnow, double speed) {
        return onSnow && speed >= t.stepUpMinSpeed ? t.stepUp : 0.6;
    }
}
