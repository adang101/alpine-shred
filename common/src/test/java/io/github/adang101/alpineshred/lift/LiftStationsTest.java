package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.park.V3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Decision 4 of the phase 8 plan: boarding stretch, unload stretch and ejection point. */
class LiftStationsTest {
    private static final double DRIVE_Y = 100;

    private static void check(LiftPath path, double platformY) {
        LiftStations.Result r = LiftStations.compute(path, platformY);
        double end = path.loadedEnd();
        // Both stretches lie inside the loaded cable, [0, loadedEnd].
        assertTrue(r.boardStart() >= 0 && r.boardEnd() <= end && r.boardStart() <= r.boardEnd(), "boarding " + r);
        assertTrue(r.unloadStart() >= 0 && r.unloadEnd() <= end && r.unloadStart() <= r.unloadEnd(), "unload " + r);
        assertEquals(0.0, r.boardStart(), 0.0);
        assertEquals(Math.min(4.0, end), r.boardEnd(), 1e-9);
        assertEquals(end, r.unloadEnd(), 1e-9);
        // The ejection point is 1 block before the end and inside the unload stretch.
        assertEquals(end - 1.0, r.ejectS(), 1e-9);
        assertTrue(r.inUnload(r.ejectS()), "ejection point in the unload stretch " + r);
        // Every seat in the unload stretch is within 3 blocks of the platform.
        for (double s = r.unloadStart(); s <= r.unloadEnd(); s += 0.1) {
            assertTrue(Math.abs(path.pos(s).y() - LiftPath.CHAIR_HANG - platformY) <= 3.0 + 1e-6, "seat out of band at " + s);
        }
        // And just before the stretch the seat is out of the band, unless the stretch is the whole cable.
        if (r.unloadStart() > 1e-9) {
            assertTrue(Math.abs(path.pos(r.unloadStart() - 0.01).y() - LiftPath.CHAIR_HANG - platformY) > 3.0 - 0.05, "boundary is tight");
        }
    }

    @Test
    void aStraightTwoNodeLine() {
        LiftPath path = LiftPath.of(List.of(new V3(0.5, DRIVE_Y + 0.5, 0.5), new V3(0.5, 60.5, 100.5)));
        check(path, DRIVE_Y);
        LiftStations.Result r = LiftStations.compute(path, DRIVE_Y);
        // The cable climbs about 0.4 per block, and the seat at the drive is 1.4 above the platform: the
        // 1.6 blocks of headroom give roughly 4 blocks of unload stretch, well short of the whole span.
        assertTrue(r.unloadEnd() - r.unloadStart() > 2.0 && r.unloadEnd() - r.unloadStart() < 20.0, r.toString());
    }

    @Test
    void aTenHeadLine() {
        List<V3> nodes = new ArrayList<>();
        nodes.add(new V3(0.5, DRIVE_Y + 0.5, 0.5));
        for (int i = 1; i <= 10; i++) nodes.add(new V3(0.5 + 3 * i, DRIVE_Y + 0.5 - 6.0 * i, 0.5 + 40.0 * i));
        nodes.add(new V3(0.5 + 36, DRIVE_Y + 0.5 - 70, 0.5 + 460));
        LiftPath path = LiftPath.of(nodes);
        check(path, DRIVE_Y);
    }

    @Test
    void aFlatLineUnloadsAlongTheWholeCable() {
        LiftPath path = LiftPath.of(List.of(new V3(0.5, DRIVE_Y + 0.5, 0.5), new V3(0.5, DRIVE_Y + 0.5, 30.5)));
        LiftStations.Result r = LiftStations.compute(path, DRIVE_Y);
        check(path, DRIVE_Y);
        // The cable sags below its ends, so nowhere is it too high.
        assertEquals(0.0, r.unloadStart(), 1e-9);
    }

    @Test
    void aPlatformFarBelowTheCableHasNoRoomToUnload() {
        LiftPath path = LiftPath.of(List.of(new V3(0.5, DRIVE_Y + 0.5, 0.5), new V3(0.5, 60.5, 100.5)));
        LiftStations.Result r = LiftStations.compute(path, DRIVE_Y - 10);
        double end = path.loadedEnd();
        assertEquals(end - 1.0, r.ejectS(), 1e-9);
        assertTrue(r.unloadStart() <= r.ejectS() && r.ejectS() <= r.unloadEnd());
    }

    @Test
    void aVeryShortLoadedCableStaysInsideItself() {
        LiftPath path = LiftPath.of(List.of(new V3(0.5, 70.5, 0.5), new V3(0.5, 70.5, 3.5)));
        LiftStations.Result r = LiftStations.compute(path, 70);
        assertTrue(r.boardEnd() <= path.loadedEnd());
        assertTrue(r.ejectS() >= 0 && r.ejectS() <= path.loadedEnd());
        assertTrue(r.inUnload(r.ejectS()));
    }
}
