package io.github.adang101.alpineshred.feel;

import io.github.adang101.alpineshred.movement.RideState;

/**
 * Snow build-up on the local rider's face: the reason to wear goggles (README clarification 92).
 * A level from 0 to 1 that climbs toward a target set by how much snow the ride throws up, and
 * clears slowly once the target drops. The HUD draws it as a frosted edge vignette with snow
 * blotches; the middle of the screen stays readable, and the opacity never passes
 * {@link #MAX_ALPHA}.
 *
 * <p>Target, per tick: speed (up to {@value #SPEED_WEIGHT} at 1.25 blocks per tick), powder
 * ({@value #POWDER_WEIGHT}), a hard carve ({@value #CARVE_WEIGHT} at full lean and speed) and snowfall
 * ({@value #SNOWFALL_WEIGHT}, scaled by speed). A powder landing and a wipeout add a burst on top. Goggles
 * mean no build-up at all, and any that is left wipes off in about a second.
 *
 * <p>Kept free of Minecraft types so the numbers are unit-tested.
 */
public final class LensBuildup {
    /** Highest edge opacity, the vignette's corners at full build-up. */
    public static final double MAX_ALPHA = 0.5;
    /** {@link #MAX_ALPHA} under {@code client.reduceMotion}. */
    public static final double REDUCED_MAX_ALPHA = 0.3;

    public static final double SPEED_FROM = 0.25;
    public static final double SPEED_RANGE = 1.0;
    public static final double SPEED_WEIGHT = 0.3;
    public static final double POWDER_WEIGHT = 0.7;
    public static final double CARVE_WEIGHT = 0.25;
    public static final double SNOWFALL_WEIGHT = 0.15;

    /** Level gained per tick while below the target: about 3 s to reach 0.9. */
    public static final double RISE = 0.015;
    /** Level lost per tick while above the target: 12.5 s from full to clear. */
    public static final double FALL = 0.004;
    /** Level lost per tick with goggles on: a second from full to clear. */
    public static final double GOGGLES_FALL = 0.05;
    public static final double POWDER_LANDING_BURST = 0.4;
    public static final double WIPEOUT_BURST = 0.6;
    /** A landing slower than this (blocks per tick) throws up no burst. */
    public static final double BURST_MIN_SPEED = 0.2;

    private RideState last = RideState.OFF;
    private double level;

    /**
     * @param lean       steering lean, -1 to 1
     * @param inSnowfall snow is falling where the rider is
     * @param goggles    goggles are worn
     */
    public void tick(RideState state, double speed, double lean, boolean inSnowfall, boolean goggles) {
        RideState previous = last;
        last = state;
        if (goggles) {
            level = Math.max(0, level - GOGGLES_FALL);
            return;
        }
        if (previous == RideState.AIR && state == RideState.POWDER && speed > BURST_MIN_SPEED) {
            level = Math.min(1, level + POWDER_LANDING_BURST);
        } else if (previous != RideState.WIPEOUT && state == RideState.WIPEOUT) {
            level = Math.min(1, level + WIPEOUT_BURST);
        }
        double target = target(state, speed, lean, inSnowfall);
        if (target > level) level = Math.min(target, level + RISE);
        else level = Math.max(target, level - FALL);
    }

    /** The level the rider's conditions are building toward; 0 when not riding. */
    static double target(RideState state, double speed, double lean, boolean inSnowfall) {
        if (!state.isControllerDriven()) return 0;
        double target = SPEED_WEIGHT * clamp((speed - SPEED_FROM) / SPEED_RANGE, 0, 1);
        if (state == RideState.POWDER) target += POWDER_WEIGHT;
        if (state == RideState.CARVE) target += CARVE_WEIGHT * Math.abs(clamp(lean, -1, 1)) * clamp(speed / 0.8, 0, 1);
        if (inSnowfall) target += SNOWFALL_WEIGHT * clamp(speed / 0.5, 0, 1);
        return Math.min(1, target);
    }

    /** The build-up, 0 to 1. */
    public double level() {
        return level;
    }

    /** Opacity of the frost vignette at its corners. */
    public double alpha(boolean reduceMotion) {
        return level * (reduceMotion ? REDUCED_MAX_ALPHA : MAX_ALPHA);
    }

    /** Opacity of the snow blotches, which only show once the build-up is heavy. */
    public double blotchAlpha(boolean reduceMotion) {
        return alpha(reduceMotion) * level;
    }

    public void reset() {
        last = RideState.OFF;
        level = 0;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
