package io.github.adang101.alpineshred.worldgen;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 12's groomed run planner on synthetic terrain: starts, stops, smoothing, bounds. */
class RunPlannerTest {
    private static final int SEA = 63;

    /** Terrain from a height function; water below sea level unless {@code water} says otherwise. */
    record Synthetic(java.util.function.IntBinaryOperator h, java.util.function.BiPredicate<Integer, Integer> water,
                     java.util.function.BiPredicate<Integer, Integer> allowed) implements RunPlanner.Terrain {
        static Synthetic of(java.util.function.IntBinaryOperator h) {
            return new Synthetic(h, (x, z) -> h.applyAsInt(x, z) <= SEA, (x, z) -> true);
        }

        public int height(int x, int z) {
            return h.applyAsInt(x, z);
        }

        public boolean water(int x, int z) {
            return water.test(x, z);
        }

        public boolean allowed(int x, int z) {
            return allowed.test(x, z);
        }
    }

    private static void checkPlan(RunPlanner.Plan p, int sx, int sz) {
        assertTrue(p.width() == 9 || p.width() == 11 || p.width() == 13, "width " + p.width());
        double len = p.length();
        assertTrue(len >= RunPlanner.MIN_BUILT_LENGTH - 8 && len <= RunPlanner.MAX_LENGTH + RunPlanner.RUN_OUT + 8, "length " + len);
        for (int i = 0; i < p.points().size(); i++) {
            double[] q = p.points().get(i);
            assertTrue(Math.hypot(q[0] - sx, q[1] - sz) + p.reach() <= RunPlanner.MAX_REACH, "point " + i + " out of reach");
            if (i > 0) assertTrue(q[2] <= p.points().get(i - 1)[2] + 1.0e-9, "the profile climbs at " + i);
            if (i > 0) {
                double step = Math.hypot(q[0] - p.points().get(i - 1)[0], q[1] - p.points().get(i - 1)[1]);
                assertTrue(step < 3.5, "points every 2 blocks, got " + step);
            }
        }
        // Every built column is inside the box the piece claims.
        int[] box = RunShape.bounds(p);
        for (double[] q : p.points()) {
            assertTrue(q[0] - p.reach() >= box[0] && q[0] + p.reach() <= box[2] && q[1] - p.reach() >= box[1] && q[1] + p.reach() <= box[3]);
        }
    }

    @Test
    void aPlainSlopeGivesARunDownIt() {
        Synthetic slope = Synthetic.of((x, z) -> 160 - (int) Math.floor(x * 0.35));
        RunPlanner.Plan p = RunPlanner.plan(slope, SEA, 0, 0, 42);
        assertNotNull(p);
        checkPlan(p, 0, 0);
        double[] end = p.points().get(p.runOutFrom() - 1);
        assertTrue(end[0] > 60, "ran down the slope (+x): ended at x " + end[0]);
        // The run-out is flat.
        for (int i = p.runOutFrom(); i < p.points().size(); i++) assertEquals(end[2], p.points().get(i)[2], 1.0e-9);
    }

    @Test
    void startsMustBeHighAndSloped() {
        assertNull(RunPlanner.plan(Synthetic.of((x, z) -> 150), SEA, 0, 0, 1), "flat");
        assertNull(RunPlanner.plan(Synthetic.of((x, z) -> 75 - x / 3), SEA, 0, 0, 1), "too low");
        assertNull(RunPlanner.plan(Synthetic.of((x, z) -> 200 - x), SEA, 0, 0, 1), "too steep");
    }

    @Test
    void waterAndBiomeEdgesStopTheRun() {
        Synthetic lake = new Synthetic((x, z) -> 160 - (int) Math.floor(x * 0.3), (x, z) -> x >= 64, (x, z) -> true);
        RunPlanner.Plan p = RunPlanner.plan(lake, SEA, 0, 0, 7);
        assertNotNull(p);
        for (double[] q : p.points()) assertTrue(q[0] < 64 + 4, "ran into the lake at x " + q[0]);
        Synthetic edge = new Synthetic((x, z) -> 160 - (int) Math.floor(x * 0.3), (x, z) -> false, (x, z) -> x < 70);
        p = RunPlanner.plan(edge, SEA, 0, 0, 7);
        assertNotNull(p);
        for (double[] q : p.points()) assertTrue(q[0] < 70 + 4, "left the biome at x " + q[0]);
    }

