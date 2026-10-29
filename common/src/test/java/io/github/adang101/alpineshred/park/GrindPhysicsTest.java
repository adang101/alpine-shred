package io.github.adang101.alpineshred.park;

import io.github.adang101.alpineshred.movement.MovementTuning;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 10, grind detection steps 2 to 5, and the section 19 rail feel target with real joins. */
class GrindPhysicsTest {
    private static final MovementTuning T = MovementTuning.DEFAULT;
    private static final double RAIL = 0.0008;
    private static final double BPS = 1 / 20.0;

    /** A line of rail pieces in a map keyed by block, looked up like the game does. */
    static final class Line implements GrindPhysics.Lookup {
        final Map<String, GrindPath> blocks = new HashMap<>();

        Line add(RailShape shape, Facing facing, int x, int y, int z) {
            long id = ((long) x & 0xFFFF) << 32 | ((long) y & 0xFFFF) << 16 | ((long) z & 0xFFFF);
            blocks.put(x + "," + y + "," + z, new GrindPath(id, RailPath.at(shape, facing, x, y, z), GrindMaterial.METAL, RAIL));
            return this;
        }

        GrindPath at(int x, int y, int z) {
            return blocks.get(x + "," + y + "," + z);
        }

        @Override
        public Iterable<GrindPath> near(V3 p) {
            List<GrindPath> out = new ArrayList<>();
            int bx = (int) Math.floor(p.x()), by = (int) Math.floor(p.y()), bz = (int) Math.floor(p.z());
            for (int x = bx - 1; x <= bx + 1; x++) {
                for (int y = by - 1; y <= by + 1; y++) {
                    for (int z = bz - 1; z <= bz + 1; z++) {
                        GrindPath g = blocks.get(x + "," + y + "," + z);
                        if (g != null) out.add(g);
                    }
                }
            }
            return out;
        }
    }

    static Line flatLine(int pieces) {
        Line line = new Line();
        for (int i = 0; i < pieces; i++) line.add(RailShape.FLAT, Facing.NORTH, 0, 0, -i);
        return line;
    }

    @Test
    void catchesARailFromAbove() {
        Line line = flatLine(3);
        // Feet 0.2 above the rail, 0.1 to the side, moving north at 0.4.
        GrindPhysics.Grind g = GrindPhysics.capture(line.near(new V3(0.6, 0.7, -0.5)), new V3(0.6, 0.7, -0.5),
                new V3(0, -0.1, -0.4), 180);
        assertNotNull(g, "no capture");
        assertEquals(1, g.direction(), "riding toward end B (north)");
        assertEquals(0.4 * 0.95, g.speed(), 1e-9, "5% entry loss");
        assertEquals(GrindPhysics.Variant.FIFTY_FIFTY, g.variant());
    }

    @Test
    void captureWindow() {
        Line line = flatLine(1);
        V3 vel = new V3(0, 0, -0.4);
        Iterable<GrindPath> c = line.near(new V3(0.5, 0.5, 0.5));
        assertNull(GrindPhysics.capture(c, new V3(0.5, 0.2, 0.5), vel, 180), "0.3 below is too low");
        assertNotNull(GrindPhysics.capture(c, new V3(0.5, 0.94, 0.5), vel, 180), "0.44 above still catches");
        assertNull(GrindPhysics.capture(c, new V3(0.5, 0.97, 0.5), vel, 180), "0.47 above is too high");
        assertNull(GrindPhysics.capture(c, new V3(0.5, 0.6, 0.5), new V3(0, 0, -0.07), 180), "too slow");
        // Approaching at 30 degrees: 0.45 radius.
        V3 slant = new V3(Math.sin(Math.toRadians(30)) * 0.4, 0, -Math.cos(Math.toRadians(30)) * 0.4);
        assertNotNull(GrindPhysics.capture(c, new V3(0.94, 0.6, 0.5), slant, 180));
        assertNull(GrindPhysics.capture(c, new V3(0.97, 0.6, 0.5), slant, 180), "0.47 off at 30 degrees");
        // Approaching within 20 degrees: grind assist widens the catch to 0.65.
        V3 along = new V3(Math.sin(Math.toRadians(10)) * 0.4, 0, -Math.cos(Math.toRadians(10)) * 0.4);
        assertNotNull(GrindPhysics.capture(c, new V3(1.1, 0.6, 0.5), along, 180), "assist at 0.6");
        assertNull(GrindPhysics.capture(c, new V3(1.2, 0.6, 0.5), along, 180), "0.7 off is beyond the assist");
        // Nearly straight across: too little speed along the rail survives the projection.
        V3 across = new V3(0.4, 0, -0.4 * Math.tan(Math.toRadians(5)));
        assertNull(GrindPhysics.capture(c, new V3(0.5, 0.6, 0.5), across, 180), "85 degrees off axis");
    }

