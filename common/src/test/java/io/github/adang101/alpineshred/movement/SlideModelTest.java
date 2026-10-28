package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlideModelTest {
    private static final MovementTuning T = MovementTuning.DEFAULT;
    private static final GearStats BOARD = GearStats.NEUTRAL_BOARD;
    private static final GearStats SKIS = GearStats.NEUTRAL_SKIS;

    @Test
    void turnRateHalvesAtTheReferenceSpeed() {
        double atRest = SlideModel.turnRate(T, GearKind.BOARD, 0);
        double atRef = SlideModel.turnRate(T, GearKind.BOARD, T.turnSpeedRef);
        assertEquals(7.0 * 0.9, atRest, 1e-12);
        assertEquals(atRest / 2, atRef, 1e-12);
        assertEquals(7.0 * 1.1, SlideModel.turnRate(T, GearKind.SKIS, 0), 1e-12);
    }

    @Test
    void lookSteeringIsLimitedBySpeedAndGrip() {
        SlideModel.State s = new SlideModel.State(0, 0.9);
        SlideModel.stepGround(s, SlideModel.Input.coast(90), SlideModel.Conditions.flat(BOARD, SurfaceProfile.GROOMED), T, SlideModel.SteerMode.RIDE);
        double expected = SlideModel.turnRate(T, GearKind.BOARD, 0.9);
        assertEquals(expected, s.lastTurn, 1e-9);
        assertEquals(expected, s.heading, 1e-9);
    }

    @Test
    void deadZoneIgnoresTinyLookOffsets() {
        SlideModel.State s = new SlideModel.State(0, 0.5);
        SlideModel.stepGround(s, SlideModel.Input.coast(1.0), SlideModel.Conditions.flat(BOARD, SurfaceProfile.GROOMED), T, SlideModel.SteerMode.RIDE);
        assertEquals(0, s.heading, 0);
    }

    @Test
    void matchingEdgeBoostsTurnAndScrubsSpeed() {
        SlideModel.Conditions c = SlideModel.Conditions.flat(BOARD, SurfaceProfile.GROOMED);
        SlideModel.State plain = new SlideModel.State(0, 0.6);
        SlideModel.State edged = new SlideModel.State(0, 0.6);
        SlideModel.stepGround(plain, SlideModel.Input.coast(60), c, T, SlideModel.SteerMode.RIDE);
        SlideModel.stepGround(edged, new SlideModel.Input(1, 60, false, false, false), c, T, SlideModel.SteerMode.RIDE);
        assertEquals(plain.lastTurn * (1 + T.edgeTurnBoost), edged.lastTurn, 1e-9);
        assertTrue(edged.speed < plain.speed, "a held edge scrubs speed");
        assertTrue(edged.edgeEngaged);
    }

    @Test
    void counterEdgeDriftsMoreThanNoEdge() {
        SlideModel.Conditions c = SlideModel.Conditions.flat(BOARD, SurfaceProfile.GROOMED);
        SlideModel.State noEdge = new SlideModel.State(0, 0.6);
        SlideModel.State counter = new SlideModel.State(0, 0.6);
        for (int i = 0; i < 5; i++) {
            SlideModel.stepGround(noEdge, SlideModel.Input.coast(60), c, T, SlideModel.SteerMode.RIDE);
            SlideModel.stepGround(counter, new SlideModel.Input(-1, 60, false, false, false), c, T, SlideModel.SteerMode.RIDE);
        }
        double lagNoEdge = Math.abs(SlideModel.wrapDegrees(noEdge.heading - noEdge.travelYaw));
        double lagCounter = Math.abs(SlideModel.wrapDegrees(counter.heading - counter.travelYaw));
        assertTrue(lagCounter > lagNoEdge, "counter edge should lower grip and widen the drift angle");
    }

    @Test
    void iceDriftsMoreThanGroomed() {
        SlideModel.State groomed = new SlideModel.State(0, 0.8);
        SlideModel.State ice = new SlideModel.State(0, 0.8);
        for (int i = 0; i < 6; i++) {
            SlideModel.stepGround(groomed, SlideModel.Input.coast(45), SlideModel.Conditions.flat(BOARD, SurfaceProfile.GROOMED), T, SlideModel.SteerMode.RIDE);
            SlideModel.stepGround(ice, SlideModel.Input.coast(45), SlideModel.Conditions.flat(BOARD, SurfaceProfile.BLUE_ICE), T, SlideModel.SteerMode.RIDE);
        }
        double lagGroomed = Math.abs(SlideModel.wrapDegrees(groomed.heading - groomed.travelYaw));
        double lagIce = Math.abs(SlideModel.wrapDegrees(ice.heading - ice.travelYaw));
        assertTrue(lagIce > 1.5 * lagGroomed, "ice lag " + lagIce + " vs groomed " + lagGroomed);
    }

    @Test
    void belowPivotSpeedKeysRotateTheBoardInPlace() {
        SlideModel.State s = new SlideModel.State(0, 0.0);
        SlideModel.stepGround(s, new SlideModel.Input(1, 0, false, false, false), SlideModel.Conditions.flat(BOARD, SurfaceProfile.SNOW_BLOCK), T, SlideModel.SteerMode.RIDE);
        assertEquals(T.pivotRate, s.heading, 1e-9);
        assertFalse(s.edgeEngaged, "pivoting is not carving");
        assertEquals(0, s.speed, 0);
    }

    @Test
    void keysSchemeSteersWithADAndIgnoresTheCamera() {
        SlideModel.State s = new SlideModel.State(0, 0.5);
        SlideModel.stepGround(s, new SlideModel.Input(-1, 120, false, false, false), SlideModel.Conditions.flat(BOARD, SurfaceProfile.GROOMED), T, SlideModel.SteerMode.KEYS);
        assertEquals(-SlideModel.turnRate(T, GearKind.BOARD, 0.5), s.heading, 1e-9);
    }

    @Test
    void brakingStopsFasterAndSkisSnowplowWithHalfSteering() {
        SlideModel.Conditions board = SlideModel.Conditions.flat(BOARD, SurfaceProfile.SNOW_BLOCK);
        SlideModel.State coast = new SlideModel.State(0, 0.5);
        SlideModel.State brake = new SlideModel.State(0, 0.5);
        SlideModel.stepGround(coast, SlideModel.Input.coast(0), board, T, SlideModel.SteerMode.RIDE);
        SlideModel.stepGround(brake, new SlideModel.Input(0, 0, false, false, true), board, T, SlideModel.SteerMode.RIDE);
        double coastLoss = 0.5 - coast.speed;
        double brakeLoss = 0.5 - brake.speed;
        assertTrue(brakeLoss > 4 * coastLoss, "brake loss " + brakeLoss + " vs coast " + coastLoss);

        SlideModel.State skis = new SlideModel.State(0, 0.5);
        SlideModel.stepGround(skis, new SlideModel.Input(0, 90, false, false, true), SlideModel.Conditions.flat(SKIS, SurfaceProfile.SNOW_BLOCK), T, SlideModel.SteerMode.RIDE);
        assertEquals(SlideModel.turnRate(T, GearKind.SKIS, 0.5) * SKIS.grip() * T.skiBrakeSteerMul, skis.lastTurn, 1e-9);
    }

    @Test
    void tuckIsFasterThanFlareOnASlope() {
        SlideModel.Conditions c = SlideModel.Conditions.slope(BOARD, SurfaceProfile.GROOMED, 20, 0);
        SlideModel.State tuck = new SlideModel.State(0, 0.5);
        SlideModel.State flare = new SlideModel.State(0, 0.5);
        for (int i = 0; i < 400; i++) {
            SlideModel.stepGround(tuck, new SlideModel.Input(0, 0, true, false, false), c, T, SlideModel.SteerMode.RIDE);
            SlideModel.stepGround(flare, new SlideModel.Input(0, 0, false, true, false), c, T, SlideModel.SteerMode.RIDE);
        }
        assertTrue(tuck.speed > flare.speed * 1.2);
        assertTrue(tuck.speed <= T.maxSpeed + 1e-12, "cap holds");
    }

    @Test
    void stallAngleOnSnowIsAboutTwoDegrees() {
        double stall = Math.toDegrees(Math.asin(SurfaceProfile.SNOW_BLOCK.friction() / T.effectiveGravity()));
        assertTrue(stall > 1.5 && stall < 3.0, "stall " + stall);
    }

    @Test
    void stoppedFacingUphillSlidesBackTailFirst() {
        SlideModel.State s = new SlideModel.State(180, 0.0); // facing north, downhill is south (yaw 0)
        SlideModel.Conditions c = SlideModel.Conditions.slope(BOARD, SurfaceProfile.GROOMED, 15, 0);
        for (int i = 0; i < 40; i++) SlideModel.stepGround(s, SlideModel.Input.coast(180), c, T, SlideModel.SteerMode.RIDE);
        assertTrue(s.speed > 0.1, "slides back down");
        assertEquals(0, SlideModel.wrapDegrees(s.travelYaw), 1.0);
        assertTrue(s.switchStance, "travelling tail-first is switch");
    }

    @Test
    void switchRidingIsStableWhenLookingAlongTravel() {
        // Landed a 180: nose points backward, rider looks where they are going.
        SlideModel.State s = new SlideModel.State(180, 0.8);
        s.travelYaw = 0;
        SlideModel.State regular = new SlideModel.State(0, 0.8);
        SlideModel.Conditions c = SlideModel.Conditions.flat(BOARD, SurfaceProfile.GROOMED);
        for (int i = 0; i < 20; i++) {
            SlideModel.stepGround(s, SlideModel.Input.coast(0), c, T, SlideModel.SteerMode.RIDE);
            SlideModel.stepGround(regular, SlideModel.Input.coast(0), c, T, SlideModel.SteerMode.RIDE);
        }
        assertTrue(s.switchStance);
        assertEquals(180, Math.abs(SlideModel.wrapDegrees(s.heading)), 1e-6);
        assertEquals(regular.speed, s.speed, 1e-12, "switch riding loses no more speed than regular");
    }

    @Test
    void lookingStraightBackDoesNotSpinTheBoard() {
        SlideModel.State s = new SlideModel.State(0, 0.8);
        SlideModel.Conditions c = SlideModel.Conditions.flat(BOARD, SurfaceProfile.GROOMED);
        for (int i = 0; i < 40; i++) SlideModel.stepGround(s, SlideModel.Input.coast(180), c, T, SlideModel.SteerMode.RIDE);
        assertEquals(0, SlideModel.wrapDegrees(s.heading), 1e-9);
        assertFalse(s.switchStance);
    }

    @Test
    void hardDriftNearNinetyDegreesDoesNotChatterBetweenStances() {
        SlideModel.State s = new SlideModel.State(0, 0.9);
        SlideModel.Conditions ice = SlideModel.Conditions.flat(BOARD, SurfaceProfile.BLUE_ICE);
        int flips = 0;
        boolean last = s.switchStance;
        for (int i = 0; i < 60; i++) {
            // Look 95 degrees right of travel every tick: a sustained near-sideways drift.
            SlideModel.stepGround(s, SlideModel.Input.coast(s.travelYaw + 95), ice, T, SlideModel.SteerMode.RIDE);
            if (s.switchStance != last) flips++;
            last = s.switchStance;
        }
        assertTrue(flips <= 1, "stance flipped " + flips + " times");
    }

    @Test
    void waxedBoardGlidesFaster() {
        SlideModel.Conditions dry = new SlideModel.Conditions(BOARD, 100, false, SurfaceProfile.SNOW_BLOCK, Math.toRadians(10), 0, 1);
        SlideModel.Conditions waxed = new SlideModel.Conditions(BOARD, 100, true, SurfaceProfile.SNOW_BLOCK, Math.toRadians(10), 0, 1);
        SlideModel.State a = new SlideModel.State(0, 0.2);
        SlideModel.State b = new SlideModel.State(0, 0.2);
        for (int i = 0; i < 3000; i++) {
            SlideModel.stepGround(a, SlideModel.Input.coast(0), dry, T, SlideModel.SteerMode.RIDE);
            SlideModel.stepGround(b, SlideModel.Input.coast(0), waxed, T, SlideModel.SteerMode.RIDE);
        }
        assertTrue(b.speed > a.speed * 1.05, "waxed " + b.speed + " vs dry " + a.speed);
    }

    @Test
    void fasterBoardHasHigherTerminalSpeed() {
        GearStats pro = GearStats.DEFAULTS.get("pro_snowboard");
        SlideModel.State base = new SlideModel.State(0, 0.2);
        SlideModel.State fast = new SlideModel.State(0, 0.2);
        for (int i = 0; i < 3000; i++) {
            SlideModel.stepGround(base, SlideModel.Input.coast(0), SlideModel.Conditions.slope(BOARD, SurfaceProfile.GROOMED, 8, 0), T, SlideModel.SteerMode.RIDE);
            SlideModel.stepGround(fast, SlideModel.Input.coast(0), SlideModel.Conditions.slope(pro, SurfaceProfile.GROOMED, 8, 0), T, SlideModel.SteerMode.RIDE);
        }
        assertEquals(1.15, fast.speed / base.speed, 0.02);
    }

    @Test
    void popAndWipeoutFormulas() {
        assertEquals(0.42, SlideModel.popVelocity(T, BOARD, 0), 1e-12);
        assertEquals(0.42 * 1.6, SlideModel.popVelocity(T, BOARD, 10), 1e-12);
        assertEquals(0.42 * 1.6, SlideModel.popVelocity(T, BOARD, 50), 1e-12);
        assertEquals(0.42 * 1.15 * 1.3, SlideModel.popVelocity(T, GearStats.DEFAULTS.get("park_snowboard"), 5), 1e-12);
        assertEquals(4.0, SlideModel.wipeoutDamage(T, 1.0, false), 1e-12);
        assertEquals(2.4, SlideModel.wipeoutDamage(T, 1.0, true), 1e-12);
        assertEquals(0, SlideModel.wipeoutDamage(T, 0.3, false), 0);
        assertEquals(0.25 / 1.4, SlideModel.powderFloatSpeed(T, GearStats.DEFAULTS.get("powder_skis")), 1e-12);
    }

    @Test
    void polePushOnlyBelowItsSpeedLimit() {
        assertEquals(0.3, SlideModel.polePush(T, 0.2), 1e-12);
        assertEquals(0.6, SlideModel.polePush(T, 0.55), 1e-12);
        assertEquals(0.7, SlideModel.polePush(T, 0.7), 1e-12);
    }

    @Test
    void groundStateClassification() {
        SlideModel.State s = new SlideModel.State(0, 0.0);
        assertEquals(RideState.IDLE, SlideModel.classifyGround(s, SlideModel.Input.coast(0), SurfaceProfile.GROOMED));
        s.speed = 0.5;
        assertEquals(RideState.GLIDE, SlideModel.classifyGround(s, SlideModel.Input.coast(0), SurfaceProfile.GROOMED));
        assertEquals(RideState.BRAKE, SlideModel.classifyGround(s, new SlideModel.Input(0, 0, false, false, true), SurfaceProfile.GROOMED));
        assertEquals(RideState.POWDER, SlideModel.classifyGround(s, SlideModel.Input.coast(0), SurfaceProfile.POWDER));
        s.edgeEngaged = true;
        assertEquals(RideState.CARVE, SlideModel.classifyGround(s, SlideModel.Input.coast(0), SurfaceProfile.GROOMED));
    }

    @Test
    void angleHelpers() {
        assertEquals(-180, SlideModel.wrapDegrees(180), 0);
        assertEquals(170, SlideModel.wrapDegrees(-190), 1e-12);
        assertEquals(0, SlideModel.yawOf(0, 1), 1e-12);
        assertEquals(90, SlideModel.yawOf(-1, 0), 1e-12);
        for (double yaw = -170; yaw < 180; yaw += 17) {
            assertEquals(yaw, SlideModel.yawOf(SlideModel.dirX(yaw), SlideModel.dirZ(yaw)), 1e-9);
        }
    }
}
