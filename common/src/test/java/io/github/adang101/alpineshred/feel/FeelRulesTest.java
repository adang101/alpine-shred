package io.github.adang101.alpineshred.feel;

import io.github.adang101.alpineshred.movement.RideMode;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.SurfaceProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sound mix, camera, screen effects and particle rates (design doc sections 9 and 17). */
class FeelRulesTest {

    // ---- sound ----------------------------------------------------------------------------------

    @Test
    void glideLoopFollowsTheDocFormulaOnSnow() {
        RideSoundRules.Mix mix = new RideSoundRules.Mix();
        RideSoundRules.targets(RideState.GLIDE, SurfaceProfile.Category.SNOW, 0.4, 0, mix);
        assertEquals(0.5 * 0.6, mix.volume(RideSoundRules.Loop.GLIDE_SNOW), 1e-9);
        assertEquals(0.8 + 0.4 * 0.35, mix.pitch(RideSoundRules.Loop.GLIDE_SNOW), 1e-9);
        RideSoundRules.targets(RideState.GLIDE, SurfaceProfile.Category.SNOW, 2.0, 0, mix);
        assertEquals(0.6, mix.volume(RideSoundRules.Loop.GLIDE_SNOW), 1e-9, "volume caps at speed 0.8");
        assertEquals(0, mix.volume(RideSoundRules.Loop.CARVE), 1e-9, "no carve sound while gliding");
    }

    @Test
    void eachSurfaceHasItsOwnLoopAndTheAirIsSilent() {
        RideSoundRules.Mix mix = new RideSoundRules.Mix();
        RideSoundRules.targets(RideState.GLIDE, SurfaceProfile.Category.ICE, 0.8, 0, mix);
        assertTrue(mix.volume(RideSoundRules.Loop.GLIDE_ICE) > 0);
        assertEquals(0, mix.volume(RideSoundRules.Loop.GLIDE_SNOW), 1e-9);
        RideSoundRules.targets(RideState.GLIDE, SurfaceProfile.Category.SCRAPE, 0.5, 0, mix);
        assertTrue(mix.volume(RideSoundRules.Loop.SCRAPE) > 0);
        RideSoundRules.targets(RideState.CARVE, SurfaceProfile.Category.SCRAPE, 0.5, 1, mix);
        assertEquals(0, mix.volume(RideSoundRules.Loop.CARVE), 1e-9, "no snow carve sound on stone");
        for (RideState silent : new RideState[]{RideState.AIR, RideState.WIPEOUT, RideState.OFF}) {
            RideSoundRules.targets(silent, SurfaceProfile.Category.SNOW, 1.0, 0.5, mix);
            for (RideSoundRules.Loop loop : RideSoundRules.Loop.all()) assertEquals(0, mix.volume(loop), 1e-9, silent + " " + loop);
        }
    }

    @Test
    void carvingAndBrakingLayerOverTheGlide() {
        RideSoundRules.Mix mix = new RideSoundRules.Mix();
        RideSoundRules.targets(RideState.CARVE, SurfaceProfile.Category.SNOW, 0.6, -0.8, mix);
        assertEquals(0.8 * 0.6 * 1.5, mix.volume(RideSoundRules.Loop.CARVE), 1e-9);
        assertTrue(mix.volume(RideSoundRules.Loop.GLIDE_SNOW) > 0);
        RideSoundRules.targets(RideState.BRAKE, SurfaceProfile.Category.SNOW, 0.6, 0, mix);
        assertTrue(mix.volume(RideSoundRules.Loop.BRAKE) > mix.volume(RideSoundRules.Loop.GLIDE_SNOW));
    }

    @Test
    void surfaceChangesCrossFadeOverFourTicks() {
        double v = 0;
        int ticks = 0;
        while (v < 0.6 - 1e-9 && ticks < 20) {
            v = RideSoundRules.fadeStep(v, 0.6);
            ticks++;
        }
        assertTrue(ticks <= RideSoundRules.FADE_TICKS, "took " + ticks + " ticks");
        assertEquals(0.6, RideSoundRules.fadeStep(0.6, 0.6), 1e-12);
        assertTrue(RideSoundRules.popPitch(1) > RideSoundRules.popPitch(0));
        assertTrue(RideSoundRules.hardLanding(-1.0));
        assertFalse(RideSoundRules.hardLanding(-0.4));
    }

