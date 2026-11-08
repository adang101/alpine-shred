package io.github.adang101.alpineshred.trick;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.movement.MovementTuning;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirRotationTest {
    private static final MovementTuning T = MovementTuning.DEFAULT;

    /** A session whose keys are both seen released on air tick 1 (nothing held at takeoff). */
    private static AirRotation armed() {
        AirRotation r = new AirRotation();
        r.step(T, GearKind.BOARD, 0, 0, false, 1, 0);
        return r;
    }

    private static double step(AirRotation r, int spin, int flip, int airTicks) {
        return r.step(T, GearKind.BOARD, spin, flip, false, airTicks, 0);
    }

    @Test
    void spinRampsToMaxOverFourTicks() {
        AirRotation r = armed();
        double[] expected = {6, 12, 18, 24, 24, 24};
        for (double e : expected) {
            assertEquals(e, step(r, 1, 0, 99), 1e-12);
        }
        assertEquals(108, r.yawAccum, 1e-12);
    }

    @Test
    void skisSpinFasterAndGrabsSlowRotation() {
        AirRotation skis = new AirRotation();
        skis.step(T, GearKind.SKIS, 0, 0, false, 1, 0);
        for (int i = 0; i < 10; i++) skis.step(T, GearKind.SKIS, -1, 0, false, 99, 0);
        assertEquals(-28, skis.spinRate(), 1e-12);
        AirRotation grab = armed();
        for (int i = 0; i < 10; i++) grab.step(T, GearKind.BOARD, 1, 0, true, 99, 0);
        assertEquals(0.8 * (6 + 12 + 18 + 24 * 7), grab.yawAccum, 1e-9);
    }

    @Test
    void reversingRestartsTheRamp() {
        AirRotation r = armed();
        for (int i = 0; i < 5; i++) step(r, 1, 0, 99);
        double held = r.yawAccum;
        step(r, -1, 0, 99);
        assertEquals(held - 6, r.yawAccum, 1e-12);
    }

    @Test
    void flipsUseTheirOwnRate() {
        AirRotation r = armed();
        for (int i = 0; i < 20; i++) step(r, 0, 1, 99);
        assertEquals(6 + 12 + 18 + 24 * 17, r.pitchAccum, 1e-9);
        assertEquals(0, r.yawAccum, 0);
    }

    // ---- takeoff keys are armed by a release ------------------------------------------------------

    @Test
    void keysHeldFromTakeoffDoNothingUntilReleased() {
        AirRotation r = new AirRotation();
        r.reset(true, true);
        for (int i = 1; i <= 20; i++) {
            assertEquals(0, step(r, 1, 1, i), 0);
        }
        assertEquals(0, r.yawAccum, 0);
        assertEquals(0, r.pitchAccum, 0);
        step(r, 0, 0, 21);
        step(r, 1, 1, 22);
        assertEquals(6, r.yawAccum, 1e-12);
        assertEquals(6, r.pitchAccum, 1e-12);
    }

    @Test
    void aKeyUpAtTakeoffAndPressedOnTheFirstAirTickRotatesAtOnce() {
        AirRotation r = new AirRotation();
        r.reset(false, false);
        assertEquals(6, step(r, 1, 0, 1), 1e-12);
        step(r, 0, 1, 2);
        assertEquals(6, r.pitchAccum, 1e-12);
    }

    @Test
    void armingIsPerAxis() {
        AirRotation r = new AirRotation();
        r.reset(false, true); // A/D up at takeoff, W held from takeoff
        step(r, 0, 1, 1);
        step(r, 1, 1, 2);
        assertEquals(6, r.yawAccum, 1e-12, "the spin axis was armed");
        assertEquals(0, r.pitchAccum, 0, "the flip axis was not");
    }

    @Test
    void resetSeedsTheSessionFromTheTakeoffKeys() {
        AirRotation r = armed();
        step(r, 1, 1, 2);
        r.reset(true, true);
        step(r, 1, 1, 1);
        assertEquals(0, r.yawAccum, 0);
        assertEquals(0, r.pitchAccum, 0);
    }

    // ---- spotting the landing ---------------------------------------------------------------------

    @Test
    void releasedFlipEasesToTheNearestWholeFlipWithoutOvershoot() {
        for (double start : new double[]{-350, -200, -170, -20, 20, 100, 170, 200, 350, 500}) {
            AirRotation r = armed();
            r.pitchAccum = start;
            double target = Math.rint(start / 360.0) * 360.0;
            double previous = Math.abs(target - start);
            for (int i = 0; i < 100; i++) {
                step(r, 0, 0, 200);
                double distance = Math.abs(target - r.pitchAccum);
                assertTrue(distance <= previous + 1e-12, "moved away from " + target + " starting at " + start);
                assertTrue(previous - distance <= T.airFlipRecover + 1e-12, "faster than the recovery rate");
                previous = distance;
            }
            assertEquals(target, r.pitchAccum, 1e-9, "start " + start);
        }
    }

    @Test
    void releasedSpinEasesToForwardOrSwitchWithoutOvershoot() {
        for (double offset : new double[]{0, 60, 100, 179, -100, 250}) {
            for (double start : new double[]{-130, -85, -20, 20, 85, 130, 200}) {
                AirRotation r = armed();
                r.yawAccum = start;
                double heading = offset + start;
                double target = Math.rint(heading / 180.0) * 180.0;
                double previous = Math.abs(target - heading);
                for (int i = 0; i < 100; i++) {
                    double turned = r.step(T, GearKind.BOARD, 0, 0, false, 200, offset);
                    double distance = Math.abs(target - (offset + r.yawAccum));
                    assertTrue(distance <= previous + 1e-12, "overshot, offset " + offset + " start " + start);
                    assertEquals(previous - distance, Math.abs(turned), 1e-9, "the board turns by the yaw change");
                    previous = distance;
                }
                assertEquals(target, offset + r.yawAccum, 1e-9, "offset " + offset + " start " + start);
            }
        }
    }

    @Test
    void recoveryIsSlowerThanAHeldKey() {
        AirRotation r = armed();
        r.yawAccum = 90.5;
        assertEquals(T.airSpinRecover, Math.abs(r.step(T, GearKind.BOARD, 0, 0, false, 99, 0)), 1e-12);
        assertTrue(T.airSpinRecover < T.airSpinMaxBoard && T.airFlipRecover < T.airFlipMax);
    }

    @Test
    void aHeldKeyBeatsRecoveryOnItsOwnAxisOnly() {
        AirRotation r = armed();
        r.yawAccum = 100;
        r.pitchAccum = 100;
        step(r, 1, 0, 99);
        assertEquals(106, r.yawAccum, 1e-12, "no recovery while spinning");
        assertEquals(100 - T.airFlipRecover, r.pitchAccum, 1e-12, "the flip axis still levels out");
    }

    @Test
    void releasingNeverSnapsTheRider() {
        AirRotation r = armed();
        for (int i = 0; i < 6; i++) step(r, 1, 1, 99);
        double yaw = r.yawAccum, pitch = r.pitchAccum;
        double turned = step(r, 0, 0, 99);
        assertEquals(yaw + turned, r.yawAccum, 1e-12);
        assertTrue(Math.abs(turned) <= T.airSpinRecover + 1e-12);
        assertTrue(Math.abs(r.pitchAccum - pitch) <= T.airFlipRecover + 1e-12);
    }

    @Test
    void anUnreachableWholeFlipIsNotRecoveredTo() {
        // 200 degrees with a limit of 300: the nearest whole flip (360) is not allowed yet, so settle to 0.
        assertEquals(-8, AirRotation.recoverPitch(200, 8, 300), 0);
        assertEquals(8, AirRotation.recoverPitch(-200, 8, 300), 0);
        // With the whole flip reachable the same angle goes up to it.
        assertEquals(8, AirRotation.recoverPitch(200, 8, 1000), 0);
    }

    @Test
    void recoveryKeepsPitchWithinTheFlipLimitEveryTick() {
        AirRotation r = armed();
        for (int tick = 2; tick < 60; tick++) {
            boolean press = tick < 14;
            step(r, 0, press ? 1 : 0, tick);
            assertTrue(FlipLimit.allows(r.pitchAccum, tick, T.airFlipMax), "tick " + tick + " pitch " + r.pitchAccum);
        }
    }

    @Test
    void grabbingSlowsRecoveryLikeAnyRotation() {
        AirRotation r = armed();
        r.yawAccum = 90.5;
        double turned = r.step(T, GearKind.BOARD, 0, 0, true, 99, 0);
        assertEquals(T.airSpinRecover * T.airGrabRotationMul, Math.abs(turned), 1e-12);
    }

    @Test
    void recoveryRateNeverExceedsTheHandRate() {
        MovementTuning wild = MovementTuning.fromJson(com.google.gson.JsonParser.parseString(
                "{\"airSpinRecover\": 90, \"airFlipRecover\": 90, \"airSpinMaxBoard\": 3, \"airFlipMax\": 2}").getAsJsonObject(), w -> { });
        AirRotation r = new AirRotation();
        r.step(wild, GearKind.BOARD, 0, 0, false, 1, 0);
        r.yawAccum = 90.5;
        r.pitchAccum = 180;
        assertEquals(3, Math.abs(r.step(wild, GearKind.BOARD, 0, 0, false, 99, 0)), 1e-12);
        assertEquals(178, r.pitchAccum, 1e-12);
    }

    /** Spin, flip and recovery in every pattern of press and release: the honest report always passes the server's validator. */
    @Test
    void anHonestReportWithRecoveryPassesTheServerValidator() {
        for (GearKind kind : GearKind.values()) {
            double spinMax = kind == GearKind.SKIS ? T.airSpinMaxSkis : T.airSpinMaxBoard;
            for (double takeoffRelative : new double[]{0, 90, 180}) {
                for (int air = 2; air <= 40; air++) {
                    for (int release = 1; release <= air; release++) {
                        for (int dir = -1; dir <= 1; dir += 2) {
                            AirRotation r = new AirRotation();
                            for (int tick = 1; tick <= air; tick++) {
                                int key = tick > 1 && tick <= release ? dir : 0;
                                r.step(T, kind, key, -key, tick % 7 == 0, tick, takeoffRelative);
                            }
                            AirReport report = new AirReport(r.yawAccum, r.pitchAccum, new int[GrabType.values().length], 0, air,
                                    takeoffRelative, TrickAngles.headingError(takeoffRelative, r.yawAccum),
                                    TrickAngles.residual(r.pitchAccum, 360), false, false, false);
                            String why = TrickValidator.check(report, air, 0, spinMax, T.airFlipMax);
                            assertEquals(null, why, why);
                        }
                    }
                }
            }
        }
    }
}
