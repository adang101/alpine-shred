package io.github.adang101.alpineshred.compat.create;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KineticDriveWatchTest {
    @Test
    void aFreshDriveIsStoppedSoFirstRotationIsAChange() {
        KineticDriveWatch watch = new KineticDriveWatch();
        assertFalse(watch.update(false, 0), "still stopped");
        assertTrue(watch.update(true, 1.0), "started");
        assertFalse(watch.update(true, 1.0), "no change");
        assertTrue(watch.update(true, 2.0), "faster");
        assertTrue(watch.update(false, 0), "lost rotation");
    }

    @Test
    void resetTakesTheBaselineSilently() {
        KineticDriveWatch watch = new KineticDriveWatch();
        watch.reset(true, 2.0);
        assertFalse(watch.update(true, 2.0), "a reload of a running drive is not a change");
        assertTrue(watch.update(true, 1.0));
    }
}
