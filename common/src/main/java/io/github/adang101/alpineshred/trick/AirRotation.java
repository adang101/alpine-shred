package io.github.adang101.alpineshred.trick;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.movement.MovementTuning;

/**
 * Spin and flip rates in the air (design doc section 8, "Inputs in the air"): holding A/D or W/S
 * ramps the rate from 0 to the maximum over {@code airRampTicks}; a held grab cuts rotation by 20%.
 * README clarification 95 adds two rules so a jump is not a flip the rider never asked for:
 * <ul>
 *   <li>A key held when the air session starts does nothing until it is released (the rider holds
 *       W and A/D on the ground); only a fresh press rotates.</li>
 *   <li>With an axis' key released, the angle eases toward the nearest landable one, "spotting the
 *       landing", at a rate below the active one, so a committed trick still has to be timed.</li>
 * </ul>
 * Recovery only ever moves an angle toward a whole rotation, never past it, and never faster than
 * the axis could be turned by hand, so the server's rate bounds ({@link TrickValidator},
 * {@link FlipLimit}) hold for an honest client unchanged.
 */
public final class AirRotation {
    /** Signed degrees spun this air session (positive = right, D). */
    public double yawAccum;
    /** Signed degrees flipped this air session (positive = front flip, W). */
    public double pitchAccum;
    private double spinRate;
    private double flipRate;
    /** Whether the axis' key has been seen released this session; until then its input is ignored. */
    private boolean spinArmed;
    private boolean flipArmed;

    /**
     * @param spinInput       -1 (A), 0, or 1 (D)
     * @param flipInput       -1 (S, back), 0, or 1 (W, front)
     * @param airTicks        air ticks so far this session, counting this one; flips stop at {@link FlipLimit}
     *                        so the report passes the server's validator
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
        spinRate = ramp(spinRate, spinKey, spinMax, t.airRampTicks);
        flipRate = ramp(flipRate, flipKey, t.airFlipMax, t.airRampTicks);
        double spun = spinRate * mul;
        double flipped = flipRate * mul;
        double cap = FlipLimit.maxPitch(airTicks, t.airFlipMax);
        // Recovery never exceeds the hand rate, so a retuned airSpinRecover cannot outrun the validator.
        if (spinKey == 0) spun += recoverYaw(takeoffRelative + yawAccum, Math.min(t.airSpinRecover, spinMax) * mul);
        if (flipKey == 0) flipped += recoverPitch(pitchAccum, Math.min(t.airFlipRecover, t.airFlipMax) * mul, cap);
        yawAccum += spun;
        pitchAccum = Math.max(-cap, Math.min(cap, pitchAccum + flipped));
        return spun;
    }

    /**
     * The turn that eases {@code heading} (takeoff offset plus spin, degrees) toward the nearest
     * multiple of 180, forward or switch, by at most {@code rate}; it stops on the target.
     */
    static double recoverYaw(double heading, double rate) {
        return approach(Math.rint(heading / 180.0) * 180.0 - heading, rate);
    }

    /**
     * The flip that eases {@code pitch} toward the nearest whole flip by at most {@code rate}, stopping
     * on it. A whole flip beyond {@code cap} ({@link FlipLimit#maxPitch}, not yet reachable) is out:
     * the rider recovers toward the whole flip below it instead.
     */
    static double recoverPitch(double pitch, double rate, double cap) {
        double target = Math.rint(pitch / 360.0) * 360.0;
        if (Math.abs(target) > cap) target -= Math.signum(target) * 360.0;
        return approach(target - pitch, rate);
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

    public double spinRate() {
        return spinRate;
    }

    public double flipRate() {
        return flipRate;
    }

    public void reset() {
        yawAccum = pitchAccum = spinRate = flipRate = 0;
        spinArmed = flipArmed = false;
    }
}
