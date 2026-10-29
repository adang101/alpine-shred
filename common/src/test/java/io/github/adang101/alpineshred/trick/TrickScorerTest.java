package io.github.adang101.alpineshred.trick;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 8's catalog, landing windows, score formula and the server's bounds on reports. */
class TrickScorerTest {
    private static int[] grabs(GrabType type, int ticks) {
        int[] g = new int[GrabType.values().length];
        g[type.ordinal()] = ticks;
        return g;
    }

    private static AirReport air(double yaw, double pitch, int[] grabs, double apex, int ticks) {
        return new AirReport(yaw, pitch, grabs, apex, ticks, 0, TrickAngles.headingError(0, yaw),
                TrickAngles.residual(pitch, 360), false, false, false);
    }

    @Test
    void catalogPoints() {
        assertEquals(100.0, TrickScorer.spinPoints(1), 1e-9);
        assertEquals(300.0, TrickScorer.spinPoints(2), 1e-9);
        assertEquals(600.0, TrickScorer.spinPoints(3), 1e-9);
        assertEquals(1000.0, TrickScorer.spinPoints(4), 1e-9);
        assertEquals(1500.0, TrickScorer.spinPoints(5), 1e-9);
        assertEquals(2100.0, TrickScorer.spinPoints(6), 1e-9);
        assertEquals(400.0, TrickScorer.flipPoints(1), 1e-9);
        assertEquals(1600.0, TrickScorer.flipPoints(2), 1e-9);
        assertEquals(60 + 120 * 1.0, TrickScorer.grabPoints(GrabType.INDY, 20, false), 1e-9);
        assertEquals((60 + 120 * 1.5) * 1.5, TrickScorer.grabPoints(GrabType.METHOD, 60, false), 1e-9, "capped at 1.5 s");
        assertEquals((60 + 120 * 0.5) * 1.2 * 1.1, TrickScorer.grabPoints(GrabType.NOSE, 10, true), 1e-9, "mitts add 10%");
        assertEquals(0.0, TrickScorer.grabPoints(GrabType.TAIL, 2, false), 1e-9, "a two-tick brush is no grab");
        assertEquals(60 * 0.4, TrickScorer.airTimePoints(20), 1e-9);
        assertEquals(0.0, TrickScorer.airTimePoints(12), 1e-9);
        assertEquals(25 * 2.5, TrickScorer.heightPoints(4.0), 1e-9);
    }

    @Test
    void spinsCountToTheNearestHalfTurn() {
        assertEquals(1, TrickAngles.halfTurns(170));
        assertEquals(2, TrickAngles.halfTurns(-350));
        assertEquals(0, TrickAngles.halfTurns(80));
        assertEquals(10, TrickAngles.headingError(0, 170), 1e-9);
        assertEquals(10, TrickAngles.headingError(0, 370), 1e-9);
        assertEquals(0, TrickAngles.headingError(0, 180), 1e-9, "a switch landing is aligned");
    }

    @Test
    void landingWindowsAndForgiveness() {
        assertEquals(LandingQuality.PERFECT, LandingQuality.evaluate(10, 5, 4, 1));
        assertEquals(LandingQuality.CLEAN, LandingQuality.evaluate(10, 5, 20, 1), "perfect needs the slope");
        assertEquals(LandingQuality.CLEAN, LandingQuality.evaluate(30, 25, 0, 1));
        assertEquals(LandingQuality.SKETCHY, LandingQuality.evaluate(50, 0, 0, 1));
        assertEquals(LandingQuality.BAIL, LandingQuality.evaluate(70, 0, 0, 1));
        assertEquals(LandingQuality.BAIL, LandingQuality.evaluate(0, 50, 0, 1));
        assertEquals(LandingQuality.SKETCHY, LandingQuality.evaluate(70, 0, 0, 1.2), "forgiving gear widens the windows");
    }

    @Test
    void anAirSessionScoresItsParts() {
        AirReport r = air(360, 0, grabs(GrabType.INDY, 20), 3.5, 30);
        List<TrickPart> parts = TrickScorer.air(r, false);
        double sum = parts.stream().mapToDouble(TrickPart::points).sum();
        assertEquals(300 + 180 + 60 * 0.9 + 25 * 2.0, sum, 1e-9);
        assertTrue(TrickScorer.air(air(360, 0, grabs(GrabType.INDY, 20), 3.5, 1), false).isEmpty(), "under 2 ticks scores nothing");
    }

