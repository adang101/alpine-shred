package io.github.adang101.alpineshred.feel;

/**
 * Leaves in the rider's face (README clarification 93). While the local rider's eye is inside a
 * {@code ride_through} block the cover closes in a few ticks; once the head is clear it fades out
 * over {@value #FADE_TICKS} ticks, so a brush past a branch is still felt. Goggles protect the
 * eyes but not the view, so they only cut the cover to {@value #GOGGLES_FACTOR} of its opacity.
 * Pure, so the numbers are unit-tested.
 */
public final class LeafCover {
    /** Highest opacity, without goggles. */
    public static final double MAX_ALPHA = 0.8;
    public static final double GOGGLES_FACTOR = 0.6;
    public static final int FADE_TICKS = 12;
    /** Level gained per tick inside leaves: four ticks to fully closed. */
    public static final double RISE = 0.25;

    private double previous;
    private double level;

    /** @param headInside the eye is inside a {@code ride_through} block while riding */
    public void tick(boolean headInside) {
        previous = level;
        level = headInside ? Math.min(1, level + RISE) : Math.max(0, level - 1.0 / FADE_TICKS);
    }

    /** Opacity this frame, interpolated between the last two ticks. */
    public double alpha(float partialTick, boolean goggles) {
        double l = previous + (level - previous) * partialTick;
        return l * MAX_ALPHA * (goggles ? GOGGLES_FACTOR : 1);
    }

    public void reset() {
        previous = level = 0;
    }
}
