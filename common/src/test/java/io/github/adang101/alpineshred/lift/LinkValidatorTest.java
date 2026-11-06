package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.lift.LinkValidator.Limits;
import io.github.adang101.alpineshred.lift.LinkValidator.Reason;
import io.github.adang101.alpineshred.lift.LinkValidator.Result;
import io.github.adang101.alpineshred.park.V3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The linker's rules: each rejection on its own, a valid chain, the first blocked sample, and agreement with {@link LiftPath}. */
class LinkValidatorTest {
    private static final Limits LIMITS = new Limits(64, 48, 1200, 64);
    private static final BlockSolid OPEN = (x, y, z) -> false;

    /** A node at the centre of block (x, y, z), as {@link LiftLine#node} makes it. */
    private static V3 node(int x, int y, int z) {
        return new V3(x + 0.5, y + 0.5, z + 0.5);
    }

    /** A straight chain along +z, {@code spacing} apart, all at one height, {@code count} nodes in all. */
    private static List<V3> straight(int count, int spacing) {
        List<V3> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) nodes.add(node(0, 100, i * spacing));
        return nodes;
    }

    @Test
    void aSimpleLinePasses() {
        assertTrue(LinkValidator.checkFinish(straight(3, 30), OPEN, LIMITS, 0).ok());
    }

    @Test
    void tooFarIsRejectedWhenTheHeadIsAdded() {
        Result r = LinkValidator.checkAdd(straight(2, 70), OPEN, LIMITS);
        assertEquals(Reason.TOO_FAR, r.reason());
        assertEquals(0, r.span());
        assertTrue(LinkValidator.checkAdd(straight(2, 64), OPEN, LIMITS).ok(), "exactly maxSpan is allowed");
        assertEquals(Reason.TOO_FAR, LinkValidator.checkFinish(straight(3, 70), OPEN, LIMITS, 0).reason());
    }

    @Test
    void tooSteepIsRejected() {
        // 30 blocks along, 60 down: about 63 degrees.
        List<V3> steep = List.of(node(0, 160, 0), node(0, 100, 30));
        Result r = LinkValidator.checkAdd(steep, OPEN, LIMITS);
        assertEquals(Reason.TOO_STEEP, r.reason());
        assertTrue(r.value() > 60 && r.value() < 64, "slope " + r.value());
        // 30 along, 50 down: about 59 degrees, fine.
        assertTrue(LinkValidator.checkAdd(List.of(node(0, 150, 0), node(0, 100, 30)), OPEN, LIMITS).ok());
    }

    @Test
    void aWallBesideTheLoadedCableBlocksOnlyTheLoadedSide() {
        // Along +z the loaded cable is to the -x side (x about -1.0 to -1.5) and the return cable to the +x side (2.0 to 2.5).
        BlockSolid left = (x, y, z) -> x <= -1;
        Result r = LinkValidator.checkFinish(straight(3, 30), left, LIMITS, 0);
        assertEquals(Reason.BLOCKED_LOADED, r.reason());
    }

    @Test
    void aWallBesideTheReturnCableBlocksOnlyTheReturnSide() {
        BlockSolid right = (x, y, z) -> x >= 2;
        Result r = LinkValidator.checkFinish(straight(3, 30), right, LIMITS, 0);
        assertEquals(Reason.BLOCKED_RETURN, r.reason());
    }

    @Test
    void theBlockBelowTheCableCountsToo() {
        // The cable is 3 above the node's block floor (y 103.0 at the towers, sagging a little); a floor 1 block under it blocks.
        BlockSolid floor = (x, y, z) -> y <= 102;
        assertEquals(Reason.BLOCKED_LOADED, LinkValidator.checkFinish(straight(3, 30), floor, LIMITS, 0).reason());
        BlockSolid lower = (x, y, z) -> y <= 90;
        assertTrue(LinkValidator.checkFinish(straight(3, 30), lower, LIMITS, 0).ok());
    }

    @Test
    void theFirstBlockedSampleIsReported() {
        // Solid beyond z 40 on the loaded side: the first sample there, on the second span (z 30.5 to 60.5, samples every 2).
        BlockSolid wall = (x, y, z) -> x <= -1 && z >= 40;
        Result r = LinkValidator.checkFinish(straight(3, 30), wall, LIMITS, 0);
        assertEquals(Reason.BLOCKED_LOADED, r.reason());
        assertEquals(1, r.span());
        assertEquals(41, r.bz()); // 16 samples a span (the offset anchors are a hair over 30 apart): z 30.5 + 1.875 k
        assertTrue(r.bx() <= -1);
        // A wall on both sides reports the loaded side first, and the earlier span before a later one.
        BlockSolid both = (x, y, z) -> z >= 10;
        Result r2 = LinkValidator.checkFinish(straight(3, 30), both, LIMITS, 0);
        assertEquals(Reason.BLOCKED_LOADED, r2.reason());
        assertEquals(0, r2.span());
        assertEquals(11, r2.bz()); // z 0.5 + 1.875 k: the first at or past 10 is k = 6
    }

    @Test
    void theCableBetweenTheLastTwoNodesIsCheckedIncrementally() {
        // Heads 0..2: adding the third node checks the first span, whose directions are by then known.
        List<V3> three = straight(3, 30);
        BlockSolid wall = (x, y, z) -> x <= -1 && z >= 5 && z < 20;
        assertEquals(Reason.BLOCKED_LOADED, LinkValidator.checkAdd(three, wall, LIMITS).reason());
        assertTrue(LinkValidator.checkAdd(three.subList(0, 2), wall, LIMITS).ok(), "one span alone is not checked until the next node");
    }

    @Test
    void tooManyHeadsIsRejected() {
        Limits three = new Limits(64, 3, 1200, 64);
        // drive + 4 heads + return
        Result finish = LinkValidator.checkFinish(straight(6, 30), OPEN, three, 0);
        assertEquals(Reason.TOO_MANY_HEADS, finish.reason());
        assertEquals(4, (int) finish.value());
        // The fourth head is refused as it is added; the third is fine.
        assertTrue(LinkValidator.checkAdd(straight(4, 30), OPEN, three).ok());
        assertEquals(Reason.TOO_MANY_HEADS, LinkValidator.checkAdd(straight(5, 30), OPEN, three).reason());
    }

    @Test
    void tooLongIsRejected() {
        Limits shortLimit = new Limits(64, 48, 100, 64);
        List<V3> nodes = straight(3, 30);
        double loop = LiftPath.of(nodes).loopLength();
        assertTrue(loop > 100);
        Result r = LinkValidator.checkFinish(nodes, OPEN, shortLimit, 0);
        assertEquals(Reason.TOO_LONG, r.reason());
        assertEquals(loop, r.value(), 1e-9);
        assertTrue(LinkValidator.checkFinish(nodes, OPEN, new Limits(64, 48, loop + 0.001, 64), 0).ok());
    }

    @Test
    void aFullLevelIsRejected() {
        assertEquals(Reason.LEVEL_FULL, LinkValidator.checkFinish(straight(3, 30), OPEN, LIMITS, 64).reason());
        assertTrue(LinkValidator.checkFinish(straight(3, 30), OPEN, LIMITS, 63).ok());
    }

    @Test
    void twoNodesInTheSameColumnAreRejected() {
        List<V3> nodes = List.of(node(0, 100, 0), node(0, 90, 0));
        assertEquals(Reason.SAME_PLACE, LinkValidator.checkAdd(nodes, OPEN, LIMITS).reason());
        // A head that doubles back onto the drive's column would give the drive no direction.
        List<V3> back = List.of(node(0, 100, 0), node(0, 95, 20), node(0, 90, 0));
        assertEquals(Reason.SAME_PLACE, LinkValidator.checkAdd(back, OPEN, LIMITS).reason());
    }

    /** A drive at the top, 10 heads wandering sideways and downhill, a return station at the bottom. */
    private static List<V3> tenHeads() {
        List<V3> nodes = new ArrayList<>();
        int[] xs = {0, 8, 14, 12, 4, -6, -12, -8, 2, 10, 14, 16};
        for (int i = 0; i < 12; i++) nodes.add(node(xs[i], 140 - i * 4, i * 32));
        return nodes;
    }

    @Test
    void aTenHeadChainPasses() {
        List<V3> nodes = tenHeads();
        assertEquals(12, nodes.size());
        for (int m = 1; m < nodes.size() - 1; m++) {
            Result r = LinkValidator.checkAdd(nodes.subList(0, m + 1), OPEN, LIMITS);
            assertTrue(r.ok(), "adding node " + m + ": " + r);
        }
        Result finish = LinkValidator.checkFinish(nodes, OPEN, LIMITS, 0);
        assertTrue(finish.ok(), finish.toString());
        assertTrue(LiftPath.of(nodes).loopLength() < 1200);
    }

    // ---- the validator's cable is the path's cable ----

    /** The smallest distance from {@code p} to the loop, by fine sampling. */
    private static double distanceToLoop(LiftPath path, V3 p) {
        double best = Double.MAX_VALUE;
        for (double s = 0; s < path.loopLength(); s += 0.02) best = Math.min(best, path.pos(s).distance(p));
        return best;
    }

    @Test
    void theValidatorsCableMatchesLiftPath() {
        List<V3> nodes = List.of(node(0, 140, 0), node(20, 130, 40), node(25, 115, 80), node(10, 101, 120), node(0, 90, 150));
        LiftPath path = LiftPath.of(nodes);
        int last = nodes.size() - 1;
        // The four ends of the two cables are the path's vertices.
        assertEquals(0, LiftPath.anchor(nodes, last, true, true).distance(path.pos(0)), 1e-9);
        assertEquals(0, LiftPath.anchor(nodes, 0, true, true).distance(path.pos(path.loadedEnd())), 1e-9);
        assertEquals(0, LiftPath.anchor(nodes, 0, true, false).distance(path.pos(path.returnStart())), 1e-9);
        assertEquals(0, LiftPath.anchor(nodes, last, true, false).distance(path.pos(path.returnEnd())), 1e-9);
        // Every sampled point of every span lies on the path, on the right side.
        for (int span = 0; span < last; span++) {
            for (boolean loaded : new boolean[]{true, false}) {
                V3 a = LiftPath.anchor(nodes, span, span == 0, loaded), b = LiftPath.anchor(nodes, span + 1, span + 1 == last, loaded);
                for (int k = 0; k <= 8; k++) {
                    V3 p = LiftPath.spanPoint(a, b, k / 8.0);
                    assertTrue(distanceToLoop(path, p) < 0.03, "span " + span + (loaded ? " loaded" : " return") + " t=" + k + "/8");
                }
            }
        }
    }
}
