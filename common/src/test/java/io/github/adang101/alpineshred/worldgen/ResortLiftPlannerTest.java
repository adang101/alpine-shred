package io.github.adang101.alpineshred.worldgen;

import io.github.adang101.alpineshred.lift.LinkValidator;
import io.github.adang101.alpineshred.worldgen.ResortLiftPlanner.Ground;
import io.github.adang101.alpineshred.worldgen.ResortLiftPlanner.Node;
import io.github.adang101.alpineshred.worldgen.ResortLiftPlanner.Outcome;
import io.github.adang101.alpineshred.worldgen.ResortLiftPlanner.Plan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The resort lift's plan: spacing, distance from the run, chair clearance, determinism, and the cases it refuses. */
class ResortLiftPlannerTest {
    private static final LinkValidator.Limits LIMITS = new LinkValidator.Limits(64, 48, 1200, 64);
    private static final double REACH = 9;
    private static final int SEA = 63;

    /** A run of {@code length} blocks down +z (points every 2 blocks, with a gentle bend in x), as (x, z, y). */
    private static List<double[]> run(int length) {
        List<double[]> pts = new ArrayList<>();
        for (int z = 0; z <= length; z += 2) pts.add(new double[]{Math.sin(z / 60.0) * 6, z, 200 - z * 0.3});
        return pts;
    }

    /** A slope down +z with rolling bumps. */
    private static final Ground ROLLING = (x, z) -> (int) Math.round(200 - z * 0.3 + Math.sin(x / 9.0) * 2 + Math.cos(z / 11.0) * 3);

    private static Plan plan(int length, Ground ground, long seed) {
        Outcome o = ResortLiftPlanner.plan(run(length), REACH, ground, SEA, LIMITS, seed);
        assertNotNull(o.plan(), o.failure());
        return o.plan();
    }

    @Test
    void nodesAreSpacedAboutFortyApartAndNeverBeyondMaxSpan() {
        Plan plan = plan(200, ROLLING, 1);
        List<Node> nodes = plan.nodes();
        assertTrue(nodes.size() >= 5, "nodes: " + nodes.size());
        for (int i = 0; i + 1 < nodes.size(); i++) {
            double d = Math.hypot(nodes.get(i + 1).x() - nodes.get(i).x(), nodes.get(i + 1).z() - nodes.get(i).z());
            assertTrue(d <= LIMITS.maxSpan(), "span " + i + " is " + d);
            assertTrue(d >= 25 && d <= 55, "span " + i + " is " + d);
        }
    }

    @Test
    void theLineIsAboutTheRunsLength() {
        Plan plan = plan(200, ROLLING, 1);
        double length = 0;
        for (int i = 0; i + 1 < plan.nodes().size(); i++) {
            length += Math.hypot(plan.nodes().get(i + 1).x() - plan.nodes().get(i).x(), plan.nodes().get(i + 1).z() - plan.nodes().get(i).z());
        }
        assertTrue(length > 150 && length < 215, "line is " + length);
    }

    @Test
    void everyNodeStandsBesideTheRunNotOnIt() {
        for (long seed : new long[]{0, 128}) {
            Plan plan = plan(200, ROLLING, seed);
            for (Node n : plan.nodes()) {
                double near = Double.MAX_VALUE;
                for (double[] p : run(200)) near = Math.min(near, Math.hypot(p[0] - n.x(), p[1] - n.z()));
                assertTrue(near >= REACH + 3, "node " + n + " is only " + near + " from the run's centre");
            }
        }
    }

    @Test
    void theTwoSeedBitsChooseOppositeSides() {
        List<double[]> straight = new ArrayList<>();
        for (int z = 0; z <= 200; z += 2) straight.add(new double[]{0, z, 200 - z * 0.3});
        Plan a = ResortLiftPlanner.plan(straight, REACH, ROLLING, SEA, LIMITS, 0).plan(), b = ResortLiftPlanner.plan(straight, REACH, ROLLING, SEA, LIMITS, 128).plan();
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(-a.side(), b.side());
    }

    @Test
    void chairsClearTheGroundByAtLeastTwoBlocks() {
        for (Ground ground : new Ground[]{ROLLING, (x, z) -> 150, (x, z) -> (int) Math.round(200 - z * 0.3 + (z % 40 < 6 ? 8 : 0))}) {
            Plan plan = plan(200, ground, 5);
            assertTrue(ResortLiftPlanner.minClearance(plan, ground) >= 2.0, "clearance " + ResortLiftPlanner.minClearance(plan, ground));
        }
    }

    @Test
    void towersAreTerrainRelative() {
        Plan plan = plan(200, ROLLING, 1);
        for (int i = 1; i + 1 < plan.nodes().size(); i++) {
            Node n = plan.nodes().get(i);
            int pole = n.y() - ROLLING.height(n.x(), n.z());
            assertTrue(pole >= 0 && pole <= ResortLiftPlanner.MAX_POLE, "pole " + pole);
        }
    }

    @Test
    void theStationsStandOnTheirPlatforms() {
        Plan plan = plan(200, ROLLING, 1);
        for (Node n : List.of(plan.nodes().get(0), plan.nodes().get(plan.nodes().size() - 1))) {
            assertTrue(n.y() >= ROLLING.height(n.x(), n.z()), "station below its ground");
            assertTrue(n.y() - ROLLING.height(n.x(), n.z()) <= 8, "station far above its ground");
        }
        assertTrue(plan.nodes().get(0).y() > plan.nodes().get(plan.nodes().size() - 1).y(), "the drive is the top");
    }

    @Test
    void theSameInputGivesTheSamePlan() {
        assertEquals(plan(200, ROLLING, 77), plan(200, ROLLING, 77));
    }

    @Test
    void aShortRunGetsNoLift() {
        Outcome o = ResortLiftPlanner.plan(run(40), REACH, ROLLING, SEA, LIMITS, 1);
        assertNull(o.plan());
        assertNotNull(o.failure());
    }

    @Test
    void waterAtANodeGetsNoLift() {
        Ground lake = (x, z) -> z > 80 && z < 120 ? SEA - 1 : ROLLING.height(x, z);
        assertNull(ResortLiftPlanner.plan(run(200), REACH, lake, SEA, LIMITS, 1).plan());
    }

    @Test
    void aCliffTooTallForTowersGetsNoLift() {
        // A wall 80 blocks high across the line's path.
        Ground wall = (x, z) -> ROLLING.height(x, z) + (z > 95 && z < 105 ? 80 : 0);
        Outcome o = ResortLiftPlanner.plan(run(200), REACH, wall, SEA, LIMITS, 1);
        assertNull(o.plan(), "planned over a wall");
    }

    @Test
    void theLimitsAreChecked() {
        Outcome o = ResortLiftPlanner.plan(run(200), REACH, ROLLING, SEA, new LinkValidator.Limits(64, 48, 100, 64), 1);
        assertNull(o.plan());
        assertTrue(o.failure().contains("TOO_LONG"), o.failure());
    }
}