    @Test
    void variantsFollowTheBoardAngleToTheRail() {
        assertEquals(GrindPhysics.Variant.FIFTY_FIFTY, GrindPhysics.variant(10));
        assertEquals(GrindPhysics.Variant.FIFTY_FIFTY, GrindPhysics.variant(-170), "switch 50-50");
        assertEquals(GrindPhysics.Variant.NOSE, GrindPhysics.variant(45));
        assertEquals(GrindPhysics.Variant.TAIL, GrindPhysics.variant(-45));
        assertEquals(GrindPhysics.Variant.BOARDSLIDE, GrindPhysics.variant(80));
        assertEquals(GrindPhysics.Variant.BOARDSLIDE, GrindPhysics.variant(-95));
    }

    /** Section 19: a 10-piece flat rail entered at 8 b/s exits with at least 80% of the entry speed. */
    @Test
    void tenPieceChainExitsWithAtLeast80Percent() {
        Line line = flatLine(10);
        double entry = 8 * BPS;
        GrindPhysics.Grind g = GrindPhysics.capture(line.near(new V3(0.5, 0.6, 0.9)), new V3(0.5, 0.6, 0.9),
                new V3(0, 0, -entry), 180);
        assertNotNull(g);
        int ticks = 0;
        GrindPhysics.Step step;
        do {
            step = GrindPhysics.advance(g, line, T, 1.0);
            g = step.grind();
            ticks++;
        } while (step.exit() == GrindPhysics.Exit.NONE && ticks < 200);
        assertEquals(GrindPhysics.Exit.END, step.exit());
        assertEquals(-9.0, step.position().z(), 1e-9, "left at the far end of piece 10");
        assertTrue(step.velocity().length() >= 0.8 * entry, "exit at " + step.velocity().length() / entry * 100 + "% of entry");
        assertEquals(0.5, step.position().y(), 1e-9);
    }

    @Test
    void followsACurveAndKeepsTheBoardAngle() {
        Line line = new Line()
                .add(RailShape.FLAT, Facing.NORTH, 0, 0, 0)
                .add(RailShape.CURVE, Facing.NORTH, 0, 0, -1)
                .add(RailShape.FLAT, Facing.EAST, 1, 0, -1)
                .add(RailShape.FLAT, Facing.EAST, 2, 0, -1);
        GrindPhysics.Grind g = GrindPhysics.capture(line.near(new V3(0.5, 0.6, 0.8)), new V3(0.5, 0.6, 0.8),
                new V3(0, 0, -0.3), 180 + 90); // board across the rail: a boardslide
        assertNotNull(g);
        assertEquals(GrindPhysics.Variant.BOARDSLIDE, g.variant());
        double relative = g.relativeHeading();
        GrindPhysics.Step step = null;
        for (int i = 0; i < 40; i++) {
            step = GrindPhysics.advance(g, line, T, 1.0);
            g = step.grind();
            if (step.exit() != GrindPhysics.Exit.NONE) break;
        }
        assertNotNull(step);
        assertEquals(GrindPhysics.Exit.END, step.exit());
        assertEquals(3.0, step.position().x(), 1e-9, "left at the east end");
        assertTrue(step.velocity().x() > 0, "heading east after the curve: " + step.velocity());
        assertEquals(relative, g.relativeHeading(), 1e-9, "board keeps its angle to the rail");
        assertEquals(-90 + relative, g.heading(), 1e-6, "board turned with the rail");
    }

