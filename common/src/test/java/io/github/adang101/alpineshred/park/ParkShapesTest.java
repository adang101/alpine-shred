package io.github.adang101.alpineshred.park;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 11: ramp steps and quarter pipe columns. */
class ParkShapesTest {
    @Test
    void rampRunsClimbOneBlockInEvenSteps() {
        for (RampSlope slope : RampSlope.values()) {
            double previous = 0;
            for (int step = 0; step <= slope.maxStep(); step++) {
                for (int column = 0; column < RampSlope.STEPS; column++) {
                    double top = slope.columnTop(step, column);
                    assertEquals(slope.rise / RampSlope.STEPS, top - previous, 1e-9, slope + " step " + step + " column " + column);
                    previous = top;
                }
            }
            assertEquals(1.0, previous, 1e-9, slope + " run tops out at one block");
        }
        assertEquals(14.04, RampSlope.QUARTER.degrees(), 0.01);
        assertEquals(26.57, RampSlope.HALF.degrees(), 0.01);
        assertEquals(45.0, RampSlope.FULL.degrees(), 0.01);
        // Eight 2-pixel steps per block: at most 2 pixels of climb per step, so ground snap and step-up stay smooth.
        assertTrue(RampSlope.FULL.rise / RampSlope.STEPS <= 2.0 / 16 + 1e-9);
    }

    @Test
    void pipeCellsFollowTheArc() {
        List<PipeArc.Cell> r3 = PipeArc.cells(3);
        assertEquals(List.of(new PipeArc.Cell(0, 0), new PipeArc.Cell(1, 0), new PipeArc.Cell(2, 0), new PipeArc.Cell(2, 1),
                new PipeArc.Cell(2, 2)), r3);
        assertEquals(10, PipeArc.cellCount(5));
        assertEquals(3.0, PipeArc.height(3, 3), 1e-9, "vertical at the coping");
        assertEquals(0.0, PipeArc.height(3, 0), 1e-9, "flat at the transition");
        assertEquals(Math.PI / 2, PipeArc.theta(5, 5), 1e-9);
        assertEquals(2.5, PipeArc.u(5, Math.PI / 6), 1e-9);
    }

    @Test
    void pipeColumnsNeverRiseAboveTheArc() {
        for (int radius : PipeArc.RADII) {
            for (int index = 0; index < PipeArc.cellCount(radius); index++) {
                PipeArc.Cell c = PipeArc.cell(radius, index);
                double previous = -1;
                for (int column = 0; column < PipeArc.COLUMNS; column++) {
                    double top = PipeArc.columnTop(radius, index, column);
                    assertTrue(top >= previous, "columns rise toward the wall");
                    previous = top;
                    // Anywhere in the column the arc is at least as high as the column's top.
                    for (double f = 0; f <= 1.0; f += 0.25) {
                        double u = c.i() + (column + f) / PipeArc.COLUMNS;
                        double arc = Math.max(0, Math.min(1, PipeArc.height(radius, u) - c.j()));
                        assertTrue(top <= arc + 1e-9, "radius " + radius + " index " + index + " column " + column + " above the arc");
                    }
                }
            }
        }
    }
}