    // ---- camera ---------------------------------------------------------------------------------

    @Test
    void cameraRollAndFovSettleOnTheDocTargets() {
        CameraEffects c = new CameraEffects();
        for (int i = 0; i < 80; i++) c.tick(true, false, 1.0, 1.4, 1.0, false);
        assertEquals(6.0, c.roll(1), 1e-3, "roll = lean * 6");
        assertEquals(18.0, c.fov(1), 1e-3, "(1.4 - 0.4) * 30 = 30, capped at 18");
        for (int i = 0; i < 80; i++) c.tick(true, false, 0, 0.7, 0.5, false);
        assertEquals(0.3 * 30 * 0.5, c.fov(1), 1e-3, "scaled by client.fovEffect");
        for (int i = 0; i < 80; i++) c.tick(true, false, 0, 0.2, 1.0, false);
        assertEquals(0, c.fov(1), 1e-3, "slow riding never narrows the view");
    }

    @Test
    void cameraSmoothsAtPointTwoPerTickAndInterpolatesFrames() {
        CameraEffects c = new CameraEffects();
        c.tick(true, false, 1.0, 0, 1, false);
        assertEquals(6.0 * 0.2, c.roll(1), 1e-9);
        assertEquals(6.0 * 0.2 * 0.5, c.roll(0.5f), 1e-9);
    }

    @Test
    void reduceMotionAndWipeoutsOverrideTheCamera() {
        CameraEffects c = new CameraEffects();
        for (int i = 0; i < 80; i++) c.tick(true, false, 1.0, 1.4, 1.0, true);
        assertEquals(0, c.roll(1), 1e-9);
        assertEquals(0, c.fov(1), 1e-9);
        for (int i = 0; i < 80; i++) c.tick(true, true, 0.0, 1.4, 1.0, false);
        assertEquals(CameraEffects.WIPEOUT_ROLL, c.roll(1), 1e-3);
        assertEquals(0, c.fov(1), 1e-3);
    }

    // ---- screen ---------------------------------------------------------------------------------

    @Test
    void speedLinesAndWhiteoutFollowSpeed() {
        ScreenEffects s = new ScreenEffects();
        s.tick(RideState.GLIDE, 0.9, false, false, false, false);
        assertEquals(0, s.speedLines(), 1e-9);
        s.tick(RideState.GLIDE, 1.15, false, false, false, false);
        assertEquals(0.5, s.speedLines(), 1e-9);
        s.tick(RideState.GLIDE, 1.15, false, false, false, true);
        assertEquals(0, s.speedLines(), 1e-9, "reduceMotion");
        s.tick(RideState.GLIDE, 1.4, true, false, false, false);
        assertEquals(0.5, s.whiteout(), 1e-9);
        s.tick(RideState.GLIDE, 1.4, true, true, false, false);
        assertEquals(0, s.whiteout(), 1e-9, "goggles");
    }

    @Test
    void powderFlashAndWipeoutPulseLastTenTicks() {
        ScreenEffects s = new ScreenEffects();
        s.tick(RideState.GLIDE, 0.8, false, false, false, false);
        s.tick(RideState.POWDER, 0.8, false, false, false, false);
        assertEquals(0.6, s.whiteout(), 1e-9);
        for (int i = 0; i < ScreenEffects.FLASH_TICKS; i++) s.tick(RideState.POWDER, 0.8, false, false, false, false);
        assertEquals(0, s.whiteout(), 1e-9);

        ScreenEffects safe = new ScreenEffects();
        safe.tick(RideState.GLIDE, 0.8, false, false, true, false);
        safe.tick(RideState.POWDER, 0.8, false, false, true, false);
        safe.tick(RideState.WIPEOUT, 0.8, false, false, true, false);
        assertEquals(0, safe.whiteout(), 1e-9, "flashSafe");
        assertEquals(0, safe.wipeoutPulse(), 1e-9, "flashSafe");

        ScreenEffects pulse = new ScreenEffects();
        pulse.tick(RideState.WIPEOUT, 0.8, false, false, false, false);
        assertEquals(0.5, pulse.wipeoutPulse(), 1e-9);
    }

