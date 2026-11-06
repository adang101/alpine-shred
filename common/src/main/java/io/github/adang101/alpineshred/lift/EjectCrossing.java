package io.github.adang101.alpineshred.lift;

/**
 * Pure helper: when does a chair eject its riders at the top (design doc section 13, "Unloading")?
 * A chair is ejected when its loop distance {@code s} crosses {@code ejectS} going forward between two
 * ticks. {@code s} wraps at the loop length, so differences are taken modulo it.
 *
 * <p>The crossing test alone would miss a chair that jumped past {@code ejectS} (a large step, or a
 * rider that arrived after the point), so {@link #shouldEject} also ejects any chair found in the span
 * from {@code ejectS} to the start of the return cable ({@code returnStart}), where nobody belongs.
 */
public final class EjectCrossing {
    /** Steps longer than this many blocks are jumps, not motion; only the zone test applies to them. */
    public static final double MAX_STEP = 2.0;

    private EjectCrossing() {
    }

    private static double mod(double v, double loop) {
        double r = v % loop;
        return r < 0 ? r + loop : r;
    }

    /** True if {@code ejectS} lies in {@code (prev, now]}, moving forward by at most {@link #MAX_STEP}. */
    public static boolean crossed(double prev, double now, double ejectS, double loop) {
        if (Double.isNaN(prev) || loop <= 0) return false;
        double step = mod(now - prev, loop);
        if (step == 0 || step > MAX_STEP) return false;
        double ahead = mod(ejectS - prev, loop);
        return ahead > 0 && ahead <= step;
    }

    /** True if {@code s} lies in {@code [ejectS, returnStart)}: past the ejection point, before the return cable. */
    public static boolean pastEject(double s, double ejectS, double returnStart, double loop) {
        if (loop <= 0) return false;
        double from = mod(s - ejectS, loop);
        double span = mod(returnStart - ejectS, loop);
        return from < span;
    }

    public static boolean shouldEject(double prev, double now, double ejectS, double returnStart, double loop) {
        return crossed(prev, now, ejectS, loop) || pastEject(now, ejectS, returnStart, loop);
    }
}
