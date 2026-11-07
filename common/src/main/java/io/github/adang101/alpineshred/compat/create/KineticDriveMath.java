package io.github.adang101.alpineshred.compat.create;

/**
 * The kinetic lift drive's numbers, pure so they can be unit-tested (phase 9 plan, decision 2; README 78).
 * Speed is {@code |rpm| / 64} blocks per second (64 rpm is 1 b/s, Create's 256 rpm top speed is 4 b/s),
 * clamped to {@code lift.kineticMaxSpeed}. Below {@link #MIN_RPM} the drive counts as stopped. The direction
 * of rotation never matters: the cable always runs up. Stress is a flat impact of {@code lift.kineticStress}
 * per rpm, whatever the chair count.
 */
public final class KineticDriveMath {
    /** Rotation slower than this (either way) is a stopped drive. */
    public static final double MIN_RPM = 8.0;
    /** rpm that give one block per second. */
    public static final double RPM_PER_BLOCK_SPEED = 64.0;

    private KineticDriveMath() {
    }

    /** Whether {@code rpm} (signed) is fast enough to run the line. NaN is stopped. */
    public static boolean running(double rpm) {
        return Math.abs(rpm) >= MIN_RPM;
    }

    /** The line speed in blocks per second for {@code rpm}, at most {@code maxSpeed}; 0 when not {@link #running}. */
    public static double targetSpeed(double rpm, double maxSpeed) {
        if (!running(rpm)) return 0;
        return Math.min(Math.abs(rpm) / RPM_PER_BLOCK_SPEED, maxSpeed);
    }

    /** Stress units the drive takes at {@code rpm}: Create multiplies the per-rpm impact by the speed. */
    public static double stressUnits(double rpm, double impactPerRpm) {
        return Math.abs(rpm) * impactPerRpm;
    }
}
