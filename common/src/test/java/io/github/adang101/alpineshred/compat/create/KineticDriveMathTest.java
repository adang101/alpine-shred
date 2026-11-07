package io.github.adang101.alpineshred.compat.create;

import io.github.adang101.alpineshred.config.AlpineConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KineticDriveMathTest {
    private static final double MAX = 4.0;

    @Test
    void rpmGivesOneBlockPerSecondPer64() {
        assertEquals(1.0, KineticDriveMath.targetSpeed(64, MAX), 0);
        assertEquals(2.0, KineticDriveMath.targetSpeed(128, MAX), 0);
        assertEquals(0.125, KineticDriveMath.targetSpeed(8, MAX), 0);
        assertEquals(0.25, KineticDriveMath.targetSpeed(16, MAX), 0);
    }

    @Test
    void createsTopSpeedIsTheDefaultCap() {
        assertEquals(4.0, KineticDriveMath.targetSpeed(256, AlpineConfig.LIFT_KINETIC_MAX_SPEED.defaultValue), 0);
        assertEquals(4.0, KineticDriveMath.targetSpeed(1000, MAX), 0, "clamped to the cap");
        assertEquals(2.5, KineticDriveMath.targetSpeed(256, 2.5), 0, "a lower cap wins");
    }

    @Test
    void underEightRpmIsStopped() {
        assertFalse(KineticDriveMath.running(7.99));
        assertFalse(KineticDriveMath.running(-7.99));
        assertFalse(KineticDriveMath.running(0));
        assertFalse(KineticDriveMath.running(Double.NaN));
        assertTrue(KineticDriveMath.running(8));
        assertTrue(KineticDriveMath.running(-8));
        assertEquals(0, KineticDriveMath.targetSpeed(7.99, MAX), 0);
        assertEquals(0, KineticDriveMath.targetSpeed(Double.NaN, MAX), 0);
    }

    @Test
    void directionDoesNotMatter() {
        for (double rpm : new double[]{8, 16, 64, 100, 256}) {
            assertEquals(KineticDriveMath.targetSpeed(rpm, MAX), KineticDriveMath.targetSpeed(-rpm, MAX), 0, "rpm " + rpm);
            assertEquals(KineticDriveMath.stressUnits(rpm, 4.0), KineticDriveMath.stressUnits(-rpm, 4.0), 0, "stress at rpm " + rpm);
        }
    }

    @Test
    void stressIsAFlatImpactPerRpm() {
        double impact = AlpineConfig.LIFT_KINETIC_STRESS.defaultValue;
        assertEquals(4.0, impact, 0);
        assertEquals(1024.0, KineticDriveMath.stressUnits(256, impact), 0, "a full-speed lift is 1024 su");
        assertEquals(256.0, KineticDriveMath.stressUnits(64, impact), 0);
        assertEquals(0, KineticDriveMath.stressUnits(0, impact), 0);
    }

    @Test
    void configRangesAreSane() {
        assertTrue(AlpineConfig.LIFT_KINETIC_MAX_SPEED.min > 0 && AlpineConfig.LIFT_KINETIC_MAX_SPEED.max <= 10.0);
        assertTrue(AlpineConfig.LIFT_KINETIC_STRESS.min > 0 && AlpineConfig.LIFT_KINETIC_STRESS.max >= AlpineConfig.LIFT_KINETIC_STRESS.defaultValue);
    }
}
