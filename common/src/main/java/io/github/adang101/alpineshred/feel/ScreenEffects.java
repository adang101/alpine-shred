package io.github.adang101.alpineshred.feel;

import io.github.adang101.alpineshred.movement.RideState;

/**
 * Full-screen effects for the local rider (design doc section 17, "Screen effects"), as alpha
 * values the HUD draws: speed lines above 0.9 blocks per tick, a whiteout vignette above 1.0 in
 * snowfall and a 10-tick white flash on entering powder above 0.6 (goggles remove both), and a
 * 10-tick red edge pulse on a wipeout. {@code client.reduceMotion} removes speed lines;
 * {@code client.flashSafe} removes the flash and the pulse.
 */
public final class ScreenEffects {
    public static final int FLASH_TICKS = 10;
    public static final int PULSE_TICKS = 10;
    public static final double SPEED_LINES_FROM = 0.9;
    public static final double SPEED_LINES_RANGE = 0.5;
    public static final double WHITEOUT_FROM = 1.0;
    public static final double POWDER_FLASH_SPEED = 0.6;

    private RideState last = RideState.OFF;
    private int flash;
    private int pulse;
    private double speedLines;
    private double whiteout;

    /**
     * @param inSnowfall snow is falling where the rider is
     * @param goggles    goggles are worn
     */
    public void tick(RideState state, double speed, boolean inSnowfall, boolean goggles, boolean flashSafe, boolean reduceMotion) {
        if (flash > 0) flash--;
        if (pulse > 0) pulse--;
        if (state != last) {
            if (state == RideState.POWDER && speed > POWDER_FLASH_SPEED && !goggles && !flashSafe) flash = FLASH_TICKS;
            if (state == RideState.WIPEOUT && !flashSafe) pulse = PULSE_TICKS;
            last = state;
        }
        boolean riding = state.isControllerDriven();
        speedLines = riding && !reduceMotion ? clamp((speed - SPEED_LINES_FROM) / SPEED_LINES_RANGE, 0, 1) : 0;
        whiteout = riding && inSnowfall && !goggles ? clamp((speed - WHITEOUT_FROM) / 0.4, 0, 1) * 0.5 : 0;
    }

    public double speedLines() {
        return speedLines;
    }

    /** Vignette plus flash, 0 to 1. */
    public double whiteout() {
        return Math.min(1, whiteout + 0.6 * flash / (double) FLASH_TICKS);
    }

    public double wipeoutPulse() {
        return 0.5 * pulse / (double) PULSE_TICKS;
    }

    public void reset() {
        last = RideState.OFF;
        flash = pulse = 0;
        speedLines = whiteout = 0;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