    @Test
    void aThousandRandomSlopesStayInBounds() {
        Random r = new Random(1234);
        int planned = 0;
        double lengths = 0;
        for (int n = 0; n < 1000; n++) {
            double a1 = r.nextDouble() * 6.28, a2 = r.nextDouble() * 6.28, f1 = 0.01 + r.nextDouble() * 0.04, f2 = 0.02 + r.nextDouble() * 0.06;
            double tilt = 0.15 + r.nextDouble() * 0.4, dir = r.nextDouble() * 6.28;
            int base = 120 + r.nextInt(80);
            Synthetic t = Synthetic.of((x, z) -> (int) Math.floor(base - tilt * (x * Math.cos(dir) + z * Math.sin(dir))
                    + 12 * Math.sin(x * f1 + a1) + 8 * Math.sin(z * f2 + a2)));
            int[] top = RunPlanner.uphillStart(t, 0, 0, 88);
            RunPlanner.Plan p = RunPlanner.plan(t, SEA, top[0], top[1], 0, 0, r.nextLong());
            if (p == null) continue;
            planned++;
            checkPlan(p, 0, 0);
            lengths += p.length();
            RunShape.markers(p);
        }
        assertTrue(planned > 300, "planned only " + planned + " of 1000");
        assertTrue(lengths / planned > 120, "runs average " + lengths / planned + " blocks");
    }

    @Test
    void columnsAreGroomedThenFadeAndMarkersStandOnBothEdges() {
        Synthetic slope = Synthetic.of((x, z) -> 160 - (int) Math.floor(x * 0.35));
        RunPlanner.Plan p = RunPlanner.plan(slope, SEA, 0, 0, 42);
        assertNotNull(p);
        double[] mid = p.points().get(p.points().size() / 3);
        int mx = (int) Math.floor(mid[0]), mz = (int) Math.floor(mid[1]);
        int ground = slope.height(mx, mz);
        RunShape.Column c = RunShape.column(p, mx, mz, ground);
        assertEquals(RunShape.Kind.GROOMED, c.kind());
        assertTrue(Math.abs(c.surface() - ground) <= RunShape.MAX_CUT, "cut " + (c.surface() - ground));
        // Far to the side: untouched; just past the edge: fading.
        double[] next = p.points().get(p.points().size() / 3 + 1);
        double vx = next[0] - mid[0], vz = next[1] - mid[1], len = Math.hypot(vx, vz);
        double nx = -vz / len, nz = vx / len;
        int fx = (int) Math.floor(mid[0] + nx * (p.width() / 2.0 + 1.5)), fz = (int) Math.floor(mid[1] + nz * (p.width() / 2.0 + 1.5));
        assertEquals(RunShape.Kind.FADE, RunShape.column(p, fx, fz, slope.height(fx, fz)).kind());
        int ox = (int) Math.floor(mid[0] + nx * 30), oz = (int) Math.floor(mid[1] + nz * 30);
        assertEquals(RunShape.Kind.NONE, RunShape.column(p, ox, oz, slope.height(ox, oz)).kind());
        int markers = RunShape.markers(p).size();
        assertTrue(markers >= 2 * (int) ((p.length() - RunPlanner.RUN_OUT) / RunShape.MARKER_SPACING) - 2, "markers " + markers);
        assertEquals(0, markers % 2, "pairs");
    }

    @Test
    void theSeedPicksTheSameRunEveryTime() {
        Synthetic slope = Synthetic.of((x, z) -> 170 - (int) Math.floor(x * 0.25 + z * 0.2));
        RunPlanner.Plan a = RunPlanner.plan(slope, SEA, 0, 0, 99), b = RunPlanner.plan(slope, SEA, 0, 0, 99);
        assertNotNull(a);
        assertEquals(a.points().size(), b.points().size());
        assertEquals(a.difficulty(), b.difficulty());
        for (int i = 0; i < a.points().size(); i++) assertEquals(a.points().get(i)[0], b.points().get(i)[0], 0.0);
    }
}
