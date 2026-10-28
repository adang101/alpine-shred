package io.github.adang101.alpineshred.trick;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.movement.MovementTuning;

/**
 * Spin and flip rates in the air (design doc section 8, "Inputs in the air"): holding A/D or W/S
 * ramps the rate from 0 to the maximum over {@code airRampTicks}; releasing holds the angle;
 * a held grab cuts rotation by 20%.
 */
public final class AirRotation {
    /** Signed degrees spun this air session (positive = right, D). */
    public double yawAccum;
    /** Signed degrees flipped this air session (positive = front flip, W). */
    public double pitchAccum;
    private double spinRate;
    private double flipRate;

    /**
     * @param spinInput -1 (A), 0, or 1 (D)
     * @param flipInput -1 (S, back), 0, or 1 (W, front)
     * @return degrees spun this tick
     */
    public double step(MovementTuning t, GearKind kind, int spinInput, int flipInput, boolean grabbing) {
        double spinMax = kind == GearKind.SKIS ? t.airSpinMaxSkis : t.airSpinMaxBoard;
        double mul = grabbing ? t.airGrabRotationMul : 1.0;
        spinRate = ramp(spinRate, Integer.signum(spinInput), spinMax, t.airRampTicks);
        flipRate = ramp(flipRate, Integer.signum(flipInput), t.airFlipMax, t.airRampTicks);
        double spun = spinRate * mul;
        yawAccum += spun;
        pitchAccum += flipRate * mul;
        return spun;
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
    }
}
