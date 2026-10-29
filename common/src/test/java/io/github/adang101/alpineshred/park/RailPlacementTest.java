package io.github.adang101.alpineshred.park;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 10, "Smart placement": the rules table, connections and the wrench's alternatives. */
class RailPlacementTest {
    static final class World implements RailPlacement.World {
        final Map<RailPlacement.Cell, RailPlacement.Piece> rails = new HashMap<>();
        final Set<RailPlacement.Cell> solid = new HashSet<>();

        World rail(int x, int y, int z, RailShape shape, Facing facing) {
            rails.put(new RailPlacement.Cell(x, y, z), new RailPlacement.Piece(shape, facing));
            return this;
        }

        /** Ground: solid blocks up to and including {@code top} in column (x, z). */
        World ground(int x, int z, int top) {
            for (int y = -3; y <= top; y++) solid.add(new RailPlacement.Cell(x, y, z));
            return this;
        }

        @Override
        public RailPlacement.Piece railAt(RailPlacement.Cell c) {
            return rails.get(c);
        }

        @Override
        public boolean solid(RailPlacement.Cell c) {
            return solid.contains(c);
        }
    }

    static RailPlacement.Cell cell(int x, int y, int z) {
        return new RailPlacement.Cell(x, y, z);
    }

    static RailPlacement.Piece piece(RailShape shape, Facing facing) {
        return new RailPlacement.Piece(shape, facing);
    }

    /** Level ground at y -1 under a 7 by 7 area, so rails at y 0 rest on it. */
    static World level() {
        World w = new World();
        for (int x = -3; x <= 3; x++) {
            for (int z = -6; z <= 3; z++) w.ground(x, z, -1);
        }
        return w;
    }

    @Test
    void noRailNearbyFollowsThePlayer() {
        RailPlacement.Choice c = RailPlacement.choose(level(), cell(0, 0, 0), Facing.EAST, false);
        assertEquals(piece(RailShape.FLAT, Facing.EAST), c.piece());
        assertNull(c.reshapeAt());
    }

    @Test
    void sneakingForcesFlatAlongTheFacing() {
        World w = level().rail(0, 0, 0, RailShape.FLAT, Facing.NORTH);
        assertEquals(piece(RailShape.FLAT, Facing.WEST), RailPlacement.choose(w, cell(0, 0, -1), Facing.WEST, true).piece());
    }

    @Test
    void extendsALineOnLevelGround() {
        World w = level().rail(0, 0, 0, RailShape.FLAT, Facing.NORTH);
        assertEquals(piece(RailShape.FLAT, Facing.NORTH), RailPlacement.choose(w, cell(0, 0, -1), Facing.EAST, false).piece());
        RailPlacement.Piece back = RailPlacement.choose(w, cell(0, 0, 1), Facing.EAST, false).piece();
        assertTrue(back.shape() == RailShape.FLAT && (back.facing() == Facing.NORTH || back.facing() == Facing.SOUTH),
                "extending backward stays on the axis: " + back);
    }

    @Test
    void climbsWhereTheGroundStepsUpAndFinishesTheRise() {
        World w = level().rail(0, 0, 0, RailShape.FLAT, Facing.NORTH);
        for (int z = -6; z <= -2; z++) w.ground(0, z, 0); // a plateau one block higher from z -2 on
        assertEquals(piece(RailShape.RISE_A, Facing.NORTH), RailPlacement.choose(w, cell(0, 0, -1), Facing.NORTH, false).piece());
        w.rail(0, 0, -1, RailShape.RISE_A, Facing.NORTH);
        assertEquals(piece(RailShape.RISE_B, Facing.NORTH), RailPlacement.choose(w, cell(0, 1, -2), Facing.NORTH, false).piece(),
                "the second half is picked automatically");
        w.rail(0, 1, -2, RailShape.RISE_B, Facing.NORTH);
        assertEquals(piece(RailShape.FLAT, Facing.NORTH), RailPlacement.choose(w, cell(0, 1, -3), Facing.NORTH, false).piece());
    }

    @Test
    void fallsWhereTheGroundDrops() {
        World w = new World();
        for (int z = -1; z <= 1; z++) w.ground(0, z, -1);
        for (int z = -6; z <= -2; z++) w.ground(0, z, -2); // one block lower from z -2 on
        w.rail(0, 0, 0, RailShape.FLAT, Facing.NORTH);
        assertEquals(piece(RailShape.RISE_B, Facing.SOUTH), RailPlacement.choose(w, cell(0, 0, -1), Facing.NORTH, false).piece(),
                "the falling half of a rise, facing back toward the end");
        w.rail(0, 0, -1, RailShape.RISE_B, Facing.SOUTH);
        assertEquals(piece(RailShape.RISE_A, Facing.SOUTH), RailPlacement.choose(w, cell(0, -1, -2), Facing.NORTH, false).piece());
        w.rail(0, -1, -2, RailShape.RISE_A, Facing.SOUTH);
        RailPlacement.Piece after = RailPlacement.choose(w, cell(0, -1, -3), Facing.NORTH, false).piece();
        assertEquals(RailShape.FLAT, after.shape());
    }

