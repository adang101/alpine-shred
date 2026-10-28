package io.github.adang101.alpineshred.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeAndContactTest {
    private static final MovementTuning T = MovementTuning.DEFAULT;

    @Test
    void planeSampleGivesExactGradeAndDownhillDirection() {
        // Heights fall towards +Z (south, yaw 0) at 0.5 per block.
        SlopeMath.Slope s = SlopeMath.fromHeights(0, 10, 9.5, 10.5, 10, 10, 1);
        assertEquals(0.5, s.grade(), 1e-12);
        assertEquals(0, s.downhillYaw(), 1e-9);
        // Same plane sampled while travelling east (yaw -90): downhill is still south.
        SlopeMath.Slope east = SlopeMath.fromHeights(-90, 10, 10, 10, 10.5, 9.5, 1);
        assertEquals(0.5, east.grade(), 1e-12);
        assertEquals(0, east.downhillYaw(), 1e-9);
    }

    @Test
    void missingSamplesFallBackToOneSidedDifferences() {
        SlopeMath.Slope s = SlopeMath.fromHeights(0, 10, Double.NaN, 10.5, 10, 10, 1);
        assertEquals(0.5, s.grade(), 1e-12);
        assertEquals(SlopeMath.Slope.FLAT, SlopeMath.fromHeights(0, Double.NaN, 1, 2, 3, 4, 1));
    }

    @Test
    void filterSmoothsAVoxelStaircaseToItsMeanGrade() {
        double grade = Math.tan(Math.toRadians(20));
        SlopeMath.Filter filter = new SlopeMath.Filter();
        double min = Double.MAX_VALUE, max = 0, sum = 0;
        int n = 0;
        for (double x = 0; x < 200; x += 1.1) {
            double c = -Math.floor(x * grade), a = -Math.floor((x + 1) * grade), b = -Math.floor((x - 1) * grade);
            SlopeMath.Slope s = filter.update(SlopeMath.fromHeights(0, c, a, b, c, c, 1), T.gradeSmoothing);
            if (x > 20) {
                min = Math.min(min, s.grade());
                max = Math.max(max, s.grade());
                sum += s.grade();
                n++;
            }
        }
        assertEquals(grade, sum / n, 0.03);
        assertTrue(max - min < 0.35, "smoothed grade range " + min + ".." + max);
    }

    @Test
    void filterHandlesDownhillDirectionCrossingNorth() {
        SlopeMath.Filter filter = new SlopeMath.Filter();
        filter.update(new SlopeMath.Slope(179, 0.4), 0.3);
        SlopeMath.Slope s = filter.update(new SlopeMath.Slope(-179, 0.4), 0.3);
        assertTrue(Math.abs(Math.abs(s.downhillYaw()) - 180) < 2, "averaged across +-180: " + s.downhillYaw());
        assertEquals(0.4, s.grade(), 0.01);
    }

    @Test
    void groundedRiderWithNoGapStaysGrounded() {
        assertEquals(GroundContact.Kind.GROUNDED, GroundContact.resolve(T, true, false, 0, 0, 0, 1).kind());
    }

    @Test
    void popOrAlreadyAirborneNeverSnaps() {
        assertEquals(GroundContact.Kind.AIRBORNE, GroundContact.resolve(T, true, true, 0, 0.2, 0, 1).kind());
        assertEquals(GroundContact.Kind.AIRBORNE, GroundContact.resolve(T, false, false, -0.3, 0.2, 0, 1).kind());
    }

    @Test
    void rampLaunchBeatsSnapAboveLaunchGradeAndSpeed() {
        GroundContact.Result r = GroundContact.resolve(T, true, false, 0, 0.5, 0.5, 0.8);
        assertEquals(GroundContact.Kind.LAUNCH, r.kind());
        assertEquals(0.9 * 0.8 * 0.5, r.launchVy(), 1e-12);
        // Too slow to launch: snaps instead.
        assertEquals(GroundContact.Kind.SNAP, GroundContact.resolve(T, true, false, 0, 0.5, 0.5, 0.3).kind());
        // Too shallow to launch.
        assertEquals(GroundContact.Kind.SNAP, GroundContact.resolve(T, true, false, 0, 0.5, 0.2, 0.8).kind());
    }

    @Test
    void launchGradeIsCappedSoSingleStepsCannotFling() {
        assertEquals(0.9 * 1.0 * 1.0, GroundContact.launchVelocity(T, 1.0, 3.0), 1e-12);
    }

    @Test
    void snapDistanceGrowsWithSpeedFromTheDocValue() {
        assertEquals(1.0, GroundContact.snapDistance(T, 0), 0);
        assertEquals(2.4, GroundContact.snapDistance(T, 1.4), 1e-12);
        assertEquals(GroundContact.Kind.AIRBORNE, GroundContact.resolve(T, true, false, 0, 3.0, 0, 1.4).kind());
        assertEquals(GroundContact.Kind.AIRBORNE, GroundContact.resolve(T, true, false, 0.1, 0.5, 0, 1.0).kind(),
                "rising riders are never snapped down");
    }

    @Test
    void stepHeightRaisesOnlyOnSnowAtSpeed() {
        assertEquals(1.0, GroundContact.stepHeight(T, true, 0.2), 0);
        assertEquals(0.6, GroundContact.stepHeight(T, true, 0.05), 0);
        assertEquals(0.6, GroundContact.stepHeight(T, false, 1.0), 0);
    }

    @Test
    void ballisticsMatchVanillaJump() {
        // A vanilla jump (0.42) clears about 1.25 blocks.
        assertEquals(1.25, Ballistics.apexHeight(0.42), 0.03);
        int ticks = Ballistics.airTicks(0.42, 0);
        assertTrue(ticks >= 11 && ticks <= 13, "vanilla jump lasts about 12 ticks: " + ticks);
    }
}
