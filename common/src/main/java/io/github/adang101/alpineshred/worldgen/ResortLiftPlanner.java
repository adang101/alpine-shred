package io.github.adang101.alpineshred.worldgen;

import io.github.adang101.alpineshred.lift.LinkValidator;
import io.github.adang101.alpineshred.lift.LiftPath;
import io.github.adang101.alpineshred.park.V3;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans a resort's lift line (design doc section 12) from the run's centreline and the terrain, with no
 * Minecraft types so it unit-tests. The drive station stands beside the top of the run, the return
 * station beside the bottom (before the lodge), and a tower head about every {@link #SPACING} blocks
 * between, all {@link #BEYOND_REACH} blocks outside the run's groomed edge on one side.
 *
 * <p>Heads are raised until the hanging chairs clear the terrain by {@link #CLEARANCE} blocks (plus a
 * margin for the terrain's estimate, {@link #MARGIN}, fading to nothing near the stations) at every
 * sample along every span, then the whole line goes through {@link LinkValidator#checkFinish}
 * against the same terrain. A line that cannot be made safe is not planned: {@link #plan} says why.
 *
 * <p>Heights are "first free block" heights: {@code ground(x, z)} is the Y of the first air block above the
 * ground, so the blocks below it are solid.
 */
public final class ResortLiftPlanner {
    /** Terrain height at a block column (the first free Y above the ground). */
    @FunctionalInterface
    public interface Ground {
        int height(int x, int z);

        /** As {@link RunPlanner.Terrain#prefetch}: the first {@code n} columns are about to be read. Never changes an answer. */
        default void prefetch(int[] xs, int[] zs, int n) {
        }
    }

    /** A node's block position. A tower head's Y is its head block; a station's is the station block, on its platform. */
    public record Node(int x, int y, int z) {
    }

    public record Plan(List<Node> nodes, int side) {
    }

    /** A plan, or null with the reason there is none. */
    public record Outcome(Plan plan, String failure) {
    }

    public static final double SPACING = 40;
    /** How far outside the run's reach (edge fade included) the line stands. */
    public static final double BEYOND_REACH = 6;
    /** Chairs hang at least this far above the ground. */
    public static final double CLEARANCE = 2;
    /** Extra clearance against terrain estimates and trees, away from the stations. */
    public static final double MARGIN = 3;
    /** The tallest tower, ground to head. */
    public static final int MAX_POLE = 30;
    /** Half the side of a station's platform (7 by 7). */
    public static final int PLATFORM_HALF = 3;
    /** Blocks short of the run's end that the return station stands, to keep its platform off the lodge. */
    static final double END_BACKOFF = 4;
    static final double MIN_LENGTH = 48;
    private static final double SAMPLE = 2;
    /** Chairs near a station are meant to be low; no clearance is asked within this distance of one. */
    private static final double STATION_SKIP = 4, STATION_RAMP = 12;

    private ResortLiftPlanner() {
    }

    /**
     * @param centre the run's centreline as (x, z, y) points, top first
     * @param reach  the run's half width plus its edge fade
     * @param ground the terrain
     * @param minGround stations and heads need ground above this (sea level)
     * @param seed   picks which side of the run is tried first
     */
    public static Outcome plan(List<double[]> centre, double reach, Ground ground, int minGround, LinkValidator.Limits limits, long seed) {
        String firstFailure = null;
        int first = ((seed >>> 7) & 1) == 0 ? 1 : -1;
        for (int side : new int[]{first, -first}) {
            Outcome o = planSide(centre, reach, ground, minGround, limits, side);
            if (o.plan() != null) return o;
            if (firstFailure == null) firstFailure = o.failure();
        }
        return new Outcome(null, firstFailure);
    }

    static Outcome planSide(List<double[]> centre, double reach, Ground ground, int minGround, LinkValidator.Limits limits, int side) {
        int n = centre.size();
        if (n < 3) return fail("run too short");
        // The offset polyline.
        double[][] q = new double[n][2];
        double offset = reach + BEYOND_REACH;
        for (int i = 0; i < n; i++) {
            double[] a = centre.get(Math.max(0, i - 1)), b = centre.get(Math.min(n - 1, i + 1));
            double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.max(1.0e-9, Math.hypot(dx, dz));
            q[i][0] = centre.get(i)[0] + side * (-dz / len) * offset;
            q[i][1] = centre.get(i)[1] + side * (dx / len) * offset;
        }
        double[] cum = new double[n];
        for (int i = 1; i < n; i++) cum[i] = cum[i - 1] + Math.hypot(q[i][0] - q[i - 1][0], q[i][1] - q[i - 1][1]);
        double length = cum[n - 1] - END_BACKOFF;
        if (length < MIN_LENGTH) return fail("line only " + (int) length + " long");
        int spans = Math.max(1, (int) Math.round(length / SPACING));
        List<int[]> columns = new ArrayList<>();
        for (int k = 0; k <= spans; k++) columns.add(at(q, cum, length * k / spans));
        // Each node clear of the run.
        for (int[] c : columns) {
            double near = Double.MAX_VALUE;
            for (double[] p : centre) near = Math.min(near, Math.hypot(p[0] - c[0], p[1] - c[1]));
            if (near < reach + BEYOND_REACH - 1) return fail("a node only " + (int) near + " from the run");
        }
        int last = columns.size() - 1;
        prefetchLine(columns, ground);
        int[] heights = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            int[] c = columns.get(i);
            if (ground.height(c[0], c[1]) <= minGround) return fail("a node on water");
            heights[i] = i == 0 || i == last ? stationY(ground, c[0], c[1]) : groundNear(ground, c[0], c[1]) + 3;
        }
        // Raise the heads until every span clears.
        boolean clear = false;
        for (int pass = 0; pass < 100 && !clear; pass++) {
            clear = true;
            for (int i = 0; i < last; i++) {
                double deficit = worstDeficit(columns, heights, i, ground, i == 0, i + 1 == last);
                if (deficit <= 0) continue;
                clear = false;
                int raise = (int) Math.ceil(deficit);
                boolean any = false;
                if (i > 0) {
                    heights[i] += raise;
                    any = true;
                }
                if (i + 1 < last) {
                    heights[i + 1] += raise;
                    any = true;
                }
                if (!any) return fail("a span between the stations cannot clear the ground");
            }
        }
        if (!clear) return fail("the heads would not clear the ground");
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i <= last; i++) {
            int[] c = columns.get(i);
            if (i > 0 && i < last && heights[i] - groundNear(ground, c[0], c[1]) > MAX_POLE) return fail("a tower over " + MAX_POLE + " tall");
            nodes.add(new Node(c[0], heights[i], c[1]));
        }
        List<V3> path = new ArrayList<>();
        for (Node nd : nodes) path.add(new V3(nd.x() + 0.5, nd.y() + 0.5, nd.z() + 0.5));
        LinkValidator.Result r = LinkValidator.checkFinish(path, (x, y, z) -> y < ground.height(x, z), limits, 0);
        if (!r.ok()) return fail("link validation: " + r.reason() + " at span " + r.span());
        return new Outcome(new Plan(List.copyOf(nodes), side), null);
    }

    /**
     * Asks the terrain for the columns the clearance checks will read: every node, and the three lateral columns
     * every {@link #SAMPLE} blocks along each span (as {@link #worstDeficit} reads them), so a slow terrain can
     * fetch them together (README clarification 86). A hint only: whatever it misses is read when it is needed.
     */
    private static void prefetchLine(List<int[]> columns, Ground ground) {
        int total = columns.size();
        for (int i = 0; i + 1 < columns.size(); i++) {
            int[] a = columns.get(i), b = columns.get(i + 1);
            total += 3 * Math.max(2, (int) Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / SAMPLE));
        }
        int[] xs = new int[total], zs = new int[total];
        int n = 0;
        for (int[] c : columns) {
            xs[n] = c[0];
            zs[n++] = c[1];
        }
        for (int i = 0; i + 1 < columns.size(); i++) {
            int[] a = columns.get(i), b = columns.get(i + 1);
            double dx = b[0] - a[0], dz = b[1] - a[1], h = Math.hypot(dx, dz);
            double nx = -dz / h, nz = dx / h;
            int samples = Math.max(2, (int) Math.ceil(h / SAMPLE));
            for (int k = 1; k < samples; k++) {
                double t = k / (double) samples;
                double x = a[0] + 0.5 + dx * t, z = a[1] + 0.5 + dz * t;
                for (int lateral = -1; lateral <= 1; lateral++) {
                    xs[n] = (int) Math.floor(x + nx * 1.5 * lateral);
                    zs[n++] = (int) Math.floor(z + nz * 1.5 * lateral);
                }
            }
        }
        ground.prefetch(xs, zs, n);
    }

    private static Outcome fail(String why) {
        return new Outcome(null, why);
    }

    /** The point {@code s} blocks along the polyline, rounded to its block column. */
    private static int[] at(double[][] q, double[] cum, double s) {
        int i = 1;
        while (i < cum.length - 1 && cum[i] < s) i++;
        double span = cum[i] - cum[i - 1], t = span <= 0 ? 0 : Math.max(0, Math.min(1, (s - cum[i - 1]) / span));
        return new int[]{(int) Math.floor(q[i - 1][0] + (q[i][0] - q[i - 1][0]) * t), (int) Math.floor(q[i - 1][1] + (q[i][1] - q[i - 1][1]) * t)};
    }

    /** The highest ground under a station's platform. */
    static int stationY(Ground ground, int x, int z) {
        int top = Integer.MIN_VALUE;
        for (int dx = -PLATFORM_HALF; dx <= PLATFORM_HALF; dx += PLATFORM_HALF) {
            for (int dz = -PLATFORM_HALF; dz <= PLATFORM_HALF; dz += PLATFORM_HALF) top = Math.max(top, ground.height(x + dx, z + dz));
        }
        return top;
    }

    /** The highest ground within a block of (x, z). */
    static int groundNear(Ground ground, int x, int z) {
        int top = Integer.MIN_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) top = Math.max(top, ground.height(x + dx, z + dz));
        }
        return top;
    }

    /**
     * How far short of the required clearance the chairs of span {@code i} (node {@code i} to {@code i + 1}) hang at the
     * worst sample; 0 or less if it clears. The seat hangs {@link LiftPath#CHAIR_HANG} below a cable
     * {@link LiftPath#CABLE_HEIGHT} above the node's centre, sagging as {@link LiftPath#sag} says.
     */
    static double worstDeficit(List<int[]> columns, int[] heights, int i, Ground ground, boolean startStation, boolean endStation) {
        int[] a = columns.get(i), b = columns.get(i + 1);
        double dx = b[0] - a[0], dz = b[1] - a[1], h = Math.hypot(dx, dz);
        double sag = LiftPath.sag(h);
        double nx = -dz / h, nz = dx / h;
        int samples = Math.max(2, (int) Math.ceil(h / SAMPLE));
        double worst = -Double.MAX_VALUE;
        for (int k = 1; k < samples; k++) {
            double t = k / (double) samples, along = t * h;
            double fromStation = Math.min(startStation ? along : Double.MAX_VALUE, endStation ? h - along : Double.MAX_VALUE);
            if (fromStation < STATION_SKIP) continue;
            double margin = MARGIN * Math.min(1, fromStation / STATION_RAMP);
            double x = a[0] + 0.5 + dx * t, z = a[1] + 0.5 + dz * t;
            double seat = seatY(heights[i], heights[i + 1], t, sag);
            double g = Double.NEGATIVE_INFINITY;
            for (int lateral = -1; lateral <= 1; lateral++) {
                g = Math.max(g, ground.height((int) Math.floor(x + nx * 1.5 * lateral), (int) Math.floor(z + nz * 1.5 * lateral)));
            }
            worst = Math.max(worst, g + CLEARANCE + margin - seat);
        }
        return worst;
    }

    private static double seatY(int y0, int y1, double t, double sag) {
        return y0 + (y1 - y0) * t + 0.5 + LiftPath.CABLE_HEIGHT - LiftPath.CHAIR_HANG - sag * 4 * t * (1 - t);
    }

    /** The smallest height of a chair seat above {@code ground} along the plan, not counting the stretches next to the stations. */
    public static double minClearance(Plan plan, Ground ground) {
        int count = plan.nodes().size();
        double min = Double.MAX_VALUE;
        for (int i = 0; i + 1 < count; i++) {
            Node a = plan.nodes().get(i), b = plan.nodes().get(i + 1);
            double dx = b.x() - a.x(), dz = b.z() - a.z(), h = Math.hypot(dx, dz), sag = LiftPath.sag(h);
            int samples = Math.max(2, (int) Math.ceil(h / SAMPLE));
            for (int k = 1; k < samples; k++) {
                double t = k / (double) samples, along = t * h;
                double fromStation = Math.min(i == 0 ? along : Double.MAX_VALUE, i + 2 == count ? h - along : Double.MAX_VALUE);
                if (fromStation < STATION_SKIP) continue;
                double seat = seatY(a.y(), b.y(), t, sag);
                min = Math.min(min, seat - ground.height((int) Math.floor(a.x() + 0.5 + dx * t), (int) Math.floor(a.z() + 0.5 + dz * t)));
            }
        }
        return min;
    }
}
