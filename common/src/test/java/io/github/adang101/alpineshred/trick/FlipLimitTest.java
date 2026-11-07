package io.github.adang101.alpineshred.trick;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.movement.MovementTuning;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client's flip limit and the server's validator share {@link FlipLimit}: what one allows, the other accepts. */
class FlipLimitTest {
    private static final MovementTuning T = MovementTuning.DEFAULT;

    private static AirReport report(double pitch, int ticks) {
        return new AirReport(0, pitch, new int[GrabType.values().length], 0, ticks, 0, 0, TrickAngles.residual(pitch, 360),
                false, false, false);
    }

    /** CI run 42355456284: flip 228 in 11 ticks rounds to one whole flip, which needs 16 ticks. */
    @Test
    void theFailedMountainRideReportIsRejectedAndNeverProduced() {
        assertNotNull(TrickValidator.check(report(228, 11), 11, 0, 24, T.airFlipMax));
        AirRotation r = new AirRotation();
        for (int tick = 1; tick <= 11; tick++) r.step(T, GearKind.BOARD, 0, 1, false, tick);
        assertTrue(Math.abs(r.pitchAccum) < 180, "held at the cap: " + r.pitchAccum);
        assertNull(TrickValidator.check(report(r.pitchAccum, 11), 11, 0, 24, T.airFlipMax));
    }

    @Test
    void aWholeFlipNeedsSixteenTicks() {
        assertTrue(FlipLimit.maxPitch(15, T.airFlipMax) < 180);
        assertTrue(FlipLimit.maxPitch(16, T.airFlipMax) > 360);
        assertEquals(0, TrickAngles.flips(FlipLimit.maxPitch(15, T.airFlipMax)));
    }

    /** Every input pattern over short airs, both flip directions, with and without a grab: the report passes the validator. */
    @Test
    void anythingTheClientRuleAllowsTheServerAccepts() {
        for (int air = 1; air <= 40; air++) {
            for (int dir = -1; dir <= 1; dir += 2) {
                for (int grab = 0; grab <= 1; grab++) {
                    for (int release = 0; release <= air; release++) {
                        AirRotation r = new AirRotation();
                        for (int tick = 1; tick <= air; tick++) {
                            r.step(T, GearKind.BOARD, 0, tick <= release ? dir : 0, grab == 1, tick);
                            assertTrue(FlipLimit.allows(r.pitchAccum, tick, T.airFlipMax), "air " + air + " tick " + tick);
                        }
                        String why = TrickValidator.check(report(r.pitchAccum, air), air, 0, 24, T.airFlipMax);
                        assertNull(why, why);
                    }
                }
            }
        }
    }

    /** The limit is tight: just past it, the validator rejects. */
    @Test
    void justPastTheLimitIsRejected() {
        for (int air = 1; air <= 40; air++) {
            double over = FlipLimit.maxPitch(air, T.airFlipMax) + 1.0;
            assertNotNull(TrickValidator.check(report(over, air), air, 0, 24, T.airFlipMax), "air " + air);
        }
    }
}
