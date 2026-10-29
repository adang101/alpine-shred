package io.github.adang101.alpineshred.trick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One combo (design doc section 8, "Score" and "Combos"): the landings and grind entries chained
 * within {@value #WINDOW_TICKS} ground ticks of each other at speed. Pure; the server keeps one per
 * rider and banks it when it runs out.
 *
 * <pre>S = (sum of trick points + air + height) * V * C * L</pre>
 * Each landing's points are multiplied by its landing factor L (and 1.1 for a switch landing) as
 * they come in; the combo's total is their sum times the variety V and combo C multipliers, so the
 * popup can show a running score and multiplier. A trick type repeated within the combo scores
 * half. The total is rounded and capped.
 */
public final class Combo {
    public static final int WINDOW_TICKS = 60;
    public static final double MIN_SPEED = 0.1;

    /** One landing or grind entry as it went into the combo. */
    public record Entry(List<TrickPart> parts, LandingQuality quality, double points) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Integer> seen = new HashMap<>();
    private final EnumSet<TrickPart.Category> categories = EnumSet.noneOf(TrickPart.Category.class);
    private double base;
    private int groundTicks;

    /**
     * Adds a landing (or grind entry). Returns the points it added before the multipliers, or -1
     * for a bail, which loses the combo: the caller drops it.
     */
    public double land(List<TrickPart> parts, LandingQuality quality, boolean switchLanding) {
        if (quality == LandingQuality.BAIL) return -1;
        double sum = 0;
        List<TrickPart> scored = new ArrayList<>();
        for (TrickPart p : parts) {
            int times = seen.merge(p.id(), 1, Integer::sum);
            double points = times > 1 ? p.points() / 2 : p.points();
            scored.add(new TrickPart(p.id(), p.category(), points));
            sum += points;
            if (p.category() != null) categories.add(p.category());
        }
        double added = sum * quality.factor * (switchLanding ? TrickScorer.SWITCH_FACTOR : 1.0);
        entries.add(new Entry(Collections.unmodifiableList(scored), quality, added));
        base += added;
        groundTicks = 0;
        return added;
    }

    /** One tick on the ground between landings; true when the combo has run out and should bank. */
    public boolean tickGround(double speed) {
        groundTicks++;
        return groundTicks > WINDOW_TICKS || speed < MIN_SPEED;
    }

    /** A landing that scored nothing still keeps the combo going: the ground clock restarts. */
    public void tickGroundReset() {
        groundTicks = 0;
    }

    /** {@code 1 + 0.2 * (distinct categories - 1)} over spin, flip, grab and grind or slide. */
    public double variety() {
        return 1 + 0.2 * Math.max(0, categories.size() - 1);
    }

    /** {@code min(3, 1 + 0.25 * chainedLandings)}: the first landing chains nothing. */
    public double comboMultiplier() {
        return Math.min(3.0, 1 + 0.25 * Math.max(0, entries.size() - 1));
    }

    public double multiplier() {
        return variety() * comboMultiplier();
    }

    public int total(int cap) {
        return (int) Math.min(cap, Math.round(base * multiplier()));
    }

    public boolean isEmpty() {
        return entries.isEmpty() || base <= 0;
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Ground ticks since the last landing, for the popup's draining timer. */
    public int groundTicks() {
        return groundTicks;
    }
}
