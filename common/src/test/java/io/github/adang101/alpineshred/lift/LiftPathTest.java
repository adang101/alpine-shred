package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.park.V3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 13's path model: loop length, continuity at the joins, sag, offsets and determinism. */
class LiftPathTest {
    private static final List<V3> STRAIGHT = List.of(new V3(0.5, 100, 0.5), new V3(0.5, 60, 100.5));
    private static final List<V3> BENT = List.of(new V3(0.5, 140, 0.5), new V3(20.5, 130, 40.5), new V3(25.5, 115, 80.5),
            new V3(10.5, 101, 120.5), new V3(0.5, 90, 150.5));

    /** Arc length of a span by fine sampling of the same curve. */
    private static double spanLength(V3 a, V3 b) {
        double h = a.horizontalDistance(b), sag = LiftPath.sag(h), len = 0;
        V3 prev = a;
        for (int k = 1; k <= 20000; k++) {
            double t = k / 20000.0;
            V3 p = new V3(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t - sag * 4 * t * (1 - t), a.z() + (b.z() - a.z()) * t);
            len += p.distance(prev);
            prev = p;
        }
        return len;
    }

    @Test
    void aStraightLineIsTwoSpansAndTwoHalfCircles() {
        LiftPath path = LiftPath.of(STRAIGHT);
        V3 drive = STRAIGHT.get(0), ret = STRAIGHT.get(1);
        // Facing down the line (+z), left is -x... n = (-d.z, d.x) = (-1, 0): the loaded cable is at x - 2.
        V3 loadedTop = drive.add(-2, 2.5, 0), loadedBottom = ret.add(-2, 2.5, 0);
        double expected = 2 * spanLength(loadedBottom, loadedTop) + 2 * Math.PI * LiftPath.WHEEL_RADIUS;
        assertEquals(expected, path.loopLength(), 0.05);
        V3 start = path.pos(0);
        assertEquals(loadedBottom.x(), start.x(), 1.0e-9);
        assertEquals(loadedBottom.y(), start.y(), 1.0e-9);
        assertEquals(loadedBottom.z(), start.z(), 1.0e-9);
        // The loaded side climbs: the drive is the top.
        assertTrue(path.pos(path.loadedEnd()).y() > path.pos(0).y() + 30);
        // Both cables are the same length, and each wheel takes half a circle.
        assertEquals(path.loadedEnd(), path.returnEnd() - path.returnStart(), 1.0e-6);
        assertEquals(Math.PI * LiftPath.WHEEL_RADIUS, path.returnStart() - path.loadedEnd(), 0.01);
        assertEquals(Math.PI * LiftPath.WHEEL_RADIUS, path.loopLength() - path.returnEnd(), 0.01);
    }

    @Test
    void spansSagAsTheDocSays() {
        assertEquals(1.5, LiftPath.sag(30), 1.0e-12);
        assertEquals(LiftPath.MAX_SAG, LiftPath.sag(100), 1.0e-12);
        LiftPath path = LiftPath.of(List.of(new V3(0.5, 80, 0.5), new V3(0.5, 80, 30.5)));
        // Halfway along the loaded span (from z 30.5 back to z 0.5), 1.5 below the chord.
        double s = path.loadedEnd() / 2;
        V3 mid = path.pos(s);
        assertEquals(15.5, mid.z(), 0.1);
        assertEquals(82.5 - 1.5, mid.y(), 0.01);
    }

    @Test
    void theLoopIsContinuousEverywhereAndAtItsJoins() {
        for (List<V3> nodes : List.of(STRAIGHT, BENT)) {
            LiftPath path = LiftPath.of(nodes);
            double step = 0.01;
            V3 prev = path.pos(0);
            for (double s = step; s <= path.loopLength() + 1.0e-9; s += step) {
                V3 p = path.pos(s);
                assertTrue(p.distance(prev) <= step * 1.0001 + 1.0e-9, "jump of " + p.distance(prev) + " at s " + s);
                prev = p;
            }
            for (double join : new double[]{0, path.loadedEnd(), path.returnStart(), path.returnEnd(), path.loopLength()}) {
                assertTrue(path.pos(join - 1.0e-6).distance(path.pos(join + 1.0e-6)) < 1.0e-5, "a gap at " + join);
            }
            assertTrue(path.pos(-3).distance(path.pos(path.loopLength() - 3)) < 1.0e-9, "negative s wraps");
            for (double s = 0; s < path.loopLength(); s += 0.7) assertEquals(1.0, path.tangent(s).length(), 1.0e-9);
        }
    }

    @Test
    void cablesPassEachHeadOnBothSidesAtTheRightHeight() {
        LiftPath path = LiftPath.of(BENT);
        V3 head = BENT.get(2);
        double bestLoaded = Double.MAX_VALUE, bestReturn = Double.MAX_VALUE;
        for (double s = 0; s < path.loopLength(); s += 0.05) {
            V3 p = path.pos(s);
            double d = p.horizontalDistance(head);
            if (s < path.loadedEnd()) bestLoaded = Math.min(bestLoaded, d);
            else if (s > path.returnStart() && s < path.returnEnd()) bestReturn = Math.min(bestReturn, d);
        }
        assertEquals(LiftPath.HEAD_OFFSET, bestLoaded, 0.06);
        assertEquals(LiftPath.HEAD_OFFSET, bestReturn, 0.06);
    }

    @Test
    void theSameNodesGiveTheSameLoopToTheLastBit() {
        LiftPath a = LiftPath.of(BENT), b = LiftPath.of(List.copyOf(BENT));
        assertEquals(Double.doubleToLongBits(a.loopLength()), Double.doubleToLongBits(b.loopLength()));
        Random r = new Random(7);
        for (int i = 0; i < 1000; i++) {
            double s = r.nextDouble() * 3 * a.loopLength() - a.loopLength();
            V3 p = a.pos(s), q = b.pos(s);
            assertEquals(Double.doubleToLongBits(p.x()), Double.doubleToLongBits(q.x()));
            assertEquals(Double.doubleToLongBits(p.y()), Double.doubleToLongBits(q.y()));
            assertEquals(Double.doubleToLongBits(p.z()), Double.doubleToLongBits(q.z()));
        }
    }

    @Test
    void chairsAreEvenlySpacedAndHangBelowTheCable() {
        LiftPath path = LiftPath.of(BENT);
        int count = LiftPath.chairCount(path.loopLength(), 24);
        assertEquals((int) Math.floor(path.loopLength() / 24), count);
        double spacing = path.loopLength() / count;
        for (int i = 0; i + 1 < count; i++) {
            assertEquals(spacing, path.wrap(path.chairS(10, i + 1, count) - path.chairS(10, i, count)), 1.0e-9);
        }
        V3 seat = path.chairPos(10, 3, count), cable = path.pos(path.chairS(10, 3, count));
        assertEquals(cable.y() - LiftPath.CHAIR_HANG, seat.y(), 1.0e-12);
        assertEquals(1, LiftPath.chairCount(5, 24));
    }

    @Test
    void badNodeListsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> LiftPath.of(List.of(new V3(0, 64, 0))));
        assertThrows(IllegalArgumentException.class, () -> LiftPath.of(List.of(new V3(0, 64, 0), new V3(0, 80, 0))));
    }
}
