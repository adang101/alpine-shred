package io.github.adang101.alpineshred.trick;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.movement.MovementTuning;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * README clarification 101: what a player does on a real air, with the air lengths measured in the
 * diagnosis (an uncharged pop 12 ticks, a charged pop 15 to 18, a 16 blocks/s park kicker 10 to 19,
 * a 28 blocks/s natural feature 31). Rotation is driven by {@link AirRotation} alone, so the
 * scenarios hold on the client and, through the shared {@link FlipLimit}, on the server.
 */
class AirTrickScenarioTest {
    private static final MovementTuning T = MovementTuning.DEFAULT;

    /** How a flown session ended: the angles, the landing it grades as, and the validator's verdict. */
    private record Flown(double yaw, double pitch, LandingQuality quality, String rejected) {
    }

    /**
     * One air session of {@code air} ticks. {@code spin}/{@code flip} give the key (-1, 0, 1) on tick 1..air;
     * {@code held} marks keys down at tick 1 as held from the ground, otherwise they are fresh at takeoff.
     */
    private static Flown fly(GearKind kind, int air, boolean held, IntUnaryOperator spin, IntUnaryOperator flip) {
        AirRotation r = new AirRotation();
        r.reset(held && spin.applyAsInt(1) != 0, held && flip.applyAsInt(1) != 0);
        for (int tick = 1; tick <= air; tick++) {
            r.step(T, kind, spin.applyAsInt(tick), flip.applyAsInt(tick), false, tick, 0);
        }
        double heading = TrickAngles.headingError(0, r.yawAccum);
        double pitch = r.reportPitch(T, air);
        double residual = TrickAngles.residual(pitch, 360);
        AirReport report = new AirReport(r.yawAccum, pitch, new int[GrabType.values().length], 0, air, 0, heading, residual,
                false, false, false);
        double spinMax = kind == GearKind.SKIS ? T.airSpinMaxSkis : T.airSpinMaxBoard;
        return new Flown(r.yawAccum, pitch, LandingQuality.evaluate(heading, residual, 0, 1.0),
                TrickValidator.check(report, air, 0, spinMax, T.airFlipMax));
    }

    private static IntUnaryOperator window(int from, int to, int key) {
        return tick -> tick >= from && tick <= to ? key : 0;
    }

    private static final IntUnaryOperator NONE = tick -> 0;

    /** Lands without a sketchy grade or a bail, and the server accepts the report. */
    private static void assertLands(Flown f, String what) {
        assertTrue(f.quality == LandingQuality.CLEAN || f.quality == LandingQuality.PERFECT, what + ": " + f);
        assertNull(f.rejected, what);
    }

    // ---- finishing a trick ------------------------------------------------------------------------

    @Test
    void aReleasedFlipPastTheCommitPointFinishesInTheDirectionItWasTurning() {
        AirRotation r = new AirRotation();
        r.reset(false, false);
        for (int tick = 1; tick <= 4; tick++) r.step(T, GearKind.BOARD, 0, 1, false, 30, 0); // 8 + 16 + 24 + 24 = 72
        r.step(T, GearKind.BOARD, 0, 0, false, 30, 0);
        assertTrue(r.pitchAccum < 72, "72 of 360 is short of the commit point and levels back out: " + r.pitchAccum);

        AirRotation q = new AirRotation();
        q.reset(false, false);
        for (int tick = 1; tick <= 7; tick++) q.step(T, GearKind.BOARD, 0, 1, false, 30, 0); // 8 + 16 + 24 * 5 = 144
        double before = q.pitchAccum;
        assertTrue(before > 0.35 * 360, "past the commit point: " + before);
        q.step(T, GearKind.BOARD, 0, 0, false, 30, 0);
        assertTrue(q.pitchAccum > before, "finishing the flip, not undoing it: " + q.pitchAccum);
        for (int i = 0; i < 20; i++) q.step(T, GearKind.BOARD, 0, 0, false, 30, 0);
        assertEquals(360, q.pitchAccum, 1e-9);
    }

    @Test
    void theCommitPointFollowsTheDirectionAndBiasZeroIsTheNearest() {
        assertEquals(360, AirRotation.settle(130, 360, 1, 0.15), 0);
        assertEquals(0, AirRotation.settle(120, 360, 1, 0.15), 0);
        assertEquals(-360, AirRotation.settle(-130, 360, -1, 0.15), 0);
        assertEquals(0, AirRotation.settle(-120, 360, -1, 0.15), 0);
        assertEquals(0, AirRotation.settle(130, 360, 1, 0.0), 0);
        assertEquals(360, AirRotation.settle(200, 360, 0, 0.15), 0, "no direction: the nearest");
        assertEquals(180, AirRotation.settle(70, 180, 1, 0.15), 0);
        assertEquals(0, AirRotation.settle(60, 180, 1, 0.15), 0);
    }

    // ---- (B): a deliberate trick is easy to start and forgiving to finish -------------------------

