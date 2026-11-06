package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.park.V3;

import java.util.List;

/**
 * The lift linker's rules (design doc section 13, "Linking with lift_linker"), pure so they unit-test
 * without a world. Nodes are path nodes ({@link LiftLine#node}): the drive first, heads in order, the
 * return station last.
 *
 * <p>Each step between neighbouring nodes must be at most {@code maxSpan} apart horizontally and at
 * most {@link #MAX_SLOPE_DEGREES} steep, and the sagging cable of both sides (loaded to the left,
 * return to the right, offsets as in {@link LiftPath#anchor}) must clear solid blocks: sampled every
 * {@link #SAMPLE_SPACING} blocks along the span (the ends, which are at the towers, excluded), neither
 * the block holding the sample nor the block below it may be solid. Samples run from the span's
 * first node to its second, the loaded side before the return side, so "the first blocked sample" is
 * well defined.
 *
 * <p>A span's cable depends on the direction through both its end nodes, and a node's direction
 * needs the node after it. So {@link #checkAdd} checks a new node's distance and slope at once, but
 * the cable of the span before the previous node, whose directions are by then known; the last span is
 * checked by {@link #checkFinish}, which re-checks the whole line.
 */
public final class LinkValidator {
    public static final double MAX_SLOPE_DEGREES = 60.0;
    public static final double SAMPLE_SPACING = 2.0;

    private LinkValidator() {
    }

    public enum Reason {
        OK,
        TOO_FAR,
        TOO_STEEP,
        SAME_PLACE,
        BLOCKED_LOADED,
        BLOCKED_RETURN,
        TOO_MANY_HEADS,
        TOO_LONG,
        LEVEL_FULL,
        SAME_STATION
    }

    /** The limits (config values). */
    public record Limits(int maxSpan, int maxHeads, double maxLength, int maxLinesPerLevel) {
    }

    /**
     * The outcome. {@code span} is the index of the failing span (it runs from node {@code span} to
     * {@code span + 1}), or -1; {@code value} is the measured number for TOO_FAR, TOO_STEEP (degrees),
     * TOO_MANY_HEADS and TOO_LONG; for a blocked cable, {@code bx, by, bz} is the first solid block.
     */
    public record Result(Reason reason, int span, double value, int bx, int by, int bz) {
        public boolean ok() {
            return reason == Reason.OK;
        }

        static Result of(Reason reason, int span, double value) {
            return new Result(reason, span, value, 0, 0, 0);
        }
    }

    public static final Result OK = Result.of(Reason.OK, -1, 0);

    /**
     * Checks that {@code nodes}, whose last node was just added (it is not the return station), may
     * stand: the head count, the last step's distance and slope, and the cable of the span before the
     * previous node.
     */
    public static Result checkAdd(List<V3> nodes, BlockSolid solid, Limits limits) {
        int last = nodes.size() - 1;
        if (last < 1) return OK;
        if (last > limits.maxHeads()) return Result.of(Reason.TOO_MANY_HEADS, last - 1, last);
        Result step = checkStep(nodes, last - 1, limits);
        if (!step.ok()) return step;
        if (last >= 2) {
            if (sameColumn(nodes.get(last - 2), nodes.get(last))) return Result.of(Reason.SAME_PLACE, last - 1, 0);
            return checkCable(nodes, last - 2, false, solid);
        }
        return OK;
    }

    /** Checks a complete line, return station last, from scratch. {@code existingLines} are the level's other lines. */
    public static Result checkFinish(List<V3> nodes, BlockSolid solid, Limits limits, int existingLines) {
        if (nodes.size() < 2) return Result.of(Reason.SAME_STATION, -1, 0);
        if (existingLines >= limits.maxLinesPerLevel()) return Result.of(Reason.LEVEL_FULL, -1, existingLines);
        int heads = nodes.size() - 2;
        if (heads > limits.maxHeads()) return Result.of(Reason.TOO_MANY_HEADS, -1, heads);
        for (int i = 0; i + 1 < nodes.size(); i++) {
            Result step = checkStep(nodes, i, limits);
            if (!step.ok()) return step;
        }
        for (int i = 0; i + 2 < nodes.size(); i++) {
            if (sameColumn(nodes.get(i), nodes.get(i + 2))) return Result.of(Reason.SAME_PLACE, i, 0);
        }
        for (int i = 0; i + 1 < nodes.size(); i++) {
            Result cable = checkCable(nodes, i, true, solid);
            if (!cable.ok()) return cable;
        }
        double length = LiftPath.of(nodes).loopLength();
        if (length > limits.maxLength()) return Result.of(Reason.TOO_LONG, -1, length);
        return OK;
    }

    private static boolean sameColumn(V3 a, V3 b) {
        return a.horizontalDistance(b) < 1.0e-9;
    }

    private static Result checkStep(List<V3> nodes, int span, Limits limits) {
        V3 a = nodes.get(span), b = nodes.get(span + 1);
        double h = a.horizontalDistance(b);
        if (h < 1.0e-9) return Result.of(Reason.SAME_PLACE, span, 0);
        if (h > limits.maxSpan()) return Result.of(Reason.TOO_FAR, span, h);
        double slope = Math.toDegrees(Math.atan2(Math.abs(b.y() - a.y()), h));
        if (slope > MAX_SLOPE_DEGREES) return Result.of(Reason.TOO_STEEP, span, slope);
        return OK;
    }

    /**
     * The cable of the span from node {@code span} to {@code span + 1}. {@code complete} says the last
     * node is the return station (a wheel offset instead of a head's).
     */
    private static Result checkCable(List<V3> nodes, int span, boolean complete, BlockSolid solid) {
        int count = nodes.size();
        boolean startStation = span == 0, endStation = complete && span + 1 == count - 1;
        for (boolean loaded : new boolean[]{true, false}) {
            V3 a = LiftPath.anchor(nodes, span, startStation, loaded), b = LiftPath.anchor(nodes, span + 1, endStation, loaded);
            double h = Math.sqrt((b.x() - a.x()) * (b.x() - a.x()) + (b.z() - a.z()) * (b.z() - a.z()));
            int n = Math.max(1, (int) Math.ceil(h / SAMPLE_SPACING));
            Reason reason = loaded ? Reason.BLOCKED_LOADED : Reason.BLOCKED_RETURN;
            for (int k = 1; k < n; k++) {
                V3 p = LiftPath.spanPoint(a, b, k / (double) n);
                int x = (int) Math.floor(p.x()), y = (int) Math.floor(p.y()), z = (int) Math.floor(p.z());
                if (solid.solid(x, y, z)) return new Result(reason, span, 0, x, y, z);
                if (solid.solid(x, y - 1, z)) return new Result(reason, span, 0, x, y - 1, z);
            }
        }
        return OK;
    }
}
