package io.github.adang101.alpineshred.lift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 13's state and persistence: ramps at 0.5 b/s^2, emergency stops at 2.0, catch-up equals stepping. */
class LiftMotionTest {
    private static final double LOOP = 400.0;

    @Test
    void rampsUpAtHalfABlockPerSecondSquared() {
        double speed = 0, travel = 0;
        for (int tick = 1; tick <= 40; tick++) {
            double[] s = LiftMotion.step(speed, travel, 2.5, false, LOOP);
            speed = s[0];
            travel = s[1];
            assertEquals(0.5 * tick / 20.0, speed, 1e-9, "tick " + tick);
        }
        assertEquals(1.0, speed, 1e-9);
        // 2.5 b/s is reached after 5 seconds, and then holds.
        double[] s = LiftMotion.advance(0, 0, 2.5, false, LOOP, 100);
        assertEquals(2.5, s[0], 1e-9);
        assertEquals(2.5, LiftMotion.advance(0, 0, 2.5, false, LOOP, 500)[0], 0.0);
    }

    @Test
    void rampsDownAtHalfABlockPerSecondSquared() {
        double speed = 2.5;
        for (int tick = 1; tick <= 20; tick++) {
            speed = LiftMotion.nextSpeed(speed, 0, false);
            assertEquals(2.5 - 0.5 * tick / 20.0, speed, 1e-9);
        }
        assertEquals(0.0, LiftMotion.advance(2.5, 0, 0, false, LOOP, 110)[0], 0.0);
        assertEquals(0.0, LiftMotion.nextSpeed(0.01, 0, false), 0.0, "does not overshoot the target");
    }

    @Test
    void anEmergencyStopSlowsAtTwoBlocksPerSecondSquared() {
        double speed = 2.5;
        for (int tick = 1; tick <= 25; tick++) {
            speed = LiftMotion.nextSpeed(speed, 0, true);
            assertEquals(Math.max(0, 2.5 - 2.0 * tick / 20.0), speed, 1e-9, "tick " + tick);
        }
        assertEquals(0.0, speed, 1e-9);
        // 2.5 b/s takes 1.25 s = 25 ticks under emergency, and 5 s = 100 ticks otherwise.
        assertEquals(0.0, LiftMotion.advance(2.5, 0, 0, true, LOOP, 25)[0], 1e-9);
        assertTrue(LiftMotion.advance(2.5, 0, 0, false, LOOP, 25)[0] > 1.0);
    }

    @Test
    void advanceEqualsRepeatedSteps() {
        for (long n : new long[]{0, 1, 7, 99, 100, 137, 1000, 5000}) {
            for (double[] start : new double[][]{{0, 0, 2.5}, {1.3, 399.0, 4.0}, {4.0, 12.5, 0}, {2.5, 0, 2.5}}) {
                for (boolean emergency : new boolean[]{false, true}) {
                    double[] stepped = {start[0], start[1]};
                    for (long i = 0; i < n; i++) {
                        stepped = LiftMotion.step(stepped[0], stepped[1], start[2], emergency, LOOP);
                    }
                    double[] jumped = LiftMotion.advance(start[0], start[1], start[2], emergency, LOOP, n);
                    assertEquals(stepped[0], jumped[0], 1e-9, "speed after " + n);
                    assertEquals(stepped[1], jumped[1], 1e-9, "travel after " + n);
                }
            }
        }
    }

    @Test
    void travelWrapsAtTheLoopLength() {
        double[] s = LiftMotion.step(4.0, 399.9, 4.0, false, LOOP);
        assertEquals(399.9 + 0.2 - LOOP, s[1], 1e-9);
        // A long run stays inside [0, loop).
        double[] far = LiftMotion.advance(4.0, 0, 4.0, false, LOOP, 20 * 1000);
        assertTrue(far[1] >= 0 && far[1] < LOOP);
        assertEquals((4.0 * 1000) % LOOP, far[1], 1e-6);
        // Without a loop length nothing wraps.
        assertEquals(0.2, LiftMotion.step(4.0, 0, 4.0, false, 0)[1], 1e-12);
    }

    @Test
    void theAllocationFreeStepIsTheArrayStep() {
        java.util.Random random = new java.util.Random(5);
        for (int n = 0; n < 5000; n++) {
            double speed = random.nextDouble() * 5, travel = random.nextDouble() * LOOP * 1.2 - 3, target = random.nextBoolean() ? 0 : 2.5;
            boolean emergency = random.nextBoolean();
            double loop = random.nextInt(4) == 0 ? 0 : LOOP;
            double[] step = LiftMotion.step(speed, travel, target, emergency, loop);
            double s = LiftMotion.nextSpeed(speed, target, emergency);
            assertEquals(step[0], s, 0.0);
            assertEquals(step[1], LiftMotion.nextTravel(travel, s, loop), 0.0);
        }
    }
}