    @Test
    void aOneEightyOnAnUnchargedPopNeedsOnlyARoughHold() {
        for (int dir = -1; dir <= 1; dir += 2) {
            for (int from = 1; from <= 3; from++) {
                for (int hold = 6; hold <= 9; hold++) {
                    Flown f = fly(GearKind.BOARD, 12, false, window(from, from + hold - 1, dir), NONE);
                    assertLands(f, "180 dir " + dir + " from " + from + " hold " + hold);
                    assertEquals(180, Math.abs(f.yaw), 1e-6, "dir " + dir + " from " + from + " hold " + hold);
                }
            }
        }
    }

    @Test
    void aThreeSixtyNeedsABigKickerAndSloppyTimingStillLands() {
        // 19 air ticks (a 16 blocks/s kicker at full grade).
        for (int from = 1; from <= 2; from++) {
            for (int hold = 14; hold <= 17; hold++) {
                Flown f = fly(GearKind.BOARD, 19, false, window(from, from + hold - 1, 1), NONE);
                assertLands(f, "360 from " + from + " hold " + hold);
                assertEquals(360, f.yaw, 30, "from " + from + " hold " + hold);
            }
        }
        Flown pop = fly(GearKind.BOARD, 12, false, window(1, 12, 1), NONE);
        assertTrue(Math.abs(pop.yaw) < 300, "a 12 tick pop is not a 360: " + pop);
    }

    @Test
    void aBackflipNeedsAKickerAndATapOfSFinishesIt() {
        for (int from = 1; from <= 3; from++) {
            for (int hold = 8; hold <= 14; hold++) {
                Flown f = fly(GearKind.BOARD, 19, false, NONE, window(from, from + hold - 1, -1));
                assertLands(f, "backflip from " + from + " hold " + hold);
                assertEquals(-360, f.pitch, 1e-6, "from " + from + " hold " + hold);
            }
        }
        assertLands(fly(GearKind.BOARD, 18, false, NONE, window(1, 9, 1)), "front flip, 18 ticks");
        Flown pop = fly(GearKind.BOARD, 12, false, NONE, window(1, 12, 1));
        assertTrue(Math.abs(pop.pitch) < 330, "no whole flip on a 12 tick pop: " + pop);
    }

    @Test
    void aShortTapLevelsOutSoNoAccidentalTrickLands() {
        for (int air = 6; air <= 20; air++) {
            for (int hold = 1; hold <= 3; hold++) {
                assertLands(fly(GearKind.BOARD, air, false, window(1, hold, 1), window(1, hold, 1)), "tap " + hold + " on " + air);
            }
        }
    }

    // ---- (A): unintended rotation never happens ---------------------------------------------------

    @Test
    void keysHeldFromTheGroundThroughAnyAirNeverRotate() {
        for (int air = 1; air <= 40; air++) {
            for (GearKind kind : GearKind.values()) {
                Flown f = fly(kind, air, true, tick -> 1, tick -> 1);
                assertEquals(0, f.yaw, 0, "air " + air);
                assertEquals(0, f.pitch, 0, "air " + air);
                assertLands(f, "held through " + air);
            }
        }
    }

    @Test
    void noInputLandsCleanOnAnyAir() {
        for (int air = 1; air <= 40; air++) {
            Flown f = fly(GearKind.BOARD, air, false, NONE, NONE);
            assertEquals(0, f.yaw, 0);
            assertEquals(0, f.pitch, 0);
            assertLands(f, "no input " + air);
        }
    }

    // ---- every reachable outcome is one the server accepts ----------------------------------------

    @Test
    void randomInputSequencesAlwaysPassTheFlipLimitAndTheValidator() {
        Random random = new Random(101L);
        for (int run = 0; run < 20000; run++) {
            GearKind kind = random.nextBoolean() ? GearKind.BOARD : GearKind.SKIS;
            int air = 1 + random.nextInt(45);
            double takeoffRelative = new double[]{0, 180, 0, random.nextDouble() * 80 - 40}[random.nextInt(4)];
            AirRotation r = new AirRotation();
            r.reset(random.nextBoolean(), random.nextBoolean());
            int spin = 0;
            int flip = 0;
            for (int tick = 1; tick <= air; tick++) {
                // Keys change now and then, like a hand: hold, release, reverse.
                if (random.nextInt(4) == 0) spin = random.nextInt(3) - 1;
                if (random.nextInt(4) == 0) flip = random.nextInt(3) - 1;
                r.step(T, kind, spin, flip, random.nextInt(5) == 0, tick, takeoffRelative);
                assertTrue(FlipLimit.allows(r.reportPitch(T, tick), tick, T.airFlipMax), "run " + run + " tick " + tick);
            }
            double pitch = r.reportPitch(T, air);
            double heading = TrickAngles.headingError(takeoffRelative, r.yawAccum);
            AirReport report = new AirReport(r.yawAccum, pitch, new int[GrabType.values().length], 0, air, takeoffRelative,
                    heading, TrickAngles.residual(pitch, 360), false, false, false);
            double spinMax = kind == GearKind.SKIS ? T.airSpinMaxSkis : T.airSpinMaxBoard;
            assertNull(TrickValidator.check(report, air, 0, spinMax, T.airFlipMax), "run " + run);
        }
    }
}
