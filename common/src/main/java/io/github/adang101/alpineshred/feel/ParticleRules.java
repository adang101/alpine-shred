package io.github.adang101.alpineshred.feel;

import io.github.adang101.alpineshred.movement.RideState;

/**
 * How many particles a rider throws (design doc section 17, "Particles"). Rates are per tick and
 * fractional; {@link Accumulator} turns them into whole particles without bias.
 */
public final class ParticleRules {
    private ParticleRules() {
    }

    /** Snow spray: about {@code 2 * |turn| * speed} per tick while carving, plus a skid while braking. */
    public static double sprayPerTick(RideState state, double speed, double lean) {
        return switch (state) {
            case CARVE -> 2.0 * Math.abs(lean) * speed * 2.5;
            case BRAKE -> 3.0 * speed;
            default -> 0;
        };
    }

    /**
     * Rail sparks per tick while grinding metal or copper (section 17: 1 to 2 per tick), rising
     * with speed and while pressing; none from wood, plastic, stone or concrete.
     */
    public static double sparksPerTick(boolean metalRail, double speed, boolean pressing) {
        if (!metalRail || speed < 0.1) return 0;
        return Math.min(2.0, 1.0 + speed * 1.5 + (pressing ? 0.5 : 0));
    }

    /** The catch cue: a burst of sparks on a metal rail. */
    public static final int CATCH_SPARKS = 8;

    /** Box slides: block dust from the box, one every few ticks at speed. */
    public static double boxDustPerTick(double speed) {
        return speed < 0.1 ? 0 : Math.min(1.0, speed * 1.2);
    }

    /** Landing burst: 6 to 20 by impact speed (downward velocity, blocks per tick). */
    public static int poofCount(double impactVelocity) {
        double t = Math.max(0, Math.min(1, (-impactVelocity - 0.2) / 0.8));
        return (int) Math.round(6 + 14 * t);
    }

    /** Carve trails: one mark per tick while carving at speed. */
    public static boolean leavesTrail(RideState state, double speed, double lean) {
        return state == RideState.CARVE && speed > 0.2 && Math.abs(lean) > 0.2;
    }

    /** Fractional rate to whole counts, carrying the remainder. */
    public static final class Accumulator {
        private double carry;

        public int take(double rate) {
            carry += Math.max(0, rate);
            int whole = (int) (carry + 1.0e-9); // so 100 x 0.37 makes 37, not 36
            carry -= whole;
            return whole;
        }
    }
}
