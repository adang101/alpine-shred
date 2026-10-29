package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RideValidatorTest {
    private static final RideValidator.Limits LIMITS =
            RideValidator.Limits.of(MovementTuning.DEFAULT, GearStats.DEFAULTS.get("snowboard"), 1.0);

    @Test
    void limitsFollowTheDocFormula() {
        assertEquals(1.40 * 1.0 * 1.08 * 1.15, LIMITS.speedCap(), 1e-9);
        assertEquals(1.0, LIMITS.groundRise(), 1e-9);
        assertTrue(LIMITS.maxLaunchVy() >= 0.42 * 1.6);
    }

    @Test
    void aFastLegalRideRaisesNothing() {
        RideValidator v = new RideValidator();
        double z = 0, y = 64;
        for (int tick = 0; tick < 400; tick++) {
            z += 1.40;           // the speed cap, every tick
            if (tick % 3 == 0) y -= 1; // descending a staircase
            assertEquals(RideValidator.Kind.OK, v.observe(tick, 0, y, z, true, LIMITS).kind(), "tick " + tick);
        }
    }

    @Test
    void sustainedOverspeedIsCorrectedToTheLastGroundedPosition() {
        RideValidator v = new RideValidator();
        double z = 0;
        for (int tick = 0; tick < 20; tick++) {
            z += 1.0;
            v.observe(tick, 0, 64, z, true, LIMITS);
        }
        double lastGood = z;
        RideValidator.Verdict verdict = null;
        for (int tick = 20; tick < 40; tick++) {
            z += 4.0;
            verdict = v.observe(tick, 0, 64, z, true, LIMITS);
            if (verdict.kind() == RideValidator.Kind.CORRECT) break;
        }
        assertEquals(RideValidator.Kind.CORRECT, verdict.kind(), "should correct sustained 80 b/s");
        assertTrue(verdict.z() < lastGood + 12, "corrects to a recent valid position, got z=" + verdict.z());
        assertTrue(verdict.reason().startsWith("speed"));
    }

    @Test
    void twoMovePacketsInOneTickAreAbsorbed() {
        RideValidator v = new RideValidator();
        double z = 0;
        for (int tick = 0; tick < 100; tick++) {
            int packets = tick % 7 == 3 ? 2 : tick % 7 == 4 ? 0 : 1;
            z += 1.40 * packets;
            assertEquals(RideValidator.Kind.OK, v.observe(tick, 0, 64, z, true, LIMITS).kind(), "tick " + tick);
        }
    }

    @Test
    void steppingUpOneBlockIsFineButFlyingUpIsNot() {
        RideValidator v = new RideValidator();
        v.observe(0, 0, 64, 0, true, LIMITS);
        assertEquals(RideValidator.Kind.OK, v.observe(1, 0, 65, 0.8, true, LIMITS).kind());
        assertEquals(RideValidator.Kind.VIOLATION, v.observe(2, 0, 67, 1.6, true, LIMITS).kind());
    }

    @Test
    void risingInTheAirNeedsAReportedPopOrLaunch() {
        RideValidator noReport = new RideValidator();
        noReport.observe(0, 0, 64, 0, false, LIMITS);
        noReport.observe(1, 0, 64, 0.5, false, LIMITS);
        assertEquals(RideValidator.Kind.VIOLATION, noReport.observe(2, 0, 64.6, 1.0, false, LIMITS).kind());

        RideValidator popped = new RideValidator();
        popped.observe(0, 0, 64, 0, true, LIMITS);
        double vy = 0.42 * 1.6, y = 64;
        popped.noteLaunch(1, vy, LIMITS);
        for (int tick = 1; tick < 20; tick++) {
            y += vy;
            vy = Ballistics.nextVerticalVelocity(vy);
            boolean landed = y <= 64;
            if (landed) y = 64;
            assertEquals(RideValidator.Kind.OK, popped.observe(tick, 0, y, tick * 0.5, landed, LIMITS).kind(), "tick " + tick);
        }
    }

    /**
     * The controller and the validator together, in the server's order: each tick the move packet
     * arrives, then the tick's ride_action (handled while the rider clock still reads the previous
     * tick), then the per-tick check.
     */
    private static void rideAndValidate(VoxelBody body, double speed, RideInput input, int ticks, String label) {
        body.vz = speed;
        SlideController c = new SlideController();
        RideContext ctx = RideContext.defaults(GearStats.DEFAULTS.get("snowboard"));
        RideValidator v = new RideValidator();
        long clock = 0;
        v.observe(clock, body.x, body.y, body.z, true, LIMITS);
        boolean launched = false;
        for (int tick = 0; tick < ticks; tick++) {
            c.tick(body, input, ctx);
            for (SlideController.Event e : c.events()) {
                if (e instanceof SlideController.Popped pop) v.noteLaunch(clock, pop.velocity(), LIMITS);
                if (e instanceof SlideController.Launched launch) {
                    v.noteRampLaunch(clock, launch.velocity(), LIMITS);
                    launched = true;
                }
            }
            clock++;
            RideValidator.Verdict verdict = v.observe(clock, body.x, body.y, body.z, body.onGround(), LIMITS);
            assertEquals(RideValidator.Kind.OK, verdict.kind(), label + " tick " + tick + ": " + verdict.reason());
        }
        assertTrue(launched || label.contains("pop"), label + ": never launched");
    }

    @Test
    void rampLaunchesAtEverySpeedPassTheValidator() {
        VoxelBody.Terrain kicker = VoxelBody.Terrain.of((x, z) -> z >= 20 && z < 22 ? 1.0 * (z - 19) : 0.0,
                (x, z) -> SurfaceProfile.GROOMED);
        for (double speed = 0.7; speed <= 1.45; speed += 0.25) {
            rideAndValidate(new VoxelBody(kicker, 0.5, 10.5), speed, RideInput.coast(0), 60, "kicker at " + speed);
        }
    }

    @Test
    void popsPassTheValidator() {
        VoxelBody body = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.GROOMED), 0.5, 0.5);
        RideInput hold = new RideInput(0, 0, 0, false, true, io.github.adang101.alpineshred.trick.GrabType.NONE);
        body.vz = 1.2;
        SlideController c = new SlideController();
        RideContext ctx = RideContext.defaults(GearStats.DEFAULTS.get("snowboard"));
        RideValidator v = new RideValidator();
        long clock = 0;
        v.observe(clock, body.x, body.y, body.z, true, LIMITS);
        for (int tick = 0; tick < 60; tick++) {
            boolean jump = tick % 25 < 12; // charge fully, release, land, repeat
            c.tick(body, jump ? hold : RideInput.coast(0), ctx);
            for (SlideController.Event e : c.events()) {
                if (e instanceof SlideController.Popped pop) v.noteLaunch(clock, pop.velocity(), LIMITS);
            }
            clock++;
            RideValidator.Verdict verdict = v.observe(clock, body.x, body.y, body.z, body.onGround(), LIMITS);
            assertEquals(RideValidator.Kind.OK, verdict.kind(), "tick " + tick + ": " + verdict.reason());
        }
    }

    @Test
    void reportedLaunchesAreCappedAtThePhysicalMaximum() {
        RideValidator v = new RideValidator();
        v.observe(0, 0, 64, 0, true, LIMITS);
        v.noteLaunch(1, 50.0, LIMITS);
        assertEquals(RideValidator.Kind.VIOLATION, v.observe(1, 0, 70, 0.5, false, LIMITS).kind());
    }

    @Test
    void aLagSpikeDeliveringASecondOfPacketsAtOnceIsFine() {
        RideValidator v = new RideValidator();
        double z = 0, y = 64;
        long clock = 0;
        for (int serverTick = 0; serverTick < 200; serverTick++) {
            // Every 40 server ticks the connection stalls for 20 ticks, then 20 packets land together.
            boolean stalled = serverTick % 40 >= 20 && serverTick % 40 < 39;
            int packets = stalled ? 0 : serverTick % 40 == 39 ? 20 : 1;
            for (int p = 0; p < packets; p++) {
                clock++;
                z += 1.40;
                if (clock % 3 == 0) y += 1; // climbing steps, three per bunch
            }
            assertEquals(RideValidator.Kind.OK, v.observe(clock, 0, y, z, true, LIMITS).kind(), "server tick " + serverTick);
        }
    }

    @Test
    void droppedPacketsCountTowardTheSameThreshold() {
        RideValidator v = new RideValidator();
        v.observe(0, 0, 64, 0, true, LIMITS);
        v.observe(1, 0, 64, 1, true, LIMITS);
        assertEquals(RideValidator.Kind.VIOLATION, v.flag(2, "rate limit", LIMITS).kind());
        assertEquals(RideValidator.Kind.VIOLATION, v.flag(3, "rate limit", LIMITS).kind());
        RideValidator.Verdict third = v.flag(4, "rate limit", LIMITS);
        assertEquals(RideValidator.Kind.CORRECT, third.kind());
        assertEquals(1, third.z(), 1e-9, "corrects to the last valid position");
    }

    @Test
    void airClaimsWhileStandingAreFlaggedAfterAGracePeriod() {
        RideValidator v = new RideValidator();
        for (int i = 0; i < RideValidator.AIR_CLAIM_GRACE_TICKS; i++) {
            assertEquals(null, v.checkAirClaim(true, true), "grace tick " + i);
        }
        assertTrue(v.checkAirClaim(true, true) != null, "standing in AIR past the grace period");
        assertEquals(null, v.checkAirClaim(true, false), "really airborne");
        assertEquals(null, v.checkAirClaim(false, true), "grounded state on the ground");
        for (int i = 0; i < 20; i++) {
            assertEquals(null, v.checkAirClaim(i % 3 != 0, i % 3 == 0), "landing flicker " + i);
        }
    }

    @Test
    void violationsOutsideTheWindowDoNotAccumulate() {
        RideValidator v = new RideValidator();
        v.observe(0, 0, 64, 0, true, LIMITS);
        long tick = 1;
        double y = 64;
        for (int round = 0; round < 5; round++) {
            y += 6; // one implausible jump per round, rounds 50 ticks apart
            assertEquals(RideValidator.Kind.VIOLATION, v.observe(tick, 0, y, 0, true, LIMITS).kind(), "round " + round);
            for (int i = 0; i < 50; i++) v.observe(++tick, 0, y, 0, true, LIMITS);
            tick++;
        }
    }
}
