package io.github.adang101.alpineshred.feel;

import io.github.adang101.alpineshred.movement.RideState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lens build-up and leaf cover (README clarifications 92 and 93). */
class LensOverlayRulesTest {
    private static final int SECOND = 20;

    private static void run(LensBuildup lens, int ticks, RideState state, double speed, double lean, boolean snowfall, boolean goggles) {
        for (int i = 0; i < ticks; i++) lens.tick(state, speed, lean, snowfall, goggles);
    }

    @Test
    void powderBuildsUpNoticeablyWithinAFewSeconds() {
        LensBuildup lens = new LensBuildup();
        run(lens, 3 * SECOND, RideState.POWDER, 0.7, 0, false, false);
        assertTrue(lens.level() > 0.7, "level " + lens.level());
        assertTrue(lens.alpha(false) > 0.3);
    }

    @Test
    void aGroomedRunAtModerateSpeedStaysMild() {
        LensBuildup lens = new LensBuildup();
        run(lens, 30 * SECOND, RideState.GLIDE, 0.6, 0, false, false);
        assertTrue(lens.level() < 0.15, "level " + lens.level());
    }

    @Test
    void hardCarvingAndSnowfallAddToSpeed() {
        LensBuildup plain = new LensBuildup();
        LensBuildup carving = new LensBuildup();
        LensBuildup snowing = new LensBuildup();
        run(plain, 20 * SECOND, RideState.CARVE, 0.9, 0, false, false);
        run(carving, 20 * SECOND, RideState.CARVE, 0.9, -1, false, false);
        run(snowing, 20 * SECOND, RideState.CARVE, 0.9, 0, true, false);
        assertTrue(carving.level() > plain.level());
        assertTrue(snowing.level() > plain.level());
    }

    @Test
    void opacityNeverPassesTheCapAndReducedMotionLowersIt() {
        LensBuildup lens = new LensBuildup();
        run(lens, 60 * SECOND, RideState.POWDER, 2.0, 1, true, false);
        assertEquals(1, lens.level(), 1e-9);
        assertEquals(LensBuildup.MAX_ALPHA, lens.alpha(false), 1e-9);
        assertEquals(LensBuildup.REDUCED_MAX_ALPHA, lens.alpha(true), 1e-9);
        assertTrue(LensBuildup.MAX_ALPHA <= 0.55);
        assertTrue(lens.blotchAlpha(false) <= lens.alpha(false));
    }

    @Test
    void goggleWearersNeverBuildUpAndWipeOffTheRest() {
        LensBuildup lens = new LensBuildup();
        run(lens, 5 * SECOND, RideState.POWDER, 1.0, 0, true, false);
        assertTrue(lens.level() > 0.5);
        run(lens, SECOND, RideState.POWDER, 1.0, 0, true, true);
        assertEquals(0, lens.level(), 1e-9, "goggles on: clear within a second");
        lens.tick(RideState.AIR, 1.0, 0, false, true);
        lens.tick(RideState.POWDER, 1.0, 0, false, true);
        lens.tick(RideState.WIPEOUT, 1.0, 0, false, true);
        assertEquals(0, lens.level(), 1e-9, "no burst through goggles");
    }

    @Test
    void itClearsSlowlyOnceStopped() {
        LensBuildup lens = new LensBuildup();
        run(lens, 5 * SECOND, RideState.POWDER, 1.0, 0, false, false);
        double full = lens.level();
        run(lens, 2 * SECOND, RideState.IDLE, 0, 0, false, false);
        assertTrue(lens.level() < full && lens.level() > full - 0.4, "still there after 2 s: " + lens.level());
        run(lens, 15 * SECOND, RideState.IDLE, 0, 0, false, false);
        assertEquals(0, lens.level(), 1e-9);
    }

    @Test
    void aPowderLandingAndAWipeoutEachThrowABurst() {
        LensBuildup landing = new LensBuildup();
        landing.tick(RideState.AIR, 0.8, 0, false, false);
        landing.tick(RideState.POWDER, 0.8, 0, false, false);
        assertTrue(landing.level() >= LensBuildup.POWDER_LANDING_BURST, "level " + landing.level());

        LensBuildup slow = new LensBuildup();
        slow.tick(RideState.AIR, 0.1, 0, false, false);
        slow.tick(RideState.POWDER, 0.1, 0, false, false);
        assertTrue(slow.level() < LensBuildup.POWDER_LANDING_BURST, "a soft drop is no burst");

        LensBuildup wipeout = new LensBuildup();
        wipeout.tick(RideState.GLIDE, 0.3, 0, false, false);
        wipeout.tick(RideState.WIPEOUT, 0.3, 0, false, false);
        assertTrue(wipeout.level() >= LensBuildup.WIPEOUT_BURST - 1e-9, "level " + wipeout.level());
        double after = wipeout.level();
        wipeout.tick(RideState.WIPEOUT, 0.3, 0, false, false);
        assertTrue(wipeout.level() <= after, "staying wiped out is one burst, not one per tick");
    }

    @Test
    void notRidingBuildsNothing() {
        LensBuildup lens = new LensBuildup();
        run(lens, 10 * SECOND, RideState.OFF, 2.0, 1, true, false);
        run(lens, 10 * SECOND, RideState.LIFT, 2.0, 1, true, false);
        assertEquals(0, lens.level(), 1e-9);
    }

    @Test
    void resetClears() {
        LensBuildup lens = new LensBuildup();
        run(lens, 5 * SECOND, RideState.POWDER, 1.0, 0, false, false);
        lens.reset();
        assertEquals(0, lens.level(), 1e-9);
    }

    // ---- leaves ---------------------------------------------------------------------------------

    @Test
    void leavesCloseQuicklyAndFadeOverTheFadeWindow() {
        LeafCover leaves = new LeafCover();
        for (int i = 0; i < 4; i++) leaves.tick(true);
        assertEquals(LeafCover.MAX_ALPHA, leaves.alpha(1, false), 1e-9);
        for (int i = 0; i < LeafCover.FADE_TICKS - 1; i++) leaves.tick(false);
        assertTrue(leaves.alpha(1, false) > 0, "still fading one tick before the end");
        leaves.tick(false);
        assertEquals(0, leaves.alpha(1, false), 1e-9);
    }

    @Test
    void aSingleTickInLeavesIsStillFelt() {
        LeafCover leaves = new LeafCover();
        leaves.tick(true);
        leaves.tick(false);
        assertTrue(leaves.alpha(1, false) > 0);
    }

    @Test
    void gogglesCutLeavesToSixtyPercentAndTheFrameIsInterpolated() {
        LeafCover leaves = new LeafCover();
        for (int i = 0; i < 4; i++) leaves.tick(true);
        assertEquals(LeafCover.MAX_ALPHA * 0.6, leaves.alpha(1, true), 1e-9);
        LeafCover opening = new LeafCover();
        opening.tick(true);
        assertEquals(0, opening.alpha(0, false), 1e-9);
        assertEquals(LeafCover.RISE * LeafCover.MAX_ALPHA / 2, opening.alpha(0.5f, false), 1e-9);
    }
}