    @Test
    void placingBesideTheEndOfALineTurnsItIntoACurve() {
        World w = level().rail(0, 0, 1, RailShape.FLAT, Facing.NORTH).rail(0, 0, 0, RailShape.FLAT, Facing.NORTH);
        RailPlacement.Choice c = RailPlacement.choose(w, cell(1, 0, 0), Facing.NORTH, false);
        assertEquals(piece(RailShape.FLAT, Facing.EAST), c.piece());
        assertEquals(cell(0, 0, 0), c.reshapeAt());
        assertEquals(piece(RailShape.CURVE, Facing.NORTH), c.reshapeTo(), "south edge to east edge");
        // Placed, the three pieces form one line.
        w.rail(0, 0, 0, RailShape.CURVE, Facing.NORTH).rail(1, 0, 0, RailShape.FLAT, Facing.EAST);
        boolean[] ends = RailPlacement.connections(w, cell(0, 0, 0), piece(RailShape.CURVE, Facing.NORTH));
        assertTrue(ends[0] && ends[1], "curve joined at both ends");
    }

    @Test
    void fillsAGapBetweenTwoLines() {
        World w = level().rail(0, 0, 1, RailShape.FLAT, Facing.NORTH).rail(0, 0, -1, RailShape.FLAT, Facing.NORTH);
        RailPlacement.Piece p = RailPlacement.choose(w, cell(0, 0, 0), Facing.EAST, false).piece();
        assertEquals(RailShape.FLAT, p.shape());
        boolean[] ends = RailPlacement.connections(w.rail(0, 0, 0, p.shape(), p.facing()), cell(0, 0, 0), p);
        assertTrue(ends[0] && ends[1]);
    }

    @Test
    void connectionsDriveEndCaps() {
        World w = level().rail(0, 0, 0, RailShape.FLAT, Facing.NORTH);
        boolean[] alone = RailPlacement.connections(w, cell(0, 0, 0), piece(RailShape.FLAT, Facing.NORTH));
        assertFalse(alone[0] || alone[1]);
        w.rail(0, 0, -1, RailShape.FLAT, Facing.NORTH);
        boolean[] one = RailPlacement.connections(w, cell(0, 0, 0), piece(RailShape.FLAT, Facing.NORTH));
        assertFalse(one[0], "end A (south) open");
        assertTrue(one[1], "end B (north) joined");
    }

    @Test
    void wrenchOffersOnlyPiecesThatKeepTheLine() {
        World w = level().rail(0, 0, 1, RailShape.FLAT, Facing.NORTH).rail(0, 0, 0, RailShape.FLAT, Facing.NORTH)
                .rail(0, 0, -1, RailShape.FLAT, Facing.NORTH);
        List<RailPlacement.Piece> middle = RailPlacement.alternatives(w, cell(0, 0, 0), piece(RailShape.FLAT, Facing.NORTH));
        assertEquals(1, middle.size(), "a straight piece between two straight neighbours has no alternative: " + middle);

        World end = level().rail(0, 0, 1, RailShape.FLAT, Facing.NORTH).rail(0, 0, 0, RailShape.FLAT, Facing.NORTH);
        List<RailPlacement.Piece> tip = RailPlacement.alternatives(end, cell(0, 0, 0), piece(RailShape.FLAT, Facing.NORTH));
        assertEquals(piece(RailShape.FLAT, Facing.NORTH), tip.get(0), "the current piece comes first");
        assertTrue(tip.contains(piece(RailShape.CURVE, Facing.NORTH)), "can turn right: " + tip);
        assertTrue(tip.contains(piece(RailShape.CURVE, Facing.EAST)), "can turn left: " + tip);
        assertTrue(tip.contains(piece(RailShape.RISE_A, Facing.NORTH)), "can start a rise: " + tip);
        assertFalse(tip.contains(piece(RailShape.FLAT, Facing.EAST)), "a cross piece breaks the line");
    }

    @Test
    void curveFacingsCoverEveryCorner() {
        assertEquals(Facing.NORTH, RailPlacement.curveConnecting(Facing.SOUTH, Facing.EAST));
        assertEquals(Facing.EAST, RailPlacement.curveConnecting(Facing.WEST, Facing.SOUTH));
        assertEquals(Facing.SOUTH, RailPlacement.curveConnecting(Facing.NORTH, Facing.WEST));
        assertEquals(Facing.WEST, RailPlacement.curveConnecting(Facing.EAST, Facing.NORTH));
        assertNull(RailPlacement.curveConnecting(Facing.NORTH, Facing.SOUTH));
    }
}
