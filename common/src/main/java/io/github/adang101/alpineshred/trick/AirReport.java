package io.github.adang101.alpineshred.trick;

import java.util.Arrays;

/**
 * What the client says happened in one air session ({@code c2s/air_report}, design doc sections 8
 * and 15), sent once on landing.
 *
 * @param yawAccum        signed degrees spun
 * @param pitchAccum      signed degrees flipped (positive: front)
 * @param grabTicks       ticks each grab was held, indexed by {@link GrabType} ordinal
 * @param apex            highest point of the air above the landing point, blocks
 * @param airTicks        ticks in the air
 * @param takeoffRelative the board's angle off the direction of travel at takeoff, degrees
 * @param headingError    the board's angle off the direction of travel at landing, folded so a
 *                        switch landing is aligned: 0 to 90
 * @param flipResidual    degrees from the nearest whole flip at landing
 * @param switchLanding   landed riding backward
 * @param fromRail        the session began off a rail or box (spin out, section 8)
 * @param endsOnRail      the session ended on a rail or box, not on the ground (spin in)
 */
public record AirReport(double yawAccum, double pitchAccum, int[] grabTicks, double apex, int airTicks,
                        double takeoffRelative, double headingError, double flipResidual, boolean switchLanding,
                        boolean fromRail, boolean endsOnRail) {
    public AirReport {
        grabTicks = Arrays.copyOf(grabTicks == null ? new int[0] : grabTicks, GrabType.values().length);
    }

    public int grab(GrabType type) {
        return grabTicks[type.ordinal()];
    }

    @Override
    public int[] grabTicks() {
        return grabTicks.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AirReport r && Double.compare(yawAccum, r.yawAccum) == 0 && Double.compare(pitchAccum, r.pitchAccum) == 0
                && Arrays.equals(grabTicks, r.grabTicks) && Double.compare(apex, r.apex) == 0 && airTicks == r.airTicks
                && Double.compare(takeoffRelative, r.takeoffRelative) == 0 && Double.compare(headingError, r.headingError) == 0
                && Double.compare(flipResidual, r.flipResidual) == 0 && switchLanding == r.switchLanding && fromRail == r.fromRail
                && endsOnRail == r.endsOnRail;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(yawAccum) * 31 + Arrays.hashCode(grabTicks) + airTicks;
    }
}
