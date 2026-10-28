package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.trick.GrabType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RideSnapshotTest {

    @Test
    void roundTripsEveryFieldCombination() {
        int checked = 0;
        for (RideState state : RideState.values()) {
            for (RideMode mode : RideMode.values()) {
                for (GrabType grab : GrabType.values()) {
                    for (int lean = -3; lean <= 3; lean++) {
                        for (int speed = 0; speed <= 15; speed += 5) {
                            for (int heading = 0; heading < 256; heading += 37) {
                                for (int flags = 0; flags < 16; flags += 5) {
                                    boolean goofy = (heading & 1) == 0;
                                    boolean grabActive = grab != GrabType.NONE;
                                    RideSnapshot s = new RideSnapshot(state, mode, goofy, grabActive, grab, flags, lean, speed, heading);
                                    int bits = s.pack();
                                    assertTrue(bits >= 0, "bit 31 must stay clear");
                                    assertEquals(s, RideSnapshot.unpack(bits));
                                    assertEquals(state, RideSnapshot.stateOf(bits));
                                    assertEquals(mode, RideSnapshot.modeOf(bits));
                                    checked++;
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(checked > 10_000);
    }

    @Test
    void offPacksToZero() {
        assertEquals(0, RideSnapshot.OFF.pack());
        assertEquals(RideSnapshot.OFF, RideSnapshot.unpack(0));
    }

    @Test
    void outOfRangeValuesAreClampedNotSpilledIntoNeighbours() {
        RideSnapshot s = new RideSnapshot(RideState.GLIDE, RideMode.BOARD, false, false, GrabType.NONE, 0xFF, 9, 99, 300);
        RideSnapshot back = RideSnapshot.unpack(s.pack());
        assertEquals(3, back.lean());
        assertEquals(15, back.speedLevel());
        assertEquals(300 - 256, back.headingStep());
        assertEquals(0xF, back.flags());
        assertEquals(RideState.GLIDE, back.state());
    }

    @Test
    void headingQuantizationErrorIsUnderOneStep() {
        for (double yaw = -720; yaw <= 720; yaw += 0.37) {
            double decoded = RideSnapshot.stepToHeading(RideSnapshot.headingToStep(yaw));
            double error = Math.abs(SlideModel.wrapDegrees(decoded - yaw));
            assertTrue(error <= 360.0 / 256 / 2 + 1e-9, "yaw " + yaw + " decoded to " + decoded);
        }
    }

    @Test
    void speedLevelsCoverTheValidatorCapForProGear() {
        double proCap = MovementTuning.DEFAULT.maxSpeed * 1.18 * 1.08;
        assertTrue(RideSnapshot.levelToSpeed(15) >= proCap, "level 15 must reach the waxed pro-ski cap");
        assertEquals(0, RideSnapshot.speedToLevel(0));
        assertEquals(15, RideSnapshot.speedToLevel(5));
        assertEquals(8, RideSnapshot.speedToLevel(1.0));
    }

    @Test
    void unknownStateIdsDecodeToOff() {
        assertEquals(RideState.OFF, RideState.byId(15));
        assertEquals(RideState.OFF, RideState.byId(-1));
        assertEquals(RideMode.NONE, RideMode.byId(3));
        assertEquals(GrabType.NONE, GrabType.byId(14));
    }

    @Test
    void groundSubStatesMatchTheDiagram() {
        int ground = 0;
        for (RideState s : RideState.values()) if (s.isGroundRiding()) ground++;
        assertEquals(5, ground, "the doc's state machine has five ground sub-states");
        assertTrue(RideState.SLIDE.isOnFeature());
        assertTrue(!RideState.LIFT.isControllerDriven(), "lift seating is vanilla vehicle movement");
    }
}
