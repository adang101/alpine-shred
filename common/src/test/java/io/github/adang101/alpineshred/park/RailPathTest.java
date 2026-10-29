package io.github.adang101.alpineshred.park;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 19, unit layer: {@code RailPath}, continuity across every shape pair. */
class RailPathTest {
    private static final double EPS = 1.0e-9;

    @Test
    void authoredPathsMatchTheDocTable() {
        RailPath flat = RailPath.local(RailShape.FLAT, Facing.NORTH);
        assertNear(new V3(0.5, 0.5, 1.0), flat.endA());
        assertNear(new V3(0.5, 0.5, 0.0), flat.endB());
        assertNear(new V3(0.5, 1.0, 0.0), RailPath.local(RailShape.RISE_A, Facing.NORTH).endB());
        assertNear(new V3(0.5, 0.0, 1.0), RailPath.local(RailShape.RISE_B, Facing.NORTH).endA());
        RailPath curve = RailPath.local(RailShape.CURVE, Facing.NORTH);
        assertEquals(6, curve.pointCount(), "curve sampled at 6 points");
        assertNear(new V3(0.5, 0.5, 1.0), curve.endA());
        assertNear(new V3(1.0, 0.5, 0.5), curve.endB());
        assertEquals(Math.PI / 4, curve.length(), 0.01, "quarter arc of radius 0.5");
        // A rise is 0.5 per block, about 26.6 degrees.
        V3 t = RailPath.local(RailShape.RISE_A, Facing.NORTH).tangentA();
        assertEquals(26.57, Math.toDegrees(Math.atan2(t.y(), t.horizontalLength())), 0.01);
    }

    @Test
    void facingsTurnClockwise() {
        RailPath east = RailPath.local(RailShape.FLAT, Facing.EAST);
        assertNear(new V3(0.0, 0.5, 0.5), east.endA());
        assertNear(new V3(1.0, 0.5, 0.5), east.endB());
        assertNear(new V3(1, 0, 0), east.tangentA());
        RailPath curveSouth = RailPath.local(RailShape.CURVE, Facing.SOUTH);
        assertNear(new V3(0.5, 0.5, 0.0), curveSouth.endA());
        assertNear(new V3(0.0, 0.5, 0.5), curveSouth.endB());
    }

    @Test
    void everyEndIsOnABlockEdgeMidpoint() {
        for (RailShape shape : RailShape.values()) {
            for (Facing facing : Facing.values()) {
                RailPath p = RailPath.local(shape, facing);
                for (V3 end : new V3[]{p.endA(), p.endB()}) {
                    boolean onXEdge = Math.abs(end.x()) < EPS || Math.abs(end.x() - 1) < EPS;
                    boolean onZEdge = Math.abs(end.z()) < EPS || Math.abs(end.z() - 1) < EPS;
                    assertTrue(onXEdge != onZEdge, shape + " " + facing + " end " + end + " not on one edge");
                    double mid = onXEdge ? end.z() : end.x();
                    assertEquals(0.5, mid, EPS, shape + " " + facing + " end off the edge midpoint");
                    // End tangents run straight across the edge, so pieces meet without a kink in plan.
                    V3 t = end == p.endA() ? p.tangentA() : p.tangentB();
                    double across = onXEdge ? Math.abs(t.x()) : Math.abs(t.z());
                    assertTrue(across > 0.85, shape + " " + facing + " meets its edge at a slant: " + t);
                }
            }
        }
    }

    /**
     * Continuity across every shape pair: wherever two pieces in neighbouring cells have ends near
     * each other, the ends coincide exactly (a gap a rider would have to jump never hides inside
     * the join distance).
     */
    @Test
    void neighbouringEndsEitherMeetExactlyOrAreFarApart() {
        int joints = 0;
        for (RailShape s1 : RailShape.values()) {
            for (Facing f1 : Facing.values()) {
                RailPath p1 = RailPath.at(s1, f1, 0, 0, 0);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            for (RailShape s2 : RailShape.values()) {
                                for (Facing f2 : Facing.values()) {
                                    RailPath p2 = RailPath.at(s2, f2, dx, dy, dz);
                                    for (V3 a : new V3[]{p1.endA(), p1.endB()}) {
                                        for (V3 b : new V3[]{p2.endA(), p2.endB()}) {
                                            double d = a.distance(b);
                                            assertTrue(d < EPS || d > GrindPhysics.JOIN_DISTANCE,
                                                    s1 + " " + f1 + " and " + s2 + " " + f2 + " at " + dx + "," + dy + "," + dz + ": ends " + d + " apart");
                                            if (d < EPS) joints++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(joints > 100, "too few joints found: " + joints);
    }

    @Test
    void risesChainUpAndFlatsResume() {
        RailPath flat = RailPath.at(RailShape.FLAT, Facing.NORTH, 0, 0, 0);
        RailPath riseA = RailPath.at(RailShape.RISE_A, Facing.NORTH, 0, 0, -1);
        RailPath riseB = RailPath.at(RailShape.RISE_B, Facing.NORTH, 0, 1, -2);
        RailPath top = RailPath.at(RailShape.FLAT, Facing.NORTH, 0, 1, -3);
        assertNear(flat.endB(), riseA.endA());
        assertNear(riseA.endB(), riseB.endA());
        assertNear(riseB.endB(), top.endA());
        assertEquals(1.0, top.endA().y() - flat.endA().y(), EPS, "a rise climbs one block over two pieces");
    }

    @Test
    void closestPointIsMeasuredHorizontally() {
        RailPath flat = RailPath.at(RailShape.FLAT, Facing.EAST, 10, 64, 20);
        RailPath.Closest c = flat.closest(new V3(10.3, 65.0, 20.6));
        assertEquals(0.3, c.s(), EPS);
        assertEquals(0.1, c.horizontal(), EPS);
        assertEquals(0.5, c.above(), EPS);
        RailPath.Closest past = flat.closest(new V3(12.0, 64.5, 20.5));
        assertEquals(1.0, past.s(), EPS, "clamped to the end");
        assertEquals(1.0, past.horizontal(), EPS);
    }

    @Test
    void pointAndTangentAlongACurve() {
        RailPath curve = RailPath.local(RailShape.CURVE, Facing.NORTH);
        V3 mid = curve.pointAt(curve.length() / 2);
        assertEquals(0.5, mid.distance(new V3(1, 0.5, 1)), 0.01, "on the arc about the south-east corner");
        V3 t = curve.tangentAt(curve.length() / 2);
        assertTrue(t.x() > 0.5 && t.z() < -0.5, "heading north-east half way round: " + t);
    }

    static void assertNear(V3 expected, V3 actual) {
        assertTrue(expected.distance(actual) < EPS, "expected " + expected + " but was " + actual);
    }
}
