package io.github.adang101.alpineshred.feel;

/**
 * Camera roll and speed field of view for the local rider (design doc section 9, "Camera
 * effects"): roll {@code lean * 6} degrees, field of view {@code + (speed - 0.4) * 30} degrees
 * capped at +18 and scaled by {@code client.fovEffect}, both smoothed exponentially with factor
 * {@value #SMOOTHING} per tick and interpolated between ticks for rendering.
 * {@code client.reduceMotion} turns both off.
 */
public final class CameraEffects {
    public static final double SMOOTHING = 0.2;
    public static final double ROLL_PER_LEAN = 6.0;
    public static final double FOV_PER_SPEED = 30.0;
    public static final double FOV_SPEED_START = 0.4;
    public static final double FOV_MAX = 18.0;
    /** Roll during a wipeout (section 7, "Wipeout ... plays a camera roll"). */
    public static final double WIPEOUT_ROLL = 18.0;

    private double roll, prevRoll;
    private double fov, prevFov;

    /** Once per client tick. Not riding returns both effects smoothly to zero. */
    public void tick(boolean riding, boolean wipeout, double lean, double speed, double fovEffect, boolean reduceMotion) {
        prevRoll = roll;
        prevFov = fov;
        boolean on = riding && !reduceMotion;
        double targetRoll = !on ? 0 : wipeout ? WIPEOUT_ROLL : clamp(lean, -1, 1) * ROLL_PER_LEAN;
        double targetFov = on && !wipeout ? targetFov(speed) * clamp(fovEffect, 0, 1) : 0;
        roll += (targetRoll - roll) * SMOOTHING;
        fov += (targetFov - fov) * SMOOTHING;
        if (Math.abs(roll) < 1.0e-4 && targetRoll == 0) roll = 0;
        if (Math.abs(fov) < 1.0e-4 && targetFov == 0) fov = 0;
    }

    public static double targetFov(double speed) {
        return clamp((speed - FOV_SPEED_START) * FOV_PER_SPEED, 0, FOV_MAX);
    }

    /** Degrees of roll for this frame; positive rolls the view toward the rider's right. */
    public double roll(float partialTick) {
        return prevRoll + (roll - prevRoll) * partialTick;
    }

    /** Degrees added to the field of view for this frame. */
    public double fov(float partialTick) {
        return prevFov + (fov - prevFov) * partialTick;
    }

    public void reset() {
        roll = prevRoll = fov = prevFov = 0;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
