package io.github.adang101.alpineshred.trick;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.movement.MovementTuning;

/**
 * Spin and flip rates in the air (design doc section 8, "Inputs in the air"): holding A/D or W/S
 * ramps the rate from 0 to the maximum over {@code airRampTicks}; a held grab cuts rotation by 20%.
 * README clarification 95 adds two rules so a jump is not a flip the rider never asked for, and
 * clarification 101 makes the second one finish a trick instead of undoing it:
 * <ul>
 *   <li>A key held when the air session starts does nothing until it is released (the rider holds
 *       W and A/D on the ground); only a fresh press rotates. The caller decides what counts as held:
 *       a key pressed only just before takeoff is a wind-up and is live at once.</li>
 *   <li>With an axis' key released, the angle eases to a landable one, "spotting the landing". Past
 *       {@code airFinishBias} short of the half-way point it completes the rotation in the direction
 *       the rider was turning (170 of a 360 flip finishes the flip) rather than undoing it.</li>
 * </ul>
 * Recovery only ever moves an angle toward a whole rotation, never past it, and never faster than
 * the axis could be turned by hand, so the server's rate bounds ({@link TrickValidator},
 * {@link FlipLimit}) hold for an honest client unchanged.
 */
public final class AirRotation {
    /** Degrees either side of sideways (90 off the way of travel) where released spin recovers at {@code airSpinSideRecover}. */
    static final double SIDEWAYS_ZONE = 25;
    /** Signed degrees spun this air session (positive = right, D). */
    public double yawAccum;
    /** Signed degrees flipped this air session (positive = front flip, W). */
    public double pitchAccum;
    private double spinRate;
    private double flipRate;
    /** Whether the axis' key was up at takeoff or has been seen released since; until then its input is ignored. */
    private boolean spinArmed = true;
    private boolean flipArmed = true;
    /** Direction (-1, 0, 1) of the last key that turned each axis, which a released axis completes toward. */
    private int spinDir;
    private int flipDir;

    /**
     * @param spinInput       -1 (A), 0, or 1 (D)
     * @param flipInput       -1 (S, back), 0, or 1 (W, front)
     * @param airTicks        air ticks so far this session, counting this one (unused by the rotation itself;
     *                        the flip is limited when reported, see {@link #reportPitch})
     * @param takeoffRelative degrees the board pointed off the direction of travel at takeoff, which
     *                        with {@link #yawAccum} gives the heading the landing is judged on
     * @return degrees the board turned this tick, spin and recovery together
     */
    public double step(MovementTuning t, GearKind kind, int spinInput, int flipInput, boolean grabbing, int airTicks,
                       double takeoffRelative) {
        double spinMax = kind == GearKind.SKIS ? t.airSpinMaxSkis : t.airSpinMaxBoard;
        double mul = grabbing ? t.airGrabRotationMul : 1.0;
        int spinKey = spinArmed ? Integer.signum(spinInput) : 0;
        int flipKey = flipArmed ? Integer.signum(flipInput) : 0;
        spinArmed |= spinInput == 0;
        flipArmed |= flipInput == 0;
        if (spinKey != 0) spinDir = spinKey;
        if (flipKey != 0) flipDir = flipKey;
        spinRate = ramp(spinRate, spinKey, spinMax, t.airRampTicks);
        flipRate = ramp(flipRate, flipKey, t.airFlipMax, t.airRampTicks);
        double spun = spinRate * mul;
        double flipped = flipRate * mul;
        // Recovery never exceeds the hand rate, so a retuned airSpinRecover cannot outrun the validator.
        if (spinKey == 0) {
            spun += recoverYaw(takeoffRelative + yawAccum, Math.min(t.airSpinRecover, spinMax) * mul,
                    Math.min(t.airSpinSideRecover, spinMax) * mul, spinDir, t.airFinishBias);
        }
        if (flipKey == 0) {
            flipped += recoverPitch(pitchAccum, Math.min(t.airFlipRecover, t.airFlipMax) * mul, flipDir, t.airFinishBias);
        }
        yawAccum += spun;
        pitchAccum += flipped;
        return spun;
    }

    /**
     * The whole rotation of {@code unit} degrees an angle settles to: the nearest one, except that
     * past {@code 0.5 - bias} of the way in the direction {@code dir} the rider was turning it is the
     * next one, so a committed rotation is finished and not undone. {@code dir} 0 is the nearest.
     */
    static double settle(double angle, double unit, int dir, double bias) {
        if (dir == 0) return Math.rint(angle / unit) * unit;
        return dir * Math.floor(dir * angle / unit + 0.5 + bias) * unit;
    }

    /**
     * The turn that eases {@code heading} (takeoff offset plus spin, degrees) toward the multiple of
     * 180 it settles to ({@link #settle}), forward or switch, by at most {@code rate}; it stops on it.
     * Within {@link #SIDEWAYS_ZONE} degrees of sideways, where a boardslide catches a rail, it is
     * slower ({@code sideRate}) so a rider who lets go at 90 still lands on the rail.
     */
    static double recoverYaw(double heading, double rate, double sideRate, int dir, double bias) {
        double sideways = Math.abs(((heading % 180) + 180) % 180 - 90);
        return approach(settle(heading, 180, dir, bias) - heading, sideways <= SIDEWAYS_ZONE ? Math.min(rate, sideRate) : rate);
    }

    /**
     * The flip that eases {@code pitch} toward the whole flip it settles to ({@link #settle}) by at
     * most {@code rate}, stopping on it. The target does not depend on how much air is left: the rider
     * spins a whole flip as fast as the hand rate, and {@link #reportPitch} limits what is reported.
     */
    static double recoverPitch(double pitch, double rate, int dir, double bias) {
        return approach(settle(pitch, 360, dir, bias) - pitch, rate);
    }

    /** {@code distance} limited to {@code rate} either way, so a step never overshoots. */
    private static double approach(double distance, double rate) {
        return Math.max(-rate, Math.min(rate, distance));
    }

    private static double ramp(double rate, int input, double max, int rampTicks) {
        if (input == 0) return 0;
        if (rate != 0 && Math.signum(rate) != input) rate = 0;
        double next = rate + input * max / rampTicks;
        return Math.max(-max, Math.min(max, next));
    }

    /**
     * The flip to report for a session of {@code airTicks}: the angle turned, held to {@link FlipLimit}
     * (the flip rate, and whole flips needing 16 air ticks each) so the report passes the server's
     * validator. Only the report is limited, not the turning, so the rider is never held at half a
     * flip waiting for the air to catch up; a flip too fast for its air reports as the part that fits.
     */
    public double reportPitch(MovementTuning t, int airTicks) {
        double cap = FlipLimit.maxPitch(airTicks, t.airFlipMax);
        return Math.max(-cap, Math.min(cap, pitchAccum));
    }

    public double spinRate() {
        return spinRate;
    }

    public double flipRate() {
        return flipRate;
    }

    /** Clears the session with nothing held, as between sessions. */
    public void reset() {
        reset(false, false);
    }

    /**
     * Starts a session. A key down on the tick the air session began ({@code spinHeld}, {@code flipHeld})
     * counts as held from takeoff and is ignored until released; a key up then and pressed on the
     * first air tick rotates at once (README 95).
     */
    public void reset(boolean spinHeld, boolean flipHeld) {
        yawAccum = pitchAccum = spinRate = flipRate = 0;
        spinArmed = !spinHeld;
        flipArmed = !flipHeld;
        spinDir = flipDir = 0;
    }
}
