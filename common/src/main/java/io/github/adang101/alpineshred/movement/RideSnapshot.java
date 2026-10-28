package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.trick.GrabType;

/**
 * The rider state that every client animates from, packed into one synced int
 * (design doc section 7, "Rider state").
 *
 * <pre>
 * bits  0-3   state            (RideState ordinal)
 * bits  4-5   mode             (none, board, skis)
 * bit   6     stance           (0 regular, 1 goofy)
 * bit   7     grab active
 * bits  8-11  grab type        (GrabType ordinal)
 * bits 12-15  spin/flip flags  (FLAG_* below)
 * bits 16-18  lean             (-3..3, 3-bit two's complement so 0 packs to 0)
 * bits 19-22  speed level      (0..15, SPEED_STEP blocks per tick per level)
 * bits 23-30  heading          (256 steps around the circle)
 * bit  31     unused, always 0 so the packed value is never negative
 * </pre>
 *
 * {@link #OFF} packs to 0, the synced data default, so an unset value decodes as not riding.
 * Lean and speed are deliberately coarse; remote clients interpolate between updates.
 */
public record RideSnapshot(
        RideState state,
        RideMode mode,
        boolean goofy,
        boolean grabActive,
        GrabType grab,
        int flags,
        int lean,
        int speedLevel,
        int headingStep) {

    public static final int FLAG_SPIN_LEFT = 1;
    public static final int FLAG_SPIN_RIGHT = 1 << 1;
    public static final int FLAG_FLIP_FRONT = 1 << 2;
    public static final int FLAG_FLIP_BACK = 1 << 3;

    public static final int MAX_LEAN = 3;
    public static final int MAX_SPEED_LEVEL = 15;
    /** 0.12 blocks per tick per level: level 15 (1.8) covers the waxed pro-ski cap of 1.78. */
    public static final double SPEED_STEP = 0.12;
    public static final int HEADING_STEPS = 256;

    public static final RideSnapshot OFF = new RideSnapshot(
            RideState.OFF, RideMode.NONE, false, false, GrabType.NONE, 0, 0, 0, 0);

    private static final int STATE_SHIFT = 0;
    private static final int MODE_SHIFT = 4;
    private static final int STANCE_SHIFT = 6;
    private static final int GRAB_ACTIVE_SHIFT = 7;
    private static final int GRAB_SHIFT = 8;
    private static final int FLAGS_SHIFT = 12;
    private static final int LEAN_SHIFT = 16;
    private static final int SPEED_SHIFT = 19;
    private static final int HEADING_SHIFT = 23;

    public RideSnapshot {
        if (state == null) state = RideState.OFF;
        if (mode == null) mode = RideMode.NONE;
        if (grab == null) grab = GrabType.NONE;
        flags &= 0xF;
        lean = clamp(lean, -MAX_LEAN, MAX_LEAN);
        speedLevel = clamp(speedLevel, 0, MAX_SPEED_LEVEL);
        headingStep = Math.floorMod(headingStep, HEADING_STEPS);
    }

    public int pack() {
        return (state.id() & 0xF) << STATE_SHIFT
                | (mode.ordinal() & 0x3) << MODE_SHIFT
                | (goofy ? 1 : 0) << STANCE_SHIFT
                | (grabActive ? 1 : 0) << GRAB_ACTIVE_SHIFT
                | (grab.ordinal() & 0xF) << GRAB_SHIFT
                | (flags & 0xF) << FLAGS_SHIFT
                | (lean & 0x7) << LEAN_SHIFT
                | (speedLevel & 0xF) << SPEED_SHIFT
                | (headingStep & 0xFF) << HEADING_SHIFT;
    }

    public static RideSnapshot unpack(int bits) {
        return new RideSnapshot(
                RideState.byId(bits >>> STATE_SHIFT & 0xF),
                RideMode.byId(bits >>> MODE_SHIFT & 0x3),
                (bits >>> STANCE_SHIFT & 1) != 0,
                (bits >>> GRAB_ACTIVE_SHIFT & 1) != 0,
                GrabType.byId(bits >>> GRAB_SHIFT & 0xF),
                bits >>> FLAGS_SHIFT & 0xF,
                ((bits >>> LEAN_SHIFT & 0x7) << 29) >> 29,
                bits >>> SPEED_SHIFT & 0xF,
                bits >>> HEADING_SHIFT & 0xFF);
    }

    /** Reads only the state bits, for hot paths that do not need the full record. */
    public static RideState stateOf(int bits) {
        return RideState.byId(bits & 0xF);
    }

    public static RideMode modeOf(int bits) {
        return RideMode.byId(bits >>> MODE_SHIFT & 0x3);
    }

    // ---- quantizers -------------------------------------------------------------------------

    public static int speedToLevel(double blocksPerTick) {
        return clamp((int) Math.round(blocksPerTick / SPEED_STEP), 0, MAX_SPEED_LEVEL);
    }

    public static double levelToSpeed(int level) {
        return clamp(level, 0, MAX_SPEED_LEVEL) * SPEED_STEP;
    }

    /** Minecraft yaw in degrees (any range) to a 0..255 step. */
    public static int headingToStep(double yawDegrees) {
        return Math.floorMod((int) Math.round(yawDegrees * HEADING_STEPS / 360.0), HEADING_STEPS);
    }

    /** A 0..255 step back to degrees in [0, 360). */
    public static double stepToHeading(int step) {
        return Math.floorMod(step, HEADING_STEPS) * 360.0 / HEADING_STEPS;
    }

    /** Lean in [-1, 1] (edge input or carve intensity) to the coarse -3..3 range. */
    public static int leanToLevel(double lean) {
        return clamp((int) Math.round(lean * MAX_LEAN), -MAX_LEAN, MAX_LEAN);
    }

    // ---- convenience ------------------------------------------------------------------------

    public double speed() {
        return levelToSpeed(speedLevel);
    }

    public double heading() {
        return stepToHeading(headingStep);
    }

    public RideSnapshot withState(RideState newState) {
        return new RideSnapshot(newState, mode, goofy, grabActive, grab, flags, lean, speedLevel, headingStep);
    }

    public RideSnapshot withMode(RideMode newMode) {
        return new RideSnapshot(state, newMode, goofy, grabActive, grab, flags, lean, speedLevel, headingStep);
    }

    public RideSnapshot withMotion(double speedBlocksPerTick, double headingDegrees, double leanUnit) {
        return new RideSnapshot(state, mode, goofy, grabActive, grab, flags,
                leanToLevel(leanUnit), speedToLevel(speedBlocksPerTick), headingToStep(headingDegrees));
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