    @Test
    void comboFormula() {
        Combo combo = new Combo();
        // Landing 1: a 360 (300) landed clean. Landing 2: an indy grab for 1 s (180), perfect.
        combo.land(List.of(new TrickPart("spin_360", TrickPart.Category.SPIN, 300)), LandingQuality.CLEAN, false);
        combo.land(List.of(new TrickPart("grab_indy", TrickPart.Category.GRAB, 180)), LandingQuality.PERFECT, false);
        double base = 300 + 180 * 1.25;
        double v = 1 + 0.2 * (2 - 1), c = 1 + 0.25;
        assertEquals((int) Math.round(base * v * c), combo.total(50000));
        // A repeat of the 360 in the same combo scores half, and a switch landing gives 1.1.
        double added = combo.land(List.of(new TrickPart("spin_360", TrickPart.Category.SPIN, 300)), LandingQuality.CLEAN, true);
        assertEquals(150 * 1.1, added, 1e-9);
        assertEquals(1000, combo.total(1000), "capped");
        assertEquals(-1.0, combo.land(List.of(), LandingQuality.BAIL, false), 1e-9, "a bail loses the combo");
        for (int i = 0; i < 9; i++) combo.land(List.of(new TrickPart("x" + i, null, 1)), LandingQuality.CLEAN, false);
        assertEquals(3.0, combo.comboMultiplier(), 1e-9, "combo multiplier caps at 3");
    }

    @Test
    void comboRunsOutOnTheGround() {
        Combo combo = new Combo();
        combo.land(List.of(new TrickPart("spin_180", TrickPart.Category.SPIN, 100)), LandingQuality.CLEAN, false);
        for (int i = 0; i < Combo.WINDOW_TICKS; i++) assertTrue(!combo.tickGround(0.5), "ran out early at " + i);
        assertTrue(combo.tickGround(0.5), "60 ground ticks without a landing");
        Combo slow = new Combo();
        slow.land(List.of(new TrickPart("spin_180", TrickPart.Category.SPIN, 100)), LandingQuality.CLEAN, false);
        assertTrue(slow.tickGround(0.05), "dropping below 0.1 ends it");
    }

    @Test
    void grindParts() {
        GrindReport g = new GrindReport(0, 0, 0, false, 1, 40, false, 8, 40, 2);
        List<TrickPart> parts = TrickScorer.grind(g);
        assertEquals(70 * 2 + 2 * 100, parts.stream().mapToDouble(TrickPart::points).sum(), 1e-9);
        GrindReport box = new GrindReport(0, 0, 0, true, 0, 5, true, 8, 20, 0);
        assertEquals(40 * 1.0 * 1.1 * 0.8, TrickScorer.grind(box).get(0).points(), 1e-9);
        assertEquals(LandingQuality.PERFECT, TrickScorer.grindEntry(0, 10));
        assertEquals(LandingQuality.CLEAN, TrickScorer.grindEntry(0, 20));
        assertEquals(LandingQuality.PERFECT, TrickScorer.grindEntry(3, 80));
    }

    @Test
    void forgedReportsAreRejected() {
        AirReport honest = air(360, 0, grabs(GrabType.INDY, 20), 3.0, 30);
        assertNull(TrickValidator.check(honest, 30, 3.2, 24, 24));
        // Rule 1: a 1080 in 30 ticks of air on a board (max 24 per tick: 822 allowed).
        assertNotNull(TrickValidator.check(air(1080, 0, grabs(GrabType.NONE, 0), 3, 30), 30, 3.2, 24, 24));
        // More air than the server saw.
        assertNotNull(TrickValidator.check(air(360, 0, grabs(GrabType.NONE, 0), 3, 60), 30, 3.2, 24, 24));
        // Rule 2: a double flip in 30 ticks.
        assertNotNull(TrickValidator.check(air(0, 720, grabs(GrabType.NONE, 0), 3, 30), 30, 3.2, 24, 24));
        // Rule 3: a grab longer than the air.
        assertNotNull(TrickValidator.check(air(0, 0, grabs(GrabType.METHOD, 40), 3, 30), 30, 3.2, 24, 24));
        // Rule 4: a clean landing claimed after a 270.
        AirReport crooked = new AirReport(270, 0, grabs(GrabType.NONE, 0), 3, 30, 0, 5, 0, false, false, false);
        assertNotNull(TrickValidator.check(crooked, 30, 3.2, 24, 24));
        // Higher than the server saw.
        assertNotNull(TrickValidator.check(air(0, 0, grabs(GrabType.NONE, 0), 9, 30), 30, 3.2, 24, 24));
        // Grinds: longer than seen, or faster than the cap.
        assertNull(TrickValidator.check(new GrindReport(0, 0, 0, false, 0, 5, false, 8, 20, 0), 22, 1.6));
        assertNotNull(TrickValidator.check(new GrindReport(0, 0, 0, false, 0, 5, false, 8, 200, 0), 22, 1.6));
        assertNotNull(TrickValidator.check(new GrindReport(0, 0, 0, false, 0, 5, false, 80, 20, 0), 22, 1.6));
    }
}
