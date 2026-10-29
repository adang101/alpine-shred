package io.github.adang101.alpineshred.trick;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Points for what a rider did (design doc section 8, "Trick catalog and base points"). Pure: the
 * server scores with it, and the client's popup shows the server's numbers.
 */
public final class TrickScorer {
    /** A grab held less than this is a brush of the board, not a grab. */
    public static final int MIN_GRAB_TICKS = 3;
    /** Spin in and spin out need this much rotation. */
    public static final double SPIN_IN_OUT_DEGREES = 160;
    public static final double SPIN_IN_OUT_POINTS = 150;
    public static final double SWITCH_UP_POINTS = 100;
    public static final double SWITCH_FACTOR = 1.1;
    public static final double BOX_FACTOR = 0.8;
    public static final double MITTS_FACTOR = 1.1;
    /** 50-50, nose, tail, boardslide: points per second (section 8). */
    private static final double[] GRIND_PER_SECOND = {40, 70, 70, 55};
    private static final String[] VARIANT_IDS = {"5050", "nose", "tail", "boardslide"};

    private TrickScorer() {
    }

    /** {@code 50 * n * (n + 1)} for {@code n} whole half-turns. */
    public static double spinPoints(int halfTurns) {
        return halfTurns <= 0 ? 0 : 50.0 * halfTurns * (halfTurns + 1);
    }

    /** {@code 400 * n^2} for {@code n} whole flips. */
    public static double flipPoints(int flips) {
        return flips <= 0 ? 0 : 400.0 * flips * flips;
    }

    /** {@code 60 + 120 * min(heldSeconds, 1.5)}, times the grab's factor (and mitts' 10%). */
    public static double grabPoints(GrabType type, int ticks, boolean mitts) {
        if (type == GrabType.NONE || ticks < MIN_GRAB_TICKS) return 0;
        double seconds = Math.min(ticks / 20.0, 1.5);
        return (60 + 120 * seconds) * type.scoreFactor() * (mitts ? MITTS_FACTOR : 1.0);
    }

    public static double airTimePoints(int airTicks) {
        return 60 * Math.max(0, airTicks / 20.0 - 0.6);
    }

    public static double heightPoints(double apex) {
        return 25 * Math.max(0, apex - 1.5);
    }

    /** The parts of an air session; nothing for a hop under 2 ticks (section 8, validation rule 3). */
    public static List<TrickPart> air(AirReport r, boolean mitts) {
        List<TrickPart> out = new ArrayList<>();
        if (r.airTicks() < 2) return out;
        int half = TrickAngles.halfTurns(r.yawAccum());
        if (half > 0) out.add(new TrickPart("spin_" + half * 180, TrickPart.Category.SPIN, spinPoints(half)));
        int flips = TrickAngles.flips(r.pitchAccum());
        if (flips > 0) {
            String dir = r.pitchAccum() >= 0 ? "front" : "back";
            out.add(new TrickPart("flip_" + dir + "_" + flips, TrickPart.Category.FLIP, flipPoints(flips)));
        }
        for (GrabType type : GrabType.values()) {
            double p = grabPoints(type, r.grab(type), mitts);
            if (p > 0) out.add(new TrickPart("grab_" + type.name().toLowerCase(Locale.ROOT), TrickPart.Category.GRAB, p));
        }
        boolean spun = Math.abs(r.yawAccum()) >= SPIN_IN_OUT_DEGREES;
        if (spun && r.fromRail()) out.add(new TrickPart("spin_out", TrickPart.Category.SPIN, SPIN_IN_OUT_POINTS));
        if (spun && r.endsOnRail()) out.add(new TrickPart("spin_in", TrickPart.Category.SPIN, SPIN_IN_OUT_POINTS));
        double air = airTimePoints(r.airTicks());
        if (air > 0) out.add(new TrickPart("air", null, air));
        double height = heightPoints(r.apex());
        if (height > 0) out.add(new TrickPart("height", null, height));
        return out;
    }

    /** The parts of a grind or box slide: per second by variant, switch-ups, switch entry and box factors. */
    public static List<TrickPart> grind(GrindReport g) {
        List<TrickPart> out = new ArrayList<>();
        int v = Math.max(0, Math.min(3, g.variant()));
        double factor = (g.switchEntry() ? SWITCH_FACTOR : 1.0) * (g.box() ? BOX_FACTOR : 1.0);
        double points = GRIND_PER_SECOND[v] * g.durationTicks() / 20.0 * factor;
        String id = (g.box() ? "slide_" : "grind_") + VARIANT_IDS[v] + (g.switchEntry() ? "_switch" : "");
        if (points > 0) out.add(new TrickPart(id, TrickPart.Category.GRIND, points));
        for (int i = 0; i < g.switchUps(); i++) out.add(new TrickPart("switch_up", TrickPart.Category.GRIND, SWITCH_UP_POINTS));
        return out;
    }

    /**
     * Section 8: "entering a grind counts as a landing for chaining; its quality comes from the
     * entry angle". Within 15 degrees of the variant's own line (along the rail for a 50-50, across
     * for a boardslide, 45 off for nose and tail) is PERFECT, otherwise CLEAN.
     */
    public static LandingQuality grindEntry(int variant, double entryAxis) {
        double ideal = switch (variant) {
            case 0 -> 0;
            case 3 -> 90;
            default -> 45;
        };
        return Math.abs(entryAxis - ideal) <= 15 ? LandingQuality.PERFECT : LandingQuality.CLEAN;
    }
}
