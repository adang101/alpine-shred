package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearStats;
import io.github.adang101.alpineshred.trick.GrabType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The controller in a voxel world: every route through a tick, travelling along +z (yaw 0). */
class SlideControllerTest {
    private static final RideContext BOARD = RideContext.defaults(GearStats.DEFAULTS.get("snowboard"));
    private static final RideContext SKIS = RideContext.defaults(GearStats.DEFAULTS.get("skis"));

    /** Starts a rider already moving along +z at {@code speed}. */
    private static SlideController riding(VoxelBody body, double speed) {
        body.vz = speed;
        SlideController c = new SlideController();
        assertEquals(SlideController.Mode.RIDE, c.tick(body, RideInput.coast(0), BOARD), "should take over on snow");
        return c;
    }

    private static List<SlideController.Event> run(SlideController c, VoxelBody body, RideContext ctx, RideInput in, int ticks) {
        List<SlideController.Event> all = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            c.tick(body, in, ctx);
            all.addAll(c.events());
        }
        return all;
    }

    @Test
    void takesOverFromWalkingOnSnow() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.SNOW_BLOCK), 0.5, 0.5);
        body.vz = 0.1;
        SlideController c = new SlideController();
        assertEquals(SlideController.Mode.RIDE, c.tick(body, RideInput.coast(0), BOARD));
        assertEquals(RideState.GLIDE, c.state());
        assertTrue(c.speed() > 0.09 && c.speed() < 0.11, "keeps walking speed: " + c.speed());
    }

    @Test
    void walksOnStoneAtWalkingSpeed() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.SCRAPE), 0.5, 0.5);
        body.vz = 0.13;
        SlideController c = new SlideController();
        assertEquals(SlideController.Mode.WALK, c.tick(body, RideInput.coast(0), BOARD));
        assertEquals(RideState.IDLE, c.state());
    }

    @Test
    void slidesOntoStoneThenScrapesDownToWalking() {
        VoxelBody.Terrain t = VoxelBody.Terrain.of((x, z) -> 0.0, (x, z) -> z < 5 ? SurfaceProfile.GROOMED : SurfaceProfile.SCRAPE);
        VoxelBody body = new VoxelBody(t, 0.5, 0.5);
        SlideController c = riding(body, 0.7);
        int ticks = 0;
        while (c.mode() == SlideController.Mode.RIDE && ticks++ < 200) c.tick(body, RideInput.coast(0), BOARD);
        assertEquals(SlideController.Mode.WALK, c.mode());
        assertTrue(body.z > 5, "slid onto the stone before stopping: z=" + body.z);
        assertTrue(c.speed() < MovementTuning.DEFAULT.scrapeSlideMinSpeed + 0.02);
    }

    @Test
    void scrapingDisabledWalksAsSoonAsTheSnowEnds() {
        VoxelBody.Terrain t = VoxelBody.Terrain.of((x, z) -> 0.0, (x, z) -> z < 2 ? SurfaceProfile.GROOMED : SurfaceProfile.SCRAPE);
        RideContext noScrape = new RideContext(MovementTuning.DEFAULT, GearStats.NEUTRAL_BOARD, 100, false, 1, SlideModel.SteerMode.RIDE, false);
        VoxelBody body = new VoxelBody(t, 0.5, 0.5);
        body.vz = 0.7;
        SlideController c = new SlideController();
        c.tick(body, RideInput.coast(0), noScrape);
        for (int i = 0; i < 10; i++) c.tick(body, RideInput.coast(0), noScrape);
        assertEquals(SlideController.Mode.WALK, c.mode());
    }

    @Test
    void descendsAVoxelStaircaseWithoutGoingAirborne() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.of((x, z) -> (int) -Math.floor(z * 0.364), SurfaceProfile.GROOMED), 0.5, 0.5);
        SlideController c = riding(body, 0.05);
        for (int i = 0; i < 400; i++) {
            c.tick(body, RideInput.coast(0), BOARD);
            assertTrue(c.state() != RideState.AIR, "went airborne at tick " + i + ", speed " + c.speed());
        }
        assertTrue(c.speed() > 1.0, "should be fast after 20 s on a 20 degree slope: " + c.speed());
        assertTrue(c.slope().grade() > 0.2 && c.slope().grade() < 0.55, "smoothed grade " + c.slope().grade());
    }

    @Test
    void aStepFollowedByADropLaunchesAndLands() {
        VoxelBody.Terrain kicker = VoxelBody.Terrain.of((x, z) -> z < 10 ? 0 : z < 11 ? 1 : -4, SurfaceProfile.GROOMED);
        VoxelBody body = new VoxelBody(kicker, 0.5, 0.5);
        SlideController c = riding(body, 0.8);
        List<SlideController.Event> events = run(c, body, BOARD, RideInput.coast(0), 80);
        assertTrue(events.stream().anyMatch(e -> e instanceof SlideController.Launched), "no launch: " + events);
        assertTrue(events.stream().anyMatch(e -> e instanceof SlideController.Landed), "no landing: " + events);
        assertTrue(c.grounded());
        assertTrue(body.z > 20, "carried on after landing: z=" + body.z);
    }

    @Test
    void aSteppedRampLaunchesWhateverTheTickPhaseAndSpeed() {
        // Four blocks rising 3/8 each (grade 0.375, just above launchGrade), then a drop: a kicker
        // built from snow layers. The last tick or two before the lip often crosses no step.
        VoxelBody.Terrain ramp = VoxelBody.Terrain.of((x, z) -> z >= 20 && z < 24 ? 0.375 * (z - 19) : 0.0,
                (x, z) -> SurfaceProfile.GROOMED);
        for (double speed = 0.65; speed <= 1.4 + 1e-9; speed += 0.15) { // slower riders drop under launchMinSpeed on the ramp
            for (int phase = 0; phase < 10; phase++) {
                VoxelBody body = new VoxelBody(ramp, 0.5, 10.5 + phase * 0.1);
                SlideController c = riding(body, speed);
                List<SlideController.Event> events = run(c, body, BOARD, RideInput.coast(0), 40);
                double s = speed;
                int p = phase;
                assertTrue(events.stream().anyMatch(e -> e instanceof SlideController.Launched),
                        () -> String.format("no launch at speed %.2f phase %d", s, p));
            }
        }
    }

    @Test
    void ridingOffAFlatLedgeDoesNotLaunch() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.of((x, z) -> z < 20 ? 0 : -1, SurfaceProfile.GROOMED), 0.5, 10.5);
        SlideController c = riding(body, 1.2);
        List<SlideController.Event> events = run(c, body, BOARD, RideInput.coast(0), 20);
        assertFalse(events.stream().anyMatch(e -> e instanceof SlideController.Launched), "launched off a flat ledge");
    }

    @Test
    void headOnWallAtSpeedWipesOutForThirtyTicks() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.of((x, z) -> z < 10 ? 0 : 5, SurfaceProfile.GROOMED), 0.5, 0.5);
        SlideController c = riding(body, 0.9);
        List<SlideController.Event> events = run(c, body, BOARD, RideInput.coast(0), 15);
        SlideController.WipedOut wipeout = (SlideController.WipedOut) events.stream()
                .filter(e -> e instanceof SlideController.WipedOut).findFirst().orElseThrow();
        assertTrue(wipeout.impactSpeed() > 0.5, "impact " + wipeout.impactSpeed());
        assertEquals(RideState.WIPEOUT, c.state());
        run(c, body, BOARD, RideInput.coast(0), MovementTuning.DEFAULT.wipeoutTicks);
        assertTrue(c.state() != RideState.WIPEOUT, "wipeout should end");
    }

    @Test
    void aHeadOnHitIsAWipeoutWhateverTheDistanceAtTheStartOfTheTick() {
        for (int i = 0; i < 16; i++) {
            double startZ = 0.5 + i * 0.05; // every phase of an 0.8-per-tick approach
            VoxelBody body = new VoxelBody(VoxelBody.Terrain.of((x, z) -> z < 10 ? 0 : 5, SurfaceProfile.GROOMED), 0.5, startZ);
            SlideController c = riding(body, 0.8);
            List<SlideController.Event> events = run(c, body, BOARD, RideInput.coast(0), 20);
            assertTrue(events.stream().anyMatch(e -> e instanceof SlideController.WipedOut), "no wipeout from z=" + startZ);
        }
    }

    @Test
    void slowWallBumpIsNotAWipeout() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.of((x, z) -> z < 3 ? 0 : 5, SurfaceProfile.GROOMED), 0.5, 0.5);
        SlideController c = riding(body, 0.3);
        List<SlideController.Event> events = run(c, body, BOARD, RideInput.coast(0), 20);
        assertFalse(events.stream().anyMatch(e -> e instanceof SlideController.WipedOut));
        assertTrue(c.speed() < 0.01);
    }

    @Test
    void wipeoutLocksInputAndBleedsSpeed() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.GROOMED), 0.5, 0.5);
        SlideController c = riding(body, 0.6);
        c.correct(MovementTuning.DEFAULT);
        assertEquals(RideState.WIPEOUT, c.state());
        body.onGround = true;
        RideInput pushing = new RideInput(1, 1, 90, false, true, GrabType.NONE);
        List<SlideController.Event> events = run(c, body, BOARD, pushing, 10);
        assertFalse(events.stream().anyMatch(e -> e instanceof SlideController.Popped), "no pops while wiped out");
        assertTrue(c.speed() < 0.05, "no skating while wiped out: " + c.speed());
    }

    @Test
    void brakingStopsInAThirdOfTheCoastingDistance() {
        VoxelBody a = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.SNOW_BLOCK), 0.5, 0.5);
        VoxelBody b = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.SNOW_BLOCK), 0.5, 0.5);
        SlideController coast = riding(a, 0.6);
        SlideController brake = riding(b, 0.6);
        RideInput sneak = new RideInput(0, 0, 0, true, false, GrabType.NONE);
        for (int i = 0; i < 2000 && coast.speed() > 0.02; i++) coast.tick(a, RideInput.coast(0), BOARD);
        for (int i = 0; i < 2000 && brake.speed() > 0.02; i++) brake.tick(b, sneak, BOARD);
        assertTrue(b.z < a.z / 3, "brake stops in " + b.z + " blocks, coasting takes " + a.z);
        assertTrue(b.z < 25, "12 b/s to a stop in under 25 blocks: " + b.z);
    }

    @Test
    void popChargesWhileHeldAndFiresOnRelease() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.GROOMED), 0.5, 0.5);
        SlideController c = riding(body, 0.5);
        RideInput hold = RideInput.coast(0).withJump(true);
        for (int i = 0; i < 12; i++) {
            c.tick(body, hold, BOARD);
            assertTrue(c.grounded(), "charging keeps the rider down");
        }
        assertEquals(MovementTuning.DEFAULT.popChargeTicks, c.chargeTicks());
        c.tick(body, RideInput.coast(0), BOARD);
        SlideController.Popped pop = (SlideController.Popped) c.events().get(0);
        assertEquals(0.42 * 1.6, pop.velocity(), 1e-9);
        assertEquals(RideState.AIR, c.state());
        List<SlideController.Event> events = run(c, body, BOARD, RideInput.coast(0), 40);
        SlideController.Landed landed = (SlideController.Landed) events.stream()
                .filter(e -> e instanceof SlideController.Landed).findFirst().orElseThrow();
        assertTrue(landed.airTicks() >= 12, "a charged pop stays up longer than a jump: " + landed.airTicks());
    }

    @Test
    void powderFloatsAtSpeedAndSinksWhenSlow() {
        VoxelBody fast = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.POWDER), 0.5, 0.5);
        SlideController c = riding(fast, 0.6);
        c.tick(fast, RideInput.coast(0), BOARD);
        assertEquals(RideState.POWDER, c.state());

        VoxelBody slow = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.POWDER), 0.5, 0.5);
        slow.vz = 0.1;
        SlideController s = new SlideController();
        assertEquals(SlideController.Mode.WALK, s.tick(slow, RideInput.coast(0), BOARD), "below float speed the rider sinks");
    }

    @Test
    void polePushIsForSkisOnTheGround() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.GROOMED), 0.5, 0.5);
        body.vz = 0.2;
        SlideController skis = new SlideController();
        skis.tick(body, RideInput.coast(0), SKIS);
        double before = skis.speed();
        assertTrue(skis.polePush(SKIS));
        assertEquals(before + MovementTuning.DEFAULT.poleImpulse, skis.speed(), 1e-9);

        VoxelBody b2 = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.GROOMED), 0.5, 0.5);
        SlideController board = riding(b2, 0.2);
        assertFalse(board.polePush(BOARD), "boards ignore poles");
    }

    @Test
    void skatingGetsARiderMovingOnFlatSnow() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.SNOW_BLOCK), 0.5, 0.5);
        SlideController c = riding(body, 0.0);
        RideInput w = new RideInput(0, 1, 0, false, false, GrabType.NONE);
        run(c, body, BOARD, w, 60);
        assertTrue(c.speed() > 0.12 && c.speed() <= MovementTuning.DEFAULT.skateMaxSpeed, "skate speed " + c.speed());
    }

    @Test
    void lookingRightTurnsTheRiderRight() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.GROOMED), 0.5, 0.5);
        SlideController c = riding(body, 0.6);
        run(c, body, BOARD, RideInput.coast(60), 20);
        assertTrue(c.travelYaw() > 20, "travel yaw " + c.travelYaw());
        assertTrue(body.x < 0, "yaw 60 heads toward -x: " + body.x);
        assertTrue(c.lean() > 0 || c.state() == RideState.CARVE || c.state() == RideState.GLIDE);
    }

    @Test
    void snapshotCarriesStateSpeedAndHeading() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.GROOMED), 0.5, 0.5);
        SlideController c = riding(body, 0.6);
        RideSnapshot s = c.snapshot(RideMode.BOARD, true);
        assertEquals(c.state(), s.state());
        assertEquals(RideMode.BOARD, s.mode());
        assertTrue(s.goofy());
        assertEquals(RideSnapshot.speedToLevel(c.speed()), s.speedLevel());
    }
}
