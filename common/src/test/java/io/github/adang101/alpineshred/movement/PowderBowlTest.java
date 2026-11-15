package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearStats;
import io.github.adang101.alpineshred.trick.GrabType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Player feedback round 2 (README clarification 102): a rider in a powder bowl could not change direction.
 * The cause that this headless world models is the sink at the top of the wall: the rider fell below the
 * float speed on the way up, the controller handed over to walking, and the ride was over. The other cause
 * (vanilla's powder collision shape giving no step-up onto the next powder block) cannot be seen here, since
 * {@link VoxelBody} always treats powder as solid; the game test {@code powderStaircaseIsClimbedLikeSnow} covers it.
 *
 * <p>The bowl is a paraboloid of full blocks: height {@code floor((x^2 + z^2) / 24)}, so a wall of about 20 to
 * 40 degrees, six blocks high at a radius of 12, like the walls a player digs or terrain makes.
 */
class PowderBowlTest {
    private static final GearStats BOARD = GearStats.DEFAULTS.get("snowboard");
    private static final MovementTuning T = MovementTuning.DEFAULT;

    private static VoxelBody.Terrain bowl(SurfaceProfile surface) {
        return VoxelBody.Terrain.of((x, z) -> (int) Math.floor((x * x + z * z) / 24.0), surface);
    }

    private static RideContext ctx(SlideModel.SteerMode mode) {
        return new RideContext(T, BOARD, 100, false, 1, mode, true);
    }

    private static RideInput keys(double strafe) {
        return new RideInput(strafe, 0, 0, false, false, GrabType.NONE);
    }

    /** A rider on the floor of the bowl at (0.5, z), already moving along +z at {@code speed}. */
    private static VoxelBody enter(SurfaceProfile surface, double z, double speed) {
        VoxelBody body = new VoxelBody(bowl(surface), 0.5, z);
        body.vz = speed;
        return body;
    }

    /** Degrees the direction of travel turned in {@code ticks} holding D, and whether the rider stayed in RIDE the whole time. */
    private record Carve(double turned, boolean rode, boolean airborne, double endSpeed) {
    }

    private static Carve carve(SurfaceProfile surface, double speed, int ticks) {
        VoxelBody body = enter(surface, -3.5, speed);
        SlideController c = new SlideController();
        RideContext ctx = ctx(SlideModel.SteerMode.KEYS);
        boolean rode = true, air = false;
        double start = Double.NaN;
        for (int i = 0; i < ticks; i++) {
            c.tick(body, keys(1), ctx);
            if (Double.isNaN(start)) start = c.travelYaw();
            rode &= c.mode() == SlideController.Mode.RIDE;
            air |= c.state() == RideState.AIR;
        }
        return new Carve(Math.abs(SlideModel.wrapDegrees(c.travelYaw() - start)), rode, air, c.speed());
    }

    @Test
    void steeringTurnsTheTravelDirectionAtEverySpeedAboveFloatSpeed() {
        double floatSpeed = SlideModel.powderFloatSpeed(T, BOARD);
        for (double speed : new double[]{floatSpeed + 0.1, 0.5, 0.9}) {
            Carve snow = carve(SurfaceProfile.SNOW_BLOCK, speed, 15);
            Carve powder = carve(SurfaceProfile.POWDER, speed, 15);
            assertTrue(powder.rode, "stopped riding in powder at entry speed " + speed);
            assertFalse(powder.airborne, "flapped into AIR on the bowl at entry speed " + speed);
            assertTrue(powder.turned >= 15, "powder carve turned only " + powder.turned + " degrees at " + speed);
            assertTrue(powder.turned >= 0.75 * snow.turned,
                    "powder turn " + powder.turned + " against snow " + snow.turned + " at entry speed " + speed);
        }
    }

    @Test
    void steeringByTheCameraFollowsInRideMode() {
        // RIDE steering: the body follows the look direction, A and D dig the edge. 90 degrees right of the entry line.
        for (double speed : new double[]{0.4, 0.9}) {
            VoxelBody body = enter(SurfaceProfile.POWDER, -3.5, speed);
            SlideController c = new SlideController();
            RideContext ctx = ctx(SlideModel.SteerMode.RIDE);
            for (int i = 0; i < 12; i++) {
                c.tick(body, new RideInput(1, 0, 90, false, false, GrabType.NONE), ctx);
                assertEquals(SlideController.Mode.RIDE, c.mode(), "stopped riding at tick " + i + " speed " + c.speed());
            }
            assertTrue(Math.abs(SlideModel.wrapDegrees(c.travelYaw() - 90)) < 25,
                    "travel did not follow the camera: " + c.travelYaw() + " at entry speed " + speed);
        }
    }

    @Test
    void aRiderWhoClimbsTheWallStallsSlidesBackAndKeepsRiding() {
        VoxelBody body = enter(SurfaceProfile.POWDER, -3.5, 0.85);
        SlideController c = new SlideController();
        RideContext ctx = ctx(SlideModel.SteerMode.KEYS);
        double highest = body.y;
        for (int i = 0; i < 200; i++) {
            c.tick(body, keys(0), ctx);
            highest = Math.max(highest, body.y);
            assertEquals(SlideController.Mode.RIDE, c.mode(),
                    "sank at tick " + i + " speed " + c.speed() + " y " + body.y + " z " + body.z);
        }
        assertTrue(highest >= 2, "never climbed the wall, best height " + highest);
        assertTrue(highest - body.y >= 1, "did not come back down, y " + body.y + " best " + highest);
    }

    @Test
    void aRiderWhoTurnsAtTheTopOfTheWallRidesBackDownUnderControl() {
        VoxelBody body = enter(SurfaceProfile.POWDER, -3.5, 0.6);
        SlideController c = new SlideController();
        RideContext ctx = ctx(SlideModel.SteerMode.KEYS);
        double highest = body.y;
        boolean turning = false;
        double downhillSpeed = 0;
        for (int i = 0; i < 160; i++) {
            // Climb, and once past the apex (the rider has lost its uphill speed) hold D to come around.
            if (!turning && highest - body.y >= 1) turning = true;
            c.tick(body, keys(turning ? 1 : 0), ctx);
            highest = Math.max(highest, body.y);
            assertEquals(SlideController.Mode.RIDE, c.mode(), "sank at tick " + i + " speed " + c.speed() + " y " + body.y);
            assertTrue(c.state() != RideState.AIR, "AIR at tick " + i);
            if (turning) downhillSpeed = Math.max(downhillSpeed, c.speed());
        }
        assertTrue(highest >= 2, "never climbed the wall, best height " + highest);
        assertTrue(body.y < highest - 1, "did not ride back down: y " + body.y + " best " + highest);
        assertTrue(downhillSpeed > SlideModel.powderFloatSpeed(T, BOARD), "never got back above the float speed: " + downhillSpeed);
    }

    @Test
    void aStoppedRiderOnFlatPowderStillSinks() {
        VoxelBody flat = new VoxelBody(VoxelBody.Terrain.flat(SurfaceProfile.POWDER), 0.5, 0.5);
        flat.vz = 0.6;
        SlideController c = new SlideController();
        RideContext ctx = ctx(SlideModel.SteerMode.KEYS);
        int sankAt = -1;
        for (int i = 0; i < 400 && sankAt < 0; i++) {
            c.tick(flat, keys(0), ctx);
            if (c.mode() == SlideController.Mode.WALK) sankAt = i;
        }
        assertTrue(sankAt > 20, "a rider carrying 0.6 should coast a while, sank at " + sankAt);
        assertTrue(sankAt > 0, "never sank on flat powder");
        assertFalse(c.floatsOnPowder(ctx) && c.mode() == SlideController.Mode.RIDE);
    }

    @Test
    void walkersAndSlowStartersStillSink() {
        for (double speed : new double[]{0.0, 0.1, 0.2}) {
            VoxelBody body = enter(SurfaceProfile.POWDER, 0.5, speed);
            SlideController c = new SlideController();
            assertEquals(SlideController.Mode.WALK, c.tick(body, keys(0), ctx(SlideModel.SteerMode.KEYS)),
                    "took over from walking on powder at speed " + speed);
        }
        // Even the hold speed, which a rider already riding keeps, does not start a ride.
        VoxelBody hold = enter(SurfaceProfile.POWDER, 0.5, SlideModel.powderFloatSpeed(T, BOARD) * T.powderHoldMul + 0.01);
        assertEquals(SlideController.Mode.WALK, new SlideController().tick(hold, keys(0), ctx(SlideModel.SteerMode.KEYS)));
    }

    @Test
    void powderGearStillFloatsEarlier() {
        GearStats skis = GearStats.DEFAULTS.get("powder_skis");
        assertTrue(SlideModel.powderFloatSpeed(T, skis) < SlideModel.powderFloatSpeed(T, BOARD));
        double speed = SlideModel.powderFloatSpeed(T, skis) + 0.01;
        assertTrue(speed < SlideModel.powderFloatSpeed(T, BOARD));
        VoxelBody body = enter(SurfaceProfile.POWDER, 0.5, speed);
        RideContext skiCtx = new RideContext(T, skis, 100, false, 1, SlideModel.SteerMode.KEYS, true);
        assertEquals(SlideController.Mode.RIDE, new SlideController().tick(body, keys(0), skiCtx));
    }

    @Test
    void holdRuleKeepsASlowRiderOnlyWhileMovingOrPulledByTheSlope() {
        double friction = SurfaceProfile.POWDER.friction();
        double hold = SlideModel.powderFloatSpeed(T, BOARD) * T.powderHoldMul;
        assertTrue(SlideModel.holdsPowder(T, BOARD, hold + 0.001, 0, friction));
        assertFalse(SlideModel.holdsPowder(T, BOARD, hold - 0.001, 0, friction), "flat and slow must sink");
        assertFalse(SlideModel.holdsPowder(T, BOARD, 0, Math.toRadians(3), friction), "a 3 degree slope does not pull past powder friction");
        assertTrue(SlideModel.holdsPowder(T, BOARD, 0, Math.toRadians(20), friction), "a stalled rider on a 20 degree wall slides back");
    }
}
