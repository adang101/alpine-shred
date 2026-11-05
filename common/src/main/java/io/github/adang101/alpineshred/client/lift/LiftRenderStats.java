package io.github.adang101.alpineshred.client.lift;

import dev.architectury.event.events.client.ClientGuiEvent;

import java.util.Locale;

/**
 * What the lift renderers cost per frame, for the client smoke (phase 8 plan, WP8). Off unless
 * {@link #enable()} is called, so ordinary play pays nothing. Each renderer times itself with
 * {@link #begin()} and {@link #end}; the HUD render, which ends every frame, closes the frame and
 * adds it to the totals of the parts that drew in it.
 */
public final class LiftRenderStats {
    /** The parts timed. */
    public enum Part {CABLE, WHEEL, CHAIR}

    private static boolean enabled;
    private static final long[] FRAME_NANOS = new long[Part.values().length];
    private static final long[] TOTAL_NANOS = new long[Part.values().length];
    private static final long[] WORST_NANOS = new long[Part.values().length];
    private static final int[] FRAMES = new int[Part.values().length];

    private LiftRenderStats() {
    }

    /** Starts counting; the smoke calls it once. */
    public static void enable() {
        if (enabled) return;
        enabled = true;
        ClientGuiEvent.RENDER_HUD.register((graphics, tickDelta) -> endFrame());
    }

    /** The start time for {@link #end}, or 0 when counting is off. */
    static long begin() {
        return enabled ? System.nanoTime() : 0L;
    }

    static void end(Part part, long started) {
        if (enabled) FRAME_NANOS[part.ordinal()] += System.nanoTime() - started;
    }

    private static void endFrame() {
        for (int i = 0; i < FRAME_NANOS.length; i++) {
            if (FRAME_NANOS[i] == 0) continue;
            TOTAL_NANOS[i] += FRAME_NANOS[i];
            WORST_NANOS[i] = Math.max(WORST_NANOS[i], FRAME_NANOS[i]);
            FRAMES[i]++;
            FRAME_NANOS[i] = 0;
        }
    }

    /** Frames in which {@code part} drew anything. */
    public static int frames(Part part) {
        return FRAMES[part.ordinal()];
    }

    /** Mean milliseconds a frame spent in {@code part}, over the frames it drew in; 0 if it never drew. */
    public static double meanMillis(Part part) {
        int i = part.ordinal();
        return FRAMES[i] == 0 ? 0 : TOTAL_NANOS[i] / 1.0e6 / FRAMES[i];
    }

    public static double worstMillis(Part part) {
        return WORST_NANOS[part.ordinal()] / 1.0e6;
    }

    public static String summary() {
        StringBuilder out = new StringBuilder("lift render cost per frame:");
        for (Part part : Part.values()) {
            out.append(String.format(Locale.ROOT, " %s mean %.3f ms worst %.3f ms over %d frames;", part.name().toLowerCase(Locale.ROOT),
                    meanMillis(part), worstMillis(part), frames(part)));
        }
        return out.toString();
    }
}