    // ---- particles ------------------------------------------------------------------------------

    @Test
    void particleRates() {
        assertEquals(6, ParticleRules.poofCount(-0.1));
        assertEquals(20, ParticleRules.poofCount(-2.0));
        assertEquals(0, ParticleRules.sprayPerTick(RideState.GLIDE, 1, 1), 1e-9);
        assertTrue(ParticleRules.sprayPerTick(RideState.CARVE, 1, 1) > ParticleRules.sprayPerTick(RideState.CARVE, 1, 0.2));
        ParticleRules.Accumulator acc = new ParticleRules.Accumulator();
        int total = 0;
        for (int i = 0; i < 100; i++) total += acc.take(0.37);
        assertEquals(37, total);
    }

    // ---- clips ----------------------------------------------------------------------------------

    @Test
    void baseClipsFollowStateModeAndStance() {
        assertNull(RiderClips.base(RideState.OFF, RideMode.BOARD, false, 0));
        assertEquals(RiderClips.GLIDE_BOARD_REGULAR, RiderClips.base(RideState.CARVE, RideMode.BOARD, false, 0.5));
        assertEquals(RiderClips.GLIDE_BOARD_GOOFY, RiderClips.base(RideState.GLIDE, RideMode.BOARD, true, 0.5));
        assertEquals(RiderClips.GLIDE_SKIS, RiderClips.base(RideState.POWDER, RideMode.SKIS, false, 0.5));
        assertEquals(RiderClips.TUCK_BOARD, RiderClips.base(RideState.GLIDE, RideMode.BOARD, false, 1.3));
        assertEquals(RiderClips.SNOWPLOW_SKIS, RiderClips.base(RideState.BRAKE, RideMode.SKIS, false, 0.5));
        assertEquals(RiderClips.WALK_STRAPPED, RiderClips.base(RideState.IDLE, RideMode.SKIS, false, 0.1));
        assertEquals(RiderClips.IDLE_BOARD, RiderClips.base(RideState.IDLE, RideMode.BOARD, false, 0));
        assertEquals(RiderClips.AIR_SKIS, RiderClips.base(RideState.AIR, RideMode.SKIS, false, 1));
        assertEquals(RiderClips.WIPEOUT_FRONT, RiderClips.base(RideState.WIPEOUT, RideMode.BOARD, false, 1));
        for (RideState state : RideState.values()) {
            for (RideMode mode : new RideMode[]{RideMode.BOARD, RideMode.SKIS}) {
                String clip = RiderClips.base(state, mode, false, 0.5);
                assertTrue(clip == null || RiderClips.ALL.contains(clip), state + " " + mode + " -> " + clip);
            }
        }
    }

    @Test
    void directorPlaysLaunchAndLandingOneShots() {
        RiderClips.Director d = new RiderClips.Director();
        d.tick(RideState.GLIDE, RideMode.BOARD, false, 0.6, 0);
        assertEquals(RiderClips.POP_CHARGE, d.tick(RideState.GLIDE, RideMode.BOARD, false, 0.6, 0.5));
        assertEquals(RiderClips.POP_LAUNCH, d.tick(RideState.AIR, RideMode.BOARD, false, 0.6, 0));
        for (int i = 1; i < RiderClips.POP_LAUNCH_TICKS; i++) d.tick(RideState.AIR, RideMode.BOARD, false, 0.6, 0);
        assertEquals(RiderClips.AIR_BOARD, d.tick(RideState.AIR, RideMode.BOARD, false, 0.6, 0));
        assertEquals(RiderClips.LAND_CLEAN, d.tick(RideState.GLIDE, RideMode.BOARD, false, 0.6, 0));
        for (int i = 1; i < RiderClips.LAND_TICKS; i++) d.tick(RideState.GLIDE, RideMode.BOARD, false, 0.6, 0);
        assertEquals(RiderClips.GLIDE_BOARD_REGULAR, d.tick(RideState.GLIDE, RideMode.BOARD, false, 0.6, 0));
        assertEquals(RiderClips.WIPEOUT_FRONT, d.tick(RideState.WIPEOUT, RideMode.BOARD, false, 0.6, 0), "wipeouts cut one-shots");
    }

