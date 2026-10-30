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

    // ---- tricks (section 8) ------------------------------------------------------------------------

    /** Rides off the kicker; in the air, {@code air} picks the input each tick from the controller. */
    private static SlideController.TrickLanded airOffTheKicker(VoxelBody body, SlideController c,
                                                                 java.util.function.Function<SlideController, RideInput> air) {
        for (int tick = 0; tick < 120; tick++) {
            RideInput in = c.state() == RideState.AIR ? air.apply(c) : RideInput.coast(0);
            c.tick(body, in, BOARD);
            for (SlideController.Event e : c.events()) {
                if (e instanceof SlideController.TrickLanded landed) return landed;
            }
        }
        throw new AssertionError("never landed: " + c.state() + " z " + body.z);
    }

    private static VoxelBody kicker() {
        return new VoxelBody(VoxelBody.Terrain.of((x, z) -> z < 10 ? 0 : z < 11 ? 1 : -4, SurfaceProfile.GROOMED), 0.5, 0.5);
    }

    @Test
    void aSpinInTheAirTurnsTheBoardAndLandsScored() {
        VoxelBody body = kicker();
        SlideController c = riding(body, 0.8);
        SlideController.TrickLanded landed = airOffTheKicker(body, c,
                ctl -> Math.abs(ctl.spinAngle()) < 335 ? new RideInput(1, 0, 0, false, false, GrabType.NONE) : RideInput.coast(0));
        assertTrue(Math.abs(landed.report().yawAccum()) > 330 && Math.abs(landed.report().yawAccum()) < 380,
                "spun " + landed.report().yawAccum());
        assertTrue(landed.report().airTicks() >= 15, "air " + landed.report().airTicks());
        assertTrue(landed.quality() == io.github.adang101.alpineshred.trick.LandingQuality.CLEAN
                || landed.quality() == io.github.adang101.alpineshred.trick.LandingQuality.PERFECT, "landing " + landed.quality());
        assertFalse(landed.report().switchLanding(), "a 360 lands forward");
        assertTrue(c.state() != RideState.WIPEOUT);
    }

    @Test
    void aHalfSpinLandsSwitch() {
        VoxelBody body = kicker();
        SlideController c = riding(body, 0.8);
        SlideController.TrickLanded landed = airOffTheKicker(body, c,
                ctl -> Math.abs(ctl.spinAngle()) < 165 ? new RideInput(-1, 0, 0, false, false, GrabType.NONE) : RideInput.coast(0));
        assertTrue(landed.report().switchLanding(), "a 180 lands backward");
        assertTrue(landed.report().yawAccum() < 0, "A spins left");
        assertTrue(landed.quality() != io.github.adang101.alpineshred.trick.LandingQuality.BAIL, "landing " + landed.quality());
    }

    @Test
    void anUnfinishedFlipBails() {
        VoxelBody body = kicker();
        SlideController c = riding(body, 0.8);
        SlideController.TrickLanded landed = airOffTheKicker(body, c, ctl -> new RideInput(0, 1, 0, false, false, GrabType.NONE));
        double residual = io.github.adang101.alpineshred.trick.TrickAngles.residual(landed.report().pitchAccum(), 360);
        if (residual > 45) {
            assertEquals(io.github.adang101.alpineshred.trick.LandingQuality.BAIL, landed.quality());
            assertEquals(RideState.WIPEOUT, c.state(), "a bail wipes out");
        }
        assertTrue(landed.report().pitchAccum() > 300, "flipped " + landed.report().pitchAccum());
    }

    @Test
    void grabsAreCountedAndSlowTheSpin() {
        VoxelBody a = kicker(), b = kicker();
        SlideController spin = riding(a, 0.8), grabbed = riding(b, 0.8);
        RideInput spinning = new RideInput(1, 0, 0, false, false, GrabType.NONE);
        RideInput spinGrab = new RideInput(1, 0, 0, false, false, GrabType.INDY);
        SlideController.TrickLanded plain = airOffTheKicker(a, spin, ctl -> spinning);
        SlideController.TrickLanded indy = airOffTheKicker(b, grabbed, ctl -> spinGrab);
        assertEquals(indy.report().airTicks(), indy.report().grab(GrabType.INDY), "held the whole air");
        assertTrue(Math.abs(indy.report().yawAccum()) < Math.abs(plain.report().yawAccum()) * 0.85, "grabbing slows the spin");
    }

    @Test
    void sneakInTheAirIsAMethodGrab() {
        VoxelBody body = kicker();
        SlideController c = riding(body, 0.8);
        SlideController.TrickLanded landed = airOffTheKicker(body, c, ctl -> new RideInput(0, 0, 0, true, false, GrabType.NONE));
        assertTrue(landed.report().grab(GrabType.METHOD) > 10, "method ticks " + landed.report().grab(GrabType.METHOD));
    }

    @Test
    void theReportedHeadingErrorIsTheTakeoffOffsetPlusTheSpin() {
        // Validation rule 4 on the server checks exactly this, so an honest client always passes it.
        for (double target : new double[]{0, 60, 100, 165, 200, 250, 335}) {
            VoxelBody body = kicker();
            SlideController c = riding(body, 0.8);
            SlideController.TrickLanded landed = airOffTheKicker(body, c,
                    ctl -> Math.abs(ctl.spinAngle()) < target ? new RideInput(1, 0, 0, false, false, GrabType.NONE) : RideInput.coast(0));
            var r = landed.report();
            assertEquals(io.github.adang101.alpineshred.trick.TrickAngles.headingError(r.takeoffRelative(), r.yawAccum()),
                    r.headingError(), 1.0e-9, "spin to " + target);
        }
    }

    /**
     * A long rail on posts beyond the kicker's drop: a 90 in the air catches it as a boardslide, and
     * off its end the board turns out of the slide by itself, so the landing is no bail.
     */
    @Test
    void offTheEndOfABoardslideTheBoardTurnsOutOnItsOwn() {
        java.util.Map<Long, io.github.adang101.alpineshred.park.GrindPath> rail = new java.util.HashMap<>();
        for (int z = 12; z < 40; z++) {
            rail.put((long) z, new io.github.adang101.alpineshred.park.GrindPath(z, io.github.adang101.alpineshred.park.RailPath.at(
                    io.github.adang101.alpineshred.park.RailShape.FLAT, io.github.adang101.alpineshred.park.Facing.NORTH, 0, -4, z),
                    io.github.adang101.alpineshred.park.GrindMaterial.METAL, 0.0008));
        }
        io.github.adang101.alpineshred.park.GrindPhysics.Lookup lookup = p -> {
            List<io.github.adang101.alpineshred.park.GrindPath> out = new ArrayList<>();
            int by = (int) Math.floor(p.y()), bz = (int) Math.floor(p.z());
            if (Math.abs(Math.floor(p.x())) <= 1 && by >= -5 && by <= -3) {
                for (int z = bz - 1; z <= bz + 1; z++) {
                    if (rail.containsKey((long) z)) out.add(rail.get((long) z));
                }
            }
            return out;
        };
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.of((x, z) -> z < 10 ? 0 : z < 11 ? 1 : -6, SurfaceProfile.GROOMED), 0.5, 0.5);
        body.park = new io.github.adang101.alpineshred.park.ParkView() {
            public io.github.adang101.alpineshred.park.GrindPhysics.Lookup grinds() {
                return lookup;
            }

            public io.github.adang101.alpineshred.park.PipeSection pipeNear(io.github.adang101.alpineshred.park.V3 feet) {
                return null;
            }

            public boolean lipNear(io.github.adang101.alpineshred.park.V3 feet) {
                return false;
            }
        };
        SlideController c = riding(body, 0.8);
        List<SlideController.TrickLanded> landings = new ArrayList<>();
        boolean slid = false;
        for (int tick = 0; tick < 200 && landings.stream().noneMatch(l -> l.report().fromRail()); tick++) {
            boolean spin = c.state() == RideState.AIR && !slid && Math.abs(c.spinAngle()) < 80;
            c.tick(body, spin ? new RideInput(1, 0, 0, false, false, GrabType.NONE) : RideInput.coast(0), BOARD);
            slid |= c.state() == RideState.GRIND;
            for (SlideController.Event e : c.events()) {
                if (e instanceof SlideController.TrickLanded l) landings.add(l);
                if (e instanceof SlideController.GrindEnded g) {
                    assertEquals(io.github.adang101.alpineshred.park.GrindPhysics.Variant.BOARDSLIDE.ordinal(), g.report().variant(),
                            "caught as a boardslide");
                    assertEquals(-4, g.report().y(), "reported on the rail's block");
                }
            }
        }
        assertTrue(slid, "never caught the rail: " + landings);
        SlideController.TrickLanded off = landings.stream().filter(l -> l.report().fromRail()).findFirst()
                .orElseThrow(() -> new AssertionError("never left the rail: " + c.state() + " z " + body.z));
        double rel = Math.abs(off.report().takeoffRelative());
        assertTrue(rel < 1.0e-6 || Math.abs(rel - 180) < 1.0e-6, "takeoff offset " + rel);
        assertTrue(off.quality() != io.github.adang101.alpineshred.trick.LandingQuality.BAIL, "bailed off the end of a boardslide");
        assertTrue(c.state() != RideState.WIPEOUT);
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
    void vanillaStyleHoverPastEdgesNeitherLaunchesNorFlickers() {
        // Vertical-first collision leaves the rider hovering a tick past every step edge.
        VoxelBody.Terrain staircase = VoxelBody.Terrain.of((x, z) -> -(int) Math.floor(z / 3.0), SurfaceProfile.SNOW_BLOCK);
        for (double speed = 0.4; speed <= 1.45; speed += 0.15) {
            VoxelBody body = new VoxelBody(staircase, 0.5, 0.5);
            body.verticalFirst = true;
            SlideController c = riding(body, speed);
            for (int tick = 0; tick < 60; tick++) {
                c.tick(body, RideInput.coast(0), BOARD);
                assertFalse(c.events().stream().anyMatch(e -> e instanceof SlideController.Launched),
                        "launched down a staircase at " + speed + ", tick " + tick);
                assertTrue(c.state() != RideState.AIR, "AIR on a staircase at " + speed + ", tick " + tick);
            }
        }
        // A plateau after a step up, then a step down: still no launch.
        VoxelBody.Terrain plateau = VoxelBody.Terrain.of((x, z) -> z < 20 ? 0 : z < 28 ? 1 : z < 31 ? 0 : -1, SurfaceProfile.GROOMED);
        VoxelBody body = new VoxelBody(plateau, 0.5, 10.5);
        body.verticalFirst = true;
        SlideController c = riding(body, 1.35);
        List<SlideController.Event> events = run(c, body, BOARD, RideInput.coast(0), 25);
        assertFalse(events.stream().anyMatch(e -> e instanceof SlideController.Launched), "launched off a plateau edge: " + events);
    }

    @Test
    void steppedRampsLaunchWithVanillaStyleCollisionToo() {
        VoxelBody.Terrain ramp = VoxelBody.Terrain.of((x, z) -> z >= 20 && z < 24 ? 0.375 * (z - 19) : 0.0,
                (x, z) -> SurfaceProfile.GROOMED);
        for (double speed = 0.65; speed <= 1.4 + 1e-9; speed += 0.15) {
            for (int phase = 0; phase < 10; phase++) {
                VoxelBody body = new VoxelBody(ramp, 0.5, 10.5 + phase * 0.1);
                body.verticalFirst = true;
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
