package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Braking as a real deceleration (README clarification 96): stopping time and distance straight down
 * a slope, holding on a steep slope, and the surfaces that bite less. The numbers in the messages are
 * the ones quoted in the clarification.
 */
class BrakingTest {
    private static final MovementTuning T = MovementTuning.DEFAULT;
    private static final GearStats BOARD = GearStats.NEUTRAL_BOARD;
    private static final GearStats SKIS = GearStats.NEUTRAL_SKIS;
    private static final SlideModel.Input BRAKE = new SlideModel.Input(0, 0, false, false, true);
    private static final int GAVE_UP = 2000;

    /** Ticks and blocks from {@code speed} straight downhill until the rider is idle; ticks is {@link #GAVE_UP} if never. */
    private record Stop(int ticks, double distance, double finalSpeed) {
    }

    private static Stop brakeDownhill(GearStats gear, SurfaceProfile surface, double degrees, double speed) {
        SlideModel.Conditions c = SlideModel.Conditions.slope(gear, surface, degrees, 0);
        SlideModel.State s = new SlideModel.State(0, speed);
        double distance = 0;
        int ticks = 0;
        while (s.speed > SlideModel.IDLE_SPEED && ticks < GAVE_UP) {
            SlideModel.stepGround(s, BRAKE, c, T, SlideModel.SteerMode.RIDE);
            distance += s.speed;
            ticks++;
        }
        return new Stop(ticks, distance, s.speed);
    }

    @Test
    void aBoardStopsFromOneBlockPerTickOnAThirtyFiveDegreeSlopeWithinThreeSeconds() {
        Stop stop = brakeDownhill(BOARD, SurfaceProfile.SNOW_BLOCK, 35, 1.0);
        assertTrue(stop.ticks() <= 60, "stops within 60 ticks, took " + stop);
        assertTrue(stop.ticks() >= 30, "a hockey stop, not a wall: " + stop);
    }

    @Test
    void aBoardStopsFromMaxSpeedOnATwentyDegreeSlopeInTwoToThreeSeconds() {
        Stop stop = brakeDownhill(BOARD, SurfaceProfile.SNOW_BLOCK, 20, T.maxSpeed);
        assertTrue(stop.ticks() >= 35 && stop.ticks() <= 60, "about 2 to 3 seconds, took " + stop);
    }

    @Test
    void skisStopSlightlyLaterThanABoardButStillWithinFourSeconds() {
        Stop board = brakeDownhill(BOARD, SurfaceProfile.SNOW_BLOCK, 35, 1.0);
        Stop skis = brakeDownhill(SKIS, SurfaceProfile.SNOW_BLOCK, 35, 1.0);
        assertTrue(skis.ticks() > board.ticks(), "skis " + skis + " vs board " + board);
        assertTrue(skis.ticks() <= 80, "skis " + skis);
    }

    @Test
    void aFlatStopFromAJogIsQuick() {
        Stop stop = brakeDownhill(BOARD, SurfaceProfile.GROOMED, 0, 0.6);
        assertTrue(stop.ticks() <= 25, "stop " + stop);
    }

    @Test
    void aBrakingRiderHoldsOnASteepSlopeInsteadOfSlidingBack() {
        SlideModel.Conditions c = SlideModel.Conditions.slope(BOARD, SurfaceProfile.SNOW_BLOCK, 35, 0);
        SlideModel.State s = new SlideModel.State(0, 0);
        for (int i = 0; i < 100; i++) SlideModel.stepGround(s, BRAKE, c, T, SlideModel.SteerMode.RIDE);
        assertEquals(0, s.speed, 0, "held");
        // Without the brake the same rider slides off.
        SlideModel.State free = new SlideModel.State(0, 0);
        for (int i = 0; i < 100; i++) SlideModel.stepGround(free, SlideModel.Input.coast(0), c, T, SlideModel.SteerMode.RIDE);
        assertTrue(free.speed > 0.2, "an unbraked rider slides: " + free.speed);
    }

    @Test
    void aBrakingRiderStoppedFacingUphillHoldsToo() {
        // Heading uphill (yaw 180) with the slope pulling back: the brake's grip beats the pull, so no slide back.
        SlideModel.Conditions c = SlideModel.Conditions.slope(BOARD, SurfaceProfile.SNOW_BLOCK, 35, 0);
        SlideModel.State s = new SlideModel.State(180, 0.05);
        for (int i = 0; i < 60; i++) SlideModel.stepGround(s, BRAKE, c, T, SlideModel.SteerMode.RIDE);
        assertEquals(0, s.speed, 1e-12);
    }

    @Test
    void whenTheSlopePullsHarderThanTheBrakeHoldsTheRiderCreeps() {
        // 35 degrees on packed ice: the brake's grip is scaled by the surface, and falls below the pull.
        Stop stop = brakeDownhill(BOARD, SurfaceProfile.PACKED_ICE, 35, 0.3);
        assertEquals(GAVE_UP, stop.ticks(), "never stops on a steep ice slope: " + stop);
        assertTrue(stop.finalSpeed() < 0.6, "but creeps, not a runaway: " + stop);
    }

    @Test
    void iceBrakesWeakerThanSnowButStillHoldsAGentleSlope() {
        Stop ice = brakeDownhill(BOARD, SurfaceProfile.ICE, 10, 0.8);
        Stop snow = brakeDownhill(BOARD, SurfaceProfile.SNOW_BLOCK, 10, 0.8);
        assertTrue(ice.ticks() < GAVE_UP, "ice " + ice);
        assertTrue(ice.distance() > snow.distance(), "ice " + ice + " vs snow " + snow);
    }

    @Test
    void powderBrakesAndStopsARider() {
        Stop stop = brakeDownhill(BOARD, SurfaceProfile.POWDER, 20, 0.8);
        assertTrue(stop.ticks() < 60, "powder " + stop);
    }

    @Test
    void brakeDecelerationIsSurfaceGripScaledAndNeverNegative() {
        SlideModel.Conditions snow = SlideModel.Conditions.flat(BOARD, SurfaceProfile.SNOW_BLOCK);
        SlideModel.Conditions groomed = SlideModel.Conditions.flat(BOARD, SurfaceProfile.GROOMED);
        assertEquals(T.brakeDecel + T.brakeSpeedDecel * 0.5, SlideModel.brakeDeceleration(T, snow, 0.5), 1e-12);
        assertEquals(SlideModel.brakeDeceleration(T, snow, 0.5), SlideModel.brakeDeceleration(T, groomed, 0.5), 1e-12,
                "grip above 1 does not make the brake stronger");
        assertEquals(T.brakeDecel, SlideModel.brakeDeceleration(T, snow, -0.3), 1e-12);
    }
}