    @Test
    void tuckHasHysteresis() {
        RiderClips.Director d = new RiderClips.Director();
        assertEquals(RiderClips.TUCK_BOARD, d.tick(RideState.GLIDE, RideMode.BOARD, false, 1.12, 0));
        assertEquals(RiderClips.TUCK_BOARD, d.tick(RideState.GLIDE, RideMode.BOARD, false, 1.05, 0), "still tucked just under");
        assertEquals(RiderClips.GLIDE_BOARD_REGULAR, d.tick(RideState.GLIDE, RideMode.BOARD, false, 0.95, 0));
        assertEquals(RiderClips.GLIDE_BOARD_REGULAR, d.tick(RideState.GLIDE, RideMode.BOARD, false, 1.05, 0), "not again until 1.1");
    }

    // ---- lean -----------------------------------------------------------------------------------

    /** Where PlayerAnimator's root transform puts the point under the pivot (the feet), in blocks. */
    private static double[] feet(LeanRig.Pose p) {
        // translate(root) . translate(0, PIVOT, 0) . Rz(roll) . Ry(0) . Rx(pitch) . translate(0, -PIVOT, 0) applied to (0,0,0)
        double y = -LeanRig.PIVOT, z = 0;
        double y1 = y * Math.cos(p.rootPitch()) - z * Math.sin(p.rootPitch());
        double z1 = y * Math.sin(p.rootPitch()) + z * Math.cos(p.rootPitch());
        double x2 = -y1 * Math.sin(p.rootRoll());
        double y2 = y1 * Math.cos(p.rootRoll());
        return new double[]{p.rootX() + x2, p.rootY() + y2 + LeanRig.PIVOT, p.rootZ() + z1};
    }

    @Test
    void leaningKeepsTheFeetAndBoardPlanted() {
        for (RideMode mode : new RideMode[]{RideMode.BOARD, RideMode.SKIS}) {
            for (double lean = -1; lean <= 1; lean += 0.25) {
                double[] f = feet(LeanRig.pose(mode, false, false, lean, 0.8, 0, true));
                assertEquals(0, f[0], 1e-9, mode + " x at lean " + lean);
                assertEquals(0, f[1], 1e-9, mode + " y at lean " + lean);
                assertEquals(0, f[2], 1e-9, mode + " z at lean " + lean);
            }
        }
    }

    @Test
    void boardersLeanTowardTheEdgeTheTurnUses() {
        // Regular, right turn: toe side, forward. Positive root pitch leans back.
        assertTrue(LeanRig.pose(RideMode.BOARD, false, false, 1, 0.8, 0, true).rootPitch() < 0);
        assertTrue(LeanRig.pose(RideMode.BOARD, true, false, 1, 0.8, 0, true).rootPitch() > 0, "goofy: heel side");
        assertTrue(LeanRig.pose(RideMode.BOARD, false, true, 1, 0.8, 0, true).rootPitch() > 0, "switch swaps edges");
        assertEquals(Math.toRadians(18), LeanRig.ROOT_TILT + LeanRig.TORSO_TILT, 1e-9, "18 degrees at full lean");
        // Skis lean sideways: positive root roll tilts left, so a right turn rolls negative.
        LeanRig.Pose skis = LeanRig.pose(RideMode.SKIS, false, false, 1, 0.8, 0, true);
        assertTrue(skis.rootRoll() < 0);
        assertEquals(0, skis.rootPitch(), 1e-12);
    }

    @Test
    void ridersCrouchWithSpeedAndCharge() {
        double slow = LeanRig.pose(RideMode.BOARD, false, false, 0, 0.1, 0, true).crouch();
        double fast = LeanRig.pose(RideMode.BOARD, false, false, 0, 1.4, 0, true).crouch();
        double charged = LeanRig.pose(RideMode.BOARD, false, false, 0, 0.1, 1, true).crouch();
        assertTrue(fast > slow && charged > fast);
        assertEquals(LeanRig.Pose.NEUTRAL, LeanRig.pose(RideMode.NONE, false, false, 1, 1, 1, true));
    }
}
