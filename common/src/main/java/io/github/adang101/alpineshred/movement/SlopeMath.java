package io.github.adang101.alpineshred.movement;

/**
 * Slope estimate from the five-point ground sample (design doc section 7): center, one block
 * ahead and behind along travel, one block left and right. The world-side sampler casts down from
 * 1.5 blocks above the feet and reads collision-shape tops; this class turns those heights into
 * a downhill direction and grade, and smooths it so voxel staircases do not jitter.
 */
public final class SlopeMath {
    /** Each sample casts from this far above the feet... */
    public static final double CAST_UP = 1.5;
    /** ...down to this far below them. Ground outside the window is a missing sample (NaN). */
    public static final double CAST_DOWN = 2.5;

    private SlopeMath() {
    }

    /** A collision-top height as the downward cast would report it, or NaN if out of reach. */
    public static double castSample(double feetY, double groundTop) {
        return groundTop <= feetY + CAST_UP && groundTop >= feetY - CAST_DOWN ? groundTop : Double.NaN;
    }

    /**
     * Downhill direction and grade (rise over run, positive). {@link #angle()} is the slope angle.
     */
    public record Slope(double downhillYaw, double grade) {
        public static final Slope FLAT = new Slope(0, 0);

        public double angle() {
            return Math.atan(grade);
        }

        public double degrees() {
            return Math.toDegrees(angle());
        }
    }

    /**
     * Central differences where both neighbours exist, one-sided otherwise. Missing samples are
     * {@code NaN} (no ground found within the cast).
     *
     * @param yaw     travel yaw the ahead/behind axis is aligned with
     * @param spacing distance from center to each outer sample, in blocks
     */
    public static Slope fromHeights(double yaw, double center, double ahead, double behind,
                                    double left, double right, double spacing) {
        if (Double.isNaN(center)) return Slope.FLAT;
        double dForward = derivative(center, ahead, behind, spacing);
        double dRight = derivative(center, right, left, spacing);
        // Forward axis f = dir(yaw); right axis r = dir(yaw + 90) (turning right increases yaw).
        double fx = SlideModel.dirX(yaw), fz = SlideModel.dirZ(yaw);
        double rx = SlideModel.dirX(yaw + 90), rz = SlideModel.dirZ(yaw + 90);
        double gx = dForward * fx + dRight * rx;
        double gz = dForward * fz + dRight * rz;
        double grade = Math.hypot(gx, gz);
        if (grade < 1.0e-9) return Slope.FLAT;
        return new Slope(SlideModel.yawOf(-gx, -gz), grade);
    }

    private static double derivative(double center, double plus, double minus, double spacing) {
        boolean hasPlus = !Double.isNaN(plus), hasMinus = !Double.isNaN(minus);
        if (hasPlus && hasMinus) return (plus - minus) / (2 * spacing);
        if (hasPlus) return (plus - center) / spacing;
        if (hasMinus) return (center - minus) / spacing;
        return 0;
    }

    /**
     * Exponential smoothing of the height gradient (alpha 0.3 by default). Smoothing the gradient
     * vector rather than the angle avoids wrap-around jumps when the downhill direction crosses
     * north.
     */
    public static final class Filter {
        private double gx, gz;
        private boolean primed;

        public Slope update(Slope raw, double alpha) {
            double rx = -SlideModel.dirX(raw.downhillYaw()) * raw.grade();
            double rz = -SlideModel.dirZ(raw.downhillYaw()) * raw.grade();
            if (!primed) {
                gx = rx;
                gz = rz;
                primed = true;
            } else {
                gx += alpha * (rx - gx);
                gz += alpha * (rz - gz);
            }
            return current();
        }

        public Slope current() {
            double grade = Math.hypot(gx, gz);
            if (grade < 1.0e-9) return Slope.FLAT;
            return new Slope(SlideModel.yawOf(-gx, -gz), grade);
        }

        public void reset() {
            gx = gz = 0;
            primed = false;
        }
    }
}
