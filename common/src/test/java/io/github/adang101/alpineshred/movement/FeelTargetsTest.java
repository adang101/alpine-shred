package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearStats;
import io.github.adang101.alpineshred.trick.AirRotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feel acceptance targets from design doc section 19, run against the default tuning and a
 * neutral board. These are the numbers the phase 2 exit gate must meet; if play-testing shows a
 * target is wrong, change the target here and in the doc together.
 */
class FeelTargetsTest {
    private static final MovementTuning T = MovementTuning.DEFAULT;
    private static final GearStats BOARD = GearStats.DEFAULTS.get("snowboard");
    private static final double BPS = 1.0 / 20.0; // blocks per tick per block-per-second

    @Test
    void twentyDegreeGroomedSlopeReaches20BpsIn6To9SecondsAndStaysUnderTheCap() {
        for (TrackSim.Terrain terrain : new TrackSim.Terrain[]{
                TrackSim.Terrain.plane(20, SurfaceProfile.GROOMED),
                TrackSim.Terrain.staircase(Math.tan(Math.toRadians(20)), SurfaceProfile.GROOMED)}) {
            TrackSim sim = new TrackSim(T, BOARD, terrain, 0.5, 0);
            int reached = -1;
            for (int i = 0; i < 1200; i++) {
                sim.tick(SlideModel.Input.coast(0));
                if (reached < 0 && sim.state.speed >= 20 * BPS) reached = sim.ticks;
            }
            double seconds = reached / 20.0;
            assertTrue(reached > 0 && seconds >= 6 && seconds <= 9, "reached 20 b/s after " + seconds + " s");
            double terminal = sim.state.speed / BPS;
            assertTrue(terminal < 28, "terminal " + terminal + " b/s must level off below the 28 b/s cap");
            assertTrue(terminal > 24, "terminal " + terminal + " b/s should be close to the analytic 26.8");
            assertEquals(0, sim.airborneEvents, "no AIR on a smooth or voxel 20 degree slope");
        }
    }

    @Test
    void fiveDegreeGroomedSlopeSettlesBetween10And14Bps() {
        TrackSim sim = new TrackSim(T, BOARD, TrackSim.Terrain.plane(5, SurfaceProfile.GROOMED), 0, 0);
        for (int i = 0; i < 4000; i++) sim.tick(SlideModel.Input.coast(0));
        double bps = sim.state.speed / BPS;
        assertTrue(bps >= 10 && bps <= 14, "settled at " + bps + " b/s");
    }

    @Test
    void tenBpsOnFlatSnowBlockCoastsAtLeast50Blocks() {
        TrackSim sim = new TrackSim(T, BOARD, TrackSim.Terrain.plane(0, SurfaceProfile.SNOW_BLOCK), 0, 10 * BPS);
        while (sim.state.speed >= 2 * BPS && sim.ticks < 10_000) sim.tick(SlideModel.Input.coast(0));
        assertTrue(sim.distance >= 50, "coasted " + sim.distance + " blocks");
    }

    @Test
    void halfSlopeRampAt16BpsGivesApex07To12() {
        TrackSim sim = kickerRun(0.5, 2.0);
        double apex = sim.apexAboveLaunch();
        assertEquals(1, sim.launches, "should launch exactly once");
        assertTrue(apex >= 0.7 && apex <= 1.2, "apex " + apex);
    }

    @Test
    void fullSlopeRampAt16BpsGivesApex25To38AndAtLeast16AirTicks() {
        TrackSim sim = kickerRun(1.0, 1.0);
        double apex = sim.apexAboveLaunch();
        assertEquals(1, sim.launches);
        assertTrue(apex >= 2.5 && apex <= 3.8, "apex " + apex);
        int ticksToLipHeight = Ballistics.airTicks(GroundContact.launchVelocity(T, launchSpeed(1.0, 1.0), 1.0), 0);
        assertTrue(ticksToLipHeight >= 16, "air ticks back to lip height: " + ticksToLipHeight);
    }

    @Test
    void tenPieceFlatRailAt8BpsExitsWithAtLeast80Percent() {
        double entry = 8 * BPS;
        double speed = entry * 0.95; // 5% entry loss (section 10, grind step 3)
        double travelled = 0;
        while (travelled < 10) {
            travelled += speed;
            speed = SlideModel.alongPathStep(T, speed, 0, 0.0008);
        }
        assertTrue(speed >= 0.8 * entry, "exit " + speed / entry * 100 + "% of entry");
    }

    @Test
    void a360IsAchievableOffAFullSlopeRampAt16Bps() {
        double vy = GroundContact.launchVelocity(T, launchSpeed(1.0, 1.0), 1.0);
        int airTicks = Ballistics.airTicks(vy, 0);
        AirRotation rotation = new AirRotation();
        int ticksFor360 = -1;
        for (int i = 1; i <= airTicks; i++) {
            rotation.step(T, GearKind.BOARD, 1, 0, false);
            if (rotation.yawAccum >= 360) {
                ticksFor360 = i;
                break;
            }
        }
        assertTrue(ticksFor360 > 0, "360 not reached in " + airTicks + " air ticks (got " + rotation.yawAccum + ")");
        // A clean landing allows 35 degrees of heading error, so 325 degrees is the real requirement.
        assertTrue(ticksFor360 <= airTicks, "needs " + ticksFor360 + " of " + airTicks + " ticks");
    }

    @Test
    void descendingVoxelStaircasesAtSpeedNeverFlickerIntoAir() {
        for (double grade : new double[]{0.2, 0.364, 0.55, 0.8, 1.0}) {
            TrackSim sim = new TrackSim(T, BOARD, TrackSim.Terrain.staircase(grade, SurfaceProfile.GROOMED), 0.25, T.maxSpeed);
            for (int i = 0; i < 600; i++) {
                sim.tick(new SlideModel.Input(0, 0, true, false, false));
            }
            assertEquals(0, sim.airborneEvents, "grade " + grade + " went airborne");
            assertEquals(0, sim.launches, "grade " + grade + " launched");
            assertTrue(sim.snaps > 0);
        }
    }

    @Test
    void docSnapDistanceAloneWouldFlickerAt45DegreesThisIsWhyItScalesWithSpeed() {
        // Guard for the GroundContact clarification: at the cap on a 45 degree staircase the drop per
        // tick exceeds the doc's fixed 1.0 block snap.
        double maxDropPerTick = Math.floor(T.maxSpeed * 1.0) + 1;
        assertTrue(maxDropPerTick > T.snapDown);
        assertTrue(GroundContact.snapDistance(T, T.maxSpeed) >= maxDropPerTick);
        assertEquals(T.snapDown, GroundContact.snapDistance(T, 0), 1e-12);
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Rider enters the ramp at 16 b/s (starting one tick before the ramp) and flies off the lip. */
    private static TrackSim kickerRun(double grade, double length) {
        TrackSim sim = new TrackSim(T, BOARD, TrackSim.Terrain.kicker(grade, length, 30), -16 * BPS, 16 * BPS);
        for (int i = 0; i < 80; i++) sim.tick(SlideModel.Input.coast(0));
        return sim;
    }

    /** Speed at the lip after climbing the ramp, from the same simulation. */
    private static double launchSpeed(double grade, double length) {
        TrackSim sim = new TrackSim(T, BOARD, TrackSim.Terrain.kicker(grade, length, 30), -16 * BPS, 16 * BPS);
        while (sim.grounded && sim.ticks < 100) sim.tick(SlideModel.Input.coast(0));
        return sim.state.speed;
    }
}
