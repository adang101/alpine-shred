package io.github.adang101.alpineshred.trick;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.movement.MovementTuning;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AirRotationTest {
    private static final MovementTuning T = MovementTuning.DEFAULT;

    @Test
    void spinRampsToMaxOverFourTicks() {
        AirRotation r = new AirRotation();
        double[] expected = {6, 12, 18, 24, 24, 24};
        for (double e : expected) {
            assertEquals(e, r.step(T, GearKind.BOARD, 1, 0, false), 1e-12);
        }
        assertEquals(108, r.yawAccum, 1e-12);
    }

    @Test
    void skisSpinFasterAndGrabsSlowRotation() {
        AirRotation skis = new AirRotation();
        for (int i = 0; i < 10; i++) skis.step(T, GearKind.SKIS, -1, 0, false);
        assertEquals(-28, skis.spinRate(), 1e-12);
        AirRotation grab = new AirRotation();
        for (int i = 0; i < 10; i++) grab.step(T, GearKind.BOARD, 1, 0, true);
        assertEquals(0.8 * (6 + 12 + 18 + 24 * 7), grab.yawAccum, 1e-9);
    }

    @Test
    void releasingHoldsTheAngleAndReversingRestartsTheRamp() {
        AirRotation r = new AirRotation();
        for (int i = 0; i < 5; i++) r.step(T, GearKind.BOARD, 1, 0, false);
        double held = r.yawAccum;
        r.step(T, GearKind.BOARD, 0, 0, false);
        assertEquals(held, r.yawAccum, 0);
        r.step(T, GearKind.BOARD, -1, 0, false);
        assertEquals(held - 6, r.yawAccum, 1e-12);
    }

    @Test
    void flipsUseTheirOwnRate() {
        AirRotation r = new AirRotation();
        for (int i = 0; i < 20; i++) r.step(T, GearKind.BOARD, 0, 1, false);
        assertEquals(6 + 12 + 18 + 24 * 17, r.pitchAccum, 1e-9);
        assertEquals(0, r.yawAccum, 0);
    }
}