    @Test
    void risesSlowAndDropsSpeedUp() {
        Line up = new Line()
                .add(RailShape.FLAT, Facing.NORTH, 0, 0, 0)
                .add(RailShape.RISE_A, Facing.NORTH, 0, 0, -1)
                .add(RailShape.RISE_B, Facing.NORTH, 0, 1, -2)
                .add(RailShape.FLAT, Facing.NORTH, 0, 1, -3);
        GrindPhysics.Grind g = GrindPhysics.capture(up.near(new V3(0.5, 0.6, 0.9)), new V3(0.5, 0.6, 0.9), new V3(0, 0, -0.5), 180);
        assertNotNull(g);
        double start = g.speed();
        GrindPhysics.Step step;
        int ticks = 0;
        do {
            step = GrindPhysics.advance(g, up, T, 1.0);
            g = step.grind();
        } while (step.exit() == GrindPhysics.Exit.NONE && ++ticks < 100);
        assertEquals(GrindPhysics.Exit.END, step.exit(), "climbed to the top");
        assertEquals(1.5, step.position().y(), 1e-9);
        assertTrue(step.velocity().length() < start - 0.02, "climbing should cost speed: " + step.velocity().length() + " from " + start);

        // The same pieces ridden the other way are a down rail.
        GrindPhysics.Grind down = GrindPhysics.capture(up.near(new V3(0.5, 1.6, -3.1)), new V3(0.5, 1.6, -3.1), new V3(0, 0, 0.5), 0);
        assertNotNull(down);
        assertEquals(-1, down.direction());
        double downStart = down.speed();
        do {
            step = GrindPhysics.advance(down, up, T, 1.0);
            down = step.grind();
        } while (step.exit() == GrindPhysics.Exit.NONE && ++ticks < 200);
        assertEquals(0.5, step.position().y(), 1e-9, "down at the bottom");
        assertTrue(step.velocity().length() > downStart, "dropping should gain speed");
    }

    @Test
    void aPieceThatDoesNotContinueTheLineEndsTheGrind() {
        // A flat piece turned across the line, beside the first one's end, does not continue it.
        Line line = new Line()
                .add(RailShape.FLAT, Facing.NORTH, 0, 0, 0)
                .add(RailShape.FLAT, Facing.EAST, 0, 0, -1);
        GrindPhysics.Grind g = GrindPhysics.capture(line.near(new V3(0.5, 0.6, 0.5)), new V3(0.5, 0.6, 0.5), new V3(0, 0, -0.3), 180);
        assertNotNull(g);
        GrindPhysics.Step step = GrindPhysics.advance(g, line, T, 1.0);
        for (int i = 0; i < 10 && step.exit() == GrindPhysics.Exit.NONE; i++) step = GrindPhysics.advance(step.grind(), line, T, 1.0);
        assertEquals(GrindPhysics.Exit.END, step.exit());
        assertEquals(0.0, step.position().z(), 1e-9, "left at the end of the first piece");
    }

    @Test
    void waxedBoardsAndRailsSlideFarther() {
        Line line = flatLine(40);
        GrindPhysics.Grind plain = GrindPhysics.capture(line.near(new V3(0.5, 0.6, 0.9)), new V3(0.5, 0.6, 0.9), new V3(0, 0, -0.2), 180);
        GrindPhysics.Grind waxed = plain;
        for (int i = 0; i < 60; i++) {
            plain = GrindPhysics.advance(plain, line, T, 1.0).grind();
            waxed = GrindPhysics.advance(waxed, line, T, 0.85).grind();
        }
        assertTrue(waxed.speed() > plain.speed(), "wax should keep more speed");
    }
}
