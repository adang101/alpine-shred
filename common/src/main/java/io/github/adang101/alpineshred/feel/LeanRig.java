package io.github.adang101.alpineshred.feel;

import io.github.adang101.alpineshred.movement.RideMode;

/**
 * The procedural lean layer (design doc section 9, "Layers"): the whole body tilts into the turn
 * and the torso a little further (18 degrees in all at full lean, the legs and board taking the
 * first 10, so they "counter-tilt" relative to the torso), and the upper body sinks with speed and
 * while charging a pop.
 *
 * <h2>Frames</h2>
 * Root ("body") rotation is applied by PlayerAnimator after the body yaw, about a pivot
 * {@value #PIVOT} blocks above the feet, in a frame where +y is up, +x is the rider's right and the
 * rider faces -z. Positive pitch leans back; positive roll tilts left. The root offset keeps the feet
 * (and the board drawn under them) where they were. Part angles are model space, where positive
 * torso pitch leans forward and the torso pivots at the neck.
 *
 * <p>A snowboarder faces sideways (body yaw is the board heading plus 90 degrees regular, minus 90
 * goofy), so a turn tilts them toward the toe or heel edge: forward or back. A skier faces the
 * direction of travel, so a turn tilts them sideways.
 */
public final class LeanRig {
    public static final double PIVOT = 0.7;
    public static final double ROOT_TILT = Math.toRadians(10);
    public static final double TORSO_TILT = Math.toRadians(8);
    /** Model pixels the upper body sinks at full speed, and at full pop charge. */
    public static final double SPEED_CROUCH = 1.5;
    public static final double CHARGE_CROUCH = 2.5;

    /** Angles in radians, offsets in blocks (root) and model pixels (crouch). */
    public record Pose(double rootPitch, double rootRoll, double rootX, double rootY, double rootZ,
                       double torsoPitch, double torsoRoll, double crouch) {
        public static final Pose NEUTRAL = new Pose(0, 0, 0, 0, 0, 0, 0, 0);

        public static Pose lerp(Pose a, Pose b, double t) {
            return new Pose(mix(a.rootPitch, b.rootPitch, t), mix(a.rootRoll, b.rootRoll, t), mix(a.rootX, b.rootX, t),
                    mix(a.rootY, b.rootY, t), mix(a.rootZ, b.rootZ, t), mix(a.torsoPitch, b.torsoPitch, t),
                    mix(a.torsoRoll, b.torsoRoll, t), mix(a.crouch, b.crouch, t));
        }

        private static double mix(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }

    private LeanRig() {
    }

    /**
     * @param lean   -1 to 1, positive toward a right turn (relative to travel)
     * @param switchStance travelling tail first, which swaps the edges a turn puts the rider on
     * @param charge 0 to 1
     */
    public static Pose pose(RideMode mode, boolean goofy, boolean switchStance, double lean, double speed,
                            double charge, boolean grounded) {
        if (mode == RideMode.NONE) return Pose.NEUTRAL;
        double l = clamp(lean, -1, 1);
        double crouch = grounded
                ? SPEED_CROUCH * clamp(speed / 1.4, 0, 1) + CHARGE_CROUCH * clamp(charge, 0, 1)
                : 1.0;
        double rootPitch = 0, rootRoll = 0, torsoPitch = Math.toRadians(3) * crouch / 2, torsoRoll = 0;
        if (mode == RideMode.BOARD) {
            // Regular riders face the right of travel: a right turn is a toe-side turn (lean forward).
            double toe = l * (goofy ? -1 : 1) * (switchStance ? -1 : 1);
            rootPitch = -toe * ROOT_TILT;
            torsoPitch += toe * TORSO_TILT;
        } else {
            rootRoll = -l * ROOT_TILT;
            torsoRoll = -l * TORSO_TILT * 0.75; // the torso pivots at the neck: keep the hip swing small
        }
        // Keep the feet planted: undo the pivot's swing of the point under it.
        double x = -PIVOT * Math.sin(rootRoll);
        double y = -PIVOT * (1 - Math.cos(rootPitch)) - PIVOT * (1 - Math.cos(rootRoll));
        double z = PIVOT * Math.sin(rootPitch);
        return new Pose(rootPitch, rootRoll, x, y, z, torsoPitch, torsoRoll, crouch);
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
