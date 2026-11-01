package io.github.adang101.alpineshred.lift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EjectCrossingTest {
    private static final double LOOP = 100;

    @Test
    void crossingTheEjectionPointIsDetected() {
        assertTrue(EjectCrossing.crossed(39.9, 40.1, 40, LOOP));
        assertTrue(EjectCrossing.crossed(39.9, 40.0, 40, LOOP)); // landing exactly on it counts
    }

    @Test
    void notYetReachedOrAlreadyPassedIsNotACrossing() {
        assertFalse(EjectCrossing.crossed(39.0, 39.9, 40, LOOP));
        assertFalse(EjectCrossing.crossed(40.0, 40.2, 40, LOOP)); // it was crossed on the previous tick
        assertFalse(EjectCrossing.crossed(41.0, 41.2, 40, LOOP));
    }

    @Test
    void aStandingChairNeverCrosses() {
        assertFalse(EjectCrossing.crossed(40, 40, 40, LOOP));
    }

    @Test
    void wrapAroundTheLoopIsHandled() {
        // Ejection point just after the loop's start.
        assertTrue(EjectCrossing.crossed(99.9, 0.1, 0.05, LOOP));
        assertFalse(EjectCrossing.crossed(99.9, 0.1, 0.5, LOOP));
        // The chair wraps, the point is just before the wrap.
        assertTrue(EjectCrossing.crossed(99.5, 0.2, 99.9, LOOP));
        assertFalse(EjectCrossing.crossed(99.95, 0.2, 99.9, LOOP));
    }

    @Test
    void backwardsOrJumpingMotionIsNotACrossing() {
        assertFalse(EjectCrossing.crossed(40.1, 39.9, 40, LOOP));
        assertFalse(EjectCrossing.crossed(30, 50, 40, LOOP)); // a 20 block jump is not motion
        assertFalse(EjectCrossing.crossed(Double.NaN, 40.1, 40, LOOP)); // the first tick
    }

    @Test
    void theZoneAfterTheEjectionPointEjectsEvenWithoutACrossing() {
        // Eject at 40, return cable starts at 60.
        assertTrue(EjectCrossing.shouldEject(30, 50, 40, 60, LOOP)); // jumped over it
        assertTrue(EjectCrossing.shouldEject(Double.NaN, 45, 40, 60, LOOP)); // boarded past it
        assertFalse(EjectCrossing.shouldEject(Double.NaN, 39, 40, 60, LOOP));
        assertFalse(EjectCrossing.shouldEject(70, 70.1, 40, 60, LOOP)); // on the return cable
    }

    @Test
    void theZoneWrapsToo() {
        // Eject at 95, return cable at 10 (past the wrap).
        assertTrue(EjectCrossing.pastEject(99, 95, 10, LOOP));
        assertTrue(EjectCrossing.pastEject(5, 95, 10, LOOP));
        assertFalse(EjectCrossing.pastEject(50, 95, 10, LOOP));
        assertFalse(EjectCrossing.pastEject(94, 95, 10, LOOP));
    }
}
